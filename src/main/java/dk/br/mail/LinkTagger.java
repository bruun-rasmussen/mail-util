package dk.br.mail;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author osa
 */
public class LinkTagger
{
  private static final Logger LOG = LoggerFactory.getLogger(LinkTagger.class);

  private final Stack<Map<String,String>> tags = new Stack();

  public LinkTagger() {
    _init();
  }

  private void _init() {
    pushFrame(); // never popped. Death by GC.
  }

  public void pushFrame() {
    tags.push(new LinkedHashMap<String, String>());
  }

  public void popFrame() {
    tags.pop().clear();
  }

  public void put(String name, String value) {
    String was = tags.peek().put(name, value);
  }

  public String amendQueryString(String qs) {
    Set<String> names = new HashSet();
    List<QueryParam> parts = new LinkedList();
    splitQueryString(qs, parts);
    for (QueryParam p : parts)
      names.add(p.name);

    for (Map<String, String> t : tags)
      for (Map.Entry<String, String> e : t.entrySet())
        if (names.add(e.getKey()))
          parts.add(QueryParam.namedValue(e.getKey(), e.getValue()));

    return joinQueryString(parts);
  }

  private static final Pattern QUERY_LIST = Pattern.compile("[&?]+");

  private static void splitQueryString(String qs, List<QueryParam> res) {
    if (!StringUtils.isEmpty(qs))
      for (String part : QUERY_LIST.split(qs))
        if (!StringUtils.isBlank(part))
          res.add(QueryParam.ofPart(part));
  }

  private static String joinQueryString(List<QueryParam> parts) {
    if (parts.isEmpty())
      return "";

    StringBuilder sb = new StringBuilder();
    for (QueryParam p : parts)
      p.appendTo(sb);
    return sb.toString();
  }

  private static final Pattern QUERY_PAIR = Pattern.compile("(?<name>[^=]*)((?<sep>=)(?<value>.*))?");

  private static class QueryParam {
    private final String name;
    private final String sep;
    private final String value;

    private QueryParam(String name, String sep, String value) {
      this.name = name;
      this.sep = sep;
      this.value = value;
    }

    private void appendTo(StringBuilder sb) {
      sb.append(sb.length() == 0 ? "?" : "&").append(urlEncode(name)).append(sep).append(urlEncode(value));
    }

    public static QueryParam ofPart(String qsPart) {
      Matcher m = QUERY_PAIR.matcher(qsPart);
      if (!m.matches())
        throw new IllegalArgumentException("'" + qsPart + "': unrecognized query string");

      LOG.debug("'{}' : {}", qsPart, m.group());
      String name = urlDecode(m.group("name"));
      String sep = m.group("sep");
      String value = urlDecode(m.group("value"));
      return new QueryParam(name, sep, value);
    }

    public static QueryParam namedValue(String name, String value) {
      return new QueryParam(name, "=", value);
    }
  }

  private static String urlDecode(String s) {
    if (StringUtils.isEmpty(s))
      return "";
    try {
      return URLDecoder.decode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String urlEncode(String s) {
    if (StringUtils.isEmpty(s))
      return "";
    try {
      return URLEncoder.encode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }
}

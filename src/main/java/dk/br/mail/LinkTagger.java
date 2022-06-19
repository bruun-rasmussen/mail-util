package dk.br.mail;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  private final LinkedList<Map<String,String>> tags = new LinkedList();
  private final Set<Pattern> taggedDomains = new HashSet();

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

  public String amendQueryString(String qs, String enc) {
    // 1) Keep values of parameters already in the query:
    List<QueryParam> parts = new LinkedList();
    splitQueryString(qs, parts, enc);

    // 2) Collect tag names in first-to-last (insertion) order:
    LinkedHashSet<String> tagNames = new LinkedHashSet();
    Iterator<Map<String, String>> frames = tags.descendingIterator();
    while (frames.hasNext())
      tagNames.addAll(frames.next().keySet());

    // 3) Disregard those present already:
    for (QueryParam p : parts)
      tagNames.remove(p.name);

    // 4) Get values for each remaining tag in top-to-bottom (stacked) order:
    for (String k : tagNames)
      for (Map<String, String> frame : tags)
        if (frame.containsKey(k)) {
          parts.add(QueryParam.namedValue(k, frame.get(k)));
          break;
        }

    return joinQueryString(parts, enc);
  }

  private static final Pattern QUERY_LIST = Pattern.compile("[&?]+");

  private static void splitQueryString(String qs, List<QueryParam> res, String enc) {
    if (!StringUtils.isEmpty(qs))
      for (String part : QUERY_LIST.split(qs))
        if (!StringUtils.isBlank(part))
          res.add(QueryParam.ofPart(part, enc));
  }

  private static String joinQueryString(List<QueryParam> parts, String enc) {
    if (parts.isEmpty())
      return "";

    StringBuilder sb = new StringBuilder();
    for (QueryParam p : parts)
      p.appendTo(sb, enc);
    return sb.toString();
  }

  private static final Pattern QUERY_PAIR = Pattern.compile("(?<name>[^=]*)((?<sep>=)(?<value>.*))?");

  private static class QueryParam {
    private final String name;
    private final String sep;
    private final String value;

    private QueryParam(String name, String sep, String value) {
      if (name == null)
        throw new IllegalArgumentException();
      this.name = name;
      this.sep = sep;
      this.value = value;
    }

    private void appendTo(StringBuilder sb, String enc) {
      sb.append(sb.length() == 0 ? "?" : "&").append(urlEncode(name, enc));
      if (sep != null) {
        sb.append(sep);
        if (value != null)
          sb.append(urlEncode(value, enc));
      }
    }

    public static QueryParam ofPart(String qsPart, String enc) {
      Matcher m = QUERY_PAIR.matcher(qsPart);
      if (!m.matches())
        throw new IllegalArgumentException("'" + qsPart + "': unrecognized query string");

      String name = urlDecode(m.group("name"), enc);
      String sep = m.group("sep");
      String value = urlDecode(m.group("value"), enc);
      LOG.debug("'{}' : [{}, {}, {}]", qsPart, name, sep, value);
      return new QueryParam(name, sep, value);
    }

    public static QueryParam namedValue(String name, String value) {
      return new QueryParam(name, "=", value);
    }
  }

  private static String urlDecode(String s, String enc) {
    if (StringUtils.isEmpty(s))
      return "";
    try {
      return URLDecoder.decode(s, enc);
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String urlEncode(String s, String enc) {
    if (StringUtils.isEmpty(s))
      return "";
    try {
      return URLEncoder.encode(s, enc);
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void addTaggedDomains(String wcs) {
    for (String wc : wcs.split("\\s+")) {
      LOG.info("### [{}]: domain pattern", wc);
      String rx = wc.replaceAll("[^?*]+", "\\\\Q$0\\\\E").replaceAll("\\?", ".").replaceAll("\\*", ".*");
      taggedDomains.add(Pattern.compile(rx));
    }
  }

  private boolean isAutoTagged(String domain) {
    for (Pattern p : taggedDomains)
      if (p.matcher(domain).matches())
        return true;
    return false;
  }

  private static final Pattern HTTP_URL =
          Pattern.compile("(?<scheme>https?:)?//(?<domain>[^/]+)(?<path>/[^?&#;]*)?(?<query>\\?[^#;]*)?(?<suffix>[;#].*)?");

  public String amendHrefAddress(String address, String enc) {
    Matcher m = HTTP_URL.matcher(address);
    if (!m.matches()) {
      LOG.debug("### href=\"{}\" not a web URL", address);
      return address;
    }

    String scheme = m.group("scheme");
    String domain = m.group("domain");
    String path = m.group("path");
    String query = m.group("query");
    String suffix = m.group("suffix");

    if (!isAutoTagged(domain)) {
      LOG.debug("Page link: [{}]//[{}][{}][{}] query: [{}] unchanged", scheme, domain, path, suffix, query);
      return address;
    }

    query = amendQueryString(query, enc);

    StringBuilder url =
        new StringBuilder(scheme)
            .append("//")
            .append(domain);
    if (path != null)
        url.append(path);
    if (query != null)
        url.append(query);
    if (suffix != null)
        url.append(suffix);

    LOG.debug("### Tagged link: [{}]//[{}][{}][{}] query: [{}]", scheme, domain, path, suffix, query);
    return url.toString();
  }
}

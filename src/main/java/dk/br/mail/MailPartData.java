package dk.br.mail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.net.ssl.SSLException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author      osa
 */
public abstract class MailPartData implements MailPartSource, Serializable
{
  private final static Logger LOG = LoggerFactory.getLogger(MailPartData.class);

  protected abstract DataSource _source() throws MessagingException;

  public DataHandler getDataHandler() throws MessagingException {
    DataSource ds = _source();
    return new DataHandler(ds);
  }

  /**
   * Return a lazy-loading part source wrapping a URL for the target resource to
   * be fetched and embedded during the mail composition.
   */
  private static MailPartData remote(URL url) {
    return new RemoteHtmlResource(url);
  }

  /**
   * Fetch the target resource and return the content as a mail part source to be
   * embedded during mail composition.
   * @throws  IOException    if the content cannot be fetched
   */
  private static MailPartData local(URL url) throws IOException {
    return _read(url);
  }

  public static MailPartSource wrap(final DataSource src) {
    if (!(src instanceof Serializable))
      throw new IllegalArgumentException("must be serializable");

    return new MailPartData() {
      @Override
      protected DataSource _source() throws MessagingException
      {
        return src;
      }
    };
  }

  public static MailPartData from(String contentType, String name, byte content[]) {
    return new BinaryData(contentType, name, content);
  }


  private final static Pattern BASE64_INLINE_URL = Pattern.compile("data:(?<contentType>[^;]*)(?<encoding>;[^,]*)?,(?<payload>.*)");

  private static MailPartData inline(String dataUrl) {
    Matcher m = BASE64_INLINE_URL.matcher(dataUrl);
    if (!m.matches())
      throw new IllegalArgumentException("'" + dataUrl + "': unrecognized data: URL format");
    String contentType = m.group("contentType");
    boolean decodeBase64 = ";base64".equals(m.group("encoding"));
    String payload = m.group("payload");
    byte content[] = decodeBase64 ? Base64.decodeBase64(payload) : payload.getBytes();
    LOG.debug("[{}] {} byte(s)", contentType, content.length);
    return new BinaryData(contentType, "inline-data", content);
  }


  public static MailPartData from(String href)
        throws IOException
  {
    return from(href, null);
  }

  public static MailPartData from(String href, URL baseHref)
        throws IOException
  {
    if (href.startsWith("res:"))
    {
      URL url = MailPartData.class.getClassLoader().getResource(href.substring("res:".length()));
      return local(url);
    }
    else if (href.startsWith("file:") || href.startsWith("jar:"))
    {
      return local(new URL(href));
    }
    else if (href.startsWith("http:") || href.startsWith("https:"))
    {
      return remote(new URL(href));
    }
    else if (href.startsWith("data:"))
    {
      return inline(href);
    }
    else
    {
      if (baseHref == null)
        throw new IOException("cannot resolve '"+href+"' - no <base href=\"...\"> present");
      URL src = new URL(baseHref, href);
      try {
        LOG.debug("\"{}\" relative to \"{}\":\n\t\"{}\"", URI.create(href), baseHref, src);
        return remote(src);
      }
      catch (RuntimeException ex) {
        LOG.error("{}: {}", src, ex.getMessage());
        throw ex;
      }
    }
  }

  private static URLConnection _connect(URL url)
      throws IOException
  {
    Set<String> seen = new HashSet();

    // Loop to follow redirects:
    for (;;) {
      if (!url.getProtocol().startsWith("http"))
        return url.openConnection();

      // Check for loops and overlong chains of redirects:
      String urlText = url.toString();
      if (seen.contains(urlText))
        throw new IOException("redirect loop");
      if (seen.size() > 10)
        throw new IOException("too many redirects");
      seen.add(urlText);

      // https://stackoverflow.com/a/26046079/442782
      HttpURLConnection conn = (HttpURLConnection)url.openConnection();
      conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:45.0) Gecko/20100101 Thunderbird/45.8.0");
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(15000);
      conn.setInstanceFollowRedirects(false);

      int res;
      try {
        res = conn.getResponseCode();
      }
      catch (SSLException ex) {
        // conn.disconnect(); here?
        if ("https".equals(url.getProtocol()) && url.getPort() == -1) {
          URL retryUrl = new URL(url.toString().replaceFirst("^https:", "http:"));
          LOG.warn("{} - {}, retry {}", url, ex.getMessage(), retryUrl);
          url = retryUrl;
          continue;
        }
        throw ex;
      }

      if (res == 301 || res == 302) {
        String location = conn.getHeaderField("Location");
        LOG.info("Redirect ({} {}) {} \u9192 {}", res, conn.getResponseMessage(), url, location);
        url = new URL(url, location);  // Deal with relative URLs
        continue;
      }

      if (res == 200)
        LOG.debug("{} ({} {})", url, res, conn.getResponseMessage());
      else
        LOG.info("{} ({} {})", url, res, conn.getResponseMessage());

      return conn;
    }
  }

  private static BinaryData _read(URL url)
      throws IOException
  {
    long t1 = System.currentTimeMillis();
    LOG.debug("fetching {}", url);
    URLConnection conn = _connect(url);

    String contentType = conn.getContentType();
    String contentEncoding = conn.getContentEncoding();

    String name = url.getFile();

    // Strip ..my-file.pdf?p1=query&p2=etc... query suffix
    int qpos = name.indexOf('?');
    if (qpos > 0)
      name = name.substring(0, qpos);

    // Strip .../some/path/my-file.pdf path prefix
    int spos = name.lastIndexOf('/');
    if (spos >= 0)
      name = name.substring(spos + 1);

    InputStream is = conn.getInputStream();
    try
    {
      byte content[] = _readStream(is);
      long t2 = System.currentTimeMillis();
      LOG.debug("{}: fetched {} bytes of {} ({}ms)", name, content.length, (contentEncoding == null ? "" : "[" + contentEncoding + "]-encoded ") + contentType, t2-t1);
      return new BinaryData(contentType, name, content);
    }
    finally
    {
      is.close();
    }
  }

  private static byte[] _readStream(InputStream in)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte buf[] = new byte[8192];
    int n;
    while ((n = in.read(buf)) > 0)
      out.write(buf, 0, n);
    return out.toByteArray();
  }

  /*
   *  A lazy container for a URL data handler. URL is serializable, DataHandler is not.
   */
  private static class RemoteHtmlResource extends MailPartData
  {
    final static long serialVersionUID = 8129104633675817198L;

    private final URL m_urlSpec;

    public RemoteHtmlResource(URL url) {
      m_urlSpec = url;
    }

    @Override
    protected DataSource _source() throws MessagingException {
      // return new URLDataSource(m_urlSpec);
      String tsUrl = m_urlSpec.toString().replaceAll(Pattern.quote("$TS$"), Long.toString(System.currentTimeMillis()));
      BinaryData data;
      try
      {
        URL url = new URL(tsUrl);
        try {
          data = _read(url);
        }
        catch (IOException ex)
        {
          throw new MessagingException("failed to fetch " + url, ex);
        }
      }
      catch (MalformedURLException ex)
      {
        throw new RuntimeException(ex);
      }

      LOG.debug("attaching {}", data);
      return data._source();
    }

    @Override
    public String toString() {
      return "[content from " + m_urlSpec + "]";
    }
  }

  /*
   *  Utility class that is used to attach a PDF file object to an email.
   */
  private static class BinaryData extends MailPartData
  {
    final static long serialVersionUID = -7813275734783155287L;

    private final String m_contentType;
    private final String m_name;
    private final byte m_content[];

    public BinaryData(String contentType, String name, byte content[])
    {
      m_contentType = contentType;
      m_name = name;
      m_content = content;
    }

    public byte[] getContentBytes()
    {
      return m_content;
    }

    @Override
    public boolean equals(Object o)
    {
      if (o == this)
        return true;
      if (o == null)
        return false;
      if (!(o instanceof BinaryData))
        return false;
      BinaryData that = (BinaryData)o;
      return
             StringUtils.equals(this.m_name, that.m_name)
          && StringUtils.equals(this.m_contentType, that.m_contentType)
          && Arrays.equals(this.m_content, that.m_content);
    }

    private Integer _hash;

    @Override
    public int hashCode()
    {
      if (_hash == null)
      {
        // Where is java.util.Arrays.hashCode(Object myArray)?
        int h = m_name.hashCode() * 37 + m_contentType.hashCode();
        for (int i = 0; i < m_content.length; i++)
          h = h * 37 + m_content[i];
        _hash = h;
      }
      return _hash;
    }

    @Override
    public String toString()
    {
      return "[" + m_name + ": " + m_content.length + " bytes of " + m_contentType + "]";
    }

    protected DataSource _source()
    {
      return new DataSource() {

        public InputStream getInputStream() throws IOException
        {
          return new ByteArrayInputStream(m_content);
        }

        public OutputStream getOutputStream() throws IOException
        {
          throw new UnsupportedOperationException("not writable");
        }

        public String getContentType()
        {
          return m_contentType;
        }

        public String getName()
        {
          return StringUtils.isEmpty(m_name) ? "unknown" : m_name;
        }
      };
    }
  }
}

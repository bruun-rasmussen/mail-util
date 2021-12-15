package dk.br.mail;

import dk.br.zurb.inky.Inky;
import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

/**
 * @author     TietoEnator Consulting
 * @since      15. juni 2004
 */
public class MailMessageParser
{
  private final static Logger LOG = LoggerFactory.getLogger(MailMessageParser.class);

  private final LinkTagger tagger = new LinkTagger();

  private final String trackingHeaderName;
  private final String trackingParameterName;
  private final char trackingTokenAlphabet[];
  private final int trackingTokenLength;

  private MailMessageParser()
  {
    Properties config = new Properties();
    String cfgSpec = System.getenv("MAIL_PARSER_CONFIG");
    try {
      URL cfg =
         StringUtils.isEmpty(cfgSpec) ?
            MailMessageParser.class.getClassLoader().getResource("dk/br/mail/mail-parser-config.properties") :
              new URL(cfgSpec);
      if (cfg != null) {
        try (InputStream is = cfg.openStream()) {
          config.load(is);
          LOG.info("{} loaded", cfg);
        }
        catch (IOException ex) {
          throw new IllegalArgumentException(cfg + " " + ex.getMessage());
        }
      }
    }
    catch (MalformedURLException ex) {
      throw new IllegalArgumentException(cfgSpec + " " + ex.getMessage());
    }

    tagger.addTaggedDomains(config.getProperty("mail.tracking.domains", "localhost localhost:* *bruun-rasmussen.dk"));
    trackingHeaderName = config.getProperty("mail.tracking.header", "X-BR-Tracking-ID");
    trackingParameterName = config.getProperty("mail.tracking.parameter", "track-id");
    trackingTokenAlphabet = config.getProperty("mail.tracking.token.alphabet", "BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz").toCharArray();
    trackingTokenLength = Integer.parseInt(config.getProperty("mail.tracking.token.length", "10"));
  }

  public static MailMessageData[] parseMails(Node mailListNode)
    throws IOException
  {
    List<MailMessageData> result = new LinkedList();
    new MailMessageParser()._appendMails(mailListNode, result);
    return result.toArray(new MailMessageData[result.size()]);
  }

  private void _appendMails(Node mailListNode, List<MailMessageData> result) throws IOException
  {
    NodeList mailListNodes = mailListNode.getChildNodes();
    for (int j = 0; j < mailListNodes.getLength(); j++)
    {
      Node mailItem = mailListNodes.item(j);
      if ("email-list".equals(mailItem.getNodeName()))
      {
        LOG.debug("### parsing {}", mailItem.getNodeName());
        _appendMails(mailItem, result);
      }
      else if ("email".equals(mailItem.getNodeName()))
      {
        MailMessageData msg = tryParseMail((Element)mailItem);
        result.add(msg);
      }
      else if ("tag-domain".equals(mailItem.getNodeName()))
      {
        String wc = _text((Element)mailItem);
        tagger.addTaggedDomains(wc);
      }
      else if ("tag".equalsIgnoreCase(mailItem.getNodeName()))
      {
        digestUrlTag((Element)mailItem);
      }
      else if (mailItem.getNodeType() == Node.COMMENT_NODE)
      {
        LOG.info("\"{}\" - comment ignored", ((Comment)mailItem).getTextContent());
      }
      else if (mailItem.getNodeType() == Node.TEXT_NODE)
      {
        String text = ((Text)mailItem).getTextContent();
        if (!StringUtils.isBlank(text))
          LOG.info("\"{}\" - text ignored", text);
      }
      else
      {
        LOG.warn("unknown {}", mailItem);
      }
    }
  }

  private void digestUrlTag(Element e) {
    String name = e.getAttribute("name");
    String value = _text(e);
    tagger.put(name, value);
  }

  /**
   * Parses custom XML e-mail specification into a serializable, transportable, object.
   */
  public static MailMessageData parseMail(Element mailNode)
    throws IOException
  {
    return new MailMessageParser().tryParseMail(mailNode);
  }

  private String randomToken() {
    char tokenChars[] = new char[trackingTokenLength];
    for (int i = 0; i < trackingTokenLength; i++) {
      int randomPos = (int)(Math.random() * trackingTokenAlphabet.length);
      tokenChars[i] = trackingTokenAlphabet[randomPos];
    }
    return new String(tokenChars);
  }

  private MailMessageData tryParseMail(Element mailNode)
    throws IOException
  {
    tagger.pushFrame();
    try {
      String trackingId = mailNode.getAttribute("tracking-id");
      if (StringUtils.isEmpty(trackingId))
        trackingId = randomToken();
      tagger.put(trackingParameterName, trackingId);

      LOG.debug("###    parsing {}", mailNode.getNodeName());

      // Pass 1: read any <related>...</related> mime parts already present, if any, to have
      // them handy for <xxx src="cid:...") style references in the html part. Relevant only
      // for XML produced from marshalling an actual existing mime-message:
      Map<String,MailPartSource> related = parseRelatedParts(mailNode);

      MailMessageData msg = new MailMessageData();
      msg.setCustomHeader(trackingHeaderName, trackingId);

      NodeList mailProperties = mailNode.getChildNodes();
      for (int i = 0; i < mailProperties.getLength(); i++)
      {
        Node _node = mailProperties.item(i);
        if (_node.getNodeType() != Node.ELEMENT_NODE)
        {
          LOG.debug("{}({}) ignored", _node, _node.getNodeType());
          continue;
        }

        Element propertyNode = (Element)_node;
        String propertyName = propertyNode.getTagName();
        if ("subject".equals(propertyName))
        {
          String subject = _text(propertyNode);
          LOG.debug("subject: \"{}\"", subject);
          msg.setSubject(subject);
        }
        else if ("sent".equals(propertyName))
        {
          Date sent = _timestamp(propertyNode);
          LOG.debug("sent: {}", sent);
          msg.setSentDate(sent);
        }
        else if ("plain-body".equals(propertyName))
        {
          String body = _text(propertyNode);
          LOG.debug("text/plain body: {} characters\n{}", body.length(), body);
          msg.setPlainBody(body);
        }
        else if ("html-body".equals(propertyName))
        {
          NodeList bodyNodes = propertyNode.getChildNodes();
          HtmlPartParser parser = new HtmlPartParser();
          parser.digest(bodyNodes, msg, related);
        }
        else if ("addresses".equals(propertyName))
        {
          parseAddresses(propertyNode.getChildNodes(), msg);
        }
        else if ("header".equals(propertyName))
        {
          String name = propertyNode.getAttribute("name");
          String value = propertyNode.getAttribute("value");
          if (StringUtils.isEmpty(value))
            value = _text(propertyNode);
          LOG.debug("{}: \"{}\"", name, value);
          msg.setCustomHeader(name, value);
        }
        else if ("related".equals(propertyName))
        {
          // Do nothing
          String partId = "cid:" + propertyNode.getAttribute("id");
          LOG.debug("(seen {} : {})", partId, related.get(partId));
        }
        else if ("attachment".equals(propertyName))
        {
          String src = propertyNode.getAttribute("src");
          msg.attach(MailPartData.from(src, null));
        }
        else if ("message-id".equals(propertyName))
        {
          String messageID = _text(propertyNode);
          msg.setMessageID(messageID);
        }
        else
        {
          String value = _text(propertyNode);
          String unknown = "X-" + StringUtils.capitalize(propertyName);
          msg.setCustomHeader(unknown, value);

          LOG.warn("unknown {}: \"{}\"", unknown, value);
        }
      }
      return msg;
    }
    finally {
      tagger.popFrame();
    }
  }

  private static Map<String,MailPartSource> parseRelatedParts(Element mailNode) throws IOException
  {
    Map<String,MailPartSource> related = new HashMap();
    NodeList mailProperties = mailNode.getChildNodes();
    for (int i = 0; i < mailProperties.getLength(); i++)
    {
      Node _node = mailProperties.item(i);
      if (_node.getNodeType() != Node.ELEMENT_NODE)
      {
        LOG.debug("{}({}) ignored", _node, _node.getNodeType());
        continue;
      }

      Element tag = (Element)_node;
      String tagName = tag.getTagName();
      if ("related".equals(tagName))
      {
        String partId = "cid:" + tag.getAttribute("id");
        MailPartSource partSource = parseRelatedMimePart(tag.getChildNodes());
        related.put(partId, partSource);
        LOG.debug("save {} : {}", partId, partSource);
      }
    }
    return related;
  }

  private static MailPartData parseRelatedMimePart(NodeList partNodes)
  {
    String contentType = null;
    String name = null;
    byte content[] = null;

    for (int i = 0; i < partNodes.getLength(); i++)
    {
      Node partNode = partNodes.item(i);
      if ("type".equals(partNode.getNodeName()))
        contentType = _text((Element)partNode);
      else if ("content".equals(partNode.getNodeName()))
        content = Base64.decodeBase64(_text((Element)partNode));
    }
    return MailPartData.from(contentType, name, content);
  }

  private void parseAddresses(NodeList addressNodes, MailMessageData msg)
  {
    for (int i = 0; i < addressNodes.getLength(); i++)
    {
      Node addressNode = addressNodes.item(i);
      if (addressNode.getNodeType() == Node.ELEMENT_NODE)
        addAddress((Element)addressNode, msg);
    }
  }

  private void addAddress(Element addressElement, MailMessageData msg)
  {
    String type = addressElement.getNodeName();
    InternetAddress addr = getAddress(addressElement);
    LOG.debug("{}: [{}]", type, addr);

    if ("to".equals(type))
      msg.addRecipientTo(addr);
    else if ("cc".equals(type))
      msg.addRecipientCc(addr);
    else if ("bcc".equals(type))
      msg.addRecipientBcc(addr);
    else if ("from".equals(type))
      msg.addFrom(addr);
    else if ("reply-to".equals(type))
      msg.addReplyTo(addr);
    else if ("sender".equals(type))
      msg.setSender(addr);
    else if ("reply-to".equals(type))
      msg.addReplyTo(addr);
    else if ("bounce-to".equals(type))
      msg.setBounceAddress(addr);
    else
      LOG.error("{}: unknown address type", type);
  }

  /**
   *  Gets the address attribute of a DOM element
   *
   * @param  element  Description of the Parameter
   * @return          The address value
   */
  private InternetAddress getAddress(Element element)
  {
    Element address = (Element)element.getElementsByTagName("email-address").item(0);
    if (address == null)
      throw new IllegalArgumentException("'" + element.getTagName() + "' email-address is missing");
    String addrText = _text(address);
    if (StringUtils.isBlank(addrText))
      throw new IllegalArgumentException("'" + element.getTagName() + "' email-address is blank");

    Element personal = (Element)element.getElementsByTagName("personal").item(0);
    String personText = personal == null ? null : _text(personal);
    try
    {
      InternetAddress addr = new InternetAddress(addrText, personText);
      try {
        addr.validate();
      }
      catch (AddressException ex) {
        throw new IllegalArgumentException("'" + element.getTagName() + "' email-address unparseable - " + ex.getMessage());
      }
      return addr;
    }
    catch (UnsupportedEncodingException ex)
    {
      throw new RuntimeException(ex.getMessage());
    }
  }

  private static final Pattern CSS_URL_PATTERN = Pattern.compile("(?<before>.*url\\(['\"]?)(?<url>[^\\)'\"]+)(?<after>['\"]?\\).*)");

  private class HtmlPartParser
  {
    private final String htmlEncoding;
    private final boolean useCssInliner;

    boolean seenInky;
    URL m_baseHref;

    private HtmlPartParser() {
      htmlEncoding = System.getProperty("dk.br.mail.html-encoding", "UTF-8");
      useCssInliner = "1|yes|true".contains(System.getProperty("dk.br.mail.inline-css", "false"));
    }

    // <Source URI> -> <Part-ID> map for all resources embedded as Related MIME parts
    final Map<String,String> m_resourcePartIds = new HashMap();
    // <Source URI> -> <Binary Data> map for same
    final Map<String,MailPartSource> m_resourceContent = new HashMap();

    private void digest(NodeList bodyNodes, MailMessageData msg, Map<String,MailPartSource> related)
      throws IOException
    {
      // Initialize:
      seenInky = false;

      m_resourcePartIds.clear();
      m_resourceContent.clear();
      m_resourceContent.putAll(related);

      // Traverse HTML DOM, extracting and dereferencing external resources (style sheets,
      // images, etc.) as we go:
      digestHtmlNodeList(bodyNodes);

      LOG.debug("HTML digested: {} part ids, {} content parts", m_resourcePartIds.size(), m_resourceContent.size());

      // Serialize the modified HTML back to text:
      String bodyText = _htmlText(bodyNodes, htmlEncoding, seenInky, useCssInliner);
      msg.setHtmlBody(bodyText);

      // Attach the dereferenced resources to be included as "related" MIME parts in the
      // final result:
      LOG.debug("HTML BODY: {} characters", bodyText.length());
      for (Map.Entry<String,String> e : m_resourcePartIds.entrySet())
      {
        String ref = e.getKey();
        String partId = e.getValue();
        LOG.debug("Attach related part {} from {}", partId, ref);

        MailPartSource partSource = partFromRef(ref);
        msg.addRelatedBodyPart(partId, partSource);
      }
    }

    private void digestHtmlNodeList(NodeList children)
      throws IOException
    {
      tagger.pushFrame();
      try {
        for (int i = 0; i < children.getLength(); i++)
          digestHtmlNode(children.item(i));
      }
      finally {
        tagger.popFrame();
      }
    }

    private void digestHtmlAttributes(NamedNodeMap children)
      throws IOException
    {
      for (int i = 0; i < children.getLength(); i++)
        digestHtmlNode(children.item(i));
    }

    private void digestHtmlNode(Node node)
      throws IOException
    {
      // Replace URLs for resources to be embedded:
      String nodeName = node.getNodeName();
      short nodeType = node.getNodeType();
      if ("img".equalsIgnoreCase(nodeName) && nodeType == Node.ELEMENT_NODE)
      {
        replaceResource((Element)node, "src");
      }
      else if ("base".equalsIgnoreCase(nodeName) && nodeType == Node.ELEMENT_NODE)
      {
        String href = ((Element)node).getAttribute("href");
        try {
          m_baseHref = new URL(href);
          LOG.debug("base href: {}", m_baseHref);
        }
        catch (MalformedURLException ex) {
          LOG.error("bad <base href=\"{}\">", href, ex);
        }
      }
      else if ("link".equalsIgnoreCase(nodeName) && nodeType == Node.ELEMENT_NODE)
      {
        replaceResource((Element)node, "href");
      }
      else if ("weblink".equalsIgnoreCase(nodeName) && nodeType == Node.ELEMENT_NODE)
      {
        digestWebLink((Element)node);
      }
      else if ("style".equalsIgnoreCase(nodeName) && nodeType == Node.ATTRIBUTE_NODE)
      {
        digestStyleAttribute((Attr)node);
      }
      else if ("href".equalsIgnoreCase(nodeName) && nodeType == Node.ATTRIBUTE_NODE)
      {
        digestHrefAttribute((Attr)node);
      }
      else if (("body".equalsIgnoreCase(nodeName)
          || "table".equalsIgnoreCase(nodeName)
          || "tr".equalsIgnoreCase(nodeName)
          || "td".equalsIgnoreCase(nodeName)
          || "div".equalsIgnoreCase(nodeName)
          || "th".equalsIgnoreCase(nodeName)) && nodeType == Node.ELEMENT_NODE)
      {
        replaceResource((Element)node, "background");
      }
      else if (("row".equalsIgnoreCase(nodeName)
          || "columns".equalsIgnoreCase(nodeName)
          || "callout".equalsIgnoreCase(nodeName)
          || "container".equalsIgnoreCase(nodeName)
          || "wrapper".equalsIgnoreCase(nodeName)
          || "spacer".equalsIgnoreCase(nodeName)
          || "callout".equalsIgnoreCase(nodeName)) && nodeType == Node.ELEMENT_NODE)
      {
        seenInky = true;
      }
      else if ("include".equalsIgnoreCase(nodeName))
      {
        includeResource(node);
      }
      else if ("tag".equalsIgnoreCase(nodeName) && nodeType == Node.ELEMENT_NODE)
      {
        digestUrlTag((Element)node);
      }
      else if ("binary-content".equalsIgnoreCase(nodeName))
      {
        node.getParentNode().removeChild(node);
      }
      else if (nodeType == Node.TEXT_NODE)
      {
        digestHtmlTextNode((Text)node);
      }

      if (node.hasAttributes())
        digestHtmlAttributes(node.getAttributes());

      // Recurse:
      digestHtmlNodeList(node.getChildNodes());
    }

    private void digestHtmlTextNode(Text text)
    {
      // Do nothing right now - this could be a great place
      // for "BBCode"-style post-processing variable text.
    }

    private void digestWebLink(Element anchor)
    {
      // TODO: implement an alternative markup for <a href="{$public-root}/...">bla bla</a>,
      //       to take some burden (e.g. the root path and the Analytics-tagging) off of the
      //       stylesheets...
      // E.g.: <weblink action="search.do">
      //          <parm name="iid" value="166654" />
      //          <tag name="utm_source" value="searchagent" />
      //          <tag name="utm_term" value="wegner" />
      //            etc.
      //          <anchor>bla bla</anchor>
      //       </weblink>
      // Tag values should be inheritable from outer-level defaults

      throw new RuntimeException("not yet implemented");
    }

    private void digestStyleAttribute(Attr attr)
    {
      String oldStyle = attr.getValue();
      String newStyle = digestStyleUrls(oldStyle);
      if (!oldStyle.equals(newStyle))
      {
        LOG.debug("STYLE FIXUP: \"{}\" -> \"{}\"", oldStyle, newStyle);
        attr.setValue(newStyle);
      }
    }

    private void digestHrefAttribute(Attr attr)
    {
      String sourceHref = attr.getValue();
      String taggedHref = tagger.amendHrefAddress(sourceHref);
      if (!StringUtils.equals(sourceHref, taggedHref)) {
        attr.setValue(sourceHref);
        LOG.info("'{}' \u2192 '{}'", sourceHref, taggedHref);
      }
    }

    private String digestStyleUrls(String style)
    {
      Matcher m = CSS_URL_PATTERN.matcher(style);
      if (!m.matches())
        return style;
      return digestStyleUrls(m.group("before")) + "url(" + _cidReference(m.group("url")) + ")" + digestStyleUrls(m.group("after"));
    }

    /**
     * Function used to implement <code>&lt;img src="http://some/url.jpg" /&gt;</code> and
     *  <code>&lt;link href="http://some/url.css" /&gt;</code>. It will retrieve the resource
     * and attach it to the mailbody, and modify the referencing URL accordingly to point to
     * the attachment.
     */
    private void replaceResource(Element elem, String resourceAttribute)
      throws DOMException, IOException
    {
      Attr urlAttr = attrIgnoreCase(elem, resourceAttribute);
      String attrName = urlAttr == null ? null : urlAttr.getName();
      String urlText = urlAttr == null ? null : urlAttr.getValue();

      if (StringUtils.isEmpty(urlText))
      {
        // No source URL specified. Look for embedded <binary-content> instead
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
          Node child = children.item(i);
          if ("binary-content".equalsIgnoreCase(child.getNodeName()))
          {
            Element tag = (Element)child;
            String md5 = tag.getAttribute("md5");
            String contentType = tag.getAttribute("type");
            urlText = "mem:/" + md5;
            MailPartSource binaryContent = m_resourceContent.get(urlText);
            if (binaryContent == null)
            {
              String binaryContentBase64 = _text(tag);
              byte bytes[] = Base64.decodeBase64(binaryContentBase64.getBytes("iso-8859-1"));
              binaryContent = MailPartData.from(contentType, md5, bytes);
              m_resourceContent.put(urlText, binaryContent);
            }
            break;
          }
        }
      }

      if (StringUtils.isEmpty(urlText))
        return;

      String embed = elem.getAttribute("embed");
      if ("inline".equals(embed)) {
        // Embed resource inline as 'data:<type>;base64,<data>' encoded URL.
        // This will not work on newer GMail, Outlook, and many other webmails,
        // so don't use it (even if believed to offer a work-around for a rendering
        // bug in Apple Mail.)
        // See https://blog.mailtrap.io/2018/11/02/embedding-images-in-html-email-have-the-rules-changed/
        String inlineBase64 = inlineData(urlText);
        if (attrName != null)
          elem.removeAttribute(attrName);
        elem.setAttribute(resourceAttribute, inlineBase64);
      }
      else if (!"false".equals(embed)) {
        // Embed resource as related MIME part. Replace the URL value by
        // intra-mail 'cid:'-... reference:
        String cidRef = _cidReference(urlText);
        if (attrName != null)
          elem.removeAttribute(attrName);
        elem.setAttribute(resourceAttribute, cidRef);
      }
      // Otherwise, just leave resource URL as is

      elem.removeAttribute("embed");
    }

    private String inlineData(String urlText) throws IOException
    {
      MailPartSource src = partFromRef(urlText);
      DataHandler data;
      try {
        data = src.getDataHandler();
      }
      catch (MessagingException ex) {
        throw new IOException(urlText + " failed to load", ex);
      }
    //String name = data.getName();
      String contentType = data.getContentType();
      byte content[];
      InputStream is = data.getInputStream();
      try
      {
        content = IOUtils.toByteArray(is);
      }
      finally {
        is.close();
      }
      return "data:" + contentType + ";base64," + Base64.encodeBase64String(content);
    }

    private String _cidReference(String urlText)
    {
      // Store the resource content away in m_resources for attachment later, and
      // replace the URL-attribute by an appropriate intra-mail reference:
      String partId = m_resourcePartIds.get(urlText);
      if (partId == null)
      {
        partId = "part." + (m_resourcePartIds.size() + 1) + "." + System.currentTimeMillis() + "@mail";
        m_resourcePartIds.put(urlText, partId);
        LOG.debug("{}: resource embedded as MIME part <{}>", urlText, partId);
      }
      return "cid:" + partId;
    }

    /**
     * Special function used to implement <code>&lt;include src="http://some/url" /&gt;</code>
     */
    private void includeResource(Node node)
      throws DOMException, IOException
    {
      String urlText = ((Element)node).getAttribute("src");
      LOG.debug("{}: embedding resource", urlText);
      if (!StringUtils.isEmpty(urlText))
      {
        try
        {
          URL url = new URL(urlText);
          InputStream is = url.openStream();
          Reader rdr = new InputStreamReader(is, "iso-8859-1");
          char buf[] = new char[256];
          int count;
          StringBuilder sb = new StringBuilder();
          while ((count = rdr.read(buf)) > 0)
            sb.append(buf, 0, count);
          rdr.close();
          is.close();

          Text res = node.getOwnerDocument().createTextNode(sb.toString());
          node.getParentNode().replaceChild(res, node);
        }
        catch (MalformedURLException ex)
        {
          LOG.error(ex.getMessage(), ex);
        }
      }
    }

    private MailPartSource partFromRef(final String ref) throws IOException {
      MailPartSource part = m_resourceContent.get(ref);
      if (part == null) {
        part = MailPartData.from(ref, m_baseHref);
        m_resourceContent.put(ref, part);
      }
      return part;
    }
  }

  private static Attr attrIgnoreCase(Element elem, String attr) {
    NamedNodeMap attrMap = elem.getAttributes();
    for (int i = 0; i < attrMap.getLength(); i++) {
      Attr n = (Attr)attrMap.item(i);
      if (n.getNodeName().equalsIgnoreCase(attr))
        return n;
    }
    return null;
  }

  private final DateFormat iso8601_ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  private Date _timestamp(Element e)
  {
    String text = _text(e);
    try {
      return iso8601_ts.parse(text);
    }
    catch (java.text.ParseException ex) {
      throw new IllegalArgumentException("'" + e.getTagName() + "' unparseable date \"" + text + "\"");
    }
  }

  private static String _text(Element e)
  {
    StringBuilder result = new StringBuilder();
    NodeList children = e.getChildNodes();
    for (int i = 0; i < children.getLength(); i++)
    {
      String s = children.item(i).getNodeValue();
      if (s != null)
        result.append(s);
    }
    return result.toString();
  }

  private static Inky inky;

 /**
   * Converts an org.w3c.dom.NodeList to a Java String.
   *
   * @param  node     org.w3c.dom.Node to convert
   * @param  method   either "xml", "html", or "text" to control the formatting
   * @param  compact  whether or not to maintain line breaks and indentation. Valid
   *  for "xml" and "html" methods only.
   */
  private static String _htmlText(NodeList nodeList, String encoding, boolean useInky, boolean useCssInliner)
  {
    if (nodeList.getLength() == 0)
      return "";

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Result result = new StreamResult(out);

    try
    {
      if (useInky) {
        if (inky == null)
          inky = new Inky();
        for (int i = 0; i < nodeList.getLength(); i++) {
          Node item = nodeList.item(i);
          inky.transform(new DOMSource(item), result, useCssInliner);
        }
      }
      else {
        Transformer s = _serializer();
        for (int i = 0; i < nodeList.getLength(); i++) {
          Node item = nodeList.item(i);
          s.transform(new DOMSource(item), result);
        }
      }

      return "<!DOCTYPE html>\n" + new String(out.toByteArray(), encoding);
    }
    catch (UnsupportedEncodingException ex)
    {
      throw new RuntimeException("XML serialization error", ex);
    }
    catch (TransformerException ex)
    {
      throw new RuntimeException("XML transformation error", ex);
    }
  }

  private static Transformer _serializer()
  {
    try {
      Transformer xf = TransformerFactory.newInstance().newTransformer();
      xf.setOutputProperty(OutputKeys.METHOD, "html");
      xf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      xf.setOutputProperty(OutputKeys.INDENT, "no");
      xf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      return xf;
    }
    catch (TransformerException ex)
    {
      throw new RuntimeException("XML serialization error", ex);
    }
  }
}

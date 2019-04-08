package dk.br.mail;

import dk.br.zurb.inky.Inky;
import java.io.*;
import java.net.*;
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

  private final boolean useCssInliner;

  private MailMessageParser()
  {
    useCssInliner = "1|yes|true".contains(System.getProperty("dk.br.mail.inline-css", "false"));
  }

  private final static Pattern CSS_URL_PATTERN = Pattern.compile("(.*)url\\(([^\\)]+)\\)(.*)");

  public static MailMessageData[] parseMails(Node mailListNode)
    throws IOException
  {
    MailMessageParser parser = new MailMessageParser();

    List<MailMessageData> result = new LinkedList<MailMessageData>();
    NodeList mailListNodes = mailListNode.getChildNodes();
    for (int j = 0; j < mailListNodes.getLength(); j++)
    {
      Node mailList = mailListNodes.item(j);
      if ("email-list".equals(mailList.getNodeName()))
      {
        LOG.debug("### parsing {}", mailList.getNodeName());
        NodeList mailNodes = mailList.getChildNodes();
        for (int i = 0; i < mailNodes.getLength(); i++)
        {
          Node mailNode = mailNodes.item(i);
          if ("email".equals(mailNode.getNodeName()))
          {
            MailMessageData msg = parser.tryParseMail(mailNode);
            result.add(msg);
          }
        }
      }
      else if ("email".equals(mailList.getNodeName()))
      {
        MailMessageData msg = parser.tryParseMail(mailList);
        result.add(msg);
      }
    }

    return result.toArray(new MailMessageData[result.size()]);
  }

  /**
   * Parses custom XML e-mail specification into a serializable, transportable, object.
   */
  public static MailMessageData parseMail(Node mailNode)
    throws IOException
  {
    return new MailMessageParser().tryParseMail(mailNode);
  }

  private MailMessageData tryParseMail(Node mailNode)
    throws IOException
  {
    LOG.debug("###    parsing {}", mailNode.getNodeName());

    MailMessageData msg = new MailMessageData();

    NodeList mailProperties = mailNode.getChildNodes();
    for (int i = 0; i < mailProperties.getLength(); i++)
    {
      Node propertyNode = mailProperties.item(i);
      if (propertyNode.getNodeType() != Node.ELEMENT_NODE)
        continue;

      String propertyName = propertyNode.getNodeName();
      if ("subject".equals(propertyName))
      {
        String subject = _text(propertyNode);
        LOG.debug("SUBJECT: {}", subject);
        msg.setSubject(subject);
      }
      else if ("plain-body".equals(propertyName))
      {
        String body = _text(propertyNode);
        LOG.info("text/plain body: {} characters", body.length());
        msg.setPlainBody(body);
      }
      else if ("html-body".equals(propertyName))
      {
        NodeList bodyNodes = propertyNode.getChildNodes();
        HtmlPartParser parser = new HtmlPartParser();
        parser.digest(bodyNodes, msg);
      }
      else if ("addresses".equals(propertyName))
      {
        parseAddresses(propertyNode.getChildNodes(), msg);
      }
      else if ("header".equals(propertyName))
      {
        String name = ((Element)propertyNode).getAttribute("name");
        String value = ((Element)propertyNode).getAttribute("value");
        if (StringUtils.isEmpty(value))
          value = _text(propertyNode);
        LOG.debug("{}: {}", name, value);
        msg.setCustomHeader(name, value);
      }
      else if ("attachment".equals(propertyName))
      {
        String src = ((Element)propertyNode).getAttribute("src");
        msg.attach(MailPartData.from(src, null));
      }
      else
      {
        LOG.error("{}: invalid mail property", propertyName);
      }
    }

    return msg;
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
    LOG.debug("{} {}", type, addr);

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
    String address = _text(element.getElementsByTagName("email-address").item(0));
    if (address == null || address.equals(""))
      throw new IllegalArgumentException("email-address is missing");

    Node personal = element.getElementsByTagName("personal").item(0);
    try
    {
      InternetAddress addr = new InternetAddress(address, personal == null ? null : _text(personal));
      try {
        addr.validate();
      }
      catch (AddressException ex) {
        throw new IllegalArgumentException("unparseable email-address " + address + " - " + ex.getMessage());
      }
      return addr;
    }
    catch (UnsupportedEncodingException ex)
    {
      throw new RuntimeException(ex.getMessage());
    }
  }

  private class HtmlPartParser
  {
    boolean seenInky;
    URL m_baseHref;

    // <Source URL> -> <Part-ID> map for all resources embedded as Related MIME parts
    final Map<String,String> m_resourcePartIds = new HashMap<String,String>();
    // <Source URL> -> <Binary Data> map for same
    final Map<String,MailPartSource> m_resourceContent = new HashMap<String,MailPartSource>();

    Stack<Map<String,String>> m_tags = new Stack<Map<String,String>>();

    private void digest(NodeList bodyNodes, MailMessageData msg)
      throws IOException
    {
      // Initialize:
      seenInky = false;
      m_resourcePartIds.clear();
      m_resourceContent.clear();

      // Traverse HTML DOM, extracting and dereferencing external resources (style sheets,
      // images, etc.) as we go:
      digestHtmlNodeList(bodyNodes);

      // Serialize the modified back HTML to text:
      String bodyText = _htmlText(bodyNodes, "iso-8859-1", seenInky, useCssInliner);
      msg.setHtmlBody(bodyText);

      // Attach the dereferenced resources to be included as "related" MIME parts in the
      // final result:
      LOG.debug("HTML BODY: {} characters", bodyText.length());
      for (Map.Entry<String,String> e : m_resourcePartIds.entrySet())
      {
        String ref = e.getKey();
        String partId = e.getValue();
        LOG.info("Attach related part {} from {}", partId, ref);

        MailPartSource partSource = partFromRef(ref);
        msg.addRelatedBodyPart(partId, partSource);
      }
    }

    private void digestHtmlNodeList(NodeList children)
      throws IOException
    {
      m_tags.push(new HashMap<String, String>());
      for (int i = 0; i < children.getLength(); i++)
        digestHtmlNode(children.item(i));
      m_tags.pop().clear();
    }

    private Map<String,String> getTags() {
      Map<String,String> tags = new HashMap<String,String>(m_tags.peek());
      for (Map<String,String> t : m_tags)
        for (Map.Entry<String, String> s : t.entrySet())
          tags.put(s.getKey(), s.getValue());
      return tags;
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
        String address = ((Attr)node).getValue();
        LOG.info("href=\"{}\" (tags: {})", address, getTags());
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
      else if ("tag".equalsIgnoreCase(nodeName))
      {
        String name = ((Element)node).getAttribute("name");
        String value = ((Element)node).getAttribute("value");
        String was = m_tags.peek().put(name, value);
        LOG.info("TAG {}=\"{}\" (was {})", name, value, was);
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
      if (LOG.isDebugEnabled())
        LOG.debug("### '{}'", text.getData());
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
        if (LOG.isDebugEnabled())
          LOG.debug("### STYLE FIXUP: \"{}\" -> \"{}\"", oldStyle, newStyle);
        attr.setValue(newStyle);
      }
    }

    private String digestStyleUrls(String style)
    {
      Matcher m = CSS_URL_PATTERN.matcher(style);
      if (!m.matches())
        return style;
      return digestStyleUrls(m.group(1)) + "url(" + cidReference(m.group(2)) + ")" + digestStyleUrls(m.group(3));
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
      String urlText = elem.getAttribute(resourceAttribute);

      if (StringUtils.isEmpty(urlText))
      {
        // No source URL specified. Look for embedded <binary-content> instead
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
          Node child = children.item(i);
          if ("binary-content".equalsIgnoreCase(child.getNodeName()))
          {
            String md5 = ((Element)child).getAttribute("md5");
            String contentType = ((Element)child).getAttribute("type");
            urlText = "mem:/" + md5;
            MailPartSource binaryContent = m_resourceContent.get(urlText);
            if (binaryContent == null)
            {
              String binaryContentBase64 = _text(child);
              byte bytes[] = Base64.decodeBase64(binaryContentBase64.getBytes());
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
        // Embed resource inline as 'data:<type>;base64,<data>' encoded URL:
        String inlineBase64 = inlineData(urlText);
        elem.setAttribute(resourceAttribute, inlineBase64);
      }
      else if (!"false".equals(embed)) {
        // Embed resource as related MIME part. Replace the URL value by
        // intra-mail 'cid:'-... reference:
        String cidRef = cidReference(urlText);
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

    private String cidReference(String urlText)
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

    private MailPartSource partFromRef(String ref) throws IOException {
      MailPartSource part = m_resourceContent.get(ref);
      if (part == null) {
        part = MailPartData.from(ref, m_baseHref);
        m_resourceContent.put(ref, part);
      }
      return part;
    }
  }

  private static String _text(Node n)
  {
    StringBuilder result = new StringBuilder();
    NodeList children = n.getChildNodes();
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

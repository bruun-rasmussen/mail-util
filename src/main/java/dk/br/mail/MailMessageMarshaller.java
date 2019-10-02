package dk.br.mail;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author osa
 */
public class MailMessageMarshaller
{
  private static final Logger LOG = LoggerFactory.getLogger(MailMessageMarshaller.class);

  public static Document marshal(Message msg) throws MessagingException
  {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db;
    dbf.setNamespaceAware(true);
    try
    {
      db = dbf.newDocumentBuilder();
    }
    catch (ParserConfigurationException ex)
    {
      throw new RuntimeException(ex);
    }

    Document doc = db.newDocument();
    Element e = doc.createElement("email");
    new MailMessageMarshaller().digestMail(e, msg);
    doc.appendChild(e);
    return doc;
  }

  private MailMessageMarshaller()
  {
  }

  private final static DateFormat ISO8601_TS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  private void digestMail(Element m, Message msg) throws MessagingException
  {
    for (String mid : msg.getHeader("Message-ID"))
      _addText(m, "message-id", mid);
    _addDate(m, "sent", msg.getSentDate());
    _addDate(m, "received", msg.getReceivedDate());

    _addText(m, "subject", msg.getSubject());
    Element addrs = _addElement(m, "addresses");
    for (Address a : _NVL(msg.getFrom()))
      _addAddress(addrs, "from", (InternetAddress)a);
    for (Address a : _NVL(msg.getRecipients(Message.RecipientType.TO)))
      _addAddress(addrs, "to", (InternetAddress)a);
    for (Address a : _NVL(msg.getRecipients(Message.RecipientType.CC)))
      _addAddress(addrs, "cc", (InternetAddress)a);
    for (Address a : _NVL(msg.getReplyTo()))
      _addAddress(addrs, "reply-to", (InternetAddress)a);

    try
    {
      _digestMimePart(m, msg);
    }
    catch (IOException ex)
    {
      throw new RuntimeException(ex);
    }
  }

  private void _digestMimePart(Element m, Part p) throws MessagingException, IOException
  {
    if (p.isMimeType("text/html"))
    {
      String htmlBody = _CRLF((String)p.getContent());
      Document htmlDoc = _parseHtml(htmlBody);
      Node htmlNode = m.getOwnerDocument().importNode(htmlDoc.getDocumentElement(), true);
      _addElement(m, "html-body").appendChild(htmlNode);
    }
    else if (p.isMimeType("text/plain"))
    {
      String plainbody = _CRLF((String)p.getContent());
      _addText(m, "plain-body", plainbody);
    }
    else if (p.isMimeType("multipart/alternative"))
    {
      MimeMultipart mm = (MimeMultipart)p.getContent();
      for (int i = 0; i < mm.getCount(); i++)
        _digestMimePart(m, mm.getBodyPart(i));
    }
    else if (p.isMimeType("multipart/related"))
    {
      MimeMultipart mm = (MimeMultipart)p.getContent();
      for (int i = 0; i < mm.getCount(); i++)
        _digestMimePart(m, mm.getBodyPart(i));
    }
    else if (p.isMimeType("multipart/mixed"))
    {
      MimeMultipart mm = (MimeMultipart)p.getContent();
      for (int i = 0; i < mm.getCount(); i++)
        _digestMimePart(m, mm.getBodyPart(i));
    }
    else
    {
      LOG.info("'{}' ignored", p.getContentType());
    }
  }

  // Naive method to normalize any line-ending convention, be it '<CR>',
  // '<CR>+<LF>', '<LF>+<CR>', into straight '<LF>'.
  private static String _CRLF(String src)
  {
    StringBuilder res = new StringBuilder();
    int cr = 0;
    int lf = 0;
    for (int i = 0; i < src.length(); i++)
    {
      char ch = src.charAt(i);
      if (ch == '\r')
      {
        ++cr;
      }
      else if (ch == '\n')
      {
        ++lf;
      }
      else
      {
        if (cr > 0)
        {
          if (lf == 0)
            lf = cr;
          cr = 0;
        }
        while (lf > 0)
        {
          res.append('\n');
          lf--;
        }
        res.append(ch);
      }
    }

    if (cr > 0)
    {
      if (lf == 0)
        lf = cr;
      cr = 0;
    }
    while (lf > 0)
    {
      res.append('\n');
      lf--;
    }

    return res.toString();
  }

  private static final Address NO_ADDRESSES[] = new Address[0];

  private static Address[] _NVL(Address src[])
  {
    // Silly null-sanity check, as javax.mail.Message.getRecipients() returns null
    // instead of empty arrays...
    return src == null ? NO_ADDRESSES : src;
  }

  private static Element _addElement(Node parentNode, String elementName)
  {
    Element element;
    Document doc = parentNode.getOwnerDocument();
    try
    {
      element = doc.createElement(elementName);
    }
    catch (DOMException ex)
    {
      LOG.error("cannot create element <{}>", elementName, ex);

      element = doc.createElement("element");
      element.setAttribute("invalid-name", elementName);
      element.setAttribute("error-message", ex.getMessage());
    }

    try
    {
      parentNode.appendChild(element);
    }
    catch (DOMException ex)
    {
      LOG.error("cannot add element <{}> to <{}>", elementName, parentNode.getNodeName(), ex);
    }

    return element;
  }

  private static void _addText(Node parentNode, String elementName, String text)
  {
    if (StringUtils.isBlank(text))
      return;
    Element e = _addElement(parentNode, elementName);
    e.appendChild(e.getOwnerDocument().createTextNode(text));
  }

  private static void _addDate(Node parentNode, String elementName, Date d)
  {
    if (d == null)
      return;
    _addText(parentNode, elementName, ISO8601_TS.format(d));
  }

  private static void _addAddress(Node parentNode, String type,
          InternetAddress a)
  {
    Element n = _addElement(parentNode, type);

    String personal = a.getPersonal();
    _addText(n, "personal", personal);
    _addText(n, "email-address", a.getAddress());
  }

  private static Document _parseHtml(String html)
  {
    org.jsoup.nodes.Document soupDoc = org.jsoup.Jsoup.parse(html);
    try {
      W3CDom domParser = new org.jsoup.helper.W3CDom();
      return domParser.fromJsoup(soupDoc);
    }
    catch (DOMException ex) {
      try {
        File f = File.createTempFile("invalid-", ".html");
        PrintWriter pw = new PrintWriter(f, "UTF-8");
        pw.println(html);
        pw.close();
        LOG.error("unparseable HTML - see {}", f.getAbsolutePath(), ex);
      }
      catch (IOException io) {
        throw new RuntimeException(io);
      }
      throw ex;
    }
  }
}

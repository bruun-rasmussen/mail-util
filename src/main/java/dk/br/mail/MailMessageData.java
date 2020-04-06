package dk.br.mail;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A serializable, transportable representation of an e-mail message. The <em>transportable</em>
 * part means that instances will never include references to "local" files or directories.
 *
 * @author     TietoEnator Consulting
 * @since      21. november 2003
 */
public final class MailMessageData
     implements Serializable, MailMessageSource
{
  private final static Logger LOG = LoggerFactory.getLogger(MailMessageData.class);

  private static final long serialVersionUID = 3478570608187099960L;

  private final static String MIME_TYPE_HTML = "text/html";
  private final static String MIME_TYPE_PLAIN = "text/plain; charset=UTF-8; format=flowed";

  private InternetAddress m_sender;
  private InternetAddress m_bounceTo;
  private final List<InternetAddress> m_from = new LinkedList<InternetAddress>();
  private final List<InternetAddress> m_replyTo = new LinkedList<InternetAddress>();
  private final List<InternetAddress> m_recipientsTo = new LinkedList<InternetAddress>();
  private final List<InternetAddress> m_recipientsCc = new LinkedList<InternetAddress>();
  private final List<InternetAddress> m_recipientsBcc = new LinkedList<InternetAddress>();
  private String m_messageID;
  private Date m_sentDate = new Date();
  private String m_subject = "(no subject)";
  private String m_plainText;
  private String m_htmlText;
  private MailPartSource m_alternative;
  private final Map<String,MailPartSource> m_relatedBodyParts = new HashMap<String,MailPartSource>();
  private final Map<String,String> m_customHeaders = new HashMap<String,String>();
  private final List<MailPartSource> m_attachments = new LinkedList<MailPartSource>();

  @Override
  public void setCustomHeader(String name, String value)
  {
    m_customHeaders.put(name, value);
  }

  public void addReplyTo(InternetAddress addresses[])
  {
    m_replyTo.addAll(Arrays.asList(addresses));
  }

  public void addReplyTo(InternetAddress address)
  {
    m_replyTo.add(address);
  }

  public void addReplyTo(String name, String email)
  {
    addReplyTo(encodeAddress(name, email));
  }

  public String getSubject()
  {
    return m_subject;
  }

  public void setSubject(String subject)
  {
    m_subject = subject;
  }

  public String getMessageID()
  {
    return m_messageID;
  }

  public void setMessageID(String messageID)
  {
    m_messageID = messageID;
  }

  public void setSentDate(Date date)
  {
    m_sentDate = date;
  }

  public void setFrom(InternetAddress address)
  {
    m_from.clear();
    addFrom(address);
  }

  public void setFrom(String name, String email)
  {
    setFrom(encodeAddress(name, email));
  }

  public void addFrom(InternetAddress address)
  {
    m_from.add(address);
  }

  public List<InternetAddress> getFrom()
  {
    return Collections.unmodifiableList(m_from);
  }

  public void setSender(InternetAddress address)
  {
    m_sender = address;
  }

  public void setBounceAddress(InternetAddress address)
  {
    m_bounceTo = address;
  }

  @Override
  public InternetAddress getBounceAddress()
  {
    return m_bounceTo;
  }

  public String getPlainBody()
  {
    return m_plainText;
  }

  public void setPlainBody(String text)
  {
    // Add body part:
    m_plainText = text;
  }

  public void addRelatedBodyPart(String partId, MailPartSource res)
  {
    m_relatedBodyParts.put(partId, res);
  }

  public String getHtmlBody()
  {
    return m_htmlText;
  }

  public void setHtmlBody(String text)
  {
    m_htmlText = text;
  }

  public void setAlternativeBody(MailPartSource altBody)
  {
    m_alternative = altBody;
  }

  public void addRecipientTo(InternetAddress address)
  {
    m_recipientsTo.add(address);
  }

  public void addRecipientTo(String name, String email)
  {
    addRecipientTo(encodeAddress(name, email));
  }

  public void addRecipientsTo(InternetAddress addresses[])
  {
    m_recipientsTo.addAll(Arrays.asList(addresses));
  }

  public void addRecipientCc(InternetAddress address)
  {
    m_recipientsCc.add(address);
  }

  public void addRecipientCc(String name, String email)
  {
    addRecipientCc(encodeAddress(name, email));
  }

  public void addRecipientsCc(InternetAddress addresses[])
  {
    m_recipientsCc.addAll(Arrays.asList(addresses));
  }

  public void addRecipientBcc(InternetAddress address)
  {
    m_recipientsBcc.add(address);
  }

  public void addRecipientBcc(String name, String email)
  {
    addRecipientBcc(encodeAddress(name, email));
  }

  public void addRecipientsBcc(InternetAddress addresses[])
  {
    m_recipientsBcc.addAll(Arrays.asList(addresses));
  }

  private static Address[] toAddressArray(List<InternetAddress> addresses)
  {
    return addresses.toArray(new Address[addresses.size()]);
  }

  public void attach(MailPartSource attachment)
  {
    m_attachments.add(attachment);
  }

  public void attach(String contentType, String name, byte content[])
  {
    attach(MailPartData.from(contentType, name, content));
  }

  public void attach(DataSource ds)
  {
    attach(MailPartData.wrap(ds));
  }

  /**
   * Produces the MIME message.
   * @param failsafe    if set, will produce and return an incomplete message even if
   *                    some related resources cannot be retrieved, e.g. non-existing
   *                    images referenced in HTML &lt;img href="http://...."&gt;
   *                    if not set, this will cause
   */
  @Override
  public MimeMessage compose(Session session, boolean failsafe)
    throws MessagingException
  {
    return new Composer(failsafe).compose(session);
  }

  private class Composer {
    private final boolean failsafe;

    public Composer(boolean failsafe) {
      this.failsafe = failsafe;
    }

    /**
     * Produces the MIME message. There are three cases: the message can 1) have a
     * Plain Text part, 2) an HTML Text part, or 3) both. If it has both, then the
     * two parts get wrapped in an "alternative"-type multipart container.
     */
    public MimeMessage compose(Session session)
      throws MessagingException
    {
      MimeMessage message = new MimeMessage(session);

      message.setSentDate(m_sentDate);
      message.addFrom(toAddressArray(m_from));
      if (m_sender != null)
      {
        message.setSender(m_sender);
        // Fixup the envelope sender address here
      }
      message.setReplyTo(toAddressArray(m_replyTo));
      message.addRecipients(Message.RecipientType.TO, toAddressArray(m_recipientsTo));
      message.addRecipients(Message.RecipientType.CC, toAddressArray(m_recipientsCc));
      message.addRecipients(Message.RecipientType.BCC, toAddressArray(m_recipientsBcc));
      if (m_subject != null)
        message.setSubject(m_subject, "UTF-8");

      for (Map.Entry<String, String> e : m_customHeaders.entrySet())
        message.addHeader(e.getKey(), e.getValue());

      composeMessageTo(message);

      return message;
    }

    private void composeMessageTo(Part target)
      throws MessagingException
    {
      // TODO: add S/MIME signature, sending a "multipart/signed", e.g. using
      //       code from http://www.isnetworks.com/

      if (m_attachments.isEmpty())
      {
        // No attchments, compose body part(s) at top level:
        composeBodyTo(target);
      }
      else
      {
        // One or more attachments to add. Wrap body as first element
        // of a mixed/multipart message:
        MimeMultipart mixedPart = new MimeMultipart("mixed");

        MimeBodyPart bodyPart = new MimeBodyPart();
        composeBodyTo(bodyPart);
        mixedPart.addBodyPart(bodyPart);

        // ... and add attachment(s) to it:
        for (MailPartSource data : m_attachments)
        {
          BodyPart attachment = composeAttachment(data);
          mixedPart.addBodyPart(attachment);
        }

        target.setContent(mixedPart);
      }
    }

    private BodyPart composeAttachment(MailPartSource src)
      throws MessagingException
    {
      try
      {
        MimeBodyPart attachment = new MimeBodyPart();
        DataHandler dh = src.getDataHandler();
        attachment.setDataHandler(dh);
        attachment.setFileName(dh.getName());
        attachment.setDisposition(Part.ATTACHMENT);
        return attachment;
      }
      catch (MessagingException ex)
      {
        if (!failsafe)
          throw ex;

        LOG.error("failed to compose attachment {}", src, ex);

        // Giving up. Attach error message instead:
        StringWriter w = new StringWriter();
        PrintWriter text = new PrintWriter(w);
        text.println("# ========");
        text.println("# Failed to compose attachment " + src);
        text.println("# Server stack trace follows:");
        ex.printStackTrace(text);
        text.println("# ========");

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setText(w.toString());
        attachment.setDisposition(Part.ATTACHMENT);
        return attachment;
      }
    }

    private void composeBodyTo(Part target)
      throws MessagingException
    {
      if (m_htmlText == null && m_plainText == null && m_alternative == null)
        // Make sure we have some HTML and/or Plain text body stuff:
        m_plainText = "";

      if (m_htmlText == null && m_alternative == null)
      {
        // No HTML. Put plain/text at top level:
        composePlainBodyTo(target);
      }
      else if (m_plainText == null && m_alternative == null)
      {
        // Not Plain text. Put HTML at top level:
        composeHtmlBodyTo(target);
      }
      else if (m_htmlText == null && m_plainText == null)
      {
        // No text. Put alternative part at top level:
        composeAlternativeTo(target);
      }
      else
      {
        // Both parts present. Wrap in altenative/multipart message:
        MimeMultipart alternatives = new MimeMultipart("alternative");

        if (m_plainText != null) {
          BodyPart plainPart = new MimeBodyPart();
          plainPart.setContent(m_plainText, MailMessageData.MIME_TYPE_PLAIN);
          alternatives.addBodyPart(plainPart);
        }

        if (m_htmlText != null) {
          BodyPart htmlPart = new MimeBodyPart();
          composeHtmlBodyTo(htmlPart);
          alternatives.addBodyPart(htmlPart);
        }

        if (m_alternative != null) {
          BodyPart altPart = new MimeBodyPart();
          composeAlternativeTo(altPart);
          alternatives.addBodyPart(altPart);
        }

        target.setContent(alternatives);
      }
    }

    /**
     * Generates the Plain/text MIME part.
     */
    private void composePlainBodyTo(Part target)
      throws MessagingException
    {
      target.setContent(m_plainText, MailMessageData.MIME_TYPE_PLAIN);
    }

    /**
     * Generates an alternative MIME part.
     */
    private void composeAlternativeTo(Part target)
      throws MessagingException
    {
      DataHandler dh = m_alternative.getDataHandler();
      target.setDataHandler(dh);
    }

    /**
     * Generates the HTML MIME part. There are two cases: either the HTML uses resources
     * (images, stylesheets) or it doesn't. If it does, the HTML part and its list of resources
     * will be wrapped in a "related"-type multipart container.
     */
    private void composeHtmlBodyTo(Part target)
      throws MessagingException
    {
      if (m_htmlText == null)
        return;

      Iterator relatedParts = m_relatedBodyParts.entrySet().iterator();
      if (!relatedParts.hasNext())
      {
        // No embedded html resources. Save a "flat" text/html body:
        target.setContent(m_htmlText, MailMessageData.MIME_TYPE_HTML);
        return;
      }

      // HTML part has one or more related parts associated:
      MimeMultipart related = new MimeMultipart("related");
      BodyPart htmlPart = new MimeBodyPart();
      htmlPart.setContent(m_htmlText, MailMessageData.MIME_TYPE_HTML);
      related.addBodyPart(htmlPart);
      do
      {
        Map.Entry e = (Map.Entry)relatedParts.next();
        String partId = (String)e.getKey();
        MailPartSource res = (MailPartSource)e.getValue();
        LOG.debug("attaching related MIME-part <{}>: {}", partId, res);

        BodyPart relatedBodyPart = new MimeBodyPart();
        try {
          DataHandler dh = res.getDataHandler();
          relatedBodyPart.setDataHandler(dh);
          relatedBodyPart.setHeader("Content-ID", "<" + partId + ">");
          relatedBodyPart.setDisposition(Part.INLINE);
          related.addBodyPart(relatedBodyPart);
        }
        catch (MessagingException ex) {
          if (failsafe) {
            LOG.error("failed to attach MIME-part <{}>: {}", partId, res, ex);
          }
          else {
            LOG.warn("{} - failed to attach MIME-part <{}>: {}", ex.getMessage(), partId, res);
            throw ex;
          }
        }
      } while (relatedParts.hasNext());

      target.setContent(related);
    }

  }


  @Override
  public InternetAddress getFirstRecipient()
  {
    return !m_recipientsTo.isEmpty() ? m_recipientsTo.get(0) :
            !m_recipientsCc.isEmpty() ? m_recipientsCc.get(0) :
            !m_recipientsBcc.isEmpty() ? m_recipientsBcc.get(0) : null;
  }

  @Override
  public String toString()
  {
    return "[" + getFirstRecipient() + ": \"" + m_subject + "\"]";
  }

  private static InternetAddress encodeAddress(String name, String email)
  {
    try {
      return new InternetAddress(email, name, "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      // Mustn't happen
      throw new RuntimeException(ex);
    }
  }

  public static MailMessageData from(Message msg) throws MessagingException, IOException {
    MailMessageData d = new MailMessageData();
    d._readMessage(msg);
    return d;
  }

  private void _readMessage(Message msg) throws MessagingException, IOException {
    setSubject(msg.getSubject());
    setSentDate(msg.getSentDate());
    for (Address a : _NVL(msg.getFrom()))
      addFrom((InternetAddress)a);
    for (Address a : _NVL(msg.getRecipients(Message.RecipientType.TO)))
      addRecipientTo((InternetAddress)a);
    for (Address a : _NVL(msg.getRecipients(Message.RecipientType.CC)))
      addRecipientCc((InternetAddress)a);
    for (Address a : _NVL(msg.getReplyTo()))
      addReplyTo((InternetAddress)a);

    _readPart(msg);
  }

  private void _readPart(Part p) throws MessagingException, IOException {
    if (p.isMimeType("text/html")) {
      setHtmlBody(_CRLF((String)p.getContent()));
    }
    else if (p.isMimeType("text/plain")) {
      setPlainBody(_CRLF((String)p.getContent()));
    }
    else if (p.isMimeType("multipart/alternative")) {
      MimeMultipart mm = (MimeMultipart)p.getContent();
      for (int i = 0; i < mm.getCount(); i++)
        _readPart(mm.getBodyPart(i));
    }
    else {
      LOG.info("'{}' ignored", p.getContentType());
    }
  }

  // Naive method to normalize any line-ending convention, be it '<CR>',
  // '<CR>+<LF>', '<LF>+<CR>', into straight '<LF>'.
  private static String _CRLF(String src) {
    StringBuilder res = new StringBuilder();
    int cr = 0;
    int lf = 0;
    for (int i = 0; i < src.length(); i++) {
      char ch = src.charAt(i);
      if (ch == '\r') {
        ++cr;
      }
      else if (ch == '\n') {
        ++lf;
      }
      else {
        if (cr > 0) {
          if (lf == 0)
            lf = cr;
          cr = 0;
        }
        while (lf > 0) {
          res.append('\n');
          lf--;
        }
        res.append(ch);
      }
    }

    if (cr > 0) {
      if (lf == 0)
        lf = cr;
      cr = 0;
    }
    while (lf > 0) {
      res.append('\n');
      lf--;
    }

    return res.toString();
  }

  private static final Address NO_ADDRESSES[] = new Address[0];

  private static Address[] _NVL(Address src[]) {
    // Silly null-sanity check, as javax.mail.Message.getRecipients() returns null
    // instead of empty arrays...
    return src == null ? NO_ADDRESSES : src;
  }
}

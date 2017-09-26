package dk.br.mail;

import dk.br.mail.MailMessageSource;
import dk.br.mail.MailPartSource;
import java.io.*;
import java.util.*;
import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A serializable, transportable representation of an e-mail message. The <em>transportable</em> part means that
 * instances will never include references to "local" files or directories.
 *
 * @author     TietoEnator Consulting
 * @since      21. november 2003
 * @version    $Id$
 */
public final class MailMessageData
     implements java.io.Serializable, MailMessageSource
{
  private final static Logger LOG = LoggerFactory.getLogger(MailMessageData.class);

  final static long serialVersionUID = -7000283573789414687L;

  private final static String MIME_TYPE_HTML = "text/html";
  private final static String MIME_TYPE_PLAIN = "text/plain; charset=UTF-8; format=flowed";

  private Address m_sender;
  private Address m_bounceTo;
  private final List m_from = new LinkedList();
  private final List m_replyTo = new LinkedList();
  private final List m_recipientsTo = new LinkedList();
  private final List m_recipientsCc = new LinkedList();
  private final List m_recipientsBcc = new LinkedList();
  private Date m_sentDate = new Date();
  private String m_subject = "(no subject)";
  private String m_plainText;
  private String m_htmlText;
  private final Map m_relatedBodyParts = new HashMap();
  private final Map m_customHeaders = new HashMap();
  private final List m_attachments = new LinkedList();

  public void setCustomHeader(String name, String value)
  {
    m_customHeaders.put(name, value);
  }

  public void addReplyTo(Address addresses[])
  {
    m_replyTo.addAll(Arrays.asList(addresses));
  }

  public void addReplyTo(Address address)
  {
    m_replyTo.add(address);
  }

  public void setSubject(String subject)
  {
    m_subject = subject;
  }

  public void setSentDate(Date date)
  {
    m_sentDate = date;
  }

  public void setFrom(Address address)
  {
    m_from.clear();
    addFrom(address);
  }

  public void setFrom(String name, String email)
  {
    setFrom(encodeAddress(name, email));
  }

  public void addFrom(Address address)
  {
    m_from.add(address);
  }

  public void setSender(Address address)
  {
    m_sender = address;
  }

  public void setBounceAddress(Address address)
  {
    m_bounceTo = address;
  }

  public Address getBounceAddress()
  {
    return m_bounceTo;
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

  public void setHtmlBody(String text)
  {
    m_htmlText = text;
  }

  public void addRecipientTo(Address address)
  {
    m_recipientsTo.add(address);
  }

  public void addRecipientTo(String name, String email)
  {
    addRecipientTo(encodeAddress(name, email));
  }

  public void addRecipientsTo(Address addresses[])
  {
    m_recipientsTo.addAll(Arrays.asList(addresses));
  }

  public void addRecipientCc(Address address)
  {
    m_recipientsCc.add(address);
  }

  public void addRecipientCc(String name, String email)
  {
    addRecipientCc(encodeAddress(name, email));
  }

  public void addRecipientsCc(Address addresses[])
  {
    m_recipientsCc.addAll(Arrays.asList(addresses));
  }

  public void addRecipientBcc(Address address)
  {
    m_recipientsBcc.add(address);
  }

  public void addRecipientBcc(String name, String email)
  {
    addRecipientBcc(encodeAddress(name, email));
  }

  public void addRecipientsBcc(Address addresses[])
  {
    m_recipientsBcc.addAll(Arrays.asList(addresses));
  }

  private static Address[] toAddressArray(List addresses)
  {
    return (Address[])addresses.toArray(new Address[addresses.size()]);
  }

  public void attach(MailPartSource attachment)
  {
    m_attachments.add(attachment);
  }

  /**
   * Produces the MIME message.
   * @param failsafe    if set, will produce and return an incomplete message even if
   *                    some related resources cannot be retrieved, e.g. non-existing
   *                    images referenced in HTML &lt;img href="http://...."&gt; 
   *                    if not set, this will cause 
   */
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

      Iterator headers = m_customHeaders.entrySet().iterator();
      while (headers.hasNext())
      {
        Map.Entry e = (Map.Entry)headers.next();
        message.addHeader((String)e.getKey(), (String)e.getValue());
      }

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
        Iterator i = m_attachments.iterator();
        while (i.hasNext())
        {
          MailPartSource data = (MailPartSource)i.next();
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
        
        LOG.error("failed to compose attachment " + src, ex);

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
      if (m_htmlText == null && m_plainText == null)
        // Make sure we have some HTML and/or Plain text body stuff:
        m_plainText = "";

      if (m_htmlText == null)
      {
        // No HTML. Put plain/text at top level:
        composePlainBodyTo(target);
      }
      else if (m_plainText == null)
      {
        // Not Plain text. Put HTML at top level:
        composeHtmlBodyTo(target);
      }
      else
      {
        // Both parts present. Wrap in altenative/multipart message:
        MimeMultipart alternatives = new MimeMultipart("alternative");

        BodyPart plainPart = new MimeBodyPart();
        plainPart.setContent(m_plainText, MailMessageData.MIME_TYPE_PLAIN);
        alternatives.addBodyPart(plainPart);

        BodyPart htmlPart = new MimeBodyPart();
        composeHtmlBodyTo(htmlPart);
        alternatives.addBodyPart(htmlPart);

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
        LOG.debug("attaching related MIME-part <" + partId + ">: " + res);

        BodyPart relatedBodyPart = new MimeBodyPart();
        try {
          DataHandler dh = res.getDataHandler();
          relatedBodyPart.setDataHandler(dh);
          relatedBodyPart.setHeader("Content-ID", "<" + partId + ">");
          related.addBodyPart(relatedBodyPart);
        }
        catch (MessagingException ex) {
          if (failsafe) {
            LOG.error("failed to attach MIME-part <" + partId + ">: " + res, ex);
          }
          else {
            LOG.warn(ex.getMessage() + " - failed to attach MIME-part <" + partId + ">: " + res);
            throw ex;
          }
        }
      } while (relatedParts.hasNext());

      target.setContent(related);
    }

  }


  public Address getFirstRecipient()
  {
    return !m_recipientsTo.isEmpty() ? (Address)m_recipientsTo.get(0) :
            !m_recipientsCc.isEmpty() ? (Address)m_recipientsCc.get(0) :
            !m_recipientsBcc.isEmpty() ? (Address)m_recipientsBcc.get(0) : null;
  }

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
}

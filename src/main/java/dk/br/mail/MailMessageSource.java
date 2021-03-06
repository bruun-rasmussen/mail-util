package dk.br.mail;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * @author     Bruun Rasmussen Kunstauktioner
 * @since      12. maj 2004
 */
public interface MailMessageSource
{
  void setCustomHeader(String name, String value);

  MimeMessage compose(Session session, boolean failsafe)
    throws MessagingException;

  String getSubject();
  InternetAddress getFirstRecipient();
  InternetAddress getBounceAddress();
}

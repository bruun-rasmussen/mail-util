package dk.br.mail;

import java.util.Date;
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
  String getCustomHeader(String name);
  void setCustomHeader(String name, String value);

  MimeMessage compose(Session session, boolean failsafe) throws MessagingException;

  String getSubject();
  Date getSentDate();
  InternetAddress getFirstSender();
  InternetAddress getFirstRecipient();
  InternetAddress getBounceAddress();
}

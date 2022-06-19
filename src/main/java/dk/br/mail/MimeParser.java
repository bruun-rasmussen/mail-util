package dk.br.mail;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/**
 * @author osa
 */
public class MimeParser
{
  public static MimeMessage parseMimeMessage(String msg)
  {
    // Create a dummy session to use for Mime parsing:
    Session session = Session.getInstance(new Properties(), null);
    try {
      return new MimeMessage(session, new ByteArrayInputStream(msg.getBytes("UTF-8")));
    }
    catch (MessagingException ex) {
      throw new RuntimeException("Unparseable - " + ex.getMessage());
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }
}

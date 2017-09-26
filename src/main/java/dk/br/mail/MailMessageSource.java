package dk.br.mail;

import javax.mail.*;
import javax.mail.internet.MimeMessage;

/**
 * @author     Bruun Rasmussen Kunstauktioner
 * @since      12. maj 2004
 * @version    $Id$
 */
public interface MailMessageSource
{
  void setCustomHeader(String name, String value);

  MimeMessage compose(Session session, boolean failsafe)
    throws MessagingException;

  Address getFirstRecipient();
  Address getBounceAddress();
}

package dk.br.mail;

import javax.activation.DataHandler;
import javax.mail.MessagingException;

/**
 * @author osa
 */
public interface MailPartSource
{
  DataHandler getDataHandler() throws MessagingException;
}

package dk.br.mail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;

import static org.junit.Assert.*;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author osa
 */
public class SendTest
{
  private final static Logger LOG = LoggerFactory.getLogger(SendTest.class);

  @Test
  public void testThis() throws IOException, TransformerException, MessagingException {
    URL htmlSrcUrl = getClass().getClassLoader().getResource("dk/br/zurb/mail/br_soegeagent.html");
    Document html = htmlSouped(htmlSrcUrl);

    URL mailXsl = getClass().getClassLoader().getResource("dk/br/mail/send-test.xsl");
    TransformerFactory xf = TransformerFactory.newInstance();

    Transformer toMail = xf.newTransformer(new StreamSource(mailXsl.openStream()));
    toMail.setParameter("to-name", "Litmus");
    toMail.setParameter("to-address", System.getProperty("user.name") + "@bruun-rasmussen.dk"); // "test@litmus.com");
    toMail.setParameter("from-name", "Litmus");
    toMail.setParameter("from-address", System.getProperty("user.name") + "@bruun-rasmussen.dk");
    toMail.setParameter("subject", "\u2709 Resultat fra din søgeagent");
    toMail.setParameter("href-base", new URL(htmlSrcUrl, "."));

    DOMResult mailRes = new DOMResult();
    toMail.transform(new DOMSource(html), mailRes);
    Node email = mailRes.getNode();

    Transformer toXml = xf.newTransformer();
    toXml.setOutputProperty("method", "xml");
    toXml.setOutputProperty("indent", "yes");
    toXml.transform(new DOMSource(email), new StreamResult(System.out));

    System.setProperty("dk.br.mail.base64-embed", "true");
    MailMessageData mailData = MailMessageParser.parseMail((Element)email.getFirstChild()); // <- da fuk?!
    mailData.setCustomHeader("X-SentFromClass", getClass().getName());

    send(mailData);
  }

  private static void send(MailMessageSource src) throws MessagingException, IOException {
    Session session = Session.getInstance(new Properties());
    MimeMessage msg = src.compose(session, true);
    // msg.getMessageID() is still null here

    File mailtest_eml = new File("mailtest.eml");
    FileOutputStream os = new FileOutputStream(mailtest_eml);
    try {
      msg.writeTo(os); // <- msg.getMessageID() gets set here
    }
    finally {
      os.close();
    }

    String messageId = msg.getMessageID();
    assertNotNull(messageId);

    LOG.info("wrote {}: {}", messageId, mailtest_eml.getAbsolutePath());
  }

  private static Document htmlSouped(URL srcUrl) throws IOException
  {
    URLConnection urlConn = srcUrl.openConnection();
    InputStream is = urlConn.getInputStream();
    org.jsoup.nodes.Document soupDoc = Jsoup.parse(is, urlConn.getContentEncoding(), srcUrl.toExternalForm());

    W3CDom w3cDom = new W3CDom();
    return w3cDom.fromJsoup(soupDoc);
  }
}

package dk.br.mail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author osa
 */
public class SendTest
{
  public void testThis() throws IOException, TransformerConfigurationException, TransformerException, AddressException, ParserConfigurationException, MessagingException {
    URL htmlSrcUrl = getClass().getClassLoader().getResource("dk/br/zurb/mail/inky/br_soegeagent.html");
    Document html = htmlSouped(htmlSrcUrl);

    URL mailXsl = getClass().getClassLoader().getResource("dk/br/mail/send-test.xsl");
    TransformerFactory xf = TransformerFactory.newInstance();

    Transformer toMail = xf.newTransformer(new StreamSource(mailXsl.openStream()));
    toMail.setParameter("to-name", "Litmus");
    toMail.setParameter("to-address", "osa@bruun-rasmussen.dk"); // "test@litmus.com");
    toMail.setParameter("from-name", "Litmus");
    toMail.setParameter("from-address", System.getProperty("user.name") + "@bruun-rasmussen.dk");
    toMail.setParameter("subject", "\u2709 Resultat fra din søgeagent");
    toMail.setParameter("href-base", new URL(htmlSrcUrl, ".."));

    DOMResult mailRes = new DOMResult();
    toMail.transform(new DOMSource(html), mailRes);
    Node email = mailRes.getNode();

    Transformer toXml = xf.newTransformer();
    toXml.setOutputProperty("method", "xml");
    toXml.setOutputProperty("indent", "yes");
    toXml.transform(new DOMSource(email), new StreamResult(System.out));

    MailMessageData mailData = MailMessageParser.parseMail(email.getFirstChild()); // <- da fuk?!
    mailData.setCustomHeader("X-SentFromClass", getClass().getName());

    send(mailData);
  }

  private static void send(MailMessageSource src) throws MessagingException {
    Properties properties = new Properties();
    properties.put("mail.smtp.host", "localhost");
    Session session = Session.getInstance(properties);

    MimeMessage msg = src.compose(session, true);
    System.out.println("whoa, " + msg + " ready to send");
    Transport.send(msg);
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

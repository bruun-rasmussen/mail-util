package dk.br.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 *
 * @author osa
 */
public class MarshallerTest
{
    private static final Logger LOG = LoggerFactory.getLogger(MarshallerTest.class);

    private Session dummy_session;
    private GreenMail mailServer;

    @Before
    public void setUp() {
        dummy_session = Session.getInstance(new Properties());
        Assert.assertNotNull(dummy_session);

        mailServer = new GreenMail(ServerSetupTest.IMAPS);
        mailServer.start();
    }

    @After
    public void tearDown() {
        mailServer.stop();
    }

    @Test
    public void testSimple() throws Exception {
        testSample(_sample("dk/br/mail/simple.eml"));
    }

    @Test
    public void testImage() throws Exception {
        testSample(_sample("dk/br/mail/html-and-image.eml"));
    }

    @Test
    public void testWithAttachment() throws Exception {
        testSample(_sample("dk/br/mail/html-with-attachment.eml"));
    }

/*  @Test
    public void testComplex() throws Exception {
        testSample(_sample("dk/br/mail/complex.eml"));
    }   */

    private URL _sample(String name) throws Exception {
      return Thread.currentThread().getContextClassLoader().getResource(name);
    }

    private MailMessageData testSample(URL eml) throws Exception {
        MimeMessage msg = loadMessage(eml);
        Document doc = MailMessageMarshaller.marshal(msg);
        assertNotNull(doc);
        String path = eml.toURI().getPath();
        String file = new File(path).getName();
        File xml = new File(file.replace(".eml", "-doc.xml"));

        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(xml));

        MailMessageData mail = MailMessageParser.parseMail(doc.getDocumentElement());
        assertNotNull(mail);
        LOG.info("parsed {} → {}: {}", file, xml, mail);

        MimeMessage fullCircle = mail.compose(dummy_session, false);
        FileOutputStream emlFile = new FileOutputStream(new File(file.replace(".eml", "-res.eml")));
        try {
          fullCircle.writeTo(emlFile);
        }
        finally {
          emlFile.close();
        }
        return mail;
    }


    private MimeMessage loadMessage(URL eml) throws Exception {
        LOG.info("reading {}", eml);

        InputStream is = eml.openStream();
        try {
          MimeMessage msg = new MimeMessage(dummy_session, is);
          assertNotNull(eml + " not found", msg);

          GreenMailUser user = mailServer.setUser("me", "*mysecret*");
          user.deliver(msg);
          return msg;
        }
        finally {
          is.close();
        }
    }
}

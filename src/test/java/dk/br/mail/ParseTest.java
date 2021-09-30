package dk.br.mail;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.junit.Assert.*;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @author osa
 */
public class ParseTest
{
  private final static Logger LOG = LoggerFactory.getLogger(ParseTest.class);
  private final static DocumentBuilderFactory DBF = DocumentBuilderFactory.newInstance();

  private DocumentBuilder db;

  @Test
  public void testGoodMail1() throws IOException {
    testMailParser("OnlineOverbidNotification-mail.xml");
  }

  @Test
  public void testGoodMail2() throws IOException {
    testMailParser("OnlineOverbidNotification-mail_ws.xml");
  }

  @Test
  public void testGoodMail3() throws IOException {
    testMailParser("mail-merge-sample.xml");
  }

  private void testMailParser(String resource) throws IOException {
    assertNotNull(resource);

    URL xmlSource = getClass().getResource(resource);
    assertNotNull(xmlSource);
    LOG.info("read {}", xmlSource);

    Document doc = parse(xmlSource);
    assertNotNull(doc);

    MailMessageData[] mailData = MailMessageParser.parseMails(doc);
    assertNotNull(mailData);
    LOG.info("read {}", Arrays.asList(mailData));
  }

  private Document parse(URL src) throws IOException {
    try {
      db = DBF.newDocumentBuilder();
    }
    catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    }

    try {
      return db.parse(src.openStream());
    }
    catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }
}

package dk.br.mail;

import dk.br.zurb.inky.Inky;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import junit.framework.TestCase;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 *
 * @author osa
 */
public class InkyTest extends TestCase
{
  private Inky inky;

  public InkyTest(String testName)
  {
    super(testName);
  }

  @Override
  protected void setUp() throws Exception
  {
    inky = new Inky();

    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception
  {
    super.tearDown();
  }

  public void testOurOwn() throws IOException, TransformerException, SAXException {
    checkTemplate("br_order-ex.html");
    checkTemplate("br_vores-vurdering.html");
    checkTemplate("br_newsletter.html");
  }

  public void __testThis() throws IOException, TransformerException, SAXException {
    checkTemplate("drip.html");
    checkTemplate("hero.html");
    checkTemplate("sidebar.html");
    checkTemplate("sidebar-hero.html");
  }

  private void checkTemplate(String src) throws IOException, TransformerException, SAXException {
    URL srcUrl = getClass().getClassLoader().getResource("dk/br/zurb/mail/source/pages/" + src);
    URLConnection urlConn = srcUrl.openConnection();
    InputStream is = urlConn.getInputStream();
    org.jsoup.nodes.Document soupDoc = Jsoup.parse(is, urlConn.getContentEncoding(), srcUrl.toExternalForm());

    W3CDom w3cDom = new W3CDom();
    Document doc = w3cDom.fromJsoup(soupDoc);

    inky.transform(new DOMSource(doc), new StreamResult(new File(src)));
  }
}

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
 * @author osa
 */
public class InkyTest extends TestCase
{
  private static Inky inky = new Inky();

  public InkyTest(String testName)
  {
    super(testName);
  }

  @Override
  protected void setUp() throws Exception
  {
//  inky = new Inky();

    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception
  {
    super.tearDown();
  }

  public void testOurOwn() throws IOException, TransformerException, SAXException {
    checkZurbResourceTemplate("br_ov-kommentar.html");
    checkZurbResourceTemplate("br_order-ex.html");
    checkZurbResourceTemplate("br_vores-vurdering.html");
    checkZurbResourceTemplate("br_newsletter.html");

    checkZurbResourceTemplate("sidebar-hero.html");
    
    checkTemplate(getClass().getClassLoader().getResource("dk/br/sample/no-inky.html"), false);
  }

  public void __testThis() throws IOException, TransformerException, SAXException {
    checkZurbResourceTemplate("drip.html");
    checkZurbResourceTemplate("hero.html");
    checkZurbResourceTemplate("sidebar.html");
  }

  private void checkZurbResourceTemplate(String src) throws IOException, TransformerException, SAXException {
    URL srcUrl = getClass().getClassLoader().getResource("dk/br/zurb/mail/source/pages/" + src);
    checkTemplate(srcUrl, true);
  }

  private void checkTemplate(URL srcUrl, boolean containsInky) 
          throws IOException, TransformerException, SAXException {
    URLConnection urlConn = srcUrl.openConnection();
    InputStream is = urlConn.getInputStream();
    org.jsoup.nodes.Document soupDoc = Jsoup.parse(is, urlConn.getContentEncoding(), srcUrl.toExternalForm());

    W3CDom w3cDom = new W3CDom();
    Document doc = w3cDom.fromJsoup(soupDoc);
    
    assertEquals(containsInky, Inky.containsInky(doc));
    
    File out = new File(srcUrl.getPath());
    inky.transform(new DOMSource(doc), new StreamResult(new File("N_" + out.getName())), false);
    inky.transform(new DOMSource(doc), new StreamResult(new File("I_" + out.getName())), true);
  }
}

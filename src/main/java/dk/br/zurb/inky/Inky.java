package dk.br.zurb.inky;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamSource;

/**
 * @author osa
 */
public class Inky
{
  private final static SAXTransformerFactory stf = (SAXTransformerFactory)TransformerFactory.newInstance(); 
  
  private String inkyCss;
  private Templates inky1;
  private Templates inky2;

  public Inky() {
    init();
  }
  
  private void init() {
    inky1 = _loadXsl(getClass().getResource("inky.xsl"));
    inky2 = _loadXsl(getClass().getResource("inky-center.xsl"));
    inkyCss = _loadText(getClass().getClassLoader().getResource("dk/br/zurb/mail/css/app.css"), Charset.defaultCharset());
  }
  
  private Templates _loadXsl(URL res) {
    try {
      StreamSource src = new StreamSource(res.openStream(), res.toExternalForm());
      return stf.newTemplates(src);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    catch (TransformerConfigurationException ex) {
      throw new RuntimeException(ex);
    }
  }

  private String _loadText(URL res, Charset encoding) {
    return new String(_load(res), encoding);
  }

  private byte[] _load(URL res) {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try {
      InputStream is = res.openStream();
      byte buf[] = new byte[8192];
      int n;
      while ((n = is.read(buf)) > 0)
        bo.write(buf, 0, n);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return bo.toByteArray();
  }
  
  public void transform(Source src, Result res) 
          throws TransformerException {
    Transformer xf1 = inky1.newTransformer();
    xf1.setParameter("inky-css", inkyCss);
    xf1.setParameter("column-count", 12);

    DOMResult r1 = new DOMResult();
    xf1.transform(src, r1);

    DOMSource s2 = new DOMSource(r1.getNode(), r1.getSystemId());
    Transformer xf2 = inky2.newTransformer();
    xf2.setOutputProperty(OutputKeys.METHOD, "html");
    xf2.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    xf2.setOutputProperty(OutputKeys.INDENT, "no");
    xf2.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); 
    
    xf2.transform(s2, res);
  }
}

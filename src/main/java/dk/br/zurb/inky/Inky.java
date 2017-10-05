package dk.br.zurb.inky;

import cz.vutbr.web.css.CSSException;
import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.domassign.Analyzer;
import cz.vutbr.web.domassign.StyleMap;
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
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author osa
 */
public class Inky
{
  private final static Logger LOG = LoggerFactory.getLogger(Inky.class);

  private final static SAXTransformerFactory stf = (SAXTransformerFactory)TransformerFactory.newInstance();

  private StyleSheet inlineCss;
  private String commonCss;
  private Templates inky1;
  private Templates inky2;
  private String htmlEncoding;

  public Inky()
  {
    init();
  }

  private void init()
  {
    inky1 = _loadXsl(getClass().getResource("inky.xsl"));
    inky2 = _loadXsl(getClass().getResource("inky-center.xsl"));
    inlineCss = _loadCss(getClass().getClassLoader().getResource("dk/br/zurb/mail/css/app.css"));
    commonCss = _loadText(getClass().getClassLoader().getResource("dk/br/zurb/mail/css/mq.css"), Charset.defaultCharset());
    htmlEncoding = "UTF-8";
  }


  /*public String getCss()
  {
    return inkyCss;
  }

  public void setCss(String inkyCss)
  {
    this.inkyCss = inkyCss;
  }*/
  private StyleSheet _loadCss(URL res)
  {
    try
    {
      return CSSFactory.parse(res, "UTF-8");
    }
    catch (IOException ex)
    {
      throw new RuntimeException(ex);
    }
    catch (CSSException ex)
    {
      LOG.error("{} failed to load", res, ex);
      throw new RuntimeException(ex);
    }
  }

  private Templates _loadXsl(URL res)
  {
    try
    {
      StreamSource src = new StreamSource(res.openStream(), res.toExternalForm());
      return stf.newTemplates(src);
    }
    catch (IOException ex)
    {
      throw new RuntimeException(ex);
    }
    catch (TransformerConfigurationException ex)
    {
      throw new RuntimeException(ex);
    }
  }

  private String _loadText(URL res, Charset encoding)
  {
    return new String(_load(res), encoding);
  }

  private byte[] _load(URL res)
  {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try
    {
      InputStream is = res.openStream();
      byte buf[] = new byte[8192];
      int n;
      while ((n = is.read(buf)) > 0)
        bo.write(buf, 0, n);
    }
    catch (IOException ex)
    {
      throw new RuntimeException(ex);
    }
    return bo.toByteArray();
  }

  public void transform(Source src, Result res)
          throws TransformerException
  {
    Transformer xf1 = inky1.newTransformer();
    xf1.setParameter("common-css", commonCss);
    xf1.setParameter("column-count", 12);

    DOMResult r1 = new DOMResult();
    xf1.transform(src, r1);

    Document doc = (Document)r1.getNode();
    Transformer s = TransformerFactory.newInstance().newTransformer();
    s.setOutputProperty(OutputKeys.METHOD, "xml");
    s.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    s.setOutputProperty(OutputKeys.INDENT, "yes");
    s.transform(new DOMSource(doc), new StreamResult(new java.io.File("test-mail.xml")));
    
    StyleMap styles = new Analyzer(inlineCss).evaluateDOM(doc, "screen", true);
    inlineCss(doc, styles);

    DOMSource s2 = new DOMSource(doc, r1.getSystemId());
    Transformer xf2 = inky2.newTransformer();
    xf2.setOutputProperty(OutputKeys.METHOD, "html");
    xf2.setOutputProperty(OutputKeys.ENCODING, htmlEncoding);
    xf2.setOutputProperty(OutputKeys.INDENT, "no");
    xf2.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

    xf2.transform(s2, res);
  }

  private void inlineCss(Node n, StyleMap sm)
  {
    NodeList cs = n.getChildNodes();
    for (int i = 0; i < cs.getLength(); i++)
      inlineCss(cs.item(i), sm);

    if (n.getNodeType() == Node.ELEMENT_NODE)
    {
      Element e = (Element)n;
      NodeData css = sm.get(e);
      if (css != null)
        patch(e, css);
    }
  }

  private void patch(Element e, NodeData css)
  {
    String s = e.getAttribute("style");
    if (s == null)
      s = "";
    for (String prop : css.getPropertyNames())
    {
      if (prop == null)
        continue;
      String val = css.getAsString(prop, true);
      if (s.length() > 0 && !s.endsWith(";"))
        s += ";";
      s += prop + ":" + val;
    }
    if (s.length() > 0)
      e.setAttribute("style", s);
  }

  public static boolean containsInky(Node src)
  {
    try {
      XPathExpression hasInky = XPathFactory.newInstance().newXPath().compile("//row|//columns|//callout");
      NodeList ns = (NodeList)hasInky.evaluate(src, XPathConstants.NODESET);
      LOG.info("has inky? [\"{}\"]", ns);
      return ns.getLength() > 0;
    }
    catch (XPathExpressionException ex) {
      throw new RuntimeException("XML serialization error", ex);
    }
  }
}

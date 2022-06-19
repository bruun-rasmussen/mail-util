package dk.br.mail;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author osa
 */
public class LinkTaggerTest
{
    LinkTagger tagger;

    @Before
    public void setUp() {
      tagger = new LinkTagger();
      tagger.put("utm_medium", "email");
      tagger.put("utm_source", "Newsletter");
    }

    @Test
    public void testSimpleLatin8() throws Exception {
      tagger.put("ping", "ping og pong");
      tagger.put("zip", "zap");
      tagger.put("q", "jørgen");

      String q = tagger.amendQueryString("?id=345+87&zip=zoop", "ISO-8859-1");
      Assert.assertEquals("?id=345+87&zip=zoop&utm_medium=email&utm_source=Newsletter&ping=ping+og+pong&q=j%F8rgen", q);
    }

    @Test
    public void testSimpleUtf8() throws Exception {
      tagger.put("ping", "ping og pong");
      tagger.put("zip", "zap");
      tagger.put("q", "jørgen");

      String q = tagger.amendQueryString("?id=345+87&zip=zoop", "UTF-8");
      Assert.assertEquals("?id=345+87&zip=zoop&utm_medium=email&utm_source=Newsletter&ping=ping+og+pong&q=j%C3%B8rgen", q);
    }

    @Test
    public void testValueless() throws Exception {
      String q = tagger.amendQueryString("?Jhsd6JH56JH&noget=mere", "iso-8859-1");
      Assert.assertEquals("?Jhsd6JH56JH&noget=mere&utm_medium=email&utm_source=Newsletter", q);
    }

    @Test
    public void testStack() throws Exception {
      Assert.assertEquals("?utm_medium=email&utm_source=Newsletter", tagger.amendQueryString("", "iso-8859-1"));
      tagger.pushFrame();
      tagger.put("x", "y");
      tagger.put("utm_source", "Myletter");
      Assert.assertEquals("?utm_medium=email&utm_source=Myletter&x=y", tagger.amendQueryString("", "iso-8859-1"));
      Assert.assertEquals("?utm_source=ThatLetter&utm_medium=email&x=y", tagger.amendQueryString("?utm_source=ThatLetter", "iso-8859-1"));
      tagger.popFrame();
      Assert.assertEquals("?utm_medium=email&utm_source=Newsletter", tagger.amendQueryString("", "iso-8859-1"));
    }

    @Test
    public void testHrefWithAnchorRef() throws Exception {
      tagger.pushFrame();
      tagger.put("utm_source", "Myletter");
      tagger.addTaggedDomains("bruun-rasmussen.dk");

      _amendedHref("https://bruun-rasmussen.dk/m/account/consignments/928146-1#messages-drawer",
                   "https://bruun-rasmussen.dk/m/account/consignments/928146-1?utm_medium=email&utm_source=Myletter#messages-drawer");
    }

    @Test
    public void testHref() throws Exception {
      tagger.pushFrame();
      tagger.put("x", "y");
      tagger.put("utm_source", "Myletter");
      tagger.addTaggedDomains("example.com *.example.com");

      _unchangedHref("ftp://example.com/~/files", "iso-8859-1");
      _amendedHref("http://example.com/files", "http://example.com/files?utm_medium=email&utm_source=Myletter&x=y");
      _amendedHref("http://some.subdomain.example.com/files", "http://some.subdomain.example.com/files?utm_medium=email&utm_source=Myletter&x=y");
      _unchangedHref("http://example.org/~/files", "iso-8859-1");

      tagger.popFrame();
    }
    private void _unchangedHref(String href, String enc) {
      Assert.assertEquals(href, tagger.amendHrefAddress(href, enc));
    }

    private void _amendedHref(String source, String amended) {
      Assert.assertEquals(amended, tagger.amendHrefAddress(source, "iso-8859-1"));
    }
}

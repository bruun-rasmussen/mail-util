package dk.br.mail;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
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
    public void testSimple() throws Exception {
      tagger.put("ping", "ping og pong");
      tagger.put("zip", "zap");

      String q = tagger.amendQueryString("?id=345+87&zip=zoop");
      Assert.assertEquals("?id=345+87&zip=zoop&utm_medium=email&utm_source=Newsletter&ping=ping+og+pong", q);
    }

    @Test
    public void testValueless() throws Exception {
      String q = tagger.amendQueryString("?Jhsd6JH56JH&noget=mere");
      Assert.assertEquals("?Jhsd6JH56JH&noget=mere&utm_medium=email&utm_source=Newsletter", q);
    }

    @Test
    public void testStack() throws Exception {
      Assert.assertEquals("?utm_medium=email&utm_source=Newsletter", tagger.amendQueryString(""));
      tagger.pushFrame();
      tagger.put("x", "y");
      tagger.put("utm_source", "Myletter");
      Assert.assertEquals("?utm_medium=email&utm_source=Myletter&x=y", tagger.amendQueryString(""));
      Assert.assertEquals("?utm_source=ThatLetter&utm_medium=email&x=y", tagger.amendQueryString("?utm_source=ThatLetter"));
      tagger.popFrame();
      Assert.assertEquals("?utm_medium=email&utm_source=Newsletter", tagger.amendQueryString(""));
    }

    @Test
    public void testHref() throws Exception {
      tagger.pushFrame();
      tagger.put("x", "y");
      tagger.put("utm_source", "Myletter");
      tagger.addTaggedDomains("example.com *.example.com");

      _unchangedHref("ftp://example.com/~/files");
      _amendedHref("http://example.com/files", "http://example.com/files?utm_medium=email&utm_source=Myletter&x=y");
      _amendedHref("http://some.subdomain.example.com/files", "http://some.subdomain.example.com/files?utm_medium=email&utm_source=Myletter&x=y");
      _unchangedHref("http://example.org/~/files");

      tagger.popFrame();
    }

    private void _unchangedHref(String href) {
      Assert.assertEquals(href, tagger.amendHrefAddress(href));
    }

    private void _amendedHref(String source, String amended) {
      Assert.assertEquals(amended, tagger.amendHrefAddress(source));
    }
}

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

}

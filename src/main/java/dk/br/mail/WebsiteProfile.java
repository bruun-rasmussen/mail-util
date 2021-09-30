package dk.br.mail;

import java.util.regex.Pattern;

/**
 * @author osa
 */
public abstract class WebsiteProfile
{
  public abstract boolean matches(String address);

  public static final WebsiteProfile DEFAULT = new WebsiteProfile() {
    public boolean matches(String address) {
      return true;
    }
  };

  private static Pattern wildcardPattern(String wp) {
    String rp = wp.replaceAll("[^?*]+", "\\\\Q$0\\\\E").replaceAll("\\?", ".").replaceAll("\\*", ".*");
    return Pattern.compile(rp);
  }

  public static void main(String args[]) {
    Pattern p = wildcardPattern("Hej*mor?");
    System.out.println(p.pattern());
  }
}

package common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The avatar catalog: a fixed, ordered preset list whose ids drive the server-side allowlist. */
public class PlayerAvatarsTest {

  @Test
  public void catalogHoldsPartnerTwelveSuspectsAndWatson() {
    // partner + 12 suspects + watson
    assertEquals(14, PlayerAvatars.IDS.size());
    assertEquals("char_partner", PlayerAvatars.IDS.get(0));
    assertTrue(PlayerAvatars.IDS.contains("char_suspect_01"));
    assertTrue(PlayerAvatars.IDS.contains("char_suspect_12"));
    assertTrue(PlayerAvatars.IDS.contains("char_watson"));
  }

  @Test
  public void defaultIdIsInTheCatalog() {
    assertTrue(PlayerAvatars.isValid(PlayerAvatars.DEFAULT_ID));
  }

  @Test
  public void allowlistAcceptsKnownIdsAndRejectsEverythingElse() {
    assertTrue(PlayerAvatars.isValid("char_suspect_03"));
    assertFalse(PlayerAvatars.isValid("char_suspect_99"));
    assertFalse(PlayerAvatars.isValid("../../etc/passwd"));
    assertFalse(PlayerAvatars.isValid(""));
    assertFalse(PlayerAvatars.isValid(null));
  }
}

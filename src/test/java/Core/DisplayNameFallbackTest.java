package Core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The Universal Name vs Display Name split on the Core models (.scratch/gui-localized-case-names).
 * Each of Room/GameObject/Suspect keeps its language-independent Universal Name and gains an
 * optional per-language Display Name that the GUI shows; when no Display Name is set,
 * {@code getDisplayName()} falls back to the Universal Name so existing cases are unaffected.
 */
public class DisplayNameFallbackTest {

  @Test
  public void roomDisplayNameFallsBackToUniversalName() {
    Room room = new Room("Study", "A quiet study.", null);
    assertEquals("Study", room.getDisplayName());

    room.setDisplayName("المكتب");
    assertEquals("المكتب", room.getDisplayName());
    assertEquals("Study", room.getName()); // Universal Name is untouched.
  }

  @Test
  public void objectDisplayNameFallsBackToUniversalName() {
    GameObject obj = new GameObject("letter", "letter", "d", "e", "de");
    assertEquals("letter", obj.getDisplayName());

    obj.setDisplayName("الرسالة");
    assertEquals("الرسالة", obj.getDisplayName());
    assertEquals("letter", obj.getName());
  }

  @Test
  public void suspectDisplayNameFallsBackToUniversalName() {
    Suspect suspect = new Suspect("LadyEleanor", "LadyEleanor", "stmt", "clue");
    assertEquals("LadyEleanor", suspect.getDisplayName());

    suspect.setDisplayName("الليدي إلينور");
    assertEquals("الليدي إلينور", suspect.getDisplayName());
    assertEquals("LadyEleanor", suspect.getName());
  }

  @Test
  public void blankDisplayNameFallsBackToUniversalName() {
    Room room = new Room("Study", "d", null);
    room.setDisplayName("  ");
    assertEquals("Study", room.getDisplayName());
  }
}

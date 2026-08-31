package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Behaviour of {@link ObjectDraft} and object placement within a {@link RoomDraft} (slice 2). */
public class ObjectDraftTest {

  @Test
  public void anObjectAddedToARoomIsRetrievable() {
    CaseDraft draft = new CaseDraft();
    RoomDraft hall = draft.addRoom("Hall");

    ObjectDraft vase = hall.addObject("Ming vase");

    assertEquals(1, hall.getObjects().size());
    assertSame(vase, hall.getObjects().get(0));
    assertEquals("Ming vase", vase.getName());
  }

  @Test
  public void idIsDerivedFromTheNameWhenNotSetExplicitly() {
    ObjectDraft obj = new ObjectDraft("Torn Letter");
    // Mirrors GameObjectExtractor: lowercase, spaces → underscores.
    assertEquals("torn_letter", obj.getId());
  }

  @Test
  public void anExplicitIdOverridesTheDerivedOneAndIsTrimmed() {
    ObjectDraft obj = new ObjectDraft("Torn Letter");
    obj.setId("  letter_01  ");
    assertEquals("letter_01", obj.getId());
  }

  @Test
  public void removingAnObjectDropsItFromTheRoom() {
    CaseDraft draft = new CaseDraft();
    RoomDraft hall = draft.addRoom("Hall");
    ObjectDraft vase = hall.addObject("vase");

    hall.removeObject(vase);

    assertTrue(hall.getObjects().isEmpty());
  }

  @Test
  public void aFreshObjectIsUnplaced() {
    ObjectDraft obj = new ObjectDraft("vase");
    assertNull(obj.getPosX());
    assertNull(obj.getPosY());
  }

  @Test
  public void placingClampsTheNormalizedPositionIntoTheUnitSquare() {
    ObjectDraft obj = new ObjectDraft("vase");
    obj.setPosition(1.4, -0.2);
    assertEquals(1.0, obj.getPosX(), 1e-9);
    assertEquals(0.0, obj.getPosY(), 1e-9);

    obj.setPosition(0.3, 0.75);
    assertEquals(0.3, obj.getPosX(), 1e-9);
    assertEquals(0.75, obj.getPosY(), 1e-9);
  }

  @Test
  public void imageScaleDefaultsToOneAndIgnoresNonPositiveValues() {
    ObjectDraft obj = new ObjectDraft("vase");
    assertEquals(1.0, obj.getImageScale(), 1e-9);

    obj.setImageScale(1.5);
    assertEquals(1.5, obj.getImageScale(), 1e-9);

    obj.setImageScale(0); // non-positive ignored, keeps the previous value
    assertEquals(1.5, obj.getImageScale(), 1e-9);
  }
}

package JsonDTO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/**
 * Guards the universal→localized object merge in {@link LocalizedCaseFile}. An object's render
 * fields (id, imagePath, normalized position, and sprite scale) are authored once at the universal
 * level and must survive the merge into each single-language view, or they never reach the engine.
 *
 * <p>{@code imageScale} was historically dropped here (see DECISIONS DEC-4), so an authored object
 * size silently became a no-op in play. This test pins all of them down.
 */
public class LocalizedCaseFileObjectMergeTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String CASE_JSON =
      "{"
          + "\"universal_title\":\"T\","
          + "\"startingRoom\":\"Hall\","
          + "\"rooms\":[{\"name\":\"Hall\",\"neighbors\":{},\"objects\":["
          + "{\"id\":\"vase\",\"name\":\"vase\",\"imagePath\":\"images/vase.png\","
          + "\"posX\":0.5,\"posY\":0.4,\"imageScale\":1.75}]}],"
          + "\"localizations\":{\"en\":{\"languageName\":\"English\",\"title\":\"T\","
          + "\"roomDetails\":[{\"name\":\"Hall\",\"description\":\"d\"}],"
          + "\"objectDetails\":[{\"name\":\"vase\",\"description\":\"d\",\"examine\":\"e\","
          + "\"deduce\":\"de\"}]}}}";

  private CaseFile.GameObjectData mergedVase() throws Exception {
    CaseFile caseFile = MAPPER.readValue(CASE_JSON, CaseFile.class);
    LocalizedCaseFile localized = new LocalizedCaseFile(caseFile, "en");
    return localized.getRooms().get(0).getObjects().get(0);
  }

  @Test
  public void imageScaleSurvivesTheMerge() throws Exception {
    CaseFile.GameObjectData vase = mergedVase();
    assertNotNull("imageScale must be carried through the localized merge", vase.getImageScale());
    assertEquals(1.75, vase.getImageScale(), 1e-9);
  }

  @Test
  public void positionAndIdentitySurviveTheMerge() throws Exception {
    CaseFile.GameObjectData vase = mergedVase();
    assertEquals("vase", vase.getId());
    assertEquals("images/vase.png", vase.getImagePath());
    assertNotNull(vase.getPosX());
    assertEquals(0.5, vase.getPosX(), 1e-9);
    assertEquals(0.4, vase.getPosY(), 1e-9);
    // Localized text is merged in by name.
    assertEquals("e", vase.getExamine());
  }
}

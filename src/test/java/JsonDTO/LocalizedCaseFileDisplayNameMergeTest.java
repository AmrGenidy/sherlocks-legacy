package JsonDTO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/**
 * Guards that the per-language Display Name (.scratch/gui-localized-case-names) authored in a
 * localization block survives the universal→localized merge in {@link LocalizedCaseFile}: a
 * {@code roomDetails[].displayName} reaches the merged {@code RoomData}, an
 * {@code objectDetails[].displayName} reaches the merged {@code GameObjectData}, and a suspect's
 * {@code displayName} reads straight off the localized suspect block. The Universal Name (the join
 * key {@code .name}) is never disturbed, and an absent Display Name merges as null (the runtime
 * then falls back to the Universal Name).
 */
public class LocalizedCaseFileDisplayNameMergeTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String CASE_JSON =
      "{"
          + "\"universal_title\":\"T\","
          + "\"startingRoom\":\"Study\","
          + "\"rooms\":[{\"name\":\"Study\",\"neighbors\":{},\"objects\":["
          + "{\"id\":\"letter\",\"name\":\"letter\"},"
          + "{\"id\":\"vase\",\"name\":\"vase\"}]}],"
          + "\"localizations\":{\"ar\":{\"languageName\":\"العربية\",\"title\":\"T\","
          + "\"roomDetails\":[{\"name\":\"Study\",\"displayName\":\"المكتب\",\"description\":\"d\"}],"
          + "\"objectDetails\":["
          + "{\"name\":\"letter\",\"displayName\":\"الرسالة\",\"examine\":\"e\",\"deduce\":\"de\"},"
          + "{\"name\":\"vase\",\"examine\":\"e\",\"deduce\":\"de\"}],"
          + "\"suspects\":[{\"id\":\"LadyEleanor\",\"name\":\"LadyEleanor\","
          + "\"displayName\":\"الليدي إلينور\",\"statement\":\"s\",\"clue\":\"c\","
          + "\"homeRoom\":\"Study\"}]}}}";

  private LocalizedCaseFile localizedArabic() throws Exception {
    CaseFile caseFile = MAPPER.readValue(CASE_JSON, CaseFile.class);
    return new LocalizedCaseFile(caseFile, "ar");
  }

  @Test
  public void roomDisplayNameSurvivesTheMerge() throws Exception {
    CaseFile.RoomData study = localizedArabic().getRooms().get(0);
    assertEquals("Study", study.getName()); // Universal Name (join key) untouched.
    assertEquals("المكتب", study.getDisplayName());
  }

  @Test
  public void objectDisplayNameSurvivesTheMerge() throws Exception {
    CaseFile.RoomData study = localizedArabic().getRooms().get(0);
    CaseFile.GameObjectData letter = study.getObjects().get(0);
    assertEquals("letter", letter.getName());
    assertEquals("الرسالة", letter.getDisplayName());
  }

  @Test
  public void absentObjectDisplayNameMergesAsNull() throws Exception {
    CaseFile.RoomData study = localizedArabic().getRooms().get(0);
    CaseFile.GameObjectData vase = study.getObjects().get(1);
    assertNull("an unauthored Display Name stays null so the runtime can fall back", vase.getDisplayName());
  }

  @Test
  public void suspectDisplayNameReadsFromTheLocalizedBlock() throws Exception {
    CaseFile.SuspectData suspect = localizedArabic().getSuspects().get(0);
    assertEquals("LadyEleanor", suspect.getName());
    assertEquals("الليدي إلينور", suspect.getDisplayName());
  }
}

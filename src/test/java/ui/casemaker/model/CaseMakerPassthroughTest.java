package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * A Case Maker load→export round-trip must PRESERVE top-level blocks the editor does not model
 * (case_file, red_herrings, leads, and any future unknown keys) instead of silently stripping them
 * (.scratch/case-maker preserve-unmodeled). {@code leads} is not even present on {@link CaseFile}
 * (Jackson drops it), so the passthrough is driven by the original JSON tree the loader retains.
 */
public class CaseMakerPassthroughTest {

  /** Same configuration {@code CaseLoader} parses cases with. */
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String CASE_JSON =
      """
      {
        "universal_title": "Passthrough Case",
        "startingRoom": "Study",
        "rooms": [ { "name": "Study", "objects": [] } ],
        "case_file": {
          "victim": { "name": "Mr. Blake", "relation_to_case": "The master of the house.",
            "notes": { "en": "A hard man." } },
          "overview": { "en": "A quiet house under grey dusk." },
          "suspect_profiles": {
            "JamesCarter": { "profession": {"en":"Gentleman"}, "age": 31,
              "bio": {"en":"In debt."}, "imagePath": "images/james.png" } }
        },
        "red_herrings": { "suspects": {
          "JamesCarter": { "is_red_herring": true, "narrative": { "en": "He had reason, no opportunity." } } } },
        "leads": [
          { "id": "lead_a", "title": {"en":"First lead"}, "description": {"en":"Look at the tea."},
            "visibleWhen": {"type":"ALWAYS"}, "completeWhen": {"type":"NOTE_DISCOVERED","noteId":"teacup"} }
        ],
        "localizations": {
          "en": { "languageName": "English", "title": "Passthrough Case",
            "roomDetails": [ { "name": "Study", "description": "A dim study." } ],
            "objectDetails": [], "suspects": [] }
        }
      }
      """;

  /** Loads the case JSON from a real file (so the loader can retain the original tree). */
  private static CaseDraft loadFromTempFile() throws Exception {
    Path tmp = Files.createTempFile("passthrough-case", ".json");
    tmp.toFile().deleteOnExit();
    Files.writeString(tmp, CASE_JSON);
    CaseFile caseFile = MAPPER.readValue(tmp.toFile(), CaseFile.class);
    caseFile.setSourcePath(tmp.toAbsolutePath().toString());
    return CaseDraftLoader.load(caseFile);
  }

  @Test
  public void unmodeledTopLevelBlocksSurviveLoadExportReload() throws Exception {
    JsonNode original = MAPPER.readTree(CASE_JSON);
    CaseDraft draft = loadFromTempFile();

    JsonNode exported = MAPPER.readTree(CaseMakerSerializer.toJson(draft));

    assertEquals(
        "case_file must pass through verbatim",
        original.get("case_file"),
        exported.get("case_file"));
    assertEquals(
        "red_herrings must pass through verbatim",
        original.get("red_herrings"),
        exported.get("red_herrings"));
    assertEquals(
        "leads must pass through verbatim (not even modeled on CaseFile)",
        original.get("leads"),
        exported.get("leads"));
  }

  @Test
  public void modeledFieldsStillOverlayOntoThePreservedOriginal() throws Exception {
    CaseDraft draft = loadFromTempFile();
    draft.setUniversalTitle("Edited Title");

    JsonNode exported = MAPPER.readTree(CaseMakerSerializer.toJson(draft));

    // The edited modeled field wins...
    assertEquals("Edited Title", exported.get("universal_title").asText());
    // ...while the unmodeled block is still intact.
    assertTrue("leads must remain after a modeled edit", exported.has("leads"));
    assertEquals(1, exported.get("leads").size());
  }

  @Test
  public void deletedModeledContentDoesNotResurrectFromTheOriginal() throws Exception {
    CaseDraft draft = loadFromTempFile();
    // The fixture has no combine_logic; the modeled tree must own that decision — the original copy
    // must never leak a modeled key the draft omits. (Guards the overlay's remove-then-set order.)
    JsonNode exported = MAPPER.readTree(CaseMakerSerializer.toJson(draft));
    assertFalse(
        "an owned key the draft does not emit must be absent, not carried over",
        exported.has("combine_logic"));
  }

  @Test
  public void newDraftWithNoOriginalExportsModeledFieldsOnly() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("Brand New");
    JsonNode exported = MAPPER.readTree(CaseMakerSerializer.toJson(draft));
    assertEquals("Brand New", exported.get("universal_title").asText());
    assertFalse("a new draft has no passthrough blocks", exported.has("leads"));
  }
}

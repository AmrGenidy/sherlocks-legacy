package ui.casemaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import extractors.CaseLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.ExamSlotDraft;
import ui.casemaker.model.FinalExamQuestionDraft;
import ui.casemaker.model.RoomDraft;

/** Export to a self-contained {@code cases/<slug>/} folder + the recursing loader (slice 6). */
public class CaseExporterTest {

  @Test
  public void slugifyMakesAFolderSafeName() {
    assertEquals("the-stolen-sapphire", CaseExporter.slugify("The Stolen Sapphire!"));
    assertEquals("case", CaseExporter.slugify("   "));
    assertEquals("a-b", CaseExporter.slugify("a___b"));
  }

  @Test
  public void exportCopiesAssetsRewritesPathsAndRestoresTheModel() throws Exception {
    Path tmp = Files.createTempDirectory("cm-export");
    Path image = Files.createTempFile("ballroom", ".jpg");
    Files.writeString(image, "not really an image");

    CaseDraft draft = validDraft();
    RoomDraft hall = draft.getRooms().get(0);
    hall.setImagePath(image.toString()); // an absolute, real file

    CaseExporter.Result result = CaseExporter.export(draft, tmp);

    // Folder + JSON + copied asset.
    assertEquals(tmp.resolve("the-stolen-sapphire"), result.caseDir());
    assertTrue(Files.isRegularFile(result.caseJson()));
    String fileName = image.getFileName().toString();
    assertTrue(Files.isRegularFile(result.caseDir().resolve("images").resolve(fileName)));
    assertEquals(1, result.assetsCopied());

    // The serialized JSON uses the case-relative path…
    String json = Files.readString(result.caseJson());
    assertTrue(json.contains("images/" + fileName));
    // …but the in-memory model is restored to the author's original absolute path.
    assertEquals(image.toString(), hall.getImagePath());
  }

  @Test
  public void exportedCaseLoadsViaTheRecursingLoaderWithNoErrors() throws Exception {
    Path tmp = Files.createTempDirectory("cm-load");
    CaseExporter.export(validDraft(), tmp);

    // CaseLoader refuses cases with validation ERRORs, so a returned case is a valid one found
    // inside the cases/<slug>/ subfolder (DEC-3 recursion).
    List<CaseFile> cases = CaseLoader.loadCases(tmp.toString());

    assertTrue(
        "the exported case should load from its subfolder",
        cases.stream().anyMatch(c -> "The Stolen Sapphire".equals(c.getUniversalTitle())));
  }

  @Test
  public void validateReportsErrorsThatBlockExportAndPassesACleanDraft() {
    assertTrue("a clean draft validates", !CaseExporter.validate(validDraft()).hasErrors());

    CaseDraft broken = validDraft();
    broken.invitationText().set(null); // required field now blank
    assertTrue(
        "missing invitation is an error",
        CaseExporter.validate(broken).errors().stream()
            .anyMatch(i -> i.message().toLowerCase().contains("invitation")));
  }

  /** A minimal case with no validation errors: title, invitation, one reachable room, an exam. */
  private CaseDraft validDraft() {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("The Stolen Sapphire");
    draft.titleText().set("The Stolen Sapphire");
    draft.invitationText().set("Come at once, Holmes.");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    FinalExamQuestionDraft question = draft.addExamQuestion();
    question.setPrompt("Who did it?");
    ExamSlotDraft slot = question.addSlot();
    slot.addChoice("c1").setText("The butler"); // first choice is correct by default
    return draft;
  }
}

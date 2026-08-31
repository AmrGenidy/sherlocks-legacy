package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.CaseLoader;
import java.util.List;
import org.junit.Test;
import ui.casemaker.CaseExporter;

/**
 * Round-trips a rich {@link CaseDraft} through the serializer and back via {@link CaseDraftLoader}
 * (slice 7): draft → JSON → {@code CaseFile} → draft, asserting the content survives so an authored
 * case can be re-opened and edited.
 */
public class CaseDraftLoaderTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private CaseDraft roundTrip(CaseDraft draft) throws Exception {
    CaseFile parsed = MAPPER.readValue(CaseMakerSerializer.toJson(draft), CaseFile.class);
    return CaseDraftLoader.load(parsed);
  }

  @Test
  public void aRichCaseSurvivesTheRoundTrip() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.addLanguage("ru");
    draft.setLanguageName("en", "English");
    draft.setLanguageName("ru", "Русский");
    draft.setUniversalTitle("Sapphire");
    draft.setAuthor("AG");
    draft.setStartingInsightTokens(5);
    draft.titleText().set("en", "T");
    draft.titleText().set("ru", "Т");
    draft.invitationText().set("en", "Come");
    draft.invitationText().set("ru", "Приди");

    RoomDraft hall = draft.addRoom("Hall");
    RoomDraft terrace = draft.addRoom("Terrace");
    draft.linkRooms(hall, "east", terrace);
    draft.setStartingRoom(hall);
    hall.descriptionText().set("en", "a hall");
    ObjectDraft vase = hall.addObject("vase");
    vase.setImagePath("images/vase.png");
    vase.examineText().set("en", "fine");
    vase.examineText().set("ru", "прекрасно");

    SuspectDraft valet = draft.addSuspect("Valet");
    valet.setHomeRoom(terrace);
    valet.setStationary(true);
    valet.setInitialState("LIE");
    valet.state("LIE").statementText().set("en", "I was home");
    valet.state("LIE").statementText().set("ru", "Я был дома");
    ContradictionDraft c = valet.state("LIE").addContradiction();
    c.setEvidenceId("vase");
    c.setNextState("TRUTH");
    c.setRewardDeductionId("valet_lied");
    c.setSuccessMessage("Caught");
    c.successMessageText().set("ru", "Попался");

    CombineRuleDraft combine = draft.addCombineRule();
    combine.setRequires(List.of("vase", "valet_lied"));
    combine.setResultDeductionId("done");
    combine.setTokenReward(2);
    combine.setRepeatable(true);
    combine.resultTextLocalized().set("en", "x");
    combine.resultTextLocalized().set("ru", "икс");

    RankTierDraft tier = draft.addRankTier();
    tier.setRankName("Sherlock");
    tier.setMaxDeductions(1);
    tier.winningStatementText().set("en", "Bravo");

    draft.addTask("Find");
    draft.getTaskTexts().get(0).set("ru", "Найди");

    FinalExamQuestionDraft question = draft.addExamQuestion();
    question.promptText().set("en", "Who");
    ExamSlotDraft slot = question.addSlot();
    slot.addChoice("c1").textLocalized().set("en", "Alice");
    ExamChoiceDraft bob = slot.addChoice("c2");
    slot.setCorrectChoice(bob);

    CaseDraft loaded = roundTrip(draft);

    // Metadata + graph.
    assertEquals("Sapphire", loaded.getUniversalTitle());
    assertEquals("AG", loaded.getAuthor());
    assertEquals(Integer.valueOf(5), loaded.getStartingInsightTokens());
    assertEquals(2, loaded.getRooms().size());
    RoomDraft loadedHall = room(loaded, "Hall");
    assertEquals("Terrace", loadedHall.getNeighbors().get("east").getName());
    assertEquals("Hall", loaded.getStartingRoom().getName());
    assertTrue(loaded.getLanguages().contains("ru"));
    assertEquals("Русский", loaded.getLanguageName("ru"));
    assertEquals("Т", loaded.titleText().get("ru"));

    // Object text per language.
    ObjectDraft loadedVase = loadedHall.getObjects().get(0);
    assertEquals("images/vase.png", loadedVase.getImagePath());
    assertEquals("fine", loadedVase.examineText().get("en"));
    assertEquals("прекрасно", loadedVase.examineText().get("ru"));

    // Suspect placement + state machine.
    SuspectDraft loadedValet = loaded.getSuspects().get(0);
    assertEquals("Terrace", loadedValet.getHomeRoom().getName());
    assertTrue(loadedValet.isStationary());
    assertEquals("LIE", loadedValet.getInitialState());
    SuspectStateDraft lie = loadedValet.state("LIE");
    assertEquals("I was home", lie.statementText().get("en"));
    assertEquals("Я был дома", lie.statementText().get("ru"));
    ContradictionDraft loadedRule = lie.getContradictions().get(0);
    assertEquals("vase", loadedRule.getEvidenceId());
    assertEquals("valet_lied", loadedRule.getRewardDeductionId());
    assertEquals("Caught", loadedRule.successMessageText().get("en"));
    assertEquals("Попался", loadedRule.successMessageText().get("ru"));

    // Combine, rank, task, exam.
    CombineRuleDraft loadedCombine = loaded.getCombineRules().get(0);
    assertEquals(List.of("vase", "valet_lied"), loadedCombine.getRequires());
    assertEquals(Integer.valueOf(2), loadedCombine.getTokenReward());
    assertTrue(loadedCombine.isRepeatable());
    assertEquals("икс", loadedCombine.resultTextLocalized().get("ru"));
    assertEquals("Sherlock", loaded.getRankTiers().get(0).getRankName());
    assertEquals(1, loaded.getRankTiers().get(0).getMaxDeductions());
    assertEquals("Bravo", loaded.getRankTiers().get(0).winningStatementText().get("en"));
    assertEquals("Find", loaded.getTaskTexts().get(0).get("en"));
    assertEquals("Найди", loaded.getTaskTexts().get(0).get("ru"));
    FinalExamQuestionDraft loadedQ = loaded.getExamQuestions().get(0);
    assertEquals("Who", loadedQ.promptText().get("en"));
    ExamSlotDraft loadedSlot = loadedQ.getSlots().get(0);
    assertEquals("c2", loadedSlot.getCorrectChoice().getChoiceId());
    assertEquals("Alice", loadedSlot.getChoices().get(0).textLocalized().get("en"));
  }

  @Test
  public void anExistingMultiLanguageCaseOpensAndStillValidates() {
    // The migrated sapphire fixture (en + ru, suspects with home rooms) loads, edits as a draft,
    // and
    // re-exports clean — the slice-7 open/edit acceptance.
    List<CaseFile> cases = CaseLoader.loadCases("testcases");
    CaseFile sapphire =
        cases.stream()
            .filter(c -> "The Stolen Sapphire".equals(c.getUniversalTitle()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("sapphire fixture not found"));

    CaseDraft draft = CaseDraftLoader.load(sapphire);

    assertTrue(draft.getLanguages().contains("ru"));
    assertEquals(2, draft.getRooms().size());
    assertFalse(draft.getSuspects().isEmpty());
    assertEquals("Terrace", suspectByName(draft, "LordAshworth").getHomeRoom().getName());
    assertFalse(
        "a re-opened, migrated case re-serializes with no validation errors",
        CaseExporter.validate(draft).hasErrors());
  }

  private SuspectDraft suspectByName(CaseDraft draft, String name) {
    return draft.getSuspects().stream()
        .filter(s -> name.equals(s.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no suspect " + name));
  }

  private RoomDraft room(CaseDraft draft, String name) {
    return draft.getRooms().stream()
        .filter(r -> name.equals(r.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no room " + name));
  }
}

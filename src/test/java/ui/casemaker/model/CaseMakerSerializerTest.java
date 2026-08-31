package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.Test;

/**
 * The serializer's contract is that a draft renders to JSON the existing {@code CaseLoader} can
 * parse. These tests serialize a slice-1 draft and read it back through the same Jackson
 * configuration the loader uses, asserting the metadata and room graph survive the round trip.
 */
public class CaseMakerSerializerTest {

  /** Same configuration {@code CaseLoader} parses cases with. */
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private CaseFile roundTrip(CaseDraft draft) throws Exception {
    String json = CaseMakerSerializer.toJson(draft);
    return MAPPER.readValue(json, CaseFile.class);
  }

  @Test
  public void metadataAndRoomGraphSurviveTheRoundTrip() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("The Stolen Sapphire");
    draft.setAuthor("A. Author");
    draft.setStartingInsightTokens(5);
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    ballroom.setImagePath("images/ballroom.jpg");
    draft.linkRooms(ballroom, "east", terrace);
    draft.setStartingRoom(ballroom);

    CaseFile parsed = roundTrip(draft);

    assertEquals("The Stolen Sapphire", parsed.getUniversalTitle());
    assertNotNull(parsed.getMetadata());
    assertEquals("A. Author", parsed.getMetadata().getAuthor());
    assertEquals(Integer.valueOf(5), parsed.getStartingInsightTokens());
    assertEquals("Ballroom", parsed.getStartingRoom());
    assertEquals(2, parsed.getRooms().size());
  }

  @Test
  public void neighbourLinksAreEmittedBidirectionally() throws Exception {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    draft.linkRooms(ballroom, "east", terrace);
    draft.setStartingRoom(ballroom);

    CaseFile parsed = roundTrip(draft);

    Map<String, String> ballroomNeighbors = findRoom(parsed, "Ballroom").getNeighbors();
    Map<String, String> terraceNeighbors = findRoom(parsed, "Terrace").getNeighbors();
    assertEquals("Terrace", ballroomNeighbors.get("east"));
    assertEquals("Ballroom", terraceNeighbors.get("west"));
  }

  private CaseFile.RoomData findRoom(CaseFile caseFile, String name) {
    return caseFile.getRooms().stream()
        .filter(r -> name.equals(r.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("room not found: " + name));
  }

  @Test
  public void objectPlacementAndScaleSurviveSerializeThenLocalizedMerge() throws Exception {
    CaseDraft draft = new CaseDraft();
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    ObjectDraft vase = hall.addObject("Ming vase");
    vase.setImagePath("images/vase.png");
    vase.setPosition(0.5, 0.4);
    vase.setImageScale(1.75);
    vase.setExamine("A priceless vase, chipped at the rim.");
    vase.setDeduce("The chip is fresh.");

    String json = CaseMakerSerializer.toJson(draft);
    CaseFile parsed = MAPPER.readValue(json, CaseFile.class);

    // The universal stub carries the render fields.
    CaseFile.GameObjectData stub = findRoom(parsed, "Hall").getObjects().get(0);
    assertEquals("ming_vase", stub.getId());

    // Through the single-language view (the path the engine actually consumes), placement, scale,
    // and the localized examine text all resolve — exercising the DEC-4 fix end to end.
    LocalizedCaseFile localized = new LocalizedCaseFile(parsed, "en");
    CaseFile.GameObjectData merged = localized.getRooms().get(0).getObjects().get(0);
    assertEquals(0.5, merged.getPosX(), 1e-9);
    assertEquals(0.4, merged.getPosY(), 1e-9);
    // A uniform setImageScale writes both axes; the merge carries them through.
    assertEquals(Double.valueOf(1.75), merged.getImageScaleX());
    assertEquals(Double.valueOf(1.75), merged.getImageScaleY());
    assertEquals("A priceless vase, chipped at the rim.", merged.getExamine());
  }

  @Test
  public void suspectPlacementAndStateMachineAreSerialized() throws Exception {
    CaseDraft draft = new CaseDraft();
    RoomDraft parlour = draft.addRoom("Parlour");
    draft.addRoom("Hall");
    draft.setStartingRoom(parlour);
    SuspectDraft valet = draft.addSuspect("The Valet");
    valet.setHomeRoom(parlour);
    valet.setPosition(0.4, 0.55);
    valet.setStationary(true);
    valet.setInitialState("LIE");
    valet.state("LIE").setStatement("I was in the kitchen all night.");
    ContradictionDraft rule = valet.state("LIE").addContradiction();
    rule.setEvidenceId("muddy_boot");
    rule.setNextState("TRUTH");
    rule.setRewardDeductionId("valet_was_outside");
    rule.setSuccessMessage("His alibi crumbles.");
    valet.state("TRUTH").setStatement("Fine — I stepped out for air.");

    CaseFile parsed = MAPPER.readValue(CaseMakerSerializer.toJson(draft), CaseFile.class);

    CaseFile.SuspectData s = parsed.getLocalizations().get("en").getSuspects().get(0);
    assertEquals("Parlour", s.getHomeRoom());
    assertEquals(0.4, s.getPosX(), 1e-9);
    assertEquals(0.55, s.getPosY(), 1e-9);
    assertEquals(true, s.isStationary());
    assertEquals("LIE", s.getInitialState());
    assertEquals("I was in the kitchen all night.", s.getStates().get("LIE").getStatement());
    CaseFile.ContradictionRule c = s.getStates().get("LIE").getContradictions().get(0);
    assertEquals("muddy_boot", c.getEvidenceId());
    assertEquals("TRUTH", c.getNextState());
    assertEquals("valet_was_outside", c.getRewardDeductionId());
  }

  /**
   * Watson's sprite scale (Case Maker placement tab) round-trips through {@code
   * metadata.watsonImageScale}: serializer → parse → the single-language view the engine consumes →
   * loader. The default 1.0 is omitted so untouched cases stay clean.
   */
  @Test
  public void watsonImageScaleRoundTripsThroughMetadata() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("A Case");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    draft.setWatsonImageScale(2.3); // uniform → both axes

    CaseFile parsed = roundTrip(draft);
    assertEquals(Double.valueOf(2.3), parsed.getMetadata().getWatsonImageScaleX());
    assertEquals(Double.valueOf(2.3), parsed.getMetadata().getWatsonImageScaleY());

    // The single-language adapter (what the engine reads) surfaces the scale.
    LocalizedCaseFile localized = new LocalizedCaseFile(parsed, "en");
    assertEquals(2.3, localized.getWatsonImageScaleX(), 1e-9);

    // Loader reads it back into an editable draft.
    CaseDraft reloaded = CaseDraftLoader.load(parsed);
    assertEquals(2.3, reloaded.getWatsonImageScaleX(), 1e-9);
  }

  /**
   * Per-room Dr. Watson positions (Case Maker placement tab) round-trip on {@code
   * rooms[].watsonPos}: serializer → parse → the single-language view the engine reads → loader.
   * Unset rooms omit the fields entirely.
   */
  @Test
  public void perRoomWatsonPositionRoundTrips() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("A Case");
    RoomDraft hall = draft.addRoom("Hall");
    draft.addRoom("Study"); // left unplaced
    draft.setStartingRoom(hall);
    hall.setWatsonPosition(0.9, 0.5);

    CaseFile parsed = roundTrip(draft);
    assertEquals(Double.valueOf(0.9), findRoom(parsed, "Hall").getWatsonPosX());
    assertEquals(Double.valueOf(0.5), findRoom(parsed, "Hall").getWatsonPosY());
    assertEquals(null, findRoom(parsed, "Study").getWatsonPosX());

    // The single-language adapter the engine consumes carries the per-room position through.
    LocalizedCaseFile localized = new LocalizedCaseFile(parsed, "en");
    CaseFile.RoomData mergedHall =
        localized.getRooms().stream()
            .filter(r -> "Hall".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Hall missing"));
    assertEquals(Double.valueOf(0.9), mergedHall.getWatsonPosX());
    assertEquals(Double.valueOf(0.5), mergedHall.getWatsonPosY());

    // Loader reads them back into editable rooms; the unplaced room stays null.
    CaseDraft reloaded = CaseDraftLoader.load(parsed);
    assertEquals(Double.valueOf(0.9), reloaded.getRooms().get(0).getWatsonPosX());
    assertEquals(Double.valueOf(0.5), reloaded.getRooms().get(0).getWatsonPosY());
    assertEquals(null, reloaded.getRooms().get(1).getWatsonPosX());
  }

  /**
   * Authored name-label offsets round-trip for objects (via the localized merge), suspects, and
   * Watson (metadata): serializer → parse → single-language view → loader.
   */
  @Test
  public void labelOffsetsRoundTripForObjectsSuspectsAndWatson() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("A Case");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    ObjectDraft vase = hall.addObject("vase");
    vase.setPosition(0.5, 0.4);
    vase.setLabelOffset(0.3, -0.8);
    SuspectDraft valet = draft.addSuspect("The Valet");
    valet.setHomeRoom(hall);
    valet.setLabelOffset(-0.2, 0.9);
    draft.setWatsonLabelOffset(0.1, 1.2);

    CaseFile parsed = roundTrip(draft);

    CaseFile.GameObjectData stub = findRoom(parsed, "Hall").getObjects().get(0);
    assertEquals(0.3, stub.getLabelDX(), 1e-9);
    assertEquals(-0.8, stub.getLabelDY(), 1e-9);
    CaseFile.SuspectData s = parsed.getLocalizations().get("en").getSuspects().get(0);
    assertEquals(-0.2, s.getLabelDX(), 1e-9);
    assertEquals(0.9, s.getLabelDY(), 1e-9);
    assertEquals(0.1, parsed.getMetadata().getWatsonLabelDX(), 1e-9);
    assertEquals(1.2, parsed.getMetadata().getWatsonLabelDY(), 1e-9);

    // The single-language adapter the engine reads carries the object offset (through the merge)
    // and Watson's offset.
    LocalizedCaseFile localized = new LocalizedCaseFile(parsed, "en");
    CaseFile.GameObjectData merged = localized.getRooms().get(0).getObjects().get(0);
    assertEquals(0.3, merged.getLabelDX(), 1e-9);
    assertEquals(0.1, localized.getWatsonLabelDX(), 1e-9);
    assertEquals(1.2, localized.getWatsonLabelDY(), 1e-9);

    // Loader reads them all back into an editable draft.
    CaseDraft reloaded = CaseDraftLoader.load(parsed);
    ObjectDraft ro = reloaded.getRooms().get(0).getObjects().get(0);
    assertEquals(0.3, ro.getLabelDX(), 1e-9);
    assertEquals(-0.8, ro.getLabelDY(), 1e-9);
    SuspectDraft rs = reloaded.getSuspects().get(0);
    assertEquals(-0.2, rs.getLabelDX(), 1e-9);
    assertEquals(0.9, rs.getLabelDY(), 1e-9);
    assertEquals(0.1, reloaded.getWatsonLabelDX(), 1e-9);
    assertEquals(1.2, reloaded.getWatsonLabelDY(), 1e-9);
  }

  /**
   * Independent X/Y scale + flip flags round-trip for objects, suspects, and Watson (metadata):
   * serializer → parse → single-language view → loader. Defaults (1.0 / false) are omitted.
   */
  @Test
  public void flipAndIndependentScaleRoundTrip() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("A Case");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    ObjectDraft vase = hall.addObject("vase");
    vase.setImageScaleX(1.8);
    vase.setImageScaleY(0.6);
    vase.setFlipX(true);
    SuspectDraft valet = draft.addSuspect("The Valet");
    valet.setHomeRoom(hall);
    valet.setImageScaleX(2.2);
    valet.setImageScaleY(2.5);
    valet.setFlipY(true);
    draft.setWatsonImageScaleX(1.3);
    draft.setWatsonImageScaleY(1.7);
    draft.setWatsonFlipX(true);

    CaseFile parsed = roundTrip(draft);

    CaseFile.GameObjectData stub = findRoom(parsed, "Hall").getObjects().get(0);
    assertEquals(1.8, stub.getImageScaleX(), 1e-9);
    assertEquals(0.6, stub.getImageScaleY(), 1e-9);
    assertEquals(Boolean.TRUE, stub.getFlipX());
    assertEquals(null, stub.getFlipY()); // false omitted
    CaseFile.SuspectData s = parsed.getLocalizations().get("en").getSuspects().get(0);
    assertEquals(2.2, s.getImageScaleX(), 1e-9);
    assertEquals(2.5, s.getImageScaleY(), 1e-9);
    assertEquals(Boolean.TRUE, s.getFlipY());
    assertEquals(1.3, parsed.getMetadata().getWatsonImageScaleX(), 1e-9);
    assertEquals(1.7, parsed.getMetadata().getWatsonImageScaleY(), 1e-9);
    assertEquals(Boolean.TRUE, parsed.getMetadata().getWatsonFlipX());

    // The single-language adapter the engine reads carries the object scale/flip + Watson's.
    LocalizedCaseFile localized = new LocalizedCaseFile(parsed, "en");
    CaseFile.GameObjectData merged = localized.getRooms().get(0).getObjects().get(0);
    assertEquals(1.8, merged.getImageScaleX(), 1e-9);
    assertEquals(Boolean.TRUE, merged.getFlipX());
    assertEquals(1.3, localized.getWatsonImageScaleX(), 1e-9);
    assertTrue(localized.isWatsonFlipX());

    // Loader reads them all back.
    CaseDraft reloaded = CaseDraftLoader.load(parsed);
    ObjectDraft ro = reloaded.getRooms().get(0).getObjects().get(0);
    assertEquals(1.8, ro.getImageScaleX(), 1e-9);
    assertEquals(0.6, ro.getImageScaleY(), 1e-9);
    assertTrue(ro.isFlipX());
    assertFalse(ro.isFlipY());
    SuspectDraft rs = reloaded.getSuspects().get(0);
    assertEquals(2.2, rs.getImageScaleX(), 1e-9);
    assertTrue(rs.isFlipY());
    assertEquals(1.3, reloaded.getWatsonImageScaleX(), 1e-9);
    assertEquals(1.7, reloaded.getWatsonImageScaleY(), 1e-9);
    assertTrue(reloaded.isWatsonFlipX());
  }

  /**
   * Sprite rotation (Case Maker placement rotation grips) round-trips for objects (via the
   * localized merge), suspects, and Watson (metadata.watsonRotation): serializer → parse →
   * single-language view → loader. The 0 default is omitted so untouched cases stay clean.
   */
  @Test
  public void rotationRoundTripsForObjectsSuspectsAndWatson() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setUniversalTitle("A Case");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    ObjectDraft vase = hall.addObject("vase");
    vase.setRotation(30);
    SuspectDraft valet = draft.addSuspect("The Valet");
    valet.setHomeRoom(hall);
    valet.setRotation(-45);
    draft.setWatsonRotation(90);

    CaseFile parsed = roundTrip(draft);

    CaseFile.GameObjectData stub = findRoom(parsed, "Hall").getObjects().get(0);
    assertEquals(30.0, stub.getRotation(), 1e-9);
    CaseFile.SuspectData s = parsed.getLocalizations().get("en").getSuspects().get(0);
    assertEquals(-45.0, s.getRotation(), 1e-9);
    assertEquals(90.0, parsed.getMetadata().getWatsonRotation(), 1e-9);

    // The single-language adapter the engine reads carries the object rotation (through the merge)
    // and Watson's rotation.
    LocalizedCaseFile localized = new LocalizedCaseFile(parsed, "en");
    CaseFile.GameObjectData merged = localized.getRooms().get(0).getObjects().get(0);
    assertEquals(30.0, merged.getRotation(), 1e-9);
    assertEquals(90.0, localized.getWatsonRotation(), 1e-9);

    // Loader reads them all back into an editable draft.
    CaseDraft reloaded = CaseDraftLoader.load(parsed);
    assertEquals(30.0, reloaded.getRooms().get(0).getObjects().get(0).getRotation(), 1e-9);
    assertEquals(-45.0, reloaded.getSuspects().get(0).getRotation(), 1e-9);
    assertEquals(90.0, reloaded.getWatsonRotation(), 1e-9);
  }

  @Test
  public void defaultRotationIsNotEmitted() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setAuthor("A. Author");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    hall.addObject("vase"); // rotation left at the 0 default
    SuspectDraft valet = draft.addSuspect("The Valet");
    valet.setHomeRoom(hall); // rotation left at 0

    CaseFile parsed = roundTrip(draft);
    assertEquals(null, findRoom(parsed, "Hall").getObjects().get(0).getRotation());
    assertEquals(null, parsed.getLocalizations().get("en").getSuspects().get(0).getRotation());
    assertEquals(null, parsed.getMetadata().getWatsonRotation());
  }

  @Test
  public void defaultWatsonImageScaleIsNotEmitted() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.setAuthor("A. Author"); // give metadata some content so the block is emitted
    draft.addRoom("Hall");
    // watsonImageScale left at the 1.0 default.
    CaseFile parsed = roundTrip(draft);
    assertEquals(null, parsed.getMetadata().getWatsonImageScale());
    // The adapter still reports the 1.0 engine default.
    assertEquals(1.0, new LocalizedCaseFile(parsed, "en").getWatsonImageScale(), 1e-9);
  }

  @Test
  public void caseLogicAndContentAreSerialized() throws Exception {
    CaseDraft draft = new CaseDraft();
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    hall.addObject("Key"); // id "key"
    hall.addObject("Book"); // id "book"

    CombineRuleDraft combine = draft.addCombineRule();
    combine.setRequires(java.util.List.of("key", "book"));
    combine.setResultDeductionId("key_and_book");
    combine.setResultText("They fit together.");
    combine.setTokenReward(2);
    combine.setRepeatable(true);

    draft.addTask("Find the thief.");

    WatsonHintDraft hint = draft.addWatsonHint();
    hint.setCategory("general");
    hint.setId("w1");
    hint.setText("Look closer at the desk.");

    RankTierDraft tier = draft.addRankTier();
    tier.setRankName("Sherlock");
    tier.setMaxDeductions(1);
    tier.setWinningStatement("Flawless.");

    FinalExamQuestionDraft question = draft.addExamQuestion();
    question.setPrompt("The thief is ____.");
    ExamSlotDraft slot = question.addSlot(); // slot1
    slot.addChoice("c1").setText("Alice");
    ExamChoiceDraft bob = slot.addChoice("c2");
    bob.setText("Bob");
    slot.setCorrectChoice(bob);

    CaseFile parsed = MAPPER.readValue(CaseMakerSerializer.toJson(draft), CaseFile.class);

    CaseFile.CombineRule cr = parsed.getCombineLogic().get(0);
    assertEquals(java.util.List.of("key", "book"), cr.getRequires());
    assertEquals("key_and_book", cr.getResultDeductionId());
    assertEquals("They fit together.", cr.getResultText().get("en"));
    assertEquals(Integer.valueOf(2), cr.getTokenReward());
    assertEquals(true, cr.isRepeatable());

    CaseFile.WatsonHint wh = parsed.getWatson().getHints().get("general").get(0);
    assertEquals("w1", wh.getId());
    assertEquals("Look closer at the desk.", wh.getText().get("en"));

    CaseFile.LocalizedData en = parsed.getLocalizations().get("en");
    assertEquals(java.util.List.of("Find the thief."), en.getTasks());
    assertEquals("Sherlock", en.getRankingTiers().get(0).getRankName());
    assertEquals("Flawless.", en.getRankingTiers().get(0).getWinningStatement());

    common.dto.FinalExamQuestionDTO eq = en.getFinalExam().getQuestions().get(0);
    assertEquals("The thief is ____.", eq.getQuestionPrompt());
    assertEquals(2, eq.getSlots().get("slot1").getChoices().size());
    assertEquals("c2", eq.getCorrectCombination().get("slot1"));
  }

  @Test
  public void everyLanguageGetsItsOwnLocalizationBlock() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.addLanguage("ru");
    draft.setLanguageName("en", "English");
    draft.setLanguageName("ru", "Русский");
    draft.titleText().set("en", "The Stolen Sapphire");
    draft.titleText().set("ru", "Похищенный сапфир");
    draft.invitationText().set("en", "Come at once.");
    draft.invitationText().set("ru", "Приезжайте немедленно.");
    RoomDraft hall = draft.addRoom("Hall");
    draft.setStartingRoom(hall);
    ObjectDraft vase = hall.addObject("vase");
    vase.examineText().set("en", "A fine vase.");
    vase.examineText().set("ru", "Прекрасная ваза.");

    CaseFile parsed = MAPPER.readValue(CaseMakerSerializer.toJson(draft), CaseFile.class);

    CaseFile.LocalizedData en = parsed.getLocalizations().get("en");
    CaseFile.LocalizedData ru = parsed.getLocalizations().get("ru");
    assertEquals("The Stolen Sapphire", en.getTitle());
    assertEquals("Похищенный сапфир", ru.getTitle());
    assertEquals("Русский", ru.getLanguageName());
    assertEquals("A fine vase.", findObjectDetail(en, "vase").getExamine());
    assertEquals("Прекрасная ваза.", findObjectDetail(ru, "vase").getExamine());
  }

  private CaseFile.ObjectDetailData findObjectDetail(CaseFile.LocalizedData loc, String name) {
    return loc.getObjectDetails().stream()
        .filter(o -> name.equals(o.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no objectDetail for " + name));
  }

  private CaseFile.RoomDetailData findRoomDetail(CaseFile.LocalizedData loc, String name) {
    return loc.getRoomDetails().stream()
        .filter(r -> name.equals(r.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no roomDetail for " + name));
  }

  /**
   * Per-language Display Names (.scratch/gui-localized-case-names) ride in the localized blocks
   * alongside the existing localized text and survive a serialize → parse → load round trip, while
   * the Universal Name (.name) is unchanged.
   */
  @Test
  public void displayNamesRoundTripThroughSerializerAndLoader() throws Exception {
    CaseDraft draft = new CaseDraft();
    draft.addLanguage("ar");
    RoomDraft study = draft.addRoom("Study");
    draft.setStartingRoom(study);
    study.displayNameText().set("ar", "المكتب");
    ObjectDraft letter = study.addObject("letter");
    letter.displayNameText().set("ar", "الرسالة");
    SuspectDraft eleanor = draft.addSuspect("LadyEleanor");
    eleanor.setHomeRoom(study);
    eleanor.displayNameText().set("ar", "الليدي إلينور");

    CaseFile parsed = MAPPER.readValue(CaseMakerSerializer.toJson(draft), CaseFile.class);

    // Serializer emits displayName into the localized blocks; Universal Name (.name) untouched.
    CaseFile.LocalizedData ar = parsed.getLocalizations().get("ar");
    assertEquals("Study", findRoomDetail(ar, "Study").getName());
    assertEquals("المكتب", findRoomDetail(ar, "Study").getDisplayName());
    assertEquals("الرسالة", findObjectDetail(ar, "letter").getDisplayName());
    assertEquals("الليدي إلينور", ar.getSuspects().get(0).getDisplayName());

    // Loader reads them back, per language.
    CaseDraft reloaded = CaseDraftLoader.load(parsed);
    RoomDraft reStudy = reloaded.getRooms().get(0);
    assertEquals("المكتب", reStudy.displayNameText().get("ar"));
    assertEquals("الرسالة", reStudy.getObjects().get(0).displayNameText().get("ar"));
    assertEquals("الليدي إلينور", reloaded.getSuspects().get(0).displayNameText().get("ar"));
  }
}

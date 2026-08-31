package ui.casemaker.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The root mutable model edited by the Case Maker. A {@code CaseDraft} is a purpose-built authoring
 * model (see DECISIONS DEC-1) — not the immutable {@code CaseFile} deserialization DTO — so that
 * editor operations map cleanly to author actions and registry-backed integrity falls out
 * naturally. A serializer maps a finished draft to {@code CaseFile}-shaped JSON.
 *
 * <p>Slice 1 covers case metadata and the room graph: add/rename rooms, link neighbours
 * bidirectionally, choose a starting room, and check connectivity.
 */
public final class CaseDraft {

  // --- Case metadata (universal) ---
  private String universalTitle;
  private String title;
  private String author;
  // Author-defined character names. The primary-language value serializes to metadata.detectiveName
  // / metadata.helperName (the default for every language); per-language overrides serialize into
  // each localizations.<lang>. Blank → the engine's defaults (Sherlock Holmes / Dr. Watson).
  private final LocalizedText detectiveName = new LocalizedText();
  private final LocalizedText helperName = new LocalizedText();
  private String watsonImagePath;
  // Dr. Watson's sprite scale, serialized as metadata.watsonImageScale. Watson has no per-case
  // suspect entry, so this is the one knob for his in-room size; 1.0 means "match the room's other
  // suspects" (the engine's default). Position stays engine-controlled (Watson follows the player).
  // Dr. Watson's independent sprite scale (both default 1.0) + horizontal/vertical flip. Global
  // (metadata.watsonImageScaleX/Y, metadata.watsonFlipX/Y) since Watson has no per-case suspect
  // entry; 1.0 means "match the room's other suspects" (the engine's default).
  private double watsonImageScaleX = 1.0;
  private double watsonImageScaleY = 1.0;
  private boolean watsonFlipX;
  private boolean watsonFlipY;
  // Watson's clockwise sprite rotation in degrees (global; serialized as metadata.watsonRotation).
  // Default 0 (upright).
  private double watsonRotation;
  // Watson's name-label offset from his sprite centre (fraction of sprite height), serialized as
  // metadata.watsonLabelDX/DY. Global like his scale; null = default "just below the sprite".
  private Double watsonLabelDX;
  private Double watsonLabelDY;
  private String soundtrack;
  private Integer startingInsightTokens;

  // Languages the case is authored in (slice 5). Always starts with the primary language; the
  // Localization tab adds/removes more. languageNames holds each language's display name.
  private final List<String> languages = new ArrayList<>(List.of(LocalizedText.PRIMARY));
  private final java.util.Map<String, String> languageNames = new java.util.LinkedHashMap<>();

  // The language the content editors currently show/edit (the "Editing language" selector in the
  // sidebar). Purely an editor-session preference — NOT serialized and unrelated to the interface
  // language. Null means "the primary language". See getAuthoringLanguage().
  private String authoringLanguage;

  // Case-level localized text (localizations.<lang>.{title,invitation,description}).
  private final LocalizedText localizedTitle = new LocalizedText();
  private final LocalizedText invitation = new LocalizedText();
  private final LocalizedText description = new LocalizedText();

  // --- Room graph ---
  private final List<RoomDraft> rooms = new ArrayList<>();
  private RoomDraft startingRoom;

  // --- Suspects ---
  private final List<SuspectDraft> suspects = new ArrayList<>();

  // --- Case logic & content (slice 4) ---
  private final List<CombineRuleDraft> combineRules = new ArrayList<>();
  private final List<LocalizedText> tasks = new ArrayList<>();
  private final List<WatsonHintDraft> watsonHints = new ArrayList<>();
  private final List<RankTierDraft> rankTiers = new ArrayList<>();
  private final List<FinalExamQuestionDraft> examQuestions = new ArrayList<>();

  // The full, original parsed JSON of a loaded case (or null for a brand-new draft). The Case Maker
  // only models a subset of the on-disk schema; on export the serializer overlays the edited fields
  // onto a copy of this so top-level blocks the editor does NOT model (case_file, red_herrings,
  // leads, and any future unknown keys) pass through verbatim instead of being silently stripped
  // (.scratch/case-maker preserve-unmodeled).
  private com.fasterxml.jackson.databind.JsonNode originalRoot;

  // The folder the case was loaded from (its images/ live here). Null for a brand-new draft. Used by
  // the editor's validation to confirm image paths that resolve on disk, so it doesn't warn about a
  // picked/relative image that actually exists (pre-export).
  private java.nio.file.Path sourceDir;
  // The exact JSON file the case was loaded from, so "Save" can overwrite it in place. Null for a
  // brand-new draft (which must be exported to create its file first).
  private java.nio.file.Path sourceFile;

  // --- Metadata accessors ---

  public java.nio.file.Path getSourceDir() {
    return sourceDir;
  }

  public void setSourceDir(java.nio.file.Path sourceDir) {
    this.sourceDir = sourceDir;
  }

  public java.nio.file.Path getSourceFile() {
    return sourceFile;
  }

  public void setSourceFile(java.nio.file.Path sourceFile) {
    this.sourceFile = sourceFile;
  }

  /**
   * The original parsed JSON of the loaded case, retained so a load→export round-trip preserves any
   * top-level blocks the editor does not model. Null for a new draft.
   */
  public com.fasterxml.jackson.databind.JsonNode getOriginalRoot() {
    return originalRoot;
  }

  public void setOriginalRoot(com.fasterxml.jackson.databind.JsonNode originalRoot) {
    this.originalRoot = originalRoot;
  }

  public String getUniversalTitle() {
    return universalTitle;
  }

  public void setUniversalTitle(String universalTitle) {
    this.universalTitle = universalTitle;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  /** Primary-language detective name (the details form); per-language via {@link #detectiveNameText()}. */
  public String getDetectiveName() {
    return detectiveName.get();
  }

  public void setDetectiveName(String detectiveName) {
    this.detectiveName.set(detectiveName);
  }

  public LocalizedText detectiveNameText() {
    return detectiveName;
  }

  /** Primary-language helper name (the details form); per-language via {@link #helperNameText()}. */
  public String getHelperName() {
    return helperName.get();
  }

  public void setHelperName(String helperName) {
    this.helperName.set(helperName);
  }

  public LocalizedText helperNameText() {
    return helperName;
  }

  public String getWatsonImagePath() {
    return watsonImagePath;
  }

  public void setWatsonImagePath(String watsonImagePath) {
    this.watsonImagePath = watsonImagePath;
  }

  /** Watson's horizontal sprite scale (representative uniform value for legacy callers). */
  public double getWatsonImageScale() {
    return watsonImageScaleX;
  }

  /** Sets a uniform Watson scale (both axes); non-positive/non-finite values are ignored. */
  public void setWatsonImageScale(double scale) {
    setWatsonImageScaleX(scale);
    setWatsonImageScaleY(scale);
  }

  public double getWatsonImageScaleX() {
    return watsonImageScaleX;
  }

  public double getWatsonImageScaleY() {
    return watsonImageScaleY;
  }

  public void setWatsonImageScaleX(double scale) {
    if (scale > 0 && Double.isFinite(scale)) {
      this.watsonImageScaleX = scale;
    }
  }

  public void setWatsonImageScaleY(double scale) {
    if (scale > 0 && Double.isFinite(scale)) {
      this.watsonImageScaleY = scale;
    }
  }

  public boolean isWatsonFlipX() {
    return watsonFlipX;
  }

  public boolean isWatsonFlipY() {
    return watsonFlipY;
  }

  public void setWatsonFlipX(boolean flipX) {
    this.watsonFlipX = flipX;
  }

  public void setWatsonFlipY(boolean flipY) {
    this.watsonFlipY = flipY;
  }

  /** Watson's clockwise sprite rotation in degrees (global; 0 = upright). */
  public double getWatsonRotation() {
    return watsonRotation;
  }

  /** Sets Watson's sprite rotation in degrees; non-finite values are ignored. */
  public void setWatsonRotation(double rotation) {
    if (Double.isFinite(rotation)) {
      this.watsonRotation = rotation;
    }
  }

  public Double getWatsonLabelDX() {
    return watsonLabelDX;
  }

  public Double getWatsonLabelDY() {
    return watsonLabelDY;
  }

  /** Sets Watson's name-label offset (fraction of sprite height), clamped to a sane range. */
  public void setWatsonLabelOffset(double dx, double dy) {
    this.watsonLabelDX = Math.max(-4.0, Math.min(4.0, dx));
    this.watsonLabelDY = Math.max(-4.0, Math.min(4.0, dy));
  }

  /** Clears Watson's authored label offset (back to the default "just below the sprite"). */
  public void clearWatsonLabelOffset() {
    this.watsonLabelDX = null;
    this.watsonLabelDY = null;
  }

  public String getSoundtrack() {
    return soundtrack;
  }

  public void setSoundtrack(String soundtrack) {
    this.soundtrack = soundtrack;
  }

  public Integer getStartingInsightTokens() {
    return startingInsightTokens;
  }

  public void setStartingInsightTokens(Integer startingInsightTokens) {
    this.startingInsightTokens = startingInsightTokens;
  }

  // --- Languages & case-level localized text (slice 5) ---

  /** The languages this case is authored in (primary first). */
  public List<String> getLanguages() {
    return List.copyOf(languages);
  }

  /**
   * The language the content editors currently display/edit. Falls back to the primary language when
   * unset, or when the previously-chosen language has since been removed. Independent of the UI
   * language.
   */
  public String getAuthoringLanguage() {
    if (authoringLanguage != null && languages.contains(authoringLanguage)) {
      return authoringLanguage;
    }
    return languages.isEmpty() ? LocalizedText.PRIMARY : languages.get(0);
  }

  /** Sets the editor's authoring language (an editor-session preference; never serialized). */
  public void setAuthoringLanguage(String code) {
    this.authoringLanguage = code;
  }

  public void addLanguage(String code) {
    if (code != null && !code.isBlank() && !languages.contains(code.trim())) {
      languages.add(code.trim());
    }
  }

  /** Removes a non-primary language (the primary language can't be removed). */
  public void removeLanguage(String code) {
    if (!LocalizedText.PRIMARY.equals(code)) {
      languages.remove(code);
      languageNames.remove(code);
    }
  }

  public String getLanguageName(String code) {
    return languageNames.get(code);
  }

  public void setLanguageName(String code, String name) {
    if (name == null || name.isBlank()) {
      languageNames.remove(code);
    } else {
      languageNames.put(code, name);
    }
  }

  public LocalizedText titleText() {
    return localizedTitle;
  }

  public LocalizedText invitationText() {
    return invitation;
  }

  public LocalizedText descriptionText() {
    return description;
  }

  // --- Room graph operations ---

  public List<RoomDraft> getRooms() {
    return List.copyOf(rooms);
  }

  public RoomDraft getStartingRoom() {
    return startingRoom;
  }

  public void setStartingRoom(RoomDraft room) {
    if (room != null && !rooms.contains(room)) {
      throw new IllegalArgumentException("Starting room must be a room in this case.");
    }
    this.startingRoom = room;
  }

  /**
   * Adds a new room. The first room added becomes the starting room by default (the author can
   * change it). Returns the created {@link RoomDraft} so callers can link it immediately.
   */
  public RoomDraft addRoom(String name) {
    RoomDraft room = new RoomDraft(name);
    rooms.add(room);
    if (startingRoom == null) {
      startingRoom = room;
    }
    return room;
  }

  /**
   * Links {@code a} —direction→ {@code b}, also setting the reverse link when the direction has an
   * opposite (it always does for the canonical compass/up-down set).
   */
  public void linkRooms(RoomDraft a, String direction, RoomDraft b) {
    requireOwned(a);
    requireOwned(b);
    a.putNeighbor(direction, b);
    String opposite = Directions.opposite(direction);
    if (opposite != null) {
      b.putNeighbor(opposite, a);
    }
  }

  /**
   * Renames a room. Because neighbours are held as references, every link and the starting-room
   * selection automatically reflect the new name; only the {@link RoomDraft}'s own name changes.
   */
  public void renameRoom(RoomDraft room, String newName) {
    requireOwned(room);
    room.setName(newName);
  }

  /**
   * Removes a room from the case, detaching it from every other room's neighbour links. If it was
   * the starting room, the starting-room selection is cleared (the author must choose a new one).
   */
  public void removeRoom(RoomDraft room) {
    requireOwned(room);
    rooms.remove(room);
    for (RoomDraft other : rooms) {
      other.removeNeighborsTo(room);
    }
    if (startingRoom == room) {
      startingRoom = null;
    }
  }

  /** Removes any link in either direction between {@code a} and {@code b}. */
  public void unlinkRooms(RoomDraft a, RoomDraft b) {
    requireOwned(a);
    requireOwned(b);
    a.removeNeighborsTo(b);
    b.removeNeighborsTo(a);
  }

  /**
   * The set of rooms reachable from the starting room by following neighbour links. Empty when no
   * starting room is set.
   */
  public Set<RoomDraft> reachableRooms() {
    Set<RoomDraft> reachable = new LinkedHashSet<>();
    if (startingRoom == null) {
      return reachable;
    }
    List<RoomDraft> stack = new ArrayList<>();
    reachable.add(startingRoom);
    stack.add(startingRoom);
    while (!stack.isEmpty()) {
      RoomDraft current = stack.remove(stack.size() - 1);
      for (RoomDraft neighbor : current.getNeighbors().values()) {
        if (reachable.add(neighbor)) {
          stack.add(neighbor);
        }
      }
    }
    return reachable;
  }

  /** Rooms not reachable from the starting room (in declaration order). */
  public List<RoomDraft> unreachableRooms() {
    Set<RoomDraft> reachable = reachableRooms();
    List<RoomDraft> unreachable = new ArrayList<>();
    for (RoomDraft room : rooms) {
      if (!reachable.contains(room)) {
        unreachable.add(room);
      }
    }
    return unreachable;
  }

  private void requireOwned(RoomDraft room) {
    if (room == null || !rooms.contains(room)) {
      throw new IllegalArgumentException("Room is not part of this case.");
    }
  }

  // --- Suspects -----------------------------------------------------------------------------

  public SuspectDraft addSuspect(String name) {
    SuspectDraft suspect = new SuspectDraft(name);
    suspects.add(suspect);
    return suspect;
  }

  public List<SuspectDraft> getSuspects() {
    return List.copyOf(suspects);
  }

  public void removeSuspect(SuspectDraft suspect) {
    suspects.remove(suspect);
  }

  // --- Combine rules ---

  public CombineRuleDraft addCombineRule() {
    CombineRuleDraft rule = new CombineRuleDraft();
    combineRules.add(rule);
    return rule;
  }

  public List<CombineRuleDraft> getCombineRules() {
    return List.copyOf(combineRules);
  }

  public void removeCombineRule(CombineRuleDraft rule) {
    combineRules.remove(rule);
  }

  // --- Tasks (per-language; primary-language convenience for the non-localization editors) ---

  public void addTask(String task) {
    LocalizedText text = new LocalizedText();
    text.set(task);
    tasks.add(text);
  }

  /**
   * Adds a task whose text is authored in a specific language (the editor's current "Editing
   * language"), leaving the other languages blank — mirrors how the suspect/object editors write
   * only the language being edited.
   */
  public void addTaskFor(String lang, String task) {
    LocalizedText text = new LocalizedText();
    text.set(lang, task);
    tasks.add(text);
  }

  public List<String> getTasks() {
    List<String> primary = new ArrayList<>();
    for (LocalizedText task : tasks) {
      primary.add(task.get());
    }
    return primary;
  }

  /** The tasks as per-language text, in order, for the Localization tab. */
  public List<LocalizedText> getTaskTexts() {
    return List.copyOf(tasks);
  }

  public void removeTask(int index) {
    if (index >= 0 && index < tasks.size()) {
      tasks.remove(index);
    }
  }

  public void setTask(int index, String task) {
    if (index >= 0 && index < tasks.size()) {
      tasks.get(index).set(task);
    }
  }

  // --- Watson hints ---

  public WatsonHintDraft addWatsonHint() {
    WatsonHintDraft hint = new WatsonHintDraft();
    watsonHints.add(hint);
    return hint;
  }

  public List<WatsonHintDraft> getWatsonHints() {
    return List.copyOf(watsonHints);
  }

  public void removeWatsonHint(WatsonHintDraft hint) {
    watsonHints.remove(hint);
  }

  // --- Rank tiers ---

  public RankTierDraft addRankTier() {
    RankTierDraft tier = new RankTierDraft();
    rankTiers.add(tier);
    return tier;
  }

  public List<RankTierDraft> getRankTiers() {
    return List.copyOf(rankTiers);
  }

  public void removeRankTier(RankTierDraft tier) {
    rankTiers.remove(tier);
  }

  // --- Final exam ---

  public FinalExamQuestionDraft addExamQuestion() {
    FinalExamQuestionDraft question = new FinalExamQuestionDraft();
    examQuestions.add(question);
    return question;
  }

  public List<FinalExamQuestionDraft> getExamQuestions() {
    return List.copyOf(examQuestions);
  }

  public void removeExamQuestion(FinalExamQuestionDraft question) {
    examQuestions.remove(question);
  }

  // --- Id registries (feed the editor's dropdowns so references can never dangle, DEC-2) ----

  /** Every authored Object id across all rooms. */
  public Set<String> objectIds() {
    Set<String> ids = new LinkedHashSet<>();
    for (RoomDraft room : rooms) {
      for (ObjectDraft object : room.getObjects()) {
        ids.add(object.getId());
      }
    }
    return ids;
  }

  /**
   * Every Deduction id minted in the case. Slice 3 mints them at Contradiction Rule rewards; slice
   * 4 adds Combine Rule results.
   */
  public Set<String> deductionIds() {
    Set<String> ids = new LinkedHashSet<>();
    for (SuspectDraft suspect : suspects) {
      for (SuspectStateDraft state : suspect.getStates().values()) {
        for (ContradictionDraft rule : state.getContradictions()) {
          addId(ids, rule.getRewardDeductionId());
        }
      }
    }
    for (CombineRuleDraft rule : combineRules) {
      addId(ids, rule.getResultDeductionId());
    }
    return ids;
  }

  private static void addId(Set<String> ids, String id) {
    if (id != null && !id.isBlank()) {
      ids.add(id.trim());
    }
  }

  /**
   * Everything a Contradiction trigger or Combine requirement may reference as Evidence: Object ids
   * ∪ Deduction ids. This is the domain the editor's evidence dropdowns offer.
   */
  public List<String> evidenceChoices() {
    Set<String> all = new LinkedHashSet<>(objectIds());
    all.addAll(deductionIds());
    List<String> sorted = new ArrayList<>(all);
    sorted.sort(String::compareTo);
    return sorted;
  }
}

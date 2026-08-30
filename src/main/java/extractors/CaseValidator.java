package extractors;

import JsonDTO.CaseFile;
import JsonDTO.CaseFile.CombineRule;
import JsonDTO.CaseFile.ContradictionRule;
import JsonDTO.CaseFile.GameObjectData;
import JsonDTO.CaseFile.LocalizedData;
import JsonDTO.CaseFile.ObjectDetailData;
import JsonDTO.CaseFile.RedHerringDetail;
import JsonDTO.CaseFile.RoomData;
import JsonDTO.CaseFile.RoomDetailData;
import JsonDTO.CaseFile.SuspectData;
import JsonDTO.CaseFile.SuspectProfileData;
import JsonDTO.CaseFile.SuspectStateData;
import common.dto.FinalExamChoiceDTO;
import common.dto.FinalExamQuestionDTO;
import common.dto.FinalExamSlotDTO;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural and referential validator for a loaded {@link CaseFile}.
 *
 * <p>Cases are hand-authored JSON. A structurally broken case used to break at runtime, mid-play.
 * This validator turns that into reject-at-load: it walks a parsed case and returns a structured
 * {@link Report} of {@link Issue}s rather than throwing or returning a bare boolean, so both the
 * loader and a CI test can act on the same findings.
 *
 * <p>Severity policy:
 *
 * <ul>
 *   <li><b>ERROR</b> — structural completeness and reference integrity (missing localization/exam,
 *       dangling room/evidence/combine ids, disconnected map). These make a case unplayable and
 *       block it from being offered.
 *   <li><b>WARNING</b> — an {@code imagePath} (or the future {@code soundtrack}) that does not
 *       resolve, and one-way neighbour links. The case still plays (a placeholder shows), so these
 *       are reported but never block loading.
 * </ul>
 */
public final class CaseValidator {

  public enum Severity {
    ERROR,
    WARNING
  }

  /** One located finding. {@code location} is a human-readable path into the case JSON. */
  public record Issue(Severity severity, String location, String message) {
    @Override
    public String toString() {
      return severity + " @ " + location + ": " + message;
    }
  }

  /** The outcome of validating a single case. */
  public static final class Report {
    private final String caseTitle;
    private final List<Issue> issues;

    Report(String caseTitle, List<Issue> issues) {
      this.caseTitle = caseTitle;
      this.issues = List.copyOf(issues);
    }

    public String caseTitle() {
      return caseTitle;
    }

    public List<Issue> issues() {
      return issues;
    }

    public List<Issue> errors() {
      return issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
    }

    public List<Issue> warnings() {
      return issues.stream().filter(i -> i.severity() == Severity.WARNING).toList();
    }

    public boolean hasErrors() {
      return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    /** A case is valid (offerable) when it has no ERROR-level issues. Warnings are tolerated. */
    public boolean isValid() {
      return !hasErrors();
    }
  }

  /** Opposite direction for each canonical neighbour direction, used for reciprocity checks. */
  private static final Map<String, String> OPPOSITE =
      Map.ofEntries(
          Map.entry("north", "south"),
          Map.entry("south", "north"),
          Map.entry("east", "west"),
          Map.entry("west", "east"),
          Map.entry("up", "down"),
          Map.entry("down", "up"),
          Map.entry("northeast", "southwest"),
          Map.entry("southwest", "northeast"),
          Map.entry("northwest", "southeast"),
          Map.entry("southeast", "northwest"));

  private CaseValidator() {}

  /** Validates a case, deriving the case directory from its {@code sourcePath} if present. */
  public static Report validate(CaseFile caseFile) {
    return validate(caseFile, caseDirOf(caseFile));
  }

  /**
   * Validates a case, resolving image paths against {@code caseDir} (and the classpath).
   *
   * @param caseFile the parsed case
   * @param caseDir directory the case JSON lives in, for resolving relative image paths; may be
   *     null
   */
  public static Report validate(CaseFile caseFile, Path caseDir) {
    List<Issue> issues = new ArrayList<>();
    if (caseFile == null) {
      issues.add(new Issue(Severity.ERROR, "case", "Case file is null."));
      return new Report("<null>", issues);
    }

    String title = caseFile.getUniversalTitle();

    // The vocabulary that ids can resolve against. Object ids double as journal-entry source ids,
    // and deductions (combine results, contradiction rewards) become journal sources at runtime.
    Set<String> objectIds = collectObjectIds(caseFile);
    Set<String> objectNames = collectObjectNames(caseFile);
    Set<String> deductionIds = collectDeductionIds(caseFile);
    Set<String> evidenceSources = new HashSet<>(objectIds);
    evidenceSources.addAll(deductionIds);

    validateTitle(caseFile, issues);
    validateLocalizations(caseFile, objectNames, issues);
    validateGraph(caseFile, issues);
    validateCrossReferences(caseFile, objectIds, evidenceSources, issues);
    validateSuspectPlacement(caseFile, universalRoomNames(caseFile), issues);
    validateWatsonHints(caseFile, issues);
    validateImages(caseFile, caseDir, issues);
    validateBudgets(caseFile, issues);

    return new Report(title == null ? "<untitled>" : title, issues);
  }

  // ---- Title ----

  private static void validateTitle(CaseFile caseFile, List<Issue> issues) {
    if (isBlank(caseFile.getUniversalTitle())) {
      issues.add(new Issue(Severity.ERROR, "universal_title", "Missing or blank universal_title."));
    }
  }

  // ---- Localization completeness ----

  private static void validateLocalizations(
      CaseFile caseFile, Set<String> objectNames, List<Issue> issues) {
    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs == null || locs.isEmpty()) {
      issues.add(new Issue(Severity.ERROR, "localizations", "No localizations defined."));
      return;
    }

    Set<String> universalRoomNames = universalRoomNames(caseFile);

    for (Map.Entry<String, LocalizedData> entry : locs.entrySet()) {
      String lang = entry.getKey();
      LocalizedData loc = entry.getValue();
      String base = "localizations." + lang;
      if (loc == null) {
        issues.add(new Issue(Severity.ERROR, base, "Localization '" + lang + "' is empty."));
        continue;
      }

      if (isBlank(loc.getTitle())) {
        issues.add(new Issue(Severity.ERROR, base + ".title", "Missing or blank title."));
      }
      if (isBlank(loc.getInvitation())) {
        issues.add(new Issue(Severity.ERROR, base + ".invitation", "Missing or blank invitation."));
      }

      validateFinalExam(loc, base, issues);
      validateCoverage(loc, universalRoomNames, objectNames, base, issues);
    }
  }

  private static void validateFinalExam(LocalizedData loc, String base, List<Issue> issues) {
    String examPath = base + ".final_exam";
    if (loc.getFinalExam() == null
        || loc.getFinalExam().getQuestions() == null
        || loc.getFinalExam().getQuestions().isEmpty()) {
      issues.add(new Issue(Severity.ERROR, examPath, "Final exam missing or has no questions."));
      return;
    }
    List<FinalExamQuestionDTO> questions = loc.getFinalExam().getQuestions();
    for (int q = 0; q < questions.size(); q++) {
      FinalExamQuestionDTO question = questions.get(q);
      String qPath = examPath + ".questions[" + q + "]";
      Map<String, FinalExamSlotDTO> slots = question.getSlots();
      Map<String, String> combo = question.getCorrectCombination();
      if (combo == null || combo.isEmpty()) {
        issues.add(new Issue(Severity.ERROR, qPath, "Question has no correct_combination."));
        continue;
      }
      for (Map.Entry<String, String> pick : combo.entrySet()) {
        String slotId = pick.getKey();
        String choiceId = pick.getValue();
        FinalExamSlotDTO slot = slots == null ? null : slots.get(slotId);
        if (slot == null) {
          issues.add(
              new Issue(
                  Severity.ERROR,
                  qPath + ".correct_combination",
                  "References slot '" + slotId + "' that does not exist in this question."));
          continue;
        }
        boolean choiceExists =
            slot.getChoices() != null
                && slot.getChoices().stream()
                    .map(FinalExamChoiceDTO::getChoiceId)
                    .anyMatch(id -> id != null && id.equals(choiceId));
        if (!choiceExists) {
          issues.add(
              new Issue(
                  Severity.ERROR,
                  qPath + ".correct_combination",
                  "Slot '" + slotId + "' has no choice '" + choiceId + "'."));
        }
      }
    }
  }

  private static void validateCoverage(
      LocalizedData loc,
      Set<String> universalRoomNames,
      Set<String> objectNames,
      String base,
      List<Issue> issues) {
    Set<String> coveredRooms = new HashSet<>();
    if (loc.getRoomDetails() != null) {
      for (RoomDetailData rd : loc.getRoomDetails()) {
        if (rd != null && rd.getName() != null) {
          coveredRooms.add(rd.getName());
        }
      }
    }
    for (String room : universalRoomNames) {
      if (!coveredRooms.contains(room)) {
        issues.add(
            new Issue(
                Severity.ERROR,
                base + ".roomDetails",
                "No roomDetails entry for room '" + room + "'."));
      }
    }

    Set<String> coveredObjects = new HashSet<>();
    if (loc.getObjectDetails() != null) {
      for (ObjectDetailData od : loc.getObjectDetails()) {
        if (od != null && od.getName() != null) {
          coveredObjects.add(od.getName().toLowerCase());
        }
      }
    }
    for (String obj : objectNames) {
      if (!coveredObjects.contains(obj)) {
        issues.add(
            new Issue(
                Severity.ERROR,
                base + ".objectDetails",
                "No objectDetails entry for object '" + obj + "'."));
      }
    }

    validateDisplayNameCoverage(loc, base, issues);
  }

  /**
   * Per-language Display Name coverage (.scratch/gui-localized-case-names). Every localization
   * should give a {@code displayName} to each room and object so the GUI shows a translated name.
   * Warn-only: a missing Display Name never blocks loading because the runtime falls back to the
   * Universal Name. (Suspect Display Names are checked in {@link #validateSuspectPlacement}, which
   * already walks the per-language suspect list.)
   */
  private static void validateDisplayNameCoverage(
      LocalizedData loc, String base, List<Issue> issues) {
    if (loc.getRoomDetails() != null) {
      for (RoomDetailData rd : loc.getRoomDetails()) {
        if (rd != null && rd.getName() != null && isBlank(rd.getDisplayName())) {
          issues.add(
              new Issue(
                  Severity.WARNING,
                  base + ".roomDetails." + safe(rd.getName()) + ".displayName",
                  "No Display Name for room '"
                      + rd.getName()
                      + "'; the Universal Name will show."));
        }
      }
    }
    if (loc.getObjectDetails() != null) {
      for (ObjectDetailData od : loc.getObjectDetails()) {
        if (od != null && od.getName() != null && isBlank(od.getDisplayName())) {
          issues.add(
              new Issue(
                  Severity.WARNING,
                  base + ".objectDetails." + safe(od.getName()) + ".displayName",
                  "No Display Name for object '"
                      + od.getName()
                      + "'; the Universal Name will show."));
        }
      }
    }
  }

  // ---- Room graph ----

  private static void validateGraph(CaseFile caseFile, List<Issue> issues) {
    List<RoomData> rooms = caseFile.getRooms();
    if (rooms == null || rooms.isEmpty()) {
      issues.add(new Issue(Severity.ERROR, "rooms", "No rooms defined."));
      return;
    }
    Set<String> roomNames = universalRoomNames(caseFile);

    String start = caseFile.getStartingRoom();
    if (isBlank(start)) {
      issues.add(new Issue(Severity.ERROR, "startingRoom", "Missing startingRoom."));
    } else if (!roomNames.contains(start)) {
      issues.add(
          new Issue(
              Severity.ERROR,
              "startingRoom",
              "startingRoom '" + start + "' is not a defined room."));
    }

    // Neighbour targets must resolve; reciprocity is a soft (warning) consistency check.
    for (RoomData room : rooms) {
      if (room == null || room.getName() == null || room.getNeighbors() == null) {
        continue;
      }
      for (Map.Entry<String, String> link : room.getNeighbors().entrySet()) {
        String direction = link.getKey();
        String target = link.getValue();
        String loc = "rooms." + room.getName() + ".neighbors." + direction;
        if (!roomNames.contains(target)) {
          issues.add(
              new Issue(Severity.ERROR, loc, "Neighbour '" + target + "' is not a defined room."));
          continue;
        }
        String opposite = OPPOSITE.get(direction.toLowerCase());
        if (opposite != null) {
          RoomData targetRoom = findRoom(rooms, target);
          String back =
              targetRoom == null || targetRoom.getNeighbors() == null
                  ? null
                  : targetRoom.getNeighbors().get(opposite);
          if (!room.getName().equals(back)) {
            issues.add(
                new Issue(
                    Severity.WARNING,
                    loc,
                    "One-way link: '"
                        + target
                        + "' does not link back "
                        + opposite
                        + " to '"
                        + room.getName()
                        + "'."));
          }
        }
      }
    }

    // Connectivity from the starting room.
    if (start != null && roomNames.contains(start)) {
      Set<String> reachable = reachableFrom(rooms, start, roomNames);
      for (String room : roomNames) {
        if (!reachable.contains(room)) {
          issues.add(
              new Issue(
                  Severity.ERROR,
                  "rooms." + room,
                  "Room '" + room + "' is unreachable from startingRoom '" + start + "'."));
        }
      }
    }
  }

  private static Set<String> reachableFrom(
      List<RoomData> rooms, String start, Set<String> roomNames) {
    Set<String> reachable = new HashSet<>();
    List<String> stack = new ArrayList<>();
    reachable.add(start);
    stack.add(start);
    while (!stack.isEmpty()) {
      String current = stack.remove(stack.size() - 1);
      RoomData room = findRoom(rooms, current);
      if (room == null || room.getNeighbors() == null) {
        continue;
      }
      for (String target : room.getNeighbors().values()) {
        if (roomNames.contains(target) && reachable.add(target)) {
          stack.add(target);
        }
      }
    }
    return reachable;
  }

  // ---- Cross references ----

  private static void validateCrossReferences(
      CaseFile caseFile, Set<String> objectIds, Set<String> evidenceSources, List<Issue> issues) {

    // Contradiction evidence must resolve to a real object or deduction source.
    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs != null) {
      for (Map.Entry<String, LocalizedData> locEntry : locs.entrySet()) {
        LocalizedData loc = locEntry.getValue();
        if (loc == null || loc.getSuspects() == null) {
          continue;
        }
        for (SuspectData suspect : loc.getSuspects()) {
          if (suspect == null || suspect.getStates() == null) {
            continue;
          }
          for (Map.Entry<String, SuspectStateData> stateEntry : suspect.getStates().entrySet()) {
            SuspectStateData state = stateEntry.getValue();
            if (state == null || state.getContradictions() == null) {
              continue;
            }
            for (ContradictionRule rule : state.getContradictions()) {
              if (rule == null || rule.getEvidenceId() == null) {
                continue;
              }
              if (!evidenceSources.contains(rule.getEvidenceId())) {
                issues.add(
                    new Issue(
                        Severity.ERROR,
                        "localizations."
                            + locEntry.getKey()
                            + ".suspects."
                            + safe(suspect.getName())
                            + ".states."
                            + stateEntry.getKey()
                            + ".contradictions",
                        "evidenceId '"
                            + rule.getEvidenceId()
                            + "' does not resolve to any object or deduction."));
              }
            }
          }
        }
      }
    }

    // Combine rules: requires must resolve; result deduction ids must be unique.
    List<CombineRule> rules = caseFile.getCombineLogic();
    if (rules != null) {
      Set<String> seenResultIds = new HashSet<>();
      for (int i = 0; i < rules.size(); i++) {
        CombineRule rule = rules.get(i);
        if (rule == null) {
          continue;
        }
        String loc = "combine_logic[" + i + "]";
        if (rule.getRequires() != null) {
          for (String required : rule.getRequires()) {
            if (!evidenceSources.contains(required)) {
              issues.add(
                  new Issue(
                      Severity.ERROR,
                      loc + ".requires",
                      "requires '" + required + "' does not resolve to any object or deduction."));
            }
          }
        }
        String resultId = rule.getResultDeductionId();
        if (isBlank(resultId)) {
          issues.add(
              new Issue(Severity.ERROR, loc + ".resultDeductionId", "Missing resultDeductionId."));
        } else if (!seenResultIds.add(resultId)) {
          issues.add(
              new Issue(
                  Severity.ERROR,
                  loc + ".resultDeductionId",
                  "Duplicate resultDeductionId '" + resultId + "'."));
        }
      }
    }

    // Red-herring recovery references must resolve when present.
    if (caseFile.getRedHerrings() != null) {
      validateRecoverable(
          caseFile.getRedHerrings().getObjects(), "red_herrings.objects", evidenceSources, issues);
      validateRecoverable(
          caseFile.getRedHerrings().getSuspects(),
          "red_herrings.suspects",
          evidenceSources,
          issues);
    }
  }

  private static void validateRecoverable(
      Map<String, RedHerringDetail> section,
      String base,
      Set<String> evidenceSources,
      List<Issue> issues) {
    if (section == null) {
      return;
    }
    for (Map.Entry<String, RedHerringDetail> entry : section.entrySet()) {
      RedHerringDetail detail = entry.getValue();
      if (detail == null) {
        continue;
      }
      String recoverableBy = detail.getRecoverableBy();
      if (recoverableBy != null
          && !recoverableBy.isBlank()
          && !evidenceSources.contains(recoverableBy)) {
        issues.add(
            new Issue(
                Severity.ERROR,
                base + "." + entry.getKey() + ".recoverable_by",
                "recoverable_by '"
                    + recoverableBy
                    + "' does not resolve to any object or deduction."));
      }
    }
  }

  // ---- Suspect placement (Case Maker slice 3): home room required + cross-language consistency
  // ----

  /**
   * Validates authored suspect placement (DEC-2/DEC-5/DEC-6/DEC-9). Each suspect must have a home
   * room that resolves to a defined room. Because suspects are stored per-language (DEC-2), the
   * language-independent placement fields must also be identical across every language, and every
   * suspect must appear in every language — the safeguard that makes per-language storage safe.
   */
  private static void validateSuspectPlacement(
      CaseFile caseFile, Set<String> roomNames, List<Issue> issues) {
    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs == null || locs.isEmpty()) {
      return; // localization completeness is reported elsewhere
    }

    // lang -> (suspect id -> placement signature) for the cross-language consistency check.
    Map<String, Map<String, String>> byLang = new LinkedHashMap<>();
    Set<String> allIds = new LinkedHashSet<>();

    for (Map.Entry<String, LocalizedData> entry : locs.entrySet()) {
      String lang = entry.getKey();
      LocalizedData loc = entry.getValue();
      Map<String, String> sigById = new LinkedHashMap<>();
      if (loc != null && loc.getSuspects() != null) {
        for (SuspectData suspect : loc.getSuspects()) {
          if (suspect == null) {
            continue;
          }
          String id = suspectId(suspect);
          String base = "localizations." + lang + ".suspects." + safe(suspect.getName());
          // Display Name coverage (warn-only): a missing one falls back to the Universal Name.
          if (isBlank(suspect.getDisplayName())) {
            issues.add(
                new Issue(
                    Severity.WARNING,
                    base + ".displayName",
                    "No Display Name for suspect '"
                        + safe(suspect.getName())
                        + "'; the Universal Name will show."));
          }
          if (isBlank(suspect.getHomeRoom())) {
            issues.add(
                new Issue(
                    Severity.ERROR,
                    base + ".homeRoom",
                    "Suspect '" + safe(suspect.getName()) + "' has no home room."));
          } else if (!roomNames.contains(suspect.getHomeRoom())) {
            issues.add(
                new Issue(
                    Severity.ERROR,
                    base + ".homeRoom",
                    "home room '" + suspect.getHomeRoom() + "' is not a defined room."));
          }
          sigById.put(id, placementSignature(suspect));
          allIds.add(id);
        }
      }
      byLang.put(lang, sigById);
    }

    if (byLang.size() < 2) {
      return; // single language: nothing to cross-check
    }

    for (String id : allIds) {
      String reference = null;
      boolean missing = false;
      boolean diverged = false;
      for (Map<String, String> sigById : byLang.values()) {
        String sig = sigById.get(id);
        if (sig == null) {
          missing = true;
        } else if (reference == null) {
          reference = sig;
        } else if (!reference.equals(sig)) {
          diverged = true;
        }
      }
      if (missing) {
        issues.add(
            new Issue(
                Severity.ERROR,
                "localizations.suspects." + id,
                "Suspect '" + id + "' is not present in every language."));
      }
      if (diverged) {
        issues.add(
            new Issue(
                Severity.ERROR,
                "localizations.suspects." + id,
                "Suspect '"
                    + id
                    + "' placement (home room/position/scale/initial state) differs between"
                    + " languages."));
      }
    }
  }

  /** An object's id mirror for suspects: explicit id when set, else the slugged name. */
  private static String suspectId(SuspectData suspect) {
    String id = suspect.getId();
    if (id != null && !id.trim().isEmpty()) {
      return id.trim();
    }
    String name = suspect.getName();
    return name == null ? "" : name.toLowerCase().replace(" ", "_");
  }

  /** The language-independent placement fields, joined for cross-language equality comparison. */
  private static String placementSignature(SuspectData suspect) {
    return suspect.getHomeRoom()
        + "|"
        + suspect.getPosX()
        + "|"
        + suspect.getPosY()
        + "|"
        + suspect.isStationary()
        + "|"
        + suspect.getImageScale()
        + "|"
        + suspect.getInitialState();
  }

  // ---- Watson hint localization coverage (warn-only) ----

  /**
   * Per-language Watson hint coverage (.scratch/gui-localized-watson-hints). Each structured {@code
   * watson.hints} entry should carry a {@code text} translation for every language the case
   * localizes. Warn-only: a missing translation never blocks loading because {@code
   * LocalizedCaseFile} falls back to the English text at runtime. The hint is pure display content
   * (never a command target), so there is no Universal/Display split — only the text is checked.
   */
  private static void validateWatsonHints(CaseFile caseFile, List<Issue> issues) {
    if (caseFile.getWatson() == null || caseFile.getWatson().getHints() == null) {
      return; // No Watson hints authored: nothing to localize.
    }
    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs == null || locs.isEmpty()) {
      return; // Localization completeness is reported in validateLocalizations.
    }
    Set<String> languages = locs.keySet();

    for (Map.Entry<String, List<CaseFile.WatsonHint>> bucket :
        caseFile.getWatson().getHints().entrySet()) {
      String category = bucket.getKey();
      List<CaseFile.WatsonHint> hints = bucket.getValue();
      if (hints == null) {
        continue;
      }
      for (int i = 0; i < hints.size(); i++) {
        CaseFile.WatsonHint hint = hints.get(i);
        if (hint == null) {
          continue;
        }
        Map<String, String> text = hint.getText();
        for (String lang : languages) {
          if (text == null || isBlank(text.get(lang))) {
            issues.add(
                new Issue(
                    Severity.WARNING,
                    "watson.hints." + category + "[" + i + "].text." + lang,
                    "No '"
                        + lang
                        + "' translation for Watson hint '"
                        + safe(hint.getId())
                        + "'; the English text will be shown."));
          }
        }
      }
    }
  }

  // ---- Images (warn-only) ----

  private static void validateImages(CaseFile caseFile, Path caseDir, List<Issue> issues) {
    if (caseFile.getMetadata() != null) {
      warnIfMissing(
          caseFile.getMetadata().getWatsonImagePath(), "metadata.watsonImagePath", caseDir, issues);
      // Optional soundtrack resolves like an image; an unresolvable path warns (the case still
      // plays,
      // silently) and never blocks loading — same policy as imagePath.
      warnIfUnresolved(
          caseFile.getMetadata().getSoundtrack(),
          "metadata.soundtrack",
          caseDir,
          "soundtrack",
          issues);
    }

    if (caseFile.getRooms() != null) {
      for (RoomData room : caseFile.getRooms()) {
        if (room == null) {
          continue;
        }
        warnIfMissing(
            room.getImagePath(), "rooms." + safe(room.getName()) + ".imagePath", caseDir, issues);
        if (room.getObjects() != null) {
          for (GameObjectData obj : room.getObjects()) {
            if (obj == null) {
              continue;
            }
            warnIfMissing(
                obj.getImagePath(),
                "rooms." + safe(room.getName()) + ".objects." + safe(obj.getName()) + ".imagePath",
                caseDir,
                issues);
            warnIfBadScale(
                obj.getImageScale(),
                "rooms." + safe(room.getName()) + ".objects." + safe(obj.getName()) + ".imageScale",
                issues);
          }
        }
      }
    }

    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs != null) {
      for (Map.Entry<String, LocalizedData> locEntry : locs.entrySet()) {
        LocalizedData loc = locEntry.getValue();
        if (loc == null || loc.getSuspects() == null) {
          continue;
        }
        for (SuspectData suspect : loc.getSuspects()) {
          if (suspect == null) {
            continue;
          }
          // A suspect must speak: either a state machine (a liar, Shape A) OR a single top-level
          // statement (an honest witness, Shape B). Neither => the suspect renders blank in-game and
          // in the editor with no error — flag it loudly. A missing state machine ALONE is fine (an
          // honest witness is a legitimate authoring choice), so this only warns when BOTH are empty.
          boolean hasStates = suspect.getStates() != null && !suspect.getStates().isEmpty();
          boolean hasStatement =
              suspect.getStatement() != null && !suspect.getStatement().isBlank();
          if (!hasStates && !hasStatement) {
            issues.add(
                new Issue(
                    Severity.WARNING,
                    "localizations."
                        + locEntry.getKey()
                        + ".suspects."
                        + safe(suspect.getName())
                        + ".statement",
                    "Suspect has neither a statement nor a state machine — will show no dialogue."));
          }
          warnIfMissing(
              suspect.getImagePath(),
              "localizations."
                  + locEntry.getKey()
                  + ".suspects."
                  + safe(suspect.getName())
                  + ".imagePath",
              caseDir,
              issues);
          warnIfBadScale(
              suspect.getImageScale(),
              "localizations."
                  + locEntry.getKey()
                  + ".suspects."
                  + safe(suspect.getName())
                  + ".imageScale",
              issues);
        }
      }
    }

    if (caseFile.getCaseFile() != null && caseFile.getCaseFile().getSuspectProfiles() != null) {
      for (Map.Entry<String, SuspectProfileData> entry :
          caseFile.getCaseFile().getSuspectProfiles().entrySet()) {
        SuspectProfileData profile = entry.getValue();
        if (profile == null) {
          continue;
        }
        warnIfMissing(
            profile.getImagePath(),
            "case_file.suspect_profiles." + entry.getKey() + ".imagePath",
            caseDir,
            issues);
      }
    }
  }

  private static void warnIfMissing(
      String imagePath, String location, Path caseDir, List<Issue> issues) {
    warnIfUnresolved(imagePath, location, caseDir, "imagePath", issues);
  }

  /**
   * Warns (never errors) when a present resource path does not resolve via {@link
   * ResourceResolver}. An absent/blank path is allowed (image → placeholder; soundtrack → silence).
   * {@code kind} is the label used in the message ({@code "imagePath"} / {@code "soundtrack"}).
   */
  private static void warnIfUnresolved(
      String path, String location, Path caseDir, String kind, List<Issue> issues) {
    if (path == null || path.isBlank()) {
      return; // An absent resource is allowed; the game falls back gracefully.
    }
    if (!ResourceResolver.resolves(path, caseDir)) {
      issues.add(
          new Issue(
              Severity.WARNING,
              location,
              kind + " '" + path + "' does not resolve (case dir or classpath)."));
    }
  }

  /**
   * Optional {@code imageScale} is accepted; an absent value defaults to 1.0 at render time. A
   * present but non-positive or non-finite value is reported (warn-only, like image misses) because
   * the renderer ignores it and falls back to 1.0 — the case still plays.
   */
  private static void warnIfBadScale(Double imageScale, String location, List<Issue> issues) {
    if (imageScale == null) {
      return; // Absent is fine; defaults to 1.0.
    }
    if (!Double.isFinite(imageScale) || imageScale <= 0) {
      issues.add(
          new Issue(
              Severity.WARNING,
              location,
              "imageScale '"
                  + imageScale
                  + "' is not a positive number; the renderer will use 1.0."));
    }
  }

  // ---- Resource budgets (SECURITY_PLAN A/P0-2): reject over-budget imported cases ----

  /**
   * Enforces {@link CaseLimits} as ERROR-level issues so a hostile/abusive case is refused at load,
   * the same as a schema failure. Bounds element counts (rooms, objects, suspects, exam questions,
   * choices, Watson hints) and author-supplied string lengths, defending against a well-formed case
   * that would otherwise exhaust memory through sheer size rather than malformed structure.
   */
  private static void validateBudgets(CaseFile caseFile, List<Issue> issues) {
    checkLen(caseFile.getUniversalTitle(), CaseLimits.MAX_NAME_LENGTH, "universal_title", issues);
    if (caseFile.getMetadata() != null) {
      checkLen(
          caseFile.getMetadata().getTitle(), CaseLimits.MAX_NAME_LENGTH, "metadata.title", issues);
      checkLen(
          caseFile.getMetadata().getAuthor(),
          CaseLimits.MAX_NAME_LENGTH,
          "metadata.author",
          issues);
    }

    validateRoomBudgets(caseFile, issues);
    validateLocalizationBudgets(caseFile, issues);
    validateWatsonHintBudget(caseFile, issues);
  }

  private static void validateRoomBudgets(CaseFile caseFile, List<Issue> issues) {
    List<RoomData> rooms = caseFile.getRooms();
    if (rooms == null) {
      return;
    }
    if (rooms.size() > CaseLimits.MAX_ROOMS) {
      issues.add(overBudget("rooms", "rooms", rooms.size(), CaseLimits.MAX_ROOMS));
    }
    int totalObjects = 0;
    for (RoomData room : rooms) {
      if (room == null) {
        continue;
      }
      String rBase = "rooms." + safe(room.getName());
      checkLen(room.getName(), CaseLimits.MAX_NAME_LENGTH, rBase + ".name", issues);
      checkLen(room.getDescription(), CaseLimits.MAX_TEXT_LENGTH, rBase + ".description", issues);
      List<GameObjectData> objects = room.getObjects();
      if (objects == null) {
        continue;
      }
      if (objects.size() > CaseLimits.MAX_OBJECTS_PER_ROOM) {
        issues.add(
            overBudget(
                rBase + ".objects", "objects", objects.size(), CaseLimits.MAX_OBJECTS_PER_ROOM));
      }
      totalObjects += objects.size();
      for (GameObjectData obj : objects) {
        if (obj == null) {
          continue;
        }
        String oBase = rBase + ".objects." + safe(obj.getName());
        checkLen(obj.getName(), CaseLimits.MAX_NAME_LENGTH, oBase + ".name", issues);
        checkLen(obj.getDescription(), CaseLimits.MAX_TEXT_LENGTH, oBase + ".description", issues);
        checkLen(obj.getExamine(), CaseLimits.MAX_TEXT_LENGTH, oBase + ".examine", issues);
        checkLen(obj.getDeduce(), CaseLimits.MAX_TEXT_LENGTH, oBase + ".deduce", issues);
      }
    }
    if (totalObjects > CaseLimits.MAX_TOTAL_OBJECTS) {
      issues.add(
          overBudget(
              "rooms.objects", "objects in total", totalObjects, CaseLimits.MAX_TOTAL_OBJECTS));
    }
  }

  private static void validateLocalizationBudgets(CaseFile caseFile, List<Issue> issues) {
    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs == null) {
      return;
    }
    for (Map.Entry<String, LocalizedData> entry : locs.entrySet()) {
      LocalizedData loc = entry.getValue();
      if (loc == null) {
        continue;
      }
      String base = "localizations." + entry.getKey();
      checkLen(loc.getTitle(), CaseLimits.MAX_NAME_LENGTH, base + ".title", issues);
      checkLen(loc.getInvitation(), CaseLimits.MAX_TEXT_LENGTH, base + ".invitation", issues);
      checkLen(loc.getDescription(), CaseLimits.MAX_TEXT_LENGTH, base + ".description", issues);
      checkLen(
          loc.getWinningMessage(), CaseLimits.MAX_TEXT_LENGTH, base + ".winning_message", issues);

      validateSuspectBudgets(loc, base, issues);
      validateExamBudgets(loc, base, issues);
    }
  }

  private static void validateSuspectBudgets(LocalizedData loc, String base, List<Issue> issues) {
    List<SuspectData> suspects = loc.getSuspects();
    if (suspects == null) {
      return;
    }
    if (suspects.size() > CaseLimits.MAX_SUSPECTS_PER_LANGUAGE) {
      issues.add(
          overBudget(
              base + ".suspects",
              "suspects",
              suspects.size(),
              CaseLimits.MAX_SUSPECTS_PER_LANGUAGE));
    }
    for (SuspectData suspect : suspects) {
      if (suspect == null) {
        continue;
      }
      String sBase = base + ".suspects." + safe(suspect.getName());
      checkLen(suspect.getName(), CaseLimits.MAX_NAME_LENGTH, sBase + ".name", issues);
      checkLen(suspect.getStatement(), CaseLimits.MAX_TEXT_LENGTH, sBase + ".statement", issues);
      checkLen(suspect.getClue(), CaseLimits.MAX_TEXT_LENGTH, sBase + ".clue", issues);
      if (suspect.getStates() != null) {
        for (Map.Entry<String, SuspectStateData> stateEntry : suspect.getStates().entrySet()) {
          SuspectStateData state = stateEntry.getValue();
          if (state != null) {
            checkLen(
                state.getStatement(),
                CaseLimits.MAX_TEXT_LENGTH,
                sBase + ".states." + stateEntry.getKey() + ".statement",
                issues);
          }
        }
      }
    }
  }

  private static void validateExamBudgets(LocalizedData loc, String base, List<Issue> issues) {
    var exam = loc.getFinalExam();
    if (exam == null || exam.getQuestions() == null) {
      return;
    }
    List<FinalExamQuestionDTO> questions = exam.getQuestions();
    if (questions.size() > CaseLimits.MAX_EXAM_QUESTIONS) {
      issues.add(
          overBudget(
              base + ".final_exam.questions",
              "exam questions",
              questions.size(),
              CaseLimits.MAX_EXAM_QUESTIONS));
    }
    for (int q = 0; q < questions.size(); q++) {
      FinalExamQuestionDTO question = questions.get(q);
      if (question == null || question.getSlots() == null) {
        continue;
      }
      for (Map.Entry<String, FinalExamSlotDTO> slotEntry : question.getSlots().entrySet()) {
        FinalExamSlotDTO slot = slotEntry.getValue();
        if (slot != null
            && slot.getChoices() != null
            && slot.getChoices().size() > CaseLimits.MAX_CHOICES_PER_SLOT) {
          issues.add(
              overBudget(
                  base + ".final_exam.questions[" + q + "].slots." + slotEntry.getKey(),
                  "choices",
                  slot.getChoices().size(),
                  CaseLimits.MAX_CHOICES_PER_SLOT));
        }
      }
    }
  }

  private static void validateWatsonHintBudget(CaseFile caseFile, List<Issue> issues) {
    if (caseFile.getWatson() == null || caseFile.getWatson().getHints() == null) {
      return;
    }
    int total = 0;
    for (List<CaseFile.WatsonHint> bucket : caseFile.getWatson().getHints().values()) {
      if (bucket != null) {
        total += bucket.size();
      }
    }
    if (total > CaseLimits.MAX_WATSON_HINTS) {
      issues.add(overBudget("watson.hints", "Watson hints", total, CaseLimits.MAX_WATSON_HINTS));
    }
  }

  private static Issue overBudget(String location, String what, int actual, int max) {
    return new Issue(
        Severity.ERROR, location, "Too many " + what + ": " + actual + " (max " + max + ").");
  }

  private static void checkLen(String value, int max, String location, List<Issue> issues) {
    if (value != null && value.length() > max) {
      issues.add(
          new Issue(
              Severity.ERROR,
              location,
              "Text exceeds the maximum length of "
                  + max
                  + " characters (was "
                  + value.length()
                  + ")."));
    }
  }

  // ---- Shared collectors ----

  private static Set<String> universalRoomNames(CaseFile caseFile) {
    Set<String> names = new LinkedHashSet<>();
    if (caseFile.getRooms() != null) {
      for (RoomData room : caseFile.getRooms()) {
        if (room != null && room.getName() != null) {
          names.add(room.getName());
        }
      }
    }
    return names;
  }

  private static Set<String> collectObjectIds(CaseFile caseFile) {
    Set<String> ids = new HashSet<>();
    if (caseFile.getRooms() != null) {
      for (RoomData room : caseFile.getRooms()) {
        if (room == null || room.getObjects() == null) {
          continue;
        }
        for (GameObjectData obj : room.getObjects()) {
          if (obj != null) {
            ids.add(objectId(obj));
          }
        }
      }
    }
    return ids;
  }

  private static Set<String> collectObjectNames(CaseFile caseFile) {
    Set<String> names = new LinkedHashSet<>();
    if (caseFile.getRooms() != null) {
      for (RoomData room : caseFile.getRooms()) {
        if (room == null || room.getObjects() == null) {
          continue;
        }
        for (GameObjectData obj : room.getObjects()) {
          if (obj != null && obj.getName() != null) {
            names.add(obj.getName().toLowerCase());
          }
        }
      }
    }
    return names;
  }

  private static Set<String> collectDeductionIds(CaseFile caseFile) {
    Set<String> ids = new HashSet<>();
    if (caseFile.getCombineLogic() != null) {
      for (CombineRule rule : caseFile.getCombineLogic()) {
        if (rule != null && rule.getResultDeductionId() != null) {
          ids.add(rule.getResultDeductionId());
        }
      }
    }
    Map<String, LocalizedData> locs = caseFile.getLocalizations();
    if (locs != null) {
      for (LocalizedData loc : locs.values()) {
        if (loc == null || loc.getSuspects() == null) {
          continue;
        }
        for (SuspectData suspect : loc.getSuspects()) {
          if (suspect == null || suspect.getStates() == null) {
            continue;
          }
          for (SuspectStateData state : suspect.getStates().values()) {
            if (state == null || state.getContradictions() == null) {
              continue;
            }
            for (ContradictionRule rule : state.getContradictions()) {
              if (rule != null && rule.getRewardDeductionId() != null) {
                ids.add(rule.getRewardDeductionId());
              }
            }
          }
        }
      }
    }
    return ids;
  }

  /**
   * Mirrors {@code GameObjectExtractor}: an object's id is its {@code id}, else its slugged name.
   */
  private static String objectId(GameObjectData obj) {
    String id = obj.getId();
    if (id != null && !id.trim().isEmpty()) {
      return id.trim();
    }
    String name = obj.getName();
    return name == null ? "" : name.toLowerCase().replace(" ", "_");
  }

  private static RoomData findRoom(List<RoomData> rooms, String name) {
    for (RoomData room : rooms) {
      if (room != null && name.equals(room.getName())) {
        return room;
      }
    }
    return null;
  }

  private static Path caseDirOf(CaseFile caseFile) {
    if (caseFile == null || caseFile.getSourcePath() == null) {
      return null;
    }
    try {
      Path source = Paths.get(caseFile.getSourcePath());
      return source.getParent();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String safe(String s) {
    return s == null ? "<unnamed>" : s;
  }
}

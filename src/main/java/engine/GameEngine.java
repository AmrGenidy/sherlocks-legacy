package engine;

import Core.Detective;
import Core.DoctorWatson;
import Core.GameObject;
import Core.Journal;
import Core.Rank;
import Core.Room;
import Core.Suspect;
import Core.TaskList;
import Core.util.RankEvaluator;
import JsonDTO.CaseData;
import common.dto.CommandCooldownUpdateDTO;
import common.dto.DeductionCountUpdateDTO;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.FinalExamDTO;
import common.dto.FinalExamQuestionDTO;
import common.dto.InitiateFinalExamDTO;
import common.dto.InsightTokenUpdateDTO;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.RoomDescriptionDTO;
import common.dto.TaskStateUpdateDTO;
import common.dto.TextMessage;
import common.dto.VisualPositionDTO;
import common.dto.WatsonHintResponseDTO;
import common.interfaces.GameActionContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Game Engine (CONTEXT.md): single authority for Case-play rules and state, shared by the
 * single-player session and the multiplayer Game Session (ADR-0001). Both {@code GameActionContext}
 * implementations are thin adapters over one instance of this class.
 *
 * <p>The engine is parameterized by exactly two seams: a {@link GameEventListener} for all output
 * and a {@link PlayerSet} for who is playing. It owns no transport and performs no host gating —
 * those are session-layer concerns.
 *
 * <p>Thread-safety: one engine instance belongs to one session and inherits its caller's
 * single-threaded discipline (the server session serializes command execution under its session
 * lock; single-player runs on one game thread).
 */
public final class GameEngine {

  private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

  private final PlayerSet players;
  private final GameEventListener listener;
  private final Random random = new Random();

  // Scaffolding strings for announcements (ui-localization). Defaults to the legacy English so
  // the server and engine tests are byte-identical; the GUI single-player session injects an
  // L10n-backed implementation. Case CONTENT always passes through from the case localization.
  private common.text.GameTexts texts = common.text.GameTexts.ENGLISH;

  private CaseData selectedCase;

  // Insight-token economy: one shared balance and one session deduction counter. The deduction
  // counter is the only rank input (issue 04: Detective.deduceCount is a per-detective
  // duplicate-deduction guard, never the rank counter).
  private int sharedInsightTokens;
  private int sessionDeduceCount;

  public GameEngine(PlayerSet players, GameEventListener listener) {
    this.players = Objects.requireNonNull(players, "players");
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  /** Replaces the announcement scaffolding texts (never null; see {@link #texts}). */
  public void setGameTexts(common.text.GameTexts texts) {
    this.texts = texts != null ? texts : common.text.GameTexts.ENGLISH;
  }

  public common.text.GameTexts getGameTexts() {
    return texts;
  }

  /**
   * Resets all case-play state for a (re)loaded case. Emits nothing — the session layer decides
   * when to announce initial state.
   *
   * @param caseData the case being loaded; {@code null} clears the engine (single-player returning
   *     to case selection)
   */
  public void loadCase(CaseData caseData) {
    this.selectedCase = caseData;
    this.sessionDeduceCount = 0;
    Integer startingTokens = caseData != null ? caseData.getStartingInsightTokens() : null;
    this.sharedInsightTokens = startingTokens != null ? startingTokens : 0;
    this.caseStarted = false;
    List<String> tasks =
        (caseData != null && caseData.getTasks() != null)
            ? new ArrayList<>(caseData.getTasks())
            : new ArrayList<>();
    this.taskList = new TaskList(tasks);
    this.taskStates = new HashMap<>();
    if (caseData != null) {
      // Hint text comes solely from the structured (already localized) watson.hints buckets;
      // the legacy flat per-localization watsonHints array was retired
      // (.scratch/gui-localized-watson-hints).
      this.watson =
          new DoctorWatson(caseData.getStructuredWatsonHints(), caseData.getRedHerrings());
    } else {
      this.watson = null;
    }
    resetExamStateHard();
  }

  /**
   * Clears the extracted world (Rooms, Suspects, Journal). Separate from {@link #loadCase} because
   * the extractors fill the world AFTER the rules state is reset — single-player only learns the
   * CaseData after extraction.
   */
  public void resetWorld() {
    rooms.clear();
    suspects.clear();
    journal.clearEntries();
  }

  public CaseData getSelectedCase() {
    return selectedCase;
  }

  // --- Case lifecycle flag ----------------------------------------------------------------------
  // The flag itself lives here (the exam gate reads it); the session layer keeps the side effects
  // of starting a case (initial broadcasts, session-state transition).

  private boolean caseStarted;

  public boolean isCaseStarted() {
    return caseStarted;
  }

  public void setCaseStartedFlag(boolean started) {
    this.caseStarted = started;
  }

  // --- Insight-token economy ------------------------------------------------------------------

  public int getSharedInsightTokens() {
    return sharedInsightTokens;
  }

  public int getSessionDeduceCount() {
    return sessionDeduceCount;
  }

  public boolean trySpendInsightToken() {
    if (sharedInsightTokens > 0) {
      sharedInsightTokens--;
      emitTokenUpdate();
      return true;
    }
    return false;
  }

  public boolean trySpendInsightTokens(int amount) {
    if (sharedInsightTokens >= amount) {
      sharedInsightTokens -= amount;
      emitTokenUpdate();
      return true;
    }
    return false;
  }

  /**
   * Awards one Insight Token. An outstanding deduction penalty is healed first: the award repays
   * the session deduction count instead of granting the token (observable through {@link
   * #getSessionDeduceCount()} in both modes — issue 04).
   */
  public void awardInsightToken() {
    if (sessionDeduceCount > 0) {
      sessionDeduceCount--;
      emitDeductionUpdate();
    } else {
      sharedInsightTokens++;
      emitTokenUpdate();
    }
  }

  /** Charges one unfunded deduction against the session's rank budget. */
  public void incrementSessionDeduceCount() {
    sessionDeduceCount++;
    emitDeductionUpdate();
  }

  private void emitTokenUpdate() {
    listener.toAll(new InsightTokenUpdateDTO(sharedInsightTokens), null);
  }

  private void emitDeductionUpdate() {
    listener.toAll(new DeductionCountUpdateDTO(sessionDeduceCount), null);
  }

  // --- Command cooldowns ------------------------------------------------------------------------
  // Combine and contradict each lock for 60 seconds after three consecutive failures; a success
  // resets the streak. Deliberately NOT reset by loadCase — a cooldown survives returning to case
  // selection, matching the historical single-player behaviour.

  private static final int FAILURES_TO_LOCK = 3;
  private static final long COMBINE_COOLDOWN_MS = 60 * 1000;
  private static final long CONTRADICT_COOLDOWN_MS = 60 * 1000;

  private int wrongCombineStreak;
  private int wrongContradictStreak;
  private long combineCooldownUntil;
  private long contradictCooldownUntil;

  public void reportCombineSuccess() {
    wrongCombineStreak = 0;
  }

  public void reportCombineFailure() {
    wrongCombineStreak++;
    if (wrongCombineStreak >= FAILURES_TO_LOCK) {
      wrongCombineStreak = 0;
      combineCooldownUntil = System.currentTimeMillis() + COMBINE_COOLDOWN_MS;
      announceCooldown("combine", "Combine", combineCooldownUntil, getCombineCooldownRemaining());
    }
  }

  public boolean isCombineOnCooldown() {
    return System.currentTimeMillis() < combineCooldownUntil;
  }

  /** Remaining combine lock in seconds, or 0 when unlocked. */
  public long getCombineCooldownRemaining() {
    long diff = combineCooldownUntil - System.currentTimeMillis();
    return diff > 0 ? diff / 1000 : 0;
  }

  public void reportContradictSuccess() {
    wrongContradictStreak = 0;
  }

  public void reportContradictFailure() {
    wrongContradictStreak++;
    if (wrongContradictStreak >= FAILURES_TO_LOCK) {
      wrongContradictStreak = 0;
      contradictCooldownUntil = System.currentTimeMillis() + CONTRADICT_COOLDOWN_MS;
      announceCooldown(
          "contradict", "Contradict", contradictCooldownUntil, getContradictCooldownRemaining());
    }
  }

  public boolean isContradictOnCooldown() {
    return System.currentTimeMillis() < contradictCooldownUntil;
  }

  /** Remaining contradict lock in seconds, or 0 when unlocked. */
  public long getContradictCooldownRemaining() {
    long diff = contradictCooldownUntil - System.currentTimeMillis();
    return diff > 0 ? diff / 1000 : 0;
  }

  // --- World state: Rooms, Suspects, Dr. Watson, the Journal -------------------------------------

  private final Map<String, Room> rooms = new HashMap<>();
  private final List<Suspect> suspects = new ArrayList<>();
  private final Journal<JournalEntryDTO> journal = new Journal<>();
  private DoctorWatson watson;

  public void addRoom(Room room) {
    if (room != null && room.getName() != null) {
      rooms.put(room.getName().toLowerCase(), room);
    } else {
      logger.warn("Attempted to add null room or room with null name.");
    }
  }

  public Room getRoomByName(String name) {
    return name != null ? rooms.get(name.toLowerCase()) : null;
  }

  public Map<String, Room> getAllRooms() {
    return Collections.unmodifiableMap(rooms);
  }

  public void addSuspect(Suspect suspect) {
    if (suspect != null) {
      suspects.add(suspect);
    } else {
      logger.warn("Attempted to add null suspect.");
    }
  }

  public List<Suspect> getAllSuspects() {
    return Collections.unmodifiableList(suspects);
  }

  public DoctorWatson getWatson() {
    return watson;
  }

  public Room getCurrentRoomForPlayer(String playerId) {
    Detective detective = players.detectiveFor(playerId);
    return detective != null ? detective.getCurrentRoom() : null;
  }

  // --- Journal
  // ------------------------------------------------------------------------------------

  public void addJournalEntry(JournalEntryDTO entry) {
    if (entry == null) {
      return;
    }
    // Growth cap (security-pass issue 02): player notes have timestamp-unique ids, so without a
    // ceiling a client could grow the shared journal — and every peer's copy — without bound.
    if (journal.getEntries().size() >= common.WireLimits.MAX_JOURNAL_ENTRIES) {
      listener.toPlayer(
          entry.getContributorPlayerId(),
          new TextMessage("The journal is full; no further entries can be added.", true));
      return;
    }
    if (journal.addEntry(entry)) {
      listener.toAll(entry, null);
      if (!players.isSolo()) {
        listener.toPlayer(
            entry.getContributorPlayerId(),
            new TextMessage("Your note was added to the journal.", false));
      }
    } else if (!players.isSolo()) {
      listener.toPlayer(
          entry.getContributorPlayerId(),
          new TextMessage("Note was a duplicate and not added.", false));
    }
  }

  /**
   * Bulk-loads saved Journal entries for a Review Session (.scratch/gui-review-enter-case):
   * replaces the current entries with {@code entries} quietly — no per-entry "added to the journal"
   * output — capped at {@link common.WireLimits#MAX_JOURNAL_ENTRIES}. Read-only seeding; the Review
   * Session disables the commands that would mutate the Journal, so it never changes the saved
   * record.
   */
  public void seedJournal(List<JournalEntryDTO> entries) {
    journal.clearEntries();
    if (entries == null) {
      return;
    }
    for (JournalEntryDTO entry : entries) {
      if (entry == null) {
        continue;
      }
      if (journal.getEntries().size() >= common.WireLimits.MAX_JOURNAL_ENTRIES) {
        break;
      }
      journal.addEntry(entry);
    }
  }

  public List<JournalEntryDTO> getJournalEntries() {
    return journal.getEntries().stream()
        .sorted(Comparator.comparingLong(JournalEntryDTO::getTimestamp))
        .collect(Collectors.toList());
  }

  public List<JournalEntryDTO> getJournalEntriesByType(JournalEntryType type) {
    return journal.getEntriesByType(type);
  }

  public List<JournalEntryDTO> getJournalEntriesBySourceId(String sourceId) {
    return journal.getEntriesBySourceId(sourceId);
  }

  public JournalEntryDTO getJournalEntryById(String entryId) {
    return journal.getEntryById(entryId);
  }

  public Map<JournalEntryType, List<JournalEntryDTO>> getJournalEntriesGroupedByType() {
    return journal.getEntriesGroupedByType();
  }

  // --- Movement and the room view
  // -----------------------------------------------------------------

  public boolean movePlayer(String playerId, String direction) {
    Detective movingPlayer = players.detectiveFor(playerId);
    if (movingPlayer == null) {
      listener.toPlayer(playerId, new TextMessage("Error: Player context not found.", true));
      return false;
    }
    Room oldRoom = movingPlayer.getCurrentRoom();
    if (oldRoom == null) {
      listener.toPlayer(
          playerId, new TextMessage("Error: Your current location is unknown. Cannot move.", true));
      return false;
    }
    Room newRoom = oldRoom.getNeighbor(direction.toLowerCase());
    if (newRoom == null) {
      listener.toPlayer(
          playerId,
          new TextMessage(
              "You can't move " + direction + " from " + oldRoom.getName() + ".", false));
      return false;
    }

    movingPlayer.setCurrentRoom(newRoom);
    updateNpcMovements(playerId);
    notifyPlayerMove(playerId, newRoom, oldRoom);
    listener.toPlayer(playerId, buildRoomDescription(newRoom, playerId));
    return true;
  }

  /** Tells every other player about a player's move; a solo session has no one to tell. */
  public void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom) {
    if (newRoom == null || oldRoom == null) {
      return;
    }
    String moverDisplay = players.displayName(movingPlayerId);
    for (Detective other : players.detectives()) {
      if (!other.getPlayerId().equals(movingPlayerId)) {
        listener.toPlayer(
            other.getPlayerId(),
            new TextMessage(
                moverDisplay
                    + " moved from "
                    + oldRoom.getName()
                    + " to "
                    + newRoom.getName()
                    + ".",
                false));
      }
    }
  }

  /**
   * Suspects and Dr. Watson take a movement turn: anyone sharing a room with a player stays put (so
   * the player can interact), and no one moves INTO a player-occupied room.
   */
  public void updateNpcMovements(String triggeringPlayerId) {
    if (!caseStarted) {
      return;
    }
    Set<String> occupiedRoomNames = new HashSet<>();
    for (Detective detective : players.detectives()) {
      if (detective.getCurrentRoom() != null) {
        occupiedRoomNames.add(detective.getCurrentRoom().getName().toLowerCase());
      }
    }

    for (Suspect suspect : suspects) {
      if (suspect.isStationary()) {
        continue; // authored to stay in its home room (Case Maker slice 3, DEC-5)
      }
      moveNpcAvoidingPlayers(suspect.getCurrentRoom(), occupiedRoomNames, suspect::setCurrentRoom);
    }
    if (watson != null) {
      moveNpcAvoidingPlayers(watson.getCurrentRoom(), occupiedRoomNames, watson::setCurrentRoom);
    }
  }

  private void moveNpcAvoidingPlayers(
      Room currentRoom, Set<String> occupiedRoomNames, java.util.function.Consumer<Room> mover) {
    if (currentRoom == null || occupiedRoomNames.contains(currentRoom.getName().toLowerCase())) {
      return; // roomless, or pinned in place by a player's presence
    }
    List<Room> possibleMoves =
        currentRoom.getNeighbors().values().stream()
            .filter(room -> !occupiedRoomNames.contains(room.getName().toLowerCase()))
            .collect(Collectors.toList());
    if (!possibleMoves.isEmpty()) {
      mover.accept(possibleMoves.get(random.nextInt(possibleMoves.size())));
    }
  }

  public String getOccupantsDescriptionInRoom(Room room, String askingPlayerId) {
    if (room == null) {
      return "Occupants: Error determining room";
    }
    List<String> occupantNames = new ArrayList<>();
    if (!players.isSolo()) {
      for (Detective detective : players.detectives()) {
        if (detective.getCurrentRoom() != null
            && detective.getCurrentRoom().getName().equalsIgnoreCase(room.getName())
            && !detective.getPlayerId().equals(askingPlayerId)) {
          occupantNames.add(players.displayName(detective.getPlayerId()));
        }
      }
    }
    for (Suspect suspect : suspects) {
      if (suspect.getCurrentRoom() != null
          && suspect.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
        occupantNames.add(suspect.getName());
      }
    }
    if (watson != null
        && watson.getCurrentRoom() != null
        && watson.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
      occupantNames.add("Dr. Watson");
    }
    return occupantNames.isEmpty()
        ? "Occupants: None"
        : "Occupants: " + String.join(", ", occupantNames);
  }

  /** The personalized room view sent to one player: objects, occupants (minus the asker), exits. */
  public RoomDescriptionDTO buildRoomDescription(Room room, String playerId) {
    if (room == null) {
      return null;
    }
    List<String> objectNames = new ArrayList<>();
    Map<String, VisualPositionDTO> objectPositions = new HashMap<>();
    Map<String, Double> spriteScales = new HashMap<>(); // horizontal scale
    Map<String, Double> spriteScalesY = new HashMap<>(); // vertical scale (absent = uniform)
    Map<String, VisualPositionDTO> flips = new HashMap<>(); // x/y in {0,1} = flipX/flipY
    // Authored name-label offsets (Case Maker placement tab), keyed by element name.
    Map<String, VisualPositionDTO> labelOffsets = new HashMap<>();
    // Universal-Name -> Display-Name side maps (.scratch/gui-localized-case-names). Populated only
    // when an element actually carries a distinct Display Name; the lists above stay Universal.
    Map<String, String> objectDisplayNames = new HashMap<>();
    Map<String, String> occupantDisplayNames = new HashMap<>();

    for (GameObject obj : room.getObjects().values()) {
      objectNames.add(obj.getName());
      if (!obj.getName().equals(obj.getDisplayName())) {
        objectDisplayNames.put(obj.getName(), obj.getDisplayName());
      }
      if (obj.getNormalizedPosX() != null && obj.getNormalizedPosY() != null) {
        objectPositions.put(
            obj.getName(), new VisualPositionDTO(obj.getNormalizedPosX(), obj.getNormalizedPosY()));
      }
      putScalesAndFlip(
          spriteScales,
          spriteScalesY,
          flips,
          obj.getName(),
          obj.getImageScaleX(),
          obj.getImageScaleY(),
          obj.isFlipX(),
          obj.isFlipY(),
          obj.getRotation());
      if (obj.getLabelDX() != null && obj.getLabelDY() != null) {
        labelOffsets.put(obj.getName(), new VisualPositionDTO(obj.getLabelDX(), obj.getLabelDY()));
      }
    }
    for (Suspect suspect : suspects) {
      if (suspect.getCurrentRoom() != room) {
        continue;
      }
      if (!suspect.getName().equals(suspect.getDisplayName())) {
        occupantDisplayNames.put(suspect.getName(), suspect.getDisplayName());
      }
      putScalesAndFlip(
          spriteScales,
          spriteScalesY,
          flips,
          suspect.getName(),
          suspect.getImageScaleX(),
          suspect.getImageScaleY(),
          suspect.isFlipX(),
          suspect.isFlipY(),
          suspect.getRotation());
      // Authored suspect placement (Case Maker slice 3, DEC-5) flows through the same element-
      // position map RoomView uses for objects.
      if (suspect.getPosX() != null && suspect.getPosY() != null) {
        objectPositions.put(
            suspect.getName(), new VisualPositionDTO(suspect.getPosX(), suspect.getPosY()));
      }
      if (suspect.getLabelDX() != null && suspect.getLabelDY() != null) {
        labelOffsets.put(
            suspect.getName(), new VisualPositionDTO(suspect.getLabelDX(), suspect.getLabelDY()));
      }
    }

    String occupantsStr = getOccupantsDescriptionInRoom(room, playerId);
    List<String> occupantNamesList = new ArrayList<>();
    if (occupantsStr != null
        && !occupantsStr.equalsIgnoreCase("Occupants: None")
        && occupantsStr.startsWith("Occupants: ")) {
      for (String name : occupantsStr.substring("Occupants: ".length()).split(",\\s*")) {
        if (!name.trim().isEmpty()) {
          occupantNamesList.add(name.trim());
        }
      }
    }

    // Dr. Watson has no per-case suspect entry, so his render fields are authored separately (Case
    // Maker placement tab) and surfaced through the same element maps RoomView reads for suspects:
    //  - size once via metadata.watsonImageScale (absent/1.0 leaves RoomView's match-the-room
    // sizing)
    //  - position PER ROOM via rooms[].watsonPos (he follows the player, so each room stores its
    // own
    //    spot); absent lets RoomView fall back to its default Watson spot.
    if (occupantNamesList.contains("Dr. Watson")) {
      // When the case renames the assistant, carry that Display Name in the side map so every client
      // (SP + MP) shows it. Left unset for the default, so the client localizes "Dr. Watson" itself.
      if (selectedCase != null) {
        String helper = selectedCase.getHelperName();
        if (helper != null && !helper.isBlank() && !"Dr. Watson".equals(helper)) {
          occupantDisplayNames.put("Dr. Watson", helper);
        }
      }
      if (selectedCase != null) {
        // Per-room Watson size/orientation wins; otherwise the case's global metadata.watson* value,
        // so a case that only set the global size renders Watson the same in every room.
        double wScaleX =
            room.getWatsonImageScaleX() != null
                ? room.getWatsonImageScaleX()
                : selectedCase.getWatsonImageScaleX();
        double wScaleY =
            room.getWatsonImageScaleY() != null
                ? room.getWatsonImageScaleY()
                : selectedCase.getWatsonImageScaleY();
        boolean wFlipX =
            room.getWatsonFlipX() != null ? room.getWatsonFlipX() : selectedCase.isWatsonFlipX();
        boolean wFlipY =
            room.getWatsonFlipY() != null ? room.getWatsonFlipY() : selectedCase.isWatsonFlipY();
        double wRotation =
            room.getWatsonRotation() != null
                ? room.getWatsonRotation()
                : selectedCase.getWatsonRotation();
        putScalesAndFlip(
            spriteScales, spriteScalesY, flips, "Dr. Watson", wScaleX, wScaleY, wFlipX, wFlipY,
            wRotation);
      }
      if (room.getWatsonPosX() != null && room.getWatsonPosY() != null) {
        objectPositions.put(
            "Dr. Watson", new VisualPositionDTO(room.getWatsonPosX(), room.getWatsonPosY()));
      }
      // Per-room Watson label offset wins; otherwise the global metadata one.
      Double wLabelDX =
          room.getWatsonLabelDX() != null
              ? room.getWatsonLabelDX()
              : (selectedCase != null ? selectedCase.getWatsonLabelDX() : null);
      Double wLabelDY =
          room.getWatsonLabelDY() != null
              ? room.getWatsonLabelDY()
              : (selectedCase != null ? selectedCase.getWatsonLabelDY() : null);
      if (wLabelDX != null && wLabelDY != null) {
        labelOffsets.put("Dr. Watson", new VisualPositionDTO(wLabelDX, wLabelDY));
      }
    }

    // Exit VALUES carry the neighbour's Display Name (display-only: sidebar button text/tooltip and
    // the terminal "(to <Room>)"). Movement is keyed by the direction, so this never affects play.
    Map<String, String> exits =
        room.getNeighbors().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDisplayName()));
    return new RoomDescriptionDTO(
        room.getName(),
        room.getDescription(),
        objectNames,
        occupantNamesList,
        exits,
        room.getImagePath(),
        objectPositions,
        spriteScales,
        room.getDisplayName(),
        objectDisplayNames,
        occupantDisplayNames,
        labelOffsets,
        spriteScalesY,
        flips);
  }

  /**
   * Records an element's horizontal/vertical sprite scale (only when non-default) and mirror flags
   * into the room DTO maps RoomView reads. Uniform (X==Y) leaves the Y map absent.
   */
  private static void putScalesAndFlip(
      Map<String, Double> spriteScales,
      Map<String, Double> spriteScalesY,
      Map<String, VisualPositionDTO> flips,
      String name,
      double scaleX,
      double scaleY,
      boolean flipX,
      boolean flipY,
      double rotation) {
    if (scaleX != 1.0) {
      spriteScales.put(name, scaleX);
    }
    if (scaleY != 1.0) {
      spriteScalesY.put(name, scaleY);
    }
    // The "flips" map doubles as the per-element visual-transform map: it carries the mirror flags
    // and (Case Maker rotation grips) the sprite rotation. Emitted whenever either is non-default.
    if (flipX || flipY || rotation != 0.0) {
      VisualPositionDTO transform = new VisualPositionDTO(flipX ? 1.0 : 0.0, flipY ? 1.0 : 0.0);
      if (rotation != 0.0) {
        transform.setRotation(rotation);
      }
      flips.put(name, transform);
    }
  }

  /** Post-exam "continue": every player gets the initiator's current room view again. */
  public void processContinueGame(String playerId) {
    Detective detective = players.detectiveFor(playerId);
    if (detective == null || detective.getCurrentRoom() == null) {
      logger.warn("processContinueGame: detective or room not found for player {}", playerId);
      return;
    }
    Room currentRoom = detective.getCurrentRoom();
    for (Detective player : players.detectives()) {
      listener.toPlayer(
          player.getPlayerId(), buildRoomDescription(currentRoom, player.getPlayerId()));
    }
  }

  // --- Case start
  // ---------------------------------------------------------------------------------

  /**
   * Places players, Dr. Watson, and Suspects for a (re)started case. Suspects are always placed in
   * a random room other than the Starting Room in both modes (the historical multiplayer rule;
   * single-player previously allowed suspects to start in the player's room).
   *
   * @return {@code false} when no room could be resolved at all (a fatal case-configuration error
   *     the session layer must handle); a missing Starting Room falls back to any loaded room
   */
  public boolean initializeStartingState() {
    if (selectedCase == null) {
      return false;
    }
    Room startingRoom = getRoomByName(selectedCase.getStartingRoom());
    if (startingRoom == null) {
      if (rooms.isEmpty()) {
        return false;
      }
      startingRoom = rooms.values().iterator().next();
      logger.warn(
          "Starting room '{}' not found; using '{}' as fallback.",
          selectedCase.getStartingRoom(),
          startingRoom.getName());
    }

    for (Detective detective : players.detectives()) {
      detective.resetForNewCase();
      detective.setCurrentRoom(startingRoom);
    }
    if (watson != null) {
      watson.setCurrentRoom(startingRoom);
    }

    if (!suspects.isEmpty() && !rooms.isEmpty()) {
      List<Room> allRoomsList = new ArrayList<>(rooms.values());
      final Room finalStartingRoom = startingRoom;
      for (Suspect suspect : suspects) {
        // Honor an authored home room (Case Maker slice 3, DEC-5); it may legitimately be the
        // starting room. Suspects without a resolvable home room keep the historical rule: a
        // random room other than the starting room.
        Room home = suspect.getHomeRoom() == null ? null : getRoomByName(suspect.getHomeRoom());
        if (home != null) {
          suspect.setCurrentRoom(home);
          continue;
        }
        List<Room> validStarts =
            allRoomsList.stream()
                .filter(r -> !r.getName().equalsIgnoreCase(finalStartingRoom.getName()))
                .collect(Collectors.toList());
        if (!validStarts.isEmpty()) {
          suspect.setCurrentRoom(validStarts.get(random.nextInt(validStarts.size())));
        } else {
          suspect.setCurrentRoom(allRoomsList.get(random.nextInt(allRoomsList.size())));
        }
      }
    }
    return true;
  }

  /**
   * Announces a started case to every player: description, tasks, rank tiers, the room view.
   * Scaffolding comes from {@link #texts} (UI language in GUI single-player, legacy English on the
   * server); the Case content — description, Task text, Rank Tier names — comes from the selected
   * case localization and passes through untouched.
   */
  public void announceCaseStart() {
    if (selectedCase == null) {
      listener.toAll(new TextMessage(texts.missingCaseData(), true), null);
      return;
    }

    listener.toAll(
        new TextMessage(
            texts.caseDescriptionHeader() + "\n" + selectedCase.getDescription(), false),
        null);

    if (!taskList.getTasks().isEmpty()) {
      StringBuilder taskMessage = new StringBuilder(texts.caseTasksHeader() + "\n");
      List<String> tasks = taskList.getTasks();
      for (int i = 0; i < tasks.size(); i++) {
        taskMessage.append((i + 1)).append(". ").append(tasks.get(i)).append("\n");
      }
      listener.toAll(new TextMessage(taskMessage.toString().trim(), false), null);
    } else {
      listener.toAll(new TextMessage(texts.noTasksAvailable(), false), null);
    }

    List<JsonDTO.CaseFile.RankTierData> caseTiers = selectedCase.getRankingTiers();
    if (caseTiers != null && !caseTiers.isEmpty()) {
      StringBuilder rankMessage = new StringBuilder("\n" + texts.rankEvaluationHeader() + "\n");
      rankMessage.append(texts.rankEvaluationExplainer()).append("\n");

      // Sort a copy — CaseData is shared across sessions and must not be mutated here.
      List<JsonDTO.CaseFile.RankTierData> tiers = new ArrayList<>(caseTiers);
      tiers.sort(Comparator.comparingInt(JsonDTO.CaseFile.RankTierData::getMaxDeductions));
      final int[] lastMax = {-1};
      for (JsonDTO.CaseFile.RankTierData tier : tiers) {
        if (tier.isDefaultRank()) continue;
        int lowerBound = lastMax[0] + 1;
        int upperBound = tier.getMaxDeductions();
        if (lowerBound > upperBound) continue;
        String range =
            (lowerBound == upperBound) ? String.valueOf(lowerBound) : lowerBound + "-" + upperBound;
        rankMessage.append(texts.rankTierLine(tier.getRankName(), range)).append("\n");
        lastMax[0] = upperBound;
      }
      tiers.stream()
          .filter(JsonDTO.CaseFile.RankTierData::isDefaultRank)
          .findFirst()
          .ifPresent(
              tier ->
                  rankMessage
                      .append(texts.rankTierDefaultLine(tier.getRankName(), lastMax[0] + 1))
                      .append("\n"));
      listener.toAll(new TextMessage(rankMessage.toString().trim(), false), null);
    }

    Room startingRoom = null;
    for (Detective detective : players.detectives()) {
      if (detective.getCurrentRoom() != null) {
        startingRoom = detective.getCurrentRoom();
        break;
      }
    }
    if (startingRoom == null) {
      startingRoom = getRoomByName(selectedCase.getStartingRoom());
    }

    if (startingRoom != null) {
      listener.toAll(
          new TextMessage("\n" + texts.startingLocation(startingRoom.getName()), false), null);
      for (Detective detective : players.detectives()) {
        listener.toPlayer(
            detective.getPlayerId(), buildRoomDescription(startingRoom, detective.getPlayerId()));
      }
    } else {
      listener.toAll(new TextMessage(texts.startingRoomNotFound(), true), null);
    }

    listener.toAll(new TextMessage("\n" + texts.typeHelpPrompt(), false), null);
    emitTokenUpdate();
    emitDeductionUpdate();
  }

  // --- Dr. Watson
  // ---------------------------------------------------------------------------------

  /**
   * @param context passed through to {@link DoctorWatson}, which inspects the journal and world
   *     through the {@code GameActionContext} surface
   */
  public WatsonHintResponseDTO askWatsonForHint(GameActionContext context, String playerId) {
    if (watson == null) {
      return new WatsonHintResponseDTO(
          "game.watson.unavailable", "Dr. Watson is not available in this case.", false);
    }
    if (!isWatsonWithPlayer(playerId)) {
      return new WatsonHintResponseDTO(
          "game.watson.notInRoom", "Dr. Watson is not in this room.", false);
    }
    return watson.provideContextAwareHint(context, playerId);
  }

  public WatsonHintResponseDTO askWatsonAboutTarget(
      GameActionContext context, String playerId, String targetName) {
    if (watson == null) {
      return new WatsonHintResponseDTO(
          "game.watson.unavailable", "Dr. Watson is not available in this case.", false);
    }
    if (!isWatsonWithPlayer(playerId)) {
      return new WatsonHintResponseDTO(
          "game.watson.notInRoom", "Dr. Watson is not in this room.", false);
    }
    if (!trySpendInsightTokens(2)) {
      return new WatsonHintResponseDTO(
          "game.watson.needTokens", "You do not have enough Insight Tokens (need 2).", false);
    }
    // analyzeTarget already returns a typed response: localized authored narrative (no key) or a
    // generic UI-language key the client resolves (.scratch/gui-localized-watson-hints phase 2).
    return watson.analyzeTarget(targetName, context, playerId);
  }

  private boolean isWatsonWithPlayer(String playerId) {
    Detective detective = players.detectiveFor(playerId);
    return detective != null
        && detective.getCurrentRoom() != null
        && watson.getCurrentRoom() != null
        && detective.getCurrentRoom().getName().equalsIgnoreCase(watson.getCurrentRoom().getName());
  }

  // --- Tasks
  // --------------------------------------------------------------------------------------
  // One validation + announcement rule for task toggles (issue 07): a valid toggle is stored and
  // broadcast as a TaskStateUpdateDTO; an invalid index earns the requester an error reply. The
  // per-task completion state is readable back (web client + tests).

  private TaskList taskList = new TaskList(new ArrayList<>());
  private Map<Integer, Boolean> taskStates = new HashMap<>();

  public TaskList getTaskList() {
    return taskList;
  }

  /** Per-task completion toggles recorded so far, keyed by task index. */
  public Map<Integer, Boolean> getTaskStates() {
    return Collections.unmodifiableMap(taskStates);
  }

  public void processUpdateTaskState(String playerId, int taskIndex, boolean isCompleted) {
    if (taskIndex >= 0 && taskIndex < taskList.getTasks().size()) {
      taskStates.put(taskIndex, isCompleted);
      listener.toAll(new TaskStateUpdateDTO(taskIndex, isCompleted), null);
    } else {
      listener.toPlayer(
          playerId,
          new TextMessage("Error updating task: Invalid task index provided: " + taskIndex, true));
    }
  }

  // --- Final-Exam lifecycle
  // -----------------------------------------------------------------------
  // One definition of "can start" (case started + not already active; issue 06 — host gating is
  // session-layer). After scoring the exam goes inactive but the questions, answers, and the last
  // result survive until the next exam start or case load, so a retry works and the GUI can read
  // the result back.

  private boolean examActive;
  private FinalExamDTO finalExam;
  private Map<Integer, Map<String, String>> examAnswers;
  private int currentExamQuestionIndex;
  private ExamResultDTO lastExamResult;
  private String examInitiatorId;

  public boolean canStartFinalExam() {
    return caseStarted && !examActive;
  }

  public boolean isExamActive() {
    return examActive;
  }

  /** The most recently scored exam result; survives until the next exam start or case load. */
  public ExamResultDTO getLastExamResult() {
    return lastExamResult;
  }

  public boolean isAwaitingExamAnswer() {
    return examActive
        && finalExam != null
        && currentExamQuestionIndex < finalExam.getQuestions().size();
  }

  public int getAwaitingQuestionNumber() {
    return isAwaitingExamAnswer() ? currentExamQuestionIndex + 1 : 0;
  }

  /** The current question as the client-facing DTO, or {@code null} when no exam is running. */
  public ExamQuestionDTO getCurrentExamQuestion() {
    if (!examActive
        || finalExam == null
        || currentExamQuestionIndex >= finalExam.getQuestions().size()) {
      return null;
    }
    FinalExamQuestionDTO question = finalExam.getQuestions().get(currentExamQuestionIndex);
    Map<String, String> selectedAnswers =
        examAnswers.getOrDefault(currentExamQuestionIndex, new HashMap<>());
    return new ExamQuestionDTO(
        currentExamQuestionIndex,
        finalExam.getQuestions().size(),
        question.getQuestionPrompt(),
        question.getSlots(),
        selectedAnswers);
  }

  public void startExam(String initiatorId) {
    if (!canStartFinalExam()) {
      listener.toPlayer(
          initiatorId,
          new TextMessage(
              "Cannot start the final exam now.", true, "game.exam.cannotStartNow", null));
      return;
    }
    if (selectedCase == null
        || selectedCase.getFinalExam() == null
        || selectedCase.getFinalExam().getQuestions().isEmpty()) {
      if (players.isSolo()) {
        listener.toPlayer(
            initiatorId,
            new TextMessage(
                "No final exam questions are configured for this case.",
                false,
                "game.exam.noQuestions",
                null));
      } else {
        listener.toAll(
            new TextMessage("Error: No final exam questions configured for this case.", true),
            null);
      }
      return;
    }

    this.examActive = true;
    this.finalExam = selectedCase.getFinalExam();
    this.examAnswers = new HashMap<>();
    this.currentExamQuestionIndex = 0;
    this.examInitiatorId = initiatorId;
    this.lastExamResult = null;

    if (players.isSolo()) {
      listener.toPlayer(
          initiatorId, new TextMessage("--- Final Exam Started ---", false, "game.exam.started", null));
    } else {
      listener.toAll(
          new TextMessage(
              "--- Final Exam Initiated by " + players.displayName(initiatorId) + " ---", false),
          null);
    }
    listener.toAll(new InitiateFinalExamDTO(sanitizedForClients(finalExam)), null);
    sendCurrentExamQuestion();
  }

  /**
   * The exam as clients may see it: prompts and slots only. The correct combinations never leave
   * the engine — scoring happens server-side against {@link #finalExam} (security-pass issue 04).
   */
  private static FinalExamDTO sanitizedForClients(FinalExamDTO exam) {
    List<FinalExamQuestionDTO> publicQuestions = new ArrayList<>();
    for (FinalExamQuestionDTO question : exam.getQuestions()) {
      publicQuestions.add(
          new FinalExamQuestionDTO(question.getQuestionPrompt(), question.getSlots(), null));
    }
    return new FinalExamDTO(publicQuestions);
  }

  public void submitExamAnswer(String playerId, int questionIndex, Map<String, String> answers) {
    if (!examActive || finalExam == null) {
      listener.toPlayer(
          playerId, new TextMessage("No exam is currently active.", true, "game.exam.notActive", null));
      return;
    }
    if (questionIndex != currentExamQuestionIndex) {
      return; // stale answer for a question that is no longer current
    }
    if (!isPlausibleAnswerMap(answers)) {
      listener.toPlayer(
          playerId, new TextMessage("Invalid exam answer submission (too large).", true));
      return;
    }
    examAnswers.put(questionIndex, answers);

    if (currentExamQuestionIndex < finalExam.getQuestions().size() - 1) {
      currentExamQuestionIndex++;
      sendCurrentExamQuestion();
    } else {
      scoreExam();
    }
  }

  /** Bounds a client-supplied answer map: entry count and per-string lengths (issue 02). */
  private static boolean isPlausibleAnswerMap(Map<String, String> answers) {
    if (answers == null) {
      return true; // "no answer" is a legal submission; scoring treats it as wrong
    }
    if (answers.size() > common.WireLimits.MAX_EXAM_ANSWER_ENTRIES) {
      return false;
    }
    for (Map.Entry<String, String> entry : answers.entrySet()) {
      if (entry.getKey() != null
          && entry.getKey().length() > common.WireLimits.MAX_EXAM_ANSWER_TEXT_LENGTH) {
        return false;
      }
      if (entry.getValue() != null
          && entry.getValue().length() > common.WireLimits.MAX_EXAM_ANSWER_TEXT_LENGTH) {
        return false;
      }
    }
    return true;
  }

  private void sendCurrentExamQuestion() {
    if (!examActive) {
      return;
    }
    ExamQuestionDTO questionDTO = getCurrentExamQuestion();
    if (questionDTO == null) {
      scoreExam();
      return;
    }
    listener.toAll(questionDTO, null);

    if (!players.isSolo()) {
      listener.toPlayer(
          examInitiatorId,
          new TextMessage(
              "Host, please submit your answer for Q" + (currentExamQuestionIndex + 1) + ".",
              false));
      String initiatorDisplay = players.displayName(examInitiatorId);
      for (Detective other : players.detectives()) {
        if (!other.getPlayerId().equals(examInitiatorId)) {
          listener.toPlayer(
              other.getPlayerId(),
              new TextMessage(
                  initiatorDisplay
                      + " is answering exam question "
                      + (currentExamQuestionIndex + 1)
                      + "/"
                      + finalExam.getQuestions().size()
                      + "...",
                  false));
        }
      }
    }
  }

  /**
   * Shown in a filled exam-review blank when the player left that slot unanswered
   * (gui-exam-review).
   */
  private static final String EXAM_NO_ANSWER = "(no answer)";

  private void scoreExam() {
    if (finalExam == null) {
      listener.toPlayer(
          examInitiatorId, new TextMessage("Error: Exam data missing for evaluation.", true));
      resetExamStateHard();
      return;
    }
    if (examAnswers == null) {
      examAnswers = new HashMap<>();
    }

    int score = 0;
    List<String> reviewableAnswersDetails = new ArrayList<>();
    List<common.dto.ExamReviewItemDTO> reviewItems = new ArrayList<>();
    int totalQuestions = finalExam.getQuestions().size();

    // Build a readable review for EVERY question (gui-exam-review): the prompt with the player's
    // chosen choice TEXT filled into its blanks, plus a right/wrong mark — never raw ids and never
    // the correct answer (the exam is retryable). Composed here so SP and MP both get it.
    for (int i = 0; i < totalQuestions; i++) {
      FinalExamQuestionDTO actualQuestion = finalExam.getQuestions().get(i);
      Map<String, String> givenAnswer = examAnswers.get(i);

      boolean correct = ExamReview.isCorrect(actualQuestion, givenAnswer);
      if (correct) {
        score++;
      }
      String filled = ExamReview.fillPrompt(actualQuestion, givenAnswer, EXAM_NO_ANSWER);
      reviewItems.add(new common.dto.ExamReviewItemDTO(i + 1, filled, correct));
      reviewableAnswersDetails.add((correct ? "✓ " : "✗ ") + "Q" + (i + 1) + ": " + filled);
    }

    Rank finalRank = RankEvaluator.evaluate(sessionDeduceCount, selectedCase);
    String finalRankString = (finalRank != null) ? finalRank.getRankName() : "Unranked";
    for (Detective detective : players.detectives()) {
      detective.setFinalExamScore(score);
      detective.setRank(finalRank);
    }

    String feedback;
    if (players.isSolo()) {
      if (score == totalQuestions) {
        feedback =
            "Outstanding! You've answered all questions correctly and solved the case perfectly.";
      } else if (score >= totalQuestions * 0.5) {
        feedback =
            "You've made some progress. Review your notes and the evidence for the questions you missed.";
      } else {
        feedback =
            "Unfortunately, your investigation fell short. Crucial details were missed. Review your notes and the evidence thoroughly.";
      }
    } else {
      String initiatorDisplay = players.displayName(examInitiatorId);
      if (score == totalQuestions) {
        feedback = "Outstanding! The case is solved perfectly by " + initiatorDisplay + "!";
      } else if (score >= totalQuestions * 0.5) {
        feedback = "Good effort by " + initiatorDisplay + "! Key aspects uncovered.";
      } else {
        feedback =
            "The mystery remains largely unsolved by "
                + initiatorDisplay
                + ". Further investigation was needed.";
      }
    }

    boolean caseSolved = (score == totalQuestions);
    String winningMessage =
        (caseSolved && finalRank != null) ? finalRank.getWinningStatement() : null;

    this.lastExamResult =
        new ExamResultDTO(
            score,
            totalQuestions,
            feedback,
            finalRankString,
            reviewableAnswersDetails,
            winningMessage,
            caseSolved);
    this.lastExamResult.setReviewItems(reviewItems);

    listener.toAll(lastExamResult, null);
    listener.toAll(
        new TextMessage("--- Final Exam Concluded ---", false, "game.exam.concluded", null), null);
    listener.toAll(new TextMessage("1. Continue Playing", false, "game.exam.continuePlaying", null), null);
    if (caseSolved) {
      listener.toAll(
          new TextMessage("2. Return to Main Menu", false, "game.exam.returnToMenu", null), null);
    }

    // Inactive, but questions/answers/result survive for a retry and for GUI read-back.
    this.examActive = false;
  }

  /** Hard exam reset: clears questions, answers, and the cached result. */
  public void resetExamStateHard() {
    this.examActive = false;
    this.finalExam = null;
    this.examAnswers = null;
    this.currentExamQuestionIndex = 0;
    this.lastExamResult = null;
    this.examInitiatorId = null;
  }

  private void announceCooldown(
      String commandKey, String commandDisplayName, long lockedUntil, long remainingSeconds) {
    // Describe the real lock length rather than a hardcoded figure: contradict locks for 60
    // seconds, combine for five minutes, so the message must match the timer the UI counts down.
    String duration =
        remainingSeconds >= 90
            ? Math.round(remainingSeconds / 60.0) + " minutes"
            : remainingSeconds + " seconds";
    String lockText =
        "Too many failed attempts. " + commandDisplayName + " is locked for " + duration + ".";
    listener.toAll(
        new TextMessage(
            "[SERVER] " + lockText,
            true,
            "game." + commandKey + ".locked",
            java.util.List.of(String.valueOf(remainingSeconds))),
        null);
    // Also surface the lock as a popup so it is seen while the Pinboard covers the terminal.
    listener.toAll(
        new common.dto.DialogueEventDTO(
            commandDisplayName + " Locked",
            lockText,
            common.dto.DialogueType.CONTRADICTION,
            "game.popup." + commandKey + "Locked",
            "game." + commandKey + ".locked",
            java.util.List.of(String.valueOf(remainingSeconds))),
        null);
    listener.toAll(new CommandCooldownUpdateDTO(commandKey, lockedUntil, remainingSeconds), null);
  }
}

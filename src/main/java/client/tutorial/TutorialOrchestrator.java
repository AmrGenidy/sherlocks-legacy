package client.tutorial;

import Core.Room;
import Core.Suspect;
import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import singleplayer.GameContextSinglePlayer;
import singleplayer.SinglePlayerMain;

/**
 * Bridges {@link TutorialManager} and the real single-player engine.
 *
 * <p>Tutorials run against a real, bundled <b>practice case</b> ({@code
 * tutorial_practice_case.json}) — not a faked scene — so every taught command actually executes and
 * emits the real typed events (journal entries, room re-render, token spend, suspect-state change).
 * User input is routed to both the engine (which executes it) and the tutorial (which observes it
 * to advance).
 *
 * <p>"exit" / "quit" / "back" are tutorial-mode controls and are NOT passed to the engine.
 */
public class TutorialOrchestrator {

  /** Practice-case resource on the classpath. */
  private static final String PRACTICE_CASE = "tutorial_practice_case.json";

  /**
   * Room the practice-case suspect is pinned to. The engine places suspects randomly ({@code
   * SuspectExtractor}), which would make the question/contradict tutorials flaky; pinning makes
   * them deterministic. Tutorials that need the suspect set {@code startRoom} to this room.
   */
  private static final String SUSPECT_ROOM = "Parlour";

  private final TutorialManager tutorialManager;
  private final SinglePlayerMain game;

  public TutorialOrchestrator(TutorialManager tutorialManager, SinglePlayerMain game) {
    this.tutorialManager = tutorialManager;
    this.game = game;
  }

  /** Boot the practice case at its own starting room. */
  public static TutorialOrchestrator bootstrap(TutorialManager tutorialManager) {
    return bootstrap(tutorialManager, null);
  }

  /**
   * Boot a fresh {@link SinglePlayerMain} on the practice case and return an orchestrator wired to
   * it.
   *
   * @param startRoom room to place the player in, or null to use the case's own starting room
   */
  public static TutorialOrchestrator bootstrap(TutorialManager tutorialManager, String startRoom) {
    return bootstrap(tutorialManager, startRoom, java.util.List.of());
  }

  /**
   * As {@link #bootstrap(TutorialManager, String)}, but runs {@code seedCommands} against the
   * engine after the case starts — BEFORE any GUI sink is attached — so a tutorial can begin with
   * evidence already examined/questioned and no visible setup (.scratch/gui-pinboard-tutorial).
   */
  public static TutorialOrchestrator bootstrap(
      TutorialManager tutorialManager, String startRoom, java.util.List<String> seedCommands) {
    SinglePlayerMain game = new SinglePlayerMain();
    GameContextSinglePlayer ctx = game.getGameContext();
    LocalizedCaseFile practice = new LocalizedCaseFile(loadPracticeCase(), "en");

    ctx.resetForNewCaseLoad();
    if (!BuildingExtractor.loadBuilding(practice, ctx)) {
      throw new IllegalStateException("Practice case failed to load building");
    }
    GameObjectExtractor.loadObjects(practice, ctx);
    try {
      SuspectExtractor.loadSuspects(practice, ctx);
    } catch (SuspectExtractor.NoValidRoomsException e) {
      throw new IllegalStateException("Practice case failed to place suspects", e);
    }

    String room = startRoom != null ? startRoom : practice.getStartingRoom();
    ctx.initializeNewCase(practice, room);
    pinSuspectsTo(ctx, SUSPECT_ROOM);

    // Tutorials must start playable — gameplay commands require a started case.
    game.processCommand("start case");

    // Silent pre-seed (no GUI sink yet): examine/question so the board/journal enter the lesson
    // already populated, avoiding a long visible setup (.scratch/gui-pinboard-tutorial).
    if (seedCommands != null) {
      for (String seed : seedCommands) {
        if (seed != null && !seed.isBlank()) {
          game.processCommand(seed.trim());
        }
      }
    }

    return new TutorialOrchestrator(tutorialManager, game);
  }

  private static CaseFile loadPracticeCase() {
    ObjectMapper mapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    try (InputStream is =
        TutorialOrchestrator.class.getClassLoader().getResourceAsStream(PRACTICE_CASE)) {
      if (is == null) {
        throw new IllegalStateException("Practice case resource not found: " + PRACTICE_CASE);
      }
      return mapper.readValue(is, CaseFile.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read practice case " + PRACTICE_CASE, e);
    }
  }

  /** Deterministically move every suspect into {@code roomName} (random placement otherwise). */
  private static void pinSuspectsTo(GameContextSinglePlayer ctx, String roomName) {
    Room target = null;
    for (Room room : ctx.getAllRooms().values()) {
      if (room != null && roomName.equalsIgnoreCase(room.getName())) {
        target = room;
        break;
      }
    }
    if (target == null) {
      return;
    }
    for (Suspect suspect : ctx.getAllSuspects()) {
      suspect.setCurrentRoom(target);
    }
  }

  public void handleUserInput(String input) {
    if (input == null) {
      return;
    }
    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    if (trimmed.equalsIgnoreCase("exit")
        || trimmed.equalsIgnoreCase("quit")
        || trimmed.equalsIgnoreCase("back")
        || isMetaAdvance(trimmed)) {
      // Tutorial-mode controls (escape hatch + closing/GUI-only "continue" gates) are not
      // real game commands; route them only to the tutorial so the engine stays quiet.
      tutorialManager.processInput(trimmed);
      return;
    }

    game.processCommand(trimmed);
    tutorialManager.processInput(trimmed);
  }

  /**
   * Tutorial-only progression words used by closing notes and GUI-feature steps. They advance the
   * step machine but are never sent to the engine — including {@code "pinboard"}, a GUI-only verb
   * (the toolbar button opens the window) with no engine command, so routing it to the engine would
   * only print "unknown command" (.scratch/gui-pinboard-tutorial).
   */
  private static boolean isMetaAdvance(String input) {
    return input.equalsIgnoreCase("continue")
        || input.equalsIgnoreCase("next")
        || input.equalsIgnoreCase("done")
        || input.equalsIgnoreCase("pinboard");
  }

  public TutorialManager getTutorialManager() {
    return tutorialManager;
  }

  public SinglePlayerMain getGame() {
    return game;
  }
}

package singleplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import JsonDTO.LocalizedCaseFile;
import common.commands.MoveCommand;
import common.dto.InsightTokenUpdateDTO;
import common.dto.ReturnToCaseSelectionDTO;
import common.dto.RoomDescriptionDTO;
import engine.EngineFixtures;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Proves the {@link GameOutputSink} seam is wired into the real single-player command path (issue
 * 02, .scratch/typed-game-events): the production {@link GameContextSinglePlayer} routes the typed
 * events it produces to its sink instead of {@code System.out}, including the token/deduction
 * updates the old console code dropped — those are exactly the events the web/GUI client must
 * consume.
 *
 * <p>Loaded through the real extraction pipeline against the deterministic sapphire fixture, the
 * same way {@code SinglePlayerContextHarness} does (but with a plain context so the sink — not a
 * test-double override of {@code sendResponseToPlayer} — is exercised).
 */
public class GameContextSinglePlayerOutputSinkTest {

  private static GameContextSinglePlayer startedSapphire() {
    LocalizedCaseFile caseFile = EngineFixtures.sapphire();
    GameContextSinglePlayer ctx = new GameContextSinglePlayer();
    ctx.resetForNewCaseLoad();
    try {
      if (!BuildingExtractor.loadBuilding(caseFile, ctx)) {
        throw new IllegalStateException("BuildingExtractor failed for " + caseFile.getTitle());
      }
      GameObjectExtractor.loadObjects(caseFile, ctx);
      SuspectExtractor.loadSuspects(caseFile, ctx);
    } catch (SuspectExtractor.NoValidRoomsException e) {
      throw new IllegalStateException("Suspect placement failed for " + caseFile.getTitle(), e);
    }
    ctx.initializeNewCase(caseFile, caseFile.getStartingRoom());
    ctx.setCaseStarted(true);
    return ctx;
  }

  private static <T> boolean containsType(List<Serializable> events, Class<T> type) {
    return events.stream().anyMatch(type::isInstance);
  }

  @Test
  public void moveCommand_emitsRoomDescriptionToTheSink() {
    GameContextSinglePlayer ctx = startedSapphire();
    List<Serializable> emitted = new ArrayList<>();
    ctx.setOutputSink(emitted::add);

    String playerId = ctx.getPlayerDetective(null).getPlayerId();
    MoveCommand move = new MoveCommand("east"); // Ballroom exits east to the Terrace
    move.setPlayerId(playerId);
    move.execute(ctx);

    assertTrue(
        "a move must emit a RoomDescriptionDTO as a typed event, not console text",
        containsType(emitted, RoomDescriptionDTO.class));
  }

  @Test
  public void stateUpdateDto_reachesTheSinkInsteadOfBeingDropped() {
    GameContextSinglePlayer ctx = startedSapphire();
    List<Serializable> emitted = new ArrayList<>();
    ctx.setOutputSink(emitted::add);

    // The legacy console path silently swallowed token updates; a typed sink must see them so the
    // GUI/web client can react to them as events.
    ctx.sendResponseToPlayer(
        ctx.getPlayerDetective(null).getPlayerId(), new InsightTokenUpdateDTO(7));

    assertTrue(containsType(emitted, InsightTokenUpdateDTO.class));
  }

  @Test
  public void exitRequest_emitsTypedReturnToCaseSelection() {
    GameContextSinglePlayer ctx = startedSapphire();
    List<Serializable> emitted = new ArrayList<>();
    ctx.setOutputSink(emitted::add);

    ctx.handlePlayerExitRequest(ctx.getPlayerDetective(null).getPlayerId());

    assertTrue(
        "exit-to-case-selection must be signalled by a typed DTO, not TextMessage content",
        containsType(emitted, ReturnToCaseSelectionDTO.class));
    assertTrue(ctx.wantsToExitToCaseSelection());
  }

  @Test
  public void nullResponse_isIgnored() {
    GameContextSinglePlayer ctx = startedSapphire();
    List<Serializable> emitted = new ArrayList<>();
    ctx.setOutputSink(emitted::add);

    ctx.sendResponseToPlayer("anyone", null);

    assertTrue(emitted.isEmpty());
  }

  @Test
  public void settingNullSink_restoresConsoleDefaultWithoutThrowing() {
    GameContextSinglePlayer ctx = startedSapphire();
    ctx.setOutputSink(null); // must fall back to the console sink, not NPE on the next emit

    ctx.sendResponseToPlayer("anyone", new InsightTokenUpdateDTO(1)); // suppressed by console sink

    // No assertion on stdout; the point is that emitting after a null sink does not throw.
    assertFalse(false);
  }
}

package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import common.dto.TaskStateUpdateDTO;
import common.dto.TextMessage;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The unified task-toggling contract across both contexts (issue 07 resolution, ADR-0001): a valid
 * toggle is validated, stored, readable back through {@code getTaskStates()}, and announced with a
 * {@link TaskStateUpdateDTO}; an invalid index earns the requester an error {@link TextMessage}.
 *
 * <p>Replaces {@code TaskStateUpdateDivergenceTest}, which pinned the pre-unification divergence
 * where single-player stored toggles silently and exposed no read-back.
 */
@RunWith(Parameterized.class)
public class TaskStateUpdateContractTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness fsm() {
    return factory.start(EngineFixtures.fsm());
  }

  @Test
  public void validToggleIsStoredReadableAndAnnounced() {
    ContextHarness h = fsm();

    h.context().processUpdateTaskState(h.playerId(), 0, true);

    assertEquals(Map.of(0, true), h.context().getTaskStates());
    assertTrue(
        "a valid toggle is announced with a TaskStateUpdateDTO in both modes",
        h.playerResponses().stream()
            .anyMatch(
                d ->
                    d instanceof TaskStateUpdateDTO dto
                        && dto.getTaskIndex() == 0
                        && dto.getIsCompleted()));
  }

  @Test
  public void toggleBackToIncompleteIsTracked() {
    ContextHarness h = fsm();

    h.context().processUpdateTaskState(h.playerId(), 1, true);
    h.context().processUpdateTaskState(h.playerId(), 1, false);

    assertEquals(Map.of(1, false), h.context().getTaskStates());
  }

  @Test
  public void invalidIndexIsRejectedWithAnErrorAndNotStored() {
    ContextHarness h = fsm();

    h.context().processUpdateTaskState(h.playerId(), 99, true);

    assertEquals(Map.of(), h.context().getTaskStates());
    assertTrue(
        "an invalid index earns an error reply in both modes",
        h.playerResponses().stream().anyMatch(d -> d instanceof TextMessage tm && tm.isError()));
  }
}

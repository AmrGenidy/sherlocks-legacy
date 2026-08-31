package engine;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Task-list contract across both contexts. Toggling behaviour (validation, announcement, and the
 * {@code getTaskStates()} read-back) is pinned in {@link TaskStateUpdateContractTest}.
 */
@RunWith(Parameterized.class)
public class TaskStateContractTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness fsm() {
    return factory.start(EngineFixtures.fsm());
  }

  @Test
  public void taskListReflectsTheCaseFile() {
    ContextHarness h = fsm();
    assertEquals(
        List.of("Inspect the knife", "Read the letter", "Get a confession"),
        h.context().getTaskList().getTasks());
  }

  @Test
  public void togglingAValidTaskIndexIsAccepted() {
    ContextHarness h = fsm();
    // No shared read-back exists; the contract is simply that a valid toggle is handled cleanly.
    h.context().processUpdateTaskState(h.playerId(), 0, true);
    h.context().processUpdateTaskState(h.playerId(), 2, true);
    h.context().processUpdateTaskState(h.playerId(), 0, false);
    // Task list is unaffected by completion toggles.
    assertEquals(3, h.context().getTaskList().getTasks().size());
  }
}

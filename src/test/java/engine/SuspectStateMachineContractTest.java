package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import Core.Suspect.SuspectState;
import common.commands.ContradictCommand;
import common.commands.ExamineCommand;
import common.commands.QuestionCommand;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Drives the complete suspect state machine LIE -> TRUTH -> PANIC through real {@code
 * ContradictCommand} execution against BOTH context implementations.
 *
 * <p>The bundled sapphire case only defines a LIE -> TRUTH leg, so this contract uses the {@code
 * fsm} fixture (a real case file loaded through {@code CaseLoader}) whose Butler suspect carries
 * the full chain: {@code knife} cracks LIE -> TRUTH, then {@code letter} cracks TRUTH -> PANIC.
 */
@RunWith(Parameterized.class)
public class SuspectStateMachineContractTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness fsm() {
    return factory.start(EngineFixtures.fsm());
  }

  @Test
  public void suspectStartsInItsDeclaredInitialState() {
    ContextHarness h = fsm();
    assertEquals(SuspectState.LIE, h.suspect("Butler").getCurrentState());
  }

  @Test
  public void firstContradictionMovesLieToTruth() {
    ContextHarness h = fsm();
    h.bringSuspectToPlayer("Butler");
    h.execute(new ExamineCommand("knife"));

    h.execute(new ContradictCommand("Butler", "knife"));

    assertEquals(SuspectState.TRUTH, h.suspect("Butler").getCurrentState());
  }

  @Test
  public void secondContradictionMovesTruthToPanic() {
    ContextHarness h = fsm();
    h.bringSuspectToPlayer("Butler");
    h.execute(new ExamineCommand("knife"));
    h.execute(new ExamineCommand("letter"));

    h.execute(new ContradictCommand("Butler", "knife")); // LIE -> TRUTH
    h.execute(new ContradictCommand("Butler", "letter")); // TRUTH -> PANIC

    assertEquals(SuspectState.PANIC, h.suspect("Butler").getCurrentState());
  }

  @Test
  public void evidenceForALaterStateDoesNothingWhileStillLying() {
    ContextHarness h = fsm();
    h.bringSuspectToPlayer("Butler");
    h.execute(new ExamineCommand("letter"));

    // The 'letter' only contradicts the TRUTH statement; in LIE it must not fire.
    h.execute(new ContradictCommand("Butler", "letter"));

    assertEquals(SuspectState.LIE, h.suspect("Butler").getCurrentState());
  }

  @Test
  public void questioningReflectsTheCurrentStateStatement() {
    ContextHarness h = fsm();
    h.bringSuspectToPlayer("Butler");
    h.execute(new ExamineCommand("knife"));
    h.execute(new ContradictCommand("Butler", "knife")); // -> TRUTH

    h.execute(new QuestionCommand("Butler"));

    assertNotNull(h.context().getJournalEntryById(h.playerId(), "stmt:butler:default"));
    assertEquals(SuspectState.TRUTH, h.suspect("Butler").getCurrentState());
    assertTrue(
        "the questioned statement should be the TRUTH-state line",
        h.suspect("Butler").getStatement().contains("I touched nothing"));
  }
}

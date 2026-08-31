package ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import Core.Suspect;
import common.dto.DialogueEventDTO;
import engine.EngineFixtures;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import singleplayer.SinglePlayerMain;

/**
 * Plays a full single-player loop (start / look / examine / deduce / question / contradict /
 * journal / final exam) through the production {@link SinglePlayerMain} facade with a {@link
 * GuiGameOutputSink} registered exactly as {@link MainController} registers it — verifying the GUI
 * is driven entirely by typed events, with no stdout capture and no marker text reaching the
 * terminal transcript. This is the automated stand-in for the issue-03 play-through; the visual
 * pass per DESIGN.md remains a human step.
 */
public class SinglePlayerGuiEventFlowTest {

  @Test
  public void fullCaseLoop_drivesEveryGuiHandlerFromTypedEventsOnly() {
    SinglePlayerMain game = new SinglePlayerMain();
    MainController controller = mock(MainController.class);
    // Same wiring and order as MainController: state listener + sink before case initialization
    // (the token/deduction counters travel the stateListener seam; everything else the sink).
    game.getGameContext().setStateListener(controller);
    game.getGameContext().setOutputSink(new GuiGameOutputSink(controller, Runnable::run));

    game.initializeCase(EngineFixtures.sapphire());
    game.processCommand("start case");

    // --- look: the room view is fed the DTO directly (no [ROOM_UPDATE] re-parse) ---
    game.processCommand("look");
    verify(controller, atLeastOnce()).updateRoomView(argThat(r -> "Ballroom".equals(r.getName())));

    // --- examine: clue narrative arrives as a dialogue event, journal refreshes ---
    game.processCommand("examine shattered_glass");
    verify(controller, atLeastOnce()).showDialogueBubble(any(DialogueEventDTO.class));
    verify(controller, atLeastOnce()).refreshJournalWindow();

    // --- deduce with no tokens: the deduction counter label updates ---
    game.processCommand("deduce shattered_glass");
    verify(controller, atLeastOnce()).onDeductionCountUpdate(1);

    // --- question: suspects are placed randomly, so co-locate Lord Ashworth first ---
    suspect(game, "LordAshworth").setCurrentRoom(game.getGameContext().getRoomByName("Ballroom"));
    game.processCommand("question LordAshworth");
    verify(controller, atLeastOnce())
        .showDialogueBubble(argThat(d -> d.getText().contains("speaking with the ambassador")));

    // --- journal review reaches the transcript as plain text ---
    game.processCommand("journal");

    // --- move: the new room arrives as a typed update ---
    game.processCommand("move east");
    verify(controller, atLeastOnce()).updateRoomView(argThat(r -> "Terrace".equals(r.getName())));

    // --- contradict: the award heals the pending deduction penalty first (issue 04 divergence),
    // so the deduction counter drops back to 0. The suspect must be present (security-pass
    // issue 06), so co-locate him on the Terrace first. ---
    game.processCommand("examine cigar_stub");
    suspect(game, "LordAshworth").setCurrentRoom(game.getGameContext().getRoomByName("Terrace"));
    game.processCommand("present cigar_stub to LordAshworth");
    verify(controller, atLeastOnce()).onDeductionCountUpdate(0);

    // --- combine: with no penalty left, the reward lands as an insight token ---
    game.processCommand("combine shattered_glass cigar_stub");
    verify(controller, atLeastOnce()).onInsightTokensUpdate(1);

    // --- final exam: started + question + result all flow through the sink ---
    game.processCommand("final exam");
    verify(controller).onSinglePlayerExamStarted();
    verify(controller, atLeastOnce()).onSinglePlayerQuestionUpdate();

    game.processCommand("1,1"); // sapphire's correct answers (slot1=s1_opt1, slot2=s2_opt1)
    verify(controller).onSinglePlayerExamResult();

    // --- the transcript never carries machine markers ---
    ArgumentCaptor<String> transcript = ArgumentCaptor.forClass(String.class);
    verify(controller, atLeastOnce()).appendTerminalText(transcript.capture(), any());
    List<String> lines = transcript.getAllValues();
    assertTrue(
        "journal review text reaches the transcript",
        lines.stream().anyMatch(t -> t.contains("Journal Contents:")));
    for (String tag :
        List.of(
            "[ROOM_UPDATE]", "[DIALOGUE_EVENT]", "[JOURNAL UPDATE]", "[EXAM_", "[SP_RESPONSE]")) {
      assertFalse(
          "machine marker " + tag + " must never reach the GUI terminal",
          lines.stream().anyMatch(t -> t.contains(tag)));
    }
    // The contradiction's pinboard sync is a Command, not a display DTO — it must never be printed
    // as a raw object line (e.g. "common.commands.pinboard.UpdatePinboardCommand@…").
    assertFalse(
        "an UpdatePinboardCommand must never reach the GUI terminal as a raw object line",
        lines.stream().anyMatch(t -> t.contains("UpdatePinboardCommand")));
  }

  /**
   * A single-player contradiction broadcasts an {@code UpdatePinboardCommand} to sync the red link.
   * With a pinboard handler registered (as {@link MainController} wires it), the handler receives
   * the {@link common.dto.pinboard.PinboardUpdateDTO} directly and nothing is emitted to the
   * terminal — the board draws the link without a raw command line ever printing.
   */
  @Test
  public void contradictionRoutesPinboardLinkToHandler_notTheTerminal() {
    SinglePlayerMain game = new SinglePlayerMain();
    MainController controller = mock(MainController.class);
    game.getGameContext().setStateListener(controller);
    game.getGameContext().setOutputSink(new GuiGameOutputSink(controller, Runnable::run));

    List<common.dto.pinboard.PinboardUpdateDTO> boardUpdates = new java.util.ArrayList<>();
    game.getGameContext().setPinboardUpdateHandler(boardUpdates::add);

    game.initializeCase(EngineFixtures.sapphire());
    game.processCommand("start case");
    // cigar_stub lives on the Terrace and contradicts Lord Ashworth's statement; co-locate him
    // there (suspects start in random rooms) before contradicting, mirroring the full-loop test.
    game.processCommand("move east");
    game.processCommand("examine cigar_stub");
    suspect(game, "LordAshworth").setCurrentRoom(game.getGameContext().getRoomByName("Terrace"));
    game.processCommand("contradict cigar_stub with LordAshworth");

    // The red link reached the board handler as an ADD_LINK update.
    assertTrue(
        "the contradiction's red link must reach the pinboard handler",
        boardUpdates.stream()
            .anyMatch(
                u -> u.getType() == common.dto.pinboard.PinboardUpdateDTO.UpdateType.ADD_LINK));

    // ...and the command never printed a raw object line to the transcript.
    ArgumentCaptor<String> transcript = ArgumentCaptor.forClass(String.class);
    verify(controller, atLeastOnce()).appendTerminalText(transcript.capture(), any());
    assertFalse(
        "the UpdatePinboardCommand must not reach the terminal",
        transcript.getAllValues().stream().anyMatch(t -> t.contains("UpdatePinboardCommand")));
  }

  private static Suspect suspect(SinglePlayerMain game, String name) {
    return game.getGameContext().getAllSuspects().stream()
        .filter(s -> s.getName().equalsIgnoreCase(name) || s.getId().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No suspect '" + name + "' loaded."));
  }
}

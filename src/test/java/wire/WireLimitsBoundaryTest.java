package wire;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import common.SerializationUtils;
import common.WireLimits;
import common.commands.AskWatsonCommand;
import common.commands.CombineCommand;
import common.commands.ContradictCommand;
import common.commands.DeduceCommand;
import common.commands.ExamineCommand;
import common.commands.JournalAddCommand;
import common.commands.MoveCommand;
import common.commands.QuestionCommand;
import common.dto.ChatMessage;
import common.dto.HostGameRequestDTO;
import common.dto.JoinPrivateGameRequestDTO;
import common.dto.JoinPublicGameRequestDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * String-length bounds on every player-controlled wire field (security-pass issue 02). The caps
 * live in the {@code @JsonCreator} constructors, so they hold for deserialized frames AND for
 * locally constructed commands; one end-to-end case proves an oversized frame is refused at the
 * deserialization boundary.
 */
public class WireLimitsBoundaryTest {

  private static String chars(int n) {
    return "x".repeat(n);
  }

  // --- end-to-end: the boundary refuses an oversized field --------------------------------------

  @Test
  public void oversizedChatTextIsRefusedAtTheDeserializationBoundary() {
    String json =
        "{\"@class\":\"common.dto.ChatMessage\",\"senderDisplayId\":\"p\",\"text\":\""
            + chars(WireLimits.MAX_CHAT_TEXT_LENGTH + 1)
            + "\",\"timestamp\":1}";

    assertThrows(
        IOException.class,
        () -> SerializationUtils.deserialize(json.getBytes(StandardCharsets.UTF_8)));
  }

  // --- per-type constructor caps -----------------------------------------------------------------

  @Test
  public void chatMessageAcceptsTextAtTheLimit() {
    ChatMessage msg = new ChatMessage("p", chars(WireLimits.MAX_CHAT_TEXT_LENGTH), 1L);
    assertEquals(WireLimits.MAX_CHAT_TEXT_LENGTH, msg.getText().length());
  }

  @Test
  public void chatMessageRejectsOversizedSenderName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ChatMessage(chars(WireLimits.MAX_DISPLAY_NAME_LENGTH + 1), "hi", 1L));
  }

  @Test
  public void moveCommandRejectsOversizedDirection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MoveCommand(chars(WireLimits.MAX_NAME_LENGTH + 1)));
  }

  @Test
  public void examineCommandRejectsOversizedObjectName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExamineCommand(chars(WireLimits.MAX_NAME_LENGTH + 1)));
  }

  @Test
  public void deduceCommandRejectsOversizedTargetName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeduceCommand(chars(WireLimits.MAX_NAME_LENGTH + 1)));
  }

  @Test
  public void questionCommandRejectsOversizedSuspectName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new QuestionCommand(chars(WireLimits.MAX_NAME_LENGTH + 1)));
  }

  @Test
  public void contradictCommandRejectsOversizedFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContradictCommand(chars(WireLimits.MAX_NAME_LENGTH + 1), "evidence"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContradictCommand("suspect", chars(WireLimits.MAX_ID_LENGTH + 1)));
  }

  @Test
  public void combineCommandRejectsOversizedNoteIds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CombineCommand(chars(WireLimits.MAX_ID_LENGTH + 1), "b"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CombineCommand("a", chars(WireLimits.MAX_ID_LENGTH + 1)));
  }

  @Test
  public void askWatsonCommandRejectsOversizedTarget() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AskWatsonCommand(chars(WireLimits.MAX_NAME_LENGTH + 1)));
  }

  @Test
  public void journalAddCommandRejectsOversizedNote() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalAddCommand(chars(WireLimits.MAX_NOTE_TEXT_LENGTH + 1)));
  }

  @Test
  public void journalAddCommandAcceptsNoteAtTheLimit() {
    JournalAddCommand cmd = new JournalAddCommand(chars(WireLimits.MAX_NOTE_TEXT_LENGTH));
    assertEquals(WireLimits.MAX_NOTE_TEXT_LENGTH, cmd.getNote().length());
  }

  @Test
  public void hostGameRequestRejectsOversizedFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new HostGameRequestDTO(chars(WireLimits.MAX_CASE_TITLE_LENGTH + 1), true, "en"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HostGameRequestDTO("case", true, chars(WireLimits.MAX_LANGUAGE_CODE_LENGTH + 1)));
  }

  @Test
  public void joinRequestsRejectOversizedIdentifiers() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JoinPublicGameRequestDTO(chars(WireLimits.MAX_ID_LENGTH + 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JoinPrivateGameRequestDTO(chars(WireLimits.MAX_ID_LENGTH + 1)));
  }

  @Test
  public void atLimitValuesStillRoundTrip() throws IOException {
    // The caps must not break legitimate maximal payloads.
    JournalAddCommand cmd = new JournalAddCommand(chars(WireLimits.MAX_NOTE_TEXT_LENGTH));
    Object restored = SerializationUtils.deserialize(SerializationUtils.serialize(cmd));
    assertTrue(restored instanceof JournalAddCommand);
    assertEquals(
        WireLimits.MAX_NOTE_TEXT_LENGTH, ((JournalAddCommand) restored).getNote().length());
  }
}

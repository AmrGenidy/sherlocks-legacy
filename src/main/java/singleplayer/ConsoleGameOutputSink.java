package singleplayer;

import common.dto.CommandCooldownUpdateDTO;
import common.dto.DeductionCountUpdateDTO;
import common.dto.DialogueEventDTO;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.FinalExamSlotDTO;
import common.dto.InitiateFinalExamDTO;
import common.dto.InsightTokenUpdateDTO;
import common.dto.JournalEntryDTO;
import common.dto.ReturnToCaseSelectionDTO;
import common.dto.RoomDescriptionDTO;
import common.dto.TaskStateUpdateDTO;
import common.dto.TextMessage;
import common.text.GameTexts;
import java.io.Serializable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link GameOutputSink} that renders each output DTO to the legacy marker-tagged console format
 * and writes it line-by-line to a sink (default {@code System.out}).
 *
 * <p>This is a verbatim relocation of the formatting that used to live inline in {@link
 * GameContextSinglePlayer#sendResponseToPlayer}. Keeping the exact text means terminal play and the
 * GUI's existing stdout-capture path stay byte-for-byte unchanged while the engine starts emitting
 * typed events. The state-update DTOs ({@link InsightTokenUpdateDTO}, {@link
 * DeductionCountUpdateDTO}, {@link CommandCooldownUpdateDTO}) are intentionally not rendered to the
 * console — they are surfaced by GUI state listeners — so {@link #render} returns {@code null} for
 * them and {@link #emit} writes nothing.
 */
public class ConsoleGameOutputSink implements GameOutputSink {

  private final Consumer<String> lineWriter;

  /** Writes rendered output to {@code System.out}, one line per emitted event. */
  public ConsoleGameOutputSink() {
    this(System.out::println);
  }

  /**
   * @param lineWriter receives each rendered line (already newline-free terminated by the writer);
   *     suppressed events are never passed to it.
   */
  public ConsoleGameOutputSink(Consumer<String> lineWriter) {
    this.lineWriter = lineWriter;
  }

  @Override
  public void emit(Serializable event) {
    String rendered = render(event);
    if (rendered != null) {
      lineWriter.accept(rendered);
    }
  }

  /**
   * Renders an output DTO to its legacy console string, or {@code null} if the DTO is suppressed
   * from console output (the token / deduction / cooldown state updates).
   */
  public static String render(Serializable responseDto) {
    if (responseDto == null) {
      return null;
    }
    if (responseDto instanceof TextMessage) {
      String text = ((TextMessage) responseDto).getText();
      if (text.contains("--- Final Exam Started ---")) {
        return "[EXAM_STARTED] " + text;
      }
      return text;
    } else if (responseDto instanceof RoomDescriptionDTO rd) {
      // Machine-readable tag for the (legacy) output parser.
      return "[ROOM_UPDATE]\n" + renderRoomBlock(rd);
    } else if (responseDto instanceof JournalEntryDTO) {
      return "[JOURNAL UPDATE] Statement added to journal.";
    } else if (responseDto instanceof DialogueEventDTO) {
      DialogueEventDTO de = (DialogueEventDTO) responseDto;
      String safeText = de.getText().replace("\n", "[NEWLINE]").replace("|", "[PIPE]");
      return "[DIALOGUE_EVENT] " + de.getTitle() + "|" + de.getType() + "|" + safeText;
    } else if (responseDto instanceof ExamQuestionDTO examQuestion) {
      return "[EXAM_QUESTION]\n" + renderExamQuestionBlock(examQuestion);
    } else if (responseDto instanceof ExamResultDTO) {
      return "[EXAM_RESULT]\n" + responseDto.toString();
    } else if (isConsoleSuppressed(responseDto)) {
      return null;
    } else {
      return "[SP_RESPONSE] " + responseDto;
    }
  }

  /**
   * Renders an output DTO to marker-free human-readable text for a display surface that consumes
   * typed events (the GUI terminal area), or {@code null} when the DTO has no textual
   * representation (the token / deduction / cooldown state updates). Same information content as
   * {@link #render}, minus the machine tags — nothing re-parses this text. Scaffolding labels stay
   * the legacy English; the GUI passes its UI-language texts to the overload.
   */
  public static String renderDisplayText(Serializable responseDto) {
    return renderDisplayText(responseDto, GameTexts.ENGLISH);
  }

  /**
   * {@link #renderDisplayText(Serializable)} with caller-supplied scaffolding labels
   * (ui-localization): room block labels, the journal-update line, and the exam-question block come
   * from {@code texts}; DTO content passes through untouched.
   */
  public static String renderDisplayText(Serializable responseDto, GameTexts texts) {
    if (responseDto == null) {
      return null;
    }
    if (responseDto instanceof TextMessage) {
      return ((TextMessage) responseDto).getText();
    } else if (responseDto instanceof RoomDescriptionDTO rd) {
      return renderRoomBlock(rd, texts);
    } else if (responseDto instanceof JournalEntryDTO) {
      return texts.statementAddedToJournal();
    } else if (responseDto instanceof DialogueEventDTO de) {
      return de.getTitle() + ": " + de.getText();
    } else if (responseDto instanceof ExamQuestionDTO examQuestion) {
      return renderExamQuestionBlock(examQuestion, texts);
    } else if (responseDto instanceof ExamResultDTO) {
      return responseDto.toString();
    } else if (isConsoleSuppressed(responseDto)) {
      return null;
    } else {
      return String.valueOf(responseDto);
    }
  }

  /**
   * DTOs with no textual representation: the token / deduction / cooldown / task state updates
   * (surfaced via GUI handlers) and the typed exam-start signal (the accompanying marker
   * TextMessage carries the console text).
   */
  private static boolean isConsoleSuppressed(Serializable responseDto) {
    return responseDto instanceof InsightTokenUpdateDTO
        || responseDto instanceof DeductionCountUpdateDTO
        || responseDto instanceof CommandCooldownUpdateDTO
        || responseDto instanceof InitiateFinalExamDTO
        || responseDto instanceof TaskStateUpdateDTO
        || responseDto instanceof ReturnToCaseSelectionDTO;
  }

  private static String renderRoomBlock(RoomDescriptionDTO rd) {
    return renderRoomBlock(rd, GameTexts.ENGLISH);
  }

  private static String renderRoomBlock(RoomDescriptionDTO rd, GameTexts texts) {
    StringBuilder sb = new StringBuilder();
    // The Room is shown by its Display Name everywhere (.scratch/gui-localized-case-names); falls
    // back to the Universal name. Objects/Occupants below stay Universal.
    sb.append(texts.roomHeader(rd.getDisplayName())).append("\n");
    sb.append(rd.getDescription()).append("\n");
    sb.append(texts.objectsLabel())
        .append(" ")
        .append(
            joinDisplayNames(
                rd.getObjectNames(),
                rd.getObjectDisplayNames(),
                texts.watsonSpeaker(),
                texts.noneLabel()))
        .append("\n");
    sb.append(texts.occupantsLabel())
        .append(" ")
        .append(
            joinDisplayNames(
                rd.getOccupantNames(),
                rd.getOccupantDisplayNames(),
                texts.watsonSpeaker(),
                texts.noneLabel()))
        .append("\n");
    sb.append(texts.exitsLabel()).append(" ");
    if (rd.getExits().isEmpty()) {
      sb.append(texts.noneLabel());
    } else {
      rd.getExits()
          .forEach((dir, roomName) -> sb.append(texts.exitEntry(dir, roomName)).append(", "));
      sb.setLength(sb.length() - 2);
    }
    return sb.toString();
  }

  /**
   * Joins universal names to a comma-separated list of their per-language Display Names (from the
   * DTO side map), falling back to the Universal name. "Dr. Watson" has no per-case Display Name
   * unless the assistant was renamed, so it falls back to the localized speaker label.
   */
  private static String joinDisplayNames(
      java.util.List<String> names,
      java.util.Map<String, String> displayMap,
      String watsonSpeaker,
      String noneLabel) {
    if (names == null || names.isEmpty()) {
      return noneLabel;
    }
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String n : names) {
      String display = displayMap == null ? null : displayMap.get(n);
      if (display == null || display.isBlank()) {
        display = "Dr. Watson".equals(n) ? watsonSpeaker : n;
      }
      out.add(display);
    }
    return String.join(", ", out);
  }

  private static String renderExamQuestionBlock(ExamQuestionDTO examQuestion) {
    return renderExamQuestionBlock(examQuestion, GameTexts.ENGLISH);
  }

  private static String renderExamQuestionBlock(ExamQuestionDTO examQuestion, GameTexts texts) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n")
        .append(
            texts.examQuestionHeader(
                examQuestion.getQuestionIndex() + 1, examQuestion.getTotalQuestions()))
        .append("\n");
    sb.append(examQuestion.getQuestionPrompt()).append("\n");
    for (Map.Entry<String, FinalExamSlotDTO> entry : examQuestion.getSlots().entrySet()) {
      sb.append("\n").append(texts.slotChoicesHeader(entry.getKey())).append("\n");
      for (int i = 0; i < entry.getValue().getChoices().size(); i++) {
        sb.append("  ")
            .append(i + 1)
            .append(") ")
            .append(entry.getValue().getChoices().get(i).getChoiceText())
            .append("\n");
      }
    }
    sb.append("\n").append(texts.enterChoicesPrompt());
    return sb.toString();
  }
}

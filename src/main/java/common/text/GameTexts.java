package common.text;

/**
 * Scaffolding strings for game-flow announcements and console/transcript rendering
 * (.scratch/ui-localization follow-up). Policy: SCAFFOLDING (headers, labels, prompts) comes from
 * the UI language via this seam; case CONTENT (descriptions, Statements, Task text, Rank Tier
 * names) comes from the selected case localization and passes through untouched.
 *
 * <p>{@link #ENGLISH} reproduces the legacy strings byte-for-byte and is the default everywhere —
 * the server and the terminal clients keep emitting exactly what they always did (the host cannot
 * know each remote player's UI language). The GUI wires an L10n-backed implementation ({@code
 * ui.i18n.L10nGameTexts}) into the single-player engine, the SP transcript renderer, and the
 * multiplayer client's console writer.
 */
public interface GameTexts {

  // --- Engine: case-start announcement (engine.GameEngine#announceCaseStart) ---

  String caseDescriptionHeader();

  String caseTasksHeader();

  String noTasksAvailable();

  String rankEvaluationHeader();

  String rankEvaluationExplainer();

  /** One non-default Rank Tier line, e.g. {@code " - Master Detective : 0-2 deductions"}. */
  String rankTierLine(String rankName, String deductionsRange);

  /** The default Rank Tier line, e.g. {@code " - Confused Constable : 5+ deductions"}. */
  String rankTierDefaultLine(String rankName, int fromDeductions);

  String startingLocation(String roomName);

  String typeHelpPrompt();

  String missingCaseData();

  String startingRoomNotFound();

  // --- Room description rendering (ConsoleGameOutputSink display text, GameClient console) ---

  /** Single-player room block header: {@code "--- <room> ---"}. */
  String roomHeader(String roomName);

  /** Multiplayer console room header: {@code "--- Location: <room> ---"}. */
  String locationHeader(String roomName);

  String objectsLabel();

  String occupantsLabel();

  String exitsLabel();

  String noneLabel();

  /** The assistant's display name for the room occupant listing (localized "Dr. Watson"). */
  String watsonSpeaker();

  /** One exit entry: {@code "east (to Terrace)"}. */
  String exitEntry(String direction, String roomName);

  // --- Misc transcript lines ---

  String statementAddedToJournal();

  String examQuestionHeader(int oneBasedIndex, int total);

  /** Per-slot choices header: {@code "<slot> choices:"}. */
  String slotChoicesHeader(String slotName);

  String enterChoicesPrompt();

  String unknownCommand();

  /**
   * In-world refusal shown when a gameplay/action command is attempted while a Final Exam is in
   * progress (.scratch/exam-command-lockout). Only chat, the Journal and the Pinboard stay open.
   */
  String commandUnavailableDuringFinalExam();

  /**
   * In-world refusal shown when a gameplay mutation or the Final Exam is attempted during a Review
   * Session (.scratch/gui-review-enter-case) — reviewing a solved Case is read-only.
   */
  String commandUnavailableDuringReview();

  /** The legacy strings, byte-for-byte — the default for server, engine tests, terminal play. */
  GameTexts ENGLISH =
      new GameTexts() {
        @Override
        public String caseDescriptionHeader() {
          return "--- Case Description ---";
        }

        @Override
        public String caseTasksHeader() {
          return "--- Case Tasks ---";
        }

        @Override
        public String noTasksAvailable() {
          return "No tasks available for this case.";
        }

        @Override
        public String rankEvaluationHeader() {
          return "--- Rank Evaluation ---";
        }

        @Override
        public String rankEvaluationExplainer() {
          return "Your final rank will be determined by the number of 'deduce' commands used:";
        }

        @Override
        public String rankTierLine(String rankName, String deductionsRange) {
          return String.format("  - %-20s: %s deductions", rankName, deductionsRange);
        }

        @Override
        public String rankTierDefaultLine(String rankName, int fromDeductions) {
          return String.format("  - %-20s: %d+ deductions", rankName, fromDeductions);
        }

        @Override
        public String startingLocation(String roomName) {
          return "You are now at the starting location: " + roomName;
        }

        @Override
        public String typeHelpPrompt() {
          return "Type 'help' to see available commands.";
        }

        @Override
        public String missingCaseData() {
          return "Critical error: Case data missing, cannot start.";
        }

        @Override
        public String startingRoomNotFound() {
          return "Error: Starting location not found for the case.";
        }

        @Override
        public String roomHeader(String roomName) {
          return "--- " + roomName + " ---";
        }

        @Override
        public String locationHeader(String roomName) {
          return "--- Location: " + roomName + " ---";
        }

        @Override
        public String objectsLabel() {
          return "Objects:";
        }

        @Override
        public String occupantsLabel() {
          return "Occupants:";
        }

        @Override
        public String exitsLabel() {
          return "Exits:";
        }

        @Override
        public String noneLabel() {
          return "None";
        }

        @Override
        public String watsonSpeaker() {
          return "Dr. Watson";
        }

        @Override
        public String exitEntry(String direction, String roomName) {
          return direction + " (to " + roomName + ")";
        }

        @Override
        public String statementAddedToJournal() {
          return "Statement added to journal.";
        }

        @Override
        public String examQuestionHeader(int oneBasedIndex, int total) {
          return "--- EXAM QUESTION " + oneBasedIndex + " of " + total + " ---";
        }

        @Override
        public String slotChoicesHeader(String slotName) {
          return slotName + " choices:";
        }

        @Override
        public String enterChoicesPrompt() {
          return "Enter your choices (e.g., '1,2'):";
        }

        @Override
        public String unknownCommand() {
          return "Unknown command. Type 'help' for available commands.";
        }

        @Override
        public String commandUnavailableDuringFinalExam() {
          return "Not now, Detective — finish the Final Exam first."
              + " You may still consult your Journal and Pinboard.";
        }

        @Override
        public String commandUnavailableDuringReview() {
          return "You are reviewing a solved case — that action is sealed."
              + " You may still move between rooms and consult your Journal and Pinboard.";
        }
      };
}

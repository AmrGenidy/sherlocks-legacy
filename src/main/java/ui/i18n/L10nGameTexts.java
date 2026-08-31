package ui.i18n;

import common.text.GameTexts;

/**
 * {@link GameTexts} backed by the active UI language bundle ({@code console.*} keys). Wired by the
 * GUI into the single-player engine, the SP transcript renderer, and the multiplayer client's
 * console writer — terminal play and the server keep {@link GameTexts#ENGLISH}.
 *
 * <p>Reads {@link L10n} live on every call, so a language switch in the menu applies to the next
 * case without rewiring.
 */
public final class L10nGameTexts implements GameTexts {

  @Override
  public String caseDescriptionHeader() {
    return L10n.t("console.caseDescriptionHeader");
  }

  @Override
  public String caseTasksHeader() {
    return L10n.t("console.caseTasksHeader");
  }

  @Override
  public String noTasksAvailable() {
    return L10n.t("console.noTasks");
  }

  @Override
  public String rankEvaluationHeader() {
    return L10n.t("console.rankEvaluationHeader");
  }

  @Override
  public String rankEvaluationExplainer() {
    return L10n.t("console.rankExplainer");
  }

  @Override
  public String rankTierLine(String rankName, String deductionsRange) {
    return L10n.t("console.rankTierLine", rankName, deductionsRange);
  }

  @Override
  public String rankTierDefaultLine(String rankName, int fromDeductions) {
    return L10n.t("console.rankTierDefaultLine", rankName, fromDeductions);
  }

  @Override
  public String startingLocation(String roomName) {
    return L10n.t("console.startingLocation", roomName);
  }

  @Override
  public String typeHelpPrompt() {
    return L10n.t("console.typeHelp");
  }

  @Override
  public String missingCaseData() {
    return L10n.t("console.missingCaseData");
  }

  @Override
  public String startingRoomNotFound() {
    return L10n.t("console.startingRoomNotFound");
  }

  @Override
  public String roomHeader(String roomName) {
    return L10n.t("console.roomHeader", roomName);
  }

  @Override
  public String locationHeader(String roomName) {
    return L10n.t("console.locationHeader", roomName);
  }

  @Override
  public String objectsLabel() {
    return L10n.t("console.objects");
  }

  @Override
  public String occupantsLabel() {
    return L10n.t("console.occupants");
  }

  @Override
  public String watsonSpeaker() {
    return L10n.t("game.watsonSpeaker");
  }

  @Override
  public String exitsLabel() {
    return L10n.t("console.exits");
  }

  @Override
  public String noneLabel() {
    return L10n.t("console.none");
  }

  @Override
  public String exitEntry(String direction, String roomName) {
    return L10n.t("console.exitEntry", direction, roomName);
  }

  @Override
  public String statementAddedToJournal() {
    return L10n.t("console.statementAdded");
  }

  @Override
  public String examQuestionHeader(int oneBasedIndex, int total) {
    return L10n.t("console.examQuestionHeader", oneBasedIndex, total);
  }

  @Override
  public String slotChoicesHeader(String slotName) {
    return L10n.t("console.slotChoices", slotName);
  }

  @Override
  public String enterChoicesPrompt() {
    return L10n.t("console.enterChoices");
  }

  @Override
  public String unknownCommand() {
    return L10n.t("console.unknownCommand");
  }

  @Override
  public String commandUnavailableDuringFinalExam() {
    return L10n.t("console.examLockout");
  }

  @Override
  public String commandUnavailableDuringReview() {
    return L10n.t("console.reviewLockout");
  }
}

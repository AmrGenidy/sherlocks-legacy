package singleplayer;

import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile;
import common.commands.Command;
import extractors.BuildingExtractor;
import extractors.CaseLoader;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import singleplayer.util.CommandFactorySinglePlayer;
import singleplayer.util.CommandParserSinglePlayer;

public class SinglePlayerMain {

  public static final String CASES_DIRECTORY = "cases";

  private final GameContextSinglePlayer gameContext;

  public SinglePlayerMain() {
    this.gameContext = new GameContextSinglePlayer();
  }

  public List<CaseFile> getAvailableCases() {
    return CaseLoader.loadCases(CASES_DIRECTORY);
  }

  public LocalizedCaseFile selectCaseAndLanguage(CaseFile caseFile, String languageCode) {
    if (caseFile == null || languageCode == null) {
      return null;
    }
    return new LocalizedCaseFile(caseFile, languageCode);
  }

  public void initializeCase(LocalizedCaseFile caseFile) {
    if (caseFile == null) return;

    System.out.println("\nLoading case: " + caseFile.getTitle() + "...");
    gameContext.resetForNewCaseLoad();

    boolean loadingSuccess = true;
    try {
      if (!BuildingExtractor.loadBuilding(caseFile, gameContext)) {
        System.out.println("Error: Failed to load building from case file.");
        loadingSuccess = false;
      } else {
        GameObjectExtractor.loadObjects(caseFile, gameContext);
        SuspectExtractor.loadSuspects(caseFile, gameContext);
      }
    } catch (Exception e) {
      System.err.println(
          "CRITICAL_LOAD_ERROR for '" + caseFile.getTitle() + "': " + e.getMessage());
      e.printStackTrace();
      loadingSuccess = false;
    }

    if (!loadingSuccess) {
      System.out.println(
          "Failed to load case '"
              + caseFile.getTitle()
              + "' completely. Returning to case selection.");
      return;
    }

    gameContext.initializeNewCase(caseFile, caseFile.getStartingRoom());

    System.out.println("\n--- Case Invitation ---");
    System.out.println(caseFile.getInvitation());
    System.out.println("\nType 'start case' to begin.");
  }

  public void processCommand(String input) {
    if (input == null || input.trim().isEmpty()) {
      return;
    }
    String trimmedInput = input.trim();

    // Handle exam input directly if exam is active and input looks like an answer
    if (gameContext.isExamActive() && trimmedInput.matches("(\\d+[,\\s]+)+\\d+")) {
      handleExamInput(trimmedInput);
      return;
    }

    String[] parsedInput = CommandParserSinglePlayer.parseInputSimple(trimmedInput);
    Command commandToExecute;
    try {
      commandToExecute = CommandFactorySinglePlayer.createCommand(parsedInput);
    } catch (IllegalArgumentException e) {
      // Command constructors enforce the wire limits (WireLimits); surface the reason instead of
      // letting the exception escape the input handler.
      gameContext.getOutputSink().emit(new common.dto.TextMessage(e.getMessage(), true));
      return;
    }

    if (commandToExecute != null) {
      if (gameContext.getPlayerDetective(null) != null) {
        commandToExecute.setPlayerId(gameContext.getPlayerDetective(null).getPlayerId());
        commandToExecute.execute(gameContext);
      } else {
        gameContext
            .getOutputSink()
            .emit(new common.dto.TextMessage("Player detective not initialized. Cannot execute command.", true));
      }
    } else {
      // Through the sink so the GUI terminal sees it too (localized scaffolding);
      // the console default sink prints it exactly as before.
      gameContext
          .getOutputSink()
          .emit(new common.dto.TextMessage(gameContext.getGameTexts().unknownCommand(), true));
    }
  }

  private void handleExamInput(String input) {
    common.dto.ExamQuestionDTO qDto = gameContext.getCurrentExamQuestionDTO();
    if (qDto == null) return;

    String[] parts = input.split("[,\\s]+");
    if (parts.length != qDto.getSlots().size()) {
      gameContext
          .getOutputSink()
          .emit(
              new common.dto.TextMessage(
                  "Please provide answers for all " + qDto.getSlots().size() + " slots (e.g. '1,2').",
                  true));
      return;
    }

    Map<String, String> answers = new HashMap<>();
    // Need to map 1-based indices to choice IDs.
    // Assuming standard iteration order matching DTO creation in
    // GameContextSinglePlayer?
    // Actually DTO has a Map<String, FinalExamSlotDTO>.
    // We need stable ordering. DTO creation uses HashMap?
    // Wait, GameContextSinglePlayer creates DTO.
    // We need access to the original FinalExam structure or trust the DTO map keys
    // are sorted?
    // DTO Slots map keys are strings (slot1, slot2).
    List<String> sortedSlotIds = new ArrayList<>(qDto.getSlots().keySet());
    Collections.sort(sortedSlotIds);

    try {
      int i = 0;
      for (String slotId : sortedSlotIds) {
        int choiceIdx = Integer.parseInt(parts[i]) - 1;
        common.dto.FinalExamSlotDTO slot = qDto.getSlots().get(slotId);
        if (choiceIdx >= 0 && choiceIdx < slot.getChoices().size()) {
          answers.put(slotId, slot.getChoices().get(choiceIdx).getChoiceId());
        } else {
          gameContext
              .getOutputSink()
              .emit(new common.dto.TextMessage("Invalid choice number for slot " + (i + 1) + ".", true));
          return;
        }
        i++;
      }
      gameContext.processSubmitQuestionAnswer(
          gameContext.getPlayerDetective(null).getPlayerId(), qDto.getQuestionIndex(), answers);
    } catch (NumberFormatException e) {
      gameContext.getOutputSink().emit(new common.dto.TextMessage("Invalid input format.", true));
    }
  }

  public GameContextSinglePlayer getGameContext() {
    return this.gameContext;
  }

  public List<String> getCurrentCaseTasks() {
    if (gameContext != null && gameContext.getCaseFile() != null) {
      return gameContext.getCaseFile().getTasks();
    }
    return null;
  }
}

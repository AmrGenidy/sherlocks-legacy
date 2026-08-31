package client.tutorial;

import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a tutorial script against a {@link TutorialHost}.
 *
 * <p>Steps are a tiny state machine: {@code SHOW_OVERLAY} paints a (localized, image-backed) hint
 * and auto-advances; {@code AWAIT_COMMAND} blocks until the player types the expected command;
 * {@code END} finishes. Room rendering is NOT faked here — the host's real game engine drives the
 * room view through its typed-event sink, so every taught command actually executes.
 *
 * <p>Overlay copy is resolved through an injected {@link Function} (the active-language bundle in
 * production, a fake in tests), keeping localization out of this headless-testable class.
 */
public class TutorialManager {

  private static final Logger logger = LoggerFactory.getLogger(TutorialManager.class);

  private final TutorialHost host;
  private final TutorialLoader tutorialLoader;
  private final Function<String, String> textResolver;
  private final TutorialProgressStore progressStore;

  private boolean active = false;
  private TutorialScript currentScript;
  private String currentTutorialId;
  private int currentStepIndex = 0;

  /** Production-ish default: identity text resolver (keys pass through). */
  public TutorialManager(TutorialHost host) {
    this(host, Function.identity(), new TutorialLoader(), new TutorialProgressStore());
  }

  public TutorialManager(TutorialHost host, Function<String, String> textResolver) {
    this(host, textResolver, new TutorialLoader(), new TutorialProgressStore());
  }

  public TutorialManager(
      TutorialHost host,
      Function<String, String> textResolver,
      TutorialLoader tutorialLoader,
      TutorialProgressStore progressStore) {
    this.host = host;
    this.textResolver = textResolver == null ? Function.identity() : textResolver;
    this.tutorialLoader = tutorialLoader;
    this.progressStore = progressStore;
  }

  public boolean isActive() {
    return active;
  }

  /** The practice-case room a tutorial wants to open in, or null to use the case's own start. */
  public String startRoomFor(String id) {
    TutorialScript script = tutorialLoader.getTutorial(id);
    return script == null ? null : script.getStartRoom();
  }

  /** The silent seed commands a tutorial runs at start (empty if none). */
  public java.util.List<String> seedCommandsFor(String id) {
    TutorialScript script = tutorialLoader.getTutorial(id);
    if (script == null || script.getSeedCommands() == null) {
      return java.util.List.of();
    }
    return script.getSeedCommands();
  }

  public boolean isCompleted(String id) {
    return progressStore != null && progressStore.isCompleted(id);
  }

  /**
   * The {@code expectedCommand} of the step currently being awaited, or null if no AWAIT_COMMAND
   * step is active. Lets the shell gate input per tutorial step (e.g. enforce the taught exam input
   * method — .scratch/gui-exam-tutorial-input-enforce).
   */
  public String currentExpectedCommand() {
    if (!active || currentScript == null || currentScript.getSteps() == null) {
      return null;
    }
    List<TutorialStep> steps = currentScript.getSteps();
    if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
      return null;
    }
    TutorialStep step = steps.get(currentStepIndex);
    return "AWAIT_COMMAND".equals(step.getType()) ? step.getExpectedCommand() : null;
  }

  public void startTutorial(String id) {
    TutorialScript script = tutorialLoader.getTutorial(id);
    if (script == null) {
      logger.warn("Tutorial not found: {}", id);
      host.appendTerminalText("\n[TUTORIAL] Tutorial not found: " + id + "\n");
      return;
    }

    logger.info("Starting tutorial: {}", id);
    this.currentScript = script;
    this.currentTutorialId = id;
    this.active = true;
    this.currentStepIndex = 0;

    // The engine drives the room; we just make sure the game view is on screen.
    host.showGameView();
    host.appendTerminalText("\n=== Starting Tutorial: " + id + " ===\n");
    processNextStep();
  }

  public void processNextStep() {
    if (!active || currentScript == null || currentScript.getSteps() == null) {
      return;
    }

    List<TutorialStep> steps = currentScript.getSteps();
    if (currentStepIndex >= steps.size()) {
      logger.warn("Tutorial step index out of bounds, ending tutorial");
      endTutorial(true);
      return;
    }

    TutorialStep step = steps.get(currentStepIndex);
    logger.debug("Processing step {}: {}", currentStepIndex, step.getType());

    switch (step.getType()) {
      case "SHOW_OVERLAY":
        handleShowOverlay(step);
        currentStepIndex++;
        processNextStep();
        break;

      case "AWAIT_COMMAND":
        // Don't advance, wait for player input.
        logger.debug("Waiting for command: {}", step.getExpectedCommand());
        break;

      case "END":
        endTutorial(true);
        break;

      default:
        // SETUP_SCENE is retired — the real engine renders the room. Tolerate any
        // unknown/legacy step by skipping it rather than breaking the run.
        logger.debug("Skipping non-rendering tutorial step type: {}", step.getType());
        currentStepIndex++;
        processNextStep();
        break;
    }
  }

  private void handleShowOverlay(TutorialStep step) {
    String message =
        step.getTextKey() != null ? textResolver.apply(step.getTextKey()) : step.getText();

    // The final overlay in a script is the completion ("type continue") bubble — never dismissible;
    // every earlier instruction can be closed after reading (.scratch/gui-tutorial-bubble-polish).
    boolean dismissible = hasLaterOverlay(currentStepIndex);
    if (message != null) {
      host.showTutorialOverlay(message, step.getArrowTarget(), dismissible);
    }
    logger.debug(
        "Show overlay: textKey={} target={} dismissible={}",
        step.getTextKey(),
        step.getArrowTarget(),
        dismissible);
  }

  /**
   * True if any step after {@code afterIndex} is another SHOW_OVERLAY (so more instructions
   * follow).
   */
  private boolean hasLaterOverlay(int afterIndex) {
    List<TutorialStep> steps = currentScript.getSteps();
    for (int i = afterIndex + 1; i < steps.size(); i++) {
      if ("SHOW_OVERLAY".equals(steps.get(i).getType())) {
        return true;
      }
    }
    return false;
  }

  public boolean processInput(String command) {
    if (!active || currentScript == null || currentScript.getSteps() == null) {
      return false;
    }

    if (command.equalsIgnoreCase("exit")
        || command.equalsIgnoreCase("quit")
        || command.equalsIgnoreCase("back")) {
      host.appendTerminalText("\n[EXITING TUTORIAL]\n");
      endTutorial(false);
      return true;
    }

    List<TutorialStep> steps = currentScript.getSteps();
    if (currentStepIndex >= steps.size()) {
      return false;
    }

    TutorialStep currentStep = steps.get(currentStepIndex);
    if (!"AWAIT_COMMAND".equals(currentStep.getType())) {
      return false;
    }

    String expected = currentStep.getExpectedCommand();
    if (expected == null) {
      logger.warn("AWAIT_COMMAND step has no expectedCommand");
      currentStepIndex++;
      processNextStep();
      return true;
    }

    if (matchesExpected(command.trim(), expected.trim())) {
      host.appendTerminalText("\n[SUCCESS] Correct! " + expected + "\n");
      currentStepIndex++;
      processNextStep();
      return true;
    } else {
      host.appendTerminalText("\n[TRY AGAIN] Expected: " + expected + "\n");
      return false;
    }
  }

  /**
   * Matches typed input against a step's expected command. Literal {@code equalsIgnoreCase} by
   * default; an expected ending in {@code " *"} is a verb-prefix wildcard ({@code "contradict *"}
   * matches {@code "contradict clue:x with The Valet"}) — used for board actions whose full command
   * is dynamic (.scratch/gui-pinboard-tutorial).
   */
  private static boolean matchesExpected(String command, String expected) {
    if (expected.endsWith(" *")) {
      String prefix = expected.substring(0, expected.length() - 1); // keep the trailing space
      return command.toLowerCase().startsWith(prefix.toLowerCase());
    }
    return command.equalsIgnoreCase(expected);
  }

  private void endTutorial(boolean completed) {
    if (completed && currentTutorialId != null && progressStore != null) {
      progressStore.markCompleted(currentTutorialId);
    }
    active = false;
    currentScript = null;
    currentTutorialId = null;
    currentStepIndex = 0;

    host.hideTutorialOverlay();
    host.showTutorialsMenu();

    host.appendTerminalText("\n=== Tutorial Complete! ===\n");
    logger.info("Tutorial ended (completed={})", completed);
  }
}

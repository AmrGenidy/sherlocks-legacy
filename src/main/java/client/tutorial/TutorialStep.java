package client.tutorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Represents a single step in a tutorial script. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TutorialStep {

  private String type;
  private String text;
  private String textKey;
  private String arrowTarget;
  private String expectedCommand;
  private SceneSetupData sceneSetup;

  public TutorialStep() {}

  public TutorialStep(
      String type,
      String text,
      String arrowTarget,
      String expectedCommand,
      SceneSetupData sceneSetup) {
    this.type = type;
    this.text = text;
    this.arrowTarget = arrowTarget;
    this.expectedCommand = expectedCommand;
    this.sceneSetup = sceneSetup;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  /**
   * i18n bundle key for this step's overlay copy (e.g. {@code tutorial.move.step1}). When present
   * it is resolved through the active language bundle; {@link #getText()} is only a dev fallback.
   */
  public String getTextKey() {
    return textKey;
  }

  public void setTextKey(String textKey) {
    this.textKey = textKey;
  }

  public String getArrowTarget() {
    return arrowTarget;
  }

  public void setArrowTarget(String arrowTarget) {
    this.arrowTarget = arrowTarget;
  }

  public String getExpectedCommand() {
    return expectedCommand;
  }

  public void setExpectedCommand(String expectedCommand) {
    this.expectedCommand = expectedCommand;
  }

  public SceneSetupData getSceneSetup() {
    return sceneSetup;
  }

  public void setSceneSetup(SceneSetupData sceneSetup) {
    this.sceneSetup = sceneSetup;
  }
}

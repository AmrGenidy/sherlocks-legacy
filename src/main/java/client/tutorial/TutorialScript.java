package client.tutorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Represents a complete tutorial script with multiple steps. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TutorialScript {

  private List<TutorialStep> steps;
  private String startRoom;
  private List<String> seedCommands;

  public TutorialScript() {}

  public TutorialScript(List<TutorialStep> steps) {
    this.steps = steps;
  }

  public List<TutorialStep> getSteps() {
    return steps;
  }

  public void setSteps(List<TutorialStep> steps) {
    this.steps = steps;
  }

  /**
   * Optional practice-case room the player is placed in when this tutorial starts. When null the
   * practice case's own starting room is used. Lets each tutorial open in the room that has the
   * objects/suspects it teaches against (e.g. examine opens where the clue lives).
   */
  public String getStartRoom() {
    return startRoom;
  }

  public void setStartRoom(String startRoom) {
    this.startRoom = startRoom;
  }

  /**
   * Optional engine commands run silently against the practice case when this tutorial starts —
   * BEFORE the GUI sink is wired — so the board/journal can enter the lesson pre-seeded (examined
   * clues, questioned suspects) without a long visible setup (.scratch/gui-pinboard-tutorial).
   */
  public List<String> getSeedCommands() {
    return seedCommands;
  }

  public void setSeedCommands(List<String> seedCommands) {
    this.seedCommands = seedCommands;
  }
}

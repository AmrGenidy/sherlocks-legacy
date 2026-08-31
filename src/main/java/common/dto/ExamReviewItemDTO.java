package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

/**
 * One reviewed Final Exam question (gui-exam-review): the question prompt with its blanks filled by
 * the player's chosen choice <b>text</b>, plus whether they got it right. Carries no slot/choice
 * ids and never the correct answer — the exam is retryable, so the review shows only what the
 * player picked and a right/wrong mark. Travels over the wire (allowed by the {@code common.dto.}
 * prefix).
 */
public class ExamReviewItemDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private int questionNumber;
  private String filledPrompt;
  private boolean correct;

  public ExamReviewItemDTO() {}

  @JsonCreator
  public ExamReviewItemDTO(
      @JsonProperty("questionNumber") int questionNumber,
      @JsonProperty("filledPrompt") String filledPrompt,
      @JsonProperty("correct") boolean correct) {
    this.questionNumber = questionNumber;
    this.filledPrompt = filledPrompt;
    this.correct = correct;
  }

  public int getQuestionNumber() {
    return questionNumber;
  }

  public String getFilledPrompt() {
    return filledPrompt;
  }

  public boolean isCorrect() {
    return correct;
  }

  public void setQuestionNumber(int questionNumber) {
    this.questionNumber = questionNumber;
  }

  public void setFilledPrompt(String filledPrompt) {
    this.filledPrompt = filledPrompt;
  }

  public void setCorrect(boolean correct) {
    this.correct = correct;
  }
}

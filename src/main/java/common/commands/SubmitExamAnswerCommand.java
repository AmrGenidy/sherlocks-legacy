package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.interfaces.GameActionContext;
import java.io.Serializable;

public class SubmitExamAnswerCommand implements Command, Serializable {
    private static final long serialVersionUID = 1L;
    private String playerId;
    private final int questionNumber;
    private final String answerText;

    @JsonCreator
    public SubmitExamAnswerCommand(
            @JsonProperty("questionNumber") int questionNumber,
            @JsonProperty("answerText") String answerText) {
        this.questionNumber = questionNumber;
        this.answerText = answerText;
    }

    @Override
    public void execute(GameActionContext context) {
        // This is now handled by SelectExamAnswerCommand
    }

    @Override
    public String getDescription() {
        return "Submits an answer for a final exam question.";
    }

    @Override
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public String getAnswerText() {
        return answerText;
    }
}

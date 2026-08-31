package common.commands;

import common.interfaces.GameActionContext;
import java.util.Map;

public class SubmitQuestionAnswerCommand extends BaseCommand {

    private int questionIndex;
    private Map<String, String> answers;

    public SubmitQuestionAnswerCommand() {
        super(false);
    }

    public SubmitQuestionAnswerCommand(int questionIndex, Map<String, String> answers) {
        super(false);
        this.questionIndex = questionIndex;
        this.answers = answers;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        context.processSubmitQuestionAnswer(getPlayerId(), questionIndex, answers);
    }

    @Override
    public String getDescription() {
        return "Submits answers for a specific final exam question.";
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(int questionIndex) {
        this.questionIndex = questionIndex;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }
}

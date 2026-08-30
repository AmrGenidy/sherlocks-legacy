package client.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FinalExamState implements Serializable {
    private static final long serialVersionUID = 1L;

    private int currentQuestionIndex;
    private final Map<Integer, Map<String, String>> answers; // Key: Question Index, Value: Map<SlotID, ChoiceID>
    private boolean submitted;

    public FinalExamState() {
        this.currentQuestionIndex = 0;
        this.answers = new HashMap<>();
        this.submitted = false;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    public Map<Integer, Map<String, String>> getAnswers() {
        return answers;
    }

    public Map<String, String> getAnswersForQuestion(int questionIndex) {
        return answers.getOrDefault(questionIndex, new HashMap<>());
    }

    public void setAnswer(int questionIndex, String slotId, String choiceId) {
        answers.computeIfAbsent(questionIndex, k -> new HashMap<>()).put(slotId, choiceId);
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }
}

package client.exam;

import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;

/**
 * Listener interface for the FinalExamController to communicate with UI layers (GUI/Terminal).
 */
public interface FinalExamListener {

    /**
     * Called to display a specific exam question to the user.
     * @param questionDTO The DTO containing all necessary info to render the question.
     */
    void showQuestion(ExamQuestionDTO questionDTO);

    /**
     * Called to display the final results of the exam.
     * @param resultDTO The DTO containing the score and feedback.
     */
    void showExamResults(ExamResultDTO resultDTO);

    /**
     * Called when the exam is started, instructing the UI to switch to the exam view.
     */
    void showExamView();

    /**
     * Called when there are unanswered questions and the user tries to submit.
     */
    void notifyUnansweredQuestions();
}

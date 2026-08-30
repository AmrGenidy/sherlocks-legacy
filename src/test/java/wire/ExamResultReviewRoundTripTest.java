package wire;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.SerializationUtils;
import common.dto.ExamResultDTO;
import common.dto.ExamReviewItemDTO;
import java.util.List;
import org.junit.Test;

/**
 * The Final Exam review must survive the wire (gui-exam-review): {@link ExamReviewItemDTO} is
 * allowed by the {@code common.dto.} prefix, so an {@link ExamResultDTO} carrying review items
 * round-trips, and its transcript reads as filled sentences — never a raw id map.
 */
public class ExamResultReviewRoundTripTest {

  @Test
  public void reviewItemsRoundTripAndTranscriptIsReadable() throws Exception {
    ExamResultDTO dto =
        new ExamResultDTO(1, 2, "Good effort.", "Inspector", List.of(), null, false);
    dto.setReviewItems(
        List.of(
            new ExamReviewItemDTO(
                1, "Lord Blackwood was murdered by Colonel Hastings, motive revenge.", true),
            new ExamReviewItemDTO(2, "The cause of death was (no answer).", false)));

    Object back = SerializationUtils.deserialize(SerializationUtils.serialize(dto));
    assertTrue(back instanceof ExamResultDTO);
    ExamResultDTO r = (ExamResultDTO) back;

    assertEquals(2, r.getReviewItems().size());
    assertEquals(1, r.getReviewItems().get(0).getQuestionNumber());
    assertTrue(r.getReviewItems().get(0).isCorrect());
    assertEquals(
        "Lord Blackwood was murdered by Colonel Hastings, motive revenge.",
        r.getReviewItems().get(0).getFilledPrompt());
    assertFalse(r.getReviewItems().get(1).isCorrect());

    String transcript = r.toString();
    assertTrue(transcript.contains("Colonel Hastings"));
    assertFalse("transcript must not contain a raw id map", transcript.contains("{slot"));
  }
}

package JsonDTO;

import common.dto.FinalExamDTO;
import java.util.List;

/**
 * An interface representing the data for a single, playable case. This contract is implemented by
 * both the raw CaseFile DTO and the single-language LocalizedCaseFile adapter, allowing the game
 * engine to be agnostic about the source of the data.
 */
public interface CaseData {

  String getTitle();

  String getInvitation();

  String getDescription();

  String getWatsonImagePath(); // NEW

  /**
   * Dr. Watson's sprite scale from {@code metadata.watsonImageScale} (Case Maker placement tab).
   * Defaults to 1.0, which the engine treats as "size Watson to match the room's other suspects".
   */
  default double getWatsonImageScale() {
    return 1.0;
  }

  /** Watson's independent horizontal/vertical scale + mirror flags (default 1.0 / false). */
  default double getWatsonImageScaleX() {
    return 1.0;
  }

  default double getWatsonImageScaleY() {
    return 1.0;
  }

  default boolean isWatsonFlipX() {
    return false;
  }

  default boolean isWatsonFlipY() {
    return false;
  }

  /** Dr. Watson's clockwise sprite rotation in degrees (global; 0 = upright). */
  default double getWatsonRotation() {
    return 0.0;
  }

  /** Watson's authored name-label offset (fraction of sprite height); null = RoomView default. */
  default Double getWatsonLabelDX() {
    return null;
  }

  default Double getWatsonLabelDY() {
    return null;
  }

  String getStartingRoom();

  List<CaseFile.SuspectData> getSuspects();

  List<CaseFile.RoomData> getRooms();

  FinalExamDTO getFinalExam();

  List<String> getTasks();

  List<CaseFile.RankTierData> getRankingTiers();

  String getWinningMessage();

  Integer getStartingInsightTokens();

  List<CaseFile.CombineRule> getCombineLogic();

  java.util.Map<String, List<LocalizedCaseFile.LocalizedWatsonHint>> getStructuredWatsonHints();

  CaseFile.RedHerringMetadata getRedHerrings();

  String getLanguageCode();

  /**
   * The lead detective's display name — the player character. A single name across all languages;
   * defaults to "Sherlock Holmes" when the case does not author one, so existing cases are
   * unchanged. Surfaced in the Case File.
   */
  default String getDetectiveName() {
    return "Sherlock Holmes";
  }

  /**
   * The assistant's display name — the helper invoked via {@code ask}. A single name across all
   * languages; defaults to "Dr. Watson" when the case does not author one. Used as the dialogue
   * speaker and accepted as an {@code ask} target alongside the stable {@code watson} keyword.
   */
  default String getHelperName() {
    return "Dr. Watson";
  }

  LocalizedCaseFile.LocalizedCaseFileBlock getCaseFile();
}

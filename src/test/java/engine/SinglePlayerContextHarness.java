package engine;

import JsonDTO.LocalizedCaseFile;
import common.dto.ExamResultDTO;
import common.interfaces.GameActionContext;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.io.Serializable;
import java.util.List;

/** {@link ContextHarness} backed by the offline single-player context. */
public class SinglePlayerContextHarness extends ContextHarness {

  private final RecordingSinglePlayerContext context;
  private final String playerId;

  private SinglePlayerContextHarness(RecordingSinglePlayerContext context) {
    this.context = context;
    this.playerId = context.getPlayerDetective(null).getPlayerId();
  }

  /**
   * Loads the case through the real extraction pipeline and starts it, mirroring SinglePlayerMain.
   */
  public static SinglePlayerContextHarness start(LocalizedCaseFile caseFile) {
    SinglePlayerContextHarness h = load(caseFile);
    h.context.setCaseStarted(true);
    return h;
  }

  /** Loads the case but leaves it in the pre-'start case' state. */
  public static SinglePlayerContextHarness startUnstarted(LocalizedCaseFile caseFile) {
    return load(caseFile);
  }

  private static SinglePlayerContextHarness load(LocalizedCaseFile caseFile) {
    RecordingSinglePlayerContext context = new RecordingSinglePlayerContext();
    context.resetForNewCaseLoad();
    try {
      if (!BuildingExtractor.loadBuilding(caseFile, context)) {
        throw new IllegalStateException("BuildingExtractor failed for " + caseFile.getTitle());
      }
      GameObjectExtractor.loadObjects(caseFile, context);
      SuspectExtractor.loadSuspects(caseFile, context);
    } catch (SuspectExtractor.NoValidRoomsException e) {
      throw new IllegalStateException("Suspect placement failed for " + caseFile.getTitle(), e);
    }
    context.initializeNewCase(caseFile, caseFile.getStartingRoom());
    return new SinglePlayerContextHarness(context);
  }

  @Override
  public String label() {
    return "SinglePlayer";
  }

  @Override
  public GameActionContext context() {
    return context;
  }

  @Override
  public String playerId() {
    return playerId;
  }

  @Override
  public int tokens() {
    return context.getSharedInsightTokens();
  }

  /** Everything the engine emitted to the player, in order. */
  public List<Serializable> sent() {
    return context.sent();
  }

  @Override
  public List<Serializable> playerResponses() {
    return context.sent();
  }

  @Override
  public ExamResultDTO lastExamResult() {
    // The production context nulls getLastResultDTO() right after scoring, so read it from the
    // recorded responses instead (see RecordingSinglePlayerContext).
    ExamResultDTO last = null;
    for (Serializable dto : context.sent()) {
      if (dto instanceof ExamResultDTO result) {
        last = result;
      }
    }
    return last;
  }
}

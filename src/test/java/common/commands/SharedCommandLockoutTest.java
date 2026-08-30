package common.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import common.interfaces.GameActionContext;
import common.text.GameTexts;
import org.junit.Test;

/**
 * The previously-{@code super(false)} shared-state commands ({@link UpdateTaskStateCommand}, {@link
 * ContinueGameCommand}) must now route through the server-side exam/review lockout gate in {@link
 * BaseCommand#execute} (SECURITY_PLAN B/P2-4) — they used to bypass it.
 */
public class SharedCommandLockoutTest {

  private static GameActionContext startedContext() {
    GameActionContext ctx = mock(GameActionContext.class);
    when(ctx.isCaseStarted()).thenReturn(true);
    when(ctx.getGameTexts()).thenReturn(GameTexts.ENGLISH);
    return ctx;
  }

  @Test
  public void updateTaskStateIsRefusedDuringFinalExam() {
    GameActionContext ctx = startedContext();
    when(ctx.isExamActive()).thenReturn(true);

    UpdateTaskStateCommand cmd = new UpdateTaskStateCommand(0, true);
    cmd.setPlayerId("p1");
    cmd.execute(ctx);

    verify(ctx, never()).processUpdateTaskState(anyString(), anyInt(), anyBoolean());
    verify(ctx).sendResponseToPlayer(eq("p1"), any());
  }

  @Test
  public void updateTaskStateIsRefusedDuringReview() {
    GameActionContext ctx = startedContext();
    when(ctx.isExamActive()).thenReturn(false);
    when(ctx.isReviewMode()).thenReturn(true);

    UpdateTaskStateCommand cmd = new UpdateTaskStateCommand(1, false);
    cmd.setPlayerId("p1");
    cmd.execute(ctx);

    verify(ctx, never()).processUpdateTaskState(anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void updateTaskStateRunsInNormalPlay() {
    GameActionContext ctx = startedContext();
    when(ctx.isExamActive()).thenReturn(false);
    when(ctx.isReviewMode()).thenReturn(false);

    UpdateTaskStateCommand cmd = new UpdateTaskStateCommand(2, true);
    cmd.setPlayerId("p1");
    cmd.execute(ctx);

    verify(ctx).processUpdateTaskState("p1", 2, true);
  }

  @Test
  public void continueGameIsRefusedDuringFinalExam() {
    GameActionContext ctx = startedContext();
    when(ctx.isExamActive()).thenReturn(true);

    ContinueGameCommand cmd = new ContinueGameCommand();
    cmd.setPlayerId("p1");
    cmd.execute(ctx);

    verify(ctx, never()).processContinueGame(anyString());
    verify(ctx).sendResponseToPlayer(eq("p1"), any());
  }

  @Test
  public void continueGameRunsAfterTheExamConcludes() {
    GameActionContext ctx = startedContext();
    when(ctx.isExamActive()).thenReturn(false); // exam over
    when(ctx.isReviewMode()).thenReturn(false);

    ContinueGameCommand cmd = new ContinueGameCommand();
    cmd.setPlayerId("p1");
    cmd.execute(ctx);

    verify(ctx).processContinueGame("p1");
  }
}

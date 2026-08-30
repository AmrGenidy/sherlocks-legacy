package server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Per-player sliding-window limit on shared-pool actions in a multiplayer session (SECURITY_PLAN
 * B/P2-5).
 *
 * <p>Insight tokens, the session deduction count, and the task checklist are a single pool shared
 * by both detectives, with no per-player ownership. That is fine for cooperative play, but it means
 * one peer can grief the other by spamming {@code deduce} / task-toggle commands to drain shared
 * tokens or inflate the shared deduction penalty. This throttle caps such actions to {@link
 * #MAX_ACTIONS} per {@link #WINDOW_MILLIS} <em>per player</em> — generous enough that normal bursts
 * of legitimate play are never affected, tight enough that sustained spam is refused.
 *
 * <p>Clock-injectable (millis) for tests; guarded by the session lock at its single call site.
 */
final class SharedPoolThrottle {

  /** Shared-pool actions allowed per player within the window. */
  static final int MAX_ACTIONS = 30;

  static final long WINDOW_MILLIS = 10_000;

  private final int maxActions;
  private final long windowMillis;
  private final LongSupplier clockMillis;
  private final Map<String, Deque<Long>> actionsByPlayer = new HashMap<>();

  SharedPoolThrottle() {
    this(MAX_ACTIONS, WINDOW_MILLIS, System::currentTimeMillis);
  }

  SharedPoolThrottle(int maxActions, long windowMillis, LongSupplier clockMillis) {
    this.maxActions = maxActions;
    this.windowMillis = windowMillis;
    this.clockMillis = clockMillis;
  }

  /**
   * Records a shared-pool action for {@code playerId} and returns {@code true} if it is within the
   * per-player budget (the action may proceed), or {@code false} if the player has exceeded the
   * budget in the current window (the action should be refused).
   */
  boolean tryAcquire(String playerId) {
    long now = clockMillis.getAsLong();
    long cutoff = now - windowMillis;
    Deque<Long> stamps = actionsByPlayer.computeIfAbsent(playerId, k -> new ArrayDeque<>());
    while (!stamps.isEmpty() && stamps.peekFirst() < cutoff) {
      stamps.pollFirst();
    }
    if (stamps.size() >= maxActions) {
      return false;
    }
    stamps.addLast(now);
    return true;
  }
}

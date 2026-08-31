package engine;

import Core.Detective;
import java.util.List;

/**
 * Who is playing the case — the {@link GameEngine}'s second seam (ADR-0001). A single-player
 * session supplies one Detective; a multiplayer Game Session supplies the host/guest pair. The
 * engine itself never models a Host: host-only gating is a session-layer concern.
 */
public interface PlayerSet {

  /**
   * Resolves a player id to their Detective, or {@code null} if unknown. A solo set resolves every
   * id (including {@code null}) to its single Detective, matching the historical single-player
   * contract.
   */
  Detective detectiveFor(String playerId);

  /** All Detectives currently in the session, never {@code null} entries. */
  List<Detective> detectives();

  /** Player-facing display name for the given player id. */
  String displayName(String playerId);

  /** {@code true} when exactly one local player is playing (single-player session). */
  boolean isSolo();
}

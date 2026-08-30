package singleplayer;

import java.io.Serializable;

/**
 * Destination for the typed game-output events the single-player engine produces.
 *
 * <p>Historically {@link GameContextSinglePlayer} stringified each output DTO into marker-tagged
 * text and wrote it to {@code System.out}, where a {@code TextAreaOutputStream} re-captured it and
 * a regex {@code GameOutputParser} scraped it back into GUI calls. This sink is the seam that
 * replaces that round-trip: the context emits the raw DTO and the sink decides how to render it.
 *
 * <ul>
 *   <li>Terminal / offline play uses {@link ConsoleGameOutputSink}, which reproduces the original
 *       console text exactly.
 *   <li>The GUI can register a sink that consumes the DTOs directly — the same typed events the
 *       network client already handles — removing the stdout-scraping pipeline entirely.
 * </ul>
 *
 * <p>Single-player is fully in-process (Hard Constraint 1): a sink is a direct method call, never a
 * socket.
 */
@FunctionalInterface
public interface GameOutputSink {

  /**
   * Emits one game-output event to the player. Implementations decide how (or whether) to render a
   * given DTO type; emitting is pure output and must not mutate engine state.
   *
   * @param event the output DTO (never {@code null})
   */
  void emit(Serializable event);
}

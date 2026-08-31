package common;

/**
 * Hard upper bounds for everything that crosses the network boundary (security-pass issue 02).
 *
 * <p>Wire types enforce these in their {@code @JsonCreator} constructors, so an oversized field
 * fails deserialization at the framing layer (the frame is refused, the connection survives) and
 * can never reach the engine. The same constructors run for locally created commands, which keeps
 * single-player and multiplayer behaviour identical.
 */
public final class WireLimits {

  /** Server-inbound frame ceiling. Client-bound frames (case data) may be larger. */
  public static final int MAX_INBOUND_FRAME_BYTES = 64 * 1024;

  /**
   * Client-inbound frame ceiling (SECURITY_PLAN B/P2-2). Bounds what a (possibly malicious) host
   * can hand a client in a single frame. It cannot be as tight as {@link #MAX_INBOUND_FRAME_BYTES}
   * because the case-list DTO legitimately carries every case's browse data and, with Jackson
   * default-typing overhead, measures ~90 KB for a handful of cases and grows with the case count.
   * 1 MB leaves ample headroom (~40 cases) while cutting the old 10 MB ceiling by 10×.
   */
  public static final int MAX_CLIENT_INBOUND_FRAME_BYTES = 1024 * 1024;

  /** Object, suspect, direction and other in-world names typed by players. */
  public static final int MAX_NAME_LENGTH = 80;

  /** Journal/evidence/session identifiers and join codes. */
  public static final int MAX_ID_LENGTH = 120;

  public static final int MAX_CHAT_TEXT_LENGTH = 1000;

  /** Free-text content: journal notes, pinboard note bodies. */
  public static final int MAX_NOTE_TEXT_LENGTH = 4000;

  public static final int MAX_DISPLAY_NAME_LENGTH = 24;

  /** Avatar preset ids (filename stems like {@code char_suspect_03}); see {@link PlayerAvatars}. */
  public static final int MAX_AVATAR_ID_LENGTH = 40;

  public static final int MAX_CASE_TITLE_LENGTH = 200;

  public static final int MAX_LANGUAGE_CODE_LENGTH = 10;

  /** Final-exam answer maps: at most this many slots, each value at most the text length below. */
  public static final int MAX_EXAM_ANSWER_ENTRIES = 32;

  public static final int MAX_EXAM_ANSWER_TEXT_LENGTH = 300;

  // Shared-state growth caps (enforced server-side where the state lives).
  public static final int MAX_PINBOARD_ITEMS = 200;
  public static final int MAX_PINBOARD_LINKS = 400;
  public static final int MAX_PINBOARD_TEMPLATE_ENTRIES = 64;
  public static final int MAX_JOURNAL_ENTRIES = 2000;

  /** Pinboard geometry must be finite and within this magnitude (defends client renderers). */
  public static final double MAX_PINBOARD_COORD = 100_000.0;

  private WireLimits() {}

  /**
   * Returns {@code value} unchanged when it fits in {@code max} characters; throws otherwise. Null
   * is allowed — null-ness is each field's own contract.
   */
  public static String requireLength(String value, int max, String field) {
    if (value != null && value.length() > max) {
      throw new IllegalArgumentException(
          field
              + " exceeds the maximum length of "
              + max
              + " characters (was "
              + value.length()
              + ").");
    }
    return value;
  }
}

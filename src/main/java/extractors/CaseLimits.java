package extractors;

/**
 * Hard budgets for an imported/shared case file (SECURITY_PLAN A/P0-2).
 *
 * <p>Cases are untrusted third-party data (players import and share them) and are parsed/validated
 * automatically at load time. Without limits, a well-formed-but-hostile case can exhaust memory —
 * either by sheer file size, by pathological parser input (deep nesting, gigantic strings), or by a
 * huge number of small elements (rooms/objects/…). These constants bound each of those vectors:
 *
 * <ul>
 *   <li>{@link #MAX_CASE_FILE_BYTES} caps the raw JSON before it is read.
 *   <li>The {@code MAX_JSON_*} values feed Jackson {@code StreamReadConstraints} so abusive JSON is
 *       rejected during parsing, before a {@code CaseFile} is ever built.
 *   <li>The structural caps are enforced by {@link CaseValidator} as ERROR-level issues, so an
 *       over-budget case is refused exactly like any other invalid case (logged, never offered).
 * </ul>
 *
 * <p>Values are intentionally generous-but-finite — far above any real authored case — and are
 * exposed as public constants so they can be tuned in one place.
 */
public final class CaseLimits {

  /** Maximum raw size of a single case JSON file. */
  public static final int MAX_CASE_FILE_BYTES = 2 * 1024 * 1024; // 2 MB

  // ---- Parser-level (Jackson StreamReadConstraints) ----

  /** Longest single JSON string value the parser will accept. */
  public static final int MAX_JSON_STRING_LENGTH = 1_000_000; // 1 MB

  /** Deepest JSON nesting (objects/arrays) the parser will accept. */
  public static final int MAX_JSON_NESTING_DEPTH = 64;

  /** Longest numeric token the parser will accept. */
  public static final int MAX_JSON_NUMBER_LENGTH = 1_000;

  // ---- Structural caps (enforced in CaseValidator as ERRORs) ----

  public static final int MAX_ROOMS = 300;
  public static final int MAX_OBJECTS_PER_ROOM = 100;
  public static final int MAX_TOTAL_OBJECTS = 3_000;
  public static final int MAX_SUSPECTS_PER_LANGUAGE = 100;
  public static final int MAX_EXAM_QUESTIONS = 100;
  public static final int MAX_CHOICES_PER_SLOT = 64;
  public static final int MAX_WATSON_HINTS = 2_000;

  /** Cap for short author-supplied strings: names, ids, titles. */
  public static final int MAX_NAME_LENGTH = 300;

  /** Cap for long-form author-supplied strings: descriptions, statements, invitations, messages. */
  public static final int MAX_TEXT_LENGTH = 20_000;

  private CaseLimits() {}
}

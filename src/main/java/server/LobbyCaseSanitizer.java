package server;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Strips a case down to what a browsing client legitimately needs (SECURITY_PLAN D).
 *
 * <p>The lobby "Host a game" list ({@code AvailableCasesDTO}) used to ship the complete
 * multilingual {@link CaseFile} to every connected client — including the final-exam {@code
 * correct_combination} (the answer key), the suspect truth-states + contradiction reward targets,
 * and combine reward targets. Since the host/engine scores every answer server-side, clients never
 * need any of that; it was a pure solution leak.
 *
 * <p>This sanitizer returns independent, browse-safe copies with all answer-key / reward content
 * removed, keeping only what the case-selection UI reads (universal title, author/metadata, and
 * each language's name, title, invitation and description). The full case — with answers — never
 * leaves the server: hosting resolves the authoritative {@link CaseFile} by title on the server
 * side.
 */
public final class LobbyCaseSanitizer {

  // Plain mapper (no default typing) used purely to deep-copy a case so stripping the copy never
  // mutates the server's authoritative loaded case objects.
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private LobbyCaseSanitizer() {}

  /** Returns browse-safe copies of {@code cases} with all solution content stripped. */
  public static List<CaseFile> forBrowsing(List<CaseFile> cases) {
    List<CaseFile> out = new ArrayList<>();
    if (cases == null) {
      return out;
    }
    for (CaseFile source : cases) {
      CaseFile safe = stripped(source);
      if (safe != null) {
        out.add(safe);
      }
    }
    return out;
  }

  /**
   * Deep-copies {@code source} and removes every answer-key / reward field. Returns null (omitting
   * the case from the browse list) if the copy cannot be made — never the original, so a
   * serialization hiccup can't leak the unsanitized case.
   */
  static CaseFile stripped(CaseFile source) {
    if (source == null) {
      return null;
    }
    CaseFile copy;
    try {
      copy = MAPPER.readValue(MAPPER.writeValueAsBytes(source), CaseFile.class);
    } catch (IOException e) {
      return null;
    }

    // Don't leak the case's absolute path on the server's filesystem.
    copy.setSourcePath(null);

    if (copy.getLocalizations() != null) {
      for (CaseFile.LocalizedData loc : copy.getLocalizations().values()) {
        if (loc == null) {
          continue;
        }
        // final_exam holds correct_combination (the exam answer key); suspects hold truth-states
        // and
        // contradiction reward targets. Neither is read by the case browser.
        loc.finalExam = null;
        loc.suspects = null;
      }
    }
    // combine_logic holds the combine reward targets (result deduction ids).
    if (copy.getCombineLogic() != null) {
      copy.getCombineLogic().clear();
    }
    return copy;
  }
}

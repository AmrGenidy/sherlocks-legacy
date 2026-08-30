package ui.i18n;

/**
 * Display titles for Cases (.scratch/ui-localization follow-up): wherever a case is LISTED or
 * SHOWN, prefer the localization title matching the current UI language; fall back to {@code
 * universalTitle}. The universal title remains the internal key everywhere — CaseLoader override
 * semantics, selection wiring, and session protocol are untouched; this is presentation only.
 */
public final class CaseTitles {

  private CaseTitles() {}

  /** The title to display for {@code caseFile} in the current UI language. */
  public static String displayTitle(JsonDTO.CaseFile caseFile) {
    if (caseFile == null) {
      return "";
    }
    if (caseFile.getLocalizations() != null) {
      JsonDTO.CaseFile.LocalizedData localization =
          caseFile.getLocalizations().get(L10n.language());
      if (localization != null
          && localization.getTitle() != null
          && !localization.getTitle().isBlank()) {
        return localization.getTitle();
      }
    }
    return caseFile.getUniversalTitle();
  }
}

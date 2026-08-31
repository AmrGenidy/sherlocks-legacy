package ui.i18n;

import common.dto.DialogueEventDTO;
import common.dto.DialogueType;

/**
 * Client-side localization seam for engine-generated dialogue popups. The engine is
 * language-agnostic, so it may tag a dialogue with a {@code titleKey} and/or {@code textKey} (plus
 * MessageFormat {@code args}) instead of emitting raw English; this helper resolves those to the
 * player's UI language via {@link L10n}, keeping {@link DialogueEventDTO#getTitle()}/{@code
 * getText()} as English fallbacks. A dialogue with no keys — authored, already-localized content —
 * passes through unchanged.
 *
 * <p>Spoken lines (Watson and suspect statements) are quoted; the property values are stored
 * unquoted, so a resolved key is wrapped in quotation marks for those types (the English fallback is
 * already quoted by the emitter). Applied at both client seams — single-player {@code
 * GuiGameOutputSink} and multiplayer {@code GameClient} — so the bubble and the transcript both read
 * localized text.
 */
public final class WatsonDialogue {

  private WatsonDialogue() {}

  public static DialogueEventDTO localize(DialogueEventDTO event) {
    if (event == null || (event.getTextKey() == null && event.getTitleKey() == null)) {
      return event;
    }
    Object[] args = event.getArgs() == null ? new Object[0] : event.getArgs().toArray();

    String title = event.getTitle();
    if (event.getTitleKey() != null) {
      title = L10n.tOr(event.getTitleKey(), event.getTitle(), args);
    }

    String body = event.getText();
    if (event.getTextKey() != null) {
      String resolved = L10n.tOrNull(event.getTextKey(), args);
      if (resolved != null) {
        boolean spoken =
            event.getType() == DialogueType.WATSON || event.getType() == DialogueType.SUSPECT;
        body = spoken ? "\"" + resolved + "\"" : resolved;
      }
      // else: keep the English fallback text (already quoted for spoken lines by the emitter).
    }

    return new DialogueEventDTO(
        title, body, event.getType(), null, null, null, event.getTimestamp());
  }
}

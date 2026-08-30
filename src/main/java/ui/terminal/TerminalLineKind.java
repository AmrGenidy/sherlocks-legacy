package ui.terminal;

import common.dto.TextMessage;
import java.io.Serializable;

/**
 * The visual category of a single terminal transcript line, used to fill it a distinct ink on the
 * sunken-well terminal (.scratch/ingame-terminal-polish DEC-3/4). A {@link TerminalView} {@code
 * Text} node carries {@link #cssClass()} so the colour lives in {@code detective-theme.css}, never
 * inline.
 *
 * <p>Classification is <b>pure and client-side</b>: {@link #of(Serializable)} maps the typed output
 * DTO the engine already emits (via {@code singleplayer.GameOutputSink}) to a kind — no string
 * heuristics, no wire-protocol change (Hard Constraint 2). It is unit-tested without the FX toolkit,
 * exactly like {@code SuggestionStripModel} / {@code CompletionEngine}.
 *
 * <p>{@link #SUCCESS} and {@link #CONTRADICTION} are wired in the enum and the stylesheet but are
 * <b>reserved</b> (DEC-4): the engine conveys "Contradiction successful!" and deduction
 * confirmations as ordinary {@link TextMessage}s with {@code isError=false}, so separating them from
 * a normal line would need either localized-substring sniffing (breaks in ar/ru) or a typed {@code
 * kind} on the wire (a protocol change). {@link #of(Serializable)} therefore never guesses them.
 */
public enum TerminalLineKind {
  /** Default body text — the detective's written record. Ink (light) / lamp-lit ochre (dark). */
  NORMAL("terminal-line--normal"),
  /** Reserved (.scratch/terminal-default-colour): petrol. No longer applied to narrative text. */
  SYSTEM("terminal-line--system"),
  /** Reserved: a character speaking (Watson, suspects). Brass. Not auto-applied — dialogue shows as a
   * bubble and reads as the default ink in the transcript. */
  DIALOGUE("terminal-line--dialogue"),
  /** An error or rejected command. Oxblood / ember. */
  ERROR("terminal-line--error"),
  /** Reserved (DEC-4): a confirmed deduction / success. Moss. */
  SUCCESS("terminal-line--success"),
  /** Reserved (DEC-4): a confirmed contradiction. Oxblood (shares the alert ink, DESIGN.md §2). */
  CONTRADICTION("terminal-line--contradiction");

  private final String cssClass;

  TerminalLineKind(String cssClass) {
    this.cssClass = cssClass;
  }

  /** The {@code .terminal-line--*} style class that fills this line's ink in the stylesheet. */
  public String cssClass() {
    return cssClass;
  }

  /**
   * Classifies a typed output DTO into a line kind for the terminal. Robust and i18n-safe — it keys
   * off the DTO type and the existing {@link TextMessage#isError()} flag only, never the (localized)
   * text content.
   *
   * <p>Ordinary narrative — room descriptions (objects/occupants/exits), journal notices, exam
   * blocks, dialogue, and plain messages — is the DEFAULT body ink ({@link #NORMAL}); colour is
   * reserved for meaningful outcomes only. Today only {@link #ERROR} (oxblood) is auto-classified;
   * {@link #SUCCESS} (moss) and {@link #CONTRADICTION} need a typed engine signal (DEC-4). Petrol
   * ({@link #SYSTEM}) is deliberately NOT used for narrative (.scratch/terminal-default-colour).
   *
   * @param event the output DTO (may be {@code null})
   * @return {@link #ERROR} for an error {@link TextMessage}, else {@link #NORMAL}
   */
  public static TerminalLineKind of(Serializable event) {
    if (event instanceof TextMessage message && message.isError()) {
      return ERROR;
    }
    return NORMAL;
  }
}

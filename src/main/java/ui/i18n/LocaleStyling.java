package ui.i18n;

import javafx.scene.Node;

/**
 * Per-window language style-class helper (.scratch/ui-localization). The layout is always
 * left-to-right in every UI language (deliberate — no RTL mirroring); what changes per language is
 * the typeface: CSS rules keyed on {@code .lang-<code>} select a face that covers the script (Amiri
 * for Arabic, PT Serif for Russian/Cyrillic, the DESIGN.md §3 faces for English).
 *
 * <p>Sub-windows and dialogs are separate Stages, so the shell's root class does not reach them;
 * call {@link #apply} on each window's scene root (or dialog pane) at creation. The shell disposes
 * and rebuilds sub-windows on language switch, so creation-time application is sufficient.
 */
public final class LocaleStyling {

  private LocaleStyling() {}

  /** Tags the node with the active {@code lang-<code>} style class (replacing any previous one). */
  public static void apply(Node sceneRoot) {
    sceneRoot.getStyleClass().removeIf(styleClass -> styleClass.startsWith("lang-"));
    sceneRoot.getStyleClass().add("lang-" + L10n.language());
  }
}

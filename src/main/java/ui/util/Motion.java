package ui.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Paper-like motion vocabulary (DESIGN.md §6, MENU_DESIGN "Motion"): gentle, ease-in-out,
 * 180–240ms, <b>no bounce, no overshoot</b>. Transitions evoke a turning page or ink settling,
 * never a "pop". <b>Every timing value lives here as a constant</b> so the whole feel can be
 * retuned from one place (.scratch/ui-immersion issue 04).
 */
public final class Motion {

  /** Screen-to-screen page-settle (cross-fade + small directional slide). Top of the §6 band. */
  public static final Duration SCREEN = Duration.millis(220);

  /** Smaller in-screen elements (journal entries, fades). */
  public static final Duration ELEMENT = Duration.millis(180);

  /** Dialogue bubbles / journal cards settling in. */
  public static final Duration DIALOGUE = Duration.millis(180);

  /** Button hover-lift / press settle — quick but still eased, never a snap (MENU_DESIGN). */
  public static final Duration HOVER = Duration.millis(120);

  /**
   * A new terminal transcript line settling in — fast (under the 120ms cap) so output stays snappy,
   * eased and fade-only (no translate) so the line never shifts the layout (.scratch/
   * ingame-terminal-polish DEC-6).
   */
  public static final Duration TERMINAL_LINE = Duration.millis(110);

  /** The one-time title/emblem entrance on first load — a slow, gentle "ink drying" settle. */
  public static final Duration INK_SETTLE = Duration.millis(640);

  /** One half-cycle of the ambient lamplight flicker (autoreversed → ~2× this per full breath). */
  public static final Duration FLICKER = Duration.millis(1700);

  /** The small translate distance (px) for a "page settling" feel — subtle, never a slide. */
  public static final double SETTLE_TRANSLATE = 12;

  /** How far (px) a plate rises on hover/focus — a hair of lift, the border is still the line. */
  public static final double HOVER_LIFT = 2;

  /** How far (px) a plate settles down when pressed. */
  public static final double PRESS_DROP = 1;

  /** How far (px) the title/emblem rises as it settles in on first load. */
  public static final double INK_RISE = 14;

  /**
   * The dimmest the lamplight glow falls to in the ambient flicker (1.0 = full). Kept high so the
   * flicker is barely perceptible and can never affect text contrast (DESIGN.md §6).
   */
  public static final double FLICKER_MIN_OPACITY = 0.82;

  /** Shared easing — ease-in-out, no overshoot (a spline/bounce would violate §6). */
  public static final Interpolator EASE = Interpolator.EASE_BOTH;

  /** Marks a node whose hover/press/focus motion is already wired, so it is never double-wired. */
  private static final Object PLATE_MOTION_KEY = new Object();

  private Motion() {}

  /** Gentle fade from transparent to opaque over {@code duration}. */
  public static FadeTransition fadeIn(Node node, Duration duration) {
    FadeTransition fade = new FadeTransition(duration, node);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    fade.setInterpolator(EASE);
    return fade;
  }

  /**
   * Fade in while a small upward translate settles to zero — ink settling onto the page. Used for
   * dialogue bubbles and journal entries.
   */
  public static void settleIn(Node node) {
    node.setOpacity(0.0);
    node.setTranslateY(SETTLE_TRANSLATE);
    FadeTransition fade = fadeIn(node, DIALOGUE);
    TranslateTransition slide = new TranslateTransition(DIALOGUE, node);
    slide.setFromY(SETTLE_TRANSLATE);
    slide.setToY(0.0);
    slide.setInterpolator(EASE);
    ParallelTransition together = new ParallelTransition(fade, slide);
    together.setOnFinished(
        e -> {
          node.setOpacity(1.0);
          node.setTranslateY(0.0);
        });
    together.play();
  }

  /**
   * Replace the single child of {@code host} with {@code incoming} as a page settling: the outgoing
   * node fades out, then the incoming node fades in while a small horizontal translate settles to
   * zero. {@code onShown} runs once the incoming node is mounted (after the fade-out), mirroring
   * the previous cross-fade's ordering.
   */
  public static void crossFadeReplace(Pane host, Node outgoing, Node incoming, Runnable onShown) {
    if (outgoing == null) {
      host.getChildren().setAll(incoming);
      if (onShown != null) {
        onShown.run();
      }
      return;
    }
    FadeTransition fadeOut = new FadeTransition(SCREEN, outgoing);
    fadeOut.setFromValue(1.0);
    fadeOut.setToValue(0.0);
    fadeOut.setInterpolator(EASE);
    fadeOut.setOnFinished(
        event -> {
          host.getChildren().setAll(incoming);
          if (onShown != null) {
            onShown.run();
          }
          incoming.setOpacity(0.0);
          incoming.setTranslateX(SETTLE_TRANSLATE);
          FadeTransition fadeIn = fadeIn(incoming, SCREEN);
          TranslateTransition slide = new TranslateTransition(SCREEN, incoming);
          slide.setFromX(SETTLE_TRANSLATE);
          slide.setToX(0.0);
          slide.setInterpolator(EASE);
          ParallelTransition together = new ParallelTransition(fadeIn, slide);
          together.setOnFinished(
              e -> {
                incoming.setOpacity(1.0);
                incoming.setTranslateX(0.0);
              });
          together.play();
        });
    fadeOut.play();
  }

  /**
   * A page-turn entrance for a freshly mounted full-window screen: fade in while a small
   * directional slide settles to zero, so the new page reads as turning into view (MENU_DESIGN
   * "page-turn"). {@code direction} is the sign of the slide (+1 settles in from the right, −1 from
   * the left); pass the direction of travel through the menu so going deeper and stepping back feel
   * opposite. The outgoing node has already been swapped out by the caller.
   */
  public static void pageTurnIn(Node incoming, double direction) {
    double from = direction * SETTLE_TRANSLATE;
    incoming.setOpacity(0.0);
    incoming.setTranslateX(from);
    FadeTransition fade = fadeIn(incoming, SCREEN);
    TranslateTransition slide = new TranslateTransition(SCREEN, incoming);
    slide.setFromX(from);
    slide.setToX(0.0);
    slide.setInterpolator(EASE);
    ParallelTransition together = new ParallelTransition(fade, slide);
    together.setOnFinished(
        e -> {
          incoming.setOpacity(1.0);
          incoming.setTranslateX(0.0);
        });
    together.play();
  }

  /**
   * A page-turn between the current top child of {@code host} and {@code incoming}: the outgoing
   * child fades out while the incoming one fades in with a small directional slide — the two
   * overlap so it reads as a single turning page (MENU_DESIGN). Used for the menu's sub-state swaps
   * (main ↔ tutorials ↔ case selection) where the screen view itself never changes. When {@code
   * host} is empty there is nothing to turn from, so the incoming child is just mounted (its own
   * outer mount carries the entrance).
   */
  public static void pageTurn(Pane host, Node incoming, double direction) {
    Node outgoing =
        host.getChildren().isEmpty() ? null : host.getChildren().get(host.getChildren().size() - 1);
    if (outgoing == null) {
      host.getChildren().setAll(incoming);
      return;
    }
    double from = direction * SETTLE_TRANSLATE;
    incoming.setOpacity(0.0);
    incoming.setTranslateX(from);
    host.getChildren().add(incoming); // overlaps the outgoing child during the turn

    FadeTransition in = fadeIn(incoming, SCREEN);
    TranslateTransition slide = new TranslateTransition(SCREEN, incoming);
    slide.setFromX(from);
    slide.setToX(0.0);
    slide.setInterpolator(EASE);
    ParallelTransition settle = new ParallelTransition(in, slide);
    settle.setOnFinished(
        e -> {
          incoming.setOpacity(1.0);
          incoming.setTranslateX(0.0);
        });

    FadeTransition out = new FadeTransition(SCREEN, outgoing);
    out.setFromValue(1.0);
    out.setToValue(0.0);
    out.setInterpolator(EASE);
    out.setOnFinished(
        e -> {
          host.getChildren().remove(outgoing);
          outgoing.setOpacity(1.0); // restore in case the node is reused
        });

    out.play();
    settle.play();
  }

  /**
   * The one-time title/emblem entrance (MENU_DESIGN): a single gentle fade-in while a small upward
   * translate settles to zero — like ink drying onto the page. Slower than a normal element fade so
   * it reads as a deliberate first-load flourish, played once.
   */
  public static void inkSettle(Node node) {
    node.setOpacity(0.0);
    node.setTranslateY(INK_RISE);
    FadeTransition fade = new FadeTransition(INK_SETTLE, node);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    fade.setInterpolator(EASE);
    TranslateTransition rise = new TranslateTransition(INK_SETTLE, node);
    rise.setFromY(INK_RISE);
    rise.setToY(0.0);
    rise.setInterpolator(EASE);
    ParallelTransition together = new ParallelTransition(fade, rise);
    together.setOnFinished(
        e -> {
          node.setOpacity(1.0);
          node.setTranslateY(0.0);
        });
    together.play();
  }

  /**
   * A barely-perceptible candle flicker on a lamplight glow node (DESIGN.md §6 ambient motion): its
   * opacity breathes gently between {@link #FLICKER_MIN_OPACITY} and full, ease-in-out and
   * autoreversed, forever. Drive a node that carries <b>only</b> the glow (never text), so the
   * flicker can never affect contrast. Returns the running {@link Timeline} so the caller can stop
   * it when the screen is torn down.
   */
  public static Timeline candleFlicker(Node glow) {
    Timeline flicker =
        new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.opacityProperty(), 1.0, EASE)),
            new KeyFrame(FLICKER, new KeyValue(glow.opacityProperty(), FLICKER_MIN_OPACITY, EASE)));
    flicker.setAutoReverse(true);
    flicker.setCycleCount(Timeline.INDEFINITE);
    flicker.play();
    return flicker;
  }

  /**
   * Wires gentle hover-lift / press / focus motion onto an engraved plate (MENU_DESIGN buttons):
   * the plate rises a hair on hover or keyboard focus and settles down when pressed, all eased over
   * {@link #HOVER} so the ochre focus ring and lift fade in rather than snap. Colour and the focus
   * ring itself stay in CSS; this owns only the {@code translateY}. Idempotent — a plate is only
   * wired once.
   */
  public static void installPlateMotion(Region plate) {
    if (plate.getProperties().putIfAbsent(PLATE_MOTION_KEY, Boolean.TRUE) != null) {
      return;
    }
    TranslateTransition move = new TranslateTransition(HOVER, plate);
    move.setInterpolator(EASE);
    Runnable settle =
        () -> {
          double target =
              plate.isPressed()
                  ? PRESS_DROP
                  : (plate.isHover() || plate.isFocused()) ? -HOVER_LIFT : 0.0;
          if (plate.getTranslateY() == target) {
            return;
          }
          move.stop();
          move.setToY(target);
          move.playFromStart();
        };
    plate.hoverProperty().addListener((o, a, b) -> settle.run());
    plate.pressedProperty().addListener((o, a, b) -> settle.run());
    plate.focusedProperty().addListener((o, a, b) -> settle.run());
  }

  /**
   * Wires {@link #installPlateMotion} onto every engraved plate under {@code root} — the menu
   * plates, the demoted icon buttons and the tutorial cards — in one call, so a screen need only
   * hand its built page over. Safe to call repeatedly (each plate is wired at most once) and after
   * CSS has resolved, so the style-class lookups match.
   */
  public static void animatePlates(Node root) {
    for (String selector : new String[] {".menu-plate", ".menu-icon-button", ".tutorial-card"}) {
      for (Node node : root.lookupAll(selector)) {
        if (node instanceof Region) {
          installPlateMotion((Region) node);
        }
      }
    }
  }
}

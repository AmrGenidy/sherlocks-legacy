package ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * The local app settings model + best-effort store, focused on the two independent reading/terminal
 * {@code textScale} multipliers (.scratch/gui-typography-readability): each clamps to the slider
 * range, defaults to 1.0, round-trips through the JSON store independently, survives an
 * unknown/stray field, and a pre-split file's single {@code textScale} migrates into BOTH. Pure —
 * no FX.
 */
public class AppSettingsTest {

  private static final double EPS = 1e-9;

  @Test
  public void defaultsAreOneForBothScales() {
    assertEquals(1.0, AppSettings.defaults().terminalTextScale(), EPS);
    assertEquals(1.0, AppSettings.defaults().readingTextScale(), EPS);
  }

  @Test
  public void eachScaleIsClampedToSliderRange() {
    AppSettings s = new AppSettings(null, "light", 0.2, 9.0);
    assertEquals(AppSettings.MIN_TEXT_SCALE, s.terminalTextScale(), EPS);
    assertEquals(AppSettings.MAX_TEXT_SCALE, s.readingTextScale(), EPS);
  }

  @Test
  public void nonFiniteScaleFallsBackToDefault() {
    AppSettings s = new AppSettings(null, "light", Double.NaN, Double.POSITIVE_INFINITY);
    assertEquals(1.0, s.terminalTextScale(), EPS);
    assertEquals(1.0, s.readingTextScale(), EPS);
  }

  @Test
  public void anInBetweenScaleSnapsToTheNearestDiscreteStep() {
    // The sliders are notch-based; a stored value must round to a 0.2 step, never an in-between
    // value.
    assertEquals(1.2, new AppSettings(null, "light", 1.13, 1.0).terminalTextScale(), EPS);
    assertEquals(1.2, new AppSettings(null, "light", 1.0, 1.16).readingTextScale(), EPS);
    assertEquals(1.0, new AppSettings(null, "light", 0.94, 1.0).terminalTextScale(), EPS);
    // A pre-widening stored value (old 0.1-step notches) lands on the nearest new notch.
    assertEquals(1.4, new AppSettings(null, "light", 1.3, 1.3).readingTextScale(), EPS);
    // Exact steps are unchanged.
    assertEquals(1.4, new AppSettings(null, "light", 1.4, 1.4).readingTextScale(), EPS);
  }

  @Test
  public void withMethodsAreIndependentNonMutatingAndClamp() {
    AppSettings base = AppSettings.defaults();

    AppSettings term = base.withTerminalTextScale(1.4);
    assertEquals(1.4, term.terminalTextScale(), EPS);
    assertEquals("reading scale must be untouched", 1.0, term.readingTextScale(), EPS);
    assertEquals("original unchanged", 1.0, base.terminalTextScale(), EPS);

    AppSettings read = base.withReadingTextScale(0.8);
    assertEquals(0.8, read.readingTextScale(), EPS);
    assertEquals("terminal scale must be untouched", 1.0, read.terminalTextScale(), EPS);
    assertEquals(
        AppSettings.MAX_TEXT_SCALE, base.withReadingTextScale(99.0).readingTextScale(), EPS);
  }

  @Test
  public void bothScalesRoundTripIndependentlyThroughTheStore() throws Exception {
    Path file = Files.createTempFile("app-settings-twoscale", ".json");
    Files.deleteIfExists(file);
    AppSettingsStore store = new AppSettingsStore(file);

    store.save(new AppSettings("ru", "dark", 1.4, 0.8));
    AppSettings back = store.load();

    assertEquals(1.4, back.terminalTextScale(), EPS);
    assertEquals(0.8, back.readingTextScale(), EPS);
    assertEquals("ru", back.language());
    assertEquals(AppSettings.DARK, back.theme());
  }

  @Test
  public void aStrayUnknownFieldDoesNotWipeTheStoredScales() throws Exception {
    Path file = Files.createTempFile("app-settings-stray", ".json");
    Files.writeString(
        file,
        "{\"language\":\"en\",\"theme\":\"dark\",\"terminalTextScale\":1.4,"
            + "\"readingTextScale\":1.2,\"someFutureField\":\"x\"}");

    AppSettings back = new AppSettingsStore(file).load();

    assertEquals(1.4, back.terminalTextScale(), EPS);
    assertEquals(1.2, back.readingTextScale(), EPS);
    assertEquals(AppSettings.DARK, back.theme());
    assertNotEquals(1.0, back.terminalTextScale(), EPS);
  }

  @Test
  public void aLegacySingleTextScaleMigratesIntoBothScales() throws Exception {
    Path file = Files.createTempFile("app-settings-legacy", ".json");
    // A pre-split file carries only the old single "textScale".
    Files.writeString(file, "{\"language\":\"en\",\"theme\":\"light\",\"textScale\":1.2}");

    AppSettings back = new AppSettingsStore(file).load();

    assertEquals("legacy scale seeds the terminal scale", 1.2, back.terminalTextScale(), EPS);
    assertEquals("legacy scale seeds the reading scale", 1.2, back.readingTextScale(), EPS);
  }

  @Test
  public void sliderRangeIsSane() {
    assertTrue(AppSettings.MIN_TEXT_SCALE < 1.0);
    assertTrue(AppSettings.MAX_TEXT_SCALE > 1.0);
  }
}

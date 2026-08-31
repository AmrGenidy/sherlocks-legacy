package ui.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Audio settings model: volume is clamped to [0,1], mute zeroes the effective volume without losing
 * the stored level, and the with* methods are non-mutating. Pure — no FX/media toolkit.
 */
public class AudioSettingsTest {

  private static final double EPS = 1e-9;

  @Test
  public void defaultsAreSaneAndUnmuted() {
    AudioSettings d = AudioSettings.defaults();
    assertEquals(AudioSettings.DEFAULT_VOLUME, d.volume(), EPS);
    assertFalse(d.muted());
  }

  @Test
  public void volumeIsClampedToUnitRange() {
    assertEquals(0.0, new AudioSettings(-3.0, false).volume(), EPS);
    assertEquals(1.0, new AudioSettings(4.2, false).volume(), EPS);
    assertEquals(0.5, new AudioSettings(0.5, false).volume(), EPS);
  }

  @Test
  public void nonFiniteVolumeFallsBackToDefault() {
    assertEquals(AudioSettings.DEFAULT_VOLUME, new AudioSettings(Double.NaN, false).volume(), EPS);
  }

  @Test
  public void effectiveVolumeIsZeroWhileMutedButLevelIsRetained() {
    AudioSettings muted = new AudioSettings(0.8, true);
    assertEquals(0.0, muted.effectiveVolume(), EPS);
    assertEquals(0.8, muted.volume(), EPS); // the level is remembered for unmute
  }

  @Test
  public void effectiveVolumeEqualsVolumeWhenUnmuted() {
    assertEquals(0.8, new AudioSettings(0.8, false).effectiveVolume(), EPS);
  }

  @Test
  public void withMethodsAreNonMutatingAndClamp() {
    AudioSettings base = new AudioSettings(0.5, false);

    AudioSettings louder = base.withVolume(2.0);
    assertEquals(1.0, louder.volume(), EPS);
    assertEquals(0.5, base.volume(), EPS); // original unchanged

    AudioSettings muted = base.withMuted(true);
    assertTrue(muted.muted());
    assertFalse(base.muted());
    assertEquals(0.5, muted.volume(), EPS); // volume preserved through mute
  }
}

package ui.audio;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable master-audio settings (.scratch/per-case-soundtrack issue 03): a {@code volume} clamped
 * to {@code [0,1]} and a {@code muted} flag. {@link #effectiveVolume()} is what the player actually
 * uses — zero while muted, without discarding the stored level so unmuting restores it.
 *
 * <p>Pure value type, persisted by {@link AudioSettingsStore}; no FX/media dependency.
 */
public final class AudioSettings {

  /** A comfortable default for ambient backing tracks. */
  public static final double DEFAULT_VOLUME = 0.6;

  private final double volume;
  private final boolean muted;

  @JsonCreator
  public AudioSettings(
      @JsonProperty("volume") double volume, @JsonProperty("muted") boolean muted) {
    this.volume = clamp(volume);
    this.muted = muted;
  }

  public static AudioSettings defaults() {
    return new AudioSettings(DEFAULT_VOLUME, false);
  }

  @JsonProperty("volume")
  public double volume() {
    return volume;
  }

  @JsonProperty("muted")
  public boolean muted() {
    return muted;
  }

  /** The volume applied to the running track: zero while muted. */
  public double effectiveVolume() {
    return muted ? 0.0 : volume;
  }

  public AudioSettings withVolume(double newVolume) {
    return new AudioSettings(newVolume, muted);
  }

  public AudioSettings withMuted(boolean newMuted) {
    return new AudioSettings(volume, newMuted);
  }

  /** Clamp to the unit range; a non-finite value falls back to the default. */
  private static double clamp(double v) {
    if (!Double.isFinite(v)) {
      return DEFAULT_VOLUME;
    }
    if (v < 0.0) {
      return 0.0;
    }
    if (v > 1.0) {
      return 1.0;
    }
    return v;
  }
}

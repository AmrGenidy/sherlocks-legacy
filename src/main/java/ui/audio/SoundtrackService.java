package ui.audio;

import JsonDTO.CaseFile;
import extractors.ResourceResolver;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-case ambient soundtrack (.scratch/per-case-soundtrack): looped, client-side playback of the
 * optional {@code metadata.soundtrack} declared by a case, with a master volume + mute persisted
 * via {@link AudioSettingsStore}.
 *
 * <p>The resolution + fallback decision is the pure, unit-tested seam ({@link
 * #resolveSoundtrack(CaseFile)} — no FX/media); {@link #playForCase(CaseFile)} is the thin {@link
 * MediaPlayer} glue around it. Everything degrades silently: an absent/blank/unresolvable path, or
 * a media engine that cannot open the file, leaves the game quiet and never throws.
 */
public class SoundtrackService {

  private static final Logger logger = LoggerFactory.getLogger(SoundtrackService.class);

  private final AudioSettingsStore store;
  private AudioSettings settings;
  private MediaPlayer player;

  public SoundtrackService() {
    this(new AudioSettingsStore());
  }

  public SoundtrackService(AudioSettingsStore store) {
    this.store = store;
    this.settings = store.load();
  }

  /**
   * Pure resolution + fallback (no FX): the case's {@code metadata.soundtrack} resolved exactly
   * like an image (case dir → classpath via {@link ResourceResolver}), or empty when the case,
   * metadata, or field is null/blank, or the path does not resolve anywhere.
   */
  public static Optional<URL> resolveSoundtrack(CaseFile caseFile) {
    if (caseFile == null || caseFile.getMetadata() == null) {
      return Optional.empty();
    }
    String path = caseFile.getMetadata().getSoundtrack();
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    return ResourceResolver.resolve(path, caseDirOf(caseFile));
  }

  /**
   * Starts (or restarts) the looped soundtrack for {@code caseFile}. Any currently playing track is
   * stopped first. If the case declares no resolvable soundtrack, playback is silently skipped.
   */
  public void playForCase(CaseFile caseFile) {
    stop();
    Optional<URL> resolved = resolveSoundtrack(caseFile);
    if (resolved.isEmpty()) {
      return; // silent graceful fallback — absent or unresolvable
    }
    try {
      Media media = new Media(resolved.get().toExternalForm());
      MediaPlayer mediaPlayer = new MediaPlayer(media);
      mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); // loop indefinitely
      mediaPlayer.setVolume(settings.effectiveVolume());
      mediaPlayer.setOnError(this::stop); // unsupported/corrupt at runtime → fall silent
      mediaPlayer.play();
      player = mediaPlayer;
    } catch (RuntimeException | Error e) {
      // Unsupported codec / malformed media must never break play — stay silent.
      logger.warn("Could not start soundtrack {}: {}", resolved.get(), e.toString());
      player = null;
    }
  }

  /** Stops and disposes any playing track. Safe to call when nothing is playing. */
  public void stop() {
    if (player != null) {
      try {
        player.stop();
        player.dispose();
      } catch (RuntimeException | Error ignored) {
        // disposing a half-built player can throw on some platforms — ignore.
      }
      player = null;
    }
  }

  public AudioSettings getSettings() {
    return settings;
  }

  /** Live volume change applied to the running track. Does not persist — call {@link #save()}. */
  public void setVolume(double volume) {
    settings = settings.withVolume(volume);
    applyVolume();
  }

  /** Mute/unmute the running track. Does not persist — call {@link #save()}. */
  public void setMuted(boolean muted) {
    settings = settings.withMuted(muted);
    applyVolume();
  }

  /** Persists the current settings locally (best-effort). */
  public void save() {
    store.save(settings);
  }

  private void applyVolume() {
    if (player != null) {
      try {
        player.setVolume(settings.effectiveVolume());
      } catch (RuntimeException ignored) {
        // ignore — a disposed/errored player just won't update.
      }
    }
  }

  /**
   * Case directory for case-relative paths; null for bundled cases (classpath-only), like images.
   */
  private static Path caseDirOf(CaseFile caseFile) {
    String sourcePath = caseFile.getSourcePath();
    if (sourcePath == null || sourcePath.isBlank()) {
      return null;
    }
    try {
      return Paths.get(sourcePath).getParent();
    } catch (RuntimeException e) {
      return null;
    }
  }
}

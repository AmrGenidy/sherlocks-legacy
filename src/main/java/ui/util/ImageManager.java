package ui.util;

import Core.GameObject;
import Core.Room;
import Core.Suspect;
import extractors.ResourceResolver;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javafx.scene.image.Image;

/**
 * Resolves case-referenced image paths to JavaFX {@link Image}s for the GUI.
 *
 * <p>Resolution is delegated to {@link ResourceResolver} (classpath → case directory → filesystem),
 * the <em>same</em> resolver the {@code CaseValidator} uses, so what the validator reports as
 * resolvable is exactly what loads here. The active case directory must be supplied via {@link
 * #setCaseDirectory(Path)} when a case loads; without it, only classpath and working-directory
 * resolution apply (which is enough for bundled assets shipped on the classpath).
 *
 * <p>Fallback chain when the case provides no resolvable image (see {@code
 * docs/PRESET_ART_WIRING.md}): (1) the case {@code imagePath} always wins; (2) on a miss/blank, a
 * deterministic hand-made engraving {@link PresetArtResolver preset}; (3) only if even the preset
 * cannot resolve, a theme-aware {@link PlaceholderImageGenerator} placeholder — so the UI never
 * shows a broken-image glyph or a blank slot.
 */
public class ImageManager {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(ImageManager.class);

  /** The kind of sprite, so a missing image falls back to a type-appropriate placeholder. */
  private enum Kind {
    ROOM,
    SUSPECT,
    OBJECT,
    WATSON
  }

  private final Map<String, Image> cache = new HashMap<>();

  // Directory of the active case JSON, for resolving case-relative image paths. May be null for
  // bundled cases whose images ship on the classpath.
  private Path caseDirectory;

  public ImageManager() {}

  /**
   * Sets the active case directory used to resolve case-relative image paths. Pass {@code null} to
   * clear it (e.g. for a bundled case). Changing the directory clears the cache so paths re-resolve
   * against the new case.
   */
  public void setCaseDirectory(Path caseDirectory) {
    if (this.caseDirectory == null
        ? caseDirectory != null
        : !this.caseDirectory.equals(caseDirectory)) {
      this.caseDirectory = caseDirectory;
      cache.clear();
    }
  }

  public Image getRoomImage(Room room) {
    if (room == null) return placeholder(Kind.ROOM, "Room");
    return loadOrFallback(room.getImagePath(), Kind.ROOM, null, room.getName());
  }

  /** Room image by path only; the preset fallback selects on the filename-derived name. */
  public Image getRoomImage(String path) {
    return loadOrFallback(path, Kind.ROOM, null, null);
  }

  /** Room image by path, with the room name supplied so the preset fallback is deterministic. */
  public Image getRoomImage(String path, String roomName) {
    return loadOrFallback(path, Kind.ROOM, null, roomName);
  }

  public Image getSuspectImage(Suspect suspect) {
    if (suspect == null) return placeholder(Kind.SUSPECT, "Suspect");
    String id =
        suspect.getId() != null && !suspect.getId().isBlank() ? suspect.getId() : suspect.getName();
    return loadOrFallback(suspect.getImagePath(), Kind.SUSPECT, id, suspect.getName());
  }

  public Image getSuspectImage(String path) {
    return loadOrFallback(path, Kind.SUSPECT, null, null);
  }

  /**
   * A suspect image decoded for a small target (e.g. the 150px Case File portrait). Cached
   * separately from the full-size sprite, so each surface gets art resampled near the size it is
   * actually drawn at instead of an 8× runtime reduction.
   */
  public Image getSuspectImage(String path, int maxHeight) {
    Image scaled = tryLoad(path, Kind.SUSPECT, maxHeight);
    return scaled != null ? scaled : loadOrFallback(path, Kind.SUSPECT, null, null);
  }

  public Image getObjectImage(GameObject object) {
    if (object == null) return placeholder(Kind.OBJECT, "Object");
    return loadOrFallback(object.getImagePath(), Kind.OBJECT, object.getId(), object.getName());
  }

  /**
   * Whether the authored image at {@code path} actually resolves for this kind. When it does not, a
   * preset/placeholder is substituted at render — and that fallback fills its own frame, so the
   * suspect/object's authored sprite scale (calibrated for margin-heavy cut-out art) must NOT be
   * applied to it, or the substitute renders far too large. Callers use this to fall back to a
   * neutral 1.0 scale.
   */
  public boolean suspectImageResolves(String path) {
    return tryLoad(path, Kind.SUSPECT) != null;
  }

  public boolean objectImageResolves(String path) {
    return tryLoad(path, Kind.OBJECT) != null;
  }

  public boolean watsonImageResolves(String path) {
    return tryLoad(path, Kind.WATSON) != null;
  }

  public Image getObjectImage(String path) {
    return loadOrFallback(path, Kind.OBJECT, null, null);
  }

  public Image getWatsonImage(String pathFromMetadata) {
    return loadOrFallback(pathFromMetadata, Kind.WATSON, null, null);
  }

  public Image getDefaultSuspectImage() {
    return placeholder(Kind.SUSPECT, "Suspect");
  }

  public Image getDefaultObjectImage() {
    return placeholder(Kind.OBJECT, "Object");
  }

  /**
   * Three-step fallback (docs/PRESET_ART_WIRING.md): authored {@code path} wins; on miss/blank a
   * deterministic engraving preset (selected from {@code idKey}/{@code nameKey}); only if even that
   * cannot resolve, a theme-aware placeholder.
   */
  private Image loadOrFallback(String path, Kind kind, String idKey, String nameKey) {
    // 1. Authored case image (classpath → case dir → filesystem via ResourceResolver, shared with
    // CaseValidator so what the validator checks is exactly what loads here).
    Image authored = tryLoad(path, kind);
    if (authored != null) {
      return authored;
    }
    // 2. Deterministic engraving preset. When the caller has no id/name (path-only overloads), the
    // filename-derived label is a stable stand-in (it equals the entity name under the
    // images/<name>.png convention the callers build).
    String presetKey = nameKey != null && !nameKey.isBlank() ? nameKey : labelFor(path, kind);
    String presetId = idKey != null && !idKey.isBlank() ? idKey : presetKey;
    Image preset = tryLoad(presetFor(kind, presetId, presetKey), kind);
    if (preset != null) {
      return preset;
    }
    // 3. Procedural placeholder — last resort only (bundled presets should always resolve).
    logger.warn("ImageManager: no image or preset for {} ('{}') — using placeholder.", kind, path);
    return placeholder(kind, labelFor(path, kind));
  }

  /**
   * The largest height (px) an image of this kind is ever drawn at, used as a decode-time downscale
   * cap. Case art is authored ~1264px tall but a sprite is drawn a few hundred px and a Case File
   * portrait only 150px — a 4–8× reduction. ImageView's runtime filter samples too few texels at
   * that ratio (it effectively point-samples), which is what makes flat-colour "cartoony" art look
   * pixelated. Resampling once at DECODE time uses a far better filter, so the ImageView is then
   * only doing a mild reduction. {@code 0} = no cap (room backgrounds fill the room pane).
   */
  private static int decodeCapFor(Kind kind) {
    return kind == Kind.ROOM ? 0 : 768;
  }

  /** Loads {@code path} as a cached {@link Image}, or null if blank/unresolvable/erroring. */
  private Image tryLoad(String path, Kind kind) {
    return tryLoad(path, kind, decodeCapFor(kind));
  }

  private Image tryLoad(String path, Kind kind, int maxHeight) {
    if (path == null || path.isBlank()) {
      return null;
    }
    String key = kind + ":" + maxHeight + ":" + path.trim();
    // Only RESOLVED art (authored images AND presets) is cached. Placeholders are deliberately NOT
    // cached: they are painted from the theme-aware Palette (DESIGN.md §8), so a placeholder
    // rasterised while light must be regenerated on the dark repaint rather than served stale —
    // otherwise a missing-art room shows a pale daylight plate in candlelight
    // (.scratch/gui-g1-theming-integrity). Real images don't re-theme, so caching them is correct
    // (and keeps repeated loads cheap).
    Image cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    try {
      Optional<URL> resolved = ResourceResolver.resolve(path.trim(), caseDirectory);
      if (resolved.isPresent()) {
        URL url = resolved.get();
        Image image;
        try (InputStream is = url.openStream()) {
          image = new Image(is);
        }
        // Re-decode smaller when the source is far larger than it will ever be drawn. Only ever
        // DOWN-scales (a small preset or icon is left at its native size), and the decoder's
        // resampler removes the aliasing ImageView's runtime scale would otherwise produce. Also
        // cuts memory a lot: an 848×1264 sprite is ~4 MB in RAM, ~1.5 MB at the cap.
        if (maxHeight > 0 && !image.isError() && image.getHeight() > maxHeight) {
          try (InputStream is2 = url.openStream()) {
            Image scaled = new Image(is2, 0, maxHeight, true, true); // preserveRatio, smooth
            if (!scaled.isError() && scaled.getHeight() > 0) {
              image = scaled;
            }
          }
        }
        cache.put(key, image);
        return image;
      }
      return null;
    } catch (Exception e) {
      logger.error("ImageManager: error loading image at " + path.trim(), e);
      return null;
    }
  }

  /** The deterministic engraving preset classpath path for a kind, or null for none. */
  private static String presetFor(Kind kind, String idKey, String nameKey) {
    return switch (kind) {
      case ROOM -> PresetArtResolver.roomPreset(nameKey);
      case SUSPECT -> PresetArtResolver.suspectPreset(idKey);
      case OBJECT -> PresetArtResolver.objectPreset(idKey, nameKey);
      case WATSON -> PresetArtResolver.watsonPreset();
    };
  }

  private Image placeholder(Kind kind, String label) {
    return switch (kind) {
      case ROOM -> PlaceholderImageGenerator.createRoomPlaceholder(label, 800, 600);
      case OBJECT -> PlaceholderImageGenerator.createObjectPlaceholder(label, 256);
      case SUSPECT, WATSON -> PlaceholderImageGenerator.createSuspectPlaceholder(label, 256);
    };
  }

  /** Derives a short human label for a placeholder from the image filename, or a type default. */
  private static String labelFor(String path, Kind kind) {
    if (path != null && !path.isBlank()) {
      String name = path.replace('\\', '/');
      int slash = name.lastIndexOf('/');
      if (slash >= 0) name = name.substring(slash + 1);
      int dot = name.lastIndexOf('.');
      if (dot > 0) name = name.substring(0, dot);
      name = name.replace('_', ' ').trim();
      if (!name.isEmpty()) return name;
    }
    return switch (kind) {
      case ROOM -> "Room";
      case SUSPECT, WATSON -> "Suspect";
      case OBJECT -> "Object";
    };
  }
}

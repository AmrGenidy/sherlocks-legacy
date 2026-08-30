package common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The fixed catalog of player-avatar preset ids — the engraving character presets bundled under
 * {@code images/presets/characters/} (see {@code docs/SAVE_AND_PROFILE.md}). Each id is a filename
 * stem (e.g. {@code char_suspect_03}); the player profile stores the <em>id</em>, never a path.
 *
 * <p>This type is intentionally pure (no JavaFX): it is the single source of truth for the gallery,
 * the profile default, and — critically — the server-side <b>allowlist</b>. The multiplayer server
 * validates an inbound avatar id against {@link #isValid} before storing/broadcasting it, so a
 * hostile peer can never inject an arbitrary string or path. FX-side id→{@code Image} resolution
 * lives separately in {@code ui.menu.AvatarImages}.
 */
public final class PlayerAvatars {

  /** The default avatar until the player picks one — the lead-detective preset. */
  public static final String DEFAULT_ID = "char_partner";

  /** Canonical, ordered catalog: the partner, the twelve suspect archetypes, and Watson. */
  public static final List<String> IDS = buildIds();

  private static final Set<String> ID_SET = new LinkedHashSet<>(IDS);

  private PlayerAvatars() {}

  private static List<String> buildIds() {
    List<String> ids =
        Stream.concat(
                Stream.of("char_partner"),
                Stream.concat(
                    IntStream.rangeClosed(1, 12)
                        .mapToObj(i -> String.format("char_suspect_%02d", i)),
                    Stream.of("char_watson")))
            .collect(Collectors.toList());
    return Collections.unmodifiableList(ids);
  }

  /** True when {@code id} is a known preset id (the wire/server allowlist check). */
  public static boolean isValid(String id) {
    return id != null && ID_SET.contains(id);
  }
}

package ui.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The single local player profile (see {@code docs/SAVE_AND_PROFILE.md}): a {@code displayName} and
 * a chosen {@code avatarId} (a preset id from {@link ui.menu.AvatarCatalog}, e.g. {@code
 * char_suspect_03}). Set once and reused so the player never retypes their multiplayer name.
 *
 * <p>Pure value type, persisted by {@link PlayerProfileStore}; no FX dependency (so it is safe to
 * read off the wire-free local store and to feed into the multiplayer identity). Honours roadmap
 * Hard Constraint 1 — an optional local profile file for offline single-player. Mirrors {@link
 * AppSettings}.
 */
public final class PlayerProfile {

  /** Default avatar until the player picks one — the lead-detective preset. */
  public static final String DEFAULT_AVATAR_ID = "char_partner";

  private final String displayName;
  private final String avatarId;

  @JsonCreator
  public PlayerProfile(
      @JsonProperty("displayName") String displayName, @JsonProperty("avatarId") String avatarId) {
    this.displayName = displayName == null ? "" : displayName;
    this.avatarId = (avatarId == null || avatarId.isBlank()) ? DEFAULT_AVATAR_ID : avatarId;
  }

  /** Defaults: no display name yet (blank), the default preset avatar. */
  public static PlayerProfile defaults() {
    return new PlayerProfile("", DEFAULT_AVATAR_ID);
  }

  @JsonProperty("displayName")
  public String displayName() {
    return displayName;
  }

  @JsonProperty("avatarId")
  public String avatarId() {
    return avatarId;
  }

  /**
   * True when the player has set a non-blank display name (so the chip can prompt to set one up).
   */
  public boolean hasDisplayName() {
    return displayName != null && !displayName.isBlank();
  }

  public PlayerProfile withDisplayName(String newDisplayName) {
    return new PlayerProfile(newDisplayName, avatarId);
  }

  public PlayerProfile withAvatarId(String newAvatarId) {
    return new PlayerProfile(displayName, newAvatarId);
  }
}

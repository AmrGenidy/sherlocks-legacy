package common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Join-code digests for LAN discovery (security-pass issue 08).
 *
 * <p>Discovery packets are broadcast to the whole LAN, so they must never carry a private game's
 * join code in cleartext. The broadcaster sends {@code digest(code)} instead; a joining client
 * digests the code the player typed and matches it against discovered packets to learn the host
 * address. The actual join still sends the typed code over TCP to the host.
 */
public final class JoinCodes {

  private JoinCodes() {}

  /**
   * SHA-256 hex digest of the normalized (trimmed, uppercased) join code; {@code null} for
   * null/blank codes (public games have no code).
   */
  public static String digest(String joinCode) {
    if (joinCode == null || joinCode.trim().isEmpty()) {
      return null;
    }
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] hash =
          sha256.digest(
              joinCode.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
    }
  }
}

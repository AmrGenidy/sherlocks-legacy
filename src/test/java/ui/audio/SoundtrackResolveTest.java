package ui.audio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;

/**
 * Resolution + fallback contract for {@code SoundtrackService.resolveSoundtrack} — the pure,
 * headless core (no FX/media). A soundtrack resolves exactly like an image (case dir → classpath
 * via ResourceResolver); anything absent, blank, or unresolvable yields empty (the silent
 * fallback).
 */
public class SoundtrackResolveTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Builds a CaseFile from JSON (as the real loader does), then stamps its source path. */
  private static CaseFile caseWith(String json, String sourcePath) throws Exception {
    CaseFile cf = MAPPER.readValue(json, CaseFile.class);
    if (sourcePath != null) {
      cf.setSourcePath(sourcePath);
    }
    return cf;
  }

  @Test
  public void nullCaseFileResolvesEmpty() {
    assertFalse(SoundtrackService.resolveSoundtrack(null).isPresent());
  }

  @Test
  public void noMetadataResolvesEmpty() throws Exception {
    assertFalse(SoundtrackService.resolveSoundtrack(caseWith("{}", null)).isPresent());
  }

  @Test
  public void absentSoundtrackResolvesEmpty() throws Exception {
    CaseFile cf = caseWith("{\"metadata\":{\"author\":\"A\"}}", null);
    assertFalse(SoundtrackService.resolveSoundtrack(cf).isPresent());
  }

  @Test
  public void blankSoundtrackResolvesEmpty() throws Exception {
    CaseFile cf = caseWith("{\"metadata\":{\"soundtrack\":\"   \"}}", null);
    assertFalse(SoundtrackService.resolveSoundtrack(cf).isPresent());
  }

  @Test
  public void presentButUnresolvableSoundtrackResolvesEmpty() throws Exception {
    CaseFile cf = caseWith("{\"metadata\":{\"soundtrack\":\"no/such/track.mp3\"}}", null);
    assertFalse(SoundtrackService.resolveSoundtrack(cf).isPresent());
  }

  @Test
  public void soundtrackResolvesRelativeToCaseDirectory() throws Exception {
    Path caseDir = Files.createTempDirectory("soundtrack-case");
    Files.writeString(caseDir.resolve("ambient.mp3"), "not really audio, just needs to exist");
    CaseFile cf =
        caseWith(
            "{\"metadata\":{\"soundtrack\":\"ambient.mp3\"}}",
            caseDir.resolve("case.json").toString());

    Optional<URL> resolved = SoundtrackService.resolveSoundtrack(cf);
    assertTrue(resolved.isPresent());
    assertTrue(resolved.get().toString().endsWith("ambient.mp3"));
  }

  @Test
  public void soundtrackResolvesViaClasspath() throws Exception {
    // ResourceResolver checks the classpath first; a bundled case resource stands in for an audio
    // file (resolution does not inspect file type). No case dir needed for classpath assets.
    CaseFile cf =
        caseWith(
            "{\"metadata\":{\"soundtrack\":\"cases/sapphire_case/sapphire_case.json\"}}", null);
    assertTrue(SoundtrackService.resolveSoundtrack(cf).isPresent());
  }
}

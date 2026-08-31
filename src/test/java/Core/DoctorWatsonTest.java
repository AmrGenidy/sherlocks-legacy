package Core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import JsonDTO.CaseData;
import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile.LocalizedWatsonHint;
import common.dto.WatsonHintResponseDTO;
import common.interfaces.GameActionContext;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Behaviour of {@link DoctorWatson} after the legacy flat-hint pool was retired
 * (.scratch/gui-localized-watson-hints): the structured (already localized) hint buckets are the
 * single source of hint text, and an exhausted bucket recycles rather than dead-ending on a
 * hardcoded English "no insights" line.
 */
public class DoctorWatsonTest {

  /**
   * With a single general hint and every hint already served, the next ask recycles the bucket and
   * returns the (localized) hint again — never the "no specific insights" fallback. General hints
   * carry no token cost, so a recycled repeat is simply free.
   */
  @Test
  public void exhaustedGeneralBucketRecyclesInsteadOfDeadEnding() {
    Map<String, List<LocalizedWatsonHint>> hints =
        Map.of("general", List.of(new LocalizedWatsonHint("g1", "Look at the glass.")));
    DoctorWatson watson = new DoctorWatson(hints, null);

    // No contradiction bucket -> the context is never consulted on the general path.
    String first = watson.provideContextAwareHint(null, "p1").getMessage();
    String second = watson.provideContextAwareHint(null, "p1").getMessage();

    assertEquals("Look at the glass.", first);
    assertEquals("the exhausted bucket should recycle the same localized hint", first, second);
  }

  // ---- Targeted analysis: generic fallbacks emit a localization key, authored narrative stays
  // text
  // (.scratch/gui-localized-watson-hints phase 2) ----

  /**
   * With no red-herring metadata, the targeted analysis has no authored narrative and must emit the
   * generic "materially connected" response as a localization KEY (so the client localizes it),
   * never raw English on the wire.
   */
  @Test
  public void targetWithoutMetadataEmitsGenericKeyNotRawEnglish() {
    DoctorWatson watson = new DoctorWatson(null, null);
    WatsonHintResponseDTO dto =
        watson.analyzeTarget("anything", mock(GameActionContext.class), "p1");
    assertEquals("watson.generic.connected", dto.getMessageKey());
  }

  /** An unrecovered red herring (no authored narrative) emits the "distraction" key. */
  @Test
  public void unrecoveredRedHerringEmitsDistractionKey() {
    CaseFile.RedHerringDetail scarf = new CaseFile.RedHerringDetail();
    scarf.isRedHerring = true; // recoverableBy null, narrative null
    CaseFile.RedHerringMetadata meta = new CaseFile.RedHerringMetadata();
    meta.objects = Map.of("scarf", scarf);

    GameActionContext ctx = mock(GameActionContext.class);
    DoctorWatson watson = new DoctorWatson(null, meta);
    WatsonHintResponseDTO dto = watson.analyzeTarget("scarf", ctx, "p1");
    assertEquals("watson.generic.distraction", dto.getMessageKey());
  }

  /**
   * An authored narrative is already localized case content: it resolves to the case-language text
   * with NO key, so it is shown verbatim (the structured/authored path is unchanged).
   */
  @Test
  public void authoredNarrativeStaysLocalizedTextWithNoKey() {
    CaseFile.RedHerringDetail letter = new CaseFile.RedHerringDetail();
    letter.isRedHerring = true;
    letter.narrative = Map.of("en", "An English clue.", "ar", "دليل عربي.");
    CaseFile.RedHerringMetadata meta = new CaseFile.RedHerringMetadata();
    meta.objects = Map.of("letter", letter);

    CaseData caseData = mock(CaseData.class);
    when(caseData.getLanguageCode()).thenReturn("ar");
    GameActionContext ctx = mock(GameActionContext.class);
    when(ctx.getSelectedCase()).thenReturn(caseData);

    DoctorWatson watson = new DoctorWatson(null, meta);
    WatsonHintResponseDTO dto = watson.analyzeTarget("letter", ctx, "p1");
    assertNull("authored narrative must not carry a key", dto.getMessageKey());
    assertEquals("دليل عربي.", dto.getMessage());
  }
}

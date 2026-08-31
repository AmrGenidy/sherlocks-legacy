package Core;

import common.dto.WatsonHintResponseDTO;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoctorWatson extends MovableCharacter {
  private static final Logger logger = LoggerFactory.getLogger(DoctorWatson.class);

  // Generic targeted-analysis responses carry a UI-language localization KEY (resolved on the
  // client)
  // instead of raw English, so a non-English playthrough localizes them too
  // (.scratch/gui-localized-watson-hints phase 2). The literal is the English fallback.
  private static final String KEY_CONNECTED = "watson.generic.connected";
  private static final String EN_CONNECTED = "This appears materially connected to the case.";
  private static final String KEY_RECOVERED = "watson.generic.recovered";
  private static final String EN_RECOVERED =
      "In light of what we now know, this detail gains relevance.";
  private static final String KEY_DISTRACTION = "watson.generic.distraction";
  private static final String EN_DISTRACTION =
      "This may be a distraction. We lack what would give it meaning.";
  private static final String KEY_NO_INSIGHTS = "watson.generic.noInsights";
  private static final String EN_NO_INSIGHTS = "I'm afraid I have no specific insights for this case.";

  // Structured, already-localized hint buckets (category -> hints), resolved to the case language
  // by LocalizedCaseFile. This is the single source of hint text since the legacy flat
  // per-localization `watsonHints` array was retired (.scratch/gui-localized-watson-hints).
  private final Map<String, List<JsonDTO.LocalizedCaseFile.LocalizedWatsonHint>> structuredHints;
  private final JsonDTO.CaseFile.RedHerringMetadata redHerrings;
  private final Set<String> usedHintIds = new HashSet<>();

  public DoctorWatson(
      Map<String, List<JsonDTO.LocalizedCaseFile.LocalizedWatsonHint>> structuredHints,
      JsonDTO.CaseFile.RedHerringMetadata redHerrings) {
    this.structuredHints = structuredHints;
    this.redHerrings = redHerrings;
  }

  /**
   * Context-aware hint: a contradiction nudge when the detective already holds matching evidence,
   * otherwise a general hint. Each bucket recycles once exhausted, so Watson always offers a real,
   * localized hint rather than dead-ending. General hints carry no token cost (the engine charges
   * only for {@code ask watson <target>}), so a recycled repeat is simply free.
   */
  public WatsonHintResponseDTO provideContextAwareHint(
      common.interfaces.GameActionContext context, String playerId) {
    // 1. Priority: Contradiction — only when a held-evidence contradiction is actually possible.
    if (structuredHints != null && structuredHints.containsKey("contradiction")) {
      String contHint = tryGetContradictionHint(context, playerId);
      if (contHint != null) return new WatsonHintResponseDTO(contHint, true);
    }

    // 2. Priority: General. Bucket text is authored case content, already localized.
    String generalHint = serveFromBucket("general");
    if (generalHint != null) {
      return new WatsonHintResponseDTO(generalHint, true);
    }

    // 3. No structured hints authored at all — a generic fallback carrying a UI-language key.
    return generic(KEY_NO_INSIGHTS, EN_NO_INSIGHTS);
  }

  /**
   * Serves an unseen hint from {@code category}, marking it used; when every hint in the bucket has
   * been seen, resets the bucket's used set and recycles (so the lifeline never empties). Returns
   * null only when the bucket is genuinely absent or empty.
   */
  private String serveFromBucket(String category) {
    List<JsonDTO.LocalizedCaseFile.LocalizedWatsonHint> bucket =
        structuredHints == null ? null : structuredHints.get(category);
    if (bucket == null || bucket.isEmpty()) {
      return null;
    }

    List<JsonDTO.LocalizedCaseFile.LocalizedWatsonHint> available = new ArrayList<>();
    for (JsonDTO.LocalizedCaseFile.LocalizedWatsonHint h : bucket) {
      if (!usedHintIds.contains(h.getId())) {
        available.add(h);
      }
    }

    if (available.isEmpty()) {
      // Every hint in this bucket has been served: recycle it.
      bucket.forEach(h -> usedHintIds.remove(h.getId()));
      available.addAll(bucket);
    }

    JsonDTO.LocalizedCaseFile.LocalizedWatsonHint selected =
        available.get(random.nextInt(available.size()));
    usedHintIds.add(selected.getId());
    return selected.getText();
  }

  private String tryGetContradictionHint(
      common.interfaces.GameActionContext context, String playerId) {
    // Logic: Iterate suspects -> check active state -> check contradiction rules ->
    // if player has evidence for rule -> return random contradiction hint
    List<common.dto.JournalEntryDTO> playerJournal = context.getJournalEntries(playerId);
    Set<String> playerEvidenceIds = new HashSet<>();
    for (common.dto.JournalEntryDTO e : playerJournal) {
      playerEvidenceIds.add(e.getSourceId());
      playerEvidenceIds.add(e.getId());
    }

    // Simple heuristic: If there is ANY possible contradiction, give a hint
    boolean possibleContradiction = false;
    for (Suspect s : context.getAllSuspects()) {
      JsonDTO.CaseFile.SuspectStateData stateData = s.getCurrentStateData();
      if (stateData != null && stateData.getContradictions() != null) {
        for (JsonDTO.CaseFile.ContradictionRule rule : stateData.getContradictions()) {
          if (playerEvidenceIds.contains(rule.getEvidenceId())) {
            possibleContradiction = true;
            break;
          }
        }
      }
      if (possibleContradiction) break;
    }

    if (possibleContradiction) {
      // Recycles like the general bucket; null only if no contradiction hints are authored.
      return serveFromBucket("contradiction");
    }
    return null;
  }

  /**
   * Targeted analysis ({@code ask watson <target>}). An authored red-herring narrative is already
   * localized case content, returned verbatim (no key). Every generic fallback instead carries a
   * UI-language localization key so the client renders it in the player's language.
   */
  public WatsonHintResponseDTO analyzeTarget(
      String targetId, common.interfaces.GameActionContext context, String playerId) {
    if (redHerrings == null) {
      return generic(KEY_CONNECTED, EN_CONNECTED);
    }

    String type = "unknown";
    boolean metaFound = false;
    boolean narrativeUsed = false;
    String lang = "unknown";

    // Attempt to get language from context if possible
    if (context.getSelectedCase() != null) {
      lang = context.getSelectedCase().getLanguageCode();
    }

    // Check objects
    if (redHerrings.getObjects() != null && redHerrings.getObjects().containsKey(targetId)) {
      type = "object";
      metaFound = true;
      JsonDTO.CaseFile.RedHerringDetail detail = redHerrings.getObjects().get(targetId);

      if (detail.getNarrative() != null && !detail.getNarrative().isEmpty()) {
        narrativeUsed = true;
        logWatsonDebug(targetId, type, metaFound, narrativeUsed, lang);
        return new WatsonHintResponseDTO(getLocalizedNarrative(detail.getNarrative(), lang), true);
      }

      logWatsonDebug(targetId, type, metaFound, narrativeUsed, lang);
      return getRedHerringNarrative(detail, context, playerId);
    }

    // Check suspects
    if (redHerrings.getSuspects() != null && redHerrings.getSuspects().containsKey(targetId)) {
      type = "suspect";
      metaFound = true;
      JsonDTO.CaseFile.RedHerringDetail detail = redHerrings.getSuspects().get(targetId);

      if (detail.getNarrative() != null && !detail.getNarrative().isEmpty()) {
        narrativeUsed = true;
        logWatsonDebug(targetId, type, metaFound, narrativeUsed, lang);
        return new WatsonHintResponseDTO(getLocalizedNarrative(detail.getNarrative(), lang), true);
      }

      logWatsonDebug(targetId, type, metaFound, narrativeUsed, lang);
      return getRedHerringNarrative(detail, context, playerId);
    }

    logWatsonDebug(targetId, type, metaFound, narrativeUsed, lang);
    return generic(KEY_CONNECTED, EN_CONNECTED);
  }

  /** A generic, non-authored response: a UI-language key plus its English fallback. */
  private static WatsonHintResponseDTO generic(String key, String englishFallback) {
    return new WatsonHintResponseDTO(key, englishFallback, true);
  }

  private void logWatsonDebug(
      String target, String type, boolean meta, boolean narrative, String lang) {
    logger.debug(
        "[WATSON] target={} type={} metaFound={} narrativeUsed={} lang={}",
        target,
        type,
        meta,
        narrative,
        lang);
  }

  private String getLocalizedNarrative(Map<String, String> narrativeMap, String langCode) {
    if (narrativeMap.containsKey(langCode)) {
      return narrativeMap.get(langCode);
    }
    if (narrativeMap.containsKey("en")) {
      return narrativeMap.get("en");
    }
    // Fallback to first available
    return narrativeMap.values().iterator().next();
  }

  private WatsonHintResponseDTO getRedHerringNarrative(
      JsonDTO.CaseFile.RedHerringDetail detail,
      common.interfaces.GameActionContext context,
      String playerId) {
    if (detail.isRedHerring()) {
      if (detail.getRecoverableBy() != null) {
        // Check if player has the recovery note
        if (context.getJournalEntryById(playerId, detail.getRecoverableBy()) != null
            || !context
                .getJournalEntriesBySourceId(playerId, detail.getRecoverableBy())
                .isEmpty()) {
          return generic(KEY_RECOVERED, EN_RECOVERED);
        }
      }
      return generic(KEY_DISTRACTION, EN_DISTRACTION);
    } else {
      return generic(KEY_CONNECTED, EN_CONNECTED);
    }
  }
}

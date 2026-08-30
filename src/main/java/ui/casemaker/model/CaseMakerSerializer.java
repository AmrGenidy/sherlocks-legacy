package ui.casemaker.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders a {@link CaseDraft} to {@code CaseFile}-shaped JSON — the save format the existing {@code
 * CaseLoader}/{@code CaseValidator} consume (see DECISIONS DEC-1). The editor never edits a {@code
 * CaseFile} directly; this is the one place that knows the on-disk JSON shape.
 *
 * <p>Slice 1 emits case metadata and the room graph. Later slices extend this with objects,
 * suspects, combine logic, and per-language localizations.
 */
public final class CaseMakerSerializer {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CaseMakerSerializer() {}

  /**
   * The top-level keys this serializer OWNS — everything {@link #modeledTree} can emit. On export
   * these are stripped from the original copy and rewritten from the draft (so deleting modeled
   * content actually removes it); any OTHER top-level key in the original (case_file, red_herrings,
   * leads, future unknowns) passes through verbatim.
   */
  private static final java.util.Set<String> OWNED_TOP_LEVEL_KEYS =
      java.util.Set.of(
          "universal_title",
          "metadata",
          "startingInsightTokens",
          "startingRoom",
          "rooms",
          "combine_logic",
          "watson",
          "localizations");

  /** Serializes the draft to a pretty-printed JSON string. */
  public static String toJson(CaseDraft draft) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toTree(draft));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize case draft to JSON", e);
    }
  }

  /**
   * The JSON to write: the modeled/edited fields overlaid onto a copy of the loaded case's original
   * tree, so top-level blocks the editor does not model pass through verbatim (a load→export
   * round-trip preserves case_file, red_herrings, leads, …). For a brand-new draft (no original)
   * this is just the modeled tree.
   */
  private static ObjectNode toTree(CaseDraft draft) {
    ObjectNode modeled = modeledTree(draft);
    if (draft.getOriginalRoot() == null || !draft.getOriginalRoot().isObject()) {
      return modeled;
    }
    ObjectNode merged = (ObjectNode) draft.getOriginalRoot().deepCopy();
    // Drop every key the serializer owns before overlaying, so stale modeled data from the original
    // (e.g. a combine rule the author deleted) never lingers; unmodeled keys are left untouched.
    merged.remove(OWNED_TOP_LEVEL_KEYS);
    merged.setAll(modeled);
    return merged;
  }

  private static ObjectNode modeledTree(CaseDraft draft) {
    ObjectNode root = MAPPER.createObjectNode();
    putIfPresent(root, "universal_title", draft.getUniversalTitle());

    ObjectNode metadata = MAPPER.createObjectNode();
    putIfPresent(metadata, "title", draft.getTitle());
    putIfPresent(metadata, "author", draft.getAuthor());
    // Author-defined character names; omitted when blank so existing cases serialize identically.
    putIfPresent(metadata, "detectiveName", draft.getDetectiveName());
    putIfPresent(metadata, "helperName", draft.getHelperName());
    putIfPresent(metadata, "watsonImagePath", draft.getWatsonImagePath());
    if (draft.getWatsonImageScaleX() != 1.0) {
      metadata.put("watsonImageScaleX", draft.getWatsonImageScaleX());
    }
    if (draft.getWatsonImageScaleY() != 1.0) {
      metadata.put("watsonImageScaleY", draft.getWatsonImageScaleY());
    }
    if (draft.isWatsonFlipX()) {
      metadata.put("watsonFlipX", true);
    }
    if (draft.isWatsonFlipY()) {
      metadata.put("watsonFlipY", true);
    }
    if (draft.getWatsonRotation() != 0) {
      metadata.put("watsonRotation", draft.getWatsonRotation());
    }
    if (draft.getWatsonLabelDX() != null && draft.getWatsonLabelDY() != null) {
      metadata.put("watsonLabelDX", draft.getWatsonLabelDX());
      metadata.put("watsonLabelDY", draft.getWatsonLabelDY());
    }
    putIfPresent(metadata, "soundtrack", draft.getSoundtrack());
    if (!metadata.isEmpty()) {
      root.set("metadata", metadata);
    }

    if (draft.getStartingInsightTokens() != null) {
      root.put("startingInsightTokens", draft.getStartingInsightTokens());
    }
    if (draft.getStartingRoom() != null) {
      root.put("startingRoom", draft.getStartingRoom().getName());
    }

    ArrayNode rooms = MAPPER.createArrayNode();
    for (RoomDraft room : draft.getRooms()) {
      ObjectNode roomNode = MAPPER.createObjectNode();
      roomNode.put("name", room.getName());
      ObjectNode neighbors = MAPPER.createObjectNode();
      room.getNeighbors()
          .forEach((direction, target) -> neighbors.put(direction, target.getName()));
      if (!neighbors.isEmpty()) {
        roomNode.set("neighbors", neighbors);
      }
      putIfPresent(roomNode, "imagePath", room.getImagePath());
      // Per-room Dr. Watson position (universal); omitted when the author left it unplaced.
      if (room.getWatsonPosX() != null && room.getWatsonPosY() != null) {
        roomNode.put("watsonPosX", room.getWatsonPosX());
        roomNode.put("watsonPosY", room.getWatsonPosY());
      }
      // Per-room Dr. Watson size/orientation overrides; each omitted when unset (falls back to the
      // global metadata.watson* value at load/runtime).
      if (room.getWatsonImageScaleX() != null) {
        roomNode.put("watsonImageScaleX", room.getWatsonImageScaleX());
      }
      if (room.getWatsonImageScaleY() != null) {
        roomNode.put("watsonImageScaleY", room.getWatsonImageScaleY());
      }
      if (Boolean.TRUE.equals(room.getWatsonFlipX())) {
        roomNode.put("watsonFlipX", true);
      }
      if (Boolean.TRUE.equals(room.getWatsonFlipY())) {
        roomNode.put("watsonFlipY", true);
      }
      if (room.getWatsonRotation() != null) {
        roomNode.put("watsonRotation", room.getWatsonRotation());
      }
      if (room.getWatsonLabelDX() != null && room.getWatsonLabelDY() != null) {
        roomNode.put("watsonLabelDX", room.getWatsonLabelDX());
        roomNode.put("watsonLabelDY", room.getWatsonLabelDY());
      }
      roomNode.set("objects", objectStubs(room));
      rooms.add(roomNode);
    }
    root.set("rooms", rooms);

    if (!draft.getCombineRules().isEmpty()) {
      root.set("combine_logic", combineLogic(draft));
    }
    if (!draft.getWatsonHints().isEmpty()) {
      root.set("watson", watson(draft));
    }
    root.set("localizations", localizations(draft));

    return root;
  }

  /** Combine rules ({@code combine_logic}, universal). resultText is a localized map (DEC-8). */
  private static ArrayNode combineLogic(CaseDraft draft) {
    ArrayNode rules = MAPPER.createArrayNode();
    for (CombineRuleDraft rule : draft.getCombineRules()) {
      ObjectNode node = MAPPER.createObjectNode();
      ArrayNode requires = MAPPER.createArrayNode();
      rule.getRequires().forEach(requires::add);
      node.set("requires", requires);
      putIfPresent(node, "resultDeductionId", rule.getResultDeductionId());
      ObjectNode resultText = langMap(rule.resultTextLocalized());
      if (resultText != null) {
        node.set("resultText", resultText);
      }
      if (rule.getTokenReward() != null) {
        node.put("tokenReward", rule.getTokenReward());
      }
      node.put("repeatable", rule.isRepeatable());
      rules.add(node);
    }
    return rules;
  }

  /**
   * Watson hints ({@code watson.hints}, universal), grouped by category; text is a localized map.
   */
  private static ObjectNode watson(CaseDraft draft) {
    ObjectNode hintsByCategory = MAPPER.createObjectNode();
    for (WatsonHintDraft hint : draft.getWatsonHints()) {
      ArrayNode list = (ArrayNode) hintsByCategory.get(hint.getCategory());
      if (list == null) {
        list = MAPPER.createArrayNode();
        hintsByCategory.set(hint.getCategory(), list);
      }
      ObjectNode node = MAPPER.createObjectNode();
      putIfPresent(node, "id", hint.getId());
      ObjectNode text = langMap(hint.textLocalized());
      if (text != null) {
        node.set("text", text);
      }
      list.add(node);
    }
    ObjectNode watson = MAPPER.createObjectNode();
    watson.set("hints", hintsByCategory);
    return watson;
  }

  /** Universal object stubs ({@code rooms[].objects[]}): id + render fields, no localized text. */
  private static ArrayNode objectStubs(RoomDraft room) {
    ArrayNode objects = MAPPER.createArrayNode();
    for (ObjectDraft object : room.getObjects()) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("id", object.getId());
      node.put("name", object.getName());
      putIfPresent(node, "imagePath", object.getImagePath());
      if (object.getPosX() != null) {
        node.put("posX", object.getPosX());
      }
      if (object.getPosY() != null) {
        node.put("posY", object.getPosY());
      }
      if (object.getImageScaleX() != 1.0) {
        node.put("imageScaleX", object.getImageScaleX());
      }
      if (object.getImageScaleY() != 1.0) {
        node.put("imageScaleY", object.getImageScaleY());
      }
      if (object.isFlipX()) {
        node.put("flipX", true);
      }
      if (object.isFlipY()) {
        node.put("flipY", true);
      }
      if (object.getRotation() != 0) {
        node.put("rotation", object.getRotation());
      }
      if (object.getLabelDX() != null && object.getLabelDY() != null) {
        node.put("labelDX", object.getLabelDX());
        node.put("labelDY", object.getLabelDY());
      }
      objects.add(node);
    }
    return objects;
  }

  /**
   * The localization block: one entry per authored language (slice 5). Structural data (ids,
   * positions, slot/choice structure, maxDeductions) is identical across languages; only text
   * differs. Untranslated text is emitted blank/absent so the validator can surface the gap.
   */
  private static ObjectNode localizations(CaseDraft draft) {
    ObjectNode localizations = MAPPER.createObjectNode();
    for (String lang : draft.getLanguages()) {
      localizations.set(lang, localization(draft, lang));
    }
    return localizations;
  }

  private static ObjectNode localization(CaseDraft draft, String lang) {
    ObjectNode loc = MAPPER.createObjectNode();
    putIfPresent(loc, "languageName", draft.getLanguageName(lang));
    putIfPresent(loc, "title", draft.titleText().get(lang));
    putIfPresent(loc, "invitation", draft.invitationText().get(lang));
    putIfPresent(loc, "description", draft.descriptionText().get(lang));
    // Per-language character-name overrides; absent → the single metadata value is used at runtime.
    putIfPresent(loc, "detectiveName", draft.detectiveNameText().get(lang));
    putIfPresent(loc, "helperName", draft.helperNameText().get(lang));

    ArrayNode roomDetails = MAPPER.createArrayNode();
    ArrayNode objectDetails = MAPPER.createArrayNode();
    for (RoomDraft room : draft.getRooms()) {
      ObjectNode roomDetail = MAPPER.createObjectNode();
      roomDetail.put("name", room.getName());
      // Per-language Display Name (.scratch/gui-localized-case-names); .name stays the Universal
      // Name.
      putIfPresent(roomDetail, "displayName", room.displayNameText().get(lang));
      roomDetail.put("description", orEmpty(room.descriptionText().get(lang)));
      roomDetails.add(roomDetail);

      for (ObjectDraft object : room.getObjects()) {
        ObjectNode detail = MAPPER.createObjectNode();
        detail.put("name", object.getName());
        putIfPresent(detail, "displayName", object.displayNameText().get(lang));
        detail.put("description", orEmpty(object.descriptionText().get(lang)));
        putIfPresent(detail, "examine", object.examineText().get(lang));
        putIfPresent(detail, "deduce", object.deduceText().get(lang));
        objectDetails.add(detail);
      }
    }
    loc.set("roomDetails", roomDetails);
    loc.set("objectDetails", objectDetails);
    loc.set("suspects", suspects(draft, lang));

    if (!draft.getTaskTexts().isEmpty()) {
      ArrayNode tasks = MAPPER.createArrayNode();
      draft.getTaskTexts().forEach(task -> tasks.add(orEmpty(task.get(lang))));
      loc.set("tasks", tasks);
    }
    if (!draft.getRankTiers().isEmpty()) {
      loc.set("rankingTiers", rankingTiers(draft, lang));
    }
    if (!draft.getExamQuestions().isEmpty()) {
      loc.set("final_exam", finalExam(draft, lang));
    }
    return loc;
  }

  private static ArrayNode rankingTiers(CaseDraft draft, String lang) {
    ArrayNode tiers = MAPPER.createArrayNode();
    for (RankTierDraft tier : draft.getRankTiers()) {
      ObjectNode node = MAPPER.createObjectNode();
      putIfPresent(node, "rankName", tier.getRankName());
      node.put("maxDeductions", tier.getMaxDeductions());
      putIfPresent(node, "description", tier.descriptionText().get(lang));
      if (tier.isDefaultRank()) {
        node.put("defaultRank", true);
      }
      putIfPresent(node, "winningStatement", tier.winningStatementText().get(lang));
      tiers.add(node);
    }
    return tiers;
  }

  private static ObjectNode finalExam(CaseDraft draft, String lang) {
    ArrayNode questions = MAPPER.createArrayNode();
    for (FinalExamQuestionDraft question : draft.getExamQuestions()) {
      ObjectNode questionNode = MAPPER.createObjectNode();
      putIfPresent(questionNode, "question_prompt", question.promptText().get(lang));

      ObjectNode slots = MAPPER.createObjectNode();
      ObjectNode correct = MAPPER.createObjectNode();
      for (ExamSlotDraft slot : question.getSlots()) {
        ObjectNode slotNode = MAPPER.createObjectNode();
        slotNode.put("slot_id", slot.getSlotId());
        ArrayNode choices = MAPPER.createArrayNode();
        for (ExamChoiceDraft choice : slot.getChoices()) {
          ObjectNode choiceNode = MAPPER.createObjectNode();
          choiceNode.put("choice_id", choice.getChoiceId());
          putIfPresent(choiceNode, "choice_text", choice.textLocalized().get(lang));
          choices.add(choiceNode);
        }
        slotNode.set("choices", choices);
        slots.set(slot.getSlotId(), slotNode);
        if (slot.getCorrectChoice() != null) {
          correct.put(slot.getSlotId(), slot.getCorrectChoice().getChoiceId());
        }
      }
      questionNode.set("slots", slots);
      questionNode.set("correct_combination", correct);
      questions.add(questionNode);
    }
    ObjectNode exam = MAPPER.createObjectNode();
    exam.set("questions", questions);
    return exam;
  }

  /**
   * Suspects for one language (DEC-2). The language-independent placement fields (home room,
   * position, scale, stationary, ids, state structure) are written into every language block
   * identically; only statements and success messages vary by language.
   */
  private static ArrayNode suspects(CaseDraft draft, String lang) {
    ArrayNode suspects = MAPPER.createArrayNode();
    for (SuspectDraft suspect : draft.getSuspects()) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("name", suspect.getName());
      node.put("id", suspect.getId());
      // Per-language Display Name (.scratch/gui-localized-case-names); .name stays the Universal
      // Name.
      putIfPresent(node, "displayName", suspect.displayNameText().get(lang));
      putIfPresent(node, "imagePath", suspect.getImagePath());
      if (suspect.getImageScaleX() != 1.0) {
        node.put("imageScaleX", suspect.getImageScaleX());
      }
      if (suspect.getImageScaleY() != 1.0) {
        node.put("imageScaleY", suspect.getImageScaleY());
      }
      if (suspect.isFlipX()) {
        node.put("flipX", true);
      }
      if (suspect.isFlipY()) {
        node.put("flipY", true);
      }
      if (suspect.getRotation() != 0) {
        node.put("rotation", suspect.getRotation());
      }
      if (suspect.getHomeRoom() != null) {
        node.put("homeRoom", suspect.getHomeRoom().getName());
      }
      if (suspect.getPosX() != null) {
        node.put("posX", suspect.getPosX());
      }
      if (suspect.getPosY() != null) {
        node.put("posY", suspect.getPosY());
      }
      if (suspect.getLabelDX() != null && suspect.getLabelDY() != null) {
        node.put("labelDX", suspect.getLabelDX());
        node.put("labelDY", suspect.getLabelDY());
      }
      node.put("stationary", suspect.isStationary());
      putIfPresent(node, "initialState", suspect.getInitialState());
      // A simple (state-less) suspect's single per-language statement + clue. Written whenever
      // present so a non-contradictable suspect keeps its dialogue through the round-trip; a
      // state-based suspect leaves these empty and speaks through its states below.
      putIfPresent(node, "statement", suspect.statementText().get(lang));
      putIfPresent(node, "clue", suspect.clueText().get(lang));

      ObjectNode states = MAPPER.createObjectNode();
      suspect
          .getStates()
          .forEach(
              (stateName, state) -> {
                ObjectNode stateNode = MAPPER.createObjectNode();
                putIfPresent(stateNode, "statement", state.statementText().get(lang));
                ArrayNode contradictions = MAPPER.createArrayNode();
                for (ContradictionDraft rule : state.getContradictions()) {
                  ObjectNode ruleNode = MAPPER.createObjectNode();
                  putIfPresent(ruleNode, "evidenceId", rule.getEvidenceId());
                  putIfPresent(ruleNode, "nextState", rule.getNextState());
                  putIfPresent(ruleNode, "rewardDeductionId", rule.getRewardDeductionId());
                  putIfPresent(ruleNode, "successMessage", rule.successMessageText().get(lang));
                  contradictions.add(ruleNode);
                }
                if (!contradictions.isEmpty()) {
                  stateNode.set("contradictions", contradictions);
                }
                states.set(stateName, stateNode);
              });
      if (!states.isEmpty()) {
        node.set("states", states);
      }
      suspects.add(node);
    }
    return suspects;
  }

  /**
   * A language→text JSON object for a {@link LocalizedText}, or {@code null} when it has no text.
   */
  private static ObjectNode langMap(LocalizedText text) {
    if (text.isEmpty()) {
      return null;
    }
    ObjectNode node = MAPPER.createObjectNode();
    text.asMap().forEach(node::put);
    return node;
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  private static void putIfPresent(ObjectNode node, String field, String value) {
    if (value != null && !value.isBlank()) {
      node.put(field, value);
    }
  }
}

package ui.casemaker.model;

import JsonDTO.CaseFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a parsed {@link CaseFile} into an editable {@link CaseDraft} — the inverse of {@link
 * CaseMakerSerializer} (slice 7). Lives in the model package so it can wire raw neighbour links and
 * reconstruct entities faithfully.
 *
 * <p>Structural data (ids, positions, slot/choice structure, rank tiers, exam shape) is taken from
 * the primary language (or the first available); only text is read per language. This collapses any
 * legacy per-language structural divergence to a single shared structure (DEC-10). Relative image
 * paths are resolved against the case directory so the editor's previews load them and a re-export
 * copies them back; legacy cases missing suspect home rooms load "unplaced" and surface as
 * validation errors for the author to fix (DEC-6).
 */
public final class CaseDraftLoader {

  private CaseDraftLoader() {}

  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();

  public static CaseDraft load(CaseFile caseFile) {
    CaseDraft draft = new CaseDraft();
    Path caseDir = caseDirOf(caseFile);
    draft.setSourceDir(caseDir); // so editor validation can confirm images that resolve on disk
    // Remember the exact source file so "Save" can overwrite it in place (null for a new case).
    if (caseFile.getSourcePath() != null && !caseFile.getSourcePath().isBlank()) {
      draft.setSourceFile(Path.of(caseFile.getSourcePath()));
    }

    loadOriginalRoot(draft, caseFile);
    loadMetadata(draft, caseFile, caseDir);
    Map<String, RoomDraft> roomsByName = loadRooms(draft, caseFile, caseDir);
    List<String> languages = loadLanguages(draft, caseFile);
    String primary = languages.isEmpty() ? LocalizedText.PRIMARY : languages.get(0);

    loadCaseText(draft, caseFile, languages);
    loadRoomAndObjectText(draft, caseFile, languages, roomsByName);
    loadSuspects(draft, caseFile, languages, primary, roomsByName, caseDir);
    loadCombineRules(draft, caseFile, languages);
    loadWatsonHints(draft, caseFile, languages);
    loadRankTiers(draft, caseFile, languages, primary);
    loadTasks(draft, caseFile, languages, primary);
    loadFinalExam(draft, caseFile, languages, primary);
    return draft;
  }

  // ---- Original-JSON passthrough -----------------------------------------------------------

  /**
   * Retains the case's full original JSON tree on the draft so a load→export round-trip preserves
   * top-level blocks the editor does not model (case_file, red_herrings, leads, and any future
   * unknown keys). The parsed {@link CaseFile} drops truly-unmodeled keys (e.g. {@code leads}), so
   * we re-read the raw file from its {@code sourcePath}. Best-effort: if the path is absent or
   * unreadable the draft simply exports the modeled fields only (prior behaviour).
   */
  private static void loadOriginalRoot(CaseDraft draft, CaseFile caseFile) {
    String source = caseFile.getSourcePath();
    if (source == null || source.isBlank()) {
      return;
    }
    try {
      Path path = Path.of(source);
      if (Files.isRegularFile(path)) {
        draft.setOriginalRoot(MAPPER.readTree(path.toFile()));
      }
    } catch (Exception ignored) {
      // leave originalRoot null — export falls back to the modeled fields only
    }
  }

  // ---- Metadata & rooms --------------------------------------------------------------------

  private static void loadMetadata(CaseDraft draft, CaseFile caseFile, Path caseDir) {
    draft.setUniversalTitle(caseFile.getUniversalTitle());
    if (caseFile.getMetadata() != null) {
      CaseFile.Metadata meta = caseFile.getMetadata();
      draft.setAuthor(meta.getAuthor());
      draft.setDetectiveName(meta.getDetectiveName());
      draft.setHelperName(meta.getHelperName());
      draft.setWatsonImagePath(resolveImage(meta.getWatsonImagePath(), caseDir));
      if (meta.getWatsonImageScale() != null) {
        draft.setWatsonImageScale(meta.getWatsonImageScale()); // legacy uniform → both axes
      }
      if (meta.getWatsonImageScaleX() != null) {
        draft.setWatsonImageScaleX(meta.getWatsonImageScaleX());
      }
      if (meta.getWatsonImageScaleY() != null) {
        draft.setWatsonImageScaleY(meta.getWatsonImageScaleY());
      }
      draft.setWatsonFlipX(Boolean.TRUE.equals(meta.getWatsonFlipX()));
      draft.setWatsonFlipY(Boolean.TRUE.equals(meta.getWatsonFlipY()));
      if (meta.getWatsonRotation() != null) {
        draft.setWatsonRotation(meta.getWatsonRotation());
      }
      if (caseFile.getMetadata().getWatsonLabelDX() != null
          && caseFile.getMetadata().getWatsonLabelDY() != null) {
        draft.setWatsonLabelOffset(
            caseFile.getMetadata().getWatsonLabelDX(), caseFile.getMetadata().getWatsonLabelDY());
      }
      draft.setSoundtrack(resolveImage(caseFile.getMetadata().getSoundtrack(), caseDir));
    }
    draft.setStartingInsightTokens(caseFile.getStartingInsightTokens());
  }

  private static Map<String, RoomDraft> loadRooms(
      CaseDraft draft, CaseFile caseFile, Path caseDir) {
    Map<String, RoomDraft> byName = new LinkedHashMap<>();
    if (caseFile.getRooms() == null) {
      return byName;
    }
    for (CaseFile.RoomData room : caseFile.getRooms()) {
      if (room == null || room.getName() == null) {
        continue;
      }
      RoomDraft draftRoom = draft.addRoom(room.getName());
      draftRoom.setImagePath(resolveImage(room.getImagePath(), caseDir));
      if (room.getWatsonPosX() != null && room.getWatsonPosY() != null) {
        draftRoom.setWatsonPosition(room.getWatsonPosX(), room.getWatsonPosY());
      }
      // Per-room Watson size/orientation overrides (each nullable; null keeps the global fallback).
      draftRoom.setWatsonImageScaleX(room.getWatsonImageScaleX());
      draftRoom.setWatsonImageScaleY(room.getWatsonImageScaleY());
      draftRoom.setWatsonFlipX(room.getWatsonFlipX());
      draftRoom.setWatsonFlipY(room.getWatsonFlipY());
      draftRoom.setWatsonRotation(room.getWatsonRotation());
      draftRoom.setWatsonLabelOffset(room.getWatsonLabelDX(), room.getWatsonLabelDY());
      byName.put(room.getName(), draftRoom);
      if (room.getObjects() != null) {
        for (CaseFile.GameObjectData obj : room.getObjects()) {
          if (obj == null || obj.getName() == null) {
            continue;
          }
          ObjectDraft draftObj = draftRoom.addObject(obj.getName());
          draftObj.setId(obj.getId());
          draftObj.setImagePath(resolveImage(obj.getImagePath(), caseDir));
          if (obj.getPosX() != null && obj.getPosY() != null) {
            draftObj.setPosition(obj.getPosX(), obj.getPosY());
          }
          if (obj.getImageScale() != null) {
            draftObj.setImageScale(obj.getImageScale()); // legacy uniform → both axes
          }
          if (obj.getImageScaleX() != null) {
            draftObj.setImageScaleX(obj.getImageScaleX());
          }
          if (obj.getImageScaleY() != null) {
            draftObj.setImageScaleY(obj.getImageScaleY());
          }
          draftObj.setFlipX(Boolean.TRUE.equals(obj.getFlipX()));
          draftObj.setFlipY(Boolean.TRUE.equals(obj.getFlipY()));
          if (obj.getRotation() != null) {
            draftObj.setRotation(obj.getRotation());
          }
          if (obj.getLabelDX() != null && obj.getLabelDY() != null) {
            draftObj.setLabelOffset(obj.getLabelDX(), obj.getLabelDY());
          }
        }
      }
    }
    // Neighbour links (raw, faithful to the JSON — putNeighbor is package-private).
    for (CaseFile.RoomData room : caseFile.getRooms()) {
      if (room == null || room.getNeighbors() == null) {
        continue;
      }
      RoomDraft from = byName.get(room.getName());
      room.getNeighbors()
          .forEach(
              (dir, targetName) -> {
                RoomDraft target = byName.get(targetName);
                if (from != null && target != null) {
                  from.putNeighbor(dir, target);
                }
              });
    }
    RoomDraft start = byName.get(caseFile.getStartingRoom());
    if (start != null) {
      draft.setStartingRoom(start);
    }
    return byName;
  }

  // ---- Languages & text --------------------------------------------------------------------

  private static List<String> loadLanguages(CaseDraft draft, CaseFile caseFile) {
    List<String> languages = new ArrayList<>();
    if (caseFile.getLocalizations() == null) {
      return languages;
    }
    // Primary first, then the rest in declaration order.
    if (caseFile.getLocalizations().containsKey(LocalizedText.PRIMARY)) {
      languages.add(LocalizedText.PRIMARY);
    }
    for (String lang : caseFile.getLocalizations().keySet()) {
      if (!languages.contains(lang)) {
        languages.add(lang);
      }
    }
    for (String lang : languages) {
      draft.addLanguage(lang); // primary already present; others appended
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc != null) {
        draft.setLanguageName(lang, loc.getLanguageName());
      }
    }
    return languages;
  }

  private static void loadCaseText(CaseDraft draft, CaseFile caseFile, List<String> languages) {
    for (String lang : languages) {
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc == null) {
        continue;
      }
      draft.titleText().set(lang, loc.getTitle());
      draft.invitationText().set(lang, loc.getInvitation());
      draft.descriptionText().set(lang, loc.getDescription());
      // Per-language character-name overrides (the single metadata value was loaded in loadMetadata
      // as the primary/default).
      if (loc.getDetectiveName() != null && !loc.getDetectiveName().isBlank()) {
        draft.detectiveNameText().set(lang, loc.getDetectiveName());
      }
      if (loc.getHelperName() != null && !loc.getHelperName().isBlank()) {
        draft.helperNameText().set(lang, loc.getHelperName());
      }
    }
  }

  private static void loadRoomAndObjectText(
      CaseDraft draft, CaseFile caseFile, List<String> languages, Map<String, RoomDraft> rooms) {
    Map<String, ObjectDraft> objectsByName = new LinkedHashMap<>();
    for (RoomDraft room : rooms.values()) {
      for (ObjectDraft object : room.getObjects()) {
        objectsByName.put(object.getName(), object);
      }
    }
    for (String lang : languages) {
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc == null) {
        continue;
      }
      if (loc.getRoomDetails() != null) {
        for (CaseFile.RoomDetailData rd : loc.getRoomDetails()) {
          RoomDraft room = rd == null ? null : rooms.get(rd.getName());
          if (room != null) {
            room.displayNameText().set(lang, rd.getDisplayName());
            room.descriptionText().set(lang, rd.getDescription());
          }
        }
      }
      if (loc.getObjectDetails() != null) {
        for (CaseFile.ObjectDetailData od : loc.getObjectDetails()) {
          ObjectDraft object = od == null ? null : objectsByName.get(od.getName());
          if (object != null) {
            object.displayNameText().set(lang, od.getDisplayName());
            object.descriptionText().set(lang, od.getDescription());
            object.examineText().set(lang, od.getExamine());
            object.deduceText().set(lang, od.getDeduce());
          }
        }
      }
    }
  }

  // ---- Suspects ----------------------------------------------------------------------------

  private static void loadSuspects(
      CaseDraft draft,
      CaseFile caseFile,
      List<String> languages,
      String primary,
      Map<String, RoomDraft> rooms,
      Path caseDir) {
    CaseFile.LocalizedData primaryLoc = caseFile.getLocalizations().get(primary);
    if (primaryLoc == null || primaryLoc.getSuspects() == null) {
      return;
    }
    Map<String, SuspectDraft> byId = new LinkedHashMap<>();
    for (CaseFile.SuspectData data : primaryLoc.getSuspects()) {
      if (data == null || data.getName() == null) {
        continue;
      }
      SuspectDraft suspect = draft.addSuspect(data.getName());
      suspect.setId(data.getId());
      suspect.setImagePath(resolveImage(data.getImagePath(), caseDir));
      if (data.getImageScale() != null) {
        suspect.setImageScale(data.getImageScale()); // legacy uniform → both axes
      }
      if (data.getImageScaleX() != null) {
        suspect.setImageScaleX(data.getImageScaleX());
      }
      if (data.getImageScaleY() != null) {
        suspect.setImageScaleY(data.getImageScaleY());
      }
      suspect.setFlipX(Boolean.TRUE.equals(data.getFlipX()));
      suspect.setFlipY(Boolean.TRUE.equals(data.getFlipY()));
      if (data.getRotation() != null) {
        suspect.setRotation(data.getRotation());
      }
      suspect.setHomeRoom(rooms.get(data.getHomeRoom()));
      if (data.getPosX() != null && data.getPosY() != null) {
        suspect.setPosition(data.getPosX(), data.getPosY());
      }
      if (data.getLabelDX() != null && data.getLabelDY() != null) {
        suspect.setLabelOffset(data.getLabelDX(), data.getLabelDY());
      }
      suspect.setStationary(data.isStationary());
      suspect.setInitialState(data.getInitialState());
      // Build the state structure + contradictions from the primary language.
      if (data.getStates() != null) {
        data.getStates()
            .forEach(
                (stateName, stateData) -> {
                  SuspectStateDraft state = suspect.state(stateName);
                  if (stateData != null && stateData.getContradictions() != null) {
                    for (CaseFile.ContradictionRule rule : stateData.getContradictions()) {
                      ContradictionDraft c = state.addContradiction();
                      c.setEvidenceId(rule.getEvidenceId());
                      c.setNextState(rule.getNextState());
                      c.setRewardDeductionId(rule.getRewardDeductionId());
                    }
                  }
                });
      }
      byId.put(suspectId(data), suspect);
    }
    // Per-language statements and contradiction success messages.
    for (String lang : languages) {
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc == null || loc.getSuspects() == null) {
        continue;
      }
      for (CaseFile.SuspectData data : loc.getSuspects()) {
        SuspectDraft suspect = data == null ? null : byId.get(suspectId(data));
        if (suspect == null) {
          continue;
        }
        // Per-language Display Name (.scratch/gui-localized-case-names).
        suspect.displayNameText().set(lang, data.getDisplayName());
        // A simple (state-less) suspect carries its dialogue as a single per-language statement plus
        // an optional clue; preserve them so the export round-trip never drops a suspect's lines.
        suspect.statementText().set(lang, data.getStatement());
        suspect.clueText().set(lang, data.getClue());
        if (data.getStates() == null) {
          continue;
        }
        data.getStates()
            .forEach(
                (stateName, stateData) -> {
                  if (!suspect.getStates().containsKey(stateName.toUpperCase())
                      || stateData == null) {
                    return;
                  }
                  SuspectStateDraft state = suspect.state(stateName);
                  state.statementText().set(lang, stateData.getStatement());
                  if (stateData.getContradictions() != null) {
                    List<ContradictionDraft> drafts = state.getContradictions();
                    for (int i = 0;
                        i < drafts.size() && i < stateData.getContradictions().size();
                        i++) {
                      drafts
                          .get(i)
                          .successMessageText()
                          .set(lang, stateData.getContradictions().get(i).getSuccessMessage());
                    }
                  }
                });
      }
    }
  }

  // ---- Logic & content ---------------------------------------------------------------------

  private static void loadCombineRules(CaseDraft draft, CaseFile caseFile, List<String> languages) {
    if (caseFile.getCombineLogic() == null) {
      return;
    }
    for (CaseFile.CombineRule rule : caseFile.getCombineLogic()) {
      if (rule == null) {
        continue;
      }
      CombineRuleDraft draftRule = draft.addCombineRule();
      if (rule.getRequires() != null) {
        draftRule.setRequires(new ArrayList<>(rule.getRequires()));
      }
      draftRule.setResultDeductionId(rule.getResultDeductionId());
      draftRule.setTokenReward(rule.getTokenReward());
      draftRule.setRepeatable(rule.isRepeatable());
      if (rule.getResultText() != null) {
        rule.getResultText()
            .forEach((lang, text) -> draftRule.resultTextLocalized().set(lang, text));
      }
    }
  }

  private static void loadWatsonHints(CaseDraft draft, CaseFile caseFile, List<String> languages) {
    if (caseFile.getWatson() == null || caseFile.getWatson().getHints() == null) {
      return;
    }
    caseFile
        .getWatson()
        .getHints()
        .forEach(
            (category, hints) -> {
              if (hints == null) {
                return;
              }
              for (CaseFile.WatsonHint hint : hints) {
                if (hint == null) {
                  continue;
                }
                WatsonHintDraft draftHint = draft.addWatsonHint();
                draftHint.setCategory(category);
                draftHint.setId(hint.getId());
                if (hint.getText() != null) {
                  hint.getText().forEach((lang, text) -> draftHint.textLocalized().set(lang, text));
                }
              }
            });
  }

  private static void loadRankTiers(
      CaseDraft draft, CaseFile caseFile, List<String> languages, String primary) {
    CaseFile.LocalizedData primaryLoc = caseFile.getLocalizations().get(primary);
    if (primaryLoc == null || primaryLoc.getRankingTiers() == null) {
      return;
    }
    List<RankTierDraft> drafts = new ArrayList<>();
    for (CaseFile.RankTierData tier : primaryLoc.getRankingTiers()) {
      RankTierDraft draftTier = draft.addRankTier();
      draftTier.setRankName(tier.getRankName());
      draftTier.setMaxDeductions(tier.getMaxDeductions());
      draftTier.setDefaultRank(tier.isDefaultRank());
      drafts.add(draftTier);
    }
    for (String lang : languages) {
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc == null || loc.getRankingTiers() == null) {
        continue;
      }
      for (int i = 0; i < drafts.size() && i < loc.getRankingTiers().size(); i++) {
        CaseFile.RankTierData tier = loc.getRankingTiers().get(i);
        drafts.get(i).descriptionText().set(lang, tier.getDescription());
        drafts.get(i).winningStatementText().set(lang, tier.getWinningStatement());
      }
    }
  }

  private static void loadTasks(
      CaseDraft draft, CaseFile caseFile, List<String> languages, String primary) {
    CaseFile.LocalizedData primaryLoc = caseFile.getLocalizations().get(primary);
    if (primaryLoc == null || primaryLoc.getTasks() == null) {
      return;
    }
    for (String ignored : primaryLoc.getTasks()) {
      draft.addTask(null); // create the slots; text filled per language below
    }
    for (String lang : languages) {
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc == null || loc.getTasks() == null) {
        continue;
      }
      List<LocalizedText> taskTexts = draft.getTaskTexts();
      for (int i = 0; i < taskTexts.size() && i < loc.getTasks().size(); i++) {
        taskTexts.get(i).set(lang, loc.getTasks().get(i));
      }
    }
  }

  private static void loadFinalExam(
      CaseDraft draft, CaseFile caseFile, List<String> languages, String primary) {
    CaseFile.LocalizedData primaryLoc = caseFile.getLocalizations().get(primary);
    if (primaryLoc == null
        || primaryLoc.getFinalExam() == null
        || primaryLoc.getFinalExam().getQuestions() == null) {
      return;
    }
    List<FinalExamQuestionDraft> questionDrafts = new ArrayList<>();
    for (common.dto.FinalExamQuestionDTO question : primaryLoc.getFinalExam().getQuestions()) {
      FinalExamQuestionDraft qd = draft.addExamQuestion();
      questionDrafts.add(qd);
      if (question.getSlots() != null) {
        for (Map.Entry<String, common.dto.FinalExamSlotDTO> entry :
            question.getSlots().entrySet()) {
          ExamSlotDraft slot = qd.addSlot();
          slot.setSlotId(entry.getKey());
          Map<String, ExamChoiceDraft> choiceById = new LinkedHashMap<>();
          if (entry.getValue() != null && entry.getValue().getChoices() != null) {
            for (common.dto.FinalExamChoiceDTO choice : entry.getValue().getChoices()) {
              ExamChoiceDraft cd = slot.addChoice(choice.getChoiceId());
              choiceById.put(choice.getChoiceId(), cd);
            }
          }
          String correctId =
              question.getCorrectCombination() == null
                  ? null
                  : question.getCorrectCombination().get(entry.getKey());
          if (correctId != null && choiceById.containsKey(correctId)) {
            slot.setCorrectChoice(choiceById.get(correctId));
          }
        }
      }
    }
    // Per-language prompt + choice text.
    for (String lang : languages) {
      CaseFile.LocalizedData loc = caseFile.getLocalizations().get(lang);
      if (loc == null || loc.getFinalExam() == null || loc.getFinalExam().getQuestions() == null) {
        continue;
      }
      List<common.dto.FinalExamQuestionDTO> questions = loc.getFinalExam().getQuestions();
      for (int q = 0; q < questionDrafts.size() && q < questions.size(); q++) {
        FinalExamQuestionDraft qd = questionDrafts.get(q);
        common.dto.FinalExamQuestionDTO question = questions.get(q);
        qd.promptText().set(lang, question.getQuestionPrompt());
        if (question.getSlots() != null) {
          for (ExamSlotDraft slot : qd.getSlots()) {
            common.dto.FinalExamSlotDTO slotDto = question.getSlots().get(slot.getSlotId());
            if (slotDto == null || slotDto.getChoices() == null) {
              continue;
            }
            Map<String, String> textById = new LinkedHashMap<>();
            for (common.dto.FinalExamChoiceDTO choice : slotDto.getChoices()) {
              textById.put(choice.getChoiceId(), choice.getChoiceText());
            }
            for (ExamChoiceDraft choice : slot.getChoices()) {
              choice.textLocalized().set(lang, textById.get(choice.getChoiceId()));
            }
          }
        }
      }
    }
  }

  // ---- Helpers -----------------------------------------------------------------------------

  private static String suspectId(CaseFile.SuspectData data) {
    if (data.getId() != null && !data.getId().trim().isEmpty()) {
      return data.getId().trim();
    }
    return data.getName() == null ? "" : data.getName().toLowerCase().replace(" ", "_");
  }

  private static Path caseDirOf(CaseFile caseFile) {
    if (caseFile.getSourcePath() == null) {
      return null;
    }
    try {
      return Path.of(caseFile.getSourcePath()).getParent();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Resolves a relative asset path against the case directory to an absolute path (so the editor's
   * previews load it and a re-export copies it). Leaves blank, absolute, or unresolvable paths
   * as-is.
   */
  private static String resolveImage(String path, Path caseDir) {
    if (path == null || path.isBlank() || caseDir == null) {
      return path;
    }
    try {
      Path candidate = caseDir.resolve(path);
      if (Files.isRegularFile(candidate)) {
        return candidate.toString();
      }
    } catch (RuntimeException ignored) {
      // leave as-is
    }
    return path;
  }
}

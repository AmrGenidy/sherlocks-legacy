// PASTE THIS, REPLACING THE ENTIRE CONTENTS of src/main/java/JsonDTO/CaseFile.java

package JsonDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.FinalExamDTO;
import java.util.List;
import java.util.Map;

public class CaseFile {

  @JsonProperty("universal_title")
  private String universalTitle;

  private Metadata metadata;
  private String startingRoom;
  private List<RoomData> rooms;
  private Map<String, LocalizedData> localizations;

  @JsonProperty("startingInsightTokens")
  private Integer startingInsightTokens; // NEW (Optional)

  @JsonProperty("combine_logic")
  private List<CombineRule> combineLogic;

  private WatsonMetadata watson; // NEW

  @JsonProperty("red_herrings")
  private RedHerringMetadata redHerrings; // NEW

  private String sourcePath; // NEW for debugging

  @JsonProperty("case_file")
  private CaseFileBlock caseFile; // NEW: Case File System

  // Getters
  public CaseFileBlock getCaseFile() {
    return caseFile;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public void setSourcePath(String sourcePath) {
    this.sourcePath = sourcePath;
  }

  public String getUniversalTitle() {
    return universalTitle;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public String getStartingRoom() {
    return startingRoom;
  }

  public List<RoomData> getRooms() {
    return rooms;
  }

  public Map<String, LocalizedData> getLocalizations() {
    return localizations;
  }

  public Integer getStartingInsightTokens() {
    return startingInsightTokens;
  }

  public void setStartingInsightTokens(Integer startingInsightTokens) {
    this.startingInsightTokens = startingInsightTokens;
  } // NEW

  public List<CombineRule> getCombineLogic() {
    return combineLogic;
  }

  public WatsonMetadata getWatson() {
    return watson;
  }

  public RedHerringMetadata getRedHerrings() {
    return redHerrings;
  }

  // --- NESTED CLASSES ---
  // Made classes PUBLIC to be accessible from LocalizedCaseFile

  public static class Metadata {
    public String title;
    public String author;
    public String watsonImagePath;
    // Dr. Watson's sprite scale (Case Maker placement tab). Optional; null means the engine's
    // default sizing (match the room's other suspects). Multiplies the base sprite size in
    // RoomView.
    public Double watsonImageScale; // legacy uniform; fallback when watsonImageScaleX/Y are absent
    // Watson's independent scale + mirror flags (global; null/false = default).
    public Double watsonImageScaleX;
    public Double watsonImageScaleY;
    public Boolean watsonFlipX;
    public Boolean watsonFlipY;
    // Dr. Watson's clockwise sprite rotation in degrees (global; Case Maker placement rotation
    // grips). Null/0 = upright.
    public Double watsonRotation;
    // Watson's name-label offset from his sprite centre (fraction of sprite height); null =
    // default.
    public Double watsonLabelDX;
    public Double watsonLabelDY;

    // Optional looped ambient soundtrack for the case. Resolved like image paths (classpath → case
    // dir via ResourceResolver); absent/unresolvable → silent fallback. See per-case-soundtrack
    // PRD.
    public String soundtrack;

    // The lead detective (the player) and the helper (the assistant asked via `ask`). A single name
    // reused across all languages; absent → the engine's defaults (Sherlock Holmes / Dr. Watson) so
    // existing cases are unaffected. These free authors from the Sherlock Holmes framing.
    public String detectiveName;
    public String helperName;

    public String getTitle() {
      return title;
    }

    public String getAuthor() {
      return author;
    }

    public String getWatsonImagePath() {
      return watsonImagePath;
    }

    public Double getWatsonImageScale() {
      return watsonImageScale;
    }

    public Double getWatsonImageScaleX() {
      return watsonImageScaleX;
    }

    public Double getWatsonImageScaleY() {
      return watsonImageScaleY;
    }

    public Boolean getWatsonFlipX() {
      return watsonFlipX;
    }

    public Boolean getWatsonFlipY() {
      return watsonFlipY;
    }

    public Double getWatsonRotation() {
      return watsonRotation;
    }

    public Double getWatsonLabelDX() {
      return watsonLabelDX;
    }

    public Double getWatsonLabelDY() {
      return watsonLabelDY;
    }

    public String getSoundtrack() {
      return soundtrack;
    }

    public String getDetectiveName() {
      return detectiveName;
    }

    public String getHelperName() {
      return helperName;
    }
  }

  public static class RoomData {
    public String name; // Public for merging
    public String description; // Public for merging
    public Map<String, String> neighbors; // Public for merging
    public List<GameObjectData> objects; // Public for merging
    public String imagePath; // NEW (optional)
    // Per-language Display Name (.scratch/gui-localized-case-names). Absent at the universal level;
    // populated by LocalizedCaseFile from roomDetails[].displayName during the merge.
    public String displayName;
    // Per-room Dr. Watson sprite position (normalized 0–1). Watson follows the player, so each room
    // he can appear in stores its own spot; null means "use RoomView's default". Universal fields.
    public Double watsonPosX;
    public Double watsonPosY;
    // Per-room Dr. Watson sprite size/orientation. Each is optional; null means "fall back to the
    // global metadata.watson* value" (so a case that only sets the global size is unchanged). This
    // lets an author scale/flip/rotate Watson differently per room to match each room's perspective.
    public Double watsonImageScaleX;
    public Double watsonImageScaleY;
    public Boolean watsonFlipX;
    public Boolean watsonFlipY;
    public Double watsonRotation;
    public Double watsonLabelDX;
    public Double watsonLabelDY;

    public String getName() {
      return name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getDescription() {
      return description;
    }

    public Map<String, String> getNeighbors() {
      return neighbors;
    }

    public List<GameObjectData> getObjects() {
      return objects;
    }

    public String getImagePath() {
      return imagePath;
    }

    public Double getWatsonPosX() {
      return watsonPosX;
    }

    public Double getWatsonPosY() {
      return watsonPosY;
    }

    public Double getWatsonImageScaleX() {
      return watsonImageScaleX;
    }

    public Double getWatsonImageScaleY() {
      return watsonImageScaleY;
    }

    public Boolean getWatsonFlipX() {
      return watsonFlipX;
    }

    public Boolean getWatsonFlipY() {
      return watsonFlipY;
    }

    public Double getWatsonRotation() {
      return watsonRotation;
    }

    public Double getWatsonLabelDX() {
      return watsonLabelDX;
    }

    public Double getWatsonLabelDY() {
      return watsonLabelDY;
    }
  }

  // Renamed from ObjectStub for clarity, matches new structure better
  public static class GameObjectData {
    public String id; // <-- Added ID
    public String name;
    // Per-language Display Name (.scratch/gui-localized-case-names). Absent at the universal level;
    // populated by LocalizedCaseFile from objectDetails[].displayName during the merge.
    public String displayName;
    public String description;
    public String examine;
    public String deduce;
    public String imagePath; // NEW (optional)
    public Double posX; // NEW (optional)
    public Double posY; // NEW (optional)
    public Double
        imageScale; // legacy uniform scale; read as a fallback when imageScaleX/Y are absent
    // NEW (optional): independent horizontal/vertical sprite scale + mirror flags. Null/false =
    // default (imageScaleX/Y fall back to imageScale, then 1.0).
    public Double imageScaleX;
    public Double imageScaleY;
    public Boolean flipX;
    public Boolean flipY;
    // NEW (optional): clockwise sprite rotation in degrees about the sprite centre (Case Maker
    // placement rotation grips). Null/0 = upright.
    public Double rotation;
    // NEW (optional): name-label offset from the sprite centre, as a fraction of sprite height.
    // Null = the default "just below the sprite" position (RoomView fallback).
    public Double labelDX;
    public Double labelDY;

    public String getId() {
      return id;
    } // <-- Added Getter

    public String getName() {
      return name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getDescription() {
      return description;
    }

    public String getExamine() {
      return examine;
    }

    public String getDeduce() {
      return deduce;
    }

    public String getImagePath() {
      return imagePath;
    }

    public Double getPosX() {
      return posX;
    }

    public Double getPosY() {
      return posY;
    }

    public Double getImageScale() {
      return imageScale;
    }

    public Double getImageScaleX() {
      return imageScaleX;
    }

    public Double getImageScaleY() {
      return imageScaleY;
    }

    public Boolean getFlipX() {
      return flipX;
    }

    public Boolean getFlipY() {
      return flipY;
    }

    public Double getRotation() {
      return rotation;
    }

    public Double getLabelDX() {
      return labelDX;
    }

    public Double getLabelDY() {
      return labelDY;
    }
  }

  public static class LocalizedData {
    public String languageName;
    public String title;
    public String invitation;
    public String description;
    // Per-language override for the detective/helper names. Absent → the single metadata value is
    // used (which itself falls back to Sherlock Holmes / Dr. Watson). Lets a case write e.g. the
    // assistant's name in Arabic script for the Arabic playthrough.
    public String detectiveName;
    public String helperName;
    public List<SuspectData> suspects;
    public List<RoomDetailData> roomDetails;
    public List<ObjectDetailData> objectDetails;

    @JsonProperty("final_exam")
    public FinalExamDTO finalExam;

    public List<String> tasks;

    /**
     * @deprecated Retired flat per-localization Watson hint pool
     *     (.scratch/gui-localized-watson-hints). Hint text now lives solely in the structured,
     *     per-language {@code watson.hints} block. This field is kept inert so legacy/external case
     *     JSON still parses; nothing reads it.
     */
    @Deprecated public List<String> watsonHints;

    public List<RankTierData> rankingTiers;

    @JsonProperty("winning_message")
    public String winningMessage;

    // Getters
    public String getLanguageName() {
      return languageName;
    }

    public String getTitle() {
      return title;
    }

    public String getInvitation() {
      return invitation;
    }

    public String getDescription() {
      return description;
    }

    public String getDetectiveName() {
      return detectiveName;
    }

    public String getHelperName() {
      return helperName;
    }

    public List<SuspectData> getSuspects() {
      return suspects;
    }

    public List<RoomDetailData> getRoomDetails() {
      return roomDetails;
    }

    public List<ObjectDetailData> getObjectDetails() {
      return objectDetails;
    }

    public FinalExamDTO getFinalExam() {
      return finalExam;
    }

    public List<String> getTasks() {
      return tasks;
    }

    /**
     * @deprecated See {@link #watsonHints}. Inert; retained only for parse-compatibility.
     */
    @Deprecated
    public List<String> getWatsonHints() {
      return watsonHints;
    }

    public List<RankTierData> getRankingTiers() {
      return rankingTiers;
    }

    public String getWinningMessage() {
      return winningMessage;
    }
  }

  public static class CombineRule {
    public List<String> requires;
    public String resultDeductionId;
    public Map<String, String> resultText; // Localized text map
    public Integer tokenReward;
    public boolean repeatable;

    public List<String> getRequires() {
      return requires;
    }

    public String getResultDeductionId() {
      return resultDeductionId;
    }

    public Map<String, String> getResultText() {
      return resultText;
    }

    public Integer getTokenReward() {
      return tokenReward != null ? tokenReward : 1;
    }

    public boolean isRepeatable() {
      return repeatable;
    }
  }

  public static class SuspectData {
    public String id; // <-- Added ID
    public String name;
    // Per-language Display Name (.scratch/gui-localized-case-names). Suspects are stored per-
    // language, so this is read straight off the localized block (no merge needed).
    public String displayName;
    public String statement;
    public String clue;
    public String image;
    public String imagePath; // NEW (optional)
    public Double
        imageScale; // legacy uniform scale; read as a fallback when imageScaleX/Y are absent
    // NEW (optional): independent horizontal/vertical sprite scale + mirror flags.
    public Double imageScaleX;
    public Double imageScaleY;
    public Boolean flipX;
    public Boolean flipY;
    // Clockwise sprite rotation in degrees about the sprite centre (Case Maker placement rotation
    // grips). Null/0 = upright.
    public Double rotation;

    // Suspect placement (Case Maker slice 3, DEC-2/DEC-5). Authored on the suspect (language-
    // independent values, stored per-language for schema consistency — see DEC-9's cross-language
    // check). homeRoom is a universal room name; posX/posY are normalized [0,1]; stationary gates
    // wandering (true = stays in the home room, false = preserves the historical random wander).
    public String homeRoom;
    public Double posX;
    public Double posY;
    public boolean stationary;
    // NEW (optional): name-label offset from the sprite centre, as a fraction of sprite height.
    // Null = the default "just below the sprite" position (RoomView fallback).
    public Double labelDX;
    public Double labelDY;

    // NEW: State Machine Support
    public String initialState;
    public Map<String, SuspectStateData> states;

    public String getId() {
      return id;
    } // <-- Added Getter

    public String getName() {
      return name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getStatement() {
      return statement;
    }

    public String getClue() {
      return clue;
    }

    public String getImage() {
      return image;
    }

    public String getImagePath() {
      return imagePath;
    }

    public Double getImageScale() {
      return imageScale;
    }

    public Double getImageScaleX() {
      return imageScaleX;
    }

    public Double getImageScaleY() {
      return imageScaleY;
    }

    public Boolean getFlipX() {
      return flipX;
    }

    public Boolean getFlipY() {
      return flipY;
    }

    public Double getRotation() {
      return rotation;
    }

    public String getHomeRoom() {
      return homeRoom;
    }

    public Double getPosX() {
      return posX;
    }

    public Double getPosY() {
      return posY;
    }

    public boolean isStationary() {
      return stationary;
    }

    public Double getLabelDX() {
      return labelDX;
    }

    public Double getLabelDY() {
      return labelDY;
    }

    public String getInitialState() {
      return initialState;
    }

    public Map<String, SuspectStateData> getStates() {
      return states;
    }
  }

  public static class SuspectStateData {
    public String statement;
    public List<ContradictionRule> contradictions;

    public String getStatement() {
      return statement;
    }

    public List<ContradictionRule> getContradictions() {
      return contradictions;
    }
  }

  public static class ContradictionRule {
    public String evidenceId;
    public String nextState;
    public String rewardDeductionId;
    public String successMessage;

    public String getEvidenceId() {
      return evidenceId;
    }

    public String getNextState() {
      return nextState;
    }

    public String getRewardDeductionId() {
      return rewardDeductionId;
    }

    public String getSuccessMessage() {
      return successMessage;
    }
  }

  public static class RoomDetailData {
    public String name;
    // Per-language Display Name (.scratch/gui-localized-case-names); optional, falls back to name.
    public String displayName;
    public String description;

    public String getName() {
      return name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getDescription() {
      return description;
    }
  }

  public static class ObjectDetailData {
    public String name;
    // Per-language Display Name (.scratch/gui-localized-case-names); optional, falls back to name.
    public String displayName;
    public String description;
    public String examine;
    public String deduce;

    public String getName() {
      return name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getDescription() {
      return description;
    }

    public String getExamine() {
      return examine;
    }

    public String getDeduce() {
      return deduce;
    }
  }

  public static class RankTierData {
    public String rankName;
    public int maxDeductions;
    public String description;
    public boolean defaultRank;
    public String winningStatement; // NEW

    public String getRankName() {
      return rankName;
    }

    public int getMaxDeductions() {
      return maxDeductions;
    }

    public String getDescription() {
      return description;
    }

    public String getWinningStatement() {
      return winningStatement;
    }

    public boolean isDefaultRank() {
      return defaultRank;
    }
  }

  // --- NEW WATSON METADATA ---
  public static class WatsonMetadata {
    public Map<String, List<WatsonHint>> hints;

    public Map<String, List<WatsonHint>> getHints() {
      return hints;
    }
  }

  public static class WatsonHint {
    public String id;
    public Map<String, String> text;

    public String getId() {
      return id;
    }

    public Map<String, String> getText() {
      return text;
    }
  }

  // --- NEW RED HERRING METADATA ---
  public static class RedHerringMetadata {
    public Map<String, RedHerringDetail> objects;
    public Map<String, RedHerringDetail> suspects;

    public Map<String, RedHerringDetail> getObjects() {
      return objects;
    }

    public Map<String, RedHerringDetail> getSuspects() {
      return suspects;
    }
  }

  public static class RedHerringDetail {
    @JsonProperty("is_red_herring")
    public boolean isRedHerring;

    @JsonProperty("recoverable_by")
    public String recoverableBy;

    public Map<String, String> narrative; // NEW: Narrative override

    @JsonProperty("is_red_herring")
    public boolean isRedHerring() {
      return isRedHerring;
    }

    public String getRecoverableBy() {
      return recoverableBy;
    }

    public Map<String, String> getNarrative() {
      return narrative;
    }
  }

  // --- NEW CASE FILE SYSTEM METADATA ---
  public static class CaseFileBlock {
    public VictimData victim;
    public Map<String, String> overview;

    @JsonProperty("suspect_profiles")
    public Map<String, SuspectProfileData> suspectProfiles;

    public VictimData getVictim() {
      return victim;
    }

    public Map<String, String> getOverview() {
      return overview;
    }

    public Map<String, SuspectProfileData> getSuspectProfiles() {
      return suspectProfiles;
    }
  }

  public static class VictimData {
    public String name;

    @JsonProperty("relation_to_case")
    public String relationToCase;

    public Map<String, String> notes;

    public String getName() {
      return name;
    }

    public String getRelationToCase() {
      return relationToCase;
    }

    public Map<String, String> getNotes() {
      return notes;
    }
  }

  public static class SuspectProfileData {
    public Map<String, String> profession;
    public Integer age;

    @JsonProperty("height_cm")
    public Integer heightCm;

    @JsonProperty("weight_kg")
    public Integer weightKg;

    @JsonProperty("relationship_to_victim")
    public Map<String, String> relationshipToVictim;

    public Map<String, String> bio;
    public String imagePath;

    public Map<String, String> getProfession() {
      return profession;
    }

    public Integer getAge() {
      return age;
    }

    public Integer getHeightCm() {
      return heightCm;
    }

    public Integer getWeightKg() {
      return weightKg;
    }

    public Map<String, String> getRelationshipToVictim() {
      return relationshipToVictim;
    }

    public Map<String, String> getBio() {
      return bio;
    }

    public String getImagePath() {
      return imagePath;
    }
  }
}

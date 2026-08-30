package JsonDTO;

import common.dto.FinalExamDTO;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An adapter class that represents a single-language version of a CaseFile. It's constructed from a
 * multilingual CaseFile DTO and a specific language code. It provides the exact same getters as the
 * OLD CaseFile DTO, so the rest of the game engine (Extractors, Contexts) doesn't need to be
 * changed.
 */
public class LocalizedCaseFile implements CaseData {
  private static final Logger logger = LoggerFactory.getLogger(LocalizedCaseFile.class);

  // These fields mirror the OLD CaseFile structure
  private String title;
  private String invitation;
  private String description;
  private String watsonImagePath; // NEW
  private double watsonImageScale = 1.0; // NEW (metadata.watsonImageScale; 1.0 = engine default)
  private double watsonImageScaleX = 1.0; // NEW (metadata.watsonImageScaleX; falls back to uniform)
  private double watsonImageScaleY = 1.0; // NEW (metadata.watsonImageScaleY; falls back to uniform)
  private boolean watsonFlipX; // NEW (metadata.watsonFlipX)
  private boolean watsonFlipY; // NEW (metadata.watsonFlipY)
  private double watsonRotation; // NEW (metadata.watsonRotation; degrees clockwise, 0 = upright)
  private Double watsonLabelDX; // NEW (metadata.watsonLabelDX; null = RoomView default)
  private Double watsonLabelDY; // NEW (metadata.watsonLabelDY; null = RoomView default)
  private String startingRoom;
  private List<CaseFile.SuspectData> suspects;
  private List<CaseFile.RoomData> rooms;
  private FinalExamDTO finalExam;
  private List<String> tasks;
  private List<CaseFile.RankTierData> rankingTiers;
  private String winningMessage;
  private Integer startingInsightTokens;
  private List<CaseFile.CombineRule> combineLogic;
  private CaseFile.RedHerringMetadata redHerrings; // NEW
  // Category -> List of Localized Hints
  private Map<String, List<LocalizedWatsonHint>> structuredWatsonHints; // NEW
  private String languageCode; // NEW
  private LocalizedCaseFileBlock caseFile; // NEW: Case File System
  // Author-defined character names (single string across all languages). Defaults keep the
  // Sherlock Holmes framing for cases that don't author their own.
  private String detectiveName = "Sherlock Holmes";
  private String helperName = "Dr. Watson";

  public static class LocalizedWatsonHint {
    public String id;
    public String text;

    public LocalizedWatsonHint(String id, String text) {
      this.id = id;
      this.text = text;
    }

    public String getId() {
      return id;
    }

    public String getText() {
      return text;
    }
  }

  /**
   * Constructs a single-language case file from a multilingual source.
   *
   * @param multiLingualCase The fully parsed CaseFile DTO containing all languages.
   * @param languageCode The language to extract (e.g., "en", "es").
   */
  public LocalizedCaseFile(CaseFile multiLingualCase, String languageCode) {
    this.languageCode = languageCode;
    CaseFile.LocalizedData locData = multiLingualCase.getLocalizations().get(languageCode);
    if (locData == null) {
      // Fallback to the first available language if the chosen one doesn't exist
      String fallbackCode = multiLingualCase.getLocalizations().keySet().iterator().next();
      locData = multiLingualCase.getLocalizations().get(fallbackCode);
      logger.warn(
          "Warning: Language '{}' not found. Falling back to '{}'.", languageCode, fallbackCode);
    }

    // 1. Populate simple text fields
    this.title = locData.getTitle();
    this.invitation = locData.getInvitation();
    this.description = locData.getDescription();
    this.finalExam = locData.getFinalExam();
    this.tasks = locData.getTasks();
    this.rankingTiers = locData.getRankingTiers();
    this.winningMessage = locData.getWinningMessage();

    // 2. Populate fields from the top-level structure
    this.startingRoom = multiLingualCase.getStartingRoom();
    if (multiLingualCase.getMetadata() != null) {
      CaseFile.Metadata meta = multiLingualCase.getMetadata();
      this.watsonImagePath = meta.getWatsonImagePath();
      if (meta.getWatsonImageScale() != null) {
        this.watsonImageScale = meta.getWatsonImageScale();
      }
      // Independent scale falls back to the legacy uniform value, then to 1.0.
      this.watsonImageScaleX =
          meta.getWatsonImageScaleX() != null ? meta.getWatsonImageScaleX() : this.watsonImageScale;
      this.watsonImageScaleY =
          meta.getWatsonImageScaleY() != null ? meta.getWatsonImageScaleY() : this.watsonImageScale;
      this.watsonFlipX = Boolean.TRUE.equals(meta.getWatsonFlipX());
      this.watsonFlipY = Boolean.TRUE.equals(meta.getWatsonFlipY());
      if (meta.getWatsonRotation() != null) {
        this.watsonRotation = meta.getWatsonRotation();
      }
      this.watsonLabelDX = meta.getWatsonLabelDX();
      this.watsonLabelDY = meta.getWatsonLabelDY();
      // Single metadata value first (the default / all-languages name)...
      if (meta.getDetectiveName() != null && !meta.getDetectiveName().isBlank()) {
        this.detectiveName = meta.getDetectiveName();
      }
      if (meta.getHelperName() != null && !meta.getHelperName().isBlank()) {
        this.helperName = meta.getHelperName();
      }
    }
    // ...then the per-language override wins when the case authored one for this language.
    if (locData.getDetectiveName() != null && !locData.getDetectiveName().isBlank()) {
      this.detectiveName = locData.getDetectiveName();
    }
    if (locData.getHelperName() != null && !locData.getHelperName().isBlank()) {
      this.helperName = locData.getHelperName();
    }
    this.startingInsightTokens =
        multiLingualCase.getStartingInsightTokens() != null
            ? multiLingualCase.getStartingInsightTokens()
            : 0;
    if (this.startingInsightTokens != 0) {
      logger.debug(
          "LocalizedCaseFile init. Source tokens={}, Localized tokens={}, Source Path={}",
          multiLingualCase.getStartingInsightTokens(),
          this.startingInsightTokens,
          multiLingualCase.getSourcePath());
    }
    this.combineLogic = multiLingualCase.getCombineLogic();
    this.suspects = locData.getSuspects(); // Suspects are fully defined in localized data
    this.redHerrings = multiLingualCase.getRedHerrings();

    // 2b. Populate Structured Watson Hints
    if (multiLingualCase.getWatson() != null && multiLingualCase.getWatson().getHints() != null) {
      this.structuredWatsonHints =
          multiLingualCase.getWatson().getHints().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      entry ->
                          entry.getValue().stream()
                              .map(
                                  hint -> {
                                    String txt = hint.getText().get(languageCode);
                                    if (txt == null) txt = hint.getText().get("en"); // Fallback
                                    if (txt == null && !hint.getText().isEmpty())
                                      txt = hint.getText().values().iterator().next();
                                    return new LocalizedWatsonHint(hint.getId(), txt);
                                  })
                              .filter(h -> h.text != null)
                              .collect(Collectors.toList())));
    }

    // 3. Merge logical room structure with localized text details
    Map<String, String> roomDescriptions =
        locData.getRoomDetails().stream()
            .collect(
                Collectors.toMap(
                    CaseFile.RoomDetailData::getName, CaseFile.RoomDetailData::getDescription));

    // Per-language room Display Names, keyed by Universal Name (.scratch/gui-localized-case-names).
    Map<String, CaseFile.RoomDetailData> roomDetailsByName =
        locData.getRoomDetails().stream()
            .collect(Collectors.toMap(CaseFile.RoomDetailData::getName, detail -> detail));

    Map<String, CaseFile.ObjectDetailData> objectDetails =
        locData.getObjectDetails().stream()
            .collect(Collectors.toMap(CaseFile.ObjectDetailData::getName, detail -> detail));

    this.rooms =
        multiLingualCase.getRooms().stream()
            .map(
                logicalRoom -> {
                  // Create a new RoomData that will hold the merged info
                  CaseFile.RoomData localizedRoom = new CaseFile.RoomData();
                  localizedRoom.name = logicalRoom.getName();
                  localizedRoom.neighbors = logicalRoom.getNeighbors();
                  localizedRoom.imagePath = logicalRoom.getImagePath(); // Copy image path
                  // Per-room Watson position (universal); carry it through the merge.
                  localizedRoom.watsonPosX = logicalRoom.getWatsonPosX();
                  localizedRoom.watsonPosY = logicalRoom.getWatsonPosY();
                  // Per-room Watson size/orientation (universal); carry through so the engine can
                  // apply a room-specific Watson scale/flip/rotation (fallback to global metadata).
                  localizedRoom.watsonImageScaleX = logicalRoom.getWatsonImageScaleX();
                  localizedRoom.watsonImageScaleY = logicalRoom.getWatsonImageScaleY();
                  localizedRoom.watsonFlipX = logicalRoom.getWatsonFlipX();
                  localizedRoom.watsonFlipY = logicalRoom.getWatsonFlipY();
                  localizedRoom.watsonRotation = logicalRoom.getWatsonRotation();
                  localizedRoom.watsonLabelDX = logicalRoom.getWatsonLabelDX();
                  localizedRoom.watsonLabelDY = logicalRoom.getWatsonLabelDY();
                  // Get the description from our localized map
                  localizedRoom.description =
                      roomDescriptions.getOrDefault(logicalRoom.getName(), "A non-descript room.");
                  // Carry the per-language room Display Name through the merge (may be null).
                  CaseFile.RoomDetailData roomDetail = roomDetailsByName.get(logicalRoom.getName());
                  if (roomDetail != null) {
                    localizedRoom.displayName = roomDetail.getDisplayName();
                  }

                  // Now merge the object details
                  if (logicalRoom.getObjects() != null) {
                    localizedRoom.objects =
                        logicalRoom.getObjects().stream()
                            .map(
                                objectStub -> {
                                  CaseFile.ObjectDetailData details =
                                      objectDetails.get(objectStub.getName());
                                  // Create the full object data DTO the extractors expect
                                  CaseFile.GameObjectData fullObject =
                                      new CaseFile.GameObjectData();
                                  fullObject.name = objectStub.getName();
                                  fullObject.id = objectStub.getId(); // Copy ID
                                  fullObject.imagePath =
                                      objectStub.getImagePath(); // Copy image path
                                  fullObject.posX = objectStub.getPosX(); // Copy Position
                                  fullObject.posY = objectStub.getPosY();
                                  fullObject.labelDX = objectStub.getLabelDX(); // Copy label offset
                                  fullObject.labelDY = objectStub.getLabelDY();
                                  fullObject.imageScale =
                                      objectStub
                                          .getImageScale(); // DEC-4: carry sprite scale through the
                                  // merge
                                  fullObject.imageScaleX = objectStub.getImageScaleX();
                                  fullObject.imageScaleY = objectStub.getImageScaleY();
                                  fullObject.flipX = objectStub.getFlipX();
                                  fullObject.flipY = objectStub.getFlipY();
                                  fullObject.rotation = objectStub.getRotation();
                                  if (details != null) {
                                    // Per-language object Display Name (may be null -> falls back).
                                    fullObject.displayName = details.getDisplayName();
                                    fullObject.description = details.getDescription();
                                    fullObject.examine = details.getExamine();
                                    fullObject.deduce = details.getDeduce();
                                  }
                                  return fullObject;
                                })
                            .collect(Collectors.toList());
                  }
                  return localizedRoom;
                })
            .collect(Collectors.toList());

    // 4. Parse Case File System Block
    if (multiLingualCase.getCaseFile() != null) {
      this.caseFile =
          new LocalizedCaseFileBlock(
              multiLingualCase.getCaseFile(), languageCode, locData.getDescription());
    }
  }

  // GETTERS (These must match the getters from your OLD CaseFile DTO)
  public String getTitle() {
    return title;
  }

  public String getInvitation() {
    return invitation;
  }

  public String getDescription() {
    if (caseFile != null && caseFile.getOverview() != null && !caseFile.getOverview().isBlank()) {
      return caseFile.getOverview();
    }
    return description;
  }

  public String getWatsonImagePath() {
    return watsonImagePath;
  }

  @Override
  public double getWatsonImageScale() {
    return watsonImageScale;
  }

  @Override
  public double getWatsonImageScaleX() {
    return watsonImageScaleX;
  }

  @Override
  public double getWatsonImageScaleY() {
    return watsonImageScaleY;
  }

  @Override
  public boolean isWatsonFlipX() {
    return watsonFlipX;
  }

  @Override
  public boolean isWatsonFlipY() {
    return watsonFlipY;
  }

  @Override
  public double getWatsonRotation() {
    return watsonRotation;
  }

  @Override
  public Double getWatsonLabelDX() {
    return watsonLabelDX;
  }

  @Override
  public Double getWatsonLabelDY() {
    return watsonLabelDY;
  }

  public String getStartingRoom() {
    return startingRoom;
  }

  public List<CaseFile.SuspectData> getSuspects() {
    return suspects;
  }

  public List<CaseFile.RoomData> getRooms() {
    return rooms;
  }

  public FinalExamDTO getFinalExam() {
    return finalExam;
  }

  public List<String> getTasks() {
    return tasks;
  }

  public List<CaseFile.RankTierData> getRankingTiers() {
    return rankingTiers;
  }

  public String getWinningMessage() {
    return winningMessage;
  }

  @Override
  public Integer getStartingInsightTokens() {
    // Delegate to the wrapped multilingual case file since this is a top-level
    // shared property
    // The 'multiLingualCase' local variable is not stored in the class, so we need
    // to either store it or pass it.
    // WAIT: The constructor does NOT save multiLingualCase. I need to check if I
    // can access it or if I need to save it.
    // Looking at the file content in Step 75, 'multiLingualCase' is ONLY in the
    // constructor.
    // I must add a field to store 'startingInsightTokens' in LocalizedCaseFile and
    // populate it in constructor.
    return startingInsightTokens;
  }

  @Override
  public List<CaseFile.CombineRule> getCombineLogic() {
    return combineLogic;
  }

  public Map<String, List<LocalizedWatsonHint>> getStructuredWatsonHints() {
    return structuredWatsonHints;
  }

  public CaseFile.RedHerringMetadata getRedHerrings() {
    return redHerrings;
  }

  @Override
  public String getLanguageCode() {
    return languageCode;
  }

  @Override
  public String getDetectiveName() {
    return detectiveName;
  }

  @Override
  public String getHelperName() {
    return helperName;
  }

  @Override
  public LocalizedCaseFileBlock getCaseFile() {
    return caseFile;
  }

  // --- NEW CASE FILE SYSTEM LOCALIZED BLOCKS ---

  public static class LocalizedCaseFileBlock {
    private LocalizedVictimData victim;
    private String overview;
    private Map<String, LocalizedSuspectProfileData> suspectProfiles;

    public LocalizedCaseFileBlock(
        CaseFile.CaseFileBlock multiLingualBlock, String languageCode, String fallbackDescription) {
      if (multiLingualBlock.getVictim() != null) {
        this.victim = new LocalizedVictimData(multiLingualBlock.getVictim(), languageCode);
      }

      this.overview = getLocalizedText(multiLingualBlock.getOverview(), languageCode);
      if (this.overview == null) {
        this.overview = fallbackDescription; // Graceful fallback
      }

      if (multiLingualBlock.getSuspectProfiles() != null) {
        this.suspectProfiles =
            multiLingualBlock.getSuspectProfiles().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new LocalizedSuspectProfileData(entry.getValue(), languageCode)));
      }
    }

    public LocalizedVictimData getVictim() {
      return victim;
    }

    public String getOverview() {
      return overview;
    }

    public Map<String, LocalizedSuspectProfileData> getSuspectProfiles() {
      return suspectProfiles;
    }
  }

  public static class LocalizedVictimData {
    private String name;
    private String relationToCase;
    private String notes;

    public LocalizedVictimData(CaseFile.VictimData multiLingualVictim, String languageCode) {
      this.name = multiLingualVictim.getName();
      this.relationToCase = multiLingualVictim.getRelationToCase();
      this.notes = getLocalizedText(multiLingualVictim.getNotes(), languageCode);
    }

    public String getName() {
      return name;
    }

    public String getRelationToCase() {
      return relationToCase;
    }

    public String getNotes() {
      return notes;
    }
  }

  public static class LocalizedSuspectProfileData {
    private String profession;
    private Integer age;
    private Integer heightCm;
    private Integer weightKg;
    private String relationshipToVictim;
    private String bio;
    private String imagePath;

    public LocalizedSuspectProfileData(
        CaseFile.SuspectProfileData multiLingualProfile, String languageCode) {
      this.profession = getLocalizedText(multiLingualProfile.getProfession(), languageCode);
      this.age = multiLingualProfile.getAge();
      this.heightCm = multiLingualProfile.getHeightCm();
      this.weightKg = multiLingualProfile.getWeightKg();
      this.relationshipToVictim =
          getLocalizedText(multiLingualProfile.getRelationshipToVictim(), languageCode);
      this.bio = getLocalizedText(multiLingualProfile.getBio(), languageCode);
      this.imagePath = multiLingualProfile.getImagePath();
    }

    public String getProfession() {
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

    public String getRelationshipToVictim() {
      return relationshipToVictim;
    }

    public String getBio() {
      return bio;
    }

    public String getImagePath() {
      return imagePath;
    }
  }

  private static String getLocalizedText(Map<String, String> translationMap, String languageCode) {
    if (translationMap == null || translationMap.isEmpty()) return null;
    if (translationMap.containsKey(languageCode)) return translationMap.get(languageCode);
    if (translationMap.containsKey("en")) return translationMap.get("en");
    return translationMap.values().iterator().next(); // fallback to first available
  }
}

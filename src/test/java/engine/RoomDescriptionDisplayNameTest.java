package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.commands.LookCommand;
import common.dto.RoomDescriptionDTO;
import java.io.Serializable;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The Universal-Name vs Display-Name contract at the engine seam (.scratch/gui-localized-case-names),
 * run against BOTH GameActionContext implementations.
 *
 * <p>{@code buildRoomDescription} must carry the per-language Display Name to the GUI WITHOUT
 * disturbing the Universal Names that commands, autocomplete and the terminal use:
 *
 * <ul>
 *   <li>the room's own Display Name rides alongside its Universal Name;
 *   <li>object/occupant Display Names ride in side maps keyed by Universal Name (the lists
 *       themselves stay Universal);
 *   <li>exit VALUES become the neighbour's Display Name (exits are display-only; movement is by the
 *       direction key);
 *   <li>an element with no authored Display Name is simply absent from the side map, so a GUI
 *       {@code getOrDefault(name, name)} falls back to the Universal Name.
 * </ul>
 */
@RunWith(Parameterized.class)
public class RoomDescriptionDisplayNameTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String CASE_JSON =
      "{"
          + "\"universal_title\":\"DN\",\"startingRoom\":\"Study\",\"startingInsightTokens\":1,"
          + "\"rooms\":["
          + "{\"name\":\"Study\",\"neighbors\":{\"east\":\"Hall\"},\"objects\":["
          + "{\"id\":\"letter\",\"name\":\"letter\"},{\"id\":\"vase\",\"name\":\"vase\"}]},"
          + "{\"name\":\"Hall\",\"neighbors\":{\"west\":\"Study\"},\"objects\":[]}],"
          + "\"localizations\":{\"ar\":{\"languageName\":\"العربية\",\"title\":\"DN\","
          + "\"invitation\":\"i\","
          + "\"roomDetails\":["
          + "{\"name\":\"Study\",\"displayName\":\"المكتب\",\"description\":\"d\"},"
          + "{\"name\":\"Hall\",\"displayName\":\"القاعة\",\"description\":\"d\"}],"
          + "\"objectDetails\":["
          + "{\"name\":\"letter\",\"displayName\":\"الرسالة\",\"examine\":\"e\",\"deduce\":\"de\"},"
          + "{\"name\":\"vase\",\"examine\":\"e\",\"deduce\":\"de\"}],"
          + "\"suspects\":[{\"id\":\"LadyEleanor\",\"name\":\"LadyEleanor\","
          + "\"displayName\":\"الليدي إلينور\",\"statement\":\"s\",\"clue\":\"c\","
          + "\"homeRoom\":\"Hall\"}],"
          + "\"final_exam\":{\"questions\":[]}}}}";

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness started() {
    try {
      CaseFile caseFile = MAPPER.readValue(CASE_JSON, CaseFile.class);
      return factory.start(new LocalizedCaseFile(caseFile, "ar"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static RoomDescriptionDTO lastRoom(ContextHarness h) {
    RoomDescriptionDTO last = null;
    for (Serializable dto : h.playerResponses()) {
      if (dto instanceof RoomDescriptionDTO rd) {
        last = rd;
      }
    }
    return last;
  }

  @Test
  public void roomCarriesDisplayNameAlongsideUniversalName() {
    ContextHarness h = started();
    h.execute(new LookCommand());

    RoomDescriptionDTO room = lastRoom(h);
    assertNotNull(room);
    assertEquals("Study", room.getName()); // Universal Name (terminal header source / record key).
    assertEquals("المكتب", room.getDisplayName()); // per-language, GUI-facing.
  }

  @Test
  public void objectListStaysUniversalWhileDisplayNamesRideAlongside() {
    ContextHarness h = started();
    h.execute(new LookCommand());

    RoomDescriptionDTO room = lastRoom(h);
    // The command-facing list is Universal.
    assertTrue(room.getObjectNames().contains("letter"));
    assertTrue(room.getObjectNames().contains("vase"));
    // The Display Name rides in a side map keyed by the Universal Name.
    assertEquals("الرسالة", room.getObjectDisplayNames().get("letter"));
    // No authored Display Name -> absent -> GUI falls back to the Universal Name.
    assertEquals("vase", room.getObjectDisplayNames().getOrDefault("vase", "vase"));
  }

  @Test
  public void occupantListStaysUniversalWhileDisplayNamesRideAlongside() {
    ContextHarness h = started();
    h.bringSuspectToPlayer("LadyEleanor");
    h.execute(new LookCommand());

    RoomDescriptionDTO room = lastRoom(h);
    assertTrue(room.getOccupantNames().contains("LadyEleanor")); // Universal (autocomplete/terminal)
    assertEquals("الليدي إلينور", room.getOccupantDisplayNames().get("LadyEleanor"));
  }

  @Test
  public void exitValuesAreNeighbourDisplayNamesKeyedByDirection() {
    ContextHarness h = started();
    h.execute(new LookCommand());

    RoomDescriptionDTO room = lastRoom(h);
    assertTrue("movement key stays the direction", room.getExits().containsKey("east"));
    assertEquals("القاعة", room.getExits().get("east")); // neighbour (Hall) Display Name
  }
}

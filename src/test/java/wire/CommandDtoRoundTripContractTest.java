package wire;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import common.SerializationUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Wire-protocol round-trip contract for every Command and DTO under {@code common}.
 *
 * <p>This suite is the serialized-form contract that the future web client must honour: the
 * JavaFX/desktop sender, the LAN peer host, and (per docs/ROADMAP.md Phase 3a) the WebSocket
 * adapter all speak the same {@link SerializationUtils} JSON. Every framed type must survive
 * serialize → deserialize with all fields intact, or browsers and desktop peers will silently
 * disagree about game state.
 *
 * <p>Coverage is driven by scanning the compiled package tree ({@link WireProtocolClasses}); a new
 * Command or DTO is pulled in automatically and fails the build if it cannot round-trip. A class
 * that cannot even be constructed is treated as a coverage gap and fails loudly — it is never
 * silently skipped.
 */
public class CommandDtoRoundTripContractTest {

  @Test
  public void everyCommandRoundTripsWithAllFieldsIntact() {
    assertAllRoundTrip(WireProtocolClasses.commands(), "Command");
  }

  @Test
  public void everyDtoRoundTripsWithAllFieldsIntact() {
    assertAllRoundTrip(WireProtocolClasses.dtos(), "DTO");
  }

  @Test
  public void scannerDiscoversTheKnownWireSurface() {
    // Guard against a broken scan silently reducing coverage to zero. These anchors are part of
    // the protocol today; if one is renamed, update this list deliberately.
    List<String> commandNames = names(WireProtocolClasses.commands());
    List<String> dtoNames = names(WireProtocolClasses.dtos());

    assertTrue("expected to discover Command types", commandNames.size() >= 20);
    assertTrue("expected to discover DTO types", dtoNames.size() >= 20);
    assertTrue("MoveCommand must be covered", commandNames.contains("common.commands.MoveCommand"));
    assertTrue(
        "RoomDescriptionDTO must be covered", dtoNames.contains("common.dto.RoomDescriptionDTO"));

    assertFalse(
        "abstract BaseCommand must not be treated as a framable wire type",
        commandNames.contains("common.commands.BaseCommand"));
    assertFalse(
        "enum DialogueType must not be treated as a standalone framable wire type",
        dtoNames.contains("common.dto.DialogueType"));
  }

  private void assertAllRoundTrip(List<Class<?>> types, String kind) {
    assertFalse("No " + kind + " types were discovered by the scanner", types.isEmpty());
    WireSampleFactory factory = new WireSampleFactory();
    List<String> failures = new ArrayList<>();

    for (Class<?> type : types) {
      Object original = factory.build(type);
      if (original == null) {
        failures.add(
            type.getName()
                + " — coverage gap: the test could not construct a sample instance. Give it a"
                + " public constructor (ideally @JsonCreator) or setters so it can be exercised.");
        continue;
      }
      try {
        byte[] wire = SerializationUtils.serialize((java.io.Serializable) original);
        Object restored = SerializationUtils.deserialize(wire);
        if (!type.isInstance(restored)) {
          failures.add(
              type.getName()
                  + " — deserialized as "
                  + (restored == null ? "null" : restored.getClass().getName())
                  + "; type identity is lost on the wire (is the class final, so default typing"
                  + " emits no @class tag?)");
          continue;
        }
        String diff = DeepEquals.diff(original, restored);
        if (diff != null) {
          failures.add(type.getName() + " — field changed across round trip at " + diff);
        }
      } catch (Exception e) {
        failures.add(type.getName() + " — threw during round trip: " + e);
      }
    }

    if (!failures.isEmpty()) {
      fail(
          failures.size()
              + " of "
              + types.size()
              + " "
              + kind
              + " types failed the wire round-trip contract:\n  - "
              + String.join("\n  - ", failures));
    }
  }

  private static List<String> names(List<Class<?>> types) {
    List<String> names = new ArrayList<>();
    for (Class<?> t : types) {
      names.add(t.getName());
    }
    return names;
  }
}

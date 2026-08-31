package extractors;

import com.fasterxml.jackson.databind.ObjectMapper;
import JsonDTO.CaseFile;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;
import java.util.Map;
import java.util.HashMap;

public class TokenInitializationTest {

    @Test
    public void testStrictTokenInitialization() throws Exception {
        // Create a temporary JSON file with 5 tokens and minimal localization + rooms
        String json = "{ \"universal_title\": \"Strict Test Case\", \"startingInsightTokens\": 5, \"rooms\": [], \"localizations\": { \"en\": { \"title\": \"Test\", \"suspects\": [], \"roomDetails\": [], \"objectDetails\": [] } } }";
        File tempFile = File.createTempFile("strict_test_case", ".json");
        Files.write(tempFile.toPath(), json.getBytes());
        tempFile.deleteOnExit();

        // Use ObjectMapper directly to simulate CaseLoader behavior
        ObjectMapper mapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CaseFile caseFile = mapper.readValue(tempFile, CaseFile.class);

        // 1. Assert Raw Value matches JSON (5)
        assertEquals("Raw CaseFile must have 5 tokens", Integer.valueOf(5), caseFile.getStartingInsightTokens());

        // 2. Test Localized Wrapper
        JsonDTO.LocalizedCaseFile localized = new JsonDTO.LocalizedCaseFile(caseFile, "en");
        assertEquals("LocalizedCaseFile must have 5 tokens", Integer.valueOf(5), localized.getStartingInsightTokens());

        // 3. Test Missing Field (Must be 0, NOT 1)
        String jsonMissing = "{ \"universal_title\": \"Missing Token Case\", \"rooms\": [], \"localizations\": { \"en\": { \"title\": \"Test\", \"suspects\": [], \"roomDetails\": [], \"objectDetails\": [] } } }";
        File tempFileMissing = File.createTempFile("missing_token_test", ".json");
        Files.write(tempFileMissing.toPath(), jsonMissing.getBytes());
        tempFileMissing.deleteOnExit();

        CaseFile caseFileMissing = mapper.readValue(tempFileMissing, CaseFile.class);
        assertNull("Raw missing tokens must be null", caseFileMissing.getStartingInsightTokens());

        JsonDTO.LocalizedCaseFile localizedMissing = new JsonDTO.LocalizedCaseFile(caseFileMissing, "en");
        assertEquals("Localized default must be 0, NOT 1", Integer.valueOf(0),
                localizedMissing.getStartingInsightTokens());
    }
}

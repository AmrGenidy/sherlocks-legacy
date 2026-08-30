package JsonDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class TokenParsingTest {

    @Test
    public void testTokenParsing() throws Exception {
        String json = "{ \"universal_title\": \"Test Case\", \"startingInsightTokens\": 5 }";
        ObjectMapper mapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        CaseFile caseFile = mapper.readValue(json, CaseFile.class);

        assertNotNull("CaseFile should not be null", caseFile);
        assertEquals("Tokens should be 5", Integer.valueOf(5), caseFile.getStartingInsightTokens());
    }

    @Test
    public void testTokenParsingDefault() throws Exception {
        String json = "{ \"universal_title\": \"Test Case\" }"; // No tokens field
        ObjectMapper mapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        CaseFile caseFile = mapper.readValue(json, CaseFile.class);

        assertNotNull("CaseFile should not be null", caseFile);
        assertNull("Tokens should be null when missing", caseFile.getStartingInsightTokens());
    }
}

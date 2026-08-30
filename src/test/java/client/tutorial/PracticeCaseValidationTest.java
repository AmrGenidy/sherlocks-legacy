package client.tutorial;

import static org.junit.Assert.*;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.CaseValidator;
import java.io.InputStream;
import org.junit.Test;

/** The bundled practice case must be structurally clean by the same validator real cases face. */
public class PracticeCaseValidationTest {

  @Test
  public void practiceCaseHasNoValidationErrors() throws Exception {
    ObjectMapper mapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream("tutorial_practice_case.json")) {
      assertNotNull("practice case resource must be on the classpath", is);
      CaseFile caseFile = mapper.readValue(is, CaseFile.class);
      CaseValidator.Report report = CaseValidator.validate(caseFile);
      assertFalse(
          "practice case must validate clean. Errors: " + report.errors(), report.hasErrors());
    }
  }
}

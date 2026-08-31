package engine;

import JsonDTO.LocalizedCaseFile;
import java.util.Arrays;
import java.util.List;

/**
 * Builds a started {@link ContextHarness} for a given case fixture.
 *
 * <p>{@link #both()} supplies the JUnit {@code Parameterized} data so every contract test runs once
 * per {@code GameActionContext} implementation.
 */
public interface ContextHarnessFactory {

  ContextHarness start(LocalizedCaseFile caseFile);

  /** Loads the case but leaves it in the pre-'start case' state. */
  ContextHarness startUnstarted(LocalizedCaseFile caseFile);

  String label();

  ContextHarnessFactory SINGLE_PLAYER =
      new ContextHarnessFactory() {
        @Override
        public ContextHarness start(LocalizedCaseFile caseFile) {
          return SinglePlayerContextHarness.start(caseFile);
        }

        @Override
        public ContextHarness startUnstarted(LocalizedCaseFile caseFile) {
          return SinglePlayerContextHarness.startUnstarted(caseFile);
        }

        @Override
        public String label() {
          return "SinglePlayer";
        }

        @Override
        public String toString() {
          return label();
        }
      };

  ContextHarnessFactory SERVER =
      new ContextHarnessFactory() {
        @Override
        public ContextHarness start(LocalizedCaseFile caseFile) {
          return ServerContextHarness.start(caseFile);
        }

        @Override
        public ContextHarness startUnstarted(LocalizedCaseFile caseFile) {
          return ServerContextHarness.startUnstarted(caseFile);
        }

        @Override
        public String label() {
          return "Server";
        }

        @Override
        public String toString() {
          return label();
        }
      };

  /** Both context implementations, for {@code @Parameterized.Parameters}. */
  static List<Object[]> both() {
    return Arrays.asList(new Object[] {SINGLE_PLAYER}, new Object[] {SERVER});
  }
}

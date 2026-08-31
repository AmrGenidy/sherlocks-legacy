package ui.windows;

import java.util.List;

/**
 * The structured command reference behind the Help window (GUI G4): grouped sections, each a small
 * header over rows of {@code (command literal, description)}. Pure data + L10n keys — no JavaFX —
 * so the {@link HelpWindow} only renders it and the grouping/vocabulary is unit-testable across all
 * three languages.
 *
 * <p>Command literals stay Latin (the parser only accepts Latin keywords), so the window renders
 * them in the typewriter face regardless of UI language; only the section headers and descriptions
 * are localized.
 */
public final class HelpReference {

  private HelpReference() {}

  /** One command row: the literal the player types, and the L10n key for its description. */
  public record Entry(String command, String descriptionKey) {}

  /** One group: the L10n key for its header, and its command rows. */
  public record Section(String headerKey, List<Entry> entries) {}

  /** The full reference, in display order. */
  public static List<Section> sections() {
    return List.of(
        new Section(
            "help.section.looking",
            List.of(
                new Entry("look", "help.cmd.look"),
                new Entry("move [north|south|east|west|up|down]", "help.cmd.move"))),
        new Section(
            "help.section.investigation",
            List.of(
                new Entry("examine [object]", "help.cmd.examine"),
                new Entry("question [suspect]", "help.cmd.question"),
                new Entry("deduce [object|suspect]", "help.cmd.deduce"),
                new Entry("contradict [evidence] with [suspect]", "help.cmd.contradict"),
                new Entry("combine [noteA_id] [noteB_id]", "help.cmd.combine"))),
        new Section(
            "help.section.record",
            List.of(
                new Entry("journal", "help.cmd.journal"),
                new Entry("journal [word]", "help.cmd.journalSearch"),
                new Entry("journal add [note]", "help.cmd.journalAdd"),
                new Entry("tasks", "help.cmd.tasks"),
                new Entry("pinboard", "help.cmd.pinboard"))),
        new Section(
            "help.section.watson",
            List.of(
                new Entry("ask watson", "help.cmd.askWatson"),
                new Entry("ask watson [object|suspect]", "help.cmd.askWatsonTarget"))),
        new Section("help.section.exam", List.of(new Entry("final exam", "help.cmd.finalExam"))),
        new Section(
            "help.section.system",
            List.of(new Entry("help", "help.cmd.help"), new Entry("exit", "help.cmd.exit"))));
  }
}

# 0002 — Navigation shell + per-screen controllers for the JavaFX client

Status: accepted (2026-06-12)

`ui.MainController` (2,805 LOC) mixed four screens (menu, lobby, in-game, final exam) with
navigation, terminal routing, localization, and session lifecycle — the interface of every change
was the whole file. We split it behind one seam:

- **`ui.shell.ScreenController`** — the screen seam. A screen owns its view graph, sub-state,
  terminal-input share, Escape step-back, and re-render on language change. Adapters:
  `ui.screens.MenuController`, `ui.screens.LobbyController`, `ui.screens.GameScreenController`,
  `ui.screens.ExamScreenController`.
- **`ui.MainController` stays, as the shell.** It keeps the `fx:controller` binding, the FXML
  widgets, and the inbound listener interfaces (`GameClientStateListener`, `FinalExamListener`,
  `TutorialHost`), and delegates each callback to the owning screen. Keeping the class name avoids
  churning every reference (`GuiGameOutputSink`, `GameClient` wiring, tutorial system, tests) in
  one slice; the shell's job is routing, not behavior.

## The deliberate calls

- **One screen showing at a time, mounted in the shell's content pane.** Screens swap their own
  sub-views internally (e.g. menu → tutorials → case selection) without shell transitions; the
  shell animates only screen-to-screen changes (DESIGN.md §6 motion).
- **Terminal input is a chain**: tutorial routing → autocomplete accept → current screen
  (`handleTerminalInput`) → shell legacy routing (shrinks as screens extract, deleted at the end).
  Same for Escape: suggestion strip → sub-windows/overlays → `onEscape()` → no-op (never silent
  app exit).
- **Localization rides the split** (.scratch/ui-localization): a screen's strings move into
  `i18n/messages_{en,ar}.properties` in the same slice that extracts the screen — each string is
  touched once. `ui.i18n.L10n` is the lookup seam; on switch the shell re-applies chrome text +
  `Node.setNodeOrientation` and the visible screen re-renders. Case content stays localized in
  case JSON, not in bundles.
- **Session state stays on the shell** (`gameClient`, `singlePlayerGame`, SP/MP flags), exposed to
  screens through shell getters. The alternative — a separate session object — adds a name without
  adding depth while the shell already brokers the listener interfaces; revisit if the web client
  track needs the session without JavaFX.

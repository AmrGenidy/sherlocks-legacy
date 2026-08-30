# PROJECT.md — Sherlock's Legacy

*The overview a senior engineer would give a new hire. For architecture-level orientation. For the
build/run commands and conventions, see the [README](README.md) and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).*

---

## 1. What this is

**Sherlock's Legacy** is a **desktop detective game** built in **JavaFX**. The player is a Victorian
detective who walks between rooms, examines objects, questions suspects, contradicts their lies with
evidence, combines clues into deductions, and finally names the culprit in a "final exam." It ships with
authored **cases** (self-contained mysteries) and includes a **Case Maker** so users can author their
own. It plays **single-player offline** or **multiplayer over a LAN**.

Who it's for: players who like Golden-Age whodunits (Agatha Christie is the explicit inspiration), and —
via the Case Maker — hobbyist case authors. It is a hobby/indie project, currently pre-release, packaged
for friends via `jpackage`.

The visual identity (see `DESIGN.md`) is deliberate: a warm, hand-crafted "leather-bound adventure
annual" look blending Tintin's *ligne claire* with gaslit Victorian London — **never** a modern
flat/SaaS aesthetic.

---

## 2. Tech stack (and why)

| Piece | Version | Why it's here |
|---|---|---|
| **Java** | source/target **17** (built & run on JDK **23**) | Mature language for a desktop app; records, sealed-ish patterns, switch expressions used throughout. |
| **JavaFX** | 22.0.2 | The desktop UI toolkit. Scenes, FXML, CSS theming, Canvas-drawn room plate. Chosen over Swing for CSS theming + modern layout. |
| **Maven** | — | Build. Plugins: `maven-shade` (fat runnable jar), `javafx-maven-plugin` (`javafx:run` in dev), `spotless` (google-java-format), `versions` + `dependency-check` (OWASP) for dep hygiene. |
| **Jackson** | 2.17.2 | All JSON: case files, save/profile/settings, **and the multiplayer wire protocol** (polymorphic, guarded — see §4). |
| **SLF4J + Logback** | 2.0.17 / 1.5.21 | Logging. Config in `src/main/resources/logback.xml`, root level INFO. |
| **JUnit 4 + Mockito** | 4.13.2 / 5.15.2 | ~160 test files. Heavy on engine/wire/case logic; light on JavaFX UI (see GAPS). |
| **jpackage** | JDK tool | Packaging to a self-contained Windows app-image (bundled JRE, no Java needed by end users). |

Notably **no framework** (no Spring, no DI container). Wiring is manual constructor injection. Networking
is **raw Java NIO** (no Netty). This keeps single-player fully offline and dependency-light.

---

## 3. Architecture

The spine is a **single shared rulebook** (`engine.GameEngine`) that both single-player and multiplayer
drive through the same command layer. The UI is a JavaFX shell hosting one screen at a time.

```
                          JavaFX UI  (package: ui/)
        ui.GameClientFX (Application)  →  ui.MainController  ← THE SHELL (fx:controller, 2.5k LOC)
              delegates each callback to one ui.shell.ScreenController:
                MenuController · LobbyController · GameScreenController · ExamScreenController
              plus windows/overlays: RoomView, TerminalView, Pinboard, Journal, CaseFile,
                                     Frontispiece, Settings, and the Case Maker (ui/casemaker/)
                         │                                   │
             single-player │ (in-process, no socket)          │ multiplayer (LAN, TCP/NIO)
                         ▼                                   ▼
        singleplayer.GameContextSinglePlayer     client.GameClient ──TCP──► server.GameServer
                  (adapter)                                                   GameSessionManager
                         │                                                     └ GameSession
                         │                                                        └ server.GameContextServer (adapter)
                         └───────────────────────┬──────────────────────────────────┘
                                                 ▼
                                        engine.GameEngine          ← single source of truth
                                          world: Core.Room / Core.Suspect / Core.DoctorWatson
                                          Journal · Tasks · Insight tokens · Deduction counter
                                          combine/contradict cooldowns · Final-Exam lifecycle
                                                 ▲  implements common.interfaces.GameActionContext
                                                 │
                                   common.commands.*   (Command Pattern)
                            Move · Examine · Question · Contradict · Combine · Deduce ·
                            AskWatson · SubmitQuestionAnswer · UpdateTaskState · UpdatePinboard …
```

**Two engine seams** (ADR-0001):
- `engine.GameEventListener` — all output (`toPlayer` / `toAll`). SP routes it to a `GameOutputSink`;
  the server routes it to session send/broadcast.
- `engine.PlayerSet` — who's playing (a solo set for SP, a host/guest set for MP).

**Data flow of a turn.** User types a command in the terminal (or clicks a sprite/button) → the active
`ScreenController` builds a `Command` via a `CommandFactory` (`singleplayer/util` or `client/util`) →
the command runs against a `GameActionContext` (the SP or server adapter) → the adapter calls
`engine.GameEngine` → the engine mutates state and emits **DTOs** (`common.dto.*`) through the listener →
the UI's `GuiGameOutputSink` turns DTOs into terminal lines, dialogue bubbles, and window updates.

**Cases** (the content pipeline):
```
case JSON  →  extractors.CaseLoader (bundled-in-jar + external folder, recursive, size-capped)
           →  extractors.CaseValidator (id integrity, reachability, localization coverage, exam)
           →  JsonDTO.CaseFile (Jackson POJOs)
           →  JsonDTO.LocalizedCaseFile (single-language adapter view for the chosen Language Code)
           →  extractors: BuildingExtractor / SuspectExtractor / GameObjectExtractor  →  Core.* world
```
**Images**: a case-authored `imagePath` → `extractors.ResourceResolver` (classpath → case dir →
filesystem) → `ui.util.ImageManager`; on a miss it falls back to a deterministic hand-drawn engraving
`ui.util.PresetArtResolver` (`images/presets/`), and only then to a procedural placeholder — so the UI
never shows a broken image.

**Wire protocol**: `common.SerializationUtils` — Jackson with **polymorphic default typing constrained
by a `PolymorphicTypeValidator` allowlist** (`common.commands.` / `common.dto.` / `JsonDTO.` + a few
enumerated collections), length-prefixed frames with size caps (64 KB server-inbound). LAN game
discovery is UDP (`client.discovery`, `server.LanGameBroadcaster`).

---

## 4. Key design decisions (and the reasoning)

- **One `GameEngine`, two thin adapters** (ADR-0001). The rulebook used to be duplicated in the SP and
  server contexts and had *drifted*. It's now one engine; the contexts are adapters implementing
  `GameActionContext`. Command classes and the engine test suite were left untouched by design.
- **Single-player is fully in-process** — never a loopback socket. This is a hard constraint (ROADMAP):
  SP must work with zero connectivity. SP calls the engine by direct method call.
- **Host gating is a session-layer concern, not engine logic.** "Only the host may start the case /
  exam" lives in `GameContextServer`; the engine only knows `caseStarted && !examActive`. So a 1-player
  session behaves exactly like SP.
- **`MainController` is a shell, not a screen** (ADR-0002). It keeps the `fx:controller` binding and the
  inbound listener interfaces and *delegates* to per-screen `ScreenController`s. It was deliberately not
  renamed to avoid churning every reference at once — the split is incremental.
- **Universal vs Display names.** Every Room/Object/Suspect has a language-independent **Universal Name**
  (used by commands, parser, autocomplete) and a per-language **Display Name** (GUI only). Commands never
  resolve on the display name. Same idea at the case level: **Universal Title** (`universal_title`) is the
  stable key everywhere; the localized title is display-only.
- **Case content is localized inside the case JSON**, not in the UI resource bundles. The UI chrome is
  translated in `i18n/messages_<lang>.properties` (8 locales); a case's rooms/suspects/clues/exam are a
  `localizations` map keyed by Language Code inside the case file.
- **Save = Completed-Case Record with "Keep the Best."** A solved case stores a monotonic **Best Result**
  (rank strength = fewest deductions ever) for the casebook seal, and a **Latest Finish** (last run's
  journal/pinboard/score) that Review shows and is overwritten every solve. See CONTEXT.md.
- **Security posture is documented and audited** (`docs/SECURITY_PLAN.md`): no native Java serialization
  on the wire, a deny-by-default polymorphic allowlist, framed/size-capped messages, `WireLimits`
  per-field validation, `StreamReadConstraints` + size/count caps on untrusted case files, case-path
  sandboxing, server-authoritative command handling. Scope is **LAN-only, trusted network**.
- **Issues live in `.scratch/<feature>/` markdown, not code comments.** There are **zero** TODO/FIXME
  markers in the source. Half-finished work and divergences are tracked as local issue files; ADRs record
  accepted decisions in `docs/adr/`.

---

## 5. Critical paths — what's load-bearing vs safe to change

**Load-bearing (change with care, tests + play-verify):**
- `engine/GameEngine.java` (1.2k LOC) — the single rulebook. Every gameplay rule change touches this;
  both SP and MP depend on it. Well-covered by the engine test suite.
- `common/interfaces/GameActionContext.java` — the seam every command talks to. Adding a method means
  implementing it in **both** `GameContextSinglePlayer` and `GameContextServer` (default methods soften
  this — see `getWatsonImagePath`).
- `common/SerializationUtils.java` — the wire (de)serialization + the security-critical PTV allowlist.
  **Never** widen the allowlist casually; there's a boundary regression test (`WireSecurityBoundaryTest`).
- `extractors/CaseLoader.java` + `CaseValidator.java` — every case flows through these. The validator is
  the gate: a case with errors is refused at load, never mid-play. `ResourceResolver.java` is the single
  path→URL resolver shared by the loader, validator, and image manager.
- `ui/MainController.java` (2.5k LOC) — the shell: session state, terminal routing, tutorial host, exam
  cooldown, screen switching. It's a god-object (see GAPS) and the highest-risk file to edit blind.
- `common/commands/*` — the Command Pattern action layer. Command construction must stay **side-effect
  free** (they're deserialized from the wire; effects belong in `execute`, gated by the context).

**Safe-ish to change:**
- Case **content** (the JSON files under `cases/<slug>/`) and their art — validated on load, no code risk.
  See `docs/CASEBOOK.md` for the authoring guide.
- CSS (`css/detective-theme.css`, `theme_dark.css`) and per-screen view code, within `DESIGN.md` rules.
- i18n strings (`i18n/messages_*.properties`).
- The Case Maker (`ui/casemaker/`) — self-contained authoring UI, not on the gameplay hot path.
- Watson hint text, tasks, ranking tiers in a case — pure content.

---

## 6. Surprising / non-obvious things that will trip you up

1. **Two ways cases load, and they behave differently.** In dev (`mvn javafx:run`) resources are plain
   files, so `CaseLoader` recurses via the filesystem branch. In a **packaged jar** it uses the JAR
   branch (`FileSystems.newFileSystem` + `Files.walk`), which has a real pitfall opening the app's own
   jar (see GAPS #1). Always test case-loading in the **packaged** build, not just dev.
2. **The multiplayer wire uses Jackson polymorphic typing.** It looks innocent but is security-critical:
   the `PolymorphicTypeValidator` allowlist is the only thing standing between a LAN peer and gadget
   deserialization. Keep command/DTO constructors inert.
3. **The tutorial system is hard-wired to one case.** `tutorials.json` + `TutorialOrchestrator` hardcode
   the practice case's rooms/objects/suspect (`Study`/`Parlour`/`the valet`/`torn_letter`/`muddy_boot`),
   and tests assert them. Editing `tutorial_practice_case.json` breaks tutorials. It is deliberately
   separate from the playable `cases/` and never appears in case selection.
4. **Watson has three hint channels, and one is dead.** `ask watson` serves the `general` and
   `contradiction` buckets; `ask watson <name/thing>` serves the `red_herrings` block. The
   `watson.hints.red_herring` bucket is loaded but **never displayed** — don't put steering there.
5. **Java field names disagree with the domain vocabulary on purpose.** `winningStatement` in code is a
   "Winning Message" in the domain; the `Rank` class is a "Rank Tier." CONTEXT.md is canonical for talk
   and new code; existing identifiers stay until a deliberate rename.
6. **Sprite scaling is by height, from the opaque bounding box.** Character/object PNGs are transparent
   cut-outs with margins; `RoomViewLayout` sizes them as `baseFactor × imageScale × renderedRoomHeight`.
   To place art you compute `imageScale` from the figure's *visible* bounds (see how A Bitter Cup / An
   Invitation to Judgement were wired). The Case Maker's Placement tab does this visually.
7. **`GameActionContext` has default methods** (e.g. `getWatsonImagePath`) so new capabilities don't
   force edits to both contexts — but that also means a method can silently no-op if an adapter forgets
   to override it.
8. **CONTEXT.md warns it's a first-pass draft.** Four splits are canonical (Clue/Evidence,
   Statement/Winning Message, Rank Tier, Detective/Player/User); the rest was inferred from code and may
   be imperfect.
9. **`.scratch/` is the issue tracker.** If you're looking for the "why" behind a divergence or a
   half-finished feature, it's in `.scratch/<feature>/`, not in code comments.

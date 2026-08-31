# Architecture

This document explains how *Sherlock's Legacy* is built - the technology, the single shared engine behind single-player and multiplayer, the data-driven case pipeline, and the terminal / GUI / Case Maker front-ends. It's aimed at anyone who wants to fork the engine, add a feature, or just understand what's under the hood.

> For day-to-day gameplay and commands, see [GAMEPLAY.md](GAMEPLAY.md). For writing cases, see [CASE_CREATION.md](CASE_CREATION.md).

---

## Table of contents

- [The technology stack](#the-technology-stack)
- [The big picture: one engine, two front-ends](#the-big-picture-one-engine-two-front-ends)
- [Single-player](#single-player)
- [Multiplayer (LAN)](#multiplayer-lan)
- [The dynamic, JSON, data-driven architecture](#the-dynamic-json-data-driven-architecture)
- [The terminal](#the-terminal)
- [The GUI](#the-gui)
- [The Case Maker](#the-case-maker)
- [Localization](#localization)
- [Security posture](#security-posture)
- [Project layout](#project-layout)
- [Building and packaging](#building-and-packaging)

---

## The technology stack

| Piece | Version | Why it's here |
|---|---|---|
| **Java** | source/target **17** (built & run on JDK **23**) | Mature language for a desktop app; records, switch expressions, and pattern matching used throughout. |
| **JavaFX** | 22.0.2 | The desktop UI toolkit - scenes, FXML, CSS theming, and a Canvas-drawn "room plate". Chosen over Swing for CSS theming and modern layout. |
| **Maven** | - | Build tool. Key plugins: `maven-shade` (fat runnable jar), `javafx-maven-plugin` (`mvn javafx:run` in dev), `spotless` (google-java-format), plus `versions` and OWASP `dependency-check` for dependency hygiene. |
| **Jackson** | 2.17.2 | All JSON: case files, saves/profile/settings, **and the multiplayer wire protocol**. |
| **SLF4J + Logback** | 2.0.17 / 1.5.21 | Logging (`src/main/resources/logback.xml`). |
| **JUnit 4 + Mockito** | 4.13.2 / 5.15.2 | ~160 test files, heavy on the engine, wire protocol, and case logic. |
| **jpackage** | JDK tool | Packages a self-contained app-image (bundled JRE) so end users don't need Java installed. |

There is **no framework** - no Spring, no dependency-injection container. Wiring is manual constructor injection, and networking is **raw Java NIO** (no Netty). This keeps single-player fully offline and the dependency surface small.

---

## The big picture: one engine, two front-ends

The heart of the game is a **single shared rulebook**, `engine.GameEngine`. Both single-player and multiplayer drive it through the **same command layer**, so the rules can never drift between the two modes.

```
                          JavaFX UI  (package: ui/)
      ui.GameClientFX (Application)  →  ui.MainController  ← THE SHELL
            delegates each callback to one ui.shell.ScreenController:
              MenuController · LobbyController · GameScreenController · ExamScreenController
            plus windows/overlays: RoomView, TerminalView, Pinboard, Journal,
                                   CaseFile, Settings, and the Case Maker (ui/casemaker/)
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

**How one turn flows:** the player types a command (or clicks a sprite/button) → the active `ScreenController` builds a `Command` object via a `CommandFactory` → the command runs against a `GameActionContext` (the single-player or server *adapter*) → the adapter calls `GameEngine` → the engine mutates state and emits **DTOs** (`common.dto.*`) through a listener → the UI's `GuiGameOutputSink` turns those DTOs into terminal lines, dialogue bubbles, and window updates.

Two small seams let the same engine serve both modes:

- **`engine.GameEventListener`** - all output (`toPlayer` / `toAll`). Single-player routes it to an in-process output sink; the server routes it to session send/broadcast.
- **`engine.PlayerSet`** - who's playing (a solo set for single-player, a host/guest set for multiplayer).

This design is recorded in [ADR-0001](adr/) ("one engine, two thin adapters"). It exists because the rulebook was once duplicated across the two modes and had *drifted*; unifying it fixed that class of bug permanently.

---

## Single-player

Single-player is **fully in-process and never opens a socket** - a hard design constraint: the game must work with zero connectivity. The single-player front-end calls the engine by direct method call through `singleplayer.GameContextSinglePlayer`, an adapter that implements the same `GameActionContext` interface the multiplayer server uses.

Because a one-player multiplayer session behaves exactly like single-player, "who is allowed to do what" (e.g. only the host may start a case) is handled in the **session layer**, not in the engine. The engine only knows abstract state like `caseStarted && !examActive`.

---

## Multiplayer (LAN)

Multiplayer runs over a local network:

- **Discovery** is UDP - a host broadcasts (`server.LanGameBroadcaster`) and clients listen (`client.discovery`) so you can find games on your LAN without typing IP addresses.
- **The session** is TCP over **raw Java NIO**. `server.GameServer` accepts connections; `GameSessionManager` owns `GameSession`s; each session has its own `server.GameContextServer` adapter driving a `GameEngine`.
- **The server is authoritative.** Clients send commands; the server validates and runs them against the engine, then broadcasts the resulting DTOs. Clients never mutate game state directly.

The scope is deliberately **LAN-only on a trusted network** - see [Security posture](#security-posture).

---

## The dynamic, JSON, data-driven architecture

The whole game is **content-driven**: a mystery is *data*, not code. A case is a self-contained JSON file (plus its art), and the engine builds the playable world from it at load time. Adding a case never requires recompiling.

```
case JSON  →  extractors.CaseLoader     (reads bundled-in-jar AND external folders, recursive, size-capped)
           →  extractors.CaseValidator  (checks id integrity, map reachability, localization coverage, exam)
           →  JsonDTO.CaseFile          (Jackson POJOs - the raw case model)
           →  JsonDTO.LocalizedCaseFile (a single-language view for the chosen Language Code)
           →  extractors: BuildingExtractor / SuspectExtractor / GameObjectExtractor
           →  Core.* world              (Rooms, Suspects, Objects, Watson - what the engine plays)
```

Two ideas make this robust:

- **Validation is a gate, not a hope.** `CaseValidator` runs at load time; a case with errors is **refused up front**, never allowed to fail mid-play. This is why authored content is safe to change without code risk.
- **Universal names vs display names.** Every room, object, and suspect has a language-independent **Universal Name** (used by commands, the parser, and autocomplete) and a per-language **Display Name** (shown in the GUI). Commands *never* resolve on a display name. The same idea applies at the case level: a stable **Universal Title** keys everything, while the localized title is display-only. This is what lets one case ship in eight languages without breaking the command layer.

**Images never break.** A case-authored `imagePath` resolves through `extractors.ResourceResolver` (classpath → case folder → filesystem, sandboxed to prevent path escapes). On a miss, the UI falls back to a deterministic hand-drawn engraving (`PresetArtResolver`), and only then to a procedural placeholder - so a missing image is never a broken image.

See [CASE_CREATION.md](CASE_CREATION.md) for the authoring side of this pipeline.

---

## The terminal

The in-world **terminal** is a first-class input method, not a debug console. The player types commands (`examine`, `question`, `move`, `contradict`, `combine`, `deduce`, `ask watson`, …) and reads back a typed transcript of dialogue, clues, and system messages, colored by message kind. It has **live autocomplete** driven by the current game state - each screen advertises what's completable right now (command names with their valid argument domains, or the screen's menu options), so the suggestions are always context-aware.

Anything you can do by clicking, you can do by typing, and vice versa - the terminal and the GUI are two views onto the same command layer.

---

## The GUI

The JavaFX UI is a **shell hosting one screen at a time**. `ui.MainController` is that shell: it holds the top-level window, routes terminal input, hosts the tutorial, and switches between screens. It is deliberately *not* itself a screen - screen behavior lives in per-screen controllers under `ui/screens/` ([ADR-0002](adr/)):

- **MenuController** - the main menu and case selection.
- **LobbyController** - hosting/joining a LAN game.
- **GameScreenController** - the in-game screen: the room view, sprites, and the toolbar.
- **ExamScreenController** - the final exam.

On top of the screens sit **windows and overlays**: the room view (a Canvas-drawn "plate" with character/object sprites), the Terminal, the **Journal**, the **Pinboard**, the **Case File**, the Settings dossier, and the Case Maker. The visual language is defined in `DESIGN.md` and enforced through CSS variables (`css/detective-theme.css` for light, `css/theme_dark.css` for dark) mirrored by `ui.util.Palette` for the Canvas.

Sprites are scaled **by height from their opaque bounding box** (`RoomViewLayout`: `baseFactor × imageScale × renderedRoomHeight`) because character/object PNGs are transparent cut-outs with margins - the Case Maker's Placement tab does this sizing visually so authors never compute it by hand.

For a tour of each window and what it does in play, see [GAMEPLAY.md](GAMEPLAY.md).

---

## The Case Maker

The **Case Maker** (`ui/casemaker/`) is a full visual authoring tool built into the game. It lets authors:

- lay out **rooms** and their connections,
- **place** character and object sprites visually on the room plate (with correct feet-on-floor positioning and per-room scaling),
- write **suspects**, their statements (truths and lies), and **objects/clues**,
- wire **deductions**, **Watson hints**, **tasks**, **rank tiers**, and the **final exam**,
- pick the **editing language** independently of the interface language, so you can translate a case in place,
- **validate** the case against the same rules the engine enforces (with a copyable terminal of warnings/errors), and
- **save/export** a ready-to-play case folder.

It's self-contained and off the gameplay hot path - a safe area to extend. The authoring guide is [CASE_CREATION.md](CASE_CREATION.md).

---

## Localization

Two layers, kept separate on purpose:

- **UI chrome** (menus, buttons, command feedback, Watson framing, tutorials) lives in `i18n/messages_<lang>.properties`, resolved through `ui.i18n.L10n`. Eight locales ship: English, Arabic, Russian, Chinese, Turkish, German, French, Spanish.
- **Case content** (room/suspect/clue/exam text) is localized **inside each case JSON**, as a `localizations` map keyed by Language Code - *not* in the UI bundles. This keeps a case a single portable file that carries all its own translations.

---

## Security posture

The multiplayer wire is the main attack surface, and it's treated as such (full write-up in `docs/SECURITY_PLAN.md`):

- **No native Java serialization on the wire.** Everything is JSON via Jackson.
- **Deny-by-default polymorphic allowlist.** `common.SerializationUtils` uses a `PolymorphicTypeValidator` that only permits classes under `common.commands.`, `common.dto.`, and `JsonDTO.` (plus a few enumerated collection types). A boundary regression test guards it. **This allowlist is security-critical - never widen it casually**, and keep command/DTO constructors side-effect free (they're deserialized from untrusted peers; effects belong in `execute`, gated by the context).
- **Framed, size-capped messages** with per-field validation (`common.WireLimits`) and `StreamReadConstraints` on untrusted case files.
- **Case paths are sandboxed** so a malicious case file can't read arbitrary files off disk.
- **Server-authoritative** command handling.

Scope is **LAN-only, trusted network** - this is not hardened for hostile internet play.

---

## Project layout

Packages are organized **by role**, not by feature:

| Package | Role |
|---|---|
| `engine/` | The single rulebook (`GameEngine`) and its seams. |
| `common/commands/` | Command Pattern action classes (Move, Examine, Question, …). |
| `common/dto/` | Wire/state data-transfer objects. |
| `common/interfaces/` | `GameActionContext` - the seam every command talks to. |
| `common/` | Serialization + wire limits (security-critical). |
| `JsonDTO/` | Case-file POJOs (Jackson). |
| `extractors/` | Case load / validate / resolve (`CaseLoader`, `CaseValidator`, `ResourceResolver`, `*Extractor`). |
| `Core/` | The runtime domain model (Room, Suspect, DoctorWatson, …). |
| `singleplayer/` | In-process single-player adapter + command factory. |
| `client/` · `server/` | Multiplayer networking (NIO, discovery, sessions). |
| `ui/` | JavaFX shell, screens (`ui/screens/`), windows (`ui/windows/`), and the Case Maker (`ui/casemaker/`). |
| `launcher/` | Entry point for the packaged jar (`launcher.MainLauncher`). |

Architecture decisions of record live in [`docs/adr/`](adr/).

---

## Building and packaging

```bash
# Run in development (JavaFX plugin, main class ui.GameClientFX)
mvn javafx:run

# Build a runnable fat jar (main class launcher.MainLauncher)
mvn clean package
#   → target/DetectiveGametest-1.0-SNAPSHOT.jar
java -jar target/DetectiveGametest-1.0-SNAPSHOT.jar

# Run tests
mvn test

# Format (scope to the files you changed)
mvn spotless:apply

# Dependency hygiene
mvn versions:display-dependency-updates
mvn dependency-check:check     # OWASP; first run downloads the NVD database
```

**Native app-image (Windows example):** build the jar with `mvn clean package`, then run `jpackage --type app-image …` against it to produce a self-contained app with a bundled JRE (end users need no Java installed).

> **Note:** Java source/target is **17**, but the toolchain builds and runs on JDK **23**. No CI is committed - tests run locally.

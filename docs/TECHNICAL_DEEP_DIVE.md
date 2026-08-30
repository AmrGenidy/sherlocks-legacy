# Sherlock's Legacy — Technical Deep Dive

*A developer's walkthrough of how the game is built: the single-/multiplayer split, the networking
(TCP and UDP), how the journal, chat, and pinboard stay in sync in real time, the core mechanics
(deduce, tokens, ranks), the contradict and combine systems I designed for depth, the tutorial system,
and the Case Maker. Written to explain the *how* and the *why*, grounded in the actual classes.*

---

## 1. One rulebook, two front ends: the single-/multiplayer split

The hardest structural problem in the project was this: I wanted the game to run **single-player,
completely offline**, *and* run **multiplayer over a LAN** — without maintaining two copies of the game
rules. Early on I did have two copies: the single-player context and the server context each implemented
the full rulebook, and they drifted apart (bugs appeared in one mode but not the other). That was
untenable.

The solution was to extract a **single authority for all gameplay rules and state** —
`engine.GameEngine` — and make both modes drive it through the same interface. This is recorded as
**ADR-0001**.

The engine owns everything that *is* the game: the world (`Core.Room`, `Core.Suspect`,
`Core.DoctorWatson`), the `Journal`, the tasks, the Insight-Token economy and the deduction counter, the
combine/contradict cooldowns, and the Final-Exam lifecycle. It knows nothing about sockets or screens.

Both modes talk to the engine through one seam, `common.interfaces.GameActionContext`, implemented by
two thin **adapters**:

- `singleplayer.GameContextSinglePlayer` — used offline, **in-process, no socket ever**. The UI calls it
  by direct method call.
- `server.GameContextServer` — used inside a multiplayer `GameSession` on the server.

To make one engine serve both, I parameterised it by exactly **two seams**:

- **`engine.GameEventListener`** — every piece of output (`toPlayer`, `toAll`). In single-player it routes
  to a local `GameOutputSink`; on the server it routes to session send/broadcast. The engine never knows
  which.
- **`engine.PlayerSet`** — *who* is playing (detective lookup, display names, occupied rooms). A solo set
  for single-player; a host/guest set for multiplayer.

The payoff: **the entire `common.commands.*` layer and the engine test suite are identical for both
modes.** A command (e.g. `ContradictCommand`) validates against the `GameActionContext`, mutates the
engine, and emits DTOs — and it doesn't care whether that context is local or networked. Duplication went
from "two full rulebooks" to "two ~700-line adapters that mostly forward calls."

A deliberate consequence: **host-only rules live in the session layer, not the engine.** "Only the host
may start the case or the final exam" is enforced in `GameContextServer`; the engine only knows
`caseStarted && !examActive`. So a one-player multiplayer session behaves *exactly* like single-player,
with no special-casing. And single-player never opens a socket — that offline guarantee is a hard
constraint the architecture protects by construction.

---

## 2. Networking: TCP for play, UDP for discovery

Multiplayer is built on **raw Java NIO** — no Netty, no framework — for two reasons: it keeps the
dependency footprint small, and it means single-player carries zero networking weight.

### TCP — the gameplay channel

- **The server.** `server.GameServer` runs an NIO accept/selector loop; `server.GameSessionManager`
  tracks sessions; each match is a `server.GameSession` wrapping a `GameContextServer` (and therefore a
  `GameEngine`). Each connected client is a `server.ClientSession`.
- **The client.** `client.GameClient` opens a `SocketChannel` to the host and runs a **dedicated
  listener thread** that blocks reading framed objects and dispatches them. Because the connection is
  persistent, everything the server sends arrives immediately — that's what makes updates feel live.
- **The wire format.** `common.SerializationUtils` handles (de)serialization and framing:
  - Objects are serialized with **Jackson**, then written **length-prefixed**: a 4-byte length followed
    by the payload (`writeFramedObject`), and read back the same way (`readFramedObject`), which enforces
    a per-frame size cap so a bad length can't exhaust memory.
  - Crucially, the wire uses Jackson **polymorphic typing** (so a `Command` or a `DTO` deserializes back
    into its real class), but constrained by a **deny-by-default `PolymorphicTypeValidator` allowlist** —
    only classes under `common.commands.`, `common.dto.`, and `JsonDTO.` (plus a few enumerated
    collections) may be reconstructed. This is the security backbone of the LAN protocol: it's why I keep
    every command/DTO constructor side-effect free, and there's a boundary regression test guarding it.
- **The message model.** Everything on the wire is either a **Command** (client → server intent, e.g.
  `UpdatePinboardCommand`, `SubmitQuestionAnswerCommand`) or a **DTO** (server → client
  state/notification, e.g. `RoomDescriptionDTO`, `DialogueEventDTO`, `InsightTokenUpdateDTO`,
  `ExamResultDTO`). The client dispatches incoming messages by type (`instanceof` chain) to the right
  handler/listener.

### UDP — LAN discovery

So that a player can *find* a game on the network without typing an IP, hosting a session starts a
**UDP broadcaster**: `server.LanGameBroadcaster` runs on its own daemon thread and periodically
broadcasts a small `LanDiscoveryPacket` (game code, host, port, case, player count). On the other side,
`client.discovery.UdpLanGameDiscoveryService` listens for those packets and populates the "public games"
list. Discovery is deliberately **advisory and non-auto-connecting** — the packet is an untrusted hint
the user confirms; you never get pulled into a session just because a packet arrived.

So: **UDP finds the game, TCP plays it.**

---

## 3. Real-time sync: journal, chat, and pinboard

The thing that makes multiplayer feel alive is that when one detective writes a note, links two cards, or
sends a message, everyone sees it instantly. I handled the three shared surfaces slightly differently
because they have different shapes.

The general model: the server has one authoritative state; clients send *intents*; the server validates,
applies, and **broadcasts** the result to everyone. `GameSession.broadcast(dto, excludePlayerId)` fans a
message out to all connected clients (optionally excluding the sender). Because each client is holding a
persistent TCP connection with a live listener thread, "broadcast" means "on screen a moment later."

### The Journal — shared engine state

The journal is **owned by the engine** (`GameEngine` holds one `Core.Journal<JournalEntryDTO>`), so it's
naturally a shared, single source of truth. When a note or a discovered clue is added
(`addJournalEntry`), the engine appends it (capped by `WireLimits.MAX_JOURNAL_ENTRIES` so a client can't
grow every peer's journal without bound) and the emission goes out through the `GameEventListener` to all
players. Every detective's journal is the same journal.

### Chat — a simple broadcast

Chat is the lightest path. The player types `/chat <message>` (or `/c`); the client wraps it in a
`common.dto.ChatMessage` (sender id, text, timestamp) and sends it to the server;
`GameSession.processChatMessage` broadcasts it to everyone; each client's `handleChatMessage` appends it
to local `chatHistory` and notifies the UI (`onChatMessageReceived`). Chat commands are intercepted early
in the input pipeline so they work in *any* game state — lobby, in-game, or exam.

### The Pinboard — full state on join, granular updates after

The pinboard is the trickiest because it's a live, mutable graph of cards and coloured links that two
people can edit at once. I sync it in two layers:

1. **Full-state snapshot on join/start.** When play begins (or a client asks), the server broadcasts a
   `PinboardStateResponseCommand` carrying the entire `PinboardStateDTO`, so every client starts from an
   identical board. The client feeds it to a `pinboardStateListener`.
2. **Granular deltas afterward.** Every edit (add a note, move a card, draw a link) becomes a small
   `PinboardUpdateDTO` inside an `UpdatePinboardCommand`. The server **validates it before relaying**
   (`PinboardUpdateValidator.isAcceptable` — I never forward unchecked client geometry or text straight
   to peers), then broadcasts it to the *other* players (excluding the sender, who already applied it
   locally). Each client applies it through a `pinboardUpdateListener`.

That split — one authoritative snapshot plus validated incremental updates — is what keeps two people's
boards consistent without resending the whole board on every drag.

---

## 4. The token economy, deduce, and the rank system

These three systems are tightly coupled, and getting the coupling right is what makes the scoring feel
fair.

### Insight Tokens

`GameEngine` keeps **one shared balance** (`sharedInsightTokens`), seeded from the case's
`startingInsightTokens`. Spending is atomic: `trySpendInsightToken()` / `trySpendInsightTokens(amount)`
deduct only if you can afford it, then emit an `InsightTokenUpdateDTO` to all players so every screen's
token counter updates. Tokens are what you pay to **deduce** and to ask Watson pointed questions, so
they're a genuine scarcity you have to budget.

### Deduce and the deduction counter

There are two numbers here, and separating them fixed a real bug (ADR-0001's "issue 04"):
- `sessionDeduceCount` — the **only** input to rank. Every unfunded deduction charges the session's rank
  budget via `incrementSessionDeduceCount()` (and emits a `DeductionCountUpdateDTO`).
- A per-detective `deduceCount` still exists, but only as a **duplicate-deduction guard**, never the rank
  input.

The clever bit is the **heal**: `awardInsightToken()` doesn't blindly hand out a token. If you currently
owe deductions (`sessionDeduceCount > 0`), the award **repays a deduction instead** of granting a token.
So earning rewards (e.g. from a good combine) can undo the rank cost of an earlier deduction. It makes
the economy forgiving of experimentation while still rewarding efficiency.

### Rank Tiers

Scoring is "fewer deductions = better." `Core.util.RankEvaluator.evaluate(sessionDeduceCount, caseFile)`
sorts the case's ranking tiers by `maxDeductions` ascending and returns the **first tier whose
`maxDeductions` you're under** — so a tight solve lands "Sherlock Holmes," a loose one lands "Dr.
Watson," and a `defaultRank` catches everyone else (it's excluded from the deduction comparison so a
0-max default can't shadow the real tiers). Because rank is derived from a language-independent number
(deductions used), the *same* performance re-derives the right localized tier name in any UI language.

---

## 5. Contradict and combine — the mechanics I built for depth

The core investigation loop I inherited from the genre — walk, examine, question — but it was flat: read
text, move on. After the semester (when the project was "done"), I went back to give it real puzzle
depth, and the two mechanics I designed for that are **contradict** and **combine**.

### Where the idea came from — *Ace Attorney*

The inspiration is directly [Ace Attorney](https://en.wikipedia.org/wiki/Ace_Attorney). Its signature
beat is the **"Objection!"**: a witness gives testimony, and you win by *presenting a specific piece of
evidence that contradicts a specific statement*. It turns "listening to dialogue" into "auditing dialogue
against your evidence." I wanted exactly that feeling — the moment where a clue you were carrying suddenly
demolishes a lie — so I built **contradict**, and then extended the idea with **combine** to add a second
layer of reasoning on top.

### Contradict — auditing a suspect's lie

I modelled every suspect as a small **state machine**: a `Core.Suspect` is in one of `LIE`, `TRUTH`, or
`PANIC` (this is canonical domain vocabulary, `Suspect State`). Each state carries its own **statement**
*and* its own set of **contradiction rules**. A **Contradiction Rule** binds:

- an **evidence id** (an object's id, or a deduction's id),
- a **next state** to move to,
- a **reward deduction** to mint, and
- a **success message** to show.

So when the detective runs `ContradictCommand` — presenting a piece of evidence to a suspect — the engine
checks the rules on the suspect's *current* state. If the evidence matches, the suspect **transitions**
(e.g. `LIE → TRUTH`), starts giving a new statement, and the detective is rewarded with a new deduction.
The same evidence can do nothing in `LIE` and then crack the suspect in `TRUTH` — because the rule lives
on the state, not on the suspect as a whole. That's what gives interrogation *sequence*: you have to
break them in the right order, with the right evidence, exactly like turning testimony against a witness
statement by statement.

To keep it from becoming brute-force ("throw every clue at every suspect"), I added a **cooldown**: three
consecutive failed contradictions lock the action (I recently tuned contradict's lockout to 60 seconds,
while combine stays longer). A success resets the streak. It gently forces the player to *reason* about
which evidence contradicts which statement rather than spam.

### Combine — assembling clues into deductions

Contradict answers "which lie does this evidence break." Combine answers "what does putting two clues
together *prove*." A **Combine Rule** declares:

- the **required ids** (two clues or earlier deductions),
- the **result deduction** it mints (globally unique), and
- a **token reward**.

Running `CombineCommand` with a matching pair mints a new, named **Deduction** — which is itself
**evidence** that later contradiction rules can reference. That's the crucial design decision: a
combine's *output* can be a contradiction's *input*. It lets me build **chains** — examine two objects,
combine them into "this was murder, not suicide," then present *that deduction* to the suspect who staged
it. The mechanic isn't a single gotcha; it's a small logic graph the player assembles.

Combine shares the failure cooldown, and its token reward feeds straight into the economy from §4 (and
can heal a deduction), so the three systems reinforce each other: good reasoning earns tokens, tokens buy
more reasoning, and sloppy reasoning costs rank.

Together, contradict + combine turned a reading experience into an actual deduction puzzle — which is the
whole reason I went back to the project after it was "finished."

---

## 6. Tutorials

New mechanics need teaching, so I built a guided tutorial system (`client.tutorial.TutorialOrchestrator`
driving `tutorials.json`). What I care about most: the tutorials run on the **real engine**, not a faked
scene. The orchestrator boots a real single-player game on a dedicated **practice case**
(`tutorial_practice_case.json`) and drives it with **seeded commands**, so every taught action —
"examine the torn letter," "contradict the valet with the muddy boot," "combine these two clues" —
actually executes through the same command layer the real game uses. The player learns the mechanic by
doing it for real, with guidance overlays explaining each step, one mechanic per tutorial (move, examine,
question, deduce, combine, contradict, journal, pinboard, ask Watson, final exam). (Because it drives the
real engine against fixed ids, the practice case is tightly coupled to the tutorial scripts — a note for
anyone editing it.)

---

## 7. The Case Maker — a create-a-case studio, and adding the images

The single biggest feature I added is the **Case Maker**: instead of hand-writing case JSON, you author a
whole mystery through a GUI. My guiding decision was **GUI first, JSON as an export target** — the author
never touches the file format.

- **The model.** Everything the author edits lives in one in-memory `ui.casemaker.model.CaseDraft`.
  Loading an existing case (`CaseDraftLoader`) reads the JSON into a draft; saving (`CaseExporter` +
  `CaseMakerSerializer`) writes the draft back out as a valid, self-contained case folder. All the
  editing tabs mutate the one shared draft.
- **The tabs.** The `CaseMakerWindow` is organised into tabs that mirror the case's parts: the **Room
  map** (rooms and their connections), **Objects**, **Suspects** (identity plus the `LIE/TRUTH/PANIC`
  states and their statements), **Case logic** (the contradiction rules, combine rules, final exam, and
  Watson hints), and **Localization**.
- **Placement — the visual editor.** The part I'm proudest of is the **Placement** tab
  (`SuspectPlacementView` + `PlacementMarkers`): it shows the chosen room's background with every
  suspect, object, and Watson as a **draggable marker rendered as its real art**, so the author positions
  and scales them by eye instead of guessing numbers. It uses the *real in-game sizing math*
  (`RoomViewLayout`) so what you place is what you play, and it grew to support resize handles, flip,
  non-uniform scaling, rotation, name-label positioning, and undo/redo.

### Adding images *after* building the GUI

This is the order I did it in, and it mattered. First I made the authoring GUI work with the game's
**preset engraving art** — the deterministic hand-drawn fallback (`PresetArtResolver`) that every
room/object/suspect resolves to when it has no bespoke image. That let me build and play whole cases
before a single custom picture existed. *Then* I layered bespoke art on top: for each case I set an art
direction (warm oil for one, cold oil for another), generated cut-out artwork, and wired the real
`imagePath`s into the case — resolving each sprite's true size from its opaque pixel bounds so figures
stand correctly in the room. Because image resolution is a clean chain (authored image → preset →
placeholder, via `ResourceResolver` + `ImageManager`), a case is always playable and never shows a broken
image, whether or not its bespoke art exists yet. The GUI came first; the images slotted in afterward
without changing a line of authoring logic.

---

## 8. In summary

The technical spine of the game is one idea applied consistently: **a single shared engine, driven
through one command interface, with the differences between modes pushed to the edges.** That's what let
single-player stay offline and multiplayer stay in sync without duplicating rules. On top of it, TCP
carries play and UDP finds games; the journal, chat, and pinboard sync through an authoritative-state +
validated-broadcast model; the token/deduce/rank economy makes efficient reasoning matter; contradict and
combine (my Ace-Attorney-inspired additions) turn reading into deduction; tutorials teach on the real
engine; and the Case Maker turns the whole thing into an authoring studio where the art is the last layer,
not a prerequisite.

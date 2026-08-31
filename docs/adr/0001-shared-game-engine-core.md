# 0001 - One shared GameEngine behind both GameContexts

Status: accepted (2026-06-12)

`GameContextServer` (~1,600 LOC) and `GameContextSinglePlayer` (~1,100 LOC) each implemented the
full case-play rulebook, and the copies had drifted (divergences 04/06/07 in
`.scratch/engine-test-suite/issues/`). We extracted a single `engine.GameEngine` that owns all
shared rules and state - world (Rooms, Suspects, Dr. Watson), Journal, Tasks, the Insight-Token
economy and Deduction counter, combine/contradict cooldowns, and the Final-Exam lifecycle - and
both contexts became thin adapters that keep implementing `GameActionContext` (so the Command
classes and the Phase-1 engine test suite are untouched).

The engine is parameterized by exactly two seams:

- **`engine.GameEventListener`** - all output (`toPlayer`, `toAll`). The server adapter routes it
  to `GameSession` send/broadcast; the single-player adapter routes it to `GameOutputSink` and the
  `GameClientStateListener` counters.
- **`engine.PlayerSet`** - who is playing (Detective lookup, display names, occupied rooms). A solo
  set for single-player, a host/guest pair for multiplayer.

## The deliberate calls

- **Single-player stays fully in-process** (ROADMAP Hard Constraint 1). The rejected alternative -
  SP as a client of a loopback `GameServer` socket - would have unified more code but violates the
  offline guarantee and adds a server process to desktop SP. SP invokes the engine by direct method
  call; no socket, no connectivity check, ever.
- **Host gating is session-layer, not engine logic.** "Only the Host may start the case / initiate
  the exam" lives in `GameContextServer`; the engine's `canStartFinalExam` knows only
  `caseStarted && !examActive`. A 1-player session therefore behaves exactly like single-player
  without modelling a host.
- **Divergence resolutions** (each flips a pinned divergence test, recorded in the issue files):
  - *04 deduction heal*: one canonical session deduction counter in the engine; increment, heal,
    and `getSessionDeduceCount()` all read it. `Detective.deduceCount` remains only a per-detective
    duplicate-deduction guard, never the rank input. SP rank now benefits from the heal (server
    behaviour won).
  - *06 exam lifecycle*: start gate is `caseStarted && !examActive` everywhere (SP behaviour won);
    after scoring the engine keeps `finalExam`, the answers, and the last `ExamResultDTO` until the
    next exam start or case load (server behaviour won - also fixes the SP race where the GUI read
    a nulled result).
  - *07 task toggles*: the engine validates the index, stores the state, emits a
    `TaskStateUpdateDTO` to all players on success and an error `TextMessage` to the requester on
    an invalid index (server behaviour won), and exposes the completion state through a getter.
- **The two remaining TextMessage content-matches are typed.** Exam start is signalled by
  `InitiateFinalExamDTO` in both modes (SP previously only sent marker text); SP exit-to-case-
  selection is signalled by a new `common.dto.ReturnToCaseSelectionDTO`. The GUI sink keys on the
  DTOs; the marker `TextMessage`s remain for transcript/console rendering only.

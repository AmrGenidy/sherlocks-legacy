# In-game message localization - inventory & plan

Every string below is emitted from the engine / command / core layer as raw English and shown to the
player verbatim, so it appears in English even when the UI language is Arabic/Russian/etc. This is
the "(suspect) has nothing to say" family the player sees mid-game.

**Why they aren't localized today:** `common.dto.TextMessage` carries only `text` + `isError` - no
localization key - and the client renders `getText()` directly. (`DialogueEventDTO` already supports a
`textKey`, so the popup path is half-ready.)

**Recommended fix (per-client, works in multiplayer):**
1. Add an optional `messageKey` (+ `Object[] args`) to `TextMessage`, keeping `text` as the English
   fallback and every constructor side-effect free (wire-allowlist safe).
2. In the client render path (`ConsoleGameOutputSink.renderDisplayText` / `GuiGameOutputSink`), resolve
   `L10n.t(messageKey, args)` in the local UI language, falling back to `text`.
3. Add the keys below to all 8 `i18n/messages_<lang>.properties` files and change each emitter to pass a
   key. For popups already on `DialogueEventDTO`, pass the `textKey` (and localize the title too).

Languages to fill: **en, ar, ru, tr, zh, fr, de, es** (en is the source/fallback).

Legend: **[P]** player-facing (translate) · **[I]** internal/debug error (rare; translate last or leave).

---

## 1. Interrogation / Question  (`common/commands/QuestionCommand.java`)

| Key | English | Where |
|---|---|---|
| `game.question.nothingToSay` | `{0} has nothing to say or seems unwilling to talk right now.` | QuestionCommand:57 [P] |
| `game.question.tryDeduce` | `(You can now try to 'deduce {0}' based on their statement.)` | QuestionCommand:81 [P] |
| `game.question.invalidRoom` | `Error: You are not in a valid room to question anyone.` | QuestionCommand:40 [I] |
| `game.suspect.notInRoom` | `Suspect '{0}' is not in this room.` | QuestionCommand:50, ContradictCommand:95 [P] |
| `game.suspect.notFound` | `Suspect '{0}' not found.` | ContradictCommand:82 [P] |

## 2. Contradict  (`common/commands/ContradictCommand.java`)

| Key | English | Where |
|---|---|---|
| `game.contradict.none` | `No contradiction found with that evidence.` | :238 [P] |
| `game.contradict.success` | `[SERVER] Contradiction successful! +1 Insight Token.` | :196 [P] |
| `game.contradict.cooldownActive` | `[SERVER] Contradict is on cooldown for {0} seconds.` | :58 [P] |
| `game.contradict.popup.noneTitle` / `.noneBody` | "No Contradiction" / "No contradiction found with that evidence." | popup DTO [P] |
| `game.contradict.popup.cooldownTitle` / `.cooldownBody` | "Contradict on Cooldown" / "Contradict is locked. Try again in {0} seconds." | popup DTO [P] |

## 3. Combine  (`common/commands/CombineCommand.java`)

| Key | English | Where |
|---|---|---|
| `game.combine.noMatch` | `[SERVER] Those notes don't connect (yet).` | :122 [P] |
| `game.combine.noLogic` | `[SERVER] Those notes don't connect (no combine logic).` | :90 [P] |
| `game.combine.duplicate` | `[SERVER] You've already drawn that conclusion.` | :140 [P] |
| `game.combine.success` | `[SERVER] Combine success! +{0} Insight Token(s).` | :151 [P] |
| `game.combine.successResult` | `[SERVER] Combine success: {0}` | :167 [P] |
| `game.combine.cooldownActive` | `[SERVER] Combine is on cooldown for {0} seconds.` | :51 [P] |
| `game.combine.usage` | `Usage: combine <noteId1> <noteId2>` | :61 [P] |
| `game.combine.needBoth` | `You must discover both notes before combining them.` | :79 [P] |
| `game.combine.resultTitle` | popup title "Combination Result" | DTO [P] |
| `game.combine.popup.noneTitle` / `.noneBody` | "No Combination" / "Those notes don't connect (yet)." | popup DTO [P] |
| `game.combine.popup.cooldownTitle` / `.cooldownBody` | "Combine on Cooldown" / "Combine is locked. Try again in {0} seconds." | popup DTO [P] |

## 4. Deduce  (`common/commands/DeduceCommand.java`)

| Key | English | Where |
|---|---|---|
| `game.deduce.spentToken` | `[SERVER] Spent 1 Insight Token.` | :91, :148 [P] |
| `game.deduce.noTokens` | `[SERVER] No Insight Tokens available. Deduction count increased.` | :95, :152 [P] |
| `game.deduce.teamCount` | `Team deductions used: {0}` | :128, :184 [P] |
| `game.deduce.noTarget` | `There is no '{0}' here.` | :71 [P] |
| `game.deduce.invalidState` | `Error: Cannot perform deduction. Invalid player or room state.` | :44 [I] |

## 5. Examine / Look / Move  (`ExamineCommand`, `LookCommand`, `MoveCommand`, engine)

| Key | English | Where |
|---|---|---|
| `game.room.invalid` | `Error: You are not in a valid room.` | ExamineCommand:38, LookCommand:27 [I] |
| `game.examine.noObject` | `There is no '{0}' here.` | ExamineCommand:44 [P] |
| `game.move.blocked` | `You can't move {0}.` (there is no exit that way) | MoveCommand:37, GameEngine:407 [P] |
| `game.move.unknownLocation` | `Error: Your current location is unknown. Cannot move.` | GameEngine:399 [I] |

## 6. Journal  (`JournalAddCommand`, `JournalCommand`, engine)

| Key | English | Where |
|---|---|---|
| `game.journal.noteAdded` | `Note added to journal.` / `Your note was added to the journal.` | JournalAddCommand:48, GameEngine:333 [P] |
| `game.journal.full` | `The journal is full; no further entries can be added.` | GameEngine:325 [P] |
| `game.journal.duplicate` | `Note was a duplicate and not added.` | GameEngine:338 [P] |
| `game.journal.empty` | `Your journal is empty.` | JournalCommand:31 [P] |
| `game.journal.noMatch` | `No journal entries found matching '{0}'.` | JournalCommand:51 [P] |
| `game.journal.statementTitle` | `{0} Statement` (journal entry title) | QuestionCommand [P] |

## 7. Dr. Watson  (`Core/DoctorWatson.java`, engine, `AskWatsonCommand`)

| Key | English | Where |
|---|---|---|
| `game.watson.connected` | `This appears materially connected to the case.` | DoctorWatson:16 [P] |
| `game.watson.relevant` | `In light of what we now know, this detail gains relevance.` | DoctorWatson:19 [P] |
| `game.watson.distraction` | `This may be a distraction. We lack what would give it meaning.` | DoctorWatson:22 [P] |
| `game.watson.noInsights` | `I'm afraid I have no specific insights for this case.` | DoctorWatson:59 [P] |
| `game.watson.unavailable` | `Dr. Watson is not available in this case.` | GameEngine:847,858 [P] |
| `game.watson.notInRoom` | `Dr. Watson is not in this room.` | GameEngine:850,861 [P] |
| `game.watson.needTokens` | `You do not have enough Insight Tokens (need 2).` | GameEngine:864 [P] |
| `game.watson.error` | `Error receiving response from Watson.` | AskWatsonCommand:43 [I] |
| (`Dr. Watson` speaker) | already localized via `game.watsonSpeaker` | - |

## 8. Cooldown lock  (`engine/GameEngine.java` announceCooldown)

| Key | English | Where |
|---|---|---|
| `game.cooldown.lockedSeconds` | `Too many failed attempts. {0} is locked for {1} seconds.` | GameEngine:~1205 [P] |
| `game.cooldown.lockedMinutes` | `Too many failed attempts. {0} is locked for {1} minutes.` | (duration branch) [P] |
| `game.command.contradict` / `game.command.combine` | display names "Contradict" / "Combine" for the above | [P] |

## 9. Case start / state  (`BaseCommand`, `StartCaseCommand`, `InitiateFinalExamCommand`, engine)

| Key | English | Where |
|---|---|---|
| `game.case.notStarted` | `The case has not started yet. Use 'start case' to begin.` | BaseCommand:41 [P] |
| `game.case.alreadyStarted` | `The case has already started.` | StartCaseCommand:19 [P] |
| `game.case.noneSelected` | `Error: No case is currently selected or ready in this session.` | StartCaseCommand:26 [I] |
| `game.exam.cannotStartNow` | `You cannot start the final exam at this time.` / `Cannot start the final exam now.` | InitiateFinalExamCommand:18, GameEngine:965 [P] |
| `game.exam.noQuestions` | `No final exam questions are configured for this case.` | GameEngine:974,977 [P] |
| `game.exam.notActive` | `No exam is currently active.` | GameEngine:1017 [P] |
| `game.exam.started` | `--- Final Exam Started ---` | GameEngine:991 [P] |
| `game.exam.concluded` | `--- Final Exam Concluded ---` | GameEngine:1181 [P] |
| `game.exam.continuePlaying` | `1. Continue Playing` | GameEngine:1182 [P] |
| `game.exam.returnToMenu` | `2. Return to Main Menu` | GameEngine:1184 [P] |
| `game.exam.answerTooLarge` | `Invalid exam answer submission (too large).` | GameEngine:1025 [I] |
| `game.exam.dataMissing` | `Error: Exam data missing for evaluation.` | GameEngine:1103 [I] |

## 10. Multiplayer host  (`server/GameContextServer.java`)

| Key | English | Where |
|---|---|---|
| `mp.exam.onlyHost` | `Only the host can answer the exam.` | :400 [P] |
| `mp.host.useStartCase` | `As host, you can directly use the 'start case' command.` | :429 [P] |
| `mp.host.startRequested` | `Request sent to the host to start the case.` | :456 [P] |
| `mp.host.startUnavailable` | `The host is not currently available to start the case.` | :463 [P] |
| `mp.exam.notStartedYet` | `The case has not started yet. Cannot request exam.` | :472 [P] |
| `mp.exam.alreadyInProgress` | `An exam is already in progress.` | :477 [P] |
| `mp.host.useFinalExam` | `As host, you can directly use 'final exam' to initiate.` | :483 [P] |
| `mp.exam.requestSent` | `Request sent to host to initiate the final exam.` | :496 [P] |
| `mp.exam.hostUnavailable` | `Host is not available to start the exam.` | :505 [P] |

## 11. Internal / defensive errors  (translate last, or leave English)

`Error: Player context not found.` · `Error updating task: Invalid task index provided: {0}` ·
`Starting room '{}' not found…` (log only) - these should almost never reach a player.

---

## Notes on the wiring

- The `[SERVER]` prefix is decorative; in localized strings drop it or keep a neutral marker - it reads
  oddly in RTL. Recommend dropping it from the player-facing text.
- Placeholders use `{0}`, `{1}` (java `MessageFormat`, matching the existing `L10n.t(key, args)` usage).
- Suspect **names** inside messages stay as authored names (Universal/Display), not translated.
- Journal entry *titles/content* (`{name} Statement`, the statement text itself) are **case content**,
  localized in the case JSON's `localizations` - not UI chrome. Only the scaffolding label
  (`… Statement`) is a candidate for `game.journal.statementTitle`.

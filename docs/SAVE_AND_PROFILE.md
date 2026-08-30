# Save model & player profile

> Decided 2026 with Max. This REPLACES the earlier "save/resume mid-case" idea (old Phase 4.4).
> The game is played in one sitting; only the *completed* investigation is saved.

## Save model — "one go"

- **No mid-case save, no resume.** There is no autosave of an in-progress case. Closing or
  quitting a case before passing the final exam discards all progress. This is deliberate: it
  makes the final exam meaningful and turns "fewer deductions = higher rank" into a real replay
  incentive. (Implication for content: size cases for a single sitting.)
- **On solving** (passing the final exam), persist a **Completed-Case Record** keyed by
  (profile, case `universal_title`). It has two layers:
  - **Best Result** (the trophy): `bestRankName` + a language-independent rank **strength**
    (`bestDeductionsUsed`, the fewest deductions ever). Monotonic.
  - **Latest Finish** (the most recent run): `rankName`, `deductionsUsed`, `finalExamScore`,
    `dateSolved`, the final **journal** (`List<JournalEntryDTO>`) and **pinboard** (`PinboardStateDTO`).
  - format version number. Pure data only — no JavaFX/UI types (so a server could reuse it).
- **Hybrid keep-the-best.** A replay updates the record as follows:
  - **Best Result is monotonic** — `bestDeductionsUsed` improves only when the new run used *fewer*
    deductions (a strictly higher **Rank Tier**, since rank is monotonic in deductions); it is never
    raised by a worse run and never lowered. `bestRankName` snapshots the rank at the moment a new
    best is set. Comparison is by **strength, not name** — rank-tier names are localized, so the seal
    re-derives the best rank name from the strength against the *current* case-language tiers
    (`RankEvaluator`); `bestRankName` is only a fallback when the case can't be loaded.
  - **Everything else is the Latest Finish** — `rankName`, `deductionsUsed`, `finalExamScore`,
    `dateSolved`, journal and pinboard are **overwritten by the most recent solve, better or worse**.
  - A detail-less **migrated** stub never overwrites a real record (it only seeds a seal when nothing
    exists); a real finish replaces a migrated stub.
- **Storage:** local app-data dir (e.g. `~/.sherlocks-legacy/`), best-effort and offline-safe;
  `profile.json` + completed-case records (one file per case, or a single `records.json`).

## Case selection behaviour

- A case that has a Completed-Case Record shows a **"Solved" wax seal** on its casebook cover,
  with its **Best Result** rank shown (re-derived from the stored strength in the current case
  language). This can be a *higher* rank than the run you would see in Review.
- **Selecting a solved case** opens a dialog with two choices:
  - **Review investigation** — enter the solved case as a non-destructive **Review Session**
    (offline, in-process; ROADMAP Hard Constraint 1): the player can **move between rooms** and
    **look** at room art normally, and the **Journal** and **Pinboard** are **seeded from the
    Completed-Case Record** so they show the completed investigation. Gameplay mutations
    (examine-adds-entry, question, deduce, contradict, combine) and the **Final Exam** are disabled,
    and the record is **never** re-written or re-evaluated under keep-the-best — Review is read-only
    by construction. The summary (rank, deductions used, exam score, date) reflects the **Latest
    Finish** — the investigation you are actually reviewing — so it may show a lower rank than the
    seal's **Best Result**. It is shown as an accessible info panel + a "Reviewing — read only" chrome
    badge; Escape/Exit returns to case selection.
    (Default: mutations off. A maintainer flag can re-enable them for full re-investigation —
    see `.scratch/gui-review-enter-case/PRD.md`.)
  - **Play again** — start a completely fresh attempt (to beat the saved rank). On a better
    solve, the record is updated per "keep the best".
- **Selecting an unsolved case** just starts it fresh — no dialog.

## Multiplayer

> **Changed 2026-06-17 (was host-only).** Each player now records their own MP solve.

- **Each player records their own solve.** On a solved co-op Final Exam, **both** clients (host AND
  guest) write a Completed-Case Record to **their own** local store (`records.json` on their own
  machine), keyed to **their own** local profile. Each record is built from **that client's** final
  Journal (`List<JournalEntryDTO>`) and Pinboard (`PinboardStateDTO`) — the guest already receives
  both via MP sync (journal entries + `PinboardUpdateDTO`), so it builds its record from its cached
  journal + current pinboard and **does not depend on the host**. (The shared rank/deductions come
  from the broadcast `ExamResultDTO` + synced session deduction count.)
- **Hybrid keep-the-best applies per local profile**, independently on each machine: each player's
  own record keeps their monotonic **Best Result** and overwrites their **Latest Finish** on every
  co-op solve.
- The "Solved" wax seal and the Review / Play-again dialog therefore appear **independently on each
  player's** case-selection screen. Review is the unified single-player offline review mode
  (`.scratch/gui-review-enter-case`): each player walks the case with **their own** saved
  journal/pinboard, non-destructive — identical whether the case was solved in SP or MP.
- The guest's profile (name + avatar) persists locally on the guest's own machine, as before.

## Player profile (single local profile)

- One local profile: a **display name** + a chosen **avatar**, saved to `profile.json` so it
  persists across launches — the player sets it once, never re-enters it.
- **Avatar choices** are picked from the engraving **character presets** in
  `resources/images/presets/characters/` (the suspect archetypes + partner; Watson optional).
  Store the chosen preset id.
- The profile **display name feeds the multiplayer display name** (pre-fills / replaces the
  manual `UpdateDisplayNameCommand` entry) so the player doesn't retype it each session.
- Editable from a small **Profile** screen, reachable from the main menu (e.g. a profile chip in
  a corner showing the avatar + name) and/or from Settings.
- Single profile for now; multiple-profile support is out of scope.

## What gets reused (don't reinvent)

- `JournalEntryDTO` and `PinboardStateDTO` are already serializable — store them directly in the
  record.
- The engine already tracks journal, pinboard, deduction count, and rank at exam conclusion —
  the save hook is just "on exam pass, gather these into a record and hand to the save service."
- Keep the record format pure-data and versioned so a future hosted server can reuse it.

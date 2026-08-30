# Context — Sherlock's Legacy Domain Language

> **First-pass draft, with four core terms confirmed.** Most entries were inferred from project signals (class names, JSON DTOs, command names, recent commits) rather than a domain conversation. The Clue/Evidence split, Statement/Winning Message split, the Rank-Tier convention, and the Detective/Player/User layering have been **canonically resolved** (see "Naming decisions" at the bottom). Other definitions are still inferences — correct them and re-run `/ubiquitous-language` after a session where the rest of the domain is actually discussed.

## Case structure

| Term                  | Definition                                                                              | Aliases to avoid                |
| --------------------- | --------------------------------------------------------------------------------------- | ------------------------------- |
| **Case**              | A self-contained mystery the detective is hired to solve                                | Mystery, scenario, level        |
| **Invitation**        | Intro text that presents a **Case** to the detective and sets the hook                  | Briefing, prologue              |
| **Starting Room**     | The **Room** the detective enters at the start of a **Case**                            | Entry room, first scene         |
| **Task**              | An explicit objective the detective must complete during a **Case**                     | Goal, quest, mission            |
| **Final Exam**        | The closing phase of a **Case** where the detective names the culprit and answers Qs    | Endgame, conclusion, verdict    |
| **Winning Message**   | Text shown when the detective successfully closes the **Case**                          | Victory text, ending blurb      |
| **Red Herring**       | A clue or detail deliberately planted to mislead the detective                          | Decoy, false lead               |

## Characters

| Term                  | Definition                                                                              | Aliases to avoid                |
| --------------------- | --------------------------------------------------------------------------------------- | ------------------------------- |
| **Detective**         | The in-fiction protagonist solving the **Case** — the role the player inhabits          | Investigator, sleuth            |
| **Player**            | The human-controlled entity, used *only* to contrast with non-player entities (e.g. **Suspects**); never used in network/auth code | (use **Detective** for narrative; **User** for networking) |
| **User**              | A networked client identity at the auth/session boundary (e.g. a participant in a **Game Session**); never used in narrative or gameplay code | (use **Detective** for narrative; **Player** for NPC contrast) |
| **Doctor Watson**     | The detective's companion; offers **Watson Hints** in exchange for **Insight Tokens**   | Assistant, sidekick, helper     |
| **Suspect**           | A character in the **Case** who may have committed the crime                            | Witness, NPC, character         |
| **Movable Character** | Base type for any character with a position in a **Room** (currently just **Suspect**)  | Mob, actor, entity              |

## Suspect interrogation

| Term                   | Definition                                                                                                                                  | Aliases to avoid           |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| **Statement**          | What a **Suspect** says when questioned — varies with **Suspect State**                                                                     | Quote, line, testimony     |
| **Suspect State**      | The suspect's current behavioural mode: `LIE`, `TRUTH`, or `PANIC` — determines which **Statement** they give and which contradictions hit  | Mood, attitude, posture    |
| **State Transition**   | Movement of a **Suspect** from one **Suspect State** to another, triggered by a successful **Contradiction**                                | State change, flip         |
| **Contradiction**      | The detective confronting a **Suspect** with a piece of **Evidence** that conflicts with their **Statement**                                | Challenge, callout, gotcha |
| **Contradiction Rule** | A rule binding a specific **Evidence** ID to a **Suspect State** transition (what happens when this evidence is thrown at this suspect now) | Rule, trigger              |
| **Evidence**           | A referenceable, ID-bearing item presented to a **Suspect** during a **Contradiction**. Distinct from **Clue**: every **Evidence** can be referenced by ID; not every **Clue** is **Evidence**. | Proof, item, clue (as a synonym) |

## Player tools

| Term               | Definition                                                                                                                          | Aliases to avoid         |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| **Journal**        | The detective's running notebook of findings, organised by **Journal Entry Type**                                                    | Notes, log, casefile     |
| **Journal Entry**  | A single record in the **Journal** — typed (e.g. clue, dialogue, deduction) and traceable back to a source                          | Note, log line           |
| **Pinboard**       | Visual workspace where the detective arranges clues, suspects, and connections                                                      | Corkboard, board         |
| **Watson Hint**    | A hint provided by **Doctor Watson** to nudge the detective forward. Authored as localized **Case** content (one text per **Language Code**, like a **Statement** or **Clue**) and shown in the chosen case language, falling back to English when a translation is absent | Tip, hint                |
| **Insight Token**  | Currency the detective spends to obtain a **Watson Hint** — finite per **Case**                                                     | Hint token, point        |
| **Deduction**      | Two faces of one concept: (1) the **act** — the detective linking clues to a conclusion, which consumes from the **Rank Tier**'s deduction budget; (2) the **artifact** — a named, ID-bearing conclusion that act unlocks (the schema's `deductionId`), minted by a **Combine Rule** result or a **Contradiction Rule** reward, and referenceable as **Evidence** by later rules. | Guess, theory, accusation |
| **Combine Rule**   | Logic for merging two clues into a derived clue                                                                                     | Recipe, combo            |
| **Clue**           | Narrative information the **Detective** learns from a **Suspect** or **Room** — free-form, not directly addressable by ID. A **Clue** may or may not also be **Evidence**. | Hint, lead, fact, evidence (as a synonym) |
| **Object**         | A placeable, ID-bearing item that sits in a **Room** at a normalized position and can be examined. The **Object** is the container; the examine/deduce text it reveals is a **Clue**; when a **Contradiction Rule** references it by ID it is acting as **Evidence**. | Item, prop, thing, clue/evidence (for the object itself) |

## Performance & outcome

| Term                  | Definition                                                                                                | Aliases to avoid     |
| --------------------- | --------------------------------------------------------------------------------------------------------- | -------------------- |
| **Rank Tier**         | A tier of detective performance, scored by how few **Deductions** were used (fewer = higher tier)         | Rank, grade, score   |
| **Max Deductions**    | The cap on **Deductions** that still qualifies for a given **Rank Tier**                                  | Budget, limit        |
| **Winning Message**   | Tier-specific congratulatory text shown when the **Case** is closed at that **Rank Tier**. (Java field is named `winningStatement`; in domain talk, **Statement** refers only to a **Suspect**'s dialogue, so call this a **Winning Message**.) | Winning statement, endgame text |

## Save & progress

| Term                     | Definition                                                                                                                                                                | Aliases to avoid                 |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------- |
| **Completed-Case Record**| The pure-data artifact written when a **Detective** passes a **Case**'s **Final Exam**. It holds two layers: the **Best Result** (the monotonic best ever — see **Keep the Best**) and the **Latest Finish**: the most recent run's **Rank Tier** name, **Deductions** used, **Final Exam** score, date solved, final **Journal** and **Pinboard** state. Plus a format version. Keyed by **Universal Title**; never holds UI types. | Save, save file, progress entry  |
| **Best Result**          | The monotonic best a **Detective** has ever achieved on a **Case**: a language-independent rank **strength** (the fewest **Deductions** ever used, from which the best **Rank Tier** is re-derived per the current **Language Code**). Only ever improves; never lowered by a worse replay. Shown on the casebook "Solved" seal. | Best rank, high score |
| **Latest Finish**        | The most recent solve's run data on a **Completed-Case Record** — **Rank Tier**, **Deductions**, score, date, **Journal**, **Pinboard**. **Overwritten on every solve**, better or worse. This is what **Review** shows. | Last run, recent solve |
| **Keep the Best**        | The hybrid replay rule. The **Best Result** is monotonic — a replay raises it only when its rank strength beats the stored one (fewer **Deductions**), never lowers it. Everything else (the **Latest Finish**) is overwritten by the most recent solve regardless of outcome. So the seal keeps the trophy while **Review** always shows the latest investigation. | Highscore, overwrite             |
| **Migrated Record**      | A minimal **Completed-Case Record** created from the pre-record solved-set on first run: it marks the **Case** solved (so the seal shows) but carries no **Rank Tier**, **Deductions**, **Journal**, or **Pinboard** — so **Review** degrades gracefully. A **Latest Finish** (real solve) replaces it, but a migrated stub never overwrites a real record. | Stub record, empty record        |
| **Review**               | Opening a **Completed-Case Record**'s **Latest Finish** read-only: its saved **Journal** and **Pinboard**, alongside a summary of that run's **Rank Tier**, **Deductions**, score, and date. (The seal may show a higher **Best Result** rank than the run being reviewed.) No **Game Engine**, no terminal — a record viewer, not a playthrough.        | Replay (that's "Play again"), spectate |
| **Universal Title**      | The stable internal id of a **Case** (`universal_title`), used as the key everywhere a **Case** must be referenced across runs (selection, **Completed-Case Record**, session protocol) — distinct from the localized display title.        | Case id, title (localized)       |

## Localization

| Term                   | Definition                                                                          | Aliases to avoid    |
| ---------------------- | ----------------------------------------------------------------------------------- | ------------------- |
| **Language Code**      | Identifier for a supported in-game language (e.g. `en`, `ar`)                       | Locale, lang        |
| **Localized Case File**| Single-language adapter view of a multi-language **Case** definition                | Translated case     |
| **Universal Name**     | The language-independent name of a **Room**, **Object**, or **Suspect** — the command-safe identifier that command parsing, autocomplete, and the terminal use, and the key that joins universal structure to each localization. Stable across every **Language Code** (sibling of **Universal Title**). | Canonical name, id (loosely), name |
| **Display Name**       | The per-language, GUI-facing name of a **Room**, **Object**, or **Suspect**, authored inside a localization block (`displayName`). Shown in the object/suspect click popup and everywhere a **Room** is displayed; falls back to the **Universal Name** when absent. Never used to resolve a command. | Localized name, translated name |

## Game sessions

| Term                     | Definition                                                                          | Aliases to avoid          |
| ------------------------ | ----------------------------------------------------------------------------------- | ------------------------- |
| **Room**                 | A physical location in the **Case** world that the detective navigates              | Scene, area, location     |
| **Single-player Session**| A local **Case** playthrough with no networking                                     | Solo game                 |
| **Game Session**         | A server-managed multiplayer **Case** playthrough                                   | Multiplayer game, match   |
| **Game Engine**          | The single authority for **Case**-play rules and state, shared by **Single-player Session** and **Game Session** | Context (for rules), core, logic layer |
| **Host**                 | The **User** who created a **Game Session**; the only participant who may start the **Case** or initiate the **Final Exam** | Owner, player 1, admin    |
| **Review Session**       | A non-destructive, offline, in-process replay of a solved **Case**: the **Detective** can move between **Rooms** and view the **Journal** and **Pinboard** seeded from the **Completed-Case Record**, but gameplay mutations and the **Final Exam** are disabled, so the saved record is never changed | Read-only viewer, replay  |

## Relationships

- A **Case** has many **Rooms**, **Suspects**, **Tasks**, and **Rank Tiers**, and exactly one **Final Exam** and one **Starting Room**
- A **Suspect** is in exactly one **Suspect State** at any moment (`LIE` / `TRUTH` / `PANIC`)
- A **Contradiction Rule** binds one piece of **Evidence** to one **State Transition** for one **Suspect** in one **Suspect State**
- A successful **Contradiction** causes a **State Transition** on the **Suspect**
- A **Detective** spends **Insight Tokens** to obtain **Watson Hints**
- An **Object** belongs to exactly one **Room**; a **Room** has many **Objects**. An **Object** reveals a **Clue** when examined and may serve as **Evidence** when a **Contradiction Rule** matches its ID
- A **Journal Entry** belongs to one **Journal** and has one type and one source
- A **Case**'s final **Rank Tier** is determined by the count of **Deductions** used vs each tier's **Max Deductions**
- A **Single-player Session** and a **Game Session** both play a **Case** through the same **Game Engine**; only a **Game Session** has a **Host**

## Example dialogue

> **Dev:** "When the detective presents **Evidence** to a **Suspect**, what decides whether the **Statement** changes?"
>
> **Domain expert:** "Each **Suspect** has a **Suspect State** — `LIE`, `TRUTH`, or `PANIC`. The current state has its own **Statement** and its own set of **Contradiction Rules**. If the evidence ID matches a rule for that state, you get a **State Transition** and the suspect starts giving a different **Statement**."
>
> **Dev:** "So a **Contradiction Rule** only fires in one state?"
>
> **Domain expert:** "Right. The same evidence might do nothing while the **Suspect** is in `LIE`, then crack them into `PANIC` once they've already moved to `TRUTH`. The rule lives on the state, not on the suspect as a whole."
>
> **Dev:** "And **Deductions** — those are separate from contradictions?"
>
> **Domain expert:** "Yes. **Contradictions** are how you crack **Suspects**. A **Deduction** is the detective's final reasoning move — linking clues to a conclusion. Each **Deduction** burns part of the budget that determines the final **Rank Tier**, so the detective wants to make fewer, sharper deductions, not spam them."

## Naming decisions

These four splits are **canonical** — they govern issue titles, code comments, refactor names, and dialogue with the domain expert. Where Java class/field names already disagree (e.g. `winningStatement`, the `Rank` class), the existing identifiers may stay until a deliberate rename; the domain vocabulary above is what conversations and new code should use.

- **Clue vs Evidence — distinct.** A **Clue** is free-form narrative information the **Detective** learns from a **Suspect** or **Room**. **Evidence** is a referenceable, ID-bearing item used in a **Contradiction**. The two categories overlap (some clues are also evidence) but are not interchangeable. Never say "evidence" when you mean a piece of learned narrative; never say "clue" when you mean the ID-bearing thing the **Contradiction Rule** matches against.
- **Statement is suspect-only.** **Statement** always refers to a **Suspect**'s dialogue. The case-completion text on a **Rank Tier** is a **Winning Message**, even though the Java field is `winningStatement`.
- **Rank Tier is the canonical noun.** Always say **Rank Tier** in domain talk, never bare "Rank". The Java class `Rank` is a code-level name; the domain concept is the tier of detective performance. This avoids collision with verbs like "ranking" and generic notions of player rank.
- **Detective / Player / User are layered, not synonyms.**
  - **Detective** — the in-fiction role. Use this in narrative, gameplay, and most domain code (deducing, asking Watson, navigating rooms).
  - **Player** — used *only* to contrast with non-player entities (NPCs, **Suspects**, environment).
  - **User** — used *only* at the network/auth boundary (e.g. participants in a **Game Session**, session identities). Never appears in narrative or gameplay code.
  - If you find yourself reaching for two of these in the same sentence, you're probably crossing a layer — stop and pick the one that matches the layer you're actually in.

# Case Design System — Sherlock's Legacy

> The framework for authoring fair, solvable, mechanically-complete cases. Read this (with CONTEXT.md
> for vocabulary and PROJECT_SPECIFICATION.md §6.2 for the case JSON schema) before designing or
> building any case. Every case must satisfy the rules below and pass `extractors/CaseValidator`.

## 0. Agreed scope for the three test cases

- All three are **classic manor / country-house whodunits** (one consistent world; they differ by
  size and complexity, not setting type).
- **Easy** teaches a *light touch of both* advanced mechanics — exactly **one simple contradict and
  one simple combine** — kept gentle. **Medium** uses both fully. **Hard** uses deep chains of both.
- **English first** — author fully in `en`; add other localizations as a later pass (the schema and
  validator still require the `en` localization to be complete).
- Process: **design the mystery with the maintainer (grill-with-docs) → build the case folder →
  validate**. One case per session.

## 1. The rules every case must obey

1. **One culprit, closed circle.** A fixed cast at the house; exactly one is guilty (v1). No outsiders.
2. **Motive–Means–Opportunity for everyone.** Every suspect has a real **motive**; the **culprit has
   all three** (motive + means + opportunity); every **innocent is cleared by a discoverable failure**
   of at least one (a verifiable alibi, no access to the means, or a motive that collapses on
   scrutiny). No suspect is filler — each one's MMO is authored and referenced by a clue.
3. **A solvable chain.** The culprit, the key evidence, and the method must be reachable *purely from
   authored clues* via examine → question → contradict → combine → deduce. No required guessing, no
   unreachable solution, no clue that exists only in the author's head.
4. **One fair twist, foreshadowed.** Exactly one central reversal per case (the obvious suspect is
   innocent / a hidden relationship / a frame-up / staged crime), with **at least one earlier clue
   that foreshadows it**. Fair play — never an arbitrary reveal.
5. **Every lie is contradictable.** Each suspect whose statement is a LIE has **at least one evidence
   clue** that contradicts it (a Contradiction Rule: evidence id → suspect → state change), and the
   flip yields progress (a Deduction or an opened path).
6. **Red herrings must resolve.** Misleading clues are encouraged, but **each one can be explained
   away** by something discoverable, so the player is never permanently misled.
7. **Technical integrity (validator).** Room graph connected from `startingRoom`; neighbor links
   consistent/bidirectional where intended; every Contradiction-Rule evidence id and every Combine
   `requires` id resolves to a real object/journal source; every `resultDeductionId` is unique; every
   suspect has a **home room**; every localization has title/invitation/roomDetails/objectDetails
   covering every universal room/object plus a `final_exam` with ≥1 question; every `imagePath`
   resolves or falls back to a room/character/object **preset**.
8. **The exam mirrors the solution.** Final-exam questions are answerable **only** from the deductions
   the chain produces — typically: *who is the culprit*, *the key piece of evidence*, and *the
   method/motive*. No exam answer should require information the player couldn't have deduced.

## 2. The solution-chain model (how mechanics carry the mystery)

Author the case as a directed chain the player walks:

- **Examine** an Object → a **Clue** (its examine/deduce text). Objects double as **Evidence** when a
  Contradiction Rule references their id.
- **Question** a Suspect → their **Statement** for the current Suspect State (LIE → TRUTH → PANIC).
- **Contradict** a suspect's lie with the right **Evidence** → flips their state (LIE→TRUTH or →PANIC),
  revealing a truer statement and usually minting a **Deduction**.
- **Combine** two Clues/Deductions whose ids satisfy a **Combine Rule** → a new **Deduction** (e.g.
  *means* + *opportunity* = *the culprit could have done it*).
- **Deduce** (costs an Insight Token) names a Deduction the player has reasoned out.
- **Final Exam** asks the player to assemble the named Deductions into the verdict.

Design the chain backwards from the verdict: decide the culprit + key evidence + method, then author the
Contradiction/Combine rules and clues that make exactly that path discoverable, then add red herrings
and innocent-clearing clues.

## 3. Difficulty tiers (the dials)

| | Easy (practice) | Medium | Hard (the big one) |
|---|---|---|---|
| Rooms | ~3 | ~5–6 | ~8–10 |
| Suspects | 4–5 | 6–7 | 8–10 |
| Objects / clues | ~4–6 | ~8–10 | ~12–16 |
| Insight tokens | **2** (no deduce-spam) | ~5–6 | ~8 |
| Contradict rules | **1** (very simple) | several | many, chained |
| Combine rules | **1** (very simple) | several | many, chained |
| Twist | 1 small | 1 + a sub-plot | layered (frame-up / hidden identity) |
| Exam questions | 2–3 | 3–4 | 4–5 |
| Rank tiers | lenient | moderate | strict (fewest deductions wins) |

Token economy: tokens gate Deduce and targeted Ask-Watson. Easy keeps tokens scarce (2) *and* the path
short so they're never needed in bulk; harder tiers grant more tokens but demand more deductions and set
stricter rank thresholds.

## 4. Mystery archetypes (all in the manor frame)

- **Easy — simple closed-circle.** A small crime (a theft or a single death) with one alibi to break
  (the one contradict) and one pair of clues to join (the one combine). One gentle twist.
- **Medium — closed-circle murder + sub-plot.** A poisoning/murder where a secondary thread
  (blackmail, an affair, a debt) muddies the water; the obvious suspect is framed, a trusted figure is
  guilty. Full contradict + combine.
- **Hard — layered closed-circle.** A murder (or double event) at a large gathering with a **frame-up**
  and a **hidden identity/relationship**; multiple alibis to break and deductions to assemble before
  the real culprit surfaces.

## 5. Per-case authoring checklist

Each case folder `cases/<slug>/` (with its own `images/`) provides, per the schema (§6.2):

- `universal_title`, `startingRoom`, author; the **Rooms** graph (neighbors, home rooms) and **Objects**
  (normalized positions, imageScale).
- **Suspects**: identity, home room, per-state statements (LIE/TRUTH/PANIC), `stationary` flag.
- **Contradiction Rules** (evidence id → suspect → state change → reward Deduction) — ids via the
  Deduction/Object registries, never free-typed.
- **Combine Rules** (`requires` ids → `resultDeductionId`).
- **Deductions** registry (every mint site creates one; every reference site picks from it).
- **Tasks** (player-facing objectives), **Watson Hints** (structured `watson.hints`, localized),
  **Rank Tiers** (by deductions used), **Final Exam** (questions with slots/choices; the correct
  combination stays server-side).
- **Localizations**: `en` complete (title, invitation, room/object/suspect names + details, watson
  hints, exam); other languages optional now.
- Every suspect has an MMO note in the design doc, each backed by a clue.

## 6. The three planned cases (parameters; plots planned with the maintainer)

1. **Easy** — 3 rooms, 4–5 suspects, 4–6 objects, 2 tokens, **1 contradict + 1 combine**, 1 small twist,
   2–3 exam questions. Goal: a short, satisfying first solve that previews both mechanics gently.
2. **Medium** — 5–6 rooms, 6–7 suspects, 8–10 objects, ~5–6 tokens, full contradict + combine, 1 twist +
   a sub-plot, 3–4 exam questions.
3. **Hard** — 8–10 rooms, 8–10 suspects, 12–16 objects, ~8 tokens, deep contradict + combine chains,
   layered twists (frame-up + hidden identity), 4–5 exam questions.

Plots, rosters (with each suspect's MMO), the exact clue chain, the Contradiction/Combine graph, and the
exam are designed per case in a grill-with-docs session, then built and validated.

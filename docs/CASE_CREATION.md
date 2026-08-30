# Case Creation Guide

One of the best things about *Sherlock's Legacy* is that **cases are data**. A mystery is a
self-contained JSON file plus its art — you can write a whole new case without touching a line of
engine code, and it drops straight into the game. This guide covers what a case is, the two ways to
author one, the format, the design habits that make a mystery actually *fair and satisfying*, and a
working method for generating its art with AI tools.

> This is the public authoring guide. A much deeper, worked-example walkthrough (design theory, full
> field-by-field reference) lives in [`docs/CASEBOOK.md`](CASEBOOK.md) — **note that it contains
> spoilers for the bundled cases.** This guide references the bundled cases by *technique*, not by
> solution, so you can read it safely before playing them.

---

## What a case is made of

Learn these parts — everything else refers to them:

- **Rooms** — the places the detective walks between. A small connected map (≈3 rooms for an easy
  case, up to ~10 for a hard one).
- **Objects (clues)** — things you can *examine*. Each yields a clue; some double as **evidence** used
  to break lies or feed a **combine**.
- **Suspects** — the people you *question*. Each hides something; **exactly one is the culprit.**
- **Combines** — deductions earned by pairing two clues (or a clue and an earlier deduction) into a
  new, named conclusion. This is how a case rewards *thinking*, not just clicking everything.
- **Contradictions** — deductions earned by presenting the right evidence to a lying suspect, flipping
  their statement from a lie to the truth.
- **Insight tokens** — a small, limited resource spent to *deduce* (combine or contradict) and to ask
  Watson pointed questions. Scarcity is what makes the player choose carefully instead of brute-forcing
  every pair of clues.
- **The case file** — the dossier: the victim, an overview, and a profile of each suspect.
- **Watson** (or your case's own author-named helper — see below) — the hint system: a general nudge, a
  contradiction nudge, and per-suspect red-herring commentary.
- **The final exam** — the ending, where the player names the culprit and reconstructs the solution.

---

## Two ways to author

### 1. The Case Maker (recommended)

The game ships with a built-in **Case Maker** — a visual editor. It's the easiest and safest way to
build a case because it writes valid JSON for you and validates as you go. With it you can:

- create **rooms** and connect them into a map,
- **place** suspect and object sprites visually on the room plate (correct scale and feet-on-floor
  positioning handled for you),
- write **suspects**, their statements (truths and lies), and **objects/clues**,
- wire **combines**, **contradictions**, **Watson hints**, **tasks**, **rank tiers**, and the **final
  exam**,
- choose the **editing language** independently of the interface language, to translate a case in
  place,
- **validate** against the same rules the engine enforces (with a copyable list of warnings/errors),
- and **save/export** a ready-to-play case folder.

### 2. Hand-editing JSON

You can also write or tweak the JSON directly — useful for bulk edits, version control, generating
content with an LLM, or scripting a validator of your own. The shipped cases under `cases/<slug>/` are
the best working references. Whichever way you author, the case must pass validation (below) to load.

---

## Anatomy of a case file

A case is a folder, `cases/<your-slug>/`, containing the case JSON and an `images/` subfolder for its
art. The JSON describes the whole mystery. At a high level it contains:

- a **`universal_title`** — the stable, language-independent key for the case (used everywhere
  internally),
- top-level **`startingRoom`** and **`startingInsightTokens`** — where the detective begins and how
  many insight tokens they start with,
- **`metadata`** — the case title and author, the soundtrack, the Watson/helper sprite settings, and
  optionally an author-configured **`detectiveName`** / **`helperName`** (see below),
- **`rooms`**, each with a universal name, a `neighbors` map of exits, an `imagePath`, and the objects
  placed inside it,
- **`case_file`**, holding the **`victim`**, the case **`overview`**, and a **`suspect_profiles`** dossier
  entry per suspect (profession, age, relationship to the victim, a short bio),
- **`combine_logic`** — an array of combines: which clue ids they `requires`, the `resultDeductionId`
  they produce, the reward text, and any `tokenReward`,
- **`suspects`**, each with a universal name, a display name, placement, a `statement`/`clue`, and
  *optionally* an `initialState` + `states` machine (see **Writing suspects**, below — this is the part
  most first-time authors get subtly wrong),
- **`watson.hints`** — `general`, `contradiction`, keyed hint text,
- **`red_herrings.suspects`** — which suspects are flagged as misleading, and the narrative that
  eventually clears them,
- **`leads`** — optional guided objectives with `visibleWhen` / `completeWhen` conditions,
- **`tasks`**, **`rankingTiers`**, and the **`final_exam`** — five questions, each with two answer
  slots,
- a **`localizations`** map — per-language display text for everything above.

> Look at a shipped case alongside this list to see each part in a real file.

### Author-named detective and helper

By default the player character is Sherlock Holmes and the hint-giver is Dr. Watson, and `ask watson`
always works as a command. But a case can set its own pair via `metadata.detectiveName` and
`metadata.helperName` — a single string, not translated per-language, since it's a proper name. If set:

- the Case File's overview shows your names instead of the defaults,
- the dialogue bubble shows your helper's name,
- `ask <helperName>` becomes a working alias alongside `ask watson` (case-insensitive, multi-word names
  supported).

This is how a case set outside the Holmes continuity — a different city, a different century, a
different language's own detective tradition — gets to feel like its own thing rather than a reskin.
The helper's sprite still needs the same care as any other figure (see the art section): unlike
Watson, an author-named helper usually **roams the map with the player**, and the player must be in the
same room to `ask` them for a hint, so their sprite has to composite convincingly into every single room
in the case, not just sit in a dialogue box.

### Two naming layers — important

Every room, object, and suspect has **two names**:

- a **Universal Name** — a stable, command-safe id (e.g. `torn_letter`, `the_valet`). Commands, the
  parser, and autocomplete use *only* this. It never changes between languages.
- a **Display Name** — the pretty, per-language text shown in the GUI.

Cases are keyed on the **Universal Title**, not the localized title. **Rule of thumb:** pick universal
names once and never translate them; put all translations in the display names / `localizations` map.

---

## Localization

Case content is translated **inside the case file**, in a `localizations` map keyed by language code
(`en`, `ar`, `ru`, `zh`, `tr`, `de`, `fr`, `es`). This keeps a case one portable file carrying all its
own languages. A case doesn't have to ship every language — three well-finished languages beat eight
half-finished ones — but **every language you do ship must be complete**: every room, every object,
every suspect state, every exam choice, every Watson hint, every red-herring narrative. A single missing
string in one locale is enough to fail validation.

### If your case has a wordplay or script-based clue

Some of the best clues aren't factual, they're *linguistic* — a dropped letter, a name hidden inside an
ordinary word, a signature that reads two ways. These are also the clues most likely to break in
translation, because the trick usually only exists in the language it was born in. Two habits keep them
alive:

1. **Author the tell in whichever language it's native to, first.** If the trick is a Latin typewriter
   fault, write it in the Latin-alphabet language first and treat it as a foreign artifact everywhere
   else. If the trick lives in Arabic script or Arabic grammar, write it in Arabic first and build the
   other languages' versions *around* that anchor, not the other way round.
2. **In every language where the trick can't translate directly, keep the original word or phrase
   untouched and add a short gloss** — enough for the player to understand what the word means and why
   it's ambiguous, without handing them the deduction outright. The goal is that a player in any
   language makes the same leap, even if the route in is a sentence longer for some of them.

Whichever approach you take, that string must then stay **byte-identical** everywhere it's referenced —
the clue object's `examine`, any `combine_logic` text that quotes it, and the suspect line that finally
says it aloud. A trick that reads two different ways in two different places isn't a clue, it's a bug.

---

## Images & building a case's art

Art paths are **relative to the case folder**: use `images/<file>.png`. Absolute paths and drive
letters are refused for security. If art is missing, the game falls back to hand-drawn preset
engravings and then procedural placeholders — so a case is playable before its art exists. But a
finished case wants finished art, and an AI image generator (attached image-to-image, so you can chain
references) is a practical way to produce it. Below is a working method, not a specific tool's syntax.

### Decide the style once, and write it as a reusable block

Pick a style — painterly, flat graphic-novel, whatever fits your setting and cast — and write it as a
**shared style suffix** you append to every single prompt in the case: rooms, suspects, objects, and the
helper alike. Consistency across dozens of images comes from repeating the same handful of style words
every time, not from re-describing "the mood" freshly in each prompt.

### Generate one room first, and use it as your anchor

Pick a representative, typical room — cluttered enough to show the style's texture, ordinary enough to
be a fair reference — and generate it first. Then **attach that image as a style/lighting reference to
every subsequent prompt.** This single habit does more for visual cohesion than any amount of extra
prompt text.

### Fix one lighting contract for the whole case, and state it every time

Pick a warm source and a cool source and a fixed direction — e.g. *"warm tungsten key light from the
LEFT, cool blue-white light from the RIGHT"* — and repeat it, direction and all, in every room and every
figure prompt. This is what lets a suspect cut out on green screen actually look like they're standing
in their own room: the room was lit warm-left/cool-right, so the figure was too.

### Every object needs a reason to exist

Don't prompt "assorted clutter." A room's contents should come from **who lives there and what they
do** — a key board because this character holds every key in the building, a stack of ledgers because
this character's whole life is paperwork, a certain machine because this character's trade requires it.
If you can't say *why* an object is in a room, it doesn't belong in the prompt. This also makes rooms
double as characterization before the player ever meets the suspect who lives there.

### Chroma-key figures and hand-held objects; write rooms as full scenes

- **Suspects, the detective's helper, and any object meant to be cut out and placed** go on a flat,
  evenly lit **chroma-key green background** (a mid green, e.g. `#00B140`), full body, head to toe, with
  margin above and below so nothing is cropped. State explicitly that **no green appears in clothing or
  props** — an inconvenient truth if your case has a character who'd naturally wear green.
- **Rooms** are full establishing shots: no people in them (the figures are composited in later by the
  engine/editor), atmospheric, lit to the shared contract.

### Anchor scale explicitly, every time

Generators size objects by *narrative importance*, not real-world size, unless you stop them. A pair of
spectacles described lovingly in three clauses will render as a dramatic foreground object even if you
also typed "small." Fixes that actually work:

- give an **absolute dimension** ("about 14 cm across"),
- give a **comparison to something already in frame** ("no wider than one floor tile," "about the size
  of a hand"),
- and state outright that objects are in **true real-world scale** relative to named large furniture in
  the room, with nothing enlarged for emphasis.

The same applies to camera distance: "wide shot" alone under-delivers. Describing the room's main
furniture as *"modest in the frame against the height of [something tall behind it]"* is what reliably
pulls the camera back to a full establishing shot with room left over for figures to be placed in.

### If you want objects grounded *in* their room, generate them together and crop

There are two valid pipelines and they trade off against each other:

- **Separate object sprites** (object alone on green, generated after the room) give you full macro
  detail — ideal for anything a player needs to *read* closely, like a letter's ghost impression or a
  document's stamp — but can mismatch the room's exact lighting and angle unless you attach the room as
  a reference and say so explicitly.
- **Objects generated inside the room and cropped out afterward** are automatically lit, angled, and
  coloured to match, which looks more integrated — but small objects (papers especially) will often
  render too soft or small to read as a clue at full size. Use this for larger, simpler objects (a tool
  bag, a wall fixture, anything unpaper-y); for small detailed objects, generate the room this way for
  the scene, then **regenerate that one object separately with the room attached as a reference**,
  asking for it "isolated and enlarged to full detail, matching its exact colour, angle, and lighting
  from the reference." You get the grounding and the readability.

If you use the integrated method, **reserve empty space explicitly** in the room prompt for wherever a
suspect or the helper sprite will later be composited — generators fill empty space by default, so say
which part of the frame must stay clear.

### If your case has several similar paper clues, differentiate them on purpose

A case with six or eight documents risks all of them reading as "a piece of paper" once cropped to
thumbnail size, especially if you (correctly) ask for no legible text so a real-world script doesn't
render as garbage. Build a small table before you prompt anything: one row per paper clue, three
columns — **silhouette** (tall ledger vs. small slip vs. folded letter), **colour** (cream vs. pink
carbon vs. faded blue), and **one distinct anchor prop** (a paperclip, a stamp, a pen laid across it).
Enforce that no two rows share more than one column.

### No legible text, ever, in any language

Every image generator renders text as garbled pseudo-glyphs, and this is *worse*, not better, in a
script the player actually reads (a broken attempt at Arabic or Chinese reads as a bug; broken Latin
"lorem ipsum" at least reads as intentional set-dressing). Put "no legible text, script suggested only
as abstract marks" in every room and object prompt, and rely on the silhouette/colour/prop system above
to carry the differentiation instead of what's written on the page.

### A minimal worked example

```
SHARED STYLE SUFFIX (append to everything):
— [your chosen style words], warm tungsten key light from the LEFT, cool blue-white light
from the RIGHT, [your setting]atmosphere, no text, no legible script, no watermark.

ROOM (generate first, becomes the anchor):
Wide establishing interior shot, camera well back, the room seen from floor to ceiling with
open floor across the foreground. [Room description built from who lives here and why each
object is present.] The [named clear area] is left open — empty, nothing placed there. All
objects in true real-world scale relative to [a named large object]; nothing enlarged for
emphasis. No people.
(+ shared suffix)

SUSPECT (attach the anchor room as reference):
Full-length figure of [character], facing the viewer directly, [age/build/features], [clothing
and one or two hand-held props that also exist as objects or room contents]. Entire figure head
to toe, margin above and below, nothing cropped. Isolated on a flat, evenly lit chroma-key green
background (#00B140), no scenery, no green in clothing or props.
(+ shared suffix)

OBJECT (attach the anchor room as reference if it should match a specific room's light):
[Object], [absolute size or in-frame comparison]. Isolated on a flat, evenly lit chroma-key
green background (#00B140), clean edges for cut-out, bold readable silhouette.
(+ shared suffix)
```

---

## Keys to writing a cohesive case

A good case feels inevitable once solved and impossible before. These are the habits that get you
there, distilled from building several full cases end to end.

### Start from the victim, not the killer

The strongest victims are the ones who touch every suspect's life at once — a person everyone in the
cast independently *owed something to* (a favour, a debt, a secret kept) rather than a person everyone
simply hated or was owed money by. A victim like that gives you organic, distributed motive across a
whole cast without inventing a separate grievance from scratch for each suspect, and their death is
what actually removes something the whole cast depended on — which is what makes it feel like a loss,
not just a puzzle's starting gun.

### Give every suspect Motive, Means, and Opportunity — and give the culprit all three

This is the genre's oldest rule because it's still the one that separates a mystery from a guessing
game. Every suspect needs a plausible *reason*, a plausible *way*, and a plausible *chance* — and every
innocent one needs to be **clearable by something the player can actually find**, not by authorial
fiat. If a suspect's innocence is never provable from clues, the player didn't solve anything by
trusting them; they guessed right.

### Weaponize the player's expectations

Work out, before you write a line of suspect dialogue, what a player who has read a few detective
stories will *assume*: that the person everyone's angriest at did it; that the newcomer with new money
did it; that the person with the most to gain financially did it. Then deliberately don't make it that
person. The culprit who works best is usually the one the setting itself trains the player to trust —
someone whose entire narrative function is to be reliable, present everywhere, and unremarkable, so
that suspecting them requires *evidence* rather than *vibe*. A motive tied to something structural or
professional (a secret about their trade, not a generic grudge) reads as more surprising and more fair
at once, because it couldn't have been guessed from personality alone — only from a clue.

### Keep an honest core

Not every suspect needs to be a liar with a contradiction to break. A case is easier to reason about,
not harder, when a minority of suspects are flatly honest witnesses: statement and clue, no state
machine, nothing to flip. Their testimony becomes the fixed ground the player can build deductions on —
without at least a few reliable voices, *nothing* in the case can be trusted, and the player has no
foothold. A workable ratio across a mid-size cast is roughly half-to-two-thirds liars, the rest honest;
adjust for your cast size, but always keep some honest witnesses, and make sure the ones you keep honest
are the ones carrying facts your combines or contradictions actually depend on — their reliability is
what makes those facts usable.

### Red herrings must resolve, always

A suspicious character who's simply left hanging, never confirmed guilty or cleared, isn't a red
herring — it's an unfinished thread, and players notice. Every suspect flagged as a red herring needs an
actual, discoverable way to be cleared: a contradiction that flips them to an innocent truth, or a piece
of evidence elsewhere that accounts for what looked suspicious about them. If a character's only
clearance is a line of hint-text asserting they're innocent, with no gameplay action that proves it,
that's a gap to close, not a red herring to leave alone.

### Chain your deductions

A flat list of "combine A+B → fact" repeated eight times is weaker than a **chain**: combine A+B into an
intermediate deduction, then combine *that* with clue C into something bigger, and so on. Chained
combines reward players who've been paying attention across the whole case, not just in one room, and
they let a single late clue re-contextualize several earlier ones at once — which is exactly the feeling
a good mystery's climax should produce. Aim for at least a few of your combines to consume an earlier
deduction rather than two raw clues.

### Decouple geography from guilt

Never place a clue that accuses a suspect inside that suspect's own room — a player will read the
co-location as confirmation before they've reasoned anything out, which cheapens the deduction. The one
acceptable exception is a *decoy*: a red herring's own space can hold the evidence that makes them look
guilty, precisely because that's a trap you intend the player to walk into and later walk back out of.
The culprit, and every other liar's contradiction evidence, should sit somewhere they'd have no reason
to keep it.

### Let the setting do characterization

A suspect's room, clothing, and possessions should tell you who they are before they say a word — what
they can and can't afford, what their trade requires, what they're embarrassed by. This isn't just an
art-direction nicety; it's fair-play design. A player who intuits a character's situation from their
surroundings is doing detective work, and a case that rewards *noticing* rather than only *reading* is a
better game.

---

## Writing suspects

### The two valid shapes — and the mistake that breaks the wrong one

A suspect can be authored one of two ways, and **both are correct** — but they must be built correctly
for their shape, or the suspect goes silent in play.

**Shape A — a liar with a state machine.** Has `initialState` (almost always `"LIE"`) and a `states`
object with at least a `LIE` state and a `TRUTH` state. The `LIE` state carries one or more
`contradictions`, each naming the `evidenceId` that breaks it, the `nextState` it flips to, a
`rewardDeductionId`, and a `successMessage` shown when the player breaks it.

**Shape B — an honest witness.** Has a top-level `statement` and `clue` and **nothing else** — no
`initialState`, no `states`. This is not a lesser or incomplete suspect; it's a deliberate, valid
character who has nothing to hide and nothing to flip.

**The pitfall:** if your engine's dialogue renderer resolves a suspect's text *only* through
`states[currentState].statement`, then a valid Shape B suspect with no `states` will silently render
**nothing at all** — not an error, just an empty dialogue box, invisible until someone opens that
specific suspect. This is an engine-level gap, not a data mistake, and the fix belongs in the renderer
(fall back to the top-level `statement` when no state machine exists), not in the case file — don't
"fix" a legitimate honest witness by giving them a pointless state machine just to make them render. If
you hit this while testing your own case, that's the bug to report, not the data to change.

### Writing a contradiction that's satisfying to break

A good `LIE` statement isn't a flat denial — it's a plausible, sympathetic account that happens to
contain the one detail the evidence disproves. Write the lie as something a reasonable, frightened, or
proud person would actually say, not as an obvious dodge. The `TRUTH` state that follows should do three
things at once: concede the specific point the evidence proved, explain *why* they lied (protecting
someone, protecting their livelihood, plain fear, an old shame), and — if they're not the culprit —
supply the alibi or fact the case still needs from them. A `successMessage` lands best when it's a small
piece of character behaviour (a pause, a gesture, one line of dialogue) rather than a flat "you were
right."

### A suspect can have more than one contradiction

Nothing requires a single breaker per suspect. A pivotal character — especially the culprit — can carry
two separate contradictions on the same `LIE` state, each with its own evidence and its own
`successMessage`, representing two different angles of unmasking (for instance: the *how* and the
*why*, broken by two unrelated pieces of evidence, either order). This is one of the more satisfying
patterns available and worth using for whoever your case's central reveal belongs to.

### Every suspect needs a clue line regardless of shape

The `clue` field — shown alongside the statement — is where you tell the player *what to notice* about
this person without telling them the answer: what their alibi rests on, what they're visibly protective
of, why they might be lying about something small while being honest about the murder. Write it as an
investigator's marginal note, not a summary of their dialogue.

---

## Writing the final exam

The final exam is five questions, each with two fill-in-the-blank slots and a bank of choices per slot.
It's the last thing the player does, and it's the truest test of whether the case was actually fair.

### Mirror the solution's actual shape

Don't invent trivia. Walk your own solution and write one question per major beat — typically: who did
it and why; how the frame or misdirection was built; how the true timeline or method was uncovered; how
the key piece of misdirection got resolved; and one question that rewards a secondary thread most
players could overlook (a subplot, a red herring's actual innocence, a minor character's fate). Every
correct answer must be something the player could only know from clues they found — never from
world-knowledge, never from a guess dressed as a question.

### Design the wrong answers as carefully as the right one

A distractor's job is to be **plausible enough that only genuine reasoning rules it out**. A weak
distractor is either obviously silly (wastes a slot) or logically impossible given the case's own text
(a trap for careful players, which is the opposite of what you want). The strongest distractors are the
suspects and facts your case spent real effort making the player suspect — your red herrings earn their
keep twice: once during play, once again as a wrong answer that tempts a player who didn't fully resolve
that thread. Never leave a stray choice that's simply incoherent with the story ("the victim himself" as
an option for who wrote a note *he* received, for instance) — every option should read as something a
plausible alternate case could have been true.

### Positioning rules that keep the exam honest

- **The correct answer must never sit in the first position** of its slot's choice list. Players
  default-click the first option under time pressure or boredom; a first-position correct answer isn't
  being tested.
- **Don't let a pattern emerge** across the five questions — if every correct answer sits at position 2,
  a player learns to ignore the case and learns your pattern instead. Vary it deliberately.
- **Both slots in a two-part question should test independent reasoning.** If slot 2's correct answer is
  trivially implied by slot 1's, you've written one question twice, not two connected ones.

### Test reasoning, not just recall, where you can

The strongest exam questions ask *why* a fact is true, not only *what* the fact is — "the true hour was
hidden by ______, exposed by ______" tests the mechanism of a deception, not merely its existence. Where
your case has a chained deduction, that's usually your best source for this kind of question, since the
chain itself *is* the reasoning you want tested.

---

## Tokens and the deduction economy

`startingInsightTokens` should be scarce enough that a player can't blindly combine every pair of clues
in the case and stumble into the solution — but not so scarce that a careful, attentive player runs out
partway through. Since most combines and some contradictions carry their own small `tokenReward`, the
practical calibration is: a player who investigates properly and combines things that actually make
sense earns back most of what they spend, while a player guessing blindly runs dry. For a harder case
(more rooms, more chained combines), 2 starting tokens is usually enough precisely *because* the economy
self-funds through correct play; an easier, shorter case can afford a slightly larger starting grant
since it has fewer opportunities to earn tokens back.

---

## The rules that keep a case fair

A good case feels like a Golden-Age whodunit; a careless one feels like a broken quiz. These are the
non-negotiables:

1. **One culprit, one closed circle.** A fixed cast; exactly one is guilty. No outsiders wander in.
2. **Motive, Means, Opportunity for everyone.** Every suspect has a real motive; the **culprit has all
   three**; every innocent is **cleared by a discoverable failure** of one. No filler suspects.
3. **A solvable chain.** The culprit, the key evidence, and the method must all be reachable *from
   authored clues alone* — no required guessing, no clue that only exists in your head.
4. **One fair, foreshadowed twist.** Exactly one central reversal, with at least one earlier clue
   hinting at it.
5. **Every lie is contradictable.** If a suspect lies, some discoverable evidence must break it and
   move the case forward.
6. **Red herrings must resolve.** Misleading clues are great, but each must be explainable by something
   findable — never a permanent dead end.
7. **The exam mirrors the solution.** Every exam answer must be deducible from the clues. Never ask
   what the player couldn't have worked out.
8. **Geography never accuses by accident.** No clue that incriminates a suspect sits in that suspect's
   own room, except deliberately in a red herring's space.
9. **Technical integrity.** The map connects, every id resolves, every language is complete.

---

## Validation checklist

A case with errors is **refused at load** — it never fails mid-play. Before sharing, make sure:

- [ ] The **map connects** — every room is reachable, and exits are two-way where intended.
- [ ] **Every id resolves** — every clue, evidence, suspect, and deduction referenced actually exists.
- [ ] **Every combine's chain is fully derivable** from raw clues alone — walk it by hand, or script a
      walk, confirming no combine depends on something unreachable.
- [ ] **Every red herring has a discoverable resolution** — a contradiction or clue that clears them,
      not just narrative text asserting their innocence.
- [ ] **No accusing clue sits in its own suspect's room**, except deliberately for a red herring.
- [ ] **Every declared language is complete** — no missing display text in any locale you ship, and any
      wordplay tell reads identically everywhere it's quoted.
- [ ] The **final exam** answers are all deducible, no correct answer is in the first position of its
      slot, and the five questions don't fall into a repeating position pattern.
- [ ] **Image paths** are `images/<file>.png` (relative), and the files exist.
- [ ] The Case Maker's **validation terminal shows no errors** (warnings are usually fine to review).
- [ ] You **playtested it** end to end, in the packaged build, and solved it *from the clues alone*.
- [ ] You opened **every suspect**, including honest witnesses, and confirmed their dialogue actually
      displays — a suspect with a valid `statement` but no visible text in-game is an engine rendering
      gap, not a reason to rewrite them.

The Case Maker runs these checks for you; hand-editors should load the case and read the validator
output.

---

## Adding & sharing your case

1. Put your case folder in `cases/<your-slug>/` (with its `images/`).
2. Launch the game — your case appears in case selection once it validates.
3. To share it, zip the case folder and hand it to another player to drop into their own `cases/`, **or**
   contribute it back to this project via a pull request (see [CONTRIBUTING.md](../CONTRIBUTING.md)) so
   it can join a shared community case library.

Because the project is AGPL-licensed, cases you contribute stay open and credited — and cases you
distribute on your own are yours to share under the same open terms.

Happy plotting. Make it fair, make it surprising, and let the player feel clever.

# Engraving Plates Spec — Sherlock's Legacy

> The locked art direction for all in-scene art: **full Victorian engraving plates.**
> Every room, suspect, and object is a hand-engraved book-plate — confident ink contour
> plus cross-hatch tone, the way an 1890s illustrated casebook would print a scene.
>
> This document is the **path map + art brief** for replacing each slot's current art with a
> real engraved plate. The shipped cases already declare an `imagePath` per slot (stand-in
> assets today); whenever a path resolves, that authored image is used, and the engraving
> placeholder from `ui.util.PlaceholderImageGenerator` shows only when a slot is blank or its
> path fails to load. Producing the real plate is done **by repointing the slot's `imagePath`
> to the plate below and dropping a PNG there — never by touching code.** See
> `.scratch/gui-g3-engraving-art/PRD.md` (art direction) and
> `.scratch/gui-g3fix-image-fallback/PRD.md` (why the placeholder is fallback-only).

---

## 1. House style — what every plate must look like

Read `DESIGN.md` §1, §2, §6 first. Then:

- **Technique:** line engraving / etching. A confident **ink contour** (the *ligne claire*
  spine of DESIGN.md §5) carries the drawing; **tone is built from cross-hatching**, never a
  flat fill and **never a gradient** (DESIGN.md §1 hard-ban). Think Doré, Tenniel, *Strand
  Magazine* — not a photo, not a painting, not a flat vector icon.
- **Palette:** monochrome ink on warm ground. Ink = the §2 *Ink* (`#241E17`); ground = *faded
  vellum* (`#E3D4B0`) for room plates. A second tone may use *Sepia* (`#6B5A43`) for lighter
  hatch. **No petrol/ochre/oxblood inside the art** — those are reserved for UI chrome.
- **No photoreal, no colour photography, no 3D render, no emoji, no flat material icons.**
- **One world:** a room, a suspect, and an object on screen together must read as plates from
  the *same* engraved book — same line weight, same hatch density, same ink.

### Dark mode
The renderer does **not** invert real art (only the placeholder inverts itself from the live
`Palette`). So author each plate to sit on a warm ground and remain legible when the UI dims
to candlelight. Practically: keep good internal contrast, avoid relying on a bright-white
field. If a plate ever needs a dedicated night version, ship it at the same path with a
`_dark` suffix — **but** that is a future renderer feature, not assumed today. For now, one
plate per slot, drawn to read in both themes.

---

## 2. Framing — the book-plate

Each plate is framed like an illustration tipped into a leather-bound annual.

| Slot kind | Canvas | Ground | Framing |
|-----------|--------|--------|---------|
| **Room**   | landscape, ≥ **1600×1200** (4:3), opaque PNG | warm vellum field | a **thick ink frame** with a faint parchment **mat** inside it (DESIGN.md §5 "Room view frame"); the scene sits inside the mat. The UI renders the room *contained* (letterboxed), so honour the 4:3 safe area. |
| **Suspect**| **square, ≥ 1024×1024**, **transparent** PNG | none (composited over the room) | a **knee-up or full-length figure**, centred, filling most of the height. No frame baked in — the figure *is* the sprite. |
| **Object** | **square, ≥ 512×512**, **transparent** PNG | none | a **single small motif**, centred, occupying the middle band — deliberately reads smaller than a suspect. No frame baked in. |

### Size rule (do not break it)
Suspects must read **clearly larger** than objects. The layout already enforces this
(`RoomViewLayout.SUSPECT_BASE_FACTOR = 0.30` vs `OBJECT_BASE_FACTOR = 0.15` of the rendered
room height), and the placeholder echoes it (a tall figure vs a small motif). When you draw
real plates, **fill the suspect square** with the figure and keep the **object motif small and
centred** so the two base factors land correctly. Per-slot nudges use the optional
`imageScale` field on the slot (default 1.0; multiplies the base size).

---

## 3. Path convention

Set the slot's `imagePath` to a file under `images/plates/`, named `<kind>_<slug>.png`:

```
images/plates/room_<room-slug>.png       e.g. images/plates/room_ballroom.png
images/plates/suspect_<suspect-slug>.png  e.g. images/plates/suspect_lord_ashworth.png
images/plates/object_<object-slug>.png    e.g. images/plates/object_shattered_glass.png
```

Paths resolve via `ResourceResolver` (classpath → case directory → filesystem — the same order
`CaseValidator` and `ImageManager` use). Drop the PNG either on the classpath
(`src/main/resources/images/plates/…`) for bundled cases, or in the case's own `images/plates/`
folder for external cases. A blank or unresolved path → the engraving placeholder (the case
still plays; `CaseValidator` warns, never errors).

**Today every slot below points at a stand-in asset that renders normally.** Repoint each
`imagePath` to the plate path here as the real engraved art is produced; a blank/unresolved
path falls back to the placeholder.

---

## 4. Plate catalogue

Depictions are art briefs, not dialogue — keep them period and atmospheric. Slugs follow §3.

### 4.1 The Stolen Sapphire (`cases/sapphire_case.json`, `src/main/resources/cases/sapphire_case.json`)

| Slot | Path | Plate depicts |
|------|------|---------------|
| Room · Ballroom | `room_ballroom.png` | A grand gas-lit ballroom: chandelier, parquet floor in receding hatch, tall windows; a sense of a party just ended. |
| Room · Terrace | `room_terrace.png` | A stone balustraded terrace at night, garden steps descending into cross-hatched dark, the ballroom glowing behind. |
| Object · shattered_glass | `object_shattered_glass.png` | A broken champagne flute, shards catching the light, fine radiating hatch where it struck the floor. |
| Object · cigar_stub | `object_cigar_stub.png` | A single extinguished cigar stub, a curl of engraved smoke, ash flecks. |
| Suspect · LordAshworth | `suspect_lord_ashworth.png` | An older aristocrat in evening tails, watch-chain, composed but guarded; knee-up. |
| Suspect · MademoiselleDupont | `suspect_mademoiselle_dupont.png` | An elegant woman in a gown, gloved, a knowing half-turn; knee-up. |

### 4.2 The Snowbound Secret of Blackwood Manor (`cases/Blackwood.json`)

| Slot | Path | Plate depicts |
|------|------|---------------|
| Room · Foyer | `room_foyer.png` | A snow-dusted manor entrance hall, grand staircase, frosted fanlight over the door. |
| Room · GrandLounge | `room_grand_lounge.png` | A fire-lit lounge, card table, decanters; cosy but tense. |
| Room · Study | `room_study.png` | A cluttered study — the death room: desk, fallen chair, bookshelves in dense hatch. |
| Room · DiningRoom | `room_dining_room.png` | A long set dining table under a chandelier, places half-cleared. |
| Room · Kitchen | `room_kitchen.png` | A servants' kitchen, copper pans, a cold hearth, scrubbed table. |
| Room · Greenhouse | `room_greenhouse.png` | A frosted glasshouse, foxglove and ferns, panes white with snow. |
| Room · Library | `room_library.png` | Floor-to-ceiling shelves, a rolling ladder, a reading lamp pool. |
| Room · MasterSuite | `room_master_suite.png` | A grand bedchamber, four-poster, a writing bureau, a cold grate. |
| Object · gambling_debts | `object_gambling_debts.png` | A fan of IOU slips and playing cards, figures inked in the margin. |
| Object · victim_body | `object_victim_body.png` | A draped figure on the study floor (decorous, period restraint), one hand visible. |
| Object · carpet | `object_carpet.png` | A rolled-back Persian rug, a dark stain at its edge. |
| Object · spilled_wine | `object_spilled_wine.png` | A toppled glass, a spreading pool of wine in radiating hatch. |
| Object · dead_mouse | `object_dead_mouse.png` | A small dead mouse, stiff, near a skirting board. |
| Object · pillbox | `object_pillbox.png` | An open silver pillbox, a few pills, an engraved monogram on the lid. |
| Object · letter | `object_letter.png` | A folded letter with a broken wax seal, copperplate script suggested. |
| Object · note | `object_note.png` | A hastily torn note, pencil scrawl, one corner missing. |
| Object · maid_diary | `object_maid_diary.png` | A small clasp diary, ribbon marker, a worn cover. |
| Object · crushed_foxgloveflower | `object_crushed_foxglove.png` | A crushed foxglove bloom, bell-flowers flattened, a single leaf. |
| Object · medical_textbook | `object_medical_textbook.png` | A heavy open medical tome, an anatomical plate visible, a marked page. |
| Object · torn_ledger | `object_torn_ledger.png` | A ledger with a page ripped out, columns of figures, a frayed stub. |
| Object · burnt_will | `object_burnt_will.png` | A charred legal document, seal half-melted, edges curled to ash. |
| Suspect · LadyEleanor | `suspect_lady_eleanor.png` | The lady of the house in mourning-dark silk, dignified, wary; knee-up. |
| Suspect · JulianVance | `suspect_julian_vance.png` | A sharp young gentleman, well-dressed, restless hands; knee-up. |
| Suspect · ColonelHastings | `suspect_colonel_hastings.png` | A stout military man, moustache, regimental bearing; knee-up. |
| Suspect · Evelyn | `suspect_evelyn.png` | A poised younger woman, fashionable, watchful; knee-up. |
| Suspect · Thomas | `suspect_thomas.png` | A footman/servant in livery, deferential posture; knee-up. |
| Suspect · Silas | `suspect_silas.png` | A lean, shadowed man, working clothes, a furtive look; knee-up. |
| Suspect · DrArisThorne | `suspect_dr_aris_thorne.png` | A physician with bag and spectacles, clinical calm; knee-up. |

### 4.3 The Last Note at the Blue Room (`cases/the-last-note-at-the-blue-room.json`)

| Slot | Path | Plate depicts |
|------|------|---------------|
| Room · Alley | `room_alley.png` | A wet back-alley behind a jazz club, brick, a single lamp, puddles in hatch. |
| Room · MainFloor | `room_main_floor.png` | The club main floor: round tables, a small stage beyond, smoke haze. |
| Room · Stage | `room_stage.png` | A nightclub stage, a microphone stand, a piano, footlights from below. |
| Room · Bar | `room_bar.png` | A long bar, bottles ranked on glass shelves, a brass rail. |
| Room · Cellar | `room_cellar.png` | A dim cellar, wine racks and barrels, a low vaulted ceiling. |
| Room · Office | `room_office.png` | A cramped back office, a safe, a cluttered desk, a filing cabinet. |
| Object · velvet_glove | `object_velvet_glove.png` | A single dropped velvet evening glove, soft folds in fine hatch. |
| Object · cigarette_stub | `object_cigarette_stub.png` | A lipsticked cigarette stub, ash, a thread of smoke. |
| Object · muddy_footprint | `object_muddy_footprint.png` | A muddy shoe-print on a floorboard, tread suggested in hatch. |
| Object · unmelted_ice | `object_unmelted_ice.png` | A near-whole ice cube in a tumbler, condensation beads. |
| Object · broken_mute | `object_broken_mute.png` | A trumpet mute, cracked, lying on the stage boards. |
| Object · uncorked_bottle | `object_uncorked_bottle.png` | An open wine bottle, cork beside it, a glint on the glass. |
| Object · locked_door | `object_locked_door.png` | A close study of a locked door, keyhole and bolt, scratch marks. |
| Object · dropped_key | `object_dropped_key.png` | A single ornate key on the floor, a long worn bow. |
| Object · empty_safe | `object_empty_safe.png` | An open wall safe, door ajar, interior empty and shadowed. |
| Object · empty_file_folder | `object_empty_file_folder.png` | A manila folder gaping open, its contents gone, a typed tab. |
| Object · lolas_contract | `object_lolas_contract.png` | A performer's contract, a signature line, an official stamp. |
| Suspect · LolaFox | `suspect_lola_fox.png` | A torch-singer in a sequined gown at a microphone, poised; knee-up. |
| Suspect · DetectiveThorne | `suspect_detective_thorne.png` | A weary plainclothes detective, overcoat and hat, notebook; knee-up. |
| Suspect · Bruno | `suspect_bruno.png` | A broad doorman/heavy, arms folded, watchful; knee-up. |
| Suspect · Sal | `suspect_sal.png` | A slick club owner in a sharp suit, cigar, easy menace; knee-up. |
| Suspect · MickeyTheRat | `suspect_mickey_the_rat.png` | A wiry, nervous informer, cap pulled low, darting eyes; knee-up. |

### 4.4 A Study in Practice — tutorial case (`src/main/resources/tutorial_practice_case.json`)

| Slot | Path | Plate depicts |
|------|------|---------------|
| Room · Study | `room_practice_study.png` | A simple detective's study for teaching: desk, lamp, one chair, a window. |
| Room · Parlour | `room_practice_parlour.png` | A modest parlour, fireplace, two armchairs, a side table. |
| Object · torn_letter | `object_torn_letter.png` | A torn letter, two pieces nearly meeting, ink script. |
| Object · muddy_boot | `object_muddy_boot.png` | A single muddy boot, laces undone, dried mud flaking. |
| Suspect · TheValet | `suspect_the_valet.png` | A correct, attentive valet in service dress, a tray under one arm; knee-up. |

> **Tutorial overlay images** (`src/main/resources/tutorials.json` `imageMap`) are a separate
> teaching surface and are intentionally **out of scope** here — they still point at the legacy
> stand-ins and have their own resolvable-path test (`TutorialImageMapTest`). Bring them onto
> engraving plates in a follow-up if desired.

---

## 5. How to install a finished plate

1. Draw the plate per §1–§2; export PNG at the size/transparency for its kind.
2. Save it at the path from §4 (classpath `src/main/resources/images/plates/…` for bundled
   cases, or the case's own `images/plates/…`).
3. Set the slot's `imagePath` in the case JSON to that path (replace the current `""`).
4. (Optional) tune `imageScale` on the slot if it reads a touch large/small.
5. Run `CaseValidator` (the case validator CLI / `CaseValidatorTest`) — a typo path warns.
   Launch the case and eyeball it in both themes.

No code changes at any step. The engraving placeholder simply stops appearing for that slot.

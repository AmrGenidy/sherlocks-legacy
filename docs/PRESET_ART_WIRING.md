# Preset fallback art — wiring spec

Goal: when a case ships no image for a suspect, object, or room, the game shows a
hand-made Victorian-engraving preset instead of the procedural `PlaceholderImageGenerator`
output. Presets live in `src/main/resources/images/presets/{characters,objects,rooms}/`
(see that folder's README for the inventory). This makes any case — including community
cases authored without art — look intentional and on-theme.

## Behaviour

1. **Resolution order** in `ImageManager` / `ImageResourceLoader`: (a) the case-provided
   `imagePath` (classpath → case dir → filesystem, as today); (b) if that is missing or
   unresolvable, a **deterministic preset**; (c) `PlaceholderImageGenerator` only as a final
   safety net (or retire it once presets cover every entity type).
2. **Deterministic = stable.** The same suspect/object/room must always map to the same
   preset across launches and across both players in multiplayer. Use a stable hash of the
   entity's id (fall back to its name) — e.g. `Math.floorMod(id.hashCode(), N)`. Never random.

## Selection rules

- **Suspect** (no image): `char_suspect_{01..12}` via `floorMod(hash(suspectId), 12) + 1`.
- **Watson** (no `watsonImagePath`): always `char_watson`.
- **Multiplayer partner / guest avatar**: `char_partner` (petrol-framed). Used in the lobby
  and in-room for the joining player when they have no avatar of their own.
- **Object** (no image): keyword match on lowercased id+name, first hit wins:
  letter/note/envelope→letter, key→key, bottle/poison→bottle, candle/candlestick→candlestick,
  book/journal/diary/ledger→book, watch/clock→pocket_watch, glove→glove,
  knife/dagger/blade→dagger, magnif/lens→magnifying_glass, pistol/gun/revolver→pistol,
  ring→ring, photo/photograph/picture→photograph, vial/flask→vial, rope/cord→rope,
  pen/quill/ink→quill_inkwell, pipe→pipe. No keyword → `floorMod(hash(objectId), 16)` over
  the object list.
- **Room** (no image): keyword match (most-specific first, so "Dining Hall"→dining and "Great
  Hall"→hallway): bedroom/bed/chamber/dormitory→bedroom, dining/dinner/banquet→dining,
  kitchen/scullery/pantry/galley→kitchen, library/study/office→study,
  parlour/parlor/drawing/lounge/sitting→parlour, hall/corridor/landing→hallway. No keyword →
  `floorMod(hash(roomName), 6)` over the 6-room list (study, parlour, hallway, bedroom, dining,
  kitchen) — 6 options + the keyword maps make adjacent rooms unlikely to repeat.


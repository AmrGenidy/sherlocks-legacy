# Preset fallback art

Full-Victorian-engraving placeholder art, used when a case provides no image for a
suspect, object, or room. Generated from the scripts in `art/presets/` (SVG masters in
`art/presets/svg/`). Style: sepia line + medium engraving hatch on aged paper, per `DESIGN.md`.

## Contents

- `characters/` (14): `char_watson`, `char_partner` (the multiplayer partner/guest avatar,
  marked with a petrol-blue frame), and `char_suspect_01`-`char_suspect_12` (varied
  archetypes: gentleman, lady, servant, scholar, etc.). 480×600.
- `objects/` (16): `obj_letter`, `obj_key`, `obj_bottle`, `obj_candlestick`, `obj_book`,
  `obj_pocket_watch`, `obj_glove`, `obj_dagger`, `obj_magnifying_glass`, `obj_pistol`,
  `obj_ring`, `obj_photograph`, `obj_vial`, `obj_rope`, `obj_quill_inkwell`, `obj_pipe`.
  400×400, transparent background.
- `rooms/` (6): `room_study`, `room_parlour`, `room_hallway`, `room_bedroom`, `room_dining`,
  `room_kitchen`. 1280×720.

## Selection rules (see docs/PRESET_ART_WIRING.md)

- **Suspect, no image** → deterministic: `hash(suspectId) % 12` → `char_suspect_NN`
  (stable per suspect, so the same suspect always gets the same face).
- **Watson, no image** → `char_watson`.
- **Multiplayer partner/guest avatar** → `char_partner`.
- **Object, no image** → keyword match on name/id to an `obj_*`; if none, deterministic
  `hash(id) % 16`.
- **Room, no image** → keyword match (most-specific first: bedroom/chamber→bedroom,
  dining/banquet→dining, kitchen/scullery→kitchen, library/study→study,
  parlour/drawing-room→parlour, hall/corridor→hallway); else `hash(roomName) % 6`.

# DESIGN.md — Sherlock's Legacy

> A design system for a vintage detective game.
> Read this file before generating, styling, or editing any UI — FXML, CSS, or JavaFX scene code.

---

## 1. Aesthetic North Star

The visual identity blends two worlds into one coherent look:

- **The Adventures of Tintin (Spielberg, 2011)** — *ligne claire* clarity, warm
  cinematic lighting, 1930s pulp-adventure energy, confident flat color held
  inside strong, clean contour lines. Motion-captured but stylised; never photoreal.
- **Sherlock Holmes / Victorian London** — gaslit fog, aged parchment, brass and
  mahogany, the hush of a detective's study, magnifying glasses and case files.

The result is a **warm, nostalgic, hand-crafted detective world**. It should feel
like a leather-bound adventure annual from the 1940s — not a modern app.

**Three words: Warm. Crafted. Investigative.**

**Hard bans** — never produce these:
- Drop shadows, glows, neon, or glassmorphism
- Gradients used as decoration (a single soft ambient light gradient is the only exception — see §6)
- Pure white (`#FFFFFF`) or pure black (`#000000`) surfaces
- Cold blue-grey "SaaS dashboard" palettes
- Flat geometric/material icons; emoji as UI controls
- Generic system fonts (Inter, Roboto, Arial, Segoe UI)

---

## 2. Color

The palette is warm, slightly desaturated, and period-accurate. Tintin's signature
**petrol blue** is the hero accent; everything else is parchment, ink, ochre and oxblood.

### Light mode — "The Study by Daylight"

| Role                     | Hex       | Use |
|--------------------------|-----------|-----|
| Parchment (app surface)  | `#EFE3C8` | Main background — aged paper |
| Vellum (raised panels)   | `#F6EEDB` | Cards, journal, dialogs |
| Faded vellum (sunken)    | `#E3D4B0` | Insets, input wells, room frame |
| Ink (primary text)       | `#241E17` | Body text, contour lines |
| Sepia (secondary text)   | `#6B5A43` | Captions, hints, metadata |
| **Petrol blue (primary)**| `#1C5D6E` | Primary buttons, links, active state, headings accent |
| Petrol blue — bright     | `#2E8198` | Hover / focus on primary |
| Ochre / brass (secondary)| `#C8893A` | Highlights, insight tokens, callouts, badges |
| Oxblood (alert / red)    | `#8E3B2E` | Contradictions, errors, danger |
| Moss green (success)     | `#5A6B3B` | Deductions confirmed, task complete |

### Dark mode — "The Study by Candlelight"

| Role                     | Hex       | Use |
|--------------------------|-----------|-----|
| Night ground (surface)   | `#1A1611` | Main background — deep brown-black |
| Mahogany (raised panels) | `#2A231A` | Cards, journal, dialogs |
| Lamp-lit ochre (text hi) | `#E8D4A8` | Primary text — warm, not white |
| Dim sepia (text low)     | `#A38F6E` | Secondary text |
| Petrol blue (primary)    | `#3E96AC` | Lifted for contrast on dark |
| Brass (secondary)        | `#D9A45C` | Highlights, tokens |
| Ember red (alert)        | `#C25B49` | Contradictions, errors |

Dark mode is a **candlelit room**, not an inverted UI: warm browns, never grey,
text is always a soft ochre — never pure white.

---

## 3. Typography

Three typefaces, each with a clear job. All are open-source and embeddable in JavaFX
via `Font.loadFont(...)`.

| Role            | Typeface                | Why |
|-----------------|-------------------------|-----|
| Display / headings | **Playfair Display** | High-contrast old-style serif; period elegance, strong on titles |
| Body / UI text  | **Spectral** (or EB Garamond) | A warm, readable serif designed for screens; never feels clinical |
| Journal / evidence / code | **Special Elite** (typewriter) or **Courier Prime** | Casework, clue text, terminal output — reads like a typed report |

Rules:
- Headings: Playfair Display, weight 500–700, generous letter-spacing on small caps.
- Body: Spectral, weight 400, ~16px, line-height 1.6.
- Never use a sans-serif. The world is made of ink and paper.
- The journal, the Final Exam questions, and the terminal output use the
  typewriter face — it reinforces "this is a detective's written record."
- Use **sentence case** for UI labels; **Title Case With Small Caps** only for
  major screen titles (e.g. *The Sapphire Falcon*).

---

## 4. Layout & spacing

- **Base unit: 8px.** All padding, margins, and gaps are multiples of 8.
- Generous, editorial whitespace — the UI should breathe like a book page.
- Asymmetric layouts are encouraged: a wide room view beside a narrower journal
  column reads more like a graphic-novel spread than a symmetric dashboard.
- **Responsiveness is mandatory.** The JavaFX scene must reflow gracefully from
  ~1024×720 up to full-screen:
  - Use `VBox`/`HBox` with `setHgrow`/`setVgrow` and percentage-based
    `GridPane` column constraints — never hard-pixel widths for panels.
  - The `RoomView` canvas scales proportionally; object sprites stay anchored to
    their normalized (0–1) positions.
  - Sub-windows (journal, chat, tasks) have sensible min sizes and remain usable
    when docked or floating.
  - Test every screen at minimum and maximum window size.

---

## 5. Components

The unifying rule is **ligne claire**: flat color fills, bounded by a clean,
confident **1.5–2px contour line** in Ink. No inner shadows, no bevels.

- **Buttons** — flat fill, 2px Ink border, 6px corner radius. Primary = petrol
  blue fill with vellum text. Secondary = vellum fill with Ink text and border.
  Hover lifts the fill one shade; pressed darkens it. No shadow — the border *is*
  the affordance.
- **Panels / cards** (journal, pinboard notes, case file) — vellum fill, 2px Ink
  border, like a card pinned to a corkboard. A subtle 1px inner highlight in a
  lighter vellum is allowed to suggest paper thickness.
- **Room view frame** — treat the room render like an illustrated plate in a
  book: a thick Ink frame, a faint parchment mat around the artwork.
- **Dialogs** — centered, vellum, with a small ochre rule line under the title.
- **Inputs** — sunken faded-vellum well, 1.5px Ink border, typewriter font for
  entered text.
- **Insight tokens / ranks / badges** — ochre/brass, small, like a wax seal or a
  brass stud.

---

## 6. Texture, imagery & motion

- A **very subtle** aged-paper grain may sit on the main surface (low opacity,
  ≤6%). It must never reduce text contrast.
- The single permitted gradient: a soft, warm radial "lamplight" pooled behind
  the central content — barely perceptible, suggesting a desk lamp.
- **Room, character, and object art** should follow ligne claire — flat color,
  clear black outlines, warm cinematic light. When generating placeholder art
  via `PlaceholderImageGenerator`, match this: flat shapes, Ink outlines, the
  palette above.
- **Icons** are thin-line, hand-drawn in feel — magnifying glass, fingerprint,
  pocket watch, ink pen, folded letter.
- **Motion** is gentle and paper-like: ease-in-out, ~180–240ms, no bounce, no
  overshoot. Transitions evoke a turning page or ink settling, never a "pop."

---

## 7. Voice & UX writing

- Tone: a light period flourish over fundamentally clear, modern instructions.
  "Examine the writing desk" — not "Click here to inspect object."
- Watson's hints sound like a patient, slightly formal companion.
- Error and contradiction messages stay in-world but never confuse: clear first,
  characterful second.
- Tutorials should feel like Watson teaching a new detective — short, encouraging,
  one idea per step.

---

## 8. JavaFX implementation notes

- **`detective-theme.css` is the source of truth.** Define the full palette as
  looked-up colors in `.root` (e.g. `-sl-parchment`, `-sl-ink`, `-sl-petrol`,
  `-sl-ochre`). Every control references these — no raw hex outside `.root`.
- `theme_dark.css` only *overrides* the looked-up color values; it must not
  redefine component structure.
- `pinboard.css` *extends* the base theme; it must not fork the palette.
- Load the three fonts once at startup via `Font.loadFont` and reference them by
  family name in CSS.
- Keep all corner radii, border widths, and the 8px spacing scale as CSS
  constants so they stay consistent across screens.
- Prefer styling through CSS classes over inline styles, so the whole look can be
  retuned from these three files.

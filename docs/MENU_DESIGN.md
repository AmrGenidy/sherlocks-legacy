# Menu design - Sherlock's Legacy

> The menus are the game's first impression and its strongest expression of the
> full-Victorian-engraving identity. They must feel like opening a leather-bound 1890s
> casebook by lamplight: warm, crafted, captivating. Read this with `DESIGN.md` (the
> design system is law) before touching any menu FXML/CSS.
>
> **Visual north star:** `docs/art-refs/main_menu_reference.png`. Match its composition,
> framing, and mood. The reference uses Georgia as a stand-in; the real game uses the
> loaded fonts below.

## The menu visual system

Every menu screen shares this chrome so they read as pages of one book:

- **Surface:** aged parchment (`-sl-parchment`) with the ≤6% paper grain and the single
  permitted soft radial "lamplight" pooled behind the central content (DESIGN.md §6).
- **Ornamental frame:** a double ink border (≈4px outer, 1.5px inner) inset from the window
  edge, with an ochre filigree flourish + oxblood dot in each corner. The frame scales with
  the window; it is the "plate" the page sits in.
- **Title treatment:** the screen title in **Playfair Display** (600-700), large, letter-spaced,
  ink, centered, with a short **ochre rule** + small oxblood centre-dot beneath, and a
  letter-spaced small-caps subtitle in **Spectral**/sepia under that.
- **Buttons = engraved plates:** vellum fill, 2px ink border, 4px radius, a faint 0.8px inner
  highlight line (paper thickness), label in Spectral/Playfair. The **primary action on each
  screen** is petrol-blue fill with vellum text. Hover lifts the fill one shade; pressed
  darkens; keyboard focus shows a clear ochre ring. No shadows - the border is the affordance.
- **Corner & accent motifs:** thin-line engraved detective icons (magnifier, pocket watch,
  fingerprint, quill) as quiet flourishes, never as controls.
- **Type roles:** Playfair = titles/headings; Spectral = body, button labels, descriptions;
  Special Elite (typewriter) = anything that is "the detective's written record" (case
  briefings, journal-style text on menus). All three are already loaded at startup.
- **Voice:** light period flourish over clear modern instruction (DESIGN.md §7). "Begin a new
  investigation", not "New game". Sentence case everywhere except major titles.

## Screens to build (all share the chrome above)

1. **Main menu** - match `docs/art-refs/main_menu_reference.png`. Agreed design decisions:
   - **Asymmetric layout that reflows.** At wide aspect ratios: a tall arched **frontispiece
     plate** on the left, the menu on the right. Below a width breakpoint it collapses to a
     centered stack (frontispiece on top, buttons below). Built on grow/percentage constraints,
     never fixed positions.
   - **Frontispiece = a rich engraved scene**, not a flat icon: the detective at his lamplit
     desk (window + moon, bookshelf, banker's lamp with a soft glow pool, papers, magnifier),
     framed like an old novel's plate, with a small italic **caption ribbon** beneath it.
     A generated reference asset exists; treat it as the visual target.
   - **Rotating epigraph.** The caption ribbon shows a quote from `resources/menu/quotes.json`,
     and the quote **changes over time** (on the timer in that file) and **again each time the
     main menu becomes visible** (returning from a submenu shows a fresh one). Transition is a
     gentle ink-style cross-fade (use `fadeMs`). Pull only from `rotation.enabledGroups`
     (public-domain Holmes quotes + original adventure lines). Every enabled quote is fully
     translated (en/ar/ru); the ribbon shows the quote in the **current UI language** and
     **re-renders in the new language the instant the language is changed**, using the
     script-appropriate font (Amiri for Arabic, PT Serif for Russian) like the rest of the UI.
     Optional `source` may be shown as a small attribution.
   - **Buttons, weighted:** a **Continue** plate as the petrol primary shown ONLY when a save
     exists (top of the stack); then four plates - Single player, Multiplayer, Create a case,
     Tutorials. When no save exists, Single player becomes the primary.
   - **Language, Settings and Quit are demoted** to small engraved icon buttons in the
     bottom-right corner - a **globe** (language), a **gear** (settings), and a **power** glyph
     (quit) - always available, never competing with the primary path. The globe opens a small
     language chooser popover (the languages listed in their own script). There is NO separate
     text language dropdown elsewhere.
   - Title block centered at top (Playfair + ochre rule + small-caps subtitle); version number
     small in the bottom-left; quiet corner motifs and flourishes.
2. **Case selection** - the centrepiece. Present cases as a shelf/gallery of **casebook
   covers** (engraved spine/cover plates showing the case title, author, language flags,
   completion seal). Hovering a book lifts it slightly and shows a short invitation excerpt
   in typewriter type. Selecting opens the **case invitation** as a letter/dossier page.
   Includes the "Add a case" affordance and language pick per case.
3. **Multiplayer** - Host / Join. Host shows the generated **join code** on an engraved plate
   (large, copyable). Join has a code-entry field styled as a sunken vellum well + a LAN
   games list. Clear, friendly, no dead ends.
4. **Lobby** - two detective place-settings (host + partner, partner uses `char_partner`
   petrol-framed), the case invitation, ready/start controls. Waiting state feels warm, not
   empty ("Awaiting your partner…").
5. **Tutorials** - a list of lessons as index cards / a contents page, each with a ✓ wax-seal
   when completed (ties to tutorial completion persistence).
6. **Settings** - audio volume, language, theme (light "study by daylight" / dark "study by
   candlelight"), rendered as a tidy dossier form. Sliders are engraved; toggles are wax-seal
   style.
7. **Pause / in-game menu** - a smaller centered dossier card overlaying a dimmed (not black)
   game: Resume, Settings, Journal, Quit to menu.
8. **Add custom case** - file picker dressed as "filing a new case".

## Layout & responsiveness (mandatory - DESIGN.md §4)

- The whole menu is built on percentage/grow constraints, never hard pixel positions. It must
  reflow gracefully from 1024×720 to full-screen and look composed at every size.
- The ornamental frame insets proportionally; the lamplight stays centred; the emblem
  medallion and button column keep their relative balance (asymmetric, editorial).
- Button plates have sensible min/max widths; text never clips; the case-book gallery wraps to
  available width.
- Test every screen at minimum and maximum window size, and in English, Arabic (RTL text in
  LTR layout per the earlier decision), and Russian.
- **No scrollbars on menu screens.** Menus must fit the window and reflow - a scroll wheel /
  scrollbar appearing on the main menu (or any menu) is a defect, not an acceptable fallback.

## Related in-game surfaces (not menus, but same family)

Two in-game surfaces share the visual language but are handled in their own prompts:

- **Terminal** (4.2f): a sunken well in the typewriter face that FOLLOWS THE THEME for contrast -
  light mode = a light parchment/vellum well with dark ink text; dark mode = a dark well with
  lamp-lit ochre/cream text. Either way high-contrast and readable, with a blinking caret, ochre
  prompt glyph, and palette-coded line types (success/error/contradiction). Must auto-scroll to
  the newest line. It must read clearly and feel snappy.
- **Pinboard** (4.2g): the odd one out - a Victorian corkboard/evidence wall (pinned engraved
  cards, oxblood thread links), distinct from the page/dossier surfaces but built from the same
  palette, fonts, and ink linework (pinboard.css extends the base theme, never forks it).

## Motion (DESIGN.md §6 - gentle, paper-like)

- Screen-to-screen: a **page-turn** feel - a soft cross-fade plus a small directional slide,
  ease-in-out 180-240ms. No bounce, no overshoot, no "pop".
- Buttons: fill lifts on hover (~120ms), darkens on press; focus ring fades in.
- Ambient (subtle, optional): a barely-perceptible flicker on the lamplight glow, like a
  candle. Must never distract or affect text contrast.
- Title/emblem may settle in on first load like ink drying (one gentle fade+rise), once.
- All timing values live as constants so the whole feel can be retuned in one place.

## Polish details that sell it

- Use the preset engraving art and the corner motifs already in `resources/images/presets/`.
- The language selector shows engraved flags or language names in their own script.
- Completed cases get a wax-seal "Solved" stamp on their book cover.
- Hover sounds (a soft page rustle / pen tick) wired through the audio system if present -
  optional, behind the volume setting, never on by default at full volume.
- Keyboard: full arrow/Tab navigation with the visible ochre focus ring; Enter activates;
  Escape steps back on every screen.

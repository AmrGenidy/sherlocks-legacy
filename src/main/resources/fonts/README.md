# Fonts

Per [DESIGN.md §3](../../../../DESIGN.md), three typefaces define the visual register. They must be present in this directory and are loaded at startup by [`ui.util.FontLoader.loadAll()`](../../java/ui/util/FontLoader.java).

## Required files

| Role | Family | Files |
|------|--------|-------|
| Body / UI text | **Spectral** | `Spectral-Regular.ttf`, `Spectral-Bold.ttf` |
| Display / headings | **Playfair Display** | `PlayfairDisplay-Regular.ttf`, `PlayfairDisplay-Bold.ttf` |
| Typewriter (journal, Final Exam, terminal) | **Special Elite** | `SpecialElite-Regular.ttf` |

Five files total. Filename casing matches Google Fonts' static-distribution naming convention. `FontLoader` uses exact paths — a rename of any file will silently fall back to system defaults.

## Where to get them

All three are open-source under the **SIL Open Font License (OFL)** and can be redistributed with the project.

- Playfair Display — <https://fonts.google.com/specimen/Playfair+Display>
- Spectral — <https://fonts.google.com/specimen/Spectral>
- Special Elite — <https://fonts.google.com/specimen/Special+Elite>

For each, download the "Get font" zip from Google Fonts, then copy the matching static `.ttf` files (under `static/` inside the zip) into this directory.

## Verifying

Run the app. On startup, `FontLoader` logs:

```
Brand font load complete: 5 files loaded, 0 files missing, 0 errors.
Registered brand families: [Playfair Display, Spectral, Special Elite].
Missing brand families: [].
```

If `Missing brand families` is non-empty, the file naming is off or a download was incomplete. The app will still start — JavaFX will fall back to the CSS font-family chain — but DESIGN.md §3 typography rules will not render correctly.

## Where the families are referenced

- **Spectral** — base body font, declared in `.root` of `detective-theme.css`.
- **Playfair Display** — `.victory-title` and `.window-title` (and anywhere else headings are styled).
- **Special Elite** — `.terminal-area`, `.terminal-input`, `.terminal-prompt`, and the `.typewriter` utility class (applied to the journal entries list and Final Exam questions per DESIGN.md §3).

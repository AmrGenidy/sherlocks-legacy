<!-- Replace <your-username> throughout with your GitHub username once the repo exists. -->

<div align="center">

<img src="docs/images/logo.png" alt="Sherlock's Legacy" width="220"/>

# Sherlock's Legacy

**A hand-crafted, data-driven Victorian detective game — walk the rooms, examine the clues, contradict the lies, and name the culprit.**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](#quick-start)
[![JavaFX 22](https://img.shields.io/badge/JavaFX-22-green.svg)](#quick-start)
[![Made with Maven](https://img.shields.io/badge/build-Maven-C71A36.svg)](#quick-start)

*Golden-Age whodunits, in the spirit of Agatha Christie — playable solo offline or with friends over a LAN, and fully moddable through JSON cases and a built-in Case Maker.*

</div>

---

<div align="center">
<img src="docs/images/screenshot-room.png" alt="A room in Sherlock's Legacy" width="80%"/>
<br/>
<em>Placeholder — replace with a real screenshot (see <a href="docs/images/README.md">docs/images/README.md</a>).</em>
</div>

---

## What is this?

You play a Victorian detective. You move between rooms, **examine** objects for clues, **question** suspects, **contradict** their lies with the evidence you've found, **combine** clues into named deductions, and finally sit a **final exam** where you name the culprit and the truth. Each mystery is a self-contained *case*, and the whole game is **data-driven** — a case is just a JSON file plus some art, so anyone can write new mysteries without touching the code.

The look is deliberate: a warm, hand-drawn "leather-bound adventure annual" — Tintin's *ligne claire* meeting gaslit London, never a flat modern UI.

**Two ways to play:** single-player fully **offline** (no network, ever), or **multiplayer over a LAN**, both running on the exact same rulebook.

## Highlights

- 🕯️ **A real deduction loop** — examine, question, contradict, combine, deduce, then the final exam. Solving is a chain of fair, discoverable steps, not a quiz.
- 🧩 **Data-driven cases** — every mystery is a JSON file (rooms, suspects, clues, deductions, exam) with its own art. No recompiling to add a case.
- 🛠️ **Built-in Case Maker** — a visual authoring tool: lay out rooms, place sprites, write suspects and clues, wire the final exam, and validate — all in-app.
- 🌍 **8 languages built in** — English, Arabic, Russian, Chinese, Turkish, German, French, Spanish. Case content is localized inside each case file.
- 🖥️ **Terminal *and* GUI** — type commands in an in-world terminal, or click sprites and buttons. Journal, Pinboard, Case File, and Chat windows keep your investigation organized.
- 🔌 **One engine, two front-ends** — a single `GameEngine` rulebook drives both solo and networked play through the same command layer, so rules never drift.

## Quick start

**Requirements:** JDK 17+ (the project builds and runs on JDK 23), and [Maven](https://maven.apache.org/).

```bash
# Clone
git clone https://github.com/<your-username>/sherlocks-legacy.git
cd sherlocks-legacy

# Run in development
mvn javafx:run

# Or build a runnable fat jar
mvn clean package
java -jar target/DetectiveGametest-1.0-SNAPSHOT.jar
```

Run the tests with `mvn test`. For a deeper build/packaging guide (including a native Windows app-image via `jpackage`), see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#building-and-packaging).

## Documentation

| Doc | What's in it |
|---|---|
| 📐 [**Architecture**](docs/ARCHITECTURE.md) | The tech stack, the shared-engine design, single-player vs multiplayer, the data-driven JSON pipeline, the terminal, the GUI shell, and the Case Maker. |
| 🎮 [**Gameplay**](docs/GAMEPLAY.md) | Every command, the GUI windows (Journal, Pinboard, Case File, Chat), the visuals, and how the deduction loop actually plays. |
| ✍️ [**Case Creation**](docs/CASE_CREATION.md) | How to author your own mystery — the case format, the Case Maker, the rules that keep a case fair, and the validation checklist. |
| 🤝 [**Contributing**](CONTRIBUTING.md) | How to fork, propose features, submit a case, and what the license means for your changes. |

## Extending the game

This project is built to be built on. You can:

- **Fork the engine** and make your own variant of the game — new mechanics, a different theme, whatever you like.
- **Write new cases** as JSON (with the Case Maker or by hand) and share them, or contribute them back to a community case library.
- **Add features via pull requests** — engine or GUI improvements that *every* case benefits from.
- **Build optional add-ons** on top of the case format and command layer.

See [CONTRIBUTING.md](CONTRIBUTING.md) for how each of these works — and note that the license (below) keeps every derivative open and credited back to this project.

## License & your rights

Sherlock's Legacy is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)** — see [LICENSE](LICENSE).

In plain terms, you are free to **use, study, modify, and share** this game, including for commercial purposes, **as long as**:

- **You keep it open.** Anyone you give a modified version to — *including people who only use it over a network/server* — must be able to get your full source code under this same license. This is the key difference from ordinary GPL, and it matters here because of the multiplayer/server code: you can't run a modified version as a hidden closed service.
- **You credit the original.** Copyright and author notices must stay intact; you can't pass this work off as your own.
- **You don't add restrictions.** You can't take it closed-source or relicense it under weaker terms.

> **A note on selling.** No open-source license can outright *forbid* charging money — but AGPL removes the incentive: because anyone you sell to receives the full source under AGPL and can share it freely, nobody can build a closed paid product on this work. If you specifically need a license whose *text* forbids commercial sale, that would be a "source-available" (non-open-source) license instead; that's a deliberate trade-off — see [CONTRIBUTING.md](CONTRIBUTING.md#license-faq).

**The original, canonical version lives here** at this repository. Forks are free to exist and diverge, but the AGPL guarantees they stay open and keep pointing back to this original — you never have to review or approve anyone's fork.

Copyright © 2026 Amr Mohamed. "Sherlock's Legacy", its art, and its authored cases are part of this project.

## Credits

Created by **Amr Mohamed**. Inspired by the Golden-Age detective fiction of Agatha Christie.

Art, case files, translations, and code contributed by the community are credited in [CONTRIBUTORS.md](CONTRIBUTORS.md) (add yourself when you contribute!).

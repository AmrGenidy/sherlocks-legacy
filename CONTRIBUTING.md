# Contributing to Sherlock's Legacy

Thanks for wanting to build on *Sherlock's Legacy*! This project is meant to be extended - forked, added to, and filled with community cases. This guide explains the ways to contribute, the ground rules, and what the license means for your work.

---

## Ways to contribute

There are four, and you're welcome to do any of them:

1. **Fork the engine.** Make your own variant - new mechanics, a different setting, your own art. Forking creates *your* copy under *your* account; the original here is untouched. You never need anyone's permission to fork. (The license, below, keeps your fork open and credited back here.)
2. **Write cases.** Author a mystery as a JSON case (with the [Case Maker](docs/CASE_CREATION.md) or by hand) and share it - as a standalone download, or contributed back here to a community case library.
3. **Add features via pull requests.** Engine or GUI improvements that *every* case benefits from - new commands, quality-of-life, accessibility, bug fixes.
4. **Improve docs, translations, or art.** All eight languages, the guides, and the preset art are fair game.

---

## Forks, pull requests, and this repo

- **Fork freely.** A fork is your own copy under your own account. You don't need permission, and you can take it anywhere you like. The AGPL keeps forks open and credited back here, so the original always stays findable.
- **Pull requests are how work comes home.** If you build something you think belongs in the main game, open a PR. I read every one, and I'll either merge it, suggest changes, or explain why it doesn't fit.
- **This repository is the original.** Whatever happens out in the forks, this is where Sherlock's Legacy lives.

---

## Development setup

**Requirements:** JDK 17+ (builds/runs on JDK 23) and Maven.

```bash
git clone https://github.com/AmrGenidy/sherlocks-legacy.git
cd sherlocks-legacy
mvn javafx:run          # run in dev
mvn test                # run the test suite
mvn spotless:apply      # format (scope to files you changed)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the codebase map and [docs/GAMEPLAY.md](docs/GAMEPLAY.md) / [docs/CASE_CREATION.md](docs/CASE_CREATION.md) for how the game and cases work.

## Pull request guidelines

- **Keep PRs focused** - one feature or fix per PR is much easier to review.
- **Match the existing style** - run `mvn spotless:apply` on your changed files before committing.
- **Add or update tests** for engine/wire/case-logic changes (the suite is JUnit 4 + Mockito).
- **Don't break the security boundary.** Never widen the wire allowlist in `common/SerializationUtils.java`, and keep command/DTO constructors side-effect free. (See [ARCHITECTURE.md → Security](docs/ARCHITECTURE.md#security-posture).)
- **Respect the design system** for any visual change (see `DESIGN.md`).
- **Describe what and why** in the PR - and for gameplay changes, note how you tested them.

## Contributing a case

- Build it with the Case Maker or by hand; make sure it **passes validation** and you've **playtested it end to end** (see the [validation checklist](docs/CASE_CREATION.md#validation-checklist)).
- Put it in `cases/<your-slug>/` with its `images/`.
- Open a PR. By contributing a case you agree it's licensed under the project's AGPL-3.0 like everything else.

## Reporting bugs & requesting features

Use the GitHub **Issues** tab and pick the matching template (bug report, feature request, or new-case proposal). A good bug report includes what you did, what you expected, what happened, and your OS + Java version.

---

## License FAQ

**What license is this?** [GNU AGPL-3.0](LICENSE). By contributing, you agree your contribution is licensed under it too.

**What does that mean for me as a contributor / forker?**

- You may **use, modify, and share** the project, commercially or not.
- Anyone you give a modified version to - **including users who only reach it over a network/server** - must be able to get your **full source** under this same license. (This "network clause" is what AGPL adds over ordinary GPL, and it's here because the game has multiplayer/server code.)
- You must **keep copyright and author notices intact** - you can't present this work as your own.
- You **can't take it closed-source** or relicense it under weaker terms.

**Can people sell it?** No open-source license can forbid charging money, but AGPL makes it pointless: anyone who buys a copy receives the full source and can share it for free. Nobody can build a closed, paid product on this work. That's the protection that matters.

### Adding a license header to new source files

When you add a **new** source file, put this at the top (fill in the year and your name):

```
/*
 * Sherlock's Legacy - <one-line description of this file>
 * Copyright (C) <year> <your name or handle>
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version. This program is distributed WITHOUT ANY WARRANTY; see the GNU
 * AGPL v3 (LICENSE) for details.
 */
```

Existing files can be updated opportunistically - no need to touch every file at once.

---

Thanks again - every fair case and every clean PR makes the game better. See you in the drawing room.

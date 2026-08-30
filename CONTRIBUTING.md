# Contributing to Sherlock's Legacy

Thanks for wanting to build on *Sherlock's Legacy*! This project is meant to be extended — forked, added to, and filled with community cases. This guide explains the ways to contribute, the ground rules, and what the license means for your work.

---

## Ways to contribute

There are four, and you're welcome to do any of them:

1. **Fork the engine.** Make your own variant — new mechanics, a different setting, your own art. Forking creates *your* copy under *your* account; the original here is untouched. You never need anyone's permission to fork. (The license, below, keeps your fork open and credited back here.)
2. **Write cases.** Author a mystery as a JSON case (with the [Case Maker](docs/CASE_CREATION.md) or by hand) and share it — as a standalone download, or contributed back here to a community case library.
3. **Add features via pull requests.** Engine or GUI improvements that *every* case benefits from — new commands, quality-of-life, accessibility, bug fixes.
4. **Improve docs, translations, or art.** All eight languages, the guides, and the preset art are fair game.

---

## How forks, PRs, and "the original" work

A common question: *"If people can change the game, how does the original stay findable, and do I have to review everyone's changes?"*

- **No, you don't review every change.** When someone wants to modify the game, they **fork** it — their own separate copy. You never see it unless they choose to open a **pull request** asking you to merge their work into *this* repo. You review only PRs, and you're free to accept, request changes, or decline.
- **The original stays canonical.** This repository is *the* original. Forks are visibly labeled "forked from" and, under the AGPL, must stay open and keep the copyright/attribution intact — so the lineage back here is permanent and legally required. Anyone can always find the original at its repo URL.
- **Two independent tracks:** people fork and diverge freely (you're not involved), **or** they submit PRs and you're the gatekeeper of *your* repo only — never of the wider ecosystem.

---

## Development setup

**Requirements:** JDK 17+ (builds/runs on JDK 23) and Maven.

```bash
git clone https://github.com/<your-username>/sherlocks-legacy.git
cd sherlocks-legacy
mvn javafx:run          # run in dev
mvn test                # run the test suite
mvn spotless:apply      # format (scope to files you changed)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the codebase map and [docs/GAMEPLAY.md](docs/GAMEPLAY.md) / [docs/CASE_CREATION.md](docs/CASE_CREATION.md) for how the game and cases work.

## Pull request guidelines

- **Keep PRs focused** — one feature or fix per PR is much easier to review.
- **Match the existing style** — run `mvn spotless:apply` on your changed files before committing.
- **Add or update tests** for engine/wire/case-logic changes (the suite is JUnit 4 + Mockito).
- **Don't break the security boundary.** Never widen the wire allowlist in `common/SerializationUtils.java`, and keep command/DTO constructors side-effect free. (See [ARCHITECTURE.md → Security](docs/ARCHITECTURE.md#security-posture).)
- **Respect the design system** for any visual change (see `DESIGN.md`).
- **Describe what and why** in the PR — and for gameplay changes, note how you tested them.

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
- Anyone you give a modified version to — **including users who only reach it over a network/server** — must be able to get your **full source** under this same license. (This "network clause" is what AGPL adds over ordinary GPL, and it's here because the game has multiplayer/server code.)
- You must **keep copyright and author notices intact** — you can't present this work as your own.
- You **can't take it closed-source** or relicense it under weaker terms.

**Can people sell it?** No open-source license can forbid charging money. But AGPL makes selling pointless: anyone you sell to gets the full source under AGPL and can share it for free, so nobody can build a closed paid product on it. That's the practical protection.

**What if I want a license whose text literally bans selling?** That requires a *source-available* (non-open-source) license such as PolyForm Noncommercial. The trade-off: it isn't "open source," fewer people will contribute, and it's legally less battle-tested than AGPL. This project chose AGPL on purpose; if you fork for a non-commercial-only variant, that's your call to make on your fork (subject to keeping this project's AGPL code AGPL).

### Adding a license header to new source files

When you add a **new** source file, put this at the top (fill in the year and your name):

```
/*
 * Sherlock's Legacy — <one-line description of this file>
 * Copyright (C) <year> <your name or handle>
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version. This program is distributed WITHOUT ANY WARRANTY; see the GNU
 * AGPL v3 (LICENSE) for details.
 */
```

Existing files can be updated opportunistically — no need to touch every file at once.

---

Thanks again — every fair case and every clean PR makes the game better. See you in the drawing room.

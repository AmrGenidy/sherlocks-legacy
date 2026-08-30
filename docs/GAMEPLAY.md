# Gameplay

How *Sherlock's Legacy* actually plays — the deduction loop, every command, the GUI windows, and the visuals. If you want to understand the code behind these, see [ARCHITECTURE.md](ARCHITECTURE.md); if you want to *write* a case, see [CASE_CREATION.md](CASE_CREATION.md).

---

## The deduction loop

You are a Victorian detective solving one self-contained mystery at a time. The loop is:

1. **Explore** — walk between rooms.
2. **Examine** objects to collect **clues**; some clues are also **evidence**.
3. **Question** suspects — each is hiding something, and exactly one is the culprit.
4. **Contradict** a suspect's lie by presenting the evidence that breaks it.
5. **Combine** clues into named **deductions** — the currency of solving.
6. Spend limited **insight tokens** to **deduce** and to ask Watson pointed questions.
7. Sit the **final exam**: name the culprit, the key evidence, and the method/motive.

You're scored on how *efficiently* you solve — fewer deductions used for a correct solution earns a higher **rank tier**. A solved case is sealed in your casebook with your best-ever result.

---

## Commands

Everything below can be typed in the [terminal](#the-terminal-window). Most also have a GUI equivalent (clicking a sprite, a button, or a neighboring-room label). Commands resolve on **universal names** (the stable, command-safe id), and the terminal offers **live autocomplete** for whatever is valid right now.

| Command | What it does |
|---|---|
| `look` | Re-describe the current room: its exits, objects, and who's here. |
| `move <direction>` | Walk to a neighboring room (e.g. `move east`). |
| `examine <object>` | Inspect an object; adds its clue to your Journal. |
| `question <suspect>` | Question a suspect; their statements appear as dialogue. |
| `contradict <evidence> with <suspect>` | Confront a suspect's lie with the evidence that breaks it (alias: `present <evidence> to <suspect>`). |
| `combine <clue_id> <clue_id>` | Combine two clues (by their ids) into a named deduction. |
| `deduce …` | Lock in a deduction (spends insight). Combining and contradicting are the two ways deductions are formed. |
| `ask watson` | Ask Dr. Watson for a general nudge or a contradiction hint. |
| `ask watson <name/thing>` | Pay insight to have Watson analyse a specific person or thing. |
| `journal` | View your Journal (all collected clues and notes). |
| `journal add <note>` | Write your own note into the Journal. |
| `tasks` | View the current case's task/objective list. |
| `final exam` | Begin the final exam once you think you've solved it. |
| `help` | List available commands and how to use them. |
| `start case` | Start the selected case. |
| `exit` | Leave the current case back to case selection. |

> The exact set of suspects, objects, directions, and evidence is defined by the case you're playing — the commands are universal, the content is data.

---

## Dr. Watson — the hint system

Watson is your in-world hint channel, tuned so it never just hands you the answer:

- **`ask watson`** gives a *general* nudge, or — if you're close — a *contradiction* nudge pointing you toward a lie you can break.
- **`ask watson <name or thing>`** is a paid "analyse this" service (costs insight) that comments on a specific suspect or clue, including resolving red-herring confusion.

Watson keeps you moving without solving the case for you.

---

## The GUI windows

Beyond the room view and terminal, your investigation is organized across several windows you can open from the in-game toolbar:

### The room view
The main stage: a warm, hand-drawn "plate" of the current room with character and object **sprites** you can click. Clicking a suspect questions them; clicking an object examines it; neighboring-room labels let you move. Name tags label each sprite. Dr. Watson appears in the room and can be asked for help.

### The Terminal window
The typed heart of the game — enter any command and read a colored transcript of dialogue, clues, and system messages. Autocomplete suggests valid commands and arguments as you type. Everything you can click, you can type here.

### The Journal
Your running record of the investigation: every clue you've examined, plus **your own notes** (`journal add …` or the Add-note button). This is where you keep track of what you know.

### The Pinboard
A cork-board for **deductions** — the named conclusions you've built by combining clues and breaking lies. It visualizes how the pieces connect as your theory of the case takes shape.

### The Case File
The dossier for the current case: the victim, an overview of the mystery, and a profile of each suspect. Your reference for *who's who* while you investigate.

### Chat (multiplayer)
In LAN multiplayer, a chat window lets detectives on the same case talk to each other. (Single-player has no chat — there's no one to talk to.)

### Settings
A trimmed in-game Settings dossier (audio, two text-size sliders, light/dark theme) reachable any time — including during the final exam — without leaving your place.

---

## The final exam

When you're ready, `final exam` presents the ending: a short set of fill-in-the-blank questions where you name the culprit, the key evidence, and the method or motive — chosen from dropdowns (or answered by number in the terminal). Every answer is deducible from clues you could have found; the exam mirrors the solution and never asks something unfair.

Submit, and you get a verdict, a per-question review (right/wrong, never revealing the correct answer for ones you missed), your rank, and — if you named the truth — the case's winning message and a sealed casebook entry.

To keep players honest, rapid repeated submissions trigger a short anti-abuse cooldown on the exam.

---

## Visuals & feel

The art direction is deliberate and consistent (defined in `DESIGN.md`): a warm, hand-crafted "leather-bound adventure annual" — the clean-line style of Tintin's *ligne claire* meeting gaslit Victorian London. Warm parchment and ink, engraved "plates", no flat modern/SaaS look, no cold blues, no pure black or white. A light and a dark ("Study by Candlelight") theme are both first-class, and eight interface languages are supported.

Cases can ship their own art for rooms, suspects, and objects; when a case doesn't, the game falls back to hand-drawn preset engravings and then to procedural placeholders, so the world always looks intentional.

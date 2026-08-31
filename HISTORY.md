# The Story of Sherlock's Legacy

## A Loophole

Every first-year programming student at my university gets the same four lab assignments: object-oriented programming, data structures, file I/O, and networking. Four separate exercises. Isolated, safe, and - if I'm honest - pretty boring.

I saw a loophole. Instead of doing four small, disconnected assignments, I proposed something else to my practitioner: let me build one cohesive project that satisfies all four requirements at once. Networking would become multiplayer. File I/O would become loading content from files instead of hardcoding it. Object-oriented programming and data structures would become a world of interacting rooms, suspects, and clues.

I've always loved detective fiction - Agatha Christie, Sherlock Holmes, the whole locked-room mystery subgenre. On the games side, I loved the interrogation drama of Ace Attorney, the feeling of slamming down the one piece of evidence that breaks a lie. I wanted that fantasy - being the clever one in the room - as something you could actually play.

My practitioner approved the pitch. This was the harder path by far: original game design, writing, and system architecture on top of the standard academic requirements, with no fallback assignment if it all fell apart.

I said yes anyway.

## The First Redo

The first version of the game was hardcoded. Every suspect was its own class. Every room was its own class. They extended common base classes, sure, but the story itself - the mystery, the map, the characters - was welded directly into the code. Want to add a room? You'd have to recompile the entire game.

My practitioner looked at this and told me to make it dynamic - to load rooms and suspects from external files instead of hardcoding them into the class hierarchy.

That meant redoing the project from its foundations. Weeks of work, restructured from the ground up.

It turned out to be the best thing that could have happened to it. Rebuilding the game as data-driven gave it a superpower I didn't fully appreciate at the time: today, there is not a single line of story anywhere in the game's code. Every room, suspect, lie, clue, and exam question lives in an external case file. The code itself is just an engine that reads them. That one forced redo is the reason this project became an engine instead of a single, disposable game.

## The Deadline

There was one lab left on the list: the graphical interface. It was optional, and by the time I reached it, there was no time left.

The game was submitted to my practitioner as a terminal-only application. Type `move north`. Type `examine desk`. It worked - completely and correctly - but it was invisible to anyone who doesn't already love a command line.

The grading rubric measured the four labs. It had no box for "invented an entire game design," "wrote an original mystery," or "balanced a playable deduction system." The creative work simply wasn't something the rubric could see.

I did not get a good grade.

I'm not saying this to criticize my practitioner - the rubric measured what it was built to measure, and it measured it fairly. It just wasn't built to measure this.

Most side projects die exactly here: the semester ends, the grade is recorded, and the repository goes quiet forever.

Mine didn't.

## After the Semester

With no deadline and nothing riding on it anymore, I kept building. I started with the thing I'd never had time for: a face for the game.

### The interface, twice

My first attempt used Java Swing - a 25-year-old UI toolkit that fought me at every turn. I abandoned it and rebuilt the interface in JavaFX, with modern layouts and full CSS theming. This time it stuck.

The trick that made the rewrite safe: the new interface doesn't replace the old game logic - it puppets it. Clicking a suspect's portrait quietly issues the same command the terminal version used to take by keyboard. The battle-tested core underneath never changed; the GUI is a skin layered on top of it. That's why a from-scratch interface rewrite didn't put the whole project at risk.

### Suspects who actually lie

Inspired directly by Ace Attorney's "Objection!" mechanic, every suspect in the game is secretly in one of three states - lying, truth, or panic - each with its own version of their story. Present the right evidence at the right statement and they crack, transition state, and reward you with a new deduction. The rules live on the state, not the suspect, so the same piece of evidence can do nothing against a lie and then break the same person wide open once they're telling the truth. Interrogation has sequence.

On top of that: **combine**, a second layer where two clues fuse into a brand-new named deduction - one that is itself usable as evidence in a later contradiction. It turns the mystery into a small logic graph the player assembles themselves, rather than a straight line of clicking through dialogue.

### Multiplayer, from scratch

No engine, no networking framework - a Java server built directly on NIO, with TCP carrying live gameplay and UDP handling LAN discovery so friends can find a hosted game without typing an IP address. Early on, single-player and multiplayer maintained two separate copies of the game's rules, and they drifted apart - bugs would show up in one mode and not the other. The fix was extracting a single shared engine that both modes drive through one command interface, so single-player stays fully offline and multiplayer stays in sync, with the rules existing exactly once.

### The detective's tools

A pinboard where you drag clue and suspect cards around and connect them with colored string - synced live across a multiplayer session. A shared journal. Case file dossiers. And Dr. Watson, upgraded into a real hint system: a free nudge when you're stuck, a sharper one once you already hold the right evidence, and a paid "analyse" option that costs you rank - because help should exist, but it should cost something.

### The Case Maker

The single biggest addition: a full authoring studio, built on the principle that the author should never have to touch a JSON file by hand. Rooms, suspects, dialogue, contradiction and combine rules, hints, and the final exam are all built through a GUI, including a visual placement editor for positioning every sprite in a scene exactly as it will appear in-game. Cases export as self-contained folders you can hand to a friend. The system that started as "load the story from a file" because a practitioner rejected my first draft became a tool that lets anyone - no code required - design their own murder.

### Shipping it properly

Eight UI languages, including full right-to-left support for Arabic. Tutorials that run on the real engine instead of a scripted fake, teaching each mechanic by actually executing it. Server-side authority so a friend can't cheat by tampering with their own client, and sandboxed handling of community case files so a stranger's case can't touch your machine. A self-contained desktop build - no Java, no manual setup, just double-click and investigate.

## Why This Matters

Four homework assignments. That was the original assignment. It got a mediocre grade.

And then it got a Case Maker, a real co-op mode, eight languages, an interrogation system built from a favorite childhood game, and a proper release.

The grade measured the homework. It was never built to measure the game.

Sherlock's Legacy is a fair-play detective game where every case is solvable from the clues you're given - no hidden information, no arbitrary guessing. Suspects lie. You catch them. You decide who did it.

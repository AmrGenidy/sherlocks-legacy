# Sherlock's Legacy — Security Plan

> Scope decided with the maintainer: **LAN-only** multiplayer (trusted local network), players **can
> import/share third-party case files**, and hiding a case's solution is a **nice-to-have** (deter
> casual peeking, not a hard guarantee). This is an internal engineering plan, not a
> vulnerability-disclosure policy.
>
> **Audited.** A `security-audit` pass has been run — findings in `.scratch/security-audit/FINDINGS.md`.
> This plan is updated to match: the transport/deserialization/DoS/command-authorization core is
> verified solid; the real residual risk is concentrated in **untrusted case files (Area A)**, one
> **answer-key leak**, and **dependency hygiene**.
>
> **Status (audit follow-up).** ✅ Done: **P0-1** case-path sandboxing, **P0-2** import limits, **P0-B**
> command authorization (verified pre-existing), **P1-1** per-connection idle timeout, **P1-2**
> dependency hygiene (jackson-databind 2.17.2, logback 1.5.21, slf4j 2.0.17, + versions/OWASP audit
> plugins), and the **D** answer-key leak (P2-1). **Remaining = optional** for a LAN-only game:
> low-severity network hardening (P2-2 client frame cap, P2-3 undecodable-frame rate-limit, P2-4
> lockout hole, P2-5 co-op griefing) and pre-release polish (hashed answers, code-signing). Nothing
> that materially threatens a trusted-LAN deployment is open.
>
> **Security track complete (follow-up).** P2-2..P2-5 are now also ✅ done — every actioned audit item
> is closed. Note: P2-2 uses a **1 MB** client-inbound cap (not 64 KB) because the sanitized case-list
> DTO measures ~92 KB and scales with case count; a tighter cap needs a trimmed `CaseSummaryDTO`
> (deferred — not urgent for a LAN library of a few dozen cases). Only pre-release polish (hashed
> answers, code-signing) and the accepted inert-PTV watch item remain.

## 1. Threat model

1. **A crafted/shared case file** (P0 surface — third-party cases are imported *and* auto-loaded from
   the `cases/` folder). Untrusted data; risk is malicious *paths*, oversized content, parser abuse.
2. **A malicious/compromised peer on the LAN** (mostly mitigated — see §2). Residual: low-severity
   lockout/griefing gaps only.
3. **A curious local player** (P2) reading a case's solution off disk. Low severity.
4. **A LAN eavesdropper** (P2). Plaintext traffic; low severity for casual LAN.
5. **Supply-chain / distribution tampering** (P2).

Out of scope: internet-facing play (would sharply raise 2, 4, 5 — revisit if scope changes).

## 2. Current posture (verified by audit — mostly solid)

Confirmed strong, **no action needed**:

- **No native Java serialization on the wire** — Jackson only; the Java-deserialization RCE family
  does not apply.
- **Deny-by-default polymorphic allowlist** (`common/SerializationUtils`): default typing constrained
  by a `PolymorphicTypeValidator` to `common.commands.` / `common.dto.` / `JsonDTO.` + enumerated
  concrete collections. Construction is **inert** across all those classes (no side-effect gadgets),
  and a **thorough boundary regression test already exists**. Rule to preserve: keep command/DTO
  constructors side-effect-free.
- **Server-authoritative command authorization is effectively DONE** (this was the plan's big P0-B):
  identity is server-stamped, host-only ops gate on server-held state, exam correctness is scored in
  the engine with the correct combinations **never sent** to clients, no command reads a target-player
  id from its payload, and a malformed command can only disconnect its own sender (catch-all + finally
  unlock — no host crash).
- **DoS baseline present**: **64 KB server-inbound** frame cap, per-field `WireLimits` on ~50 wire
  classes, command rate limiter, connection cap + slow-reader drop.
- **UDP LAN discovery** is validated and **non-auto-connecting**.
- **Exam-start answer leak already fixed** (issue 04).

Corrections folded in from the audit: the earlier "10 MB frame cap" note was stale — **server-inbound
is 64 KB**; only the **client-inbound** path still allows 10 MB (see P2-6).

## 3. Controls & backlog

### A. Untrusted case files — **P0** (import *and* auto-load)

- **P0-1 · Sandbox all case-provided paths.** `imagePath`/`soundtrack` flow into `ResourceResolver`
  (`ResourceResolver.java:31-59`) → `new File(path).exists()`. Today `..` traversal, absolute paths,
  and **Windows UNC (`\\host\share`)** are accepted — and a UNC path triggers an **outbound SMB / NTLM
  credential-leak** at the `exists()` check. Critically, this fires **automatically**: `CaseLoader`
  loads every case in the `cases/` folder at startup/case-select and `CaseValidator` resolves each
  `imagePath`, so merely *placing* a hostile case in the folder egresses — no explicit import needed.
  Fix: sanitize **before any filesystem touch** — reject absolute paths, `..`, and `\\`/`//` prefixes;
  confine resolution to the case's own directory + bundled resources; never resolve a non-sandboxed
  path. Enforce inside `ResourceResolver` so validation itself cannot egress.
- **P0-2 · Import limits.** No file-size cap and no room/object/suspect/string count caps
  (`CaseLoader.java:47,160`, `CaseValidator.java`) — a well-formed hostile case can OOM the app. Add:
  max file size, and caps on rooms/objects/suspects/choices/string lengths/total assets, enforced at
  the validation gate; refuse anything over budget.

### B. LAN network — residual low-severity gaps only

- **P1-1 · Per-connection idle timeout.** No idle timeout, so a slow-loris can hold all 16 slots /
  dangle partial frames. Add idle/read timeouts and reap stalled connections.
- **P2-6 · Lower the client-inbound frame cap** from 10 MB to match the 64 KB server-inbound cap
  (defense-in-depth: a malicious host shouldn't be able to hand a client a huge frame).
- **P2-3 · Rate-limit undecodable frames.** Only *decoded* messages are rate-limited; a flood of
  garbage frames is bounded only by the write-queue cap. Count/limit decode failures per connection.
- **P2-4 · Lockout hole.** `super(false)` commands (`UpdateTaskStateCommand`, `ContinueGameCommand`)
  skip the server-side exam/review lockout. Low, bounded — bring them under the same gate.
- **P2-5 · Shared-pool co-op griefing.** A peer can spam `DeduceCommand`/task toggles to drain shared
  tokens / inflate penalties (not impersonation). Add per-player action limits on shared-pool actions.

### C. App & dependency integrity — **P1/P2**

- **P1-2 · Dependency hygiene.** logback 1.4.8 (behind several CVE fixes — **low real exploitability
  here**, not using vulnerable appenders) and Jackson 2.15.2; no dependency-audit step in `pom.xml`.
  Add a dependency-audit/update cadence; the whole default-typing defense relies on Jackson behaving.
- **P2 · Sign the distributable + publish checksums**; least-privilege (user-dir writes only); logging
  hygiene (no secrets, no untrusted input into log patterns).

### D. Hiding the solution — **P2**, but one quick win

- **QUICK WIN · Answer-key leak in the case list.** `AvailableCasesDTO` still ships every case's
  `correctCombination` to any client browsing "Host" (issue 04 fixed only the exam-start broadcast).
  Strip `correctCombination` (and any answer-key fields) from the browse DTO — the host/engine already
  scores server-side. Small, self-contained; do it alongside the P0s.
- **P2 · Hashed final-exam answers** (store a salted hash, compare submissions) and optional
  at-rest case obfuscation. Raises the casual bar; not bulletproof, only worth it for competitive play.

## 4. Prioritized backlog (do in this order)

| Status | Pri | Item | Area | Ref |
|--------|-----|------|------|-----|
| ✅ | P0 | Sandbox case paths (no absolute/`..`/UNC; confine to case dir + presets; sanitize before FS touch) | A | P0-1 |
| ✅ | P0 | Import limits (file size + room/object/suspect/string/asset caps + StreamReadConstraints) | A | P0-2 |
| ✅ | Quick win | Strip `correctCombination` from lobby case-list payload (server `LobbyCaseSanitizer`) | D | P2-1 |
| ✅ | P1 | Per-connection idle/read timeout (slow-loris) | B | P1-1 |
| ✅ | P1 | Dependency bumps (jackson 2.17.2, logback 1.5.21, slf4j 2.0.17) + versions/OWASP audit | C | P1-2 |
| ✅ | — | Server-authoritative command authorization (verified already implemented) | B | P0-B |
| ✅ | P2 | Client-inbound frame cap lowered 10 MB → **1 MB** (see note; 64 KB blocked by ~92 KB case list) | B | P2-2 |
| ✅ | P2 | Rate-limit undecodable-frame flood (`DecodeFailureLimiter`, 20/10 s → drop connection) | B | P2-3 |
| ✅ | P2 | `super(false)` task/continue commands routed through the exam/review lockout | B | P2-4 |
| ✅ | P2 | Per-player shared-pool throttle (`SharedPoolThrottle`, 30/10 s; deduce + task toggles) | B | P2-5 |
| ⬜ | — | Trimmed `CaseSummaryDTO` for browsing (would allow a 64 KB cap; deferred, not urgent) | D | — |
| ⬜ | — | Hashed final-exam answers (+ optional case obfuscation) — pre-release polish | D | — |
| ⬜ | — | Code-sign distributable + checksums; logging hygiene — pre-release polish | C | — |

Every ⬜ item is now optional pre-release polish or a deferred nice-to-have; no security-relevant risk
is open for a trusted-LAN deployment.

Dropped from the original plan (audit-verified as already done): server-authoritative command
authorization, PTV tightness + regression test, UDP discovery validation, per-field wire validation,
connection cap / rate limiting, server-inbound frame cap.

## 5. Verification

- Regression tests to add with the fixes: a path-traversal / absolute / UNC `imagePath` in a case is
  refused **before** any filesystem access; import limits reject an over-budget case; the browse DTO
  contains no `correctCombination`; an idle connection is reaped.
- Confirm during P0-1 that no case-load path is ever network- or auto-triggered beyond the known
  `cases/`-folder startup scan (that scan is exactly why P0-1 is urgent).
- Re-run `security-audit` after the P0/P1 fixes and reconcile against `.scratch/security-audit/FINDINGS.md`.
- Re-review this plan whenever multiplayer scope changes (especially LAN → internet).

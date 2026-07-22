# Project: doltrooms (doltlite-room-bridge)

A bridge between Room 3 (the Kotlin Multiplatform release of
androidx.room) and DoltLite (DoltHub's SQLite fork with Git-style
version control), implemented as a custom `androidx.sqlite`
`SQLiteDriver` that links `libdoltlite` instead of sqlite3. Currently
in the maintenance phase (`ARCHITECTURE.md` §4): the implementation
iteration completed 2026-07-18 at Step 11, and the step-by-step plan
file has since been retired — read `ARCHITECTURE.md` and this file
before doing anything else. New feature work opens only by human
decision as a new iteration.

## Governing documents

- **`README.md` is a statement of fact for the repository's current
  state.** It is human-curated and sacred. Never modify it. If it
  becomes out of date, suggest edits or additions to the human instead
  of making them yourself.
- **`ARCHITECTURE.md` holds this project's architectural decisions**
  (D1–D11). It may be agent-written and may contain historical detail.
  Read it before any non-trivial change. You are not permitted to make
  changes elsewhere in the project that violate a decision in
  `ARCHITECTURE.md` unless explicitly told to. When such a decision is
  overridden, `ARCHITECTURE.md` must be updated to match the new
  decision. It contains only settled decisions — never add
  speculative/deferred material to it. Where it and `README.md`
  disagree, the README is the newer decision: update `ARCHITECTURE.md`
  to follow it.
- **`docs/deferred-verification.md` is the live verification
  burn-down** — implemented-but-unverified work, one entry per gap
  with what to run and where, flipped to VERIFIED (entry kept for the
  record) as hardware becomes reachable. Keeping it truthful in the
  same session that closes or discovers a gap is part of the work.
  (The step-by-step implementation plan that used to live in
  `PLAN.md` was retired and deleted in July 2026 after its backlog
  completed; the repo state plus these governing documents are the
  context carried between sessions.)
- **`docs/FEASIBILITY.md` holds the founding research** — why the
  bridge is DoltLite-as-driver and why Room-to-Dolt-server is
  infeasible. It is context, not decisions, and it is a snapshot
  (2026-07-17, pre-DoltLite-0.11.28): where it conflicts with a
  skill's more recently verified claim, trust the skill and its
  citation. Read it when the user asks "why this design" questions.

## Working rules (contributing guidelines)

- **Small and auditable beats fast.** The human is following every
  file. Prefer one well-explained file over three generated ones. Do
  not scaffold ahead of the current iteration's scope
  (`ARCHITECTURE.md` §4) — currently that means maintenance only:
  bug fixes, doc/skill truthfulness, and deferred-verification
  burn-down; no new features without a human-opened iteration.
- **Never answer from memory about Room 3 or DoltLite.** Both shipped
  in March 2026, past most training cutoffs, and DoltLite releases
  near-daily. Load the relevant skill; if the skill doesn't answer
  it, fetch and cite per the `skill-maintenance` workflow, then fold
  the verified fact back into the skill.
- **Pin versions deliberately.** DoltLite's storage format may break
  between versions (`doltlite` skill); when dependencies are
  eventually added, one DoltLite version must be pinned across all
  platform artifacts, and claims in skills should record the version
  they were verified against.
- **After editing any `.md` file, re-check cross-references.** Update
  decision ids (D1–D11), section numbers (§1–§4), file names, and
  links in the other docs (`AGENTS.md`, `ARCHITECTURE.md`,
  `.agents/skills/`) that point at what you changed. A dangling `§`
  or renamed file reference is a bug.
- **Branch and PR flow:** day-to-day work happens on `develop` or
  feature branches; PRs target `main`. Keep commits small and
  single-topic; never commit `build/`, `.gradle/`, `.kotlin/`, or
  `.idea/` content. Do not commit or push without being asked.
- Build/test loop: `./gradlew build`, tests
  via `./gradlew :library:allTests` (JVM/native) — Android host tests
  run under `:library:testAndroidHostTest`.

## Skills

`.agents/skills/` holds reference docs (progressive disclosure: read
a `SKILL.md` only when its trigger hits, follow its `references/`
links only as routed).

Citation convention: skills cite upstream docs inline at the point of
each claim, and cite `ARCHITECTURE.md` by decision id (`D1`) or
section (`§4`), never by file:line — line numbers are brittle and
break on every edit (see the `skill-maintenance` skill).

Active for this iteration:

- [`room3`](.agents/skills/room3/SKILL.md) — Room 3
  (`androidx.room3`) KMP: Gradle/KSP wiring, `setDriver`, connection
  pooling, `useWriterConnection`/`@RawQuery` for `dolt_*` SQL,
  `@SkipQueryVerification`, testing patterns.
- [`androidx-sqlite`](.agents/skills/androidx-sqlite/SKILL.md) — the
  `SQLiteDriver`/`SQLiteConnection`/`SQLiteStatement` contract we
  implement, verbatim interfaces, `BundledSQLiteDriver` internals and
  its sqlite3 call map, third-party driver precedents.
- [`sqlite-c-api`](.agents/skills/sqlite-c-api/SKILL.md) — the
  `sqlite3_*` C API from a driver-implementer's view: statement state
  machine, bind/column rules, errors, threading modes, transactions,
  and the fork-divergence watchlist for DoltLite.
- [`doltlite`](.agents/skills/doltlite/SKILL.md) — DoltLite itself:
  C-API compatibility, `dolt_*` SQL surface, remotes and
  `doltlite-remotesrv`, platform artifacts, version/format-stability
  gotchas.
- [`dolt`](.agents/skills/dolt/SKILL.md) — Dolt-proper as conceptual
  template and unbridged sync target; why Room↔Dolt-server is
  infeasible; the procedure/system-table surface DoltLite mirrors.
- [`kmp-native-interop`](.agents/skills/kmp-native-interop/SKILL.md)
  — wrapping a C library per platform: cinterop `.def` files, JNI and
  native-lib packaging (AAR jniLibs vs JAR resources), JNA
  trade-offs, the AGP KMP library plugin, publishing.
- [`kotlin-audit-baseline`](.agents/skills/kotlin-audit-baseline/SKILL.md)
  — verified Kotlin best-practice audit rules for reviewing code
  here: public-API hygiene (explicit visibility/types, KDoc, detekt
  libraries ruleset, Explicit API mode, binary-compatibility
  validation), expect/actual and cinterop/JNI interop checks,
  coroutines discipline (dispatchers, cancellation,
  CancellationException), and the AGENTS.md agent-guidelines
  landscape. Load when auditing/reviewing Kotlin or debating lint
  and API-stability tooling.
- [`architecture-docs`](.agents/skills/architecture-docs/SKILL.md) —
  conventions for maintaining `ARCHITECTURE.md`; load before editing
  it.
- [`red-green-testing`](.agents/skills/red-green-testing/SKILL.md) —
  the red/green/refactor (test-first TDD) discipline mandated by
  `ARCHITECTURE.md` D7 for new-from-scratch classes/features: the cycle,
  the three laws, the test list, and how red/green maps to `commonTest`
  and differential validation against `BundledSQLiteDriver`. Load before
  writing any net-new production class.
- [`skill-maintenance`](.agents/skills/skill-maintenance/SKILL.md) —
  **load before creating, editing, moving, or deleting any skill**,
  and before any WebFetch/WebSearch on a project library; the Agent
  Skills format (Anthropic level 1/2/3 + agentskills.io spec), the
  verify→quote→cite→sync→validate workflow, `skills-ref validate`.

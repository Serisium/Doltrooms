# Design: Entity-grade Dolt primitives for Room 3 — decisions

**Status: DECIDED, prototype-proven — 2026-07-23.** Read path only;
writer verbs stay on `DoltDatabase` (D10). Evidence lives in the
runnable suites: `prototypes/room-entity-primitives/` (verifier shim +
23 tests), `library/src/jvmTest/.../DoltReadSurfaceProbeTest.kt` (15
pinned engine facts), `BackroomsEpochSeedTest`. The exploration record
(approach comparison, per-query matrix) is in this file's git history.

## The decision

End users write ordinary verified `@Query`/`@DatabaseView` SQL against
Dolt's read surface and get compile-time verification, typed POJOs,
`suspend`, and `Flow`. This is delivered by swapping Room's
verification engine to DoltLite via a KSP-classpath artifact, plus a
small set of library-shipped types and recipes.

## Decided objects

### 1. `doltrooms-verifier` — the verification artifact

A host-JVM-only artifact shadowing xerial's `org.sqlite.JDBC`,
`org.sqlite.SQLiteJDBCLoader`, and `org.sqlite.SQLiteConnection` with a
JDBC facade over the public `DoltLiteDriver` (~200 lines; prototyped in
`prototypes/room-entity-primitives/verifier-shim/`). It serves every
KMP target (verification is host-side) and never ships in the app.
Packages host natives (linux-x64, osx-arm64, osx-x64) from the pinned
amalgamation (D9).

Behavioral contract (pinned from room3-compiler 3.0.0 bytecode; re-pin
on Room upgrades):
- `createConnection("jdbc:sqlite::memory:")` → `DoltLiteDriver().open(":memory:")`.
- `executeUpdate` runs each entity/index/view DDL, then
  `dolt_commit('-Am', …)` — per-table TVFs exist only for committed
  tables.
- `prepareStatement` maps `SQLiteException` → `SQLException` (the
  compile error); metadata = column names from the prepared statement,
  affinities from a step-once harvest (NULL affinity on empty results),
  `getTableName` = null.
- Connection failure prints a loud stderr banner before rethrowing
  (Room otherwise disables verification silently).

Consumer wiring:

```kotlin
ksp("androidx.room3:room3-compiler:3.0.0")
ksp("dev.seri.doltrooms:doltrooms-verifier:<version>")
configurations.matching { it.name.startsWith("ksp") }.configureEach {
    exclude(group = "org.xerial", module = "sqlite-jdbc")
}
```

Canary (documented consumer recipe): temporarily attach a DAO with a
deliberately bad query; the build must fail with a DoltLite-sourced
error. Proves verification is live.

### 2. Library row types and query builders (`commonMain`)

Typed rows for the system tables — `BranchRow`, `CommitRow`, `TagRow`,
`StatusRow`, `RemoteRow`, `DiffStatRow`, history rows — and
`RoomRawQuery` builders for the per-table TVFs, whose table name is
part of the function NAME and cannot bind: `tableDiffQuery(table,
from, to)`, plus `tableAtQuery`/`tableHistoryQuery` siblings.
Deliberate ABI event (D11, `updateLegacyAbi`).

### 3. `DoltEvent` — the invalidation anchor

Version-control verbs perform no DML, so no Room flow can observe them
directly. Decision: a one-row entity bumped by the library's
writer-side verbs after each vcs operation. Flow recipes on top:

- Branches/tags/status (always-fresh surfaces):
  `@RawQuery(observedEntities = [DoltEvent::class])` with fixed SQL.
- Commit log: `dao.commitTicks().map { dolt.log() }` — a verified
  `@Query` flow over the anchor mapped through the writer-connection
  helper. Required because reader connections freeze their
  `dolt_log`/`dolt_history` walk at open-time (constraint 4 below);
  `@Transaction` does not reroute flows to the writer (verified).

### 4. The epoch seed

`library/src/jvmTest/resources/dolt/backrooms-epoch-2019.db` (1148
bytes): a database whose history begins 2019-05-13 12:00:00, minted
under a pinned clock (recipe in `BackroomsEpochSeedTest`; re-mint on
engine pin bumps). Standard seed for read-path tests; the Backrooms
production fixture builds on top with fully date-coherent history.

## Engine constraints the surface is shaped by (0.11.33, probe-pinned)

1. No `dolt_log('<ref>')`, no `dolt_commits`: per-ref history is hash
   chains only (recursive CTE over repo-wide `dolt_commit_ancestors`
   from `dolt_branches.hash`); metadata needs the current branch or an
   upstream feature.
2. `dolt_diff_stat(from,to)` fails when the window contains a
   `sqlite_sequence` change — i.e. inserts into Room `autoGenerate`
   tables. Safe on update/delete-only windows.
3. Root commit date is immutable (`--amend` refuses the root); the
   ancestry-filtered `timelineByDate` query or an epoch seed handles
   date-ordered reads.
4. Reader connections freeze commit-graph walks (`dolt_log`,
   `dolt_history_<t>`) at their open-time session head; data,
   `dolt_branches`, `dolt_commit_ancestors`, `dolt_at_<t>(ref)` are
   always fresh.
5. `@DatabaseView` DDL lands in `dolt_schemas`, dirties the working
   tree, and is versioned per-branch — consumers commit once after
   first open, before branching.
6. `PagingSource` DAO functions are Android-target-only in Room 3.0.0.

## Productization steps

1. Promote the shim into the `doltrooms-verifier` artifact with
   packaged host natives.
2. Land the row types + builders in `commonMain` (`updateLegacyAbi`).
3. Fold the `DoltEvent` bump into `DoltDatabase`'s writer verbs.
4. Docs: consumer wiring + canary + §constraints in USAGE.md.
5. Automate the canary as an expected-failure compile test.
6. Validate paging + the shim flow in the Android sample.
7. Upstream requests: `dolt_log(ref)`/`dolt_commits`; `dolt_diff_stat`
   sqlite_sequence fix; session-head refresh on statement boundaries.

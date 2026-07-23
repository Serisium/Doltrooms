# Design: Entity-grade Dolt primitives for Room 3

**Status: DESIGN with working prototype — 2026-07-22.** Human-opened
exploration (Seri, 2026-07-22); pre-production, so breaking existing
contracts is allowed. Complements `docs/design/vcs-interface-draft.md`
(the writer-side git-verb facade): this document covers the READ path —
making Dolt's system tables, TVFs, and diffs consumable through ordinary
verified `@Query` SQL with typed results and Room's coroutine machinery.

All engine facts below were probed at the DoltLite 0.11.33 pin; all Room
facts against room3-compiler 3.0.0. Evidence lives in two committed,
runnable places:

- `library/src/jvmTest/.../dolt/DoltReadSurfaceProbeTest.kt` — the
  engine-fact probes (11 tests).
- `prototypes/room-entity-primitives/` — the working prototype: a
  verifier shim + a DAO whose target queries compile **with verification
  on** and pass 10 runtime tests on DoltLite
  (`cd prototypes/room-entity-primitives && ../../gradlew :app:test`).

## 1. Recommendation (up front)

**Ship approach D — a `doltrooms-verifier` artifact for the consumer's
KSP classpath that swaps Room's compile-time verification database from
stock SQLite to DoltLite — layered with the small amount of B
(`@RawQuery` builders) that per-table TVF naming forces, and library-
shipped row types (the POJO half of A).** With the shim on the processor
classpath, end users write ordinary `@Query`/`@DatabaseView` SQL against
`dolt_branches`, `dolt_tags`, `dolt_log`, `dolt_status`, `dolt_remotes`,
`dolt_commit_ancestors`, `dolt_at_<table>(ref)`,
`dolt_diff_<table>(from,to)`, `dolt_history_<table>` … and get real
compile-time verification (bad table/column names fail the build with
DoltLite's own error text), typed POJOs, `suspend`, and — within the
invalidation limits of §6 — `Flow`.

The prototype proves the full loop: 9 of the 10 target queries compile
verified and run green (§5); the tenth (`PagingSource`) is an
Android-only Room feature, not a dolt limitation.

## 2. Why plain `@Entity` cannot work (approach C — dead end)

Two independent walls, both already established and re-confirmed:

1. **Compile time:** Room's `DatabaseVerifier` prepares every `@Query`
   against in-memory *stock* SQLite via xerial JDBC; dolt names fail
   with "no such table/function". There is no global verification-skip
   option and no externally-managed-entity opt-out.
2. **Runtime:** Room's generated open delegate CREATEs every entity
   table and validates its `PRAGMA table_info` (including a required
   `@PrimaryKey`). A `@Entity(tableName = "dolt_branches")` would
   collide with the engine's virtual table and fail validation.

Approach C would need two upstream Room features (verification engine
injection + externally-managed entities). Approach D delivers the first
of those *without* forking Room, because the verifier's engine binding
is a loose, name-based JDBC contract (§4). The second stays out of
reach — dolt system tables can never be `entities = [...]` members —
but §5 shows that matters less than expected: plain POJOs + views cover
every read shape except direct invalidation tracking.

## 3. The approaches

| | Mechanism | Verification | User experience |
|---|---|---|---|
| **A** | Library ships `@Dao` interfaces annotated `@SkipQueryVerification` + `@ColumnInfo` POJOs | None (skipped) | Canned queries only — consumers cannot write their own dolt SQL verified |
| **B** | `@RawQuery(observedEntities=[...])` + library `RoomRawQuery` builders | Never verified (by design) | Typed results + Flow; SQL is opaque strings |
| **C** | Plain `@Entity` over dolt tables | n/a | Dead end (§2) |
| **D** | `org.sqlite` JDBC facade over DoltLite on the KSP classpath | **Full, native** — dolt SQL verifies like ordinary schema | Ordinary `@Query`/`@DatabaseView`; compile errors for typos in dolt SQL |

A and B remain useful *under* D: A's POJO row types (`BranchRow`,
`CommitRow`, …) should ship in the library regardless of who verifies
the SQL, and B is still *required* for exactly one shape — the
per-table TVFs (`dolt_diff_<table>`), whose table name is part of the
function NAME and cannot bind as a parameter, so a DAO cannot express
"diff any table" as one `@Query`. (A fixed table's diff *can* be a
verified `@Query` — target query 8 embeds
`dolt_diff_fruittie('HEAD~1','HEAD')` literally.)

## 4. Approach D: the verifier shim

### 4.1 The contract (pinned from room3-compiler 3.0.0 bytecode)

`DatabaseVerifier` binds its engine by *class name*, not by
`DriverManager` URL resolution. The complete surface it touches:

- static init: `org.sqlite.SQLiteJDBCLoader.initialize()`,
  `org.sqlite.JDBC.isValidURL("jdbc:sqlite::memory:")`,
  `DriverManager.getDriver("jdbc:sqlite:")` falling back to
  `new org.sqlite.JDBC()`; warns unless the resolved driver is
  `instanceof org.sqlite.JDBC`.
- `create()`: `org.sqlite.JDBC.createConnection("jdbc:sqlite::memory:",
  Properties)` — a **static** call whose bytecode descriptor returns
  `org/sqlite/SQLiteConnection`, so that exact class must exist. Any
  `Throwable` here is swallowed into the
  `CANNOT_CREATE_VERIFICATION_DATABASE` warning (verification silently
  off — §4.4).
- schema setup: `connection.createStatement().executeUpdate(ddl)` for
  every entity `CREATE TABLE`, every index, and every `@DatabaseView`'s
  `CREATE VIEW`.
- `analyze(sql)`: `connection.prepareStatement(sql)` — an
  `SQLException` here becomes the "There is a problem with the query"
  **compile error** — then `getMetaData()` (never executes the
  statement): `getColumnCount`, `getColumnName(i)`,
  `getColumnTypeName(i)` (matched against affinity names
  `NULL/TEXT/INTEGER/REAL/BLOB`; anything else falls back to NULL
  affinity), `getTableName(i)`.

That is the whole engine dependency: three named classes, six JDBC
interface methods, four metadata getters.

### 4.2 The shim (prototyped, working)

`prototypes/room-entity-primitives/verifier-shim/` implements the
contract over the **public** `DoltLiteDriver` (androidx.sqlite API) —
no new native code, no touching the library's internals:

- `org.sqlite.JDBC`, `org.sqlite.SQLiteJDBCLoader`,
  `org.sqlite.SQLiteConnection` shadow xerial's classes; the big
  `java.sql` interfaces are `java.lang.reflect.Proxy` handlers, so only
  the ~10 methods Room calls are implemented (anything else throws
  `UnsupportedOperationException` loudly).
- `createConnection` → `DoltLiteDriver().open(":memory:")` — probed
  fact: `:memory:` DoltLite supports the full dolt_* surface.
- **Commit-after-DDL:** after every `executeUpdate`, the shim runs
  `SELECT dolt_commit('-Am', 'verifier schema')`. Required — DoltLite
  materializes the generated per-table TVFs (`dolt_at_<t>`,
  `dolt_diff_<t>`, …) only for committed tables; without it, target
  queries 5/8 fail verification with "no such table: dolt_at_fruittie"
  (observed).
- **Metadata:** column names come from the prepared statement
  (`sqlite3_column_name` needs no execution). androidx.sqlite exposes
  no decltype, so the shim steps once inside try/catch and reads the
  first row's storage classes for affinities; empty results (all
  ordinary user-table queries — the verify repo's tables are empty)
  report NULL affinity, which Room treats as "unknown" exactly as it
  does xerial's expression columns. Observed: **zero** Room warnings on
  a clean build of the whole prototype DAO. `getTableName` returns null
  ("unknown origin"); consequence: Room resolves multimap column
  ambiguity by parsing, and the disjoint-column multimap (query 9)
  works; heavily-ambiguous multimaps may need `@MapColumn` (untested).
- Consumer wiring (deterministic, not classpath-ordering-dependent):

  ```kotlin
  dependencies {
      ksp("androidx.room3:room3-compiler:3.0.0")
      ksp("dev.seri.doltrooms:doltrooms-verifier:<version>")   // the shim
  }
  configurations.matching { it.name.startsWith("ksp") }.configureEach {
      exclude(group = "org.xerial", module = "sqlite-jdbc")     // exactly one org.sqlite
  }
  ```

  (If a project runs other KSP processors that need xerial, scope the
  exclude to the Room-bearing configurations instead.)

### 4.3 Why this serves every KMP target

Verification is a *host* concern: KSP compilations for all targets
(jvm, android, iOS, linux) run room3-compiler on the host JVM, so one
host-JVM shim serves the whole target matrix. The shim needs a
host-loadable DoltLite: the library jar packages `natives/linux-x64`
(covers Linux dev/CI hosts); on macOS the prototype borrows the
`compileDoltliteJniHost` dylib into the shim's resources. A shipped
`doltrooms-verifier` artifact must package host natives for
linux-x64, osx-arm64, osx-x64 (the D9 one-pin rule extends: same
amalgamation, same flags). The KSP daemon re-runs processors in fresh
classloaders; the loader's extract-to-fresh-temp-file pattern survives
this (observed across many KSP runs in one daemon).

### 4.4 The silent-fallback risk, mitigated

If the shim's connection fails, Room *silently disables verification*
(warning `CANNOT_CREATE_VERIFICATION_DATABASE`) and everything
compiles unchecked. Mitigations, all prototyped:

1. The shim prints a loud stderr banner before rethrowing, so the
   condition is visible in build logs.
2. **Canary recipe** (negative control): attach a DAO with a deliberate
   error and confirm the build fails. Recorded 2026-07-22 —
   attaching the prototype's `CanaryDao` produces:

   ```
   e: [ksp] ...: There is a problem with the query: Error code: 1, message: no such table: table_that_does_not_exist
   e: [ksp] ...: There is a problem with the query: Error code: 1, message: no such column: definitely_not_a_column
   ```

   Real DoltLite error text, sourced through the shim. The shipped
   artifact's docs should carry this recipe; a library-side compile
   test (expected-failure KSP invocation) can automate it later.
3. `@Query("SELECT dolt_version()")` compiles **only** under an active
   shim (stock SQLite rejects it) — a cheap positive control, though it
   cannot distinguish "shim active" from "verification disabled".

### 4.5 Risks

- **Internal contract.** The `org.sqlite.*` binding is room3-compiler
  *implementation*, pinned here from 3.0.0 bytecode. A Room upgrade can
  change it (Room 2.7.2's verifier is the same shape, so it is stable in
  practice, but it is not API). The canary turns a broken contract into
  a visible failure, and the shim itself is ~200 lines to re-pin.
- **Verification engine = DoltLite ≠ stock SQLite.** Under the shim,
  *ordinary* queries are also verified against DoltLite. That is
  arguably more correct for this project (the runtime engine IS
  DoltLite), and the library's conformance suite keeps the two engines'
  SQL surfaces aligned — but a stock-SQLite-only construct DoltLite
  rejects would now fail verification. None are known
  (`KnownDivergenceTest` tracks the divergence set).
- **Step-once execution.** Harvesting affinities executes the query
  against the throwaway verify repo. Write-shaped dolt calls
  (`dolt_commit(...)`) in a `@Query` would execute there — but those
  belong on the writer connection by design (D10), not in DAOs, and
  step errors are swallowed (prepare success is the verification
  signal). Documented, acceptable.

## 5. Target-query matrix (all verdicts empirical)

Compile = verified `@Query` compiles under the shim. Runtime = test in
`DoltPrimitivesDaoTest` green on DoltLite.

| # | Query | Verdict | Notes |
|---|---|---|---|
| 1 | `dolt_branches WHERE name LIKE :p` → `List<BranchRow>` | ✅ compile ✅ runtime | Pure win for D; A could only can it |
| 2 | branches ∪ `dolt_tags` ref search → `List<RefRow>` | ✅ ✅ | `dolt_tags` schema probed: `tag_name, tag_hash, tagger, email, date, message` |
| 3 | `dolt_log(:ref)` per-ref history | ❌ **engine gap** | `dolt_log` takes 0 args at 0.11.33 ("too many arguments on dolt_log() - max 0"). No approach can express it. Amended substitutes, both ✅✅: current-branch `dolt_log` → `List<CommitRow>`; per-ref **hash chain** via recursive CTE over repo-wide `dolt_commit_ancestors` (metadata for off-branch commits is unreachable — no `dolt_commits` table either; upstream feature request is the real fix) |
| 4 | per-table diff Flow via `@RawQuery(observedEntities=[Fruittie])` | ✅ ✅ | Table name is part of the TVF NAME → RawQuery is *required*, not a fallback; library ships the `RoomRawQuery` builder; Flow re-emits on entity DML (tested) |
| 5 | `dolt_at_fruittie(:ref)` → `List<Fruittie>` (real entity type) | ✅ ✅ | Needs the shim's commit-after-DDL; refs bind as parameters |
| 6 | `Flow<List<BranchRow>>` over `dolt_branches` | ✅ compile ❌ runtime | `IllegalArgumentException: There is no table with name dolt_branches` from the InvalidationTracker at collection. See §6 |
| 7 | `@DatabaseView("SELECT … FROM dolt_branches")` + verified query on the view | ✅ ✅ | D's flagship: view DDL verifies AND runs (Room creates it at open; DoltLite accepts views over system tables — probed). Caveats §6.2 |
| 8 | JOIN `dolt_log` × `fruittie` with embedded `dolt_diff_fruittie('HEAD~1','HEAD')` | ✅ ✅ | Cross-domain verified SQL, subquery TVF with literal refs |
| 9 | multimap `Map<BranchRow, List<CommitRow>>` | ✅ ✅ | Works with disjoint column names despite `getTableName` = null; ambiguous multimaps may need `@MapColumn` |
| 10 | `PagingSource<Int, CommitRow>` | ❌ platform gap | Room 3.0.0: "Only suspend functions are allowed in DAOs declared in source sets targeting non-Android platforms" — PagingSource is Android-only. The query itself verifies; expected to work in an Android source set (unvalidated — needs instrumentation) |
| + | `dolt_status`, `dolt_remotes`, `SELECT dolt_version()` | ✅ ✅ | `sqlite_sequence` appears in status alongside user tables (AUTOINCREMENT bookkeeping) |

Under approaches A/B alone: 1, 2, 3', 5, 8 become canned library
functions (no user-authored verified SQL), 6/7/9 are impossible (7
cannot even compile — `@DatabaseView` has no skip-verification escape),
4 is unchanged. That contrast is the case for D.

## 6. Reactivity and runtime semantics

### 6.1 What invalidation can and cannot see

Room invalidation is trigger-based on *declared entity tables*; probed:
`CREATE TEMP TRIGGER` works on user tables under DoltLite but is
rejected on dolt system tables. Consequences:

- `Flow` over dolt system tables directly (query 6) fails at runtime —
  the tracker refuses unknown table names.
- `@RawQuery(observedEntities=[UserEntity])` Flows re-emit on DML to
  those entities (query 4, tested) — the right tool for diff/status
  views that should track *data* changes.
- Version-control verbs (commit/checkout/merge) perform no DML on user
  tables → no trigger fires → **flows go stale across vcs operations**.
  Recipe until a better story exists: re-run reads after `DoltDatabase`
  verbs (the verbs are all suspend calls with known completion points —
  callers can `flow.retrigger()` by resubscribing or use plain suspend
  reads). A future library refinement: a one-row `dolt_events` entity
  the writer-side verbs touch after each vcs operation, giving every
  dolt-reading Flow an `observedEntities` anchor — expressible today
  with `@RawQuery(observedEntities=[DoltEvents::class])`, or even a
  verified `@Query` JOINing the anchor table. Not prototyped; noted as
  the obvious next experiment.

### 6.2 Views are versioned schema

Probed: `CREATE VIEW` lands in `dolt_schemas` and **dirties the
working tree**. So a `@DatabaseView` created by Room at first open
leaves `dolt_status` non-empty until the next commit, and the view is
part of per-branch schema — a branch created before the view existed
lacks it after checkout. Consumer guidance (for USAGE.md when this
ships): commit once after first open ("schema" commit), before
branching.

## 7. Productization sketch (not started)

1. New artifact **`doltrooms-verifier`** (host-JVM-only Gradle module):
   the three `org.sqlite` classes + proxy handlers, packaged host
   natives (linux-x64, osx-arm64, osx-x64) built from the pinned
   amalgamation (D9 discipline). It never touches the app runtime
   classpath. ~200 lines of Kotlin as prototyped.
2. Library `commonMain` gains the row types (`BranchRow`, `CommitRow`,
   `TagRow`, `StatusRow`, `RemoteRow`, …) and the `RoomRawQuery`
   builders (`tableDiffQuery`, `tableAtQuery`, `tableHistoryQuery`) —
   deliberate ABI event (D11, `updateLegacyAbi`).
3. Whether the library also ships a canned `@Dao` (approach A) becomes
   a UX choice, not a necessity; if shipped, its queries can be
   *verified* in this repo's own build via the shim instead of carrying
   `@SkipQueryVerification`.
4. Docs: consumer wiring + canary recipe + §6 semantics.
5. Test-first throughout (D7); the prototype's DAO tests seed the
   suite.

## 8. Open items

- Upstream: request `dolt_log('<ref>')` / `dolt_commits` in DoltLite —
  the one read shape (per-ref history with metadata) no design can
  reach today (§5 row 3).
- Validate query 10 (`PagingSource`) and the whole shim flow in the
  `samples/codelab` Android build (host-side KSP is identical; runtime
  paging is the open half).
- Prototype the `dolt_events` invalidation anchor (§6.1).
- Automate the canary as an expected-failure compile test.
- If/when the Room pin moves, re-pin the §4.1 bytecode contract (a
  10-minute javap pass; the shim fails loudly if a method goes
  missing).

# Module architecture: the doltrooms multi-module split

**Status: APPROVED DIRECTION — 2026-07-24.** Defines the module set,
the dependency DAG, per-module responsibilities, and where current and
contract-planned code lands. The version-control surface CONTRACT this
implements lives in `docs/design/vcs-interface-draft.md` (revision 4+)
on the `feature/vcs-interface` branch; this doc governs *packaging*,
that one governs *API*. Scope note: the contract's READ half is
audited/probe-backed; its WRITE half is still subject to change — the
module boundaries below are stable against both outcomes.

## Design rules

1. Each module has one concern, a clean public API, and its own proof
   (test gate) of that API's promise.
2. The dependency graph is a DAG — no cycles, verified by Gradle by
   construction (a cycle fails configuration).
3. Connection points between modules are explicit, documented, and
   diagnosable (each has a named failure mode and a probe query).
4. Kebab-case Gradle paths and artifact ids; `Dolt*` symbol prefix,
   `dolt-` module prefix (rationale below).

## The boundary mirror

DoltLite's own architecture is a two-layer cut at SQLite's `btree.h`
interface: everything above (VDBE, parser, planner) is untouched
SQLite, proven by differential oracle tests against stock SQLite;
everything below is the prolly-tree engine plus the `doltlite_*`
version-control surface. doltrooms mirrors that boundary one level up:

| DoltLite | doltrooms | Shared promise |
|---|---|---|
| above-`btree.h` SQLite compat (oracle-tested) | `:driver` (Room-conformance-tested) | drop-in replacement, no observable changes |
| the `doltlite_*` vcs layer | the `:dolt-*` module family | the version-control surface |
| `packaging/` (npm, Swift, …) | `:driver`'s KMP targets + natives | multi-platform distribution |

The subdivision *within* the dolt family (core/read/write/remotes) has
no DoltLite analog — it is doltrooms' consumer-facing refinement,
matching git's own command taxonomy ("examine the history and state" /
"grow, mark and tweak your common history" / "collaborate") and the
D3/D9 decision that sync is an opt-in surface.

## Naming

Module prefix is `dolt-`, not `vcs-`: every SQL object a consumer
touches is literally `dolt_*`, DoltLite names its layer `doltlite_*`,
and every public symbol in the contract is already Dolt-prefixed
(`DoltDatabase`, `DoltQuery`, `DoltRef`, `DoltEvents`, `DoltRowId`,
`DoltConnection`) — module name, SQL surface, and symbol vocabulary
stay one system. Upstream server-Dolt's `dtables`/`dprocedures`
(by-SQL-object-kind) taxonomy is deliberately NOT mirrored — it names
mechanism, not intent — but is kept as a diagnosis mapping:
`:dolt-read` consumes dtables + dtablefunctions + dfunctions;
`:dolt-write` calls dprocedures + session functions; `:dolt-remotes`
calls the sync subset of dprocedures.

## The DAG

```
androidx.sqlite ◄── :driver                      natives, cinterop, SQLite/Room parity
Room runtime    ◄── :dolt-core                   DoltRef, DoltEvents, row types, DoltRowId
                    :dolt-read    ──► :dolt-core @DoltQuery machinery (annotations, flow runtime, builders)
                    :dolt-write   ──► :dolt-core facade verbs, options DSL, MergeResult, anchor bumps
                    :dolt-remotes ──► :dolt-write fetch/push/pull/clone, Remotes collection
 [KSP classpath]    :verifier     ──► :driver(jvm)   org.sqlite shim for Room's compiler
 [KSP classpath]    :processor    ──► :driver(jvm)   @DoltQuery codegen + anchor lint + DDL emitter
                    doltrooms (BOM/umbrella)          version alignment + all-in-one coordinate
```

Deliberate property: **no runtime dolt module depends on `:driver` at
compile time.** The facade and generated code issue SQL through Room's
connection APIs; the engine contract is the `dolt_*` SQL surface
itself. The runtime graph is two independent stalks joined only by the
consumer wiring `DoltLiteDriver` into their Room builder; the BOM
aligns versions.

## Modules

### `:driver` — dev.seri.doltrooms:doltrooms-driver

SQLite compatibility. Importing this alone gives full command of
Room's basic features with Room-observable parity against the stock
driver: someone migrating from `BundledSQLiteDriver` drops this in,
performs a data migration (file formats differ), and sees no changes
at the Room API surface.

- Contains: `DoltLiteDriver` (expect/actuals, all targets),
  `DoltLiteNative` JNI glue, native loaders, cinterop defs, the
  amalgamation download/compile build machinery, packaged natives.
- Depends on: `androidx.sqlite` only. No Room dependency.
- Proof gate: the Room conformance suite (`AbstractRoomConformanceTest`
  + per-target runners) — the analog of DoltLite's differential oracle
  tests, and this module's formal acceptance gate.
- Honesty bounds on "parity" (engine-pinned, documented in KDoc):
  file format differs (migration required); INTEGER-PK tables are
  rowid tables with a branch-shared auto-increment counter
  (observable only once vcs verbs are used); `Dirty`-adjacent
  behaviors surface only via `dolt_*` calls, which this module never
  makes.

### `:dolt-core` — doltrooms-dolt-core

The shared kernel of the version-control surface: types both the read
and write sides need, so read and write stay siblings (no
write→read dependency).

- Contains (per contract): `DoltRef` sealed hierarchy (+
  `parent`/`minus`), the six `DoltEvents` anchor entities, the shipped
  row types (`BranchRow`, `CommitRow`, `TagRow`, `StatusRow`,
  `RemoteRow`, `DiffStatRow`), `DoltRowId`/`DoltEntityBase` (the
  blessed key supertype, contract §13).
- Depends on: Room runtime (annotations + entities).
- Proof gate: row-type schema tests against the pinned engine
  (schemas are probe-pinned at 0.11.33).

### `:dolt-read` — doltrooms-dolt-read

The reactive/declared read machinery — NOT the queries themselves
(those are consumer DAOs): `@DoltQuery`/`DoltConnection` annotations,
the runtime support the generated impls call into, the
runtime-dynamic-table `RoomRawQuery` builders.

- Depends on: `:dolt-core`.
- Proof gate: generated-flow behavior tests (anchor observation,
  Writer routing, distinct) — the P1/P2/P4 probe suites seed these.

### `:dolt-write` — doltrooms-dolt-write

The git verbs: `DoltDatabase` facade, noun collections
(`Branches`/`Tags` — `Remotes` moves out, below), options DSL, sealed
results, `ConflictPolicy`, the anchor-bump implementation (the §6 bump
matrix is this module's OUTPUT contract), `onBranch`. Owns the writer
session, hence also the session-state reads (`currentBranch()`,
`log()`) — a documented boundary feature, not a leak.

- Depends on: `:dolt-core`.
- Proof gate: `AbstractDoltDatabaseTest` verb suites (D7 test-first).

### `:dolt-remotes` — doltrooms-dolt-remotes

Sync: `Remotes` collection, `fetch`/`push`/`pull`, the `clone`
bootstrap. Separate because D3/D9 make sync an opt-in,
trusted-network-only surface (plain `file://`/`http://`). API-hygiene
split, not binary-size: the engine in `:driver` contains the sync code
regardless.

- Depends on: `:dolt-write` (pull = fetch + merge; reuses
  `MergeResult`/`ConflictPolicy`).
- Proof gate: remote round-trip suites against `file://` and
  `doltlite-remotesrv` fixtures.

### `:verifier` — doltrooms-verifier (host JVM, KSP classpath only)

The `org.sqlite` shim swapping Room's verification database to
DoltLite, per the read-path design. Never ships in an app.

- Depends on: `:driver` (jvm target, host natives).
- Proof gate: the canary (deliberately-bad query must fail the build
  with a DoltLite-sourced error), automated as an expected-failure
  compile test.

### `:processor` — doltrooms-processor (host JVM, KSP classpath only)

The library's own KSP processor: `@DoltQuery` codegen, the anchor
lint (contract §8), the DDL emitter with its pinned feature ceiling
(contract §12 D-d + §13 blessed shape).

- Depends on: `:driver` (jvm); knows `:dolt-read`'s annotations by
  qualified name (no compile dependency required).
- Proof gate: the P5 (`dolt-anchor-lint`) and P6/P7
  (`ksp-ddl-verify`, `entity-supertype-probe`) prototype harnesses,
  promoted into module tests.

### BOM / umbrella — doltrooms

Version alignment across all published modules plus an all-in-one
coordinate for consumers who want everything.

## Connection points and their diagnosis

| Connection | Mechanism | Failure mode | Probe |
|---|---|---|---|
| `:dolt-write` → `:dolt-read` reactivity | the `DoltEvents` anchors: write bumps, read observes (§6 bump matrix = the inter-module contract) | flows stop re-emitting | `SELECT tick FROM dolt_event_<domain>` before/after a verb |
| any `:dolt-*` → engine | the `dolt_*` SQL surface (runtime, not compile-time) | loud `SQLiteException: no such function: dolt_commit` on non-Dolt engines | `SELECT dolt_version()` |
| consumer ↔ `:dolt-core` | `@Database` registration of the six anchors (+ optional `DoltEntityBase`) | tracker errors at collection / lint error | documented entities block in USAGE |
| consumer ↔ `:verifier`/`:processor` | KSP classpath wiring + xerial exclusion | silent verification loss (Room warns only) | the canary |
| `:driver` ↔ Room | `SQLiteDriver` interface | conformance suite failures | run the suite |

## Where current code lands (the mechanical move)

From today's `:library` (measured 2026-07-24):

- `dev.seri.doltrooms.driver.*` — all five source sets (~807 lines),
  cinterop defs, native build tasks, loaders → `:driver`, unchanged.
- Room conformance tests → `:driver` test suites (its acceptance gate).
- `dev.seri.doltrooms.dolt.DoltDatabase` + result types (~527 lines)
  → `:dolt-write` **as an interim whole**: the current facade predates
  the rev-4 contract; its read members and superseded types are
  deleted, and its remotes members migrate to `:dolt-remotes`, when
  the rev-4 facade is implemented — the class is not split
  mechanically now.
- `AbstractDoltDatabaseTest` and dolt-verb tests → `:dolt-write`.
- `:dolt-core`, `:dolt-read`, `:dolt-remotes`, `:verifier`,
  `:processor` are scaffolded with build files, package skeletons, and
  a README each stating intended contents (populated when the rev-4
  implementation lands; the prototypes under `prototypes/` are their
  seeds).
- Publishing: per-module coordinates as listed; existing publishing
  conventions (Central Portal, `release` env) carry over. The ABI gate
  becomes per-module; the split itself is a deliberate ABI event
  (D11, `updateLegacyAbi` regenerated per module).

## Parking lot (future modules, not scaffolded now)

- `:testing` — published fixtures: the conformance suite and the epoch
  seed for consumers' own tests.
- `:migration` — stock-SQLite→DoltLite file migration helpers backing
  `:driver`'s migration story.

# doltlite-room-bridge — Architecture

**Status:** Target architecture as of 2026-08-04. Maintenance phase
(§4); the implementation iteration completed at Step 11, and
human-opened iterations have since landed samples (Step 12),
API-governance tooling (Step 13+), and the Kotlin Toolchain build
migration (D12). The step-by-step plan file that sequenced the
original iterations has been retired and deleted (2026-07-21), along
with its frozen snapshot.

This document specifies the project's intended end state — the
architecture every iteration builds toward, not a snapshot of what is
built today. Every entry is a settled decision; if something isn't
here, it isn't decided. Do not add speculative options, maybes, or
deferred "might later" material — only decisions.

`README.md` is the human-curated statement of the project and takes
precedence: when this document and the README disagree, the README is
the newer decision and this document must be updated to match it.

---

## 1. What this is

A bridge between Room 3 — the Kotlin Multiplatform release of
androidx.room — and DoltLite, DoltHub's SQLite fork with Git-style
version control. The bridge is a custom driver for Room's
`androidx.sqlite` driver API that links `libdoltlite` instead of
sqlite3, giving KMP apps a local, version-controlled database where
branch/merge/diff/commit are ordinary SQL calls.

The founding feasibility research lives in `docs/FEASIBILITY.md`. Its
verdicts are the basis of the decisions below.

## 2. Decisions

### D1 — The integration point is `androidx.sqlite`'s `SQLiteDriver`, nothing else

The bridge is an implementation of the three `androidx.sqlite` driver
interfaces — `SQLiteDriver`, `SQLiteConnection`, `SQLiteStatement` —
functionally a re-skin of `BundledSQLiteDriver` over `libdoltlite`.
Room, its annotation processor, and its generated code are consumed
unmodified; we never fork or patch Room. DoltLite's version-control
surface (`dolt_commit`, branches, diffs, merges) is reached as
ordinary SQL through DAOs or Room's raw-connection APIs, not through
new bridge-level APIs.

### D2 — The engine is DoltLite's sqlite3-compatible C API; the Dolt server is out of scope

The native engine under the driver is `libdoltlite`, consumed through
the `sqlite3_*` C API surface it preserves. There is no Go embedding,
no MySQL wire-protocol client, and no attempt to make Room talk to a
Dolt SQL server — `docs/FEASIBILITY.md` settled that as infeasible
(dialect, protocol, and driver-model mismatches). Any future
Dolt-server bridge would be an application-level ETL problem outside
this project.

### D3 — Sync targets `doltlite-remotesrv`, on DoltLite ≥ 0.11.28

The versioned-sync story is DoltLite's own remote protocol
(`dolt_clone/push/pull/fetch`) against a `doltlite-remotesrv` — not
Dolt remotes, not DoltHub. No interop between DoltLite and Dolt-proper
remotes is assumed anywhere in the design; if upstream ships it, that
is a new decision. Network sync requires DoltLite ≥ 0.11.28, the
release that added TLS 1.3 and bearer-token auth to the remote
protocol (see the `doltlite` skill); older versions have neither and
may only sync behind a trusted proxy. Amendment (Step 8): the TLS +
auth stack is excluded from the DoltLite amalgamation, and D9 builds
every platform from the amalgamation — so *this library's* engines
sync over `file://` and plain `http://` only, and network sync is
trusted-networks-only at any version. If upstream ships TLS in the
amalgamation, revisit.

### D4 — Platform ladder: JVM first, then Android, then iOS; web is speculative

Driver work lands in this order: JVM desktop (JNI shim — the
proof-of-concept and test vehicle), Android, iOS (Kotlin/Native
cinterop), then web. A JS/WASM driver is acknowledged as the hardest
target; by human decision of 2026-07-17 it is scheduled last
(implementation Step 9) as a best-effort, explicitly droppable rung —
nothing may be scaffolded for it before that step opens. Validation at
every rung is running existing Room test suites against the new
driver. Amendment (Step 9): the web rung was DROPPED, exercising the
droppable clause — no `wasmJs` target exists and none is scaffolded.
Three facts probed on 2026-07-18 settled it: (1) androidx.sqlite
2.7.0 splits its interfaces into nonWeb (synchronous
`open`/`prepare`/`step`) and web (`suspend`) variants, and once a web
target joins the target set, `commonMain` resolves the suspend
variants — the driver's shared expect surface stops compiling
("Non-suspend function 'open' cannot override suspend function"), so
a web driver shares no code with the other rungs and would force the
entire `commonMain`/`commonTest` tree onto a nonWeb intermediate
source-set rail; (2) the only workable web engine is the prebuilt
`@dolthub/doltlite-wasm` npm artifact (Kotlin/Wasm cannot link an
Emscripten-built C library) — exactly the upstream-prebuilt category
D9 rejects; (3) its OPFS storage is browser-only, so the driver is
untestable on a browserless Linux host. Revisit only as a dedicated
iteration if a web rung becomes a real requirement.

### D5 — The repo is a multi-module KMP library (amended 2026-07-24)

Original decision (2026-07-17): the project keeps the Kotlin
`multiplatform-library-template` shape — one `:library` module holding
the driver, no extra modules until an iteration explicitly needs them.
Amendment (Step 12, human-opened 2026-07-21): sample apps live under
`samples/` as *separate Gradle builds* that consume the library via
`includeBuild("../..")`; samples never become modules of it (still
true).

**Amendment (human-opened, 2026-07-24): the iteration that needs extra
modules arrived.** `:library` is dissolved into a module set with one
concern each, an acyclic dependency DAG, and per-module proof gates —
full design in `docs/design/module-architecture.md` (the DAG, the
DoltLite `btree.h` boundary mirror, connection-point diagnosis, and
naming rationale). The modules, briefly:

- `:driver` (`doltrooms-driver`) — SQLite compatibility: the
  `DoltLiteDriver` engine, natives/cinterop and their build machinery,
  proven by the Room conformance suite. Depends on `androidx.sqlite`
  only.
- `:dolt-core` (`doltrooms-dolt-core`) — shared kernel of the
  version-control surface: refs, invalidation anchors, row types, key
  types. Depends on Room runtime only.
- `:dolt-read` (`doltrooms-dolt-read`) — the declared reactive read
  machinery (`@DoltQuery` annotations, flow runtime, query builders).
- `:dolt-write` (`doltrooms-dolt-write`) — the git verbs: the
  `DoltDatabase` facade, options DSL, results, anchor bumps; owns the
  writer session.
- `:dolt-remotes` (`doltrooms-dolt-remotes`) — opt-in sync surface:
  remotes CRUD, fetch/push/pull, the clone bootstrap (D3/D9 posture).
- `:verifier` (`doltrooms-verifier`) — host-JVM KSP-classpath artifact:
  the `org.sqlite` shim pointing Room's own query verifier at DoltLite.
- `:processor` (`doltrooms-processor`) — host-JVM KSP processor:
  `@DoltQuery` codegen, the anchor lint, the DDL-verify harness.

No runtime `dolt-*` module depends on `:driver` at compile time — the
engine contract is the `dolt_*` SQL surface itself; a BOM aligns
versions. The template's `CustomFibi` placeholder deletion (2026-07-17)
and the samples-as-separate-builds rule are unchanged.

### D6 — Documentation structure: sacred README, this file, research skills

Repository knowledge is layered the same way as its sibling project
trinisphere: `README.md` is human-curated fact (agents never edit it);
`ARCHITECTURE.md` holds settled decisions; `docs/FEASIBILITY.md` is
founding research — context, not decisions; `.agents/skills/` holds
progressive-disclosure reference skills for the libraries this project
touches, maintained under the `skill-maintenance` workflow. `AGENTS.md`
binds these together and is the entry point for any agent.

### D7 — New classes and features are built test-first (red/green/refactor)

Any class or feature written from scratch is developed test-first on the
red/green/refactor cycle: write a failing test for the next increment,
watch it fail, write the minimum code to pass, then refactor while the
tests stay green
(https://martinfowler.com/bliki/TestDrivenDevelopment.html). This binds
the first implementation iteration onward — there is no code to test in
the research iteration (§4). It governs the code this project authors
(driver-interface implementations, per-platform bindings, helpers); D4's
reused Room suites remain a separate acceptance/differential gate on top,
not red-first tests. New tests go in `commonTest` so one red test drives
every target (§3.3). The D5 template placeholder code is exempt — it is
slated for deletion, not retrofitted with tests. The mechanics — the
cycle, the three laws, the test list, differential green against
`BundledSQLiteDriver` — live in the `red-green-testing` skill.

### D8 — Android ships our own NDK-compiled `.so`, not the doltlite-android AAR

The Android artifact packages `libdoltroomsjni.so` per device ABI
(arm64-v8a, x86_64) in the AAR's jniLibs, cross-compiled by this build
from the same pinned DoltLite amalgamation and compile-flag set as
every other platform. The JNA-based `com.dolthub:doltlite-android` AAR
is not used: it would introduce a second, independently versioned copy
of `libdoltlite` (breaking the one-pin rule in AGENTS.md), JNA's
per-call overhead on the `step()` hot path, and a loader model that
bypasses the shared `DoltLiteNative` JNI binding. Android host tests
run the same suites on the host JVM against the desktop `.so`; device
ABIs are exercised by the (deferred) device test run.

### D9 — Every platform builds `libdoltlite` from the one pinned amalgamation

The engine under every platform binding is compiled by this build from
the same release amalgamation, at the single version pinned in the
version catalog, with the settled compile-flag set: the desktop JVM
`.so`, the Android per-ABI `.so` (D8), and the Kotlin/Native klibs
(cinterop binds `doltlite.h` headers-only; linuxX64 embeds a static
archive of the amalgamation into the klib, and iOS archives are built
the same way on a macOS host — see `docs/deferred-verification.md`).
Upstream prebuilt platform artifacts are not consumed — the JNA-based
Android AAR (D8), the doltlite-swift XCFramework (which lagged the pin,
0.11.17 vs 0.11.33, when this was settled), and the per-OS prebuilt
lib zips all version independently of each other and would break the
one-pin rule (AGENTS.md).

### D10 — Typed dolt_* helpers ride Room's raw-connection API; room3-runtime is a commonMain `api` dependency

The version-control helper surface is the `dev.seri.doltrooms.dolt`
package: `DoltDatabase` (commit/branch/checkout/merge/log/diff/status,
plus the Step 8 remote surface addRemote/remotes/removeRemote/push/
pull/fetch) with typed results (`DoltCommit`, `DoltBranch`,
`DoltDiffRow`, `DoltRemote`, …), issuing plain `SELECT dolt_*(...)`
SQL through `RoomDatabase.useWriterConnection` — D1's
no-new-engine-API rule is upheld. One deliberate exception to the
ride-Room rule: `DoltDatabase.clone` is a companion function on a raw
`SQLiteDriver` connection, because the engine only clones into a
fresh database and a `RoomDatabase` is never fresh (Room's schema DDL
dirties it at open) — clone bootstraps the file, then Room opens it. Because `RoomDatabase` appears in that public surface,
`room3-runtime` is an `api` dependency of `commonMain` (the revisit
Step 4's test-only placement anticipated); the Room compiler/KSP still
serves the test suites only, so no Room-generated code ships. Two
engine facts (probed at the 0.11.33 pin) shape the helpers: dolt_*
calls are never wrapped in explicit transactions (`dolt_commit`
self-commits and ends any open transaction), and every helper runs on
the pool's single writer connection because DoltLite branch state is
per-connection session state — Room reader connections do not follow a
checkout.

### D11 — The public API surface is mechanically enforced

The library is a published artifact, so its `public` surface is a
contract, not a default. Two mechanisms enforced it under Gradle:
Kotlin's Explicit API mode (`explicitApi()`) — the compiler rejecting
implicit visibility and inferred types on public declarations — and
KGP's built-in ABI validation comparing the public binary API against
a committed golden dump (per-module `<module>/api/`), regenerated only
deliberately so accidental breaking changes fail the build. The rules
of this decision are unchanged; the *mechanisms* are suspended by the
D12 migration until the toolchain supports them (the `api/` dumps and
detekt configs stay committed; enforcement is by review meanwhile).
Rationale, citations, and the wider audit baseline live in the
`kotlin-audit-baseline` skill.

### D12 — The build system is the Kotlin Toolchain (human-opened, 2026-08-04)

The Gradle build is replaced by JetBrains' Kotlin Toolchain (the
`./kotlin` wrapper pins the distribution): a root `project.yaml`, one
`module.yaml` per module, and the `build-plugins/doltlite` build
plugin, which owns every custom native step (pinned amalgamation
download, host JNI library as generated jvm resources, static engine
archives — including the macOS→linux-x64 Konan cross-compile — and the
generated cinterop `.def`). `processor`/`verifier` keep their Maven
layout via `layout: maven-like`; the KMP modules use the toolchain
layout (`src`, `src@<platform>`, `test`). The `kotlin-toolchain` skill
is the operating reference, including the known toolchain gaps and the
workarounds in force.

Consequences, settled with the migration: D11's *mechanisms* are
suspended (no `explicitApi()`, ABI-gate, or detekt integration yet —
the golden dumps and detekt configs stay committed, and D11's rules
still bind by review) until the toolchain supports them; D8's AAR
jniLibs packaging, android test runs, the device-test runners, and
`samples/codelab` (which consumed the library via Gradle
`includeBuild`) are parked, tracked in `docs/deferred-verification.md`;
publishing stays on the D5 coordinates and single-macOS-host rule,
parked until the toolchain ships kmp/lib publishing.

## 3. Codemap

### 3.1 Repository layout

| Path | What lives there |
|---|---|
| `README.md` | Human-curated statement of the project. Never agent-edited. |
| `ARCHITECTURE.md` | This file — settled decisions D1–D12. |
| `AGENTS.md` | Governing docs, working rules, contributing guidelines, skills index. |
| `docs/FEASIBILITY.md` | Founding research: why DoltLite-as-driver, why not Dolt server. |
| `docs/USAGE.md` | Consumer guide: dependency + `setDriver` setup, per-platform engine delivery, the dolt_* helper tour, remotes/sync, the divergence table. |
| `docs/deferred-verification.md` | Checklist of implemented-but-unverified work plus verified records that still bear on future work: the iOS record, XCFramework packaging and Maven Central publishing (need a Mac), the remotesrv fixture off linux-x64. Fully-verified entries with no future bearing are pruned. |
| `.agents/skills/` | Reference skills (level 1/2/3 progressive disclosure). |
| `driver/`, `dolt-core/`, `dolt-read/`, `dolt-write/`, `dolt-remotes/`, `verifier/`, `processor/` | The module set (D5, amended 2026-07-24) — one concern each, described under D5; design in `docs/design/module-architecture.md`. |
| `samples/codelab/` | Fruitties sample: Google's kmp-migrate-room codelab in its post-migration state, ported to Room 3 + `DoltLiteDriver` for Android and iOS. A standalone composite build over the root (D5 amendment); its own README documents lineage and every delta from upstream. |
| `kotlin`, `kotlin.bat`, `project.yaml`, `<module>/module.yaml`, `build-plugins/doltlite/` | Build wiring — the Kotlin Toolchain (D12, §3.2). |

### 3.2 Build wiring (Kotlin Toolchain, D12)

- `kotlin` / `kotlin.bat` — the vendored wrapper; pins the toolchain
  distribution version and its SHA-256 (the only place the build-tool
  version lives).
- `project.yaml` — the module list (the D5-amended set plus
  `build-plugins/doltlite`) and the plugin registration.
  `samples/codelab` is not a module (parked, D12).
- `<module>/module.yaml` — product type/platforms, dependencies
  (module deps mirror the D5 DAG; `exported` is Gradle's `api`),
  settings, and per-fragment test config. Versions live here (Room 3
  `3.0.0` under `androidx.room3`, androidx.sqlite `2.7.0`, coroutines,
  Android minSdk `24` / compileSdk `36`) — except the DoltLite pin.
- `build-plugins/doltlite/` — the build plugin owning every custom
  native step (D8/D9): pinned amalgamation download (the DoltLite
  version + SHA-256 pins live at the top of its `src/tasks.kt`), host
  JNI library contributed as generated jvm resources, per-target
  static engine archives (incl. the macOS→linux-x64 Konan
  cross-compile), the generated cinterop `.def` from
  `driver/cinterop/doltlite.def.in`, and the `fetchRemotesrv` custom
  command (linux-x64 test fixture).
- The shipped artifacts depend on androidx.sqlite and, per module DAG,
  `room3-runtime` (D10 — an `exported` dependency of `dolt-core`); the
  Room *compiler*/KSP serves the test suites only — no Room-generated
  code ships (D1: Room is consumed unmodified, never forked).
- Publishing: parked pending toolchain kmp/lib publishing (D12);
  coordinates and the single-macOS-host rule are unchanged from D5,
  and `publish.yml` fails loudly until restored.

### 3.3 Targets and source sets (per KMP module; written for `:library`, now chiefly `:driver`)

Terms per the official project-structure docs
(<https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html>):
a target "describes a compilation target … the format of the produced
binaries, available language constructions, and allowed
dependencies"; a source set is "a set of source files with its own
targets, dependencies, and compiler options … the main way to share
code in multiplatform projects".

Targets are declared in each KMP module's `module.yaml`
(`product.platforms`, D12): `jvm`, `android`, `iosArm64`,
`iosSimulatorArm64`, and `linuxX64`. This matrix already covers the
first three rungs of the D4 ladder; it grows (e.g. macOS) only when an
iteration needs it.

`driver/` holds the driver (relocated from `library/` by the D5
amendment; `dolt-write/` holds the D10 helper package) in the
toolchain layout (D12): `src/` declares the public
`DoltLiteDriver`/`DoltLiteConnection`/`DoltLiteStatement` expect
classes (D1's three interfaces; `dolt-write/src/` the `DoltDatabase`
helper, D10); `src@jvmAndroid/` (a declared alias) carries the shared
JNI binding (`DoltLiteNative` plus the C++ glue under `native/jni/`)
with `src@jvm`/`src@android` library loaders beneath it; `src@native/`
carries the Kotlin/Native actuals over the cinterop bindings defined
by the `driver/cinterop/doltlite.def.in` template (headers-only
bindings, engine archives per D9, rendered by the doltlite plugin).
The fragment shape mirrors the old source sets: common `test/` runs on
every target with per-platform `test@jvm`, `test@ios`,
`test@linuxX64` beneath it (android test runs parked, D12).

That shape carries the three mechanics the driver relies on:

- **Common-to-platform visibility is one-way**: "the code in
  `jvmMain` can use code from `commonMain`. However, the opposite
  isn't true" (same page). The common API surface of the driver lives
  in `commonMain`; platform bindings live below it.
- **expect/actual**: `commonMain` declares an `expect`, each platform
  source set provides the `actual` — the seam the driver uses for
  per-platform `libdoltlite` bindings.
- **Intermediate source sets**: `iosMain` is not a platform source
  set — "there is no single `ios` target"; it is the
  default-hierarchy intermediate set compiling to both `iosArm64` and
  `iosSimulatorArm64`, whose platform sets "are usually empty, as
  Kotlin code for iOS devices and simulators is normally the same"
  (same page). One `iosMain` source file serves both iOS targets.
- **Test naming**: `Main` vs `Test` suffixes are the predefined
  convention; `androidHostTest` (not `androidTest`) is the AGP KMP
  plugin's naming — the legacy `src/main`/`src/test` layout is
  unsupported under that plugin (see the `kmp-native-interop` skill).

All library code lives in the `dev.seri.doltrooms` namespace (Kotlin
packages `dev.seri.doltrooms.*`).

## 4. Current iteration

**Maintenance.** The implementation iteration (opened by human
decision 2026-07-17, completed 2026-07-18 at Step 11) built the full
library: the DoltLite-backed `SQLiteDriver` across the platform
ladder (D4; the web rung was dropped at Step 9), the typed `dolt_*`
versioning helpers (D10), `doltlite-remotesrv` sync (D3), CI, and
publishing preparation. The plan is complete and its plan file has
been retired (deleted 2026-07-21, frozen snapshot included); new
work opens only by human decision as a new iteration with its own
plan.
Until then the scope gate is: fix bugs, keep docs/skills truthful,
and burn down `docs/deferred-verification.md` when the needed
hardware (macOS host, Android device, GitHub push) becomes available
— do not scaffold new features. Test-first (D7) continues to bind
any net-new production code.

One such iteration has since run: **Step 12 (human-opened
2026-07-21)** added the `samples/codelab` Fruitties app (D5
amendment) and, running for the first time on a macOS host, closed
the deferred iOS verification — per-slice engine archives, iosTest
concretes, and a green `iosSimulatorArm64Test`. The scope gate above
is back in force.

**Step 13 (human-opened 2026-07-22)** enabled Explicit API mode
(D11) — all main compilations passed with zero violations, so the
hand-maintained `public` discipline was already conformant.

**Step 14 (human-opened 2026-07-22)** added detekt (2.0.0-alpha.3 —
the 1.x line cannot read Kotlin 2.3 metadata — with the opt-in
libraries ruleset; required a Gradle wrapper bump 9.1.0 → 9.3.1).
`check` now gates on the five code-bearing main source sets;
each module's `config/detekt/detekt.yml` overlays the defaults and records
each deliberate deviation's rationale. Findings resolved by
justified suppression only — no code reshaping.

**Step 15 (human-opened 2026-07-22)** enabled KGP's built-in ABI
validation (D11 amendment) and committed the golden dump: `check`
gates on `checkLegacyAbi`. First generated on an x86-64 Linux
build server (predating the PR #6 cross toolchain), where the first
gated `check` ran green — jvmTest 118/0, linuxX64Test 52/0, detekt
gates, `checkLegacyAbi`; after rebasing onto PR #6, a macOS
regeneration reproduced the dump byte-identically, so either dev
host maintains it.

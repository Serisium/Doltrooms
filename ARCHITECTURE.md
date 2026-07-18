# doltlite-room-bridge — Architecture

**Status:** Target architecture as of 2026-07-18. Implementation phase
(§4); driver work is sequenced by `PLAN.md`.

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
may only sync behind a trusted proxy.

### D4 — Platform ladder: JVM first, then Android, then iOS; web is speculative

Driver work lands in this order: JVM desktop (JNI shim — the
proof-of-concept and test vehicle), Android, iOS (Kotlin/Native
cinterop), then web. A JS/WASM driver is acknowledged as the hardest
target; by human decision of 2026-07-17 it is scheduled last
(`PLAN.md` Step 9) as a best-effort, explicitly droppable rung —
nothing may be scaffolded for it before that step opens. Validation at
every rung is running existing Room test suites against the new
driver.

### D5 — The repo stays a single-module KMP library

The project keeps the Kotlin `multiplatform-library-template` shape:
one `:library` module holding the driver, Gradle version catalogs, no
sample apps or extra modules until an iteration explicitly needs them.
The template's `CustomFibi` placeholder code was deleted when the
implementation iteration opened (2026-07-17).

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

## 3. Codemap

### 3.1 Repository layout

| Path | What lives there |
|---|---|
| `README.md` | Human-curated statement of the project. Never agent-edited. |
| `ARCHITECTURE.md` | This file — settled decisions D1–D9. |
| `AGENTS.md` | Governing docs, working rules, contributing guidelines, skills index. |
| `PLAN.md` | The living implementation plan: session protocol, current state, step backlog, step log. The unit of work is one step per agent session (§4). |
| `docs/FEASIBILITY.md` | Founding research: why DoltLite-as-driver, why not Dolt server. |
| `docs/deferred-verification.md` | Checklist of implemented-but-unverifiable-on-Linux work: iOS compile/link/test (needs a Mac), Android on-device tests. |
| `.agents/skills/` | Reference skills (level 1/2/3 progressive disclosure). |
| `library/` | The one KMP library module (D5) — driver sources under `library/src/` (§3.3). |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradle.properties` | Build wiring from the template (§3.2). |

### 3.2 Gradle wiring

The repo keeps the `multiplatform-library-template` build shape:

- `settings.gradle.kts` — repositories (`google()`, `mavenCentral()`,
  `gradlePluginPortal()`) for both plugin and dependency resolution;
  `rootProject.name = "doltrooms"`; `include(":library")` — the single
  module of D5.
- `gradle/libs.versions.toml` — the version catalog, the only place
  versions live: Kotlin `2.3.10`, AGP `9.0.1`, Android minSdk `24` /
  compileSdk `36`, vanniktech maven-publish `0.36.0`, and the pinned
  implementation versions: DoltLite `0.11.33` (one version across all
  platform artifacts), Room 3 `3.0.0` (`androidx.room3`),
  androidx.sqlite `2.7.0`, KSP `2.3.10`, Android NDK
  `28.2.13676358` (cross-compiles the D8 device ABIs). Catalog
  aliases exist ahead
  of use; build scripts wire them in only when the owning PLAN.md step
  opens.
- Root `build.gradle.kts` — declares the build's plugins `apply false`
  (Kotlin Multiplatform, `com.android.kotlin.multiplatform.library`,
  vanniktech maven-publish, KSP, `androidx.room3`);
  `library/build.gradle.kts` applies them. Room and KSP serve the
  test suites only — the shipped artifact depends on androidx.sqlite,
  never on Room (D1).
- `gradle.properties` — configuration cache and build cache on;
  `kotlin.mpp.enableCInteropCommonization=true` commonizes the shared
  `doltlite` cinterop bindings across the native targets, so the one
  `nativeMain` driver implementation compiles against them (D9).
- Publishing: the vanniktech plugin targets Maven Central with
  signing. Coordinates are decided: group `dev.seri.doltrooms`,
  artifact `doltrooms` (Android namespace `dev.seri.doltrooms`).
  The POM's license/developer/scm entries remain template
  placeholders until the publishing step of `PLAN.md`.

### 3.3 The `:library` module — targets and source sets

Terms per the official project-structure docs
(<https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html>):
a target "describes a compilation target … the format of the produced
binaries, available language constructions, and allowed
dependencies"; a source set is "a set of source files with its own
targets, dependencies, and compiler options … the main way to share
code in multiplatform projects".

Targets declared in `library/build.gradle.kts`: `jvm()`,
`androidLibrary {}` (the AGP KMP library plugin — configured inside
`kotlin {}`, single-variant, host/device tests opted in via
`withHostTestBuilder {}` / `withDeviceTestBuilder {}`, plus
`withJava()`; see
<https://developer.android.com/kotlin/multiplatform/plugin>),
`iosArm64()`, `iosSimulatorArm64()`, and `linuxX64()`. This matrix
already covers the first three rungs of the D4 ladder; it grows (e.g.
macOS) only when an iteration needs it.

`library/src/` holds the driver, populated step by step by `PLAN.md`:
`commonMain` declares the public `DoltLiteDriver`/`DoltLiteConnection`/
`DoltLiteStatement` expect classes (D1's three interfaces);
`jvmAndroidMain` carries the shared JNI binding (`DoltLiteNative` plus
the C++ glue) with `jvmMain`/`androidMain` library loaders beneath it;
`nativeMain` carries the Kotlin/Native actuals over the cinterop
bindings defined by `library/src/nativeInterop/cinterop/doltlite.def`
(headers-only bindings, engine archives per D9). The tree
keeps the template's source-set shape: `commonMain` with per-platform
`*Main` sets below it, `commonTest` running on every target with
per-platform `*Test` sets (`jvmTest`, `iosTest`, `linuxX64Test`,
`androidHostTest`).

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

**Implementation.** Promoted from research by human decision on
2026-07-17: build the full library — the DoltLite-backed
`SQLiteDriver` across the platform ladder (D4, web last), the typed
`dolt_*` versioning helpers, `doltlite-remotesrv` sync, CI, and
publishing preparation. The work is sequenced by `PLAN.md`: one step
per agent session, test-first per D7, each session ending with a
state summary in `PLAN.md` and a commit. Scope is bounded by the
current unchecked `PLAN.md` step — do not scaffold ahead of it.

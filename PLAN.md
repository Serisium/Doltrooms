# doltrooms — Living Implementation Plan

<!-- Agents: this file + repo state is your ONLY carried context between
     sessions. Read it fully, then follow the Session Protocol, BEFORE
     doing anything else. -->

## Session Protocol

Each step below is executed in one fresh agent session:

1. Read this file top to bottom. Then read `ARCHITECTURE.md` (decisions
   D1–D7) and `AGENTS.md` (working rules, skills index).
2. Read the skill references listed on your step's card — never answer
   from memory about Room 3, DoltLite, androidx.sqlite, or KSP. Any step
   that writes net-new production code loads
   `.agents/skills/red-green-testing/SKILL.md` first (D7).
3. Execute ONLY the next unchecked step. Test-first per the
   red-green-testing skill: keep a test list; per increment write one
   failing test and watch it fail (a compile error counts as red); write
   the minimum code to green; refactor only while green; commit on green.
4. Before ending: run the step's Verify commands; REPLACE the
   "Current State" section below; APPEND a Step Log entry (what was done,
   what diverged from the card, failures, follow-ups); check the step
   off; make a final small, auditable commit. Never end a session red.
5. Never edit `README.md` (human-only). Record settled decisions in
   `ARCHITECTURE.md` (load the `architecture-docs` skill first) and keep
   its codemap + cross-references current after any `.md` edit.
6. If reality diverges from a future step's card, update that card in
   place and say so in the Step Log.

## Current State

- **Last completed step:** Step 0 (repo hygiene + plan bootstrap).
- **Branch:** `claude/doltrooms-kmp-plan-932ed0` (fast-forwarded onto
  `origin/main` `1f09e16`, which added the red-green-testing skill + D7).
- **Repo shape:** root project renamed `doltrooms`; single `:library`
  module (D5); group `dev.seri.doltrooms`, version `0.1.0-SNAPSHOT`,
  Android namespace `dev.seri.doltrooms`, publish coordinates
  `dev.seri.doltrooms:doltrooms`. All template Fibi code deleted —
  `library/src/` is empty; targets `jvm()`, `androidLibrary{}`,
  `iosArm64`, `iosSimulatorArm64`, `linuxX64` compile with zero sources.
- **Pinned versions** (`gradle/libs.versions.toml`): doltlite `0.11.33`,
  room3 `3.0.0`, androidxSqlite `2.7.0`, ksp `2.3.10`, kotlin `2.3.10`,
  agp `9.0.1`. Catalog aliases exist for androidx-sqlite(-bundled),
  room3-runtime/compiler/testing, and the ksp + room3 plugins; nothing
  is wired into build scripts yet.
- **Build/test commands that pass:** `./gradlew build`
  (see Step 0 log for environment notes: JDK 21 via dnf, Android SDK
  platform 36 at `/opt/android-sdk`, `local.properties` gitignored).
- **Known divergences vs BundledSQLiteDriver:** none recorded yet
  (table starts at Step 3).
- **Open problems handed to next session:** none.

## Step Backlog

### [x] Step 0 — Repo hygiene + living-plan bootstrap
Done — see Step Log.

### [ ] Step 1 — Amalgamation acquisition + JVM native plumbing + minimal CI
- **Goal:** Pinned DoltLite 0.11.33 amalgamation downloads reproducibly
  and compiles (with minimal JNI glue) into a loadable
  `libdoltroomsjni.so`; a jvm test proves the lib loads.
- **Skills:** `red-green-testing`, `doltlite/references/artifacts-and-build.md`,
  `kmp-native-interop/references/jni-and-packaging.md` + `precedents.md`,
  `androidx-sqlite/references/bundled-driver-internals.md` (compile flags).
- **Key tasks:** Gradle task downloading the 0.11.33 amalgamation from
  GitHub releases (SHA-256 checked, cached under `build/`); compile
  amalgamation + `doltrooms_jni.cpp` (exposing `nativeLibVersion()`)
  into `libdoltroomsjni.so`, mirroring BundledSQLiteDriver's compile
  flags (record the flag set — it is a settled decision); `jvmMain`
  resource-extraction `NativeLibraryLoader` (xerial/powersync pattern);
  `.github/workflows/ci.yml` running `./gradlew build :library:jvmTest`.
- **Red-green:** red = jvm `NativeLoadTest` asserting
  `DoltLiteNative.libVersion()` returns the doltlite version string,
  failing on a `TODO()` stub; green = glue + loader implemented.
- **Verify:** `./gradlew :library:jvmTest`.
- **Record:** exact release URL + checksum, compiler flags, loader
  resource-path convention.
- **Risks:** release asset naming may differ from skill notes — verify
  against the live release page, then fold back into the doltlite skill.

### [ ] Step 2 — Driver skeleton: commonMain API + JVM open/exec/close
- **Goal:** `DoltLiteDriver : SQLiteDriver` in `commonMain`
  (expect/actual per the bundled template); JVM actual can
  `open(":memory:")`, `execSQL` DDL, `close`.
- **Skills:** `red-green-testing`,
  `androidx-sqlite/references/driver-interfaces.md` (verbatim contract)
  + `bundled-driver-internals.md`, `sqlite-c-api` SKILL.
- **Key tasks:** add `libs.androidx.sqlite` to commonMain; re-skin the
  bundled driver's class structure (`DoltLiteDriver`/`DoltLiteConnection`/
  `DoltLiteStatement`, `hasConnectionPool = false`) in
  `dev.seri.doltrooms.driver`; JNI for `sqlite3_open_v2` (+ enable
  extended result codes), minimal prepare/step path, `sqlite3_close`;
  errors via `androidx.sqlite.throwSQLiteException`.
- **Red-green:** per feature — open-close, execSQL DDL, open-failure
  error-code mapping.
- **Verify:** `./gradlew :library:jvmTest`.
- **Record:** class map (file → responsibility), what was copied from
  the bundled internals vs deliberately diverged.
- **Risks:** contract deviations here poison everything downstream —
  keep the re-skin literal.

### [ ] Step 3 — Full statement API + differential conformance harness (JVM)
- **Goal:** Complete `SQLiteStatement` + `inTransaction`; identical
  commonTest conformance suite passes against DoltLiteDriver AND
  BundledSQLiteDriver on JVM.
- **Skills:** `red-green-testing`, `androidx-sqlite` references,
  `sqlite-c-api/references/lifecycle-and-statements.md` +
  `errors-and-threading.md` + `transactions-and-concurrency.md`,
  `room3/references/testing.md` (differential pattern).
- **Key tasks:** full bind*/get* surface (1-based binds, 0-based gets),
  `step`/`reset`/`clearBindings`, idempotent closes, `SQLITE_RANGE`/
  `SQLITE_MISUSE` pre-checks, `SQLITE_TRANSIENT` text/blob binds,
  `inTransaction` via `sqlite3_get_autocommit == 0`; commonTest
  `AbstractDriverConformanceTest` (abstract `fun driver()`; kotlin.test
  + runTest) covering types/NULL/blobs/step/reset/rebind/errors/
  transactions/multi-connection WAL shape (4r+1w)/not-thread-affine
  use; jvmTest concrete classes for both drivers; `KnownDivergenceTest`
  probing `last_insert_rowid` on non-INTEGER-PK (WITHOUT ROWID) tables.
- **Red-green:** the harness IS the red generator — implement until the
  only remaining diffs are documented divergences.
- **Verify:** `./gradlew :library:jvmTest` (both suites green).
- **Record:** the divergence table → promote into Current State
  permanently; conformance coverage checklist.
- **Risks:** genuine DoltLite deviations are documented contract, not
  bugs to fight.

### [ ] Step 4 — Room3 integration (JVM), differential
- **Goal:** A real Room3 database (entities, suspend DAOs, @Transaction,
  flow queries) runs green via
  `Room.inMemoryDatabaseBuilder<Db>().setDriver(DoltLiteDriver())` on
  JVM, and identically on BundledSQLiteDriver.
- **Skills:** `red-green-testing`, `room3` SKILL +
  `references/kmp-setup-and-builder.md`, `testing.md`,
  `raw-connections-and-transactions.md`, `query-verification.md`.
- **Key tasks:** wire ksp + room3 plugins and per-target
  `add("kspJvm", libs.room3.compiler)` etc. for TEST compilation;
  `room3 { schemaDirectory("$projectDir/schemas") }`; decide
  room3-runtime placement (commonMain vs commonTest — record why);
  commonTest Room suite (`AbstractRoomConformanceTest`, differential);
  exercise Room's own pooling (WAL 4r+1w), busy behavior.
- **Red-green:** red = Room suite failing on driver gaps (busy handling,
  changes counting, statement reuse after reset); green = driver fixes.
- **Verify:** `./gradlew :library:jvmTest`.
- **Risks:** KSP 2.3.10 / Kotlin 2.3.10 alignment (note: KSP versioning
  is now plain `2.3.x`, no longer `<kotlin>-<ksp>` — the room3 skill's
  note is stale; fold the correction into the skill per
  skill-maintenance).

### [ ] Step 5 — Android target
- **Goal:** Android artifact ships our own NDK-compiled `.so` per ABI
  (arm64-v8a, x86_64; NOT the JNA-based `com.dolthub:doltlite-android`
  AAR); full conformance + Room suites green as `androidHostTest` on
  Linux.
- **Skills:** `red-green-testing`,
  `kmp-native-interop/references/jni-and-packaging.md` +
  `targets-and-publishing.md`.
- **Key tasks:** NDK cross-compile amalgamation+glue; wire jniLibs into
  AGP KMP `androidLibrary{}`; `androidMain` loader
  (`System.loadLibrary`); host tests reuse the desktop `.so`
  (`:library:testAndroidHostTest`); `withDeviceTestBuilder` configured,
  device runs deferred; record the skip-doltlite-AAR decision in
  ARCHITECTURE.md.
- **Red-green:** red = androidHostTest concrete conformance classes
  failing to load the native lib; green = loader + packaging.
- **Verify:** `./gradlew :library:testAndroidHostTest` and android
  device-test assembly compiles; CI updated.
- **Risks:** NDK availability in-env (install, or defer device-ABI
  compile to CI with the decision recorded).

### [ ] Step 6 — Native targets: linuxX64 (full) + iOS (compile-only)
- **Goal:** `nativeMain` actuals via cinterop over `doltlite.h`;
  conformance + Room suites green on linuxX64; iOS klibs compile.
- **Skills:** `red-green-testing`,
  `kmp-native-interop/references/cinterop.md` + `precedents.md`,
  `androidx-sqlite/references/bundled-driver-internals.md` (native side).
- **Key tasks:** `nativeInterop/cinterop/doltlite.def` (headers +
  static `libdoltlite.a` built per KonanTarget by extending Step-1
  plumbing); `nativeMain` actuals; `linuxX64Test` concrete classes for
  both suites (BundledSQLiteDriver is KMP — differential works);
  iOS compiles on Linux, link+test deferred to a Mac (write the
  deferred checklist).
- **Red-green:** red = linuxX64 conformance classes on stubs; green =
  cinterop implementation.
- **Verify:** `./gradlew :library:linuxX64Test
  :library:compileKotlinIosArm64 :library:compileKotlinIosSimulatorArm64`.
- **Risks:** doltlite-swift XCFramework lags (0.11.17) → compile the
  amalgamation for iOS to keep the 0.11.33 pin (record as decision).

### [ ] Step 7 — dolt_* versioning API
- **Goal:** Typed commonMain helpers — `DoltDatabase`
  commit/branch/checkout/merge/log/diff/status with result types
  (`DoltCommit`, `DoltBranch`, `DoltDiffRow`, …) — over
  `useWriterConnection` + `Transactor` (the sanctioned path; D1: no new
  bridge-level engine APIs, this is plain SQL underneath).
- **Skills:** `red-green-testing`,
  `doltlite/references/version-control-sql.md`,
  `room3/references/raw-connections-and-transactions.md` +
  `query-verification.md`.
- **Key tasks:** `dev.seri.doltrooms.dolt` package; helpers issue
  `SELECT dolt_commit('-m', ?)` etc. inside `immediateTransaction`;
  document `@SkipQueryVerification` for users embedding dolt_* in DAOs
  (Room verifies @Query against stock sqlite-jdbc); harness gains a
  capability flag so dolt tests run DoltLite-only, plus one guard test
  that Bundled errors cleanly.
- **Red-green:** per helper (e.g. red: commit-then-log returns the
  commit; green: implement helper). Run on jvm + linuxX64 +
  androidHostTest.
- **Verify:** `./gradlew :library:jvmTest :library:linuxX64Test
  :library:testAndroidHostTest`.
- **Risks:** dolt_* names/result columns are version-sensitive — verify
  against the doltlite skill (pinned 0.11.33), never memory.

### [ ] Step 8 — Sync via doltlite-remotesrv
- **Goal:** `push/pull/clone/fetch` helpers; `file://` remote
  round-trip proven by test; https path best-effort.
- **Skills:** `red-green-testing`,
  `doltlite/references/remotes-and-sync.md`.
- **Key tasks:** extend the dolt package with remote helpers; file://
  round-trip test (two DB instances: push from A, pull into B, assert
  rows + dolt_log equality); https test against a spawned
  `doltlite-remotesrv` fixture if the binary is obtainable in-env, else
  `@Ignore`-gated with the reason recorded (file://-only scope-cut is
  pre-authorized). D3 gate: pinned 0.11.33 ≥ 0.11.28 (TLS + bearer-JWT).
- **Red-green:** red = file-remote round-trip on stubs; green implement.
- **Verify:** `./gradlew :library:jvmTest :library:linuxX64Test`.
- **Risks:** remotesrv binary distribution unclear; auth setup unknown.

### [ ] Step 9 — Web target (wasmJs, best-effort, droppable)
- **Goal:** `wasmJs` target compiles; async driver over
  `@dolthub/doltlite-wasm` implementing androidx.sqlite's suspend web
  interface variants; single connection; smoke tests where runnable.
- **Skills:** `red-green-testing`,
  `androidx-sqlite/references/driver-interfaces.md` (webMain variants),
  `doltlite/references/artifacts-and-build.md` (wasm artifact),
  `kmp-native-interop/references/targets-and-publishing.md` (web notes).
- **Key tasks:** add `wasmJs` target + npm dep; `wasmJsMain` async
  driver; verify Room3-on-wasm status in-session before promising Room
  support there; record npm version skew vs the 0.11.33 pin.
- **Red-green:** red = wasm smoke test (open, exec, query one row) on
  stub; green implement.
- **Verify:** minimum `./gradlew :library:compileKotlinWasmJs`; tests
  where the toolchain allows. This step is explicitly droppable without
  blocking Steps 10–11 — if dropped, record why and remove the target.
- **Risks:** highest-risk step: androidx web driver surface vs Room3
  expectations; doltlite-wasm version skew. (D4 amended to schedule web
  last — see ARCHITECTURE.md.)

### [ ] Step 10 — CI hardening + verification matrix
- **Goal:** One workflow covering everything Linux-verifiable: build,
  jvmTest, linuxX64Test, testAndroidHostTest, iOS klib compile, wasm
  compile; caching for the amalgamation download + Konan;
  `docs/deferred-verification.md` checklist for Mac (iOS link+test,
  XCFramework packaging) and Android device tests.
- **Skills:** none new; reread `kmp-native-interop` publishing notes.
- **Red-green:** N/A (infra). Verify: green pipeline on a no-op change;
  freeze the canonical command list into Current State.

### [ ] Step 11 — Publishing + docs + closeout
- **Goal:** vanniktech publishing for `dev.seri.doltrooms:doltrooms`
  (POM real, signing via env), `publishToMavenLocal` smoke; Dokka;
  `docs/USAGE.md` (per-platform setup, setDriver usage, dolt_* tour,
  divergence table). README untouched — suggest README updates to the
  human instead.
- **Skills:** `kmp-native-interop/references/targets-and-publishing.md`.
- **Key tasks:** finalize POM (license, developer, scm); note that
  full Maven Central publishing requires a Mac host (cinterop
  artifacts) — record in deferred-verification; final ARCHITECTURE.md
  codemap; flip this file to maintenance mode.
- **Verify:** `./gradlew publishToMavenLocal` + full test matrix.

## Step Log

### Step 0 — Repo hygiene + living-plan bootstrap (2026-07-17, branch `claude/doltrooms-kmp-plan-932ed0`)

- Fast-forwarded the branch onto `origin/main` `1f09e16` (PR #1:
  red-green-testing skill + ARCHITECTURE.md D7).
- Renamed `rootProject.name` to `doltrooms`; set group
  `dev.seri.doltrooms`, version `0.1.0-SNAPSHOT`, Android namespace
  `dev.seri.doltrooms`, publish coordinates
  `dev.seri.doltrooms:doltrooms`; replaced the template POM
  name/description/url (license/developer/scm placeholders remain for
  Step 11).
- Deleted all template Fibi sources/tests (`library/src/**`, 10 files).
- Pinned versions in the catalog and added (unwired) aliases:
  doltlite 0.11.33, room3 3.0.0 (verified on Maven Central),
  androidxSqlite 2.7.0 (verified), ksp 2.3.10 (verified on Maven
  Central; KSP now versions as plain `2.3.x`, released 2026-07-09).
- Environment notes (not committed): JDK 21 installed via
  `dnf install java-21-openjdk-devel`; Android SDK cmdline-tools +
  `platforms;android-36` + `build-tools;36.0.0` at `/opt/android-sdk`;
  `local.properties` (gitignored) points there. Fresh sessions must
  re-check these exist before building.
- ARCHITECTURE.md updated: Status date, D4 amended (web scheduled last,
  best-effort — human decision of 2026-07-17), codemap refreshed
  (renamed root project, PLAN.md row, empty `library/src`, catalog
  pins, coordinates), §4 flipped from Research to Implementation
  (governed by this file). AGENTS.md updated to route sessions here and
  to reflect the open iteration scope.
- Fixed stale cross-references in the `red-green-testing` skill
  (research-iteration wording, `FibiTest.kt`/`CustomFibi` mentions)
  under the skill-maintenance workflow; frontmatter untouched, so
  `skills-ref validate` was hand-checked (tool not installed in-env).
- Follow-ups: (1) room3 skill's KSP version-scheme note is stale (says
  `<kotlin>-<ksp>`; reality is plain `2.3.x`) — fold in via the
  skill-maintenance workflow in a later session; (2) POM
  license/developer/scm still placeholder until Step 11.
- Verification: `./gradlew build` PASSED (exit 0) — jvm, android,
  iosArm64/iosSimulatorArm64 klibs, linuxX64 all compile with the
  emptied source tree; Konan toolchain provisioned on first run.

# doltrooms — Living Implementation Plan

<!-- Agents: this file + repo state is your ONLY carried context between
     sessions. Read it fully, then follow the Session Protocol, BEFORE
     doing anything else. -->

## Session Protocol

Each step below is executed in one fresh agent session:

1. Read this file top to bottom. Then read `ARCHITECTURE.md` (decisions
   D1–D8) and `AGENTS.md` (working rules, skills index).
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

- **Last completed step:** Step 5 (Android target: NDK-built jniLibs +
  androidHostTest).
- **Branch:** `claude/plan-step-5-a532ac` (carries the Step 0–4
  commits).
- **Repo shape:** single `:library` module (D5); group
  `dev.seri.doltrooms`, version `0.1.0-SNAPSHOT`, Android namespace
  `dev.seri.doltrooms`, publish coordinates
  `dev.seri.doltrooms:doltrooms`. Targets: `jvm()`, `androidLibrary{}`,
  `iosArm64`, `iosSimulatorArm64`, `linuxX64`.
- **Source-set map:** `commonMain` (androidx.sqlite as `api` dep;
  public expect classes `DoltLiteDriver`/`DoltLiteConnection`/
  `DoltLiteStatement` in `dev.seri.doltrooms.driver`, declaring the
  full nonWeb member surface — every target is nonWeb, so commonMain
  resolves androidx.sqlite's nonWeb interfaces and commonTest can call
  `open`/`prepare`/`step` directly, which Step 3's conformance suite
  relies on) → `jvmAndroidMain` (actuals + `DoltLiteNative` JNI object
  + C++ glue `jni/doltrooms_jni.cpp`, `JNI_OnLoad`+`RegisterNatives`)
  → `jvmMain` (`NativeLibraryLoader`: JAR-resource extraction from
  `natives/<os>-<arch>/`, override `-Ddev.seri.doltrooms.lib.path`)
  and `androidMain` (`System.loadLibrary("doltroomsjni")` — resolved
  on device from the AAR's jniLibs, which Step 5 packages per ABI
  (arm64-v8a, x86_64) from our own NDK build (D8), and on host tests
  from `java.library.path` pointing at the desktop `.so`).
  `nativeMain` holds TODO-stub actuals until Step 6.
- **Test map (78 jvm + 33 androidHost tests, all green):** `commonTest
  …/driver/AbstractDriverConformanceTest.kt` — the differential
  conformance suite (27 cases; its header carries the coverage
  checklist: type roundtrips incl. empty blob + NULL + unbound params,
  all five column types, column metadata before stepping, multi-row
  step, reset/rebind/clearBindings, RANGE/no-row/closed-statement
  error contract, invalid-SQL prepare, idempotent closes, BEGIN/
  COMMIT/ROLLBACK + inTransaction, user_version round-trip across
  reopen, 4-reader+1-writer file visibility, not-thread-affine
  sequential use via runTest+Dispatchers.Default). `commonTest
  …/room/AbstractRoomConformanceTest.kt` — the Step 4 differential
  Room suite (6 cases; header checklist: suspend @Insert/@Query
  roundtrip, parameterized list @Query + @Update/@Delete change
  counts, @Transaction commit/rollback, Flow @Query re-emission via
  InvalidationTracker, WAL-file-DB 4r+1w concurrent DAO readers
  during writes, two-instance busy contract SQLITE_BUSY(5) +
  post-release recovery) over `…/room/RoomConformanceDb.kt` (Person
  entity INTEGER-autoGenerate PK — deliberately rowid-backed given
  the WITHOUT ROWID divergence; DAO incl. default-body @Transaction
  `insertPair`; `@ConstructedBy` expect object, KSP-generated
  actuals; schema exported to `library/schemas/`). jvmTest concretes:
  `DoltLiteDriverConformanceTest`/`BundledSQLiteDriverConformanceTest`
  and `DoltLiteRoomConformanceTest`/`BundledRoomConformanceTest`
  (Bundled legs are the oracle — a test failing there is a bad test,
  not a divergence), `KnownDivergenceTest` (5 probes asserting BOTH
  engines' observed behavior so upstream changes surface as
  failures), `DoltLiteDriverTest` (6), `NativeLoadTest` (1).
  androidHostTest concretes (Step 5): DoltLite legs of both suites
  run on the host JVM against the desktop `.so`; NO Bundled oracle
  there — the android variant of sqlite-bundled ships only device-ABI
  jniLibs, and forcing `sqlite-bundled-jvm` would NoClassDefFound on
  the android `SQLiteException` typealias. Both abstract suites carry
  `exceptionMessagesObservable` (default true; android host concretes
  false): AGP's mockable android.jar stubs the
  `android.database.SQLException` constructor, so exception MESSAGES
  are unobservable in android host tests — types/behavior still
  assert; message content is covered by jvm/native legs and
  on-device. linuxX64Test concrete classes are Step 6 work.
- **Step 4 Room-level findings (no new divergences):** DoltLite runs
  Room's full JVM stack identically to Bundled — InvalidationTracker's
  temp-table + trigger machinery works, Flow re-emission works, WAL
  pool serves 4 concurrent readers during writes, and the
  two-Room-instance busy probe (2026-07-18) showed the identical
  contract on both engines: Room sets no busy_timeout, a second
  instance's write during a held immediate transaction throws
  androidx `SQLiteException` "Error code: 5, … database is locked"
  promptly and succeeds after release. `useWriterConnection`/
  `immediateTransaction`/`usePrepared` also exercised through the
  driver (busy test) — the Step 7 dolt_* path is plumbed.
- **Step 5 Android findings:** the driver + Room suites pass on the
  android target's host tests with ZERO driver changes — the only
  platform delta is test-infrastructure (mockable android.jar erases
  exception messages; see the test map). Device-ABI validation is
  packaging-level only so far (ELF/alignment/NEEDED checks on the
  AAR + device-test APK); actual on-device runs stay deferred
  (`withDeviceTestBuilder` configured, `connectedAndroidDeviceTest`
  exists but needs a device/emulator).
- **Driver class map (Steps 2–3):**
  - `commonMain …/driver/DoltLiteDriver.kt` — public expect API; the
    connection now also declares `inTransaction`.
  - `jvmAndroidMain …/driver/DoltLiteDriver.jvmAndroid.kt` — actuals:
    `open` (`sqlite3_open_v2` READWRITE|CREATE + extended result
    codes, rc-checked, handle closed on eager failure), `prepare`
    (`sqlite3_prepare16_v2`), connection `inTransaction`
    (`sqlite3_get_autocommit == 0`, throws when closed), idempotent
    connection `close` via `@Volatile isClosed`; statement: full
    bind*/get* surface (1-based binds rc-checked with errmsg,
    0-based gets), `step` (ROW→true/DONE→false/else throw), `reset`,
    `clearBindings`, column metadata (`getColumnCount/Name/Type`,
    `isNull` via type==NULL), statement-side `@Volatile isClosed`
    guard (all members throw MISUSE "statement is closed" after
    close — load-bearing: sqlite3_* on a finalized stmt SIGABRTed
    the test JVM), pre-check trio on value getters (closed → no-row
    via `sqlite3_stmt_busy` → column-range vs `column_count`),
    `getColumnName` null → `OutOfMemoryError`.
  - `jvmAndroidMain …/driver/DoltLiteNative.kt` + `jni/doltrooms_jni.cpp`
    — registered natives: lib version, open/close, prepare, step,
    finalize, reset, clearBindings, stmtBusy, errmsg16,
    getAutocommit, bind long/double/text16/blob/null (TRANSIENT;
    empty blob via `bind_zeroblob` since a null data pointer would
    bind SQL NULL), column long/double/text16/blob/type/count/name.
    JNI returns raw rc values; **Kotlin** throws via
    `androidx.sqlite.throwSQLiteException` (deliberate divergence from
    bundled's C++-side throwing — keeps the Android
    `SQLiteException = android.database.SQLException` typealias
    working for free).
  - `nativeMain …/driver/DoltLiteDriver.native.kt` — TODO stubs
    (replaced by cinterop actuals in Step 6).
- **Copied from bundled vs diverged:** copied — open flags, extended
  result codes at open, UTF-16 prepare/column-text, error-message
  format, idempotent closes, the no-row/column-range pre-check trio,
  TRANSIENT text/blob binds. Diverged — error throwing lives in
  Kotlin (above); `sqlite3_db_config(ENABLE_LOAD_EXTENSION)` dropped
  per the adaptation checklist.
- **Known divergences vs stock/Bundled (permanent table; each row is
  asserted by a test):**
  1. **Deferred open (probed 2026-07-17, C probes vs system sqlite
     3.50.2):** DoltLite 0.11.33 `sqlite3_open_v2(READWRITE|CREATE)`
     returns OK for an unopenable path (missing parent directory);
     `SQLITE_CANTOPEN` (14) surfaces at the first `sqlite3_step`
     (prepare still returns OK). Stock fails eagerly at open. Eager
     open failures do still exist (READONLY on missing file → 14 at
     open; invalid flags → 21), so `open()` keeps its rc check.
     Documented as contract by
     `DoltLiteDriverTest.openFailureIsDeferredToFirstStatement`.
  2. **Default journal mode (probed 2026-07-17 via driver):** DoltLite
     reports `PRAGMA journal_mode` = `wal` out of the box on file
     databases; stock defaults to `delete`. `PRAGMA journal_mode=WAL`
     returns `wal` on both. `KnownDivergenceTest`.
  3. **WITHOUT ROWID storage for non-INTEGER-PK tables (probed
     2026-07-17):** after inserting into a TEXT-PK table,
     `last_insert_rowid()` is 0 on DoltLite (1 on stock) and
     `SELECT rowid` errors "no such column" (returns the rowid on
     stock). INTEGER-PK tables agree on both engines — which is what
     Room's generated inserts use. `KnownDivergenceTest`.
  4. Compile flags: `SQLITE_OMIT_SHARED_CACHE` and
     `SQLITE_DEFAULT_WAL_SYNCHRONOUS=1` dropped (Step 1). The feared
     WAL-synchronous divergence did NOT materialize: after
     `PRAGMA journal_mode=WAL` both engines report `synchronous` = 2
     (FULL) on the plain open→WAL path (Step 1 follow-up closed;
     asserted by `KnownDivergenceTest`). Shared-cache code is merely
     compiled in, unused.
  Conforming probes worth remembering (same runs): `user_version`
  round-trips across close/reopen (Room's schema store works),
  `changes()` counts, 4r+1w multi-connection visibility,
  uncommitted-write isolation, cross-thread sequential connection
  use.
- **Build facts:** `applyDefaultHierarchyTemplate()` in
  `library/build.gradle.kts` must stay — the custom `jvmAndroidMain`
  dependsOn edges disable Kotlin's default hierarchy and orphan
  `nativeMain` without it. `-Xexpect-actual-classes` acknowledges
  expect/actual-class Beta (androidx does the same). Test deps:
  commonTest adds `kotlinx-coroutines-test` 1.10.2 and
  `room3-runtime` (Room is a TEST-ONLY consumer: the shipped library
  is a driver Room consumes, D1 — room3-runtime deliberately NOT in
  commonMain; revisit only when Step 7's typed helpers need Room
  types in public API); jvmTest adds `androidx.sqlite:sqlite-bundled`
  (the oracle). Step 4 wired the `ksp` + `androidx.room3` Gradle
  plugins (root: apply false; library: applied), `room3 {
  schemaDirectory("$projectDir/schemas") }` (required once the plugin
  is applied), and `room3-compiler` on every TARGET'S TEST ksp
  configuration only: `kspJvmTest`, `kspAndroidHostTest`,
  `kspAndroidDeviceTest`, `kspLinuxX64Test`, `kspIosArm64Test`,
  `kspIosSimulatorArm64Test` (main compilations get no KSP — no Room
  code ships). Only ONE `failOnNoDiscoveredTests = false` carve-out
  remains (`linuxX64Test`) — REMOVE it at Step 6. Step 5 wired
  `testAndroidHostTest` to depend on `compileDoltliteJni` and prepend
  its output dir to the forked JVM's `java.library.path` (AGP already
  sets that property, ending with the unused
  `src/androidHostTest/jniLibs` convention dir — prepend, don't
  replace).
- **Native build plumbing** (`library/build.gradle.kts`): tasks
  `downloadDoltliteAmalgamation` (release zip
  `doltlite-amalgamation-0.11.33.zip`, SHA-256
  `12e47892ead2b8016234eed3377e9e659bd61a9a6e932f9364d7326dbc095d13`,
  cached under `library/build/doltlite/`) → `unpackDoltliteAmalgamation`
  (flattens to `build/doltlite/src/{doltlite.c,doltlite.h,doltliteext.h}`)
  → `compileDoltliteJni` (gcc/g++ host build →
  `build/nativeLibs/jvm/natives/linux-x64/libdoltroomsjni.so`, wired
  into jvmMain resources) and `compileDoltliteAndroidJni` (Step 5:
  NDK clang per ABI — `aarch64-linux-android24-clang` /
  `x86_64-linux-android24-clang(++)` from the pinned NDK, same flag
  set, `-static-libstdc++`, no `-lpthread` on bionic →
  `build/nativeLibs/android/<abi>/libdoltroomsjni.so`, wired into the
  AAR via `androidComponents.onVariants { variant.sources.jniLibs
  ?.addGeneratedSourceDirectory(...) }` — the KMP android components
  extension extends plain `AndroidComponentsExtension`, so it's
  `onVariants`, not `onVariant`; verified in the artifact: correct
  ELF machines, 16 KB LOAD alignment, NEEDED = libc/libm/libdl only;
  NDK dir resolved from `local.properties` sdk.dir or `ANDROID_HOME`
  + the catalog's ndk pin). **Settled compile flags:** androidx
  sqlite-bundled's set MINUS `SQLITE_OMIT_SHARED_CACHE` and
  `SQLITE_DEFAULT_WAL_SYNCHRONOUS=1` (both break DoltLite's fork — see
  Step 1 log), PLUS `-DDOLTLITE_VERSION="0.11.33"`.
- **Pinned versions** (`gradle/libs.versions.toml`): doltlite `0.11.33`,
  room3 `3.0.0`, androidxSqlite `2.7.0`, ksp `2.3.10`, kotlin `2.3.10`,
  agp `9.0.1`, kotlinxCoroutines `1.10.2`, android-ndk
  `28.2.13676358` (Step 5; r28+ defaults to 16 KB page alignment). androidx-sqlite (commonMain
  api), sqlite-bundled (jvmTest) and coroutines-test (commonTest) are
  wired; Step 4 wired room3-runtime (commonTest), room3-compiler
  (test ksp configs), and the ksp + room3 plugin aliases;
  `room3-testing` stays unwired (nothing uses MigrationTestHelper
  yet).
- **Engine version facts:** the 0.11.33 amalgamation carries
  `SQLITE_VERSION "3.54.0"` (what `sqlite3_libversion()` returns); the
  DoltLite release version is only observable via SQL
  `SELECT dolt_version()` and only because we compile with
  `-DDOLTLITE_VERSION="0.11.33"` (amalgamation fallback define is
  `"doltlite-amalgamation"`).
- **Build/test commands that pass:** `./gradlew build` (all five
  targets, now incl. NDK jniLibs in the AAR), `./gradlew
  :library:jvmTest` (78 tests), `./gradlew
  :library:testAndroidHostTest` (33 tests), `./gradlew
  :library:bundleAndroidMainAar :library:assembleAndroidDeviceTest`
  (AAR + device-test APK both carry `lib/<abi>/libdoltroomsjni.so`).
  CI: `.github/workflows/ci.yml` (ubuntu, JDK 21, setup-android,
  `sdkmanager "ndk;28.2.13676358"`, runs build + jvmTest +
  testAndroidHostTest) — not yet observed running on GitHub; note the
  linuxX64 test binary needs `libcrypt.so.1` at runtime (ubuntu ships
  it; Fedora needed libxcrypt-compat).
- **Environment (re-check before building):** JDK 21 (dnf), gcc/g++ 15
  (dnf), Android SDK platform 36 + build-tools 36 at `/opt/android-sdk`
  (`local.properties`, gitignored — recreate it in fresh worktrees),
  NDK 28.2.13676358 at `/opt/android-sdk/ndk/` (installed Step 5 via
  sdkmanager), sqlite-devel 3.50.2 (dnf; for differential C probes
  against stock), libxcrypt-compat (dnf; linuxX64 test.kexe links
  libcrypt.so.1).
- **Open problems handed to next session:** none.

## Step Backlog

### [x] Step 0 — Repo hygiene + living-plan bootstrap
Done — see Step Log.

### [x] Step 1 — Amalgamation acquisition + JVM native plumbing + minimal CI
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

### [x] Step 2 — Driver skeleton: commonMain API + JVM open/exec/close
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

### [x] Step 3 — Full statement API + differential conformance harness (JVM)
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

### [x] Step 4 — Room3 integration (JVM), differential
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

### [x] Step 5 — Android target
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
  (`:library:testAndroidHostTest`); androidHostTest concrete classes
  for BOTH commonTest suites (Step 3 driver conformance + Step 4 Room
  — their kspAndroidHostTest/kspAndroidDeviceTest compiler wiring
  already landed in Step 4), then REMOVE the
  `testAndroidHostTest` zero-test carve-out
  (`failOnNoDiscoveredTests`) from `library/build.gradle.kts`;
  `withDeviceTestBuilder` configured, device runs deferred; record
  the skip-doltlite-AAR decision in ARCHITECTURE.md.
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
  both suites (BundledSQLiteDriver is KMP — differential works), then
  REMOVE the `linuxX64Test` zero-test carve-out
  (`failOnNoDiscoveredTests`) from `library/build.gradle.kts`;
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

### Step 1 — Amalgamation + JVM native plumbing + minimal CI (2026-07-17)

- **Red first:** `NativeLoadTest.nativeLibraryLoadsAndReportsEngineVersion`
  written before any production code; watched it fail
  (`Unresolved reference 'DoltLiteNative'` — compile error counts as
  red). Green after loader + glue + build plumbing; refactors on green
  (checksum-failure cleanup, `const_cast` to silence the
  JNINativeMethod warning). 1 test, 0 failures.
- **Card divergence:** the card said the test should assert the
  "doltlite version string" — wrong at the C level. The amalgamation
  has no DoltLite-version C symbol; `sqlite3_libversion()` returns
  `"3.54.0"` (`SQLITE_VERSION` in doltlite.h). The test asserts that
  instead. The DoltLite release version is exposed only through SQL
  `dolt_version()`, which reads the `DOLTLITE_VERSION` compile-time
  define — fallback `"doltlite-amalgamation"` — so we pass
  `-DDOLTLITE_VERSION="0.11.33"`. A Step-2+ test should assert
  `SELECT dolt_version()` = `0.11.33` once prepare/step exists.
- **Release asset naming confirmed** (risk from the card, resolved):
  `doltlite-amalgamation-0.11.33.zip` containing
  `doltlite-amalgamation-0.11.33/{doltlite.c,doltlite.h,doltliteext.h}`.
  Also present per release: `doltlite-lib-<os>-<arch>` prebuilt zips,
  `doltlite-tools-<os>-<arch>` zips (likely carries
  `doltlite-remotesrv` — relevant to Step 8), xcframework, wasm zip,
  autoconf tarball, deb packages.
- **Fork-divergence discovery (settled decision, recorded in the build
  script):** DoltLite 0.11.33 does NOT compile with two androidx
  sqlite-bundled flags: `SQLITE_OMIT_SHARED_CACHE` (fork's btree shim
  redefines/calls the Btree lock functions that flag empties out) and
  `SQLITE_DEFAULT_WAL_SYNCHRONOUS=1` (activates `setDefaultSyncFlag`,
  which dereferences the fork-opaque `Btree` struct). Both dropped;
  all other androidx flags compile clean. Consequences: shared-cache
  code is compiled in (unused by the driver) and WAL default
  synchronous stays at SQLite's default (`FULL`) instead of androidx's
  `NORMAL` — candidate `PRAGMA synchronous` tuning later; probe at
  Step 3.
- **Environment delta:** installed `gcc`/`gcc-c++` 15 via dnf.
- CI workflow added (`.github/workflows/ci.yml`): ubuntu-latest,
  temurin JDK 21, `android-actions/setup-android@v3`,
  `gradle/actions/setup-gradle@v4`, runs
  `./gradlew build :library:jvmTest`. Unverified until pushed.
- **Follow-ups:** fold the release-asset naming facts into the
  doltlite skill (artifacts reference) per skill-maintenance — queued
  with the existing KSP-note follow-up from Step 0.

### Step 2 — Driver skeleton: commonMain API + JVM open/exec/close (2026-07-17, branch `claude/plan-step-2-2f8885`)

- **Red-green, five increments, one commit each:** (1) open/close
  `:memory:` — red was `Unresolved reference 'DoltLiteDriver'`;
  (2) `execSQL` DDL — red on prepare's `TODO`; (3) open-failure error
  mapping (see divergence below); (4) closed-connection guard — red on
  wrong error shape from a freed handle; (5) `SELECT dolt_version()`
  = `0.11.33` via minimal `getText` — closes the loop Step 1 queued.
  Final suite: 7 jvm tests, 0 failures; `./gradlew build` green on all
  five targets.
- **Fork-divergence discovery (now documented contract, in Current
  State's table):** increment 3's red predicted eager
  `SQLITE_CANTOPEN` from `open()`; the test instead PASSED open and
  failed differently. C-level differential probes (amalgamation vs
  system sqlite 3.50.2, scratch programs) settled it: DoltLite opens
  lazily — OK at `open_v2`, CANTOPEN(14) at first `step` (prepare
  OK). The test was rewritten to assert the real deferred behavior;
  `open()` keeps its bundled-style rc check because eager failures
  still exist (READONLY+missing → 14, invalid flags → 21).
- **A test that could not fail:** `connectionCloseIsIdempotent`
  passed before any guard existed by riding on double-`close_v2`
  UB. The deterministic red came from
  `prepareOnClosedConnectionThrows` (asserting the contract message);
  its `isClosed` guard is what makes the idempotency test meaningful.
  Lesson recorded per the red-green skill's "too easy to write a test
  that cannot fail".
- **Card divergences:** (a) errors are thrown from **Kotlin** via
  `androidx.sqlite.throwSQLiteException` over raw rc returns from
  JNI, not from C++ like bundled — simpler glue and the Android
  `SQLiteException` typealias works with no `FindClass` gymnastics;
  (b) `hasConnectionPool = false` comes from the interface default
  rather than an explicit override; (c) the card didn't mention the
  nativeMain TODO stubs or that the expect classes must re-declare
  the whole nonWeb member surface (commonMain resolves the nonWeb
  actual interfaces since all targets are nonWeb) — both were
  required to keep every target compiling; (d) a minimal `getText`
  landed ahead of Step 3's full surface to run the queued
  dolt_version assertion.
- **Build-script discoveries:** Step 1's custom `jvmAndroidMain`
  dependsOn edges had silently disabled Kotlin's default source-set
  hierarchy — `applyDefaultHierarchyTemplate()` restores
  `nativeMain`/`iosMain`; added `-Xexpect-actual-classes`.
  androidx.sqlite 2.7.0 resolves fine for every declared target from
  commonMain as an `api` dependency.
- **Environment delta:** installed `sqlite-devel` (stock 3.50.2) via
  dnf for the differential probes; recreated gitignored
  `local.properties` in the worktree.
- **Follow-ups:** fold the deferred-open probe results into the
  `sqlite-c-api` skill's fork-divergence watchlist (confirmed
  instance) and/or the `doltlite` skill's gotchas per
  skill-maintenance — queued with the Step 0 (KSP note) and Step 1
  (release-asset naming) items. Statement-side `isClosed` guard and
  pre-check trio are Step 3 work, already on its card.

### Step 3 — Full statement API + differential conformance harness (2026-07-17, branch `claude/step-3-continuation-5b6dba`)

- **Red-green, eight increments, one commit each:** (1) harness
  bootstrap — commonTest `AbstractDriverConformanceTest` (abstract
  `driver()`), jvmTest concretes for DoltLite + Bundled (the oracle),
  first red on `bindLong`'s TODO; (2) full type surface — text (UTF-16
  incl. surrogate pair), double, blob (incl. empty), null/isNull,
  unbound-param-is-NULL, getColumnType×5; (3) column metadata before
  stepping; (4) cursor control — multi-row step, reset retains
  bindings, rebind, clearBindings; (5) error contract — RANGE/no-row/
  closed-statement guards; (6) `inTransaction` (expect-surface
  addition) + BEGIN/COMMIT/ROLLBACK semantics; (7) 4r+1w
  multi-connection visibility + not-thread-affine sequential use
  (runTest + Dispatchers.Default; needed kotlinx-coroutines-test and
  an abstract `tempDbPath()`); (8) `user_version` conformance +
  `KnownDivergenceTest`. Final: 66 jvm tests green (27 conformance ×
  both drivers + 12 others), `./gradlew build` green on all targets.
- **The harness worked as designed:** every DoltLite red listed above
  appeared while the identical test passed on Bundled, so no test
  ambiguity ever needed debugging. Increments 7's probes passed
  without new driver code — recorded as acceptance-style differential
  checks rather than true reds (the red-green skill's "test that
  cannot fail" caveat acknowledged: their oracle leg is what
  validates them).
- **A red that was a SIGABRT:** `statementUseAfterCloseThrows` didn't
  fail with an assertion — it killed the test JVM (exit 134,
  "segfaults and heap corruption" per the finalize doc) and silently
  truncated the DoltLite suite run to 5 tests. The statement-side
  `@Volatile isClosed` guard is load-bearing, not cosmetic. Lesson:
  when a test-run count looks short, suspect a native crash, not
  Gradle filtering.
- **Fork-divergence discoveries (promoted to the permanent Current
  State table, each asserted by `KnownDivergenceTest`):** DoltLite
  file DBs report `journal_mode` = `wal` by default (stock:
  `delete`); WITHOUT ROWID semantics for non-INTEGER-PK tables
  (last_insert_rowid 0, `SELECT rowid` errors); the Step 1
  WAL-synchronous worry did NOT materialize (both engines report
  FULL after `journal_mode=WAL`) — Step 1 follow-up closed.
  Conforming: user_version across reopen, changes(), 4r+1w
  visibility, uncommitted-write isolation, cross-thread use.
  Discovery method: a throwaway `ScratchProbeTest` printed both
  engines' behavior, results were encoded as assertions on BOTH
  engines, probe deleted.
- **Card divergences:** (a) NOMEM → OutOfMemoryError is implemented
  (getColumnName) but untestable — no test forces allocation
  failure; (b) `SQLITE_TRANSIENT` empty-blob bind needed
  `bind_zeroblob` because a null data pointer binds SQL NULL — found
  by the oracle leg of `emptyBlobRoundTripsAsEmpty`; (c) the card's
  "multi-connection WAL shape" needed no WAL-specific driver work
  (DoltLite is effectively always-WAL); (d) added `user_version`
  round-trip beyond the card (Room's schema store — the watchlist
  said verify it).
- **Build/environment deltas:** commonTest gaining sources made
  Gradle 9 fail `testAndroidHostTest`/`linuxX64Test` for discovering
  zero tests — `failOnNoDiscoveredTests = false` carve-outs added,
  removal queued on the Step 5/6 cards (updated in place); installed
  `libxcrypt-compat` (linuxX64 test.kexe links `libcrypt.so.1`);
  catalog gained `kotlinxCoroutines` 1.10.2.
- **Follow-ups (queued for skill-maintenance, with Step 0–2 items):**
  fold into `doltlite` skill gotchas — default `journal_mode=wal`,
  the WITHOUT ROWID probe results, and the WAL-synchronous
  non-divergence; fold the stmt-busy/no-row and finalize-SIGABRT
  notes into `sqlite-c-api` watchlist as confirmed-preserved vs
  confirmed-diverged entries.

### Step 4 — Room3 integration (JVM), differential (2026-07-18, branch `claude/step-4-continuation-c1b6c8`)

- **Red-green, six increments, one commit each:** (1) bootstrap —
  commonTest `RoomConformanceDb` (Person entity, suspend DAO,
  `@ConstructedBy` expect object) + `AbstractRoomConformanceTest`
  with insert/query roundtrip, jvmTest concretes for both drivers;
  red was `Unresolved reference 'room3'`, green was the full build
  wiring (ksp + androidx.room3 plugins, room3-runtime in commonTest,
  room3-compiler on all six test ksp configs, schemaDirectory);
  (2) parameterized list @Query + @Update/@Delete change counts;
  (3) default-body @Transaction commit/rollback; (4) Flow @Query
  re-emits after write; (5) WAL file DB, 4 concurrent DAO readers
  during writer @Transactions; (6) two-instance busy contract.
  Final: 78 jvm tests green (27 driver-conformance ×2 + 6 Room ×2 +
  12 others); `./gradlew build` green on all five targets.
- **No new divergences at the Room level.** Every Room feature on the
  card ran identically on DoltLite and Bundled with zero driver
  changes — Step 3's statement surface was already sufficient.
  Notable conforming machinery: InvalidationTracker (temp table +
  per-table triggers + Flow re-emission), Room's 4r+1w WAL pool,
  changes-count reads backing @Update/@Delete return values.
- **Busy probe (throwaway `ScratchBusyProbeTest`, deleted per Step 3
  precedent):** with a second Room instance writing while the first
  held an immediate transaction, BOTH engines threw androidx
  `SQLiteException` "Error code: 5, … database is locked" promptly
  (Room sets no busy_timeout) and recovered after release — encoded
  as `secondInstanceWriteDuringHeldTransactionThrowsBusy`. The busy
  test also exercises `useWriterConnection`/`immediateTransaction`/
  `usePrepared` through our driver, pre-validating Step 7's path.
- **Settled decision (recorded in build-script comments + Current
  State): Room is test-only.** room3-runtime lives in commonTest, the
  compiler only on test ksp configurations — the shipped artifact is
  a driver Room consumes (D1), so the library must not drag Room into
  consumers' dependency graphs. Revisit only if Step 7's typed
  helpers need Room types in their public surface.
- **Card divergences:** (a) the card's `add("kspJvm", …)` sketch names
  MAIN configurations; the real test-compilation configs are
  `kspJvmTest`/`kspAndroidHostTest`/`kspAndroidDeviceTest`/
  `kspLinuxX64Test`/`kspIos*Test` (discovered via
  `:library:dependencies`); (b) the predicted driver gaps (busy
  handling, changes counting, statement reuse) produced no reds —
  increments 4–6 are acceptance-style differential checks whose
  oracle leg validates them (red-green skill caveat, as in Step 3);
  (c) `@SkipQueryVerification` was NOT exercised — no dolt_* SQL goes
  through @Query in this step; it stays on Step 7's card.
- **Skill maintenance:** folded the KSP version-scheme correction
  into `room3/references/kmp-setup-and-builder.md` (plain `2.3.x`
  scheme verified on Maven Central + in-repo build; kotlinlang
  quickstart still shows the old compound scheme) plus the
  test-configuration names above — closes the Step 0 follow-up.
  skills-ref still not installed in-env; frontmatter untouched,
  hand-checked.
- **Card updates in place:** Step 5's key tasks now say concrete
  classes for BOTH commonTest suites and note the already-landed
  kspAndroidHostTest/kspAndroidDeviceTest wiring.
- **Environment delta:** recreated gitignored `local.properties`
  (fresh worktree). No new packages needed.
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  otherwise unchanged.

### Step 5 — Android target (2026-07-18, branch `claude/plan-step-5-a532ac`)

- **Red-green, two increments, one commit each:** (1) androidHostTest
  concrete classes for BOTH commonTest suites — red was
  `UnsatisfiedLinkError: no doltroomsjni in java.library.path` (33/33
  failing, exactly the card's prediction); green wired
  `testAndroidHostTest` to depend on `compileDoltliteJni` and prepend
  its output to `java.library.path`, and removed the
  `testAndroidHostTest` zero-test carve-out. (2) NDK cross-compile —
  infra increment verified in the artifact rather than by unit test
  (Step 1 CI precedent): `compileDoltliteAndroidJni` builds
  arm64-v8a + x86_64 with the pinned NDK's API-24 clang, wired into
  the AAR via the variant API's generated jniLibs dir; checked ELF
  machines, 16 KB LOAD alignment (r28 default), NEEDED =
  libc/libm/libdl only, and `lib/<abi>/` present in both the AAR and
  the assembled device-test APK. Final: jvmTest 78 + androidHostTest
  33 green, `./gradlew build` green on all targets.
- **Discovery: the mockable android.jar erases exception messages.**
  After the loader green, 7 tests still failed — every
  message-content assertion. On android host tests
  `androidx.sqlite.SQLiteException` is the typealias to
  `android.database.SQLException`, and AGP's mockable android.jar
  stubs its constructor (type intact, message dropped). Both abstract
  suites gained `exceptionMessagesObservable` (android host concretes
  override to false): types/behavior assert everywhere; message
  content asserts on jvm (and native/device later). NOT a DoltLite
  divergence — it would hit the Bundled oracle identically.
- **No Bundled oracle leg on androidHostTest (recorded scope):** the
  android variant of sqlite-bundled ships only device-ABI jniLibs
  (nothing loadable on a host JVM), and forcing
  `sqlite-bundled-jvm` can't work either — its classes reference
  `androidx.sqlite.SQLiteException` as a CLASS, which doesn't exist
  in the android variant of androidx.sqlite (typealias). The
  differential oracle stays on jvmTest; androidHostTest runs the
  DoltLite legs as the D4 acceptance gate for the android rung.
- **Card divergences:** (a) the AGP KMP components extension has no
  `onVariant` — it extends plain `AndroidComponentsExtension`, so
  it's `onVariants { }`; (b) `java.util.Properties` must be imported
  in the build script (`java.` collides with the Kotlin-DSL `java`
  extension accessor); (c) the jni-and-packaging skill's three-ABI
  recipe (incl. armeabi-v7a) was narrowed to the card's two ABIs;
  (d) Android link line drops `-lpthread` (bionic has no separate
  libpthread) and adds `-static-libstdc++` (glue uses no STL — NEEDED
  stays libc/libm/libdl).
- **Decision recorded:** ARCHITECTURE.md D8 — ship our own
  NDK-compiled `.so` per ABI, not the JNA-based
  `com.dolthub:doltlite-android` AAR (one DoltLite pin, no JNA
  per-call overhead, one shared `DoltLiteNative` binding).
  Cross-reference sweep: D1–D7 ranges bumped to D1–D8 in
  ARCHITECTURE.md/AGENTS.md/PLAN.md + architecture-docs skill
  (frontmatter hand-checked; skills-ref still not in-env) +
  skill-maintenance reference; the kmp-native-interop
  jni-and-packaging note that suggested the JNA AAR as "the zero-NDK
  v1 path" now records D8's rejection instead.
- **CI updated (unverified until pushed):** installs
  `ndk;28.2.13676358` via sdkmanager and runs
  `./gradlew build :library:jvmTest :library:testAndroidHostTest`.
- **Environment delta:** recreated `local.properties`; installed NDK
  28.2.13676358 via sdkmanager into `/opt/android-sdk/ndk/`.
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  unchanged. Device/emulator runs remain deferred (Step 10's
  deferred-verification checklist).

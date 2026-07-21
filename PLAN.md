# doltrooms — Living Implementation Plan

<!-- Agents: this file + repo state is your ONLY carried context between
     sessions. Read it fully, then follow the Session Protocol, BEFORE
     doing anything else. -->

## MAINTENANCE MODE (since Step 11, 2026-07-18)

The step backlog is complete — there is no "next unchecked step". The
implementation iteration ended at Step 11; `ARCHITECTURE.md` §4 bounds
what a session may now do: bug fixes, keeping docs/skills truthful,
and burning down `docs/deferred-verification.md` as hardware becomes
available (macOS host, Android device, GitHub push). New feature work
opens only by human decision, as a new iteration appending new step
cards below — the Session Protocol then applies to them unchanged.
Maintenance sessions still read this file first, keep "Current State"
truthful, and append a dated Step Log entry for anything they change.

## Session Protocol

Each step below is executed in one fresh agent session:

1. Read this file top to bottom. Then read `ARCHITECTURE.md` (decisions
   D1–D10) and `AGENTS.md` (working rules, skills index).
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

- **Last completed step:** Step 12 — samples/codelab (Fruitties on
  Dolt) + first-macOS-host iOS verification, a one-step human-opened
  iteration (2026-07-21). The Fruitties app from Google's
  kmp-migrate-room codelab (post-migration `end` state) lives in
  `samples/codelab/` as a standalone composite build
  (`includeBuild("../..")`, explicit substitution of
  `dev.seri.doltrooms:doltrooms` → `:library`; D5 amendment), ported
  to Room 3 + `DoltLiteDriver` on Android and iOS — its README
  records the lineage and every delta (Room 2→3 incl. the
  `@Relation` plural-attribute rename, AGP 9 built-in Kotlin, Hilt
  2.60.1, SKIE dropped for a manual `FlowWatch` bridge, `iosX64`
  dropped). The session also extended `library/build.gradle.kts`
  with macOS-host-gated `CompileDoltliteAppleStaticTask` per-slice
  engine archives (embedded into the iOS cinterop klibs exactly like
  linuxX64) and `library/src/iosTest/` concretes, closing the
  deferred-verification iOS entry: `:library:iosSimulatorArm64Test`
  is GREEN — 52/52 on the iOS 18.3 simulator. Same-session
  addendum (user request "install debug android sample app"): the
  pinned NDK + cmdline-tools were installed on this Mac, the sample
  APK built (via the new darwin-x86_64 NDK host-tag fix), installed,
  and smoke-run on a physical motorola razr 2025 (arm64-v8a, DB
  writes observed), its `connectedDebugAndroidTest` is 10/10, and
  `:library:connectedAndroidDeviceTest` is 52/52 — after two
  first-run fixes: the device-test APK gained the missing
  `androidx.test:runner` dependency, and
  `library/src/androidDeviceTest/` concretes were created (there
  were none; the suite silently ran 0 tests). Still deferred: iOS
  app run in a booted simulator (host lacks the Xcode 26.5 iOS
  runtime), x86_64 Android ABI (no emulator).
- **Previous step (11) summary:** Publishing + docs + closeout.
  Infra/docs-only (D7 N/A).
  Publishing finalized: real POM (Apache-2.0 matching `LICENSE`,
  developer `Serisium`/Seri Greenwood, scm from the origin remote),
  Dokka 2.2.0 wired as the Maven Central `-javadoc` jar via the
  vanniktech `KotlinMultiplatform` configure
  (`JavadocJar.Dokka("dokkaGeneratePublicationHtml")` — the Dokka 2.x
  task name; sources jar on), signing/credentials via
  `ORG_GRADLE_PROJECT_*` env (sign tasks SKIP without a key, so
  `publishToMavenLocal` works unsigned). Smoke-verified on Linux:
  root umbrella + `-jvm` (natives inside) + `-android` (both ABI
  `.so`s) + `-linuxx64` (klib + cinterop klib) publications, real
  Dokka HTML in every javadoc jar. iOS publications and the actual
  Central release need a single macOS host —
  `docs/deferred-verification.md` has the dedicated entry.
  `docs/USAGE.md` written (setup, per-platform delivery, dolt_* tour,
  sync, divergence table). ARCHITECTURE.md §4 flipped to Maintenance;
  the Step 0–3 skill-maintenance follow-up queue was drained (last
  item: sqlite-c-api watchlist gained the confirmed-at-0.11.33
  section).
- **Branch:** `claude/kmp-room-codelab-samples-fcce62` (from `main`
  post-PR-#2).
- **Repo shape:** single `:library` module in the root build (D5);
  group `dev.seri.doltrooms`, version `0.1.0-SNAPSHOT`, Android
  namespace `dev.seri.doltrooms`, publish coordinates
  `dev.seri.doltrooms:doltrooms`. Targets: `jvm()`, `androidLibrary{}`,
  `iosArm64`, `iosSimulatorArm64`, `linuxX64`. Plus `samples/codelab/`,
  a separate included-build Gradle project (`:shared` KMP +
  `:androidApp` + `iosApp/` Xcode project) documented in its own
  README (D5 amendment).
- **Source-set map:** `commonMain` (androidx.sqlite AND room3-runtime
  as `api` deps — the latter since Step 7, D10; public expect classes
  `DoltLiteDriver`/`DoltLiteConnection`/
  `DoltLiteStatement` in `dev.seri.doltrooms.driver`, plus the Step 7
  `dev.seri.doltrooms.dolt` package — plain-Kotlin `DoltDatabase`
  helpers + `DoltCommit`/`DoltBranch`/`DoltStatusEntry`/`DoltDiffRow`
  result types over `RoomDatabase.useWriterConnection` — declaring the
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
  `nativeMain` (Step 6) holds the real Kotlin/Native actuals over the
  commonized cinterop bindings from
  `src/nativeInterop/cinterop/doltlite.def` (headers-only bindings —
  see the class map; engine archives per D9).
- **Test map (118 jvm + 52 androidHost + 52 linuxX64 + 52
  iosSimulatorArm64 + 52 androidDevice tests — the iOS leg via the
  Step 12 `library/src/iosTest/` concretes, green on the iOS 18.3
  simulator, macOS hosts only; the device leg via the Step 12
  `library/src/androidDeviceTest/` concretes, green on a physical
  arm64-v8a phone, needs a connected device — all
  green; linuxX64 lost its 33 Bundled-oracle legs to the post-Step-11
  symbol-collision fix — see the maintenance log entry):** `commonTest
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
  actuals; schema exported to `library/schemas/`; Step 7 added
  `@SkipQueryVerification @Query("SELECT dolt_version()")
  doltVersion()` — the living validation that dolt_* SQL compiles in
  DAOs when verification is skipped). `commonTest
  …/dolt/AbstractDoltDatabaseTest.kt` — the Step 7+8 dolt-helper suite
  (19 cases; NOT differential — no dolt oracle exists; its
  `engineSupportsDolt` capability flag keeps the versioning tests
  DoltLite-only, and the one flag=false concrete runs only the guard
  test that a dolt-less engine throws clean SQLiteExceptions; header
  checklist: commit/log round-trip incl. the repo-init commit, status
  staging lifecycle + nothing-to-commit error, branch/checkout/
  branches/deleteBranch round-trip + no-such-branch error,
  readers-don't-follow-checkout contract, ff merge returns merged
  head, clean 3-way merge creates merge commit, conflicted merge
  throws + rolls back, typed diff rows incl. WORKING pseudo-ref,
  @SkipQueryVerification dolt_version through the DAO; Step 8:
  remote add/list/remove + duplicate/unknown-remote errors, push+clone
  file:// replication, pull round-trip, fetch-materializes-origin/main
  contract, non-ff push reject + --force, conflicted pull rollback,
  clone-requires-fresh-db + missing-remote error). jvmTest also
  carries `…/dolt/RemoteServerSyncTest.kt` (Step 8, jvm-only fixture,
  2 cases): spawns the real `doltlite-remotesrv` from the pinned
  release's tools zip for an http push/clone/pull round-trip through
  the driver, and pins that amalgamation-built engines reject
  `https://` ("URL must start with file:// or http://"); skips with a
  printed reason on non-linux-x64 hosts, FAILS if the Gradle fixture
  wiring goes missing on linux-x64. jvmTest concretes:
  `DoltLiteDriverConformanceTest`/`BundledSQLiteDriverConformanceTest`
  and `DoltLiteRoomConformanceTest`/`BundledRoomConformanceTest`
  (Bundled legs are the oracle — a test failing there is a bad test,
  not a divergence), `DoltLiteDoltDatabaseTest`/
  `BundledDoltDatabaseTest` (the latter flag=false, guard only),
  `KnownDivergenceTest` (5 probes asserting BOTH
  engines' observed behavior so upstream changes surface as
  failures), `DoltLiteDriverTest` (6), `NativeLoadTest` (1).
  androidHostTest concretes (Step 5, dolt suite added Step 7):
  DoltLite legs of the three suites
  run on the host JVM against the desktop `.so`; NO Bundled oracle
  there — the android variant of sqlite-bundled ships only device-ABI
  jniLibs, and forcing `sqlite-bundled-jvm` would NoClassDefFound on
  the android `SQLiteException` typealias. Both abstract suites carry
  `exceptionMessagesObservable` (default true; android host concretes
  false): AGP's mockable android.jar stubs the
  `android.database.SQLException` constructor, so exception MESSAGES
  are unobservable in android host tests — types/behavior still
  assert; message content is covered by jvm/native legs and
  on-device. linuxX64Test concretes (Step 6, narrowed post-Step-11):
  the DoltLite legs of both conformance suites plus the dolt suite
  (Step 7) and `NativeTempDbPath.kt` (`kotlin.random`-suffixed /tmp
  paths standing in for `File.createTempFile`); exception messages
  observable, no overrides. NO Bundled oracle on native: both engines
  would be statically linked into the one test binary, and both
  archives export the same unprefixed `sqlite3_*` symbols — the
  linker silently resolves BOTH drivers to whichever archive comes
  first (maintenance log entry, 2026-07-18). The oracle lives on
  jvmTest only; the dolt suite pins engine identity on native.
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
- **Step 6 native findings:** the conformance + Room suites are green
  on linuxX64 with ZERO driver-semantics changes — the nativeMain
  actuals are a literal port of the jvmAndroid ones onto cinterop.
  (Step 6 believed it ran a native differential matrix with BOTH
  engines; the post-Step-11 CI run exposed that as illusory — with
  two engines statically linked into one binary, one archive's
  `sqlite3_*` symbols capture both drivers, so the "oracle" leg was
  DoltLite locally and the "DoltLite" leg was stock sqlite on CI.
  The Bundled legs were removed; see the maintenance log entry.)
  Exception messages ARE observable on
  native (no android.jar in the way). iOS diverged from the card: KGP
  disables Apple-target compilation on a Linux host once the target
  has a cinterop ("cross compilation … has been disabled because it
  contains cinterops"), so the iOS tasks are SKIPPED (builds stay
  green) and compile+link+test ALL defer to a Mac —
  `docs/deferred-verification.md` (created this step) has the
  ordered checklist, incl. building per-slice iOS archives of the
  amalgamation to keep the 0.11.33 pin (now decision D9; the
  upstream XCFramework lags at 0.11.17).
- **Step 7 dolt_* findings (all probed at 0.11.33, 2026-07-18; the
  full fact sheet lives in the doltlite skill's version-control-sql
  "Probed facts" section):** branch state is PER-CONNECTION session
  state — a checkout switches only the issuing connection, new
  connections open on `main`, and nothing persists across reopen (no
  default-branch knob; dolt_config knows only user.name/user.email).
  Hence `DoltDatabase` runs every helper on Room's single writer
  connection, and Room reader connections do NOT follow a checkout —
  DAO reads keep seeing `main` (pinned by
  `readerConnectionsDoNotFollowCheckout`). `dolt_commit` inside an
  open `BEGIN` commits and ENDS that transaction (the wrapper's
  `COMMIT` then fails), so helpers use plain `usePrepared`, never
  `withTransaction` — the card's "inside immediateTransaction" was
  wrong. Conflicted merges: autocommit → throw + rollback; explicit
  txn → throw but leave the txn open with dolt_conflicts populated
  for resolution (recipe in DoltDatabase's KDoc). `dolt_diff()`
  takes no args in DoltLite — row diffs go through the per-table
  `dolt_diff_<table>` TVF (quotable name, bindable ref args, WORKING
  pseudo-ref). dolt works fully on `:memory:`. AUTOINCREMENT's
  `sqlite_sequence` shows up in dolt_status/diffs alongside user
  tables.
- **Step 8 sync findings (all probed at 0.11.33, 2026-07-18; full
  fact sheet in the doltlite skill's remotes-and-sync "Probed facts"
  section):** the DoltLite amalgamation EXCLUDES the TLS/credential
  stack (doltlite.c: "excluded only from the single-file
  amalgamation") — so every engine this repo builds (D9) supports
  `file://` and plain `http://` remotes only; `https://` fails at
  first use ("URL must start with file:// or http://") and
  bearer-JWT auth is unavailable. The release's PREBUILT tools
  binaries DO speak TLS 1.3 (shell-verified against a --cert/--key
  remotesrv with `DOLTLITE_CA_FILE` trusting a self-signed cert), so
  the gap is packaging, not protocol; D3 amended accordingly —
  network sync is trusted-networks-only. `dolt_clone` requires a
  FRESH database (Room dirties one at open), hence
  `DoltDatabase.clone` is a companion bootstrap on a raw driver
  connection (D10 amended). `<remote>/<branch>` refs resolve only
  after the first `dolt_fetch` — not even right after clone. Pull =
  fetch+merge with merge's exact conflict semantics. AUTOINCREMENT
  is merge-hostile across replicas: concurrent inserts mint the same
  rowid → PK collision → pull conflict (distinct explicit ids merge
  clean; `sqlite_sequence` itself never conflicts) — syncing apps
  need collision-free keys. First push creates a `file://` remote;
  non-ff push rejects, `'--force'` overwrites.
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
  - `nativeMain …/driver/DoltLiteDriver.native.kt` (Step 6) — the
    cinterop actuals, a literal port of the jvmAndroid semantics:
    same open/prepare/step/bind/get surface, guards, and error paths,
    calling `dev.seri.doltrooms.doltlite.c.*` bindings. Native-only
    mechanics: opaque handles import from `cnames.structs`
    (`sqlite3`, `sqlite3_stmt` — no package typealias when typedef
    name == struct tag); `SQLITE_TRANSIENT` recreated as
    `(-1L).toCPointer()` (SQLiter pattern; a macro cast cinterop
    can't expose); UTF-16 prepare/bind via `String.utf16` with
    `length * 2` byte counts; blob reads via `readBytes`, text reads
    length-based from `column_bytes16`; empty-blob `bind_zeroblob`;
    errmsg via UTF-8 `sqlite3_errmsg().toKString()`.
  - `src/nativeInterop/cinterop/doltlite.def` — headers-only bindings
    (`headers = doltlite.h`, `headerFilter`, `noStringConversion` on
    the UTF-8 prepares, `linkerOpts.linux_x64`). Deliberately does
    NOT compile/parse doltlite.c: parsing it would fully define
    `struct sqlite3` on that target, moving the type out of
    `cnames.structs` and breaking the shared nativeMain source set
    (kmp-native-interop skill, cinterop gotchas).
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
  expect/actual-class Beta (androidx does the same). Room runtime:
  `room3-runtime` moved commonTest→commonMain `api` in Step 7 (D10 —
  `DoltDatabase`'s public surface takes a `RoomDatabase`); the Room
  COMPILER stays test-only. Test deps:
  commonTest adds `kotlinx-coroutines-test` 1.10.2;
  jvmTest adds `androidx.sqlite:sqlite-bundled`
  (the oracle). Step 4 wired the `ksp` + `androidx.room3` Gradle
  plugins (root: apply false; library: applied), `room3 {
  schemaDirectory("$projectDir/schemas") }` (required once the plugin
  is applied), and `room3-compiler` on every TARGET'S TEST ksp
  configuration only: `kspJvmTest`, `kspAndroidHostTest`,
  `kspAndroidDeviceTest`, `kspLinuxX64Test`, `kspIosArm64Test`,
  `kspIosSimulatorArm64Test` (main compilations get no KSP — no Room
  code ships). Step 6 removed the LAST `failOnNoDiscoveredTests`
  carve-out; its `sqlite-bundled` dependency on linuxX64Test was
  REVERTED post-Step-11 (symbol collision — see the build-script
  comment at the former wiring site and the maintenance log entry).
  Step 5 wired
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
  + the catalog's ndk pin). Step 6 native plumbing:
  `compileDoltliteStaticLinuxX64` (host gcc → `ar` →
  `build/nativeLibs/linuxX64/libdoltlite.a`, embedded into the klib
  via cinterop `extraOpts -staticLibrary/-libraryPath`; the archive
  must dodge Konan's bundled glibc-2.19 sysroot skew:
  `-DSQLITE_DISABLE_LFS` kills the fcntl→fcntl64 redirect — no-op on
  x86_64 — and `objcopy --redefine-sym` maps the `_GNU_SOURCE`-forced
  glibc-2.38 `__isoc23_strto*` C23 redirects back to classic
  symbols); one shared `doltlite` cinterop on all three native
  targets' main compilations (commonized into nativeMain), with
  explicit task deps — cinterop does NOT track the embedded archive
  as an input, so it's declared via `inputs.files`. Step 8 fixture
  plumbing: `downloadDoltliteTools`/`unpackDoltliteTools` (release
  zip `doltlite-tools-linux-x64-0.11.33.zip`, SHA-256
  `6d9b2353f051ce79d3637d57facae293cacb320cfb5b3eebe896c18af1338932`,
  linux-x64 hosts only via `onlyIf` — the flag is copied to a local
  inside each task block because an `onlyIf` capturing a script-level
  val breaks the configuration cache) → jvmTest gets system property
  `dev.seri.doltrooms.remotesrv` pointing at the unpacked
  `doltlite-remotesrv`. Step 10: BOTH download tasks accept a
  pre-seeded zip whose SHA-256 already matches the pin without a
  network fetch (makes the CI zip cache effective; anything else is
  re-fetched, and the mismatch-after-download failure contract is
  unchanged). **Settled compile
  flags:** androidx
  sqlite-bundled's set MINUS `SQLITE_OMIT_SHARED_CACHE` and
  `SQLITE_DEFAULT_WAL_SYNCHRONOUS=1` (both break DoltLite's fork — see
  Step 1 log), PLUS `-DDOLTLITE_VERSION="0.11.33"`.
- **Pinned versions** (`gradle/libs.versions.toml`): doltlite `0.11.33`,
  room3 `3.0.0`, androidxSqlite `2.7.0`, ksp `2.3.10`, kotlin `2.3.10`,
  agp `9.0.1`, kotlinxCoroutines `1.10.2`, android-ndk
  `28.2.13676358` (Step 5; r28+ defaults to 16 KB page alignment),
  dokka `2.2.0` (Step 11; V2 mode is the default, plugin applied in
  the library, `apply false` at the root). androidx-sqlite (commonMain
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
- **Build/test commands that pass — the canonical Linux matrix,
  frozen at Step 10 (the same single line CI runs):**
  `./gradlew build :library:jvmTest :library:testAndroidHostTest
  :library:linuxX64Test` (118 + 52 + 52 tests; the two iOS targets
  are SKIPPED on a Linux host since they carry a cinterop — exit
  stays 0; no wasm target exists, D4). Also passing: `./gradlew
  :library:bundleAndroidMainAar :library:assembleAndroidDeviceTest`
  (AAR + device-test APK both carry `lib/<abi>/libdoltroomsjni.so`)
  and `./gradlew :library:publishToMavenLocal` (Step 11 smoke — four
  publications, unsigned without env keys).
  CI: `.github/workflows/ci.yml` (ubuntu, JDK 21, setup-android,
  `sdkmanager "ndk;28.2.13676358"`, setup-gradle, actions/cache for
  DoltLite zips + `~/.konan`, the canonical line, test-report upload
  on failure, concurrency cancel, 60-min timeout) — never yet
  observed running on GitHub (docs/deferred-verification.md has the
  first-run checklist); note the linuxX64 test binary needs
  `libcrypt.so.1` at runtime (ubuntu ships it; Fedora needed
  libxcrypt-compat).
- **Environment (re-check before building):** JDK 21 (dnf), gcc/g++ 15
  (dnf), Android SDK platform 36 + build-tools 36 at `/opt/android-sdk`
  (`local.properties`, gitignored — recreate it in fresh worktrees),
  NDK 28.2.13676358 at `/opt/android-sdk/ndk/` (installed Step 5 via
  sdkmanager), sqlite-devel 3.50.2 (dnf; for differential C probes
  against stock), libxcrypt-compat (dnf; linuxX64 test.kexe links
  libcrypt.so.1).
- **Open problems handed to next session:** none — the backlog is
  complete. Remaining work is hardware-gated and enumerated in
  `docs/deferred-verification.md` (macOS: iOS + XCFramework + Central
  publishing; device: Android on-device; GitHub: first observed CI
  run).

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

### [x] Step 6 — Native targets: linuxX64 (full) + iOS (compile-only)
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

### [x] Step 7 — dolt_* versioning API
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

### [x] Step 8 — Sync via doltlite-remotesrv
- **Goal:** `push/pull/clone/fetch` helpers; `file://` remote
  round-trip proven by test; https path best-effort.
- **Skills:** `red-green-testing`,
  `doltlite/references/remotes-and-sync.md`.
- **Key tasks:** extend the dolt package (`DoltDatabase`, Step 7) with
  remote helpers — same shape: plain `usePrepared` on the writer
  connection, NO transaction wrapper (Step 7 findings); file://
  round-trip test (two DB instances: push from A, pull into B, assert
  rows + dolt_log equality); https test against a spawned
  `doltlite-remotesrv` fixture if the binary is obtainable in-env, else
  `@Ignore`-gated with the reason recorded (file://-only scope-cut is
  pre-authorized). D3 gate: pinned 0.11.33 ≥ 0.11.28 (TLS + bearer-JWT).
- **Red-green:** red = file-remote round-trip on stubs; green implement.
- **Verify:** `./gradlew :library:jvmTest :library:linuxX64Test`.
- **Risks:** remotesrv binary distribution unclear; auth setup unknown.

### [x] Step 9 — Web target (wasmJs, best-effort, droppable)
DROPPED (2026-07-18), exercising the card's droppable clause — no
`wasmJs` target was ever added, so there was nothing to remove. The
in-session evidence (probed compile failure, artifact facts, no
browser in-env) is in the Step Log; the decision is the D4 amendment
in ARCHITECTURE.md. Revisit only as a dedicated iteration.

### [x] Step 10 — CI hardening + verification matrix
- **Goal:** One workflow covering everything Linux-verifiable: build,
  jvmTest, linuxX64Test, testAndroidHostTest — NOT iOS:
  since Step 6's cinterop, Apple targets cannot compile on a Linux
  host (their tasks SKIP; see the Step 6 log); and NOT wasm (the web
  rung was dropped in Step 9 — D4 amendment); caching for the
  amalgamation + tools downloads + Konan; EXTEND `docs/deferred-verification.md`
  (created in Step 6 with the iOS compile/link/test and Android
  device-test entries) with XCFramework packaging and whatever else
  accumulates.
- **Skills:** none new; reread `kmp-native-interop` publishing notes.
- **Red-green:** N/A (infra). Verify: green pipeline on a no-op change;
  freeze the canonical command list into Current State.

### [x] Step 11 — Publishing + docs + closeout
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

### [x] Step 12 — samples/codelab: Fruitties on Dolt (human-opened iteration, 2026-07-21)
- **Goal:** build out sample apps into `samples/codelab`: Google's
  kmp-migrate-room codelab in its post-migration state, modified to
  run on Dolt for all platforms, with a README documenting that
  lineage.
- **Skills:** `room3` (+ kmp-setup reference), `skill-maintenance`
  (codelab fetch), `architecture-docs` (D5 amendment).
- **Key tasks:** port the `end`-branch Fruitties app to Room 3 +
  `DoltLiteDriver` as a standalone composite build; replace SKIE with
  a manual Flow bridge (no Kotlin 2.3.x SKIE exists); add the iOS
  engine archives the sample's framework link needs (deferred-
  verification iOS item 3) plus `iosTest` concretes (item 4).
- **Verify:** sample compile + unit tests; framework links (both
  slices) + Swift typecheck; `:library:iosSimulatorArm64Test` green.
  APK packaging/device tests and an in-simulator app run stay in
  `docs/deferred-verification.md`.

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

### Step 6 — Native targets: linuxX64 (full) + iOS (deferred) (2026-07-18, branch `claude/step-6-plan-5c003b`)

- **Red-green, one large increment (as the card framed it), one
  commit:** red = the four linuxX64Test concretes (both suites × both
  engines) — 66 tests discovered, the 33 DoltLite legs failing
  `NotImplementedError` on the nativeMain TODO stubs while all 33
  Bundled oracle legs passed, validating the harness on the native
  rung before any driver code existed. Green = the `doltlite.def`
  cinterop + `compileDoltliteStaticLinuxX64` + real nativeMain
  actuals (a literal port of the jvmAndroid semantics). Final: 66/66
  linuxX64, 78 jvm, 33 androidHost, `./gradlew build` green; the last
  `failOnNoDiscoveredTests` carve-out removed.
- **Zero driver-semantics changes were needed** — every conformance
  case, Room case, and documented divergence behaves identically
  through cinterop. Exception messages are observable on native, so
  the message-content assertions run there (unlike androidHostTest).
- **Three build-level fights, all resolved and folded into the
  kmp-native-interop skill (cinterop gotchas section):**
  (1) glibc skew — Konan links linuxX64 against its bundled
  glibc-2.19 sysroot, so the host-gcc archive's references to
  `fcntl64` (glibc 2.28, via the amalgamation's own
  `_FILE_OFFSET_BITS=64`) and `__isoc23_strtol` (glibc 2.38 C23
  redirect, forced by `_GNU_SOURCE` regardless of `-std`) broke
  `ld.lld`; fixed with `-DSQLITE_DISABLE_LFS` (no-op on x86_64) +
  `objcopy --redefine-sym __isoc23_strto*` → classic symbols.
  (2) A first attempt compiled the amalgamation INSIDE cinterop
  (SQLDelight-style `---` + `#include "doltlite.c"`): cinterop then
  PARSED the amalgamation, fully defining `struct sqlite3` on linux
  only and breaking shared nativeMain (opaque `cnames.structs` vs
  package class across targets). Bindings must stay headers-only;
  reverted to the embedded static archive.
  (3) cinterop does not track the `-staticLibrary` archive as an
  input — a rebuilt archive left a stale copy in the klib until
  declared via `inputs.files`.
- **Card divergence — iOS:** the card assumed "iOS compiles on Linux,
  link+test deferred". Reality: KGP 2.3.10 disables Apple-target
  compilation on non-Mac hosts the moment the target carries a
  cinterop (tasks SKIPPED, build exit 0). So iOS compile+link+test
  ALL defer to a Mac. `docs/deferred-verification.md` created with
  the ordered Mac checklist (cinterop → compile → per-slice
  amalgamation archives → simulator tests) and the Step 5 Android
  device-test entry; Step 10's card updated in place (no iOS in CI).
- **Decision recorded:** ARCHITECTURE.md D9 — every platform builds
  `libdoltlite` from the one pinned amalgamation; upstream prebuilt
  artifacts (JNA AAR — D8, doltlite-swift XCFramework at 0.11.17 vs
  our 0.11.33 pin, prebuilt lib zips) are not consumed. §3.2/§3.3
  updated (commonized cinterop, nativeMain no longer stubs), codemap
  gained `docs/deferred-verification.md`; D1–D8 range references
  bumped to D1–D9 across AGENTS.md/PLAN.md/architecture-docs/
  skill-maintenance (frontmatter of architecture-docs hand-checked;
  skills-ref still not in-env).
- **Native driver mechanics worth remembering:** opaque handles
  import from `cnames.structs` (no package typealias when typedef
  name == struct tag); `SQLITE_TRANSIENT` = `(-1L).toCPointer()`
  (SQLiter pattern); `String.utf16` + `length * 2` for UTF-16
  prepare/bind; length-based text reads via `column_bytes16`;
  `kotlinx.cinterop.value` must be imported explicitly for
  `allocPointerTo(...).value`.
- **CI updated (unverified until pushed):** adds
  `:library:linuxX64Test` (ubuntu ships the `libcrypt.so.1` the test
  binary links).
- **Environment delta:** recreated `local.properties` (fresh
  worktree). No new packages.
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  unchanged.

### Step 7 — dolt_* versioning API (2026-07-18, branch `claude/plan-step-7-14b1ed`)

- **Probe first (Step 3/4 precedent):** a throwaway
  `ScratchDoltProbeTest` (two rounds, deleted) established every
  version-sensitive fact against the pinned 0.11.33 before any test
  was written — return shapes (`dolt_commit`/`dolt_merge` → hash,
  `dolt_add`/`dolt_branch`/`dolt_checkout` → 0), the
  `dolt_log`/`dolt_status`/`dolt_branches`/`dolt_diff_<table>` column
  schemas, per-connection branch state, transaction interactions,
  conflict behavior, `:memory:` support, quoted-TVF/bindable-ref diff
  mechanics. All folded into the doltlite skill's version-control-sql
  reference ("Probed facts" section) per skill-maintenance.
- **Red-green, six increments, one commit each:** (1) suite bootstrap —
  commonTest `AbstractDoltDatabaseTest` with `engineSupportsDolt`
  capability flag, jvm concretes for DoltLite AND Bundled (flag=false
  → guard test only), commit/log green after moving `room3-runtime`
  commonTest→commonMain `api` and writing `DoltDatabase.commit/log`
  (red was `Unresolved reference 'DoltDatabase'`); (2) status +
  nothing-to-commit error contract; (3) branch/checkout/currentBranch/
  branches/deleteBranch + the readers-don't-follow-checkout contract
  test; (4) merge — ff head, clean 3-way merge commit, conflicted
  merge throws + rolls back; (5) diff — typed rows via the per-table
  TVF incl. WORKING pseudo-ref; (6) `@SkipQueryVerification
  doltVersion()` on PersonDao, compile- and runtime-validated. Then
  linuxX64 + androidHost concretes (no new driver or helper changes
  needed). Final: 102 jvm + 45 androidHost + 78 linuxX64, `./gradlew
  build` green.
- **Card divergences:** (a) the card said helpers issue dolt_* SQL
  "inside `immediateTransaction`" — WRONG at the engine level:
  `dolt_commit` inside an open `BEGIN` commits and ends that
  transaction, so the wrapper's closing `COMMIT` throws "cannot
  commit - no transaction is active"; helpers use plain `usePrepared`
  on the writer connection with no transaction wrapper (KDoc + the
  room3 raw-connections reference updated). (b) DoltLite's `dolt_diff()`
  function takes no arguments ("too many arguments on dolt_diff() -
  max 0") — row-level diff goes through the generated
  `dolt_diff_<table>` TVF instead. (c) The "capability flag" landed as
  `engineSupportsDolt` on the abstract suite: versioning tests
  early-return when false, and the guard test (clean SQLiteException
  from a dolt-less engine, DB still usable after) runs only when
  false — the Bundled jvm concrete is that one guard leg.
- **Engine finding with API consequences — branch state is
  per-connection:** a checkout affects only the issuing connection;
  fresh connections open on `main`; nothing persists across reopen;
  no default-branch config exists. So `DoltDatabase` routes ALL
  helpers (reads included) through `useWriterConnection` for a
  consistent view, and Room reader connections do not follow a
  checkout — DAO reads keep seeing `main`. Pinned as the
  `readerConnectionsDoNotFollowCheckout` test and documented
  prominently in the class KDoc; branch-and-read workflows must stay
  on the writer connection (or accept main-branch reads).
- **Decision recorded:** ARCHITECTURE.md D10 — typed dolt_* helpers
  ride Room's raw-connection API; `room3-runtime` becomes a
  commonMain `api` dependency (the revisit Step 4 anticipated), while
  the Room compiler stays test-only. §3.2/§3.3 updated; D1–D9 range
  references bumped to D1–D10 across AGENTS.md/PLAN.md/
  architecture-docs/skill-maintenance (frontmatter hand-checked;
  skills-ref still not in-env).
- **Skill maintenance:** doltlite version-control-sql gained the
  probed-facts section; room3 raw-connections' "verify whether
  dolt_commit may run inside an explicit transaction" question is
  answered (it consumes the transaction — never wrap); room3
  query-verification's @SkipQueryVerification grammar-tolerance
  caveat is now empirically validated (PersonDao.doltVersion is the
  living compile test).
- **Environment delta:** recreated `local.properties` (fresh
  worktree). No new packages.
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  unchanged.

### Step 8 — Sync via doltlite-remotesrv (2026-07-18, branch `claude/step-8-continuation-4210d5`)

- **Probe first (Step 7 precedent):** a throwaway
  `ScratchRemoteProbeTest` (two rounds, deleted) plus shell probes
  with the release CLI established every remote fact at 0.11.33
  before any real test: return values (all remote functions → 0),
  `dolt_remotes` schema, arities (`dolt_push(remote, branch
  [,'--force'])`, `dolt_pull(remote, branch)` fixed; `dolt_fetch`
  1-or-2-arg), error strings, clone's fresh-database requirement,
  fetch materializing `origin/<branch>`, diverged-pull auto-merge,
  conflicted-pull rollback, force push. All folded into the doltlite
  skill's remotes-and-sync "Probed facts" section per
  skill-maintenance.
- **Red-green, five increments, one commit each:** (1)
  addRemote/remotes/removeRemote + `DoltRemote` (red: unresolved
  refs; detour: a KDoc example containing a literal refspec glob
  commented out half the file — Kotlin block comments NEST, so a
  slash-star inside KDoc opens a nested comment; reworded); (2) push
  (incl. `force`) + the `clone` companion, replication test; (3)
  pull, completing the card's push-from-A/pull-into-B round-trip
  (rows + dolt_log equality); (4) fetch + the
  not-mergeable-before-first-fetch contract; (5) probe-backed
  contract pins (non-ff reject/--force, conflicted-pull rollback,
  clone-needs-fresh-db + missing-remote). Then the jvm
  `RemoteServerSyncTest` fixture (red: test fails hard on linux-x64
  when the Gradle wiring is absent; green: tools download/unpack
  tasks + system property). Final matrix: 118 jvm + 52 androidHost +
  85 linuxX64, `./gradlew build` green.
- **Card divergences:** (a) "https path best-effort" resolved as
  IMPOSSIBLE for this library, not just unproven: the amalgamation
  deliberately excludes the TLS/credential stack (doltlite.c quote in
  the skill), so amalgamation-built engines (D9 = all of ours) reject
  `https://` at first use and have no bearer-JWT auth.
  `httpsRejectedByAmalgamationBuiltEngine` pins the contract; D3
  amended (trusted-networks-only). The prebuilt remotesrv + CLI DO
  speak TLS 1.3 — shell-verified with a self-signed `--cert/--key`
  server and `DOLTLITE_CA_FILE` (fact recorded in the skill for a
  future prebuilt-artifacts revisit). (b) The card's "extend
  DoltDatabase with remote helpers — same shape" held EXCEPT clone:
  the engine only clones into a fresh database and Room dirties one
  at open, so `DoltDatabase.clone` is a companion bootstrap on a raw
  driver connection (D10 amended). (c) The remotesrv binary IS
  obtainable in-env (`doltlite-tools-linux-x64-0.11.33.zip`,
  checksum recorded) — the http fixture leg is real, spawning
  `-p 0` and parsing the printed URL; no `@Ignore` needed. Non-linux-
  x64 hosts skip it (docs/deferred-verification.md entry).
- **Engine finding with API consequences — replica id collision:**
  concurrent inserts on two replicas mint the same AUTOINCREMENT
  rowid, and a shared PK with different values is a row conflict on
  pull/merge (first seen as an unexpected red in the force-push
  test). Distinct explicit ids merge cleanly; `sqlite_sequence`
  itself never conflicts (resolves toward the incoming/larger seq).
  Documented in DoltDatabase's KDoc ("Remotes and sync"): syncing
  apps need collision-free keys.
- **Gradle gotcha:** an `onlyIf { scriptLevelVal }` cannot be
  configuration-cached ("cannot serialize Gradle script object
  references") — copy the value to a local inside the task
  registration block first.
- **Decisions recorded:** D3 amendment (amalgamation has no TLS/auth
  → file:// + plain http only, trusted networks, revisit if upstream
  ships TLS in the amalgamation); D10 amendment (remote surface +
  the one clone-bootstrap exception to ride-Room).
- **Environment delta:** recreated `local.properties` (fresh
  worktree). No new packages (openssl/curl already present; used
  only for probing).
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  unchanged.

### Step 9 — Web target (wasmJs) — DROPPED (2026-07-18, branch `claude/step-9-b192ee`)

- **Outcome: the droppable clause was exercised.** No production or
  build code changed; `wasmJs` was never added (D4 forbade
  scaffolding ahead), so "remove the target" was a no-op. D7/red-green
  did not bind — no net-new production code was written.
- **Evidence gathered before deciding (not from memory):**
  1. *Upstream artifacts all exist* — availability is NOT the
     blocker. Gradle module metadata (dl.google.com, fetched
     2026-07-18): `androidx.sqlite:sqlite:2.7.0` publishes
     `sqlite-wasm-js` + `sqlite-js` variants;
     `androidx.room3:room3-runtime:3.0.0` publishes
     `room3-runtime-wasm-js`. npm registry: `@dolthub/doltlite-wasm`
     0.11.33 exists and was still `latest` (published 2026-07-17) —
     lockstep with our pin today, though it versions independently.
  2. *The structural blocker, probed by compile experiment:*
     temporarily added `wasmJs { browser() }` and ran
     `:library:compileKotlinWasmJs`. Result (errors captured, edit
     reverted): commonMain resolved androidx.sqlite's WEB actuals —
     `DoltLiteDriver.kt` failed with "Non-suspend function 'open'
     cannot override suspend function 'suspend fun open(fileName:
     String)'" (same for `prepare`, `step`), and `DoltDatabase.kt`'s
     `open`/`prepare`/`step` calls became suspend-only. Since 2.7.0
     the interfaces are expect-split commonMain/nonWebMain/webMain;
     a source set sees the fragments shared by ALL its targets, so
     one web target evicts the nonWeb members from `commonMain`.
     Supporting web therefore means migrating every commonMain/
     commonTest file (public expect classes, `DoltDatabase`, all
     three abstract suites, the Room fixture) onto a new nonWeb
     intermediate rail — and the web driver itself would share zero
     code with the other five targets (different, suspend interface
     variants; a from-scratch driver).
  3. *The engine would violate D9:* Kotlin/Wasm cannot link an
     Emscripten-built C library (kmp-native-interop skill), so the
     engine must be the prebuilt `@dolthub/doltlite-wasm` npm package
     — the upstream-prebuilt category D9 explicitly rejects (JNA AAR,
     XCFramework, lib zips). Its 0.11.33 layout (registry `exports`
     map) is the standard sqlite-wasm `ext/wasm` build:
     `sqlite3.mjs`/`sqlite3.wasm`, OPFS async proxy,
     worker1 + promiser, no TypeScript types — a binding would be
     hand-written `external` declarations over an unpinnable-by-us
     artifact.
  4. *Untestable in-env:* OPFS is browser-only (the Node story is a
     separate NATIVE package, `@dolthub/doltlite`); no browser exists
     on this host (checked chromium/chrome/firefox — absent), so the
     card's "smoke tests where runnable" resolves to "none runnable"
     — a from-scratch driver would land with zero executed tests,
     against the project's differential discipline.
- **Decision recorded:** ARCHITECTURE.md D4 amended in place (drop +
  the three probed facts + revisit-as-dedicated-iteration); §4's
  ladder reference updated. Step 10's card updated in place (no wasm
  compile in CI).
- **Skill maintenance (per the five-step workflow):**
  `androidx-sqlite` SKILL.md now records the target-set⇒resolution
  mechanic and the published `sqlite-wasm-js`/`sqlite-js` variants;
  `doltlite` artifacts-and-build gained the doltlite-wasm 0.11.33
  package layout + lockstep note; `kmp-native-interop`
  targets-and-publishing's web section rewritten from "out of scope —
  know why" to "dropped — know why" with the probe facts and module
  metadata citations. skills-ref still not in-env; frontmatter
  untouched, hand-checked.
- **Environment delta:** recreated `local.properties` (fresh
  worktree). No new packages.
- **Verification:** full matrix re-run at closeout on the unchanged
  tree — `./gradlew build :library:jvmTest :library:testAndroidHostTest
  :library:linuxX64Test` all green (118 jvm + 52 androidHost +
  85 linuxX64), iOS tasks skipped as expected on a Linux host.
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  unchanged.

### Step 10 — CI hardening + verification matrix (2026-07-18, branch `claude/step-10-b20930`)

- Infra step; D7 red-green N/A per the card. Coverage needed no
  change — the workflow's one `run` line has covered the full
  Linux-verifiable matrix since Steps 6/8 — so the work was the
  hardening, the cache-enabling build-script change, and the doc
  extensions.
- **Build-script change (the piece that makes CI caching real):**
  `downloadDoltliteAmalgamation`/`downloadDoltliteTools` now accept a
  pre-seeded zip whose SHA-256 matches the pin and skip the network
  fetch. Rationale: `actions/cache` restores files, but Gradle task
  history does not survive a fresh runner, so the tasks would
  re-execute and re-download on every CI run regardless of the cache.
  Both branches verified locally: `--rerun` with matching zips
  present → task completes in ~1s with file mtimes unchanged (no
  fetch); a corrupted zip → re-fetched, checksum green. The
  mismatch-after-download failure contract is unchanged. The skip
  logic is duplicated inline in each task's `doLast` deliberately — a
  shared script-level helper captured inside a task action would
  break the configuration cache (Step 8's `onlyIf` lesson, same
  mechanism).
- **Workflow hardening (`.github/workflows/ci.yml`):** two
  `actions/cache` steps — `library/build/doltlite/*.zip` and
  `~/.konan` (`gradle/actions/setup-gradle` caches neither) — with
  keys hashing `gradle/libs.versions.toml`, since every relevant pin
  (doltlite, kotlin/Konan) lives there; `concurrency`
  cancel-in-progress per ref; `timeout-minutes: 60` (the cold local
  matrix incl. downloads runs well under 15 min; CI adds Konan
  provisioning once, then the cache carries it); on-failure artifact
  upload of `library/build/reports/tests/` (path verified against the
  local run's actual report layout: `jvmTest`/`testAndroidHostTest`/
  `linuxX64Test`/`allTests`).
- **Card's "green pipeline on a no-op change" — deferred, recorded:**
  the workflow triggers on pushes to `main`/`develop` and on PRs, and
  in-env sessions do not push (AGENTS.md working rule; every prior
  step logged CI as "unverified until pushed"). Local stand-ins all
  ran green on this tree: the canonical matrix (118 + 52 + 85), a
  YAML parse check of the workflow, and both download branches above.
  actionlint was attempted but unavailable (not in Fedora repos;
  binary download blocked in-env). The first-observation checklist —
  green run, cache miss-then-hit, no-network download-task pass-through,
  timeout headroom — is now a `docs/deferred-verification.md` entry
  and lands with this branch's PR.
- **docs/deferred-verification.md extended (card):** new XCFramework
  packaging section (Mac-only `XCFramework()` DSL +
  `assembleXCFramework`, dependent on the iOS checklist's per-slice
  archives; pointer to Step 11's publish-from-a-single-Mac
  requirement) and the first-observed-CI-run section; the header's
  step-ownership line updated. ARCHITECTURE.md codemap row for the
  file synced (architecture-docs skill loaded; no decisions touched,
  D-numbers unchanged).
- **Canonical command list frozen into Current State (card):**
  `./gradlew build :library:jvmTest :library:testAndroidHostTest
  :library:linuxX64Test`.
- **Environment delta:** recreated `local.properties` (fresh
  worktree). No new packages.
- **Follow-ups:** none new; Step 0–3 skill-maintenance queue
  unchanged.

### Step 11 — Publishing + docs + closeout (2026-07-18, branch `claude/step-11-e75870`)

- Infra/docs step; D7 red-green N/A (no production code). Executed as
  three commits (publishing, USAGE.md, docs/skills sync) plus this
  closeout commit.
- **Publishing finalized** (`library/build.gradle.kts`): POM
  license/developer/scm placeholders replaced with reality —
  Apache-2.0 (matches the repo `LICENSE`), developer `Serisium` (Seri
  Greenwood), scm URLs derived from the `origin` remote
  (`github.com/Serisium/Doltrooms`). Dokka `2.2.0` added (catalog pin
  + root `apply false` + applied in the library) and wired as the
  Central-required `-javadoc` jar via `mavenPublishing {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka(...),
  sourcesJar = SourcesJar.Sources())) }`; no
  `androidVariantsToPublish` parameter with the AGP KMP library
  plugin (vanniktech 0.36.0 docs). Signing/credentials arrive via
  `ORG_GRADLE_PROJECT_mavenCentralUsername`/`-Password` +
  `signingInMemoryKey`/`-KeyId`/`-KeyPassword`.
- **Verified facts (not from memory):** Dokka's latest stable is
  2.2.0 (Maven Central metadata, 2026-03); at 2.2.0 V2 mode is the
  default (no `pluginMode` property, no V1 warning); the real
  generation task is `dokkaGeneratePublicationHtml` — the vanniktech
  docs' `"dokkaHtml"` example is the Dokka 1.x task name and
  `dokkaGenerateHtml` is just a lifecycle wrapper (probed via
  `help --task`); with `signAllPublications()` and no key in env the
  sign tasks are SKIPPED, so `publishToMavenLocal` works unsigned.
  All folded into the kmp-native-interop targets-and-publishing
  reference (new vanniktech-specifics section) per skill-maintenance.
- **publishToMavenLocal smoke (card):** green on Linux; inspected
  `~/.m2` — root umbrella (jar + module metadata +
  kotlin-tooling-metadata + real POM), `-jvm` jar with
  `natives/linux-x64/libdoltroomsjni.so`, `-android` AAR with both
  ABI `.so`s, `-linuxx64` klib with the `-cinterop-doltlite.klib`
  alongside, real Dokka HTML in every `-javadoc` jar. iOS
  publications are absent on a Linux host (KGP skips Apple targets
  carrying cinterops) — hence the new dedicated
  `docs/deferred-verification.md` entry: the actual Central release
  runs entirely from a single macOS host (checklist included), which
  the card pre-announced.
- **docs/USAGE.md written (card):** dependency + context-free
  `Room.databaseBuilder().setDriver(DoltLiteDriver())` setup,
  per-platform engine-delivery table (JAR-resource extraction +
  `-Ddev.seri.doltrooms.lib.path` override, AAR jniLibs, embedded
  static archive + libcrypt note, iOS deferral), the dolt_* tour with
  the load-bearing semantics (per-connection branch state /
  readers-don't-follow-checkout, never wrap helpers in transactions,
  conflict behavior), remotes/sync (file://+http-only, clone-before-
  Room bootstrap, refs-after-first-fetch, AUTOINCREMENT collision
  warning), `@SkipQueryVerification` example, and the divergence
  table (the compile-flag row reworded as the non-divergence it is).
- **Docs/architecture sync:** ARCHITECTURE.md — codemap gained the
  USAGE.md row, deferred-verification row now names Maven Central,
  §3.2's publishing bullet describes the finalized setup, §4 flipped
  Implementation → Maintenance (iteration completed at this step),
  Status line updated; AGENTS.md phase references synced. No
  decisions added or renumbered — D1–D10 unchanged.
- **Closeout extras:** the Step 0–3 skill-maintenance queue is now
  fully drained — audit found everything already folded by Steps
  4/7/9 except the sqlite-c-api watchlist, which now carries the
  confirmed-at-0.11.33 section (deferred open, wal default, WITHOUT
  ROWID, finalize-SIGABRT; confirmed-preserved list). PLAN.md gained
  the MAINTENANCE MODE preamble (the card's "flip to maintenance
  mode").
- **README suggestions for the human (never agent-edited, D6):**
  (1) point readers at `docs/USAGE.md`; (2) a "Status" refresh — the
  driver + dolt_* + sync are implemented with 118/52/85 green tests
  across jvm/androidHost/linuxX64; (3) note the platform matrix (JVM,
  Android, linuxX64 verified; iOS built-but-Mac-deferred; web
  dropped); (4) Maven coordinates `dev.seri.doltrooms:doltrooms` once
  the first Central release lands.
- **Verification:** `./gradlew :library:publishToMavenLocal build
  :library:jvmTest :library:testAndroidHostTest :library:linuxX64Test`
  all green on this tree (118 + 52 + 85; iOS tasks skipped as always
  on Linux). CI remains unobserved on GitHub (no-push rule) — first
  PR observation checklist unchanged in deferred-verification.
- **Environment delta:** recreated `local.properties` (fresh
  worktree). No new packages (Dokka arrives via Gradle).
- **Follow-ups:** none — see the maintenance-mode preamble for what
  future sessions may do.

### Maintenance — linuxX64 engine symbol collision + template-workflow cleanup (2026-07-18, post-Step-11, same branch/session, PR #2)

- **Trigger:** the first observed CI run (PR #2, created this
  session on user request along with `docs/BUILDOUT_PLAN.md`, a
  frozen snapshot of this file). `ci.yml` failed in `linuxX64Test`:
  all 18 dolt-touching cases red with "no such function:
  dolt_commit" (messages recovered from the Step 10 on-failure
  test-report artifact — that machinery paid for itself on its first
  outing), while every driver/Room conformance case passed.
- **Root cause (verified structurally, not guessed):** the linuxX64
  test binary statically linked TWO engines — our `libdoltlite.a`
  (275 exported `sqlite3_*` globals) and androidx sqlite-bundled's
  `libandroidXBundledSqlite.a` (269, inside its cinterop klib). With
  identical strong symbols, archive link semantics produce no
  duplicate-symbol error: the first archive wins for EVERY caller,
  so ONE engine served BOTH drivers. Locally DoltLite won (nm on the
  kexe: single `sqlite3_open_v2`, 443 dolt strings) — meaning Step
  6's "native differential matrix" was illusory (the oracle leg ran
  DoltLite); on ubuntu CI androidx's engine won — DoltLiteDriver
  drove stock sqlite, and only the dolt suite could notice. No local
  test could catch it: the conformance suites deliberately avoid
  divergent behaviors, and no Bundled dolt-guard concrete existed on
  native.
- **Fix (user-approved option: drop the native oracle legs):**
  removed `sqlite-bundled` from linuxX64Test dependencies (replaced
  by an explanatory comment at the wiring site) and deleted the two
  Bundled concrete classes in `library/src/linuxX64Test/`. Exactly
  one engine now links into the native test binary on every host.
  The differential oracle remains on jvmTest (separate dynamic
  libraries — no collision possible); on native the dolt suite pins
  engine identity (it is precisely what failed when the wrong engine
  captured the binary). linuxX64 test count: 85 → 52. Current State
  synced (test map, Step 6 findings, build facts, canonical counts
  118 + 52 + 52).
- **Also (user-approved): deleted `.github/workflows/gradle.yml`** —
  an unmaintained `multiplatform-library-template` leftover from the
  initial commit (never tracked by any step): its matrix duplicated
  ci.yml without the caching/NDK setup, and its macos
  `iosSimulatorArm64Test` job cannot pass before the deferred iOS
  archive work. `publish.yml` (same origin, release-triggered,
  dormant) KEPT for later review against the finalized signing env
  names before the first release.
- **Docs/skills:** USAGE.md gained the consumer-facing warning
  (never statically link a second SQLite into the same native
  binary); deferred-verification's first-CI-run entry rewritten as
  partially observed + the iOS checklist warned off Apple Bundled
  legs; kmp-native-interop cinterop gotchas gained the
  archive-order-roulette entry. BUILDOUT_PLAN.md deliberately NOT
  updated (frozen snapshot).
- **Verification:** full canonical matrix green post-fix
  (118 + 52 + 52); CI on PR #2 re-observed after push: GREEN in
  ~5 min, and a re-run HIT both caches with the download tasks
  passing through sub-second on the restored zips — the
  first-observed-CI-run checklist is fully closed
  (deferred-verification entry updated; note actions/cache saves
  only on job success, so a failed first run populates nothing).

### Step 12 — samples/codelab: Fruitties on Dolt + first-macOS iOS verification (2026-07-21, branch `claude/kmp-room-codelab-samples-fcce62`)

- **Opened by the human** ("Build out sample apps into samples/codelab"
  with the codelab lineage in the README) — the first post-maintenance
  iteration, appended per the MAINTENANCE MODE preamble.
- **Source:** `android/codelab-android-kmp`, `migrate-room` project,
  branch `end` (the codelab's solution state: Room 2.7.2 KMP over
  BundledSQLiteDriver, SKIE 0.10.4, Hilt 2.57, Kotlin 2.2.0,
  AGP 8.11.1). Copied wholesale; Apache-2.0 AOSP headers preserved;
  every delta is listed in `samples/codelab/README.md`.
- **Port decisions and findings (all verified by building):**
  - Standalone composite build: `includeBuild("../..")` with an
    EXPLICIT `dependencySubstitution` — automatic substitution does
    not map the `doltrooms` artifactId onto the `:library` project
    name. Runs off the root wrapper (no second wrapper committed).
  - Kotlin floor: 2.3.10 (the library's klibs), which cascaded: KSP
    2.3.10, AGP 9.0.1 (built-in Kotlin — `org.jetbrains.kotlin.android`
    must NOT be applied; Hilt 2.57's plugin dies on removed
    BaseExtension → 2.60.1), Room 2.7.2 → Room 3.0.0.
  - Room 3 rename beyond the package: `@Relation` singular
    `parentColumn`/`entityColumn` became plural array
    `parentColumns`/`entityColumns` (verified via javap on
    room3-common-jvm-3.0.0), and the processor rejects a relation POJO
    whose constructor puts the `@Relation` property before the
    `@Embedded` parent — reordered, fakes updated (room3 skill gained
    the gotcha).
  - No SKIE for Kotlin 2.3.x exists (Maven artifact sweep: per-KGP
    plugins stop at kgp_2.2.0, latest 0.10.13 of 2026-06-24) — Swift
    Flow interop replaced by `shared/src/iosMain/.../FlowWatch.kt` +
    AsyncStream wrapping; Kotlin top-level funcs now reached via
    file-facade classes; suspend Int returns arrive as KotlinInt.
  - Upstream `end` branch does not itself compile its androidApp unit
    tests (FakeFruittieApi feeds List<Fruittie> into
    List<FruittieNetworkEntity>) — fixed in the sample.
  - DoltLite divergence #1 honored: the Android factory mkdirs the
    database parent directory before open.
  - `iosX64` dropped (library ships arm64 slices only); Spotless
    dropped.
- **Library changes (bug-fix / deferred-verification scope):**
  - `CompileDoltliteAppleStaticTask` — per-slice static engine
    archives (`xcrun --sdk iphoneos|iphonesimulator clang` +
    `libtool -static`, same pinned amalgamation + flags; targets
    arm64-apple-ios12.0 / arm64-apple-ios14.0-simulator), embedded
    into the iOS cinterop klibs via the linuxX64
    `-staticLibrary`/`-libraryPath` pattern. Registration, embedding,
    and task wiring are ALL gated on a macOS host so Linux/CI
    behavior is unchanged.
  - `CompileDoltliteAndroidJniTask` now picks the NDK host prebuilt
    tag (darwin-x86_64 vs linux-x86_64) instead of hardcoding linux.
  - `library/src/iosTest/` concretes mirroring linuxX64Test (temp
    paths via NSTemporaryDirectory; no Bundled oracle — the
    symbol-collision rule).
- **Verified this session (macOS, Xcode 26.5, JDK 24):**
  `:library:iosSimulatorArm64Test` GREEN — 52 tests / 0 failures on
  the iOS 18.3 iPhone 16 Pro simulator (deferred-verification iOS
  entry closed, incl. clean links for both slices);
  `:androidApp:compileDebugKotlin` and `:androidApp:testDebugUnitTest`
  green; `sharedKit` links for iosArm64 + iosSimulatorArm64; app
  Swift sources typecheck against the framework (swiftc -typecheck,
  target-membership-accurate file set).
- **Still deferred (entries updated):** sample APK packaging +
  connected tests (no NDK/emulator on this host), in-simulator app
  run (xcodebuild refuses all destinations without the Xcode 26.5
  iOS runtime download — `xcodebuild -downloadPlatform iOS`),
  remotesrv osx-arm64 fixture (jvmTest not runnable here anyway:
  desktop JNI build is still linux-only by design).
- **Docs:** ARCHITECTURE.md D5 amended (samples as included builds) +
  codemap row + §4 postscript + status date;
  `docs/deferred-verification.md` iOS section flipped to VERIFIED
  (XCFramework + publishing notes updated); `samples/codelab/README.md`
  written (lineage + full delta list); room3 skill gotcha added.
  Root README untouched (suggestion for the human: its "Status:
  research only" line and milestone list now lag reality — samples
  exist and iOS is verified).

### Step 12 addendum — first on-device run (2026-07-21, same branch, user request "install debug android sample app on connected adb phone")

- **Toolchain:** the Mac gained Android cmdline-tools (sdkmanager
  13114758) and the pinned `ndk;28.2.13676358` (installed via
  sdkmanager; the NDK task's darwin-x86_64 host-tag fix from earlier
  this session made it usable).
- **Sample verified end-to-end on a physical motorola razr 2025
  (arm64-v8a):** `:androidApp:installDebug` green (first-ever APK
  packaging of the DoltLite engine on a Mac host); app launched via
  adb, fruit list loaded through the DoltLite-backed Room DB
  (sharedfruits.db + WAL artifacts confirmed via run-as), cart writes
  re-emitted into the UI; screenshots taken.
  `:androidApp:connectedDebugAndroidTest` 10/10 — including the
  foreign-key-violation case (pins that DoltLite ENFORCES `PRAGMA
  foreign_keys` on-device, previously unverified anywhere) and both
  `DoltVersioningTest` cases (commit/log, branch/merge on-device).
  One sample fix: `androidTestImplementation(libs.doltrooms)` — the
  instrumented tests reference the driver directly and :shared does
  not export it.
- **Library deferred entry "Android on-device" closed:**
  `:library:connectedAndroidDeviceTest` 52/52 on the phone. The
  first real execution exposed two latent gaps behind Step 5's
  "device-test APK assembles" claim, both fixed: (1)
  `androidx.test:runner` was never packaged into the device-test APK
  (instrumentation crashed at init with ClassNotFoundException —
  catalog gained `androidx-test-runner = 1.7.0`, androidDeviceTest
  source set depends on it); (2) NO `androidDeviceTest` concretes
  existed, so after fix 1 the run was "green" with 0 tests — created
  `library/src/androidDeviceTest/` mirroring androidHostTest, minus
  the `exceptionMessagesObservable = false` override (real
  android.jar keeps messages; all 52 pass with them).
- **Docs synced:** deferred-verification Android entry flipped to
  VERIFIED (x86_64 ABI noted as the remainder), sample README
  verification section updated, this file's Current State + test map
  updated (52-test androidDevice leg).
- **x86_64 verification (2026-07-21, later same session):** the full
  Linux matrix also ran on a real Fedora 43 x86_64 host
  (root@oxefit-fedora, ~/doltrooms rsync of this worktree, JDK 21,
  /opt/android-sdk): jvmTest 118/118, testAndroidHostTest 52/52,
  linuxX64Test 52/52, and the linux-x64-only RemoteServerSyncTest ran
  for real (2/2 — live doltlite-remotesrv http sync). First
  verification outside ubuntu CI; the USAGE.md Fedora note holds
  (libcrypt.so.1 present via libxcrypt-compat, binary linked and ran).
- **CI jobs for the ABI gap + samples (2026-07-21, later same
  session):** the x86_64 emulator cannot run on any reachable host
  (fedora box = KVM guest without nested virt, Apple Silicon =
  arm64-only), so `ci.yml` gained three jobs: `android-x86_64-emulator`
  (KVM-enabled GitHub runner, API 35 x86_64 image; library
  connectedAndroidDeviceTest + sample connectedDebugAndroidTest),
  `sample-android` (APK assembly + unit tests), `sample-ios`
  (macos-15: library simulator suite, sharedKit link, xcodebuild of
  the app; setup-android added because arm64 macOS images ship no
  SDK and configuration needs one). None observed yet —
  deferred-verification has the new first-run entry with the specific
  watch-fors.
- **iOS app end-to-end (2026-07-21, later same session, after the
  user's `xcodebuild -downloadPlatform iOS`):** the Fruitties app
  built with xcodebuild for the simulator — one fix surfaced and was
  folded back into ci.yml's sample-ios job: the generic simulator
  destination links BOTH arches and sharedKit has no x86_64 slice
  (iosX64 dropped), so `ARCHS=arm64 ONLY_ACTIVE_ARCH=YES` is
  required. Installed + launched on the iPhone 16 Pro (iOS 18.3)
  simulator: network fetch into the DoltLite DB, fruit list + cart
  live via the FlowWatch bridge (incl. the @Relation join renders),
  cart writes persist. The deferred-verification iOS entry now has
  nothing simulator-side open; only physical Apple hardware remains.

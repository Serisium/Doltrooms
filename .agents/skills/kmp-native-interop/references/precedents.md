# Precedent projects — how others bundle SQLite in KMP

Verified 2026-07-17. Study order: androidx sqlite-bundled first (it
is the thing we're re-skinning, ARCHITECTURE.md D1), then SQLiter for
pure-Native, then packaging references.

## androidx sqlite-bundled — the gold standard

https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-bundled

- Downloads/prepares the SQLite amalgamation (3.50.1 via
  `PrepareSqliteSourcesTask.groovy`) and compiles it per KonanTarget
  (ANDROID_ARM32/ARM64/X64/X86, MACOS_ARM64, MINGW_X64, LINUX_X64,
  LINUX_ARM64) with explicit flags — notably `-DSQLITE_THREADSAFE=2`,
  `-Oz` on Android / `-O3` elsewhere.
- A separate `sqliteJni` C++ compilation from
  `src/jvmAndAndroidMain/jni/sqlite_bindings.cpp` statically includes
  the sqlite compilation; then
  `androidLibrary { addNativeLibrariesToJniLibs(...) }` for the AAR
  and `jvm() { addNativeLibrariesToResources(...) }` for the JAR.
- Native targets get `createCinterop(target, sqliteCompilation)` with
  the tiny `src/nativeInterop/cinterop/androidXBundledSqlite.def`
  (`headers = sqlite3.h`, per-OS `linkerOpts`,
  `noStringConversion = sqlite3_prepare_v2 sqlite3_prepare_v3`).
- Source layout: `commonMain` (API), `jvmAndAndroidMain` (JNI Kotlin
  + C++), `androidMain`/`jvmMain` (per-platform
  `NativeLibraryLoader`), `nativeMain` (cinterop impl — actually
  typealiases into sqlite-framework's `NativeSQLiteDriver`; see the
  `androidx-sqlite` skill).

For DoltLite, substitute the release-attached amalgamation
(`doltlite` skill, artifacts reference) for the SQLite amalgamation
and keep the structure.

## Touchlab SQLiter — minimal Kotlin/Native-only driver

https://github.com/touchlab/SQLiter — "SQLiter powers the SQLDelight
library on native clients." Def file at
`sqliter-driver/src/nativeInterop/cinterop/sqlite3.def`: binds system
sqlite3 headers, `noStringConversion` on the prepare functions,
`excludedFunctions` for opt-in symbols, per-target `linkerOpts`
(`-lsqlite3` — pushing linking onto the consuming app, a usability
tax androidx avoids by compiling sqlite in). Published as
`co.touchlab:sqliter-driver`.

## SQLDelight native driver — consumer layered on SQLiter

https://github.com/sqldelight/sqldelight/tree/master/drivers/native-driver
— depends on SQLiter; full Apple/Linux/Mingw matrix;
`applyDefaultHierarchyTemplate { group("nativeLinuxLike") … }` for a
custom hierarchy; links `-lsqlite3` only for test binaries; on
linuxX64 tests it cinterops the sqlite3 amalgamation "to prevent
linking issues on new linux distros."

## xerial/sqlite-jdbc — desktop JVM packaging/extraction

https://github.com/xerial/sqlite-jdbc/blob/master/USAGE.md — the
JAR-resource extraction pattern and its `-D` overrides
(`org.sqlite.lib.path`, `org.sqlite.tmpdir`), plus the explicit
statement that Android needs `jniLibs` packaging instead. Details in
`jni-and-packaging.md`.

## Room 3 sqlite-web — the async web frontier

https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-web
— `WebWorkerSQLiteDriver` over SQLite-WASM + OPFS via a Web Worker
messaging protocol; suspend-first, single connection. The template if
the web rung (ARCHITECTURE.md D4 — unscheduled) ever activates;
`@dolthub/doltlite-wasm` would sit where sqlite-wasm sits.

## Driver-shaped precedents (implementing androidx SQLiteDriver)

Covered in depth in the `androidx-sqlite` skill's
`references/third-party-drivers.md` — notably
powersync-kotlin's bundled-driver clone over a SQLite fork
(structurally identical to our task) and danysantiago's
androidx-driver-samples.

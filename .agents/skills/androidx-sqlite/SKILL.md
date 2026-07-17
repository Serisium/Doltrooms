---
name: androidx-sqlite
description: The androidx.sqlite SQLiteDriver API — the exact three-interface contract (SQLiteDriver, SQLiteConnection, SQLiteStatement) this project implements over libdoltlite (ARCHITECTURE.md D1). Use when implementing or reviewing driver code, deciding hasConnectionPool, mapping driver methods to sqlite3_* calls, matching BundledSQLiteDriver/NativeSQLiteDriver behavior (error format, SQLITE_TRANSIENT, extended result codes, idempotent close), choosing artifacts (androidx.sqlite:sqlite, sqlite-bundled, sqlite-framework, sqlite-async, sqlite-web), or studying third-party custom drivers. Triggers: SQLiteDriver, SQLiteConnection, SQLiteStatement, androidx.sqlite, sqlite-bundled, BundledSQLiteDriver, NativeSQLiteDriver, hasConnectionPool, throwSQLiteException, SQLiteException, step reset clearBindings, bind index, getColumnType, execSQL, sqlite_bindings.cpp, driver implementation.
---

# androidx.sqlite — the driver contract we implement

## What this skill is

The `androidx.sqlite` driver API surface, verified 2026-07-17 against
androidx-main source and Maven metadata. Current stable: **2.7.0**
(released 2026-07-01, same day as Room 3.0.0)
(https://developer.android.com/jetpack/androidx/releases/sqlite).

Artifacts: `androidx.sqlite:sqlite` (the interfaces),
`sqlite-bundled` (`BundledSQLiteDriver`), `sqlite-framework`
(`AndroidSQLiteDriver`, `NativeSQLiteDriver`), `sqlite-async`
(suspend bridges, new in 2.7.0), `sqlite-web`
(`WebWorkerSQLiteDriver`, new in 2.7.0). `sqlite-ktx` is an **empty
compatibility artifact** — don't depend on it (its build.gradle says
"empty artifact maintained for compatibility",
https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-ktx).

## Role in this project

Our driver is an implementation of these three interfaces over
`libdoltlite` (ARCHITECTURE.md D1), structurally a re-skin of
`BundledSQLiteDriver`. Implement against the **nonWeb actuals**: since
2.7.0 the interfaces are `expect` split across `commonMain` (shared
subset), `nonWebMain` (synchronous `open`/`prepare`/`step` — Android/
JVM/native), and `webMain` (same but `suspend`). In androidx's
`commonMain`, `SQLiteDriver` has no `open`, `SQLiteConnection` no
`prepare`, `SQLiteStatement` no `step`.

## The contract in one screen

- `SQLiteDriver`: `open(fileName): SQLiteConnection` +
  `hasConnectionPool: Boolean` (default **false**). The KDoc contract:
  a driver with an internal pool "should be capable of opening
  connections that are safe to be used in a multi-thread and
  concurrent environment whereas a driver that does not … will
  require the application to manage connections in a thread-safe
  manner"
  (https://github.com/androidx/androidx/blob/androidx-main/sqlite/sqlite/src/commonMain/kotlin/androidx/sqlite/SQLiteDriver.kt).
  Leave it `false` and let Room pool/confine (see the `room3` skill);
  flipping it to `true` puts Room in passthrough mode and exposes your
  connections to concurrent use.
- `SQLiteConnection : AutoCloseable`: `prepare(sql)`,
  `inTransaction()` (default body **throws NotImplementedError** —
  implement it as `sqlite3_get_autocommit(db) == 0`), `close()`
  (idempotent no-op when already closed).
- `SQLiteStatement : AutoCloseable`: `bind*` (**1-based**), `get*` /
  `isNull` / column metadata (**0-based**), `step(): Boolean`
  (`SQLITE_ROW`→true, `SQLITE_DONE`→false, else throw), `reset()`
  (retains bindings), `clearBindings()` (unset = NULL), `close()`
  (idempotent). Defaulted members delegate: `bindFloat→bindDouble`,
  `bindInt/bindBoolean→bindLong`, `getFloat/getInt/getBoolean` from
  the wide getters, `getColumnNames` from count+name.
- Column-type constants live in `androidx.sqlite.SQLite.kt`:
  `SQLITE_DATA_INTEGER=1, FLOAT=2, TEXT=3, BLOB=4, NULL=5`.
- **Errors**: throw via `androidx.sqlite.throwSQLiteException(code,
  msg)` — message format `"Error code: N, message: …"`. The
  `SQLiteException` constructor is `@RestrictTo`; on Android the type
  aliases to `android.database.SQLException`. Reference impls
  range-check columns (`SQLITE_RANGE` "column index out of range"),
  guard no-row reads via `sqlite3_stmt_busy` (`SQLITE_MISUSE`
  "no row"), throw `SQLITE_MISUSE` "connection/statement is closed"
  after close, and map `SQLITE_NOMEM` to `OutOfMemoryError`.
- `execSQL` is literally `prepare(sql).use { it.step() }` — the
  driver must tolerate `step()` on DDL/DML. `use` is stdlib
  `AutoCloseable.use`, not an androidx API.

**Minimal abstract set to implement** (everything else defaults):
`bindBlob, bindDouble, bindLong, bindText, bindNull, getBlob,
getDouble, getLong, getText, isNull, getColumnCount, getColumnName,
getColumnType, step, reset, clearBindings, close` + `prepare`/
connection-`close` + `open`.

## Reference-implementation behaviors to copy

From `sqlite_bindings.cpp` / `NativeSQLiteStatement.kt` (routing in
`references/bundled-driver-internals.md`):

1. Open with `sqlite3_open_v2`, then **enable extended result codes**
   (`sqlite3_extended_result_codes(db, 1)`) — Room's error handling
   expects them.
2. Bind text/blobs with **`SQLITE_TRANSIENT`** — Kotlin arrays aren't
   pinned past the call.
3. Reference impls use UTF-16 entry points (`prepare16_v2`,
   `bind_text16`, `column_text16`) because Kotlin strings are UTF-16 —
   an implementation choice, not contract.
4. Bundled compiles SQLite with `-DSQLITE_THREADSAFE=2` (multi-thread
   mode): "connections opened by the driver are NOT thread-safe" —
   Room's pool provides the confinement.
5. Statement/connection `close()` are idempotent via a closed flag;
   `finalize`/`close_v2` under the hood.

## When to load reference files

- Verbatim interface declarations (all three, nonWeb actuals, KDoc)
  plus constants/helpers/open flags:
  `references/driver-interfaces.md`.
- BundledSQLiteDriver internals — repo paths, the method→`sqlite3_*`
  call map, error-checking patterns, compile flags:
  `references/bundled-driver-internals.md`.
- Third-party custom drivers worth studying (powersync's fork
  re-skin, danysantiago's samples, SQLCipher/Sentry patterns):
  `references/third-party-drivers.md`.

## Authoritative URLs

- Releases: https://developer.android.com/jetpack/androidx/releases/sqlite
- KMP guide + raw driver sample: https://developer.android.com/kotlin/multiplatform/sqlite
- API reference: https://developer.android.com/reference/kotlin/androidx/sqlite/package-summary
- Interfaces source: https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite/src
- Bundled driver: https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-bundled
- Native driver impl: https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-framework/src/nativeMain/kotlin/androidx/sqlite/driver
- Version ground truth: `https://dl.google.com/android/maven2/androidx/sqlite/<artifact>/maven-metadata.xml`

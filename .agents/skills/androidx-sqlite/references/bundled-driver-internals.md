# BundledSQLiteDriver internals — the re-skinning template

Verified 2026-07-17 against androidx-main. All paths under
https://github.com/androidx/androidx/tree/androidx-main/.

## Repo map

- `sqlite/sqlite-bundled/src/commonMain/kotlin/androidx/sqlite/driver/bundled/`
  — `expect class BundledSQLiteDriver/Connection/Statement`,
  `BundledSQLite.kt` (open flags), `NativeLibraryLoader.kt`.
- `sqlite/sqlite-bundled/src/jvmAndAndroidMain/kotlin/…/BundledSQLiteDriver.jvmAndAndroid.kt`
  — JNI-backed actuals (`private external fun nativeOpen(name:
  String, openFlags: Int): Long` etc.).
- `sqlite/sqlite-bundled/src/jvmAndAndroidMain/jni/sqlite_bindings.cpp`
  — the entire C++ side.
- `sqlite/sqlite-bundled/src/nativeMain/…` — **just typealiases**:
  `actual typealias BundledSQLiteDriver =
  androidx.sqlite.driver.NativeSQLiteDriver`; the real cinterop
  implementation lives in **sqlite-framework**:
  `sqlite/sqlite-framework/src/nativeMain/kotlin/androidx/sqlite/driver/NativeSQLiteDriver.kt`,
  `NativeSQLiteConnection.kt`, `NativeSQLiteStatement.kt`.
- `sqlite/sqlite-bundled/src/nativeInterop/cinterop/androidXBundledSqlite.def`
  — trivially small: `package = androidx.sqlite3.bundled`,
  `headers = sqlite3.h`, `linkerOpts.linux/osx = -lpthread -ldl`,
  `noStringConversion = sqlite3_prepare_v2 sqlite3_prepare_v3`.
- `sqlite/sqlite-bundled/build.gradle` — SQLite **3.50.1**
  amalgamation compiled per KonanTarget; JNI lib name `sqliteJni`,
  loaded lazily on first `open()`.

Compile flags (build.gradle): `HAVE_USLEEP=1,
SQLITE_DEFAULT_AUTOVACUUM=1, SQLITE_DEFAULT_MEMSTATUS=0,
SQLITE_DEFAULT_WAL_SYNCHRONOUS=1, SQLITE_ENABLE_COLUMN_METADATA,
SQLITE_ENABLE_FTS3/FTS3_PARENTHESIS/FTS4/FTS5, SQLITE_ENABLE_JSON1,
SQLITE_ENABLE_MATH_FUNCTIONS, SQLITE_ENABLE_NORMALIZE,
SQLITE_ENABLE_RTREE, SQLITE_ENABLE_STAT4, SQLITE_HAVE_ISNAN,
SQLITE_OMIT_BUILTIN_TEST, SQLITE_OMIT_DEPRECATED,
SQLITE_OMIT_PROGRESS_CALLBACK, SQLITE_OMIT_SHARED_CACHE,
SQLITE_SECURE_DELETE, SQLITE_TEMP_STORE=3, **SQLITE_THREADSAFE=2**`;
`-Oz` on Android, `-O3` elsewhere.

## Method → sqlite3 call map (JNI and native implement identically)

| Kotlin API | C call | Error behavior |
|---|---|---|
| `Driver.open` | `sqlite3_open_v2(path, &db, flags, nullptr)` then `sqlite3_extended_result_codes(db, 1)` then `sqlite3_db_config(db, SQLITE_DBCONFIG_ENABLE_LOAD_EXTENSION, 1, 0)` | throw on `rc != SQLITE_OK` (no errmsg — db may be null) |
| `Driver.threadingMode` | `sqlite3_threadsafe()` | — |
| `Connection.prepare` | `sqlite3_prepare16_v2` (UTF-16) | throw with `sqlite3_errmsg(db)` |
| `Connection.inTransaction` | `sqlite3_get_autocommit(db) == 0` | closed → `SQLITE_MISUSE` "connection is closed" |
| `Connection.close` | `sqlite3_close_v2(db)` | idempotent (`isClosed` flag) |
| `bindBlob` | `sqlite3_bind_blob(stmt, index, blob, len, SQLITE_TRANSIENT)` | rc != OK → throw with errmsg |
| `bindDouble` / `bindLong` | `sqlite3_bind_double` / `sqlite3_bind_int64` | " |
| `bindText` | `sqlite3_bind_text16(…, SQLITE_TRANSIENT)` | " |
| `bindNull` | `sqlite3_bind_null` | " |
| `step` | `sqlite3_step`: `SQLITE_ROW`→true, `SQLITE_DONE`→false, else throw with errmsg | |
| `getBlob` | `sqlite3_column_blob` + `sqlite3_column_bytes` (copy) | pre-checks below; NOMEM → `OutOfMemoryError` |
| `getDouble` / `getLong` | `sqlite3_column_double` / `sqlite3_column_int64` | pre-checks below |
| `getText` | `sqlite3_column_text16` (+ `column_bytes16` on JNI) | pre-checks; NOMEM → OOM |
| `isNull` | `getColumnType(index) == SQLITE_NULL` (native) | |
| `getColumnCount` | `sqlite3_column_count` | works without a row |
| `getColumnName` | `sqlite3_column_name` (JNI) / `column_name16` (native); null → `OutOfMemoryError` | column-range check only |
| `getColumnType` | `sqlite3_column_type` | pre-checks below |
| `reset` / `clearBindings` | `sqlite3_reset` / `sqlite3_clear_bindings` | rc != OK → throw |
| `Statement.close` | `sqlite3_finalize` | idempotent |
| `addExtension` → at open | `sqlite3_load_extension(db, file, entry, &errMsg)`; native guards with `sqlite3_compileoption_used("OMIT_LOAD_EXTENSION")` | failure closes the just-opened connection and rethrows |

## Error-checking pattern (copy this)

```c
// sqlite_bindings.cpp
static bool throwIfNoRow(JNIEnv *env, sqlite3_stmt *stmt) {
    if (sqlite3_stmt_busy(stmt) == 0) {
        return throwSQLiteException(env, SQLITE_MISUSE, "no row");
    } ...
static bool throwIfInvalidColumn(JNIEnv *env, sqlite3_stmt *stmt, int index) {
    if (index < 0 || index >= sqlite3_column_count(stmt)) {
        return throwSQLiteException(env, SQLITE_RANGE, "column index out of range");
    } ...
```

Plus: `throwIfClosed()` → `SQLITE_MISUSE`
"connection is closed"/"statement is closed" on every method after
close (Kotlin/Native uses a `@Volatile var isClosed`);
`sqlite3_errcode(db) == SQLITE_NOMEM` → `OutOfMemoryError`; exception
message format exactly `"Error code: %d, message: %s"` (the C++
replicates `throwSQLiteException`).

Behavioral notes surfaced by the pre-checks:

- `getColumnType`/value getters are valid only while positioned on a
  row (else `SQLITE_MISUSE` "no row" via `sqlite3_stmt_busy`);
  `getColumnCount`/`getColumnName` work any time the statement is
  open.
- Bundled's KDoc: with `SQLITE_THREADSAFE=2`, "connections opened by
  the driver are NOT thread-safe" — confinement is the pool's job.

## DoltLite adaptation checklist

1. Swap the SQLite amalgamation for the DoltLite release amalgamation
   (`doltlite` skill, artifacts reference); include `doltlite.h`; the
   `sqlite3_*` calls in the table above are unchanged.
2. Keep `SQLITE_TRANSIENT`, extended-result-codes-at-open, the error
   message format, idempotent closes, and the pre-check trio.
3. Drop or gate `sqlite3_load_extension` support until DoltLite's
   behavior is verified.
4. Re-evaluate every compile flag against DoltLite's build — its
   autoconf build may not accept androidx's flag set
   (`sqlite-c-api` fork-divergence watchlist).

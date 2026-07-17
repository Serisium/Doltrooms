# Testing Room 3 and a custom driver across platforms

Verified 2026-07-17. Relevant to ARCHITECTURE.md D4: "Validation at
every rung is running existing Room test suites against the new
driver."

## room3-testing is multiplatform

`androidx.room3:room3-testing` has source sets `commonMain`,
`androidMain`, `jvmMain`, `nativeMain`, `webMain`.
`MigrationTestHelper` is an `expect class` in commonMain
(https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:room3/room3-testing/src/commonMain/kotlin/androidx/room3/testing/MigrationTestHelper.kt):

- `createDatabase(version)` returns a `SQLiteConnection`;
  `runMigrationsAndValidate(version, migrations)` validates against
  exported schemas.
- It uses `androidx.sqlite.async.execSQL/prepare/step`, so tests are
  suspend tests. Requires `exportSchema` and the Gradle plugin's
  `schemaDirectory` (`kmp-setup-and-builder.md`).

## Driver validation pattern (no published conformance kit)

No driver test kit is published as an artifact (verified absence:
not on the sqlite or room3 release pages). The working pattern:

1. Plain `kotlin.test` + `runTest` in `commonTest`.
2. Build with `Room.inMemoryDatabaseBuilder<Db>()` (context-free) or
   a temp-file path, `.setDriver(<driver under test>)`.
3. Run suspend DAO calls; the same test source executes on JVM, iOS
   simulator, native, and (if targeted) web.
4. **Differential testing**: run the identical suite against
   `BundledSQLiteDriver` and diff behavior — the practical
   conformance check for our DoltLite driver.

Google's own conformance suites live unpublished in androidx
(`sqlite/sqlite-*-test` modules, e.g. the web-worker tests under
https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:sqlite/)
— good templates for statement/connection contract tests.

The classic instrumented-test doc
(https://developer.android.com/training/data-storage/room/testing-db)
is Room 2-era; prefer common-code tests for Room 3/KMP.

## DoltLite-specific test targets

Beyond stock conformance, the driver suite should probe the
fork-divergence watchlist (`sqlite-c-api` skill): journal-mode pragma
results, `PRAGMA user_version` round-trip, `last_insert_rowid` on
non-INTEGER-PK tables (DoltLite stores them WITHOUT ROWID — `doltlite`
skill), busy behavior under Room's 4-reader/1-writer WAL pool, and
`dolt_*` calls through `useWriterConnection`.

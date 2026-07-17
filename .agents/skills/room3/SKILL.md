---
name: room3
description: Reference for Room 3 (androidx.room3), the Kotlin Multiplatform release of Room that this project's driver plugs into (ARCHITECTURE.md D1). Use when configuring Room 3 Gradle/KSP wiring (androidx.room3:room3-runtime, room3-compiler, the androidx.room3 plugin, schemaDirectory), building a RoomDatabase with setDriver/setQueryCoroutineContext/journal modes and connection pools, reaching raw or vendor SQL (useWriterConnection, useReaderConnection, Transactor, usePrepared, withWriteTransaction, @RawQuery), fighting compile-time query verification of dolt_* functions (@SkipQueryVerification, DatabaseVerifier), writing KMP tests with room3-testing/MigrationTestHelper, or answering what changed from Room 2.x (coroutine-first, SupportSQLite removal, js/wasmJs targets). Triggers: Room 3, room3, RoomDatabase, setDriver, DAO, @Query, @RawQuery, @SkipQueryVerification, useWriterConnection, Transactor, InvalidationTracker, KSP, schemaDirectory, hasConnectionPool, SQLITE_BUSY, MigrationTestHelper.
---

# Room 3 — the consumer of our driver

## What this skill is

Room 3.0.0 (stable **2026-07-01**; alphas from 2026-03-11) is the KMP
rewrite of Room, verified 2026-07-17 against
https://developer.android.com/jetpack/androidx/releases/room3 and
androidx-main source. It lives in a **new Maven group and package** so
it can coexist with Room 2.x:

> "androidx.room:room-runtime has become androidx.room3:room3-runtime
> and classes such as androidx.room.RoomDatabase will now be located
> at androidx.room3.RoomDatabase."
> (https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html)

Room 2.x (currently 2.8.4) is in maintenance mode. Room 3 is
KSP-only, generates Kotlin only, and pairs with androidx.sqlite
2.7.0 (`androidx-sqlite` skill).

## Role in this project

Room 3 is consumed unmodified (ARCHITECTURE.md D1). Three pillars
matter to us:

1. **Driver-only architecture** — `SupportSQLiteDatabase`/
   `SupportSQLiteOpenHelper`/`Cursor` are gone; "A `SQLiteDriver` is
   now required to build a `RoomDatabase`"
   (https://developer.android.com/jetpack/androidx/releases/room3).
   Our driver slots in via `RoomDatabase.Builder.setDriver(...)`.
2. **Coroutine-first** — "Room 3.0 disallows blocking DAO functions"
   (https://www.infoq.com/news/2026/04/room-3-kotlin-async-sqlite/);
   DAO functions are `suspend` or reactive; executors replaced by
   `setQueryCoroutineContext` (default `Dispatchers.IO`).
3. **Full KMP** — Android, JVM, iOS, macOS, tvOS, watchOS, Linux,
   Windows, plus new `js`/`wasmJs` (async-only; "once a project
   targets web then only the asynchronous APIs are available",
   https://developer.android.com/jetpack/androidx/releases/sqlite).

## The three facts that shape the driver work

### 1. Room owns the connection pool (unless told otherwise)

With `hasConnectionPool = false` (our default — `androidx-sqlite`
skill), Room pools over `driver.open(fileName)`: journal mode
`WRITE_AHEAD_LOGGING` (the default) gets "four reader and one writer"
connections; `TRUNCATE` gets one; in-memory forces one; acquisition
timeout 30s. "Be aware that if multiple writers are used then
database operations might fail with `SQLITE_BUSY` errors"
(`RoomDatabase.kt` KDoc, androidx-main). Connections are
coroutine-confined — used by one coroutine at a time but potentially
on different threads across suspensions: connections need not be
concurrently thread-safe but **must not be thread-affine**. A driver
reporting `true` flips Room into `PassthroughConnectionPool` (one
connection, no management, concurrent use) — don't, unless the driver
truly pools.

### 2. Vendor SQL goes through raw connections

The sanctioned path for `dolt_commit()` and friends:

```kotlin
db.useWriterConnection { transactor ->
    transactor.usePrepared("SELECT dolt_commit('-a', '-m', ?)") { stmt ->
        stmt.bindText(1, message); stmt.step(); stmt.getText(0)
    }
}
```

`useWriterConnection` refreshes the `InvalidationTracker` afterward,
so Flow observers see raw-SQL writes. Readers allow only DEFERRED
transactions; `@RawQuery` "can only be used for read queries" — a
write-in-select like `dolt_commit` belongs on the writer connection.
Full API surface: `references/raw-connections-and-transactions.md`.

### 3. `@Query("SELECT dolt_commit(...)")` fails compilation by default

The Room compiler verifies every `@Query` by preparing it against a
real in-memory **stock SQLite** via sqlite-jdbc
(`DatabaseVerifier.kt`: `jdbc:sqlite::memory:`) — unknown functions
error out ("There is a problem with the query: …"). The escape is
`@SkipQueryVerification` (per function, DAO, or Database). Details,
quotes, and the CI temp-dir pitfall:
`references/query-verification.md`.

## Gotchas

- **Docs lag:** https://developer.android.com/kotlin/multiplatform/room
  still shows Room 2.8.4 coordinates/plugin (`androidx.room`,
  `room { }`). Room 3 uses `androidx.room3:*`, plugin id
  `androidx.room3`, extension `room3 { }`. The release page is the
  authority.
- **Room emits SQLite-dialect DDL and assumes stock-SQLite behavior**
  (~3.50 via the verifier); DoltLite divergence must be handled below
  Room or via skipped-verification queries.
- Migration bridge from Room 2: `room3-sqlite-wrapper`'s
  `getSupportWrapper()`; `runInTransaction { }` →
  `withWriteTransaction { }`.
- `InvalidationTracker.Observer` is gone — use
  `invalidationTracker.createFlow("Table")`.

## When to load reference files

- Gradle/KSP wiring, artifact list, schemaDirectory, builder options
  (`setDriver`, `setQueryCoroutineContext`, pool sizing):
  `references/kmp-setup-and-builder.md`.
- `useWriterConnection`/`useReaderConnection`, `Transactor`,
  `TransactionScope`, `@RawQuery` — full verbatim signatures and
  constraints: `references/raw-connections-and-transactions.md`.
- Compile-time query verification internals and
  `@SkipQueryVerification`: `references/query-verification.md`.
- Testing our driver on every platform (room3-testing,
  MigrationTestHelper, common-test patterns):
  `references/testing.md`.

## Authoritative URLs

- Release notes: https://developer.android.com/jetpack/androidx/releases/room3
- Announcement: https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- androidx.sqlite releases: https://developer.android.com/jetpack/androidx/releases/sqlite
- KMP setup guide (Room 2-era wiring): https://developer.android.com/kotlin/multiplatform/room
- Driver guide: https://developer.android.com/kotlin/multiplatform/sqlite
- Raw-connection APIs: https://developer.android.com/training/data-storage/room/room-kmp-migration
- Source: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:room3/

# Room 3 KMP setup and RoomDatabase.Builder

Verified 2026-07-17. Coordinates from
https://developer.android.com/jetpack/androidx/releases/room3 — the
KMP guide (https://developer.android.com/kotlin/multiplatform/room)
still shows Room 2.8.4 wiring; translate its layout to these
coordinates.

## Dependencies and plugin

```kotlin
dependencies {
    val room_version = "3.0.0"
    implementation("androidx.room3:room3-runtime:$room_version")
    ksp("androidx.room3:room3-compiler:$room_version")
}

// top-level
plugins { id("androidx.room3") version "$room_version" apply false }
// module
plugins { id("androidx.room3") }
room3 { schemaDirectory("$projectDir/schemas") }
```

- `schemaDirectory` is **required** when using the Room Gradle
  plugin; schemas export into flavored folders
  (`schemas/<variant>/<pkg>.MyDatabase/1.json`).
- KSP plugin id `com.google.devtools.ksp`; since KSP 2.3.0 the version
  scheme is plain `2.3.x` (e.g. `2.3.10`, released 2026-07-09; verified
  on Maven Central 2026-07-17), no longer the older
  `<kotlinVersion>-<kspVersion>` compound that
  https://kotlinlang.org/docs/ksp-quickstart.html still shows. Kotlin
  2.3.10 + KSP 2.3.10 + room3-compiler 3.0.0 build green together
  (verified in-repo 2026-07-18, implementation Step 4).
- KMP compiler wiring is per-target:

```kotlin
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    // …one per target
}
```

with `commonMain.dependencies { implementation(room3-runtime);
implementation(<driver artifact>) }`.

- Test-only Room (entities/DAOs in `commonTest`, as in this repo's
  differential suite) uses the per-target TEST configurations instead:
  `kspJvmTest`, `kspLinuxX64Test`, `kspIosArm64Test`,
  `kspIosSimulatorArm64Test`, and — under the AGP KMP library plugin —
  `kspAndroidHostTest` / `kspAndroidDeviceTest` (names verified
  in-repo 2026-07-18 via `gradle :library:dependencies`; implementation
  Step 4). An `expect object` `RoomDatabaseConstructor` referenced
  from `@ConstructedBy` in commonTest then gets its KSP-generated
  `actual` in every target's test compilation.

Sibling artifacts confirmed in androidx-main: `room3-common`,
`room3-compiler`, `room3-runtime`, `room3-testing`,
`room3-migration`, `room3-paging`, `room3-guava`, `room3-livedata`,
`room3-rxjava3`, `room3-sqlite-wrapper`, `room3-gradle-plugin`.

## Builder (commonMain)

```kotlin
Room.databaseBuilder<AppDb>(name = dbFilePath)   // context-free since 3.0.0-alpha03
    .setDriver(myDriver)                          // REQUIRED in Room 3
    .setQueryCoroutineContext(Dispatchers.IO)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .setSingleConnectionPool()                    // or setMultipleConnectionPool(r, w)
    .addMigrations(...)
    .addCallback(...)
    .build()

Room.inMemoryDatabaseBuilder<AppDb>()             // also context-free
```

KDoc contracts (RoomDatabase.kt, androidx-main —
https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomDatabase.kt):

- `setDriver`: "Sets the [SQLiteDriver] implementation to be used by
  Room to open database connections."
- Pool sizing: "If neither this function or
  [setMultipleConnectionPool] are called then Room will default to a
  connection pool configuration that is based on the [JournalMode].
  For [JournalMode.TRUNCATE] a single connection is used, while for
  [JournalMode.WRITE_AHEAD_LOGGING] multiple connections are used,
  four reader and one writer." And: "Be aware that if multiple
  writers are used then database operations might fail with
  `SQLITE_BUSY` errors. These must be handled by the callers and
  might be mitigated by configuring the `busy_timeout`."
- Pool bypass: "A connection pool is only used if the supplied
  `SQLiteDriver` has no internal pool, i.e.
  `SQLiteDriver.hasConnectionPool` returns `false`. If the configured
  driver has an internal pool then calling this function has no
  effect."
- `setQueryCoroutineContext`: "Sets the [CoroutineContext] that will
  be used to execute all asynchronous queries and tasks… If no
  [CoroutineDispatcher] is present in the [context] then this
  function will throw an [IllegalArgumentException]". Default is
  `Dispatchers.IO`
  (https://developer.android.com/training/data-storage/room/room-kmp-migration).

Pool internals: acquisition timeout is 30 seconds
(`ConnectionPoolImpl.kt`: `internal var timeout = 30.seconds`); a
driver with `hasConnectionPool = true` gets
`PassthroughConnectionPool` — "An implementation of a connection pool
that doesn't do any connection management" — issuing transactions as
plain `BEGIN DEFERRED/IMMEDIATE/EXCLUSIVE TRANSACTION` SQL. Web
targets are "restricted to single connection pool to avoid 'database
locked' issues" (release page).

## Shipped drivers (for comparison testing)

From https://developer.android.com/kotlin/multiplatform/sqlite:

| Driver | Artifact | Platforms |
|---|---|---|
| `AndroidSQLiteDriver` | `androidx.sqlite:sqlite-framework` | Android |
| `NativeSQLiteDriver` | `androidx.sqlite:sqlite-framework` | iOS, Mac, Linux |
| `BundledSQLiteDriver` | `androidx.sqlite:sqlite-bundled` | Android, iOS, Mac, Linux, JVM |
| `WebWorkerSQLiteDriver` | `androidx.sqlite:sqlite-web` | js/wasmJs (SQLite-WASM + OPFS) |

"The recommended implementation to use is `BundledSQLiteDriver`" —
also our behavioral reference to diff the DoltLite driver against.

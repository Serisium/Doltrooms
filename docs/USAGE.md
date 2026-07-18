# Using doltrooms

doltrooms is a [Room 3](https://developer.android.com/kotlin/multiplatform/room)
`SQLiteDriver` backed by [DoltLite](https://github.com/dolthub/doltlite),
DoltHub's SQLite fork with Git-style version control. You use Room
exactly as you would over `BundledSQLiteDriver`; on top of that, the
`dev.seri.doltrooms.dolt` package gives you typed commit / branch /
merge / diff / sync helpers that are plain `SELECT dolt_*(...)` SQL
underneath.

Coordinates: `dev.seri.doltrooms:doltrooms` (Maven Central once
published; `publishToMavenLocal` works today). The library `api`-exposes
`androidx.sqlite:sqlite` and `androidx.room3:room3-runtime` — you do not
need to redeclare them.

```kotlin
// commonMain
dependencies {
    implementation("dev.seri.doltrooms:doltrooms:<version>")
}
```

## Opening a Room database with the driver

Room 3's builder is context-free and requires a driver:

```kotlin
import androidx.room3.Room
import dev.seri.doltrooms.driver.DoltLiteDriver

val db = Room.databaseBuilder<AppDatabase>(name = dbFilePath)
    .setDriver(DoltLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

// In-memory works too — including all dolt_* versioning features:
val mem = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(DoltLiteDriver())
    .build()
```

`AppDatabase` is an ordinary Room 3 KMP database (`@Database` +
`@ConstructedBy(AppDatabaseConstructor::class)` with an
`expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>`;
KSP generates the actuals).

### Per-platform notes

| Platform | How the engine arrives | Notes |
|---|---|---|
| JVM desktop | `libdoltroomsjni.so` is extracted from the JAR (`natives/<os>-<arch>/`) to a temp file and loaded on first use | Override with `-Ddev.seri.doltrooms.lib.path=/abs/path/to/lib`. Currently packaged: `linux-x64`. |
| Android | The AAR ships `jni/<abi>/libdoltroomsjni.so` for `arm64-v8a` and `x86_64`, NDK-built from the same pinned DoltLite amalgamation | minSdk 24. 16 KB page alignment (NDK r28). |
| Linux x64 (Kotlin/Native) | The klib embeds a static `libdoltlite.a` — binaries link self-contained | The final executable additionally links `libcrypt.so.1` (ships on most distros; Fedora needs `libxcrypt-compat`). |
| iOS | Declared targets (`iosArm64`, `iosSimulatorArm64`) | Artifacts must be built and published from a macOS host and are not yet verified — see `deferred-verification.md`. |

Every platform runs the same engine version: one DoltLite pin,
compiled from the release amalgamation by this build (no upstream
prebuilt binaries).

> **Kotlin/Native warning — do not link a second SQLite into the same
> binary.** doltrooms' klib embeds a static `libdoltlite.a` exporting
> the standard `sqlite3_*` symbols. If another dependency of your
> native binary also statically links a SQLite engine (e.g.
> `androidx.sqlite:sqlite-bundled`), the linker silently resolves
> *both* drivers to whichever archive it reads first — one of the two
> engines is not the one you think it is (first symptom: `no such
> function: dolt_version`). Keep exactly one SQLite engine per native
> binary. JVM and Android are unaffected (separate dynamic libraries).

## The dolt_* tour

`DoltDatabase` wraps any `RoomDatabase` opened over `DoltLiteDriver`:

```kotlin
import dev.seri.doltrooms.dolt.DoltDatabase

val dolt = DoltDatabase(db)

// Commit the current state (stages everything by default), inspect history:
val hash = dolt.commit("initial rows")
val history: List<DoltCommit> = dolt.log()
val pending: List<DoltStatusEntry> = dolt.status()

// Branch, switch, merge:
dolt.branch("feature")
dolt.checkout("feature")            // or checkout("new-branch", create = true)
db.personDao().insert(Person(name = "Ada", age = 36))
dolt.commit("add Ada on feature")
dolt.checkout("main")
val mergedHead = dolt.merge("feature")   // ff or 3-way; conflicts throw

// Typed row-level diff between any two refs ("WORKING" is valid):
val rows: List<DoltDiffRow> = dolt.diff("Person", from = "main", to = "feature")
```

Semantics you must know (probed against the pinned DoltLite version,
each pinned by a test):

- **Branch state is per-connection.** All helpers run on Room's single
  writer connection for a consistent view, but Room's *reader*
  connections do not follow a checkout — DAO reads keep seeing `main`.
  Branch-and-read workflows should either read through helpers/raw
  writer-connection SQL or accept main-branch reads. Nothing persists
  across reopen: a reopened database is on `main`.
- **Never wrap dolt_* calls in your own transaction.** `dolt_commit`
  self-commits and *ends* any open transaction (the helpers already do
  the right thing; don't call them inside `db.useWriterConnection`'s
  `immediateTransaction`).
- **Conflicted merges** throw a `SQLiteException` and roll back in
  autocommit mode. Inside an explicit transaction the transaction is
  left open with `dolt_conflicts` populated for resolution (recipe in
  the `DoltDatabase` KDoc).
- A dolt-less engine (e.g. `BundledSQLiteDriver`) throws a clean
  `SQLiteException` from any helper; the database stays usable.

### dolt_* SQL in your own DAOs

Room verifies `@Query` SQL against stock SQLite at compile time, which
doesn't know the `dolt_*` functions — skip verification for those:

```kotlin
@SkipQueryVerification
@Query("SELECT dolt_version()")
suspend fun doltVersion(): String
```

## Remotes and sync

Replication runs DoltLite's own remote protocol against `file://`
paths or a `doltlite-remotesrv` over **plain http on trusted networks
only** — the amalgamation this library builds from excludes the
TLS/credential stack, so `https://` URLs are rejected at first use.

```kotlin
// Publish:
dolt.addRemote("origin", "file:///backups/app-remote")   // or http://host:port/repo
dolt.commit("first release")
dolt.push("origin", "main")                              // push(force = true) overwrites

// Bootstrap a replica — clone needs a FRESH file, so it runs on a raw
// driver connection BEFORE Room ever opens the path:
DoltDatabase.clone(DoltLiteDriver(), "file:///backups/app-remote", replicaPath)
val replica = Room.databaseBuilder<AppDatabase>(name = replicaPath)
    .setDriver(DoltLiteDriver()).build()

// Stay in sync:
DoltDatabase(replica).pull("origin", "main")   // fetch + merge, merge semantics apply
dolt.fetch("origin")                            // materializes origin/<branch> refs
```

Sync caveats (each pinned by a test):

- `<remote>/<branch>` refs resolve only after the first `fetch` — not
  even right after `clone`.
- Non-fast-forward pushes are rejected; `push(force = true)` overwrites.
- A conflicted `pull` throws and rolls back, like `merge`.
- **AUTOINCREMENT keys are merge-hostile across replicas:** concurrent
  inserts on two replicas mint the same rowid, and a shared PK with
  different values is a row conflict on pull/merge. Syncing apps need
  collision-free keys (UUIDs, client-scoped ranges, …).

## Known divergences from stock SQLite

DoltLite is a fork; these differences are permanent, documented
contract, each asserted by a test in this repo:

| # | Behavior | DoltLite | Stock SQLite / `BundledSQLiteDriver` |
|---|---|---|---|
| 1 | Unopenable path (e.g. missing parent dir) with `READWRITE \| CREATE` | `open` succeeds; `SQLITE_CANTOPEN` (14) surfaces at the first statement *step* | Fails eagerly at open |
| 2 | Default journal mode on file databases | `wal` | `delete` |
| 3 | Tables without an INTEGER PRIMARY KEY | Stored WITHOUT ROWID: `last_insert_rowid()` returns 0, `SELECT rowid` errors | Rowid-backed; both work |

(A fourth build-level difference: two of `BundledSQLiteDriver`'s
compile flags — `SQLITE_OMIT_SHARED_CACHE`,
`SQLITE_DEFAULT_WAL_SYNCHRONOUS=1` — don't compile against the fork
and are dropped. No observable behavior difference resulted: both
engines report `PRAGMA synchronous` = 2 (FULL) after
`journal_mode=WAL`.)

Row 3 is why your syncing entities should use INTEGER
(`autoGenerate`-style) or deliberately chosen explicit primary keys:
Room's generated `@Insert` return values rely on `last_insert_rowid()`,
which only behaves on rowid-backed (INTEGER-PK) tables.

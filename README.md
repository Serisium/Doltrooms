# Doltrooms
Doltrooms: a [Room3](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) SQLiteDriver backed by [DoltLite](https://www.dolthub.com/#doltlite).
## Status
Doltrooms has a completed first-revision of an implementation which passes the Room3 test suite— running `DoltLiteDriver` instead of `BundledSQLiteDriver`. This gives your Room database git-like revision control and remote synchronization, all while keeping multiplatform targets and familar Room interfaces.

Sample applications have been adapted from the [Room3 Codelabs project](https://developer.android.com/codelabs/kmp-migrate-room?hl=en#0) and are stored in [Fruitties sample](samples/codelab/).

Next, I plan to implement Dolt’s git-like interface and [remotesrv](https://github.com/dolthub/doltlite#remotes) synchronization.

| Architecture                  | Status                                           | Engine size                  | Stock SQLite size¹ | Min OS / API                   |
|-------------------------------|--------------------------------------------------|------------------------------|--------------------|--------------------------------|
| Android · arm64-v8a           | ✅ Verified on a physical phone (52 + 10 tests)   | 3.1 MB `.so` (2.9 MB in APK) | 1.3 MB             | API 24+ (16 KB page-aligned)   |
| Android · x86_64              | ✅ Verified in CI (emulator, 52 + 10 tests)       | 3.1 MB `.so` (2.9 MB in APK) | 1.2 MB             | API 24+ (16 KB page-aligned)   |
| iOS · arm64 (device)          | ✅ Verified on a physical iPad (52 tests + sample app run) | 3.9 MB static `.a`  | 2.3 MB             | iOS 12.0+                      |
| iOS Simulator · arm64         | ✅ Verified (52 tests + sample app run)           | 4.0 MB static `.a`           | ≈2.3 MB            | iOS 14.0+                      |
| JVM desktop · linux-x64       | ✅ Verified (118 tests incl. live remotesrv sync) | 3.1 MB `.so`                 | 1.9 MB             | JDK 11+                        |
| JVM desktop · macOS / Windows | ❌ Not packaged yet                               | —                            | 1.8 / 1.9 MB²      | —                              |
| Linux x64 · Kotlin/Native     | ✅ Verified (52 tests, real Fedora host)          | 3.8 MB static `.a`           | 2.6 MB             | glibc ≥ 2.19 + `libcrypt.so.1` |
| Web (wasmJs)                  | 🚫                                                | —                            | —                  | —                              |

¹ `androidx.sqlite:sqlite-bundled` 2.7.0, measured from the published artifacts. Every doltrooms row compiles the one pinned DoltLite 0.11.33 amalgamation with the same flag set.
² Stock ships `osx_arm64` and `windows_x64` natives that doltrooms doesn't package yet.
## Quick Start
Add the dependency to your shared module — it `api`-exposes
`androidx.room3:room3-runtime` and `androidx.sqlite:sqlite`, so nothing
else is needed:

```kotlin
// commonMain
dependencies {
    implementation("dev.seri.doltrooms:doltrooms:<version>")
}
```

`AppDatabase` below is an ordinary Room 3 KMP database (`@Database` +
`@ConstructedBy(AppDatabaseConstructor::class)`; KSP generates the
platform actuals).
### Android (Kotlin)
```kotlin
import android.content.Context
import androidx.room3.Room
import dev.seri.doltrooms.driver.DoltLiteDriver
```

```kotlin
fun appDatabase(context: Context): AppDatabase {
    val dbFile = context.getDatabasePath("app.db")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
        .setDriver(DoltLiteDriver())
        .build()
}
```
### JVM (Kotlin)
```kotlin
import androidx.room3.Room
import dev.seri.doltrooms.driver.DoltLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File
```

```kotlin

fun appDatabase(): AppDatabase {
    val dbFile = File(System.getProperty("user.home"), ".myapp/app.db")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
        .setDriver(DoltLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
```
### iOS (Swift)
Room's builder is Kotlin API, so the database is created by a small factory in your shared module's `iosMain` and called from Swift:

```kotlin
// shared/src/iosMain — AppDatabase.ios.kt
import androidx.room3.Room
import dev.seri.doltrooms.driver.DoltLiteDriver
import platform.Foundation.NSHomeDirectory

fun appDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = NSHomeDirectory() + "/Documents/app.db")
        .setDriver(DoltLiteDriver())
        .build()
```

```swift
import sharedKit   // your KMP framework

// Kotlin top-level functions surface through their file-facade class:
let database = AppDatabase_iosKt.appDatabase()
```

The engine is statically embedded in the framework — keep exactly one SQLite engine per binary (don't also link `sqlite-bundled`).
### Linux (Kotlin/Native)
On Linux, this requires `libcrypt.so.1`. a static `libdoltlite.a` is embedded.

```kotlin
import androidx.room3.Room
import dev.seri.doltrooms.driver.DoltLiteDriver
```

```kotlin
fun appDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = "/var/lib/myapp/app.db")
        .setDriver(DoltLiteDriver())
        .build()
```

## Git, Sync
Coming next!
## Project documentation
All developer-facing documentation(including this README) is hand-written by [Seri](https://github.com/Serisium). The following docs are used in the development of this project and may be agent-written.
| Document                                                     | What it is                                                   |
|--------------------------------------------------------------|--------------------------------------------------------------|
| [`ARCHITECTURE.md`](ARCHITECTURE.md)                         | The settled architectural decisions (D1–D10) and a codemap of the repository — why the bridge is a driver, why every platform compiles one pinned DoltLite amalgamation, what was deliberately dropped. |
| [`docs/USAGE.md`](docs/USAGE.md)                             | The consumer guide: dependency setup, opening a database with `DoltLiteDriver`, the `dolt_*` helper tour, remotes and sync, and the known divergences from stock SQLite. |
| [`docs/FEASIBILITY.md`](docs/FEASIBILITY.md)                 | The founding research (2026-07-17): why DoltLite-as-driver works and why Room-to-Dolt-server doesn't. Context, not decisions. |
| [`docs/deferred-verification.md`](docs/deferred-verification.md) | The verification ledger: every claim that couldn't be verified in the environment it was written in, and the record of when and where it eventually was. |
| [`AGENTS.md`](AGENTS.md)                                     | Working rules for AI agents contributing to this repository, including which documents they may and may not edit. |
| [`.agents/skills/`](.agents/skills/)                         | Reference docs on the libraries this project builds against (Room 3, androidx.sqlite, DoltLite, the SQLite C API, …), kept re-verified against upstream because most of them postdate model training cutoffs. |
## License & Acknowledgements
This project is published under the Apache-2.0 license.

[Frutties Sample](samples/codelab) is built from the AOSP Room3 codelab (Apache-2.0, headers preserved)
[DoltLite](https://github.com/dolthub/doltlite) is built by [DoltHub](https://www.dolthub.com/)
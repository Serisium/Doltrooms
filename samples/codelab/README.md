# Fruitties on DoltLite — the KMP Room-migration codelab, Dolt-backed

A sample app for [doltrooms](../../README.md): the Fruitties shopping
app running Room 3 over the DoltLite-backed `DoltLiteDriver` on both
Android (Jetpack Compose) and iOS (SwiftUI).

## Lineage

1. **The codelab.** Google's
   [Migrate your Room database to Kotlin Multiplatform](https://developer.android.com/codelabs/kmp-migrate-room?hl=en#0)
   codelab migrates the Fruitties app's Room database from
   Android-only into a shared KMP module consumed by an Android and an
   iOS app.
2. **Its post-migration state.** This sample starts from the codelab's
   *solution* code — the `migrate-room` project on the `end` branch of
   [android/codelab-android-kmp](https://github.com/android/codelab-android-kmp)
   — i.e. the app as it looks after completing the codelab: Room
   2.7 KMP over `BundledSQLiteDriver`, SKIE for Swift interop.
3. **Modified to run on Dolt for all platforms.** The database layer is
   swapped onto [DoltLite](https://github.com/dolthub/doltlite) via
   `dev.seri.doltrooms:doltrooms` (consumed from source through a
   composite build — see `settings.gradle.kts`), so the same Fruitties
   schema and DAOs now run on a version-controlled database on every
   platform. `DoltVersioningTest` (androidTest) shows commit / branch /
   merge working against the app's own database; the full tour of the
   dolt_* surface is in [docs/USAGE.md](../../docs/USAGE.md).

The upstream sample is Copyright The Android Open Source Project,
Apache License 2.0; original file headers are preserved.

## Changes from the codelab end state

Everything not listed is untouched upstream code.

- **Room 2 → Room 3.** doltrooms is built against Room 3
  (`androidx.room3`), so the sample migrates with it: all
  `androidx.room` imports become `androidx.room3`; the builders are
  Room 3's context-free ones (`Room.databaseBuilder<AppDatabase>(name =
  path)` on Android too); `@Relation`'s singular
  `parentColumn`/`entityColumn` attributes became the plural
  `parentColumns`/`entityColumns` arrays, and the `@Relation` POJO
  puts the `@Embedded` parent first (Room 3's processor rejects the
  upstream ordering).
- **`BundledSQLiteDriver` → `DoltLiteDriver`** in
  `shared/src/{android,ios}Main/.../AppDatabase.*.kt` and the DAO
  instrumented tests. The Android factory creates the database's
  parent directory eagerly — DoltLite reports an unopenable path only
  at the first statement step, not at open (divergence #1 in
  [docs/USAGE.md](../../docs/USAGE.md)).
- **Toolchain aligned with the library** (its Kotlin 2.3.10 klibs set
  the floor): Kotlin 2.2.0 → 2.3.10, KSP → 2.3.10, AGP 8.11.1 → 9.0.1
  (AGP 9 has built-in Kotlin, so the `org.jetbrains.kotlin.android`
  plugin is removed), Hilt 2.57 → 2.60.1 (2.57's Gradle plugin uses an
  API AGP 9 removed).
- **SKIE removed; manual Flow bridge.** No SKIE release supports
  Kotlin 2.3.x at the time of writing, so the codelab's `for await`
  over DAO Flows is replaced by `shared/src/iosMain/.../FlowWatch.kt`
  plus `AsyncStream` wrapping in the Swift repositories. Two SKIE
  conveniences disappear with it: Kotlin top-level functions are
  reached through their file-facade class
  (`AppDatabase_iosKt.getPersistentDatabase()`), and `suspend fun
  count(): Int` arrives as `KotlinInt` (`.intValue`).
- **`iosX64` target dropped** — doltrooms ships `iosArm64` and
  `iosSimulatorArm64` only.
- **Instrumented tests**: the constraint-violation test now expects
  `androidx.sqlite.SQLiteException` (with a pluggable driver Room 3
  does not throw `android.database.sqlite` types), and
  `DoltVersioningTest` is added.
- **Small upstream fixes** the Room 3 constructor reorder forced (fake
  DAO/repository argument order) and one pre-existing `end`-branch
  compile error (`FakeFruittieApi` fed `List<Fruittie>` into a
  `List<FruittieNetworkEntity>` field).
- **Build plumbing**: Spotless/ktlint and the per-sample Gradle
  wrapper are dropped; the sample builds with the repository root's
  wrapper and includes the root build (`includeBuild("../..")`) with an
  explicit substitution of `dev.seri.doltrooms:doltrooms` →
  `:library`.

## Building and running

All commands from this directory, using the **root** wrapper. Android
needs `local.properties` with `sdk.dir` (or `ANDROID_HOME`) and, for
APK packaging, the NDK version pinned in the root
`gradle/libs.versions.toml` (the library cross-compiles the DoltLite
engine into the AAR).

```sh
# Android
../../gradlew :androidApp:assembleDebug        # APK (needs the pinned NDK)
../../gradlew :androidApp:testDebugUnitTest    # JVM unit tests (fakes)
../../gradlew :androidApp:connectedDebugAndroidTest  # DAO + dolt tests, needs device/emulator

# iOS — open iosApp/Fruitties.xcodeproj in Xcode and run, or:
../../gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

The Xcode build-phase script calls `../../gradlew
:shared:embedAndSignAppleFrameworkForXcode`, which builds the DoltLite
engine archive for the active slice and embeds it in `sharedKit`
(macOS host required — the archives are compiled with the Apple SDK's
clang by the library build).

## Verification state (2026-07-21, macOS host + motorola razr 2025 + iPad mini 6)

Android — verified end-to-end on a physical arm64-v8a device:
`:androidApp:installDebug` packages the DoltLite engine into the APK
and installs; the app runs, loads the fruit list into the DoltLite
database, and cart writes flow back into the UI;
`:androidApp:connectedDebugAndroidTest` is green (10/10: both DAO
suites incl. the foreign-key-violation case, plus
`DoltVersioningTest`); `:androidApp:testDebugUnitTest` green on the
host.

iOS — verified end-to-end on the iOS 18.3 simulator: the app builds
with xcodebuild (arm64-only — `sharedKit` has no x86_64 slice since
this sample drops `iosX64`; pass `ARCHS=arm64` when building for the
generic simulator destination), installs, and runs — network fetch
into the DoltLite database, list and cart rendering live through the
FlowWatch bridge, cart writes persisting. The doltrooms library's own
52-test conformance/Room/dolt suite also passes on the same
simulator. Also verified on physical hardware (iPad mini 6th gen,
iPadOS 26.5.2): built against the device destination with
`-allowProvisioningUpdates` and a free personal team, installed and
launched via `devicectl` — the fruit list loads into the on-device
DoltLite database and cart writes are durable at commit time and
persist across app relaunch (confirmed both on-screen and by pulling
`Documents/fruits.db` from the app data container). The library's
52-test suite also passes on the same iPad via
`:library:iosArm64DeviceTest`, which borrows this app's bundle id and
provisioning profile (and replaces the installed app while it runs).
Nothing unexercised remains.

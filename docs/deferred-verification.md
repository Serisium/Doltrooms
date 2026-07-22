# Deferred verification checklist

Work that is implemented and believed correct but cannot be *verified*
in the current development environment. Each entry lists what to run
and where. Verified entries stay only while they still bear on future
work (an open dependent, a reusable procedure, a live caveat);
fully-verified entries with no future bearing are deleted — git
history is the record (audit policy set 2026-07-22).

## iOS (needs a macOS host with Xcode) — VERIFIED 2026-07-21 incl. physical hardware; kept: the open entries below and the device-test procedure lean on it

Kotlin disables Apple-target compilation on non-Mac hosts as soon as
the target has a cinterop: "cross compilation to target 'iosArm64' has
been disabled because it contains cinterops: 'doltlite' which cannot be
processed on host 'linux_x64'" (KGP 2.3.10 warning, observed
2026-07-18). The iOS tasks are SKIPPED on Linux; compile, link, and
test were all deferred to a Mac.

Executed 2026-07-21 on a macOS host (Xcode 26.5), in the order this
entry prescribed:

1. Both iOS cinterops ran unchanged (headers-only def). ✓
2. `compileKotlinIosArm64` / `compileKotlinIosSimulatorArm64` compile
   the nativeMain actuals against the commonized bindings. ✓
3. `library/build.gradle.kts` gained `CompileDoltliteAppleStaticTask`:
   per-slice static archives of the pinned 0.11.33 amalgamation
   (`xcrun --sdk iphoneos|iphonesimulator clang` + `libtool -static`,
   same `doltliteCompileFlags`; deployment targets
   `arm64-apple-ios12.0` / `arm64-apple-ios14.0-simulator`), embedded
   via the same `-staticLibrary`/`-libraryPath` extraOpts as linuxX64.
   The tasks and the embedding are macOS-host-gated so Linux/CI
   behavior is byte-identical (iOS cinterops stay headers-only
   there). ✓
4. `library/src/iosTest/` concretes were created mirroring
   `linuxX64Test/` (temp paths via `NSTemporaryDirectory()`), and
   `./gradlew :library:iosSimulatorArm64Test` is GREEN: 52 tests, 0
   failures on the iOS 18.3 iPhone 16 Pro simulator — the same suite
   count linuxX64 runs. No Bundled-oracle concretes were added (the
   two-static-engines symbol-collision rule holds on Apple too). ✓
5. Link cleanliness: the samples/codelab `sharedKit` framework links
   with the embedded archive for BOTH slices
   (`linkDebugFrameworkIosArm64` / `...IosSimulatorArm64`, no
   undefined `sqlite3_*`, no deployment-target warnings), and the
   sample app's Swift sources typecheck against it. ✓

Later the same day, after `xcodebuild -downloadPlatform iOS`: the
samples/codelab Fruitties app built with xcodebuild (ARCHS=arm64 —
sharedKit has no x86_64 slice since the sample drops iosX64; the
generic simulator destination otherwise links both arches), installed
into the iPhone 16 Pro (iOS 18.3) simulator, and ran end-to-end —
network fetch into the DoltLite DB, list + cart rendering live
through the FlowWatch bridge (including the @Relation join), cart
writes persisting. Nothing simulator-side remains.

Physical hardware (same day, maintenance session): the same app
deployed to a physical iPad mini (6th generation, iPadOS 26.5.2,
arm64). First-run setup was all user-side — Developer Mode enabled
on-device, an Apple ID added in Xcode → Settings → Accounts (free
personal team; `xcodebuild -allowProvisioningUpdates
DEVELOPMENT_TEAM=<team>` then minted the certificate and profile
itself), and the developer profile trusted on-device (first launch is
denied with an FBSOpenApplicationServiceErrorDomain Security error
until Settings → General → VPN & Device Management → Trust). Built
against the `platform=iOS,id=<udid>` destination, installed and
launched via `devicectl device install app` / `device process
launch`. Verified end-to-end: network fetch into the DoltLite DB —
`Documents/fruits.db` pulled from the app data container (`devicectl
device copy from --domain-type appDataContainer`) shows the Room
schema (Fruittie/CartItem incl. the FK ON DELETE CASCADE) and the
fetched rows; the file is DoltLite-format (not stock-sqlite3 magic),
with no WAL sidecar — the engine writes its own `.fruits.db-lock` /
`fruits.db.lck` lock files. A watched single "Add" tap grew the DB
file immediately (commit-time durability on hardware), and the full
cart — including that tap — survived a `devicectl` terminate +
relaunch, confirmed on-screen.

The library's own 52-test suite also runs on the device: the same
session added `:library:iosArm64DeviceTest` (macOS-gated, in
`library/build.gradle.kts`) — it wraps the linked iosArm64 test.kexe
in a minimal .app, borrows the bundle id + provisioning profile from
a built-and-signed host app (pass
`-Pdoltrooms.iosDeviceTest.hostApp=<path to Fruitties.app>` and
`-Pdoltrooms.iosDeviceTest.udid=<udid>`; installing the runner
replaces that app on the device), signs, installs, launches attached
via `devicectl --console`, and parses the Kotlin/Native summary.
First run: GREEN, 52/52 in ~3.5 s on the iPad mini 6 — the same
suite count as every other platform leg. The device must be unlocked
at launch (a locked device fails with FBSOpenApplicationErrorDomain
"Locked"). Nothing iOS-side remains deferred.

## remotesrv fixture on non-linux-x64 hosts — deferred by Step 8

The jvm `RemoteServerSyncTest` drives real http sync against a spawned
`doltlite-remotesrv`. The binary comes from the release's
`doltlite-tools-linux-x64-<version>.zip`, downloaded and
checksum-verified by Gradle **on linux-x64 hosts only** — on any other
host the tests print a SKIP and pass vacuously. When the suite first
runs on a Mac (e.g. during the iOS verification above), extend
`downloadDoltliteTools` in `library/build.gradle.kts` with the
`osx-arm64` asset + its recorded SHA-256 to unskip them. The sync
logic itself is platform-independent and fully covered by the
commonTest `file://` remote tests on every target.

## XCFramework packaging (needs a macOS host) — deferred by Step 10

No XCFramework is configured yet — the iOS targets publish klibs only
(the KMP umbrella publication carries them; see the
`kmp-native-interop` targets-and-publishing reference). If Apple
consumers ever need a binary framework, the `XCFramework()` DSL plus
`assembleXCFramework` are Mac-only. The per-slice static amalgamation
archives they depend on exist since 2026-07-21 (item 3 of the iOS
checklist above) — only the DSL wiring remains.

## Maven Central publishing (needs a macOS host) — deferred by Step 11

Publishing is fully configured (vanniktech plugin, real POM, Dokka
javadoc jars, signing from `ORG_GRADLE_PROJECT_signingInMemoryKey*` /
`mavenCentralUsername`/`-Password` environment) and verified on the
macOS host (2026-07-21, PR #6): `./gradlew :library:publishToMavenLocal`
is GREEN with the full six-publication set (umbrella, `-jvm`,
`-android`, `-linuxx64`, `-iosarm64`, `-iossimulatorarm64`, real
`-javadoc` jars throughout; signing tasks correctly SKIP with no key
in the environment). The Linux artifacts cross-compile on macOS with
the Kotlin/Native-provisioned toolchain — design and rationale live
in `library/build.gradle.kts` (the "macOS-host cross toolchain"
comment block). The real Central release must run entirely from that
single macOS host (Central "explicitly forbids duplicate
publications" — `kmp-native-interop` targets-and-publishing
reference): `./gradlew publishToMavenLocal`, inspect, then
`./gradlew publishToMavenCentral` with credentials + key in env —
still pending only credentials and the human's go. Also before the
first release: review the dormant, release-triggered `publish.yml`
workflow's secrets and env names against the finalized signing setup
(it survives from the initial template; noted when PR #2's first CI
run exposed it, 2026-07-18).

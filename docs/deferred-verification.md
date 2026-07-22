# Deferred verification checklist

Work that is implemented and believed correct but cannot be *verified*
in the current development environment. Each entry lists what to run
and where. Complete through Step 12 (samples/codelab; first
macOS-host session, 2026-07-21) plus the same-day physical-iPad
maintenance session.

## iOS (needs a macOS host with Xcode) — VERIFIED 2026-07-21 incl. physical hardware (samples/codelab session + same-day maintenance session), entry kept for the record

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
`mavenCentralUsername`/`-Password` environment) and smoke-verified on
Linux: `./gradlew :library:publishToMavenLocal` publishes the root
umbrella + `-jvm` + `-android` + `-linuxx64` publications with real
Dokka HTML in every `-javadoc` jar, the extracted-resource `.so` in the
jvm jar, both ABI `.so`s in the AAR, and the cinterop klib alongside
the linuxX64 klib (signing tasks correctly SKIP with no key in the
environment). What Linux cannot verify: the iOS publications — KGP
skips Apple compilation on non-Mac hosts once a target carries a
cinterop, so a Linux `publish` would upload an umbrella that references
iOS variants without their artifacts. The real Central release must
run entirely from a single macOS host ("publish all artifacts from a
single host"; Central "explicitly forbids duplicate publications" —
`kmp-native-interop` targets-and-publishing reference). The iOS
checklist above closed 2026-07-21, so a macOS host can now produce
the complete artifact set: `./gradlew publishToMavenLocal` (now
including `-iosarm64`/`-iossimulatorarm64`), inspect, then
`./gradlew publishToMavenCentral` with credentials + key in env —
still pending only credentials and the human's go.

2026-07-21 addendum: the first macOS `publishToMavenLocal` exposed
two tasks that still hardcoded a Linux host toolchain (host
`gcc`/`objcopy`, `$JAVA_HOME/include/linux`), blocking every
publication on macOS. Fixed the same session (PR #6): on macOS hosts
the two Linux artifacts cross-compile with the
Kotlin/Native-provisioned toolchain, and local test runs load a
not-packaged host-twin dylib — the design and its rationale live in
`library/build.gradle.kts` (the "macOS-host cross toolchain" comment
block and the ones on the two compile tasks). Verified on the macOS
host: `:library:publishToMavenLocal` GREEN with the full
six-publication set (umbrella, `-jvm`, `-android`, `-linuxx64`,
`-iosarm64`, `-iossimulatorarm64`, real `-javadoc` jars throughout);
`file` reports the jvm jar's `natives/linux-x64/libdoltroomsjni.so`
and the embedded `libdoltlite.a` member as ELF 64-bit x86-64, with
no `__isoc23_*` symbols; jvmTest 118/0, testAndroidHostTest 52/0,
iosSimulatorArm64Test 52/0 (linuxX64Test correctly SKIPS — a Mac
links but cannot run Linux binaries); PR #6's `ci.yml` run observed
GREEN, confirming the unchanged Linux-host path.

## First observed run of the Step 12 CI jobs — FULLY OBSERVED 2026-07-21 (PR #3), entry kept for the record

Step 12 added three jobs to `ci.yml`: `android-x86_64-emulator` (KVM
udev rule + reactivecircus/android-emulator-runner@v2, API 35
google_apis x86_64; `:library:connectedAndroidDeviceTest` — the
x86_64 ABI leg — then the samples/codelab
`connectedDebugAndroidTest`), `sample-android` (sample APK assembly +
unit tests through the composite build), and `sample-ios` (macos-15:
library simulator suite, sharedKit link, `xcodebuild` of the
Fruitties app). All three, plus the pre-existing `build`, passed
GREEN on their very first run (PR #3): every watch-for held — the
API 35 x86_64 image resolved, macos-15's simulators satisfied the
generic destination (with the `ARCHS=arm64` flag caught locally
before push), `android-actions/setup-android` provisioned the SDK on
the arm64 macOS image, and the `cd samples/codelab && ../../gradlew`
invocations behaved under setup-gradle. With this, every platform
leg of the test matrix runs per-PR in CI. Nothing about CI remains
deferred.

## First observed CI run — FULLY OBSERVED 2026-07-18 (PR #2), entry kept for the record

`.github/workflows/ci.yml` ran for the first time on PR #2. Observed:
the workflow executes end-to-end (checkout, JDK, SDK/NDK, caches,
Gradle) and completed in ~5 min — ample 60-minute headroom; the first
run failed legitimately in `linuxX64Test` (the two-static-engines
symbol collision, since fixed — the 2026-07-18 post-Step-11
maintenance fix that removed the native Bundled-oracle test legs),
which also proved the on-failure test-report upload works
(the report artifact diagnosed the failure). It also exposed two
unmaintained template workflows from the initial commit: `gradle.yml`
(deleted — duplicated ci.yml, and its macos `iosSimulatorArm64Test`
job cannot pass before the iOS checklist above lands) and
`publish.yml` (kept — release-triggered, dormant; review its secrets
and env names against the finalized signing setup before the first
release). Subsequently observed, closing the checklist: after the
collision fix, the run is GREEN end-to-end (~5 min); on a re-run both
`actions/cache` steps HIT and restored (DoltLite zips, `~/.konan` —
note: actions/cache saves only on job success, so the failed first
run populated nothing), and the download tasks passed through in
sub-second time on the restored zips — the pre-seeded-zip acceptance
worked with no network fetch. Nothing about CI remains deferred.

## Android on-device (needs a device/emulator) — VERIFIED 2026-07-21 (motorola razr 2025, arm64-v8a), entry kept for the record

`./gradlew :library:connectedAndroidDeviceTest` is GREEN on a real
device: 52 tests / 0 failures (driver conformance + Room + dolt
suites, real android.jar, so the exception-message assertions the
mockable jar erases in host tests ran for real). The first-ever
execution surfaced two gaps this entry's "the device-test APK
assembles" assumption hid, both fixed in `library/`:
`androidx.test:runner` was never a dependency of the device-test APK
(instrumentation died at init with ClassNotFoundException), and no
`library/src/androidDeviceTest/` concretes existed, so the suite ran
0 tests — they now mirror the androidHostTest concretes without the
`exceptionMessagesObservable = false` override. Also verified on the
same device: the `samples/codelab` Fruitties app installs, runs, and
persists to its DoltLite database (manual smoke + its 10/10
`connectedDebugAndroidTest` incl. `DoltVersioningTest`). The x86_64
ABI — which no reachable dev host can run (Apple Silicon is
arm64-only; the fedora box is a VM without nested virt) — is covered
by ci.yml's `android-x86_64-emulator` job, observed GREEN on its
first run (PR #3, 2026-07-21: library 52/52 + sample 10/10 on an API
35 x86_64 emulator). Both ABIs verified; nothing remains here.

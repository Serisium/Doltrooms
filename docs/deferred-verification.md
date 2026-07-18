# Deferred verification checklist

Work that is implemented and believed correct but cannot be *verified*
in the Linux-only development environment. Each entry lists what to run
and where. PLAN.md Step 10 owns extending this file (Android device
tests, CI coverage); Step 11 adds publishing checks.

## iOS (needs a macOS host with Xcode) — deferred by Step 6

Kotlin disables Apple-target compilation on non-Mac hosts as soon as
the target has a cinterop: "cross compilation to target 'iosArm64' has
been disabled because it contains cinterops: 'doltlite' which cannot be
processed on host 'linux_x64'" (KGP 2.3.10 warning, observed
2026-07-18). The iOS tasks are SKIPPED on Linux, so `./gradlew build`
stays green here, but nothing iOS is actually compiled — compile,
link, and test are ALL deferred to a Mac, not just link+test as
PLAN.md Step 6's card originally assumed.

On a Mac, in order:

1. `./gradlew :library:cinteropDoltliteIosArm64
   :library:cinteropDoltliteIosSimulatorArm64` — the def is
   headers-only and `doltlite.h` needs nothing beyond compiler-builtin
   headers (`<stdarg.h>`), so this is expected to work unchanged.
2. `./gradlew :library:compileKotlinIosArm64
   :library:compileKotlinIosSimulatorArm64` — the nativeMain actuals
   compile against the commonized bindings (same sources linuxX64
   verifies on every build).
3. Extend `library/build.gradle.kts` with per-KonanTarget static
   archives of the amalgamation for iOS (Apple clang per target slice,
   same `doltliteCompileFlags`, embedded via the same
   `-staticLibrary`/`-libraryPath` extraOpts pattern the linuxX64
   cinterop uses — see `compileDoltliteStaticLinuxX64`). Keeping the
   0.11.33 amalgamation pin is settled: the upstream
   doltlite-swift/XCFramework artifacts lag the pin (0.11.17 at last
   check), so we compile the amalgamation ourselves like every other
   platform.
4. `./gradlew :library:iosSimulatorArm64Test` — runs both commonTest
   conformance suites (the linuxX64Test concrete classes have iOS
   equivalents only if added; create `iosTest` concretes mirroring
   `library/src/linuxX64Test/` first).
5. Glibc-style symbol skew does not apply (Apple libSystem), but
   verify the archive links clean against the device and simulator
   sysroots.

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

## Android on-device (needs a device/emulator) — deferred by Step 5

`connectedAndroidDeviceTest` exists (`withDeviceTestBuilder` is
configured) and the device-test APK assembles with
`lib/<abi>/libdoltroomsjni.so` present, but no device/emulator is
available in-env. On a machine with one: `./gradlew
:library:connectedAndroidDeviceTest` — runs both conformance suites
on-device per ABI (arm64-v8a, x86_64), including the exception-message
assertions the mockable android.jar erases in host tests.

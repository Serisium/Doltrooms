# Targets, source sets, the AGP KMP plugin, and publishing

Verified 2026-07-17 against kotlinlang.org and
developer.android.com.

## Default hierarchy and expect/actual

- "The Kotlin Gradle plugin has a built-in default hierarchy
  template… The plugin sets up those source sets automatically based
  on the targets specified in your project"
  (https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html).
  Declaring `iosArm64()` + `iosSimulatorArm64()` yields
  `iosMain` → `appleMain` → `nativeMain` → `commonMain` with
  type-safe accessors.
- Custom shared sets (e.g. `jvmAndAndroidMain` for shared JNI code):
  `applyDefaultHierarchyTemplate()` then
  `val jvmAndAndroidMain by creating { dependsOn(commonMain.get()) }`
  and point member source sets at it.
- expect/actual
  (https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html):
  every expect needs an actual in every platform source set; same
  package required; actuals may live in intermediate source sets.
  "Expected and actual classes are in Beta" — prefer expect
  functions/interfaces at API boundaries, or add
  `freeCompilerArgs.add("-Xexpect-actual-classes")`.

## The AGP KMP library plugin (used by `library/build.gradle.kts`)

https://developer.android.com/kotlin/multiplatform/plugin:

- Plugin id `com.android.kotlin.multiplatform.library`; AGP ≥ 8.10,
  KGP ≥ 2.0. Configured inside `kotlin {}` via `androidLibrary {}`
  (`android {}` on AGP ≥ 8.12) — "There's no top-level `android`
  extension block."
- "The plugin uses a single variant, removing support for product
  flavors and build types."
- "Both unit and device (instrumentation) tests are disabled by
  default" — opt in with `withHostTestBuilder {}` /
  `withDeviceTestBuilder { sourceSetTreeName = "test" }` (this repo's
  template already does).
- Source sets are `androidMain` / `androidHostTest` /
  `androidDeviceTest`; "The legacy `src/main`, `src/test`, and
  `src/androidTest` directories are NOT supported." Don't copy old
  `com.android.library` recipes — that path "is expected to be
  removed in Android Gradle plugin 10.0 (second half of 2026)".

## Final binaries for Apple consumers

https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html:

```kotlin
val xcf = XCFramework()
listOf(iosArm64(), iosSimulatorArm64()).forEach {
    it.binaries.framework { baseName = "shared"; xcf.add(this) }
}
```

Tasks `assembleXCFramework` / `assemble<Name>ReleaseXCFramework`;
static libs via `binaries.staticLib { }`; CocoaPods variants
(`podPublishReleaseXCFramework`) exist under the CocoaPods plugin.

## Publishing to Maven

https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html:

- Umbrella publication: "The root publication serves as an entry
  point that references all target-specific publications." Per-target
  coordinates are `groupId:artifactId-targetName:version`
  (`lib-jvm`, `lib-iosarm64`, …); native artifacts are klibs, and a
  cinterop klib travels inside its target's publication.
- Maven Central requires a root JAR without classifier — "The Kotlin
  Multiplatform plugin automatically produces the required artifact
  with the embedded metadata artifacts." Gradle module metadata
  (`*.module`) redirects consumers per platform.
- **Host requirements:** a Mac is required when "Your library or
  dependent modules have cinterop dependencies" (ours will), when
  CocoaPods is used, or when building Apple final binaries. And
  "publish all artifacts from a single host" — Maven Central
  "explicitly forbids duplicate publications." Net: this library
  publishes from macOS.
- Android target: the new AGP KMP plugin publishes its single variant
  (the old `publishLibraryVariants` dance belongs to
  `com.android.library`).

## vanniktech maven-publish specifics (verified in-repo 2026-07-18, plugin 0.36.0, Dokka 2.2.0)

- Central requires a `-javadoc` jar; for KMP wire it via
  `mavenPublishing { configure(KotlinMultiplatform(javadocJar =
  JavadocJar.Dokka("<task>"), sourcesJar = SourcesJar.Sources())) }`.
  With Dokka 2.x the input task is `dokkaGeneratePublicationHtml`
  (the docs' `"dokkaHtml"` example is the Dokka 1.x task name;
  `dokkaGenerateHtml` is only a lifecycle wrapper). When the Android
  target uses `com.android.kotlin.multiplatform.library`: "leave out
  the third parameter (`androidVariantsToPublish`)"
  (https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/docs/what.md).
- Dokka 2.2.0 (latest stable on Maven Central as of 2026-03) runs
  V2 mode by default — no `pluginMode` property needed; plugin id
  `org.jetbrains.dokka`.
- Signing/credentials via environment:
  `ORG_GRADLE_PROJECT_mavenCentralUsername`/`-Password` (a Central
  Portal user token, not the login) and
  `ORG_GRADLE_PROJECT_signingInMemoryKey` (+ optional `-KeyId`,
  `-KeyPassword`)
  (https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/docs/central.md).
  With `signAllPublications()` but no key in the environment, the
  `sign*` tasks are SKIPPED — `publishToMavenLocal` works unsigned
  (observed in-repo).
- On a Linux host this repo's `publishToMavenLocal` produces the root
  umbrella + `-jvm` + `-android` + `-linuxx64` publications (the
  cinterop klib travels next to the linuxX64 klib as
  `-cinterop-doltlite.klib`); the iOS publications require the Mac
  host per the section above.

## JS/Wasm (dropped, ARCHITECTURE.md D4 amendment — know why)

Kotlin/Wasm and Kotlin/JS interop is "JavaScript-only" via `external`
declarations / `JsAny` (https://kotlinlang.org/docs/wasm-js-interop.html)
— there is no supported linking of a Kotlin/Wasm module against an
Emscripten-compiled C library. The workable path would be binding to a
sqlite-wasm-style JS package (`@dolthub/doltlite-wasm`) from
`wasmJsMain`, and OPFS storage forces an async, single-connection
driver — Room 3's `androidx.sqlite:sqlite-web` `WebWorkerSQLiteDriver`
is the template (`room3` skill).

This repo's web rung was DROPPED at PLAN.md Step 9 (ARCHITECTURE.md
D4 amendment). The decisive mechanic, probed in-repo 2026-07-18 by
adding `wasmJs { browser() }` to the target set: androidx.sqlite
2.7.0's interfaces are expect-split into nonWeb (synchronous
`open`/`prepare`/`step`) and web (`suspend`) actuals, and a source
set's dependency view is the intersection of fragments shared by all
its targets — so the moment a web target joins, `commonMain` sees the
suspend variants and any shared non-suspend driver surface fails to
compile ("Non-suspend function 'open' cannot override suspend
function 'suspend fun open(fileName: String)'"). A KMP library whose
common surface declares the nonWeb members must move that entire tree
(main and test) onto a nonWeb intermediate source set before a web
target can even be declared. Note the upstream artifacts themselves
are not the blocker: `androidx.sqlite:sqlite` 2.7.0 and
`androidx.room3:room3-runtime` 3.0.0 both publish `js` and `wasmJs`
variants (`sqlite-wasm-js`, `room3-runtime-wasm-js` — Gradle module
metadata at
https://dl.google.com/android/maven2/androidx/sqlite/sqlite/2.7.0/sqlite-2.7.0.module
and
…/androidx/room3/room3-runtime/3.0.0/room3-runtime-3.0.0.module,
verified 2026-07-18).

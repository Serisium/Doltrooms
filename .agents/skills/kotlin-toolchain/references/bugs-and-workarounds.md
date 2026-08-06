# Toolchain bug ledger

Every toolchain bug this repo has hit, with status, the in-repo
workaround, and its removal condition. Filed upstream 2026-08-05 by the
human where noted; repro repos carry the reports' reproduction projects.
Findings 2026-08-04/05, versions 0.11.1, 0.12.0-dev-4213, 0.12.0-dev-4215.

## 1. `generated.*.fragment.modifier` crashes in its documented form — FILED: [KTC-5646](https://youtrack.jetbrains.com/issue/KTC-5646)

- **Symptom:** `Internal error: NoSuchElementException` from
  `selectFragmentByDescriptor` during model reading.
- **Cause:** docs say write the qualifier "without the `@`"; the model
  stores fragment modifiers WITH `@` and compares verbatim (the
  function carries a FIXME about crashing on user input).
- **Status:** NOT fixed in 0.12.0-dev-4213 (re-verified after the
  dev-pin bump). Repro:
  `github.com/Serisium/kotlin-toolchain-fragment-modifier-repro`.
- **Workaround:** `modifier: "@jvm"` / `"@native"` in
  `build-plugins/doltlite/plugin.yaml`. Remove the quotes-and-@ only
  after a toolchain release makes the documented bare form parse.

## 2. CLI hangs after a KSP processor crash — FILED: [KTC-5645](https://youtrack.jetbrains.com/issue/KTC-5645)

- **Symptom:** [ksp] ERROR prints, then the CLI never exits (needs a
  kill). NOT native-specific: any crashing processor reproduces it on
  jvm in ~4s.
- **Cause (root-caused in the repro):** the KSP2 child JVM never exits
  after main throws (non-daemon pooled thread); the CLI blocks in
  `awaitListening` readLine with no watchdog. KTC-5477's fix kills the
  child only on coroutine cancellation — nothing cancels on the
  failure path.
- **Status:** NOT fixed in 0.12.0-dev-4213; no existing KTC issue for
  hang-on-failure. Repro:
  `github.com/Serisium/kotlin-toolchain-ksp-hang-repro`.
- **Workaround:** none needed in-config (our KSP no longer crashes);
  when debugging KSP failures, expect to kill the process and read
  `build/logs/`.

## 3. AAR-packaged maven TEST-dependencies never reach android KSP — [KTC-5649](https://youtrack.jetbrains.com/issue/KTC-5649), FIXED in 0.12.0-dev-4225

- **Symptom:** `kspAndroidTest` fails (Room `MissingType`) although
  the dependency is declared in `test-dependencies` (or
  `test-dependencies@android`).
- **Cause (root-caused):** `kspAndroidTest` is wired to the MAIN
  AAR-transform task (`isTest` not passed), so test-scope AARs stay
  raw and are filtered from the KSP `-libraries`. JAR test-deps and
  main-scope AARs are fine — which is why dolt-write (Room via
  exported MAIN dolt-core dep) works and driver would not. The
  earlier jvmAndroid-alias theory was a red herring.
- **Status:** reproduces on 0.12.0-dev-4213. KTC-5602 is the
  main-scope cousin; no exact issue. Repro:
  `github.com/Serisium/kotlin-toolchain-android-testdeps-repro`.
- **Workaround retired 2026-08-06** (verified on 0.12.0-dev-4225): the
  Room fixture is back in driver's common `test/` and the `nonAndroid`
  alias is gone. Android host tests stay parked in
  `unmigrated/androidHostTest` for the loader gap only (below).

## 4. Native-target KSP misses the Kotlin/Native stdlib — FIXED in dev

- **Symptom (0.11.1):** `cannot find required type
  XTypeName[kotlin.UByte]` from Room under standalone `KSPNativeMain`;
  proven by re-running the exact args file with the konan stdlib
  prepended to `-libraries` → exit 0.
- **Status:** FIXED by KTC-4398 in the 0.12.0-dev line — the reason
  the wrapper pins `0.12.0-dev-4213`. Verified: native test KSP runs
  in-build; no committed generated sources needed. (A repro-repo task
  was spawned for the record; since the fix is already upstream it
  may be dropped.)
- **Workaround history:** committed `test@native/` Room outputs +
  jvm-scoped KSP (removed 2026-08-04 with the dev pin). Nothing to
  remove now; re-pin to stable ≥ 0.12 when released.

## 5. Native TEST links omitted the module's cinterop klib — FIXED in dev

- **Symptom (0.11.1):** native test binaries link, but every cinterop
  call throws `kotlin.internal.IrLinkageError` ("No function found
  for symbol 'dev.seri.doltrooms.doltlite.c/...'") — the test-link
  `-library` list had no `doltlite.klib` (main compile had it).
  Affected plugin-contributed AND `cinterop/`-scanned defs alike; no
  config-level workaround exists (freeCompilerArgs `-library=` poisons
  cinterop and the KLIB resolver rejects relative paths).
- **Status:** fixed in 0.12.0-dev-4213 (verified: 52/52 simulator
  tests). Covered by the same dev-pin; re-verify at the stable re-pin.

## Publishing bugs (KMP publishing preview, 0.12.0-dev-4215; KTC-719 is the feature ticket)

Found wiring `settings.publishing` + `./kotlin publish mavenLocal`
(repro chips spawned 2026-08-05; drafts not posted):

## 6. Publish crashes on a source-less KMP module — [KTC-5652](https://youtrack.jetbrains.com/issue/KTC-5652), FIXED in 0.12.0-dev-4225

- Internal `NoSuchElementException` in
  `PrepareMavenPublishablesTask.generateGradleMetadataForLeafPlatforms`
  instead of a diagnostic. Hit by our scaffold modules. Primarily a
  diagnostics bug — the basics docs explicitly allow source-less
  modules ("All sources and resources are optional"), and BOM-style
  deps-only modules are a real pattern, so full support may be the
  intended fix.
- **Workaround retired 2026-08-06**: the `ModuleScaffold` markers are
  deleted; source-less `dolt-core` published clean on dev-4225.

## 6b. Non-Kotlin files under `src/` fail publish — [KTC-5654](https://youtrack.jetbrains.com/issue/KTC-5654), FIXED upstream

- Any non-`.kt` file in a source tree (our old `.gitkeep`s; a stray
  macOS `.DS_Store` is the real-world case) is passed verbatim to the
  publish-only `compileMetadataCommon` as a source entry → "error:
  source entry is not a Kotlin file" — while `./kotlin build`/`test`
  are green, so it detonates only at publish time.
- No repo change needed (the `.gitkeep`s are gone regardless); keep
  source trees tidy as a matter of hygiene.

## 7. Publish-time metadata compilation fails on platform-set-refined dependency APIs — FILED: [KTC-5665](https://youtrack.jetbrains.com/issue/KTC-5665); THE remaining publishing blocker

- driver's synchronous `SQLiteDriver.open` override fails
  `compileMetadataCommon` with "'open' overrides nothing" — publish
  fails though every build/test is green. Root cause refined by the
  repro session: dependency resolution picks the RIGHT source-set
  klibs, but they're emitted general-first and the metadata compiler
  shadows duplicate classifiers first-on-classpath (swapping the two
  classpath entries fixes the compile). Blocks publishing `driver`,
  `dolt-write`, `dolt-remotes` (dep chain), and `processor`/`verifier`
  (publishing a module prepares its module DEPENDENCIES' publications).
- **Still broken on 0.12.0-dev-4225; no workaround.** `dolt-core` and
  `dolt-read` publish clean (umbrella + per-platform sets, correct
  `.module` metadata and — since KTC-5650 — correct POMs). Repro:
  `github.com/Serisium/kotlin-toolchain-publish-metadata-variant-repro`.
  When a fix ships: re-pin, `./kotlin publish mavenLocal` should clear
  all seven modules — that is the last publishing gate before a
  release.

## 8. Every per-platform POM pins ONE platform's dependency variant — [KTC-5650](https://youtrack.jetbrains.com/issue/KTC-5650), FIXED in 0.12.0-dev-4225

- Was: all of dolt-core's platform POMs declared
  `room3-runtime-iossimulatorarm64`. Verified fixed on dev-4225: each
  platform POM now names its own variant (jvm → room3-runtime-jvm,
  android → -android, ...).

## Publishing friction (feedback, not necessarily bugs)

- `signArtifacts: true` demands `KOTLIN_TOOLCHAIN_SIGNING_KEY` even
  for a mavenLocal publish — no keyless local verification (vanniktech
  skipped signing without a key). Local verify needs a throwaway key
  (RSA-4096 verified; give it more than a day's validity — an EXPIRED
  key fails with the misleading "does not contain any usable component
  keys capable of signing").
- Native test output changed in the 0.12.0-dev line: per-test
  Started/Passed lines instead of the gtest-style [==========] summary
  — greps against the old format silently match nothing (exit codes
  are unaffected).
- A mavenLocal publish target must be declared manually
  (`repositories: [{id: mavenLocal, url: mavenLocal, publish: true}]`,
  in `//publishing.module-template.yaml`) — no built-in analogue of
  `publishToMavenLocal`.
- The publishing docs page still says KMP publication is unsupported —
  docs lag the implementation.
- Empty javadoc jar by default (acknowledged in the docs).

## Not bugs, but toolchain gaps (no issue to file)

- **kmp/lib publishing:** PREVIEW (KTC-719); wired via
  `//publishing.module-template.yaml`, blocked for release by bugs
  7-8 above. `publish.yml` is the intended final shape and fails
  loudly meanwhile.
- **explicitApi / ABI validation / detekt:** no toolchain equivalents;
  D11's mechanisms suspended (see ARCHITECTURE.md D12).
- **AAR jniLibs packaging:** no hook — android artifacts ship no
  natives; android device runtime parked.
- **Host-conditional test system properties:** `settings.jvm.test.
  systemProperties` is host-unconditional — the reason fetchRemotesrv
  is a manual command.
- **Android host tests' loader:** `System.loadLibrary` needs
  `java.library.path` control the toolchain doesn't expose.

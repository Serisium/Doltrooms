# Toolchain bug ledger

Every toolchain bug this repo has hit, with status, the in-repo
workaround, and its removal condition. Repro repos carry ready-to-post
YouTrack drafts (ISSUE-DRAFT.md; none posted yet — human decision).
All findings 2026-08-04, versions 0.11.1 and 0.12.0-dev-4213.

## 1. `generated.*.fragment.modifier` crashes in its documented form

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

## 2. CLI hangs after a KSP processor crash

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

## 3. AAR-packaged maven TEST-dependencies never reach android KSP

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
- **Workaround:** driver's Room fixture lives in `test@nonAndroid/`
  (alias) so the android test fragment has no Room inputs; driver's
  android host tests are parked in `unmigrated/androidHostTest`.
  Revisit when a fix ships AND the loader problem (below, "not bugs")
  is solved.

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

## Not bugs, but toolchain gaps (no issue to file)

- **kmp/lib publishing:** unsupported; announced for later this month.
  `publish.yml` fails loudly meanwhile.
- **explicitApi / ABI validation / detekt:** no toolchain equivalents;
  D11's mechanisms suspended (see ARCHITECTURE.md D12).
- **AAR jniLibs packaging:** no hook — android artifacts ship no
  natives; android device runtime parked.
- **Host-conditional test system properties:** `settings.jvm.test.
  systemProperties` is host-unconditional — the reason fetchRemotesrv
  is a manual command.
- **Android host tests' loader:** `System.loadLibrary` needs
  `java.library.path` control the toolchain doesn't expose.

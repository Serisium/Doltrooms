# module.yaml semantics (the subset this repo uses)

Reference: <https://kotlin-toolchain.org/latest/> — reference/module
(full schema), user-guide/multiplatform (fragments, propagation),
user-guide/advanced/ksp. Everything below was verified in this repo
2026-08-04 unless marked doc-only.

## Product and layout

```yaml
product:
  type: kmp/lib            # or jvm/lib, jvm/app, jvm/amper-plugin, ...
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64, linuxX64 ]
```

- `platforms` lists PLATFORMS only, never families ("no platform
  family shortcuts" — doc rule).
- KMP layout is mandatory for `kmp/lib`: `src/`, `src@<qualifier>/`,
  `test/`, `test@<qualifier>/`, resources as `resources[@...]/`,
  cinterop `.def`s auto-scanned from `cinterop[@...]/`. Package
  directories under `src/` are allowed (we keep `dev/seri/...`).
- `layout: maven-like` exists for jvm-only products ONLY
  (processor/verifier use it — zero moves from `src/main/kotlin`).

## Aliases and fragment qualifiers

```yaml
aliases:
  - jvmAndroid: [ jvm, android ]
  - nonAndroid: [ jvm, iosArm64, iosSimulatorArm64, linuxX64 ]
```

`src@jvmAndroid/`, `test@nonAndroid/` then behave like intermediate
source sets (visible to each member platform's fragment). The default
family qualifiers (`@native`, `@apple`, `@ios`, `@linux`) come from
the platform hierarchy.

## Dependencies

```yaml
dependencies:
  - androidx.sqlite:sqlite:2.7.0: exported     # exported == Gradle api
  - ../dolt-core: exported                     # module dep, path-based
test-dependencies:
  - org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0
test-dependencies@jvm:
  - androidx.sqlite:sqlite-bundled:2.7.0       # platform-scoped
```

- Scopes: default `all`; `compile-only`, `runtime-only`; long form
  needed to combine with `exported`.
- kotlin-test is preconfigured for every platform's tests (doc).
- Default repositories: Maven Central + Google (doc) — no
  `repositories:` block needed here.
- KMP libraries resolve per-platform variants automatically (AARs for
  android are transformed — but see the ledger's bug 3 for the
  test-scope AAR gap).

## Settings and propagation — the trap

`settings` and `test-settings` (and their `@qualifier` variants)
merge by the propagation rules: **scalars override, lists and maps
APPEND**. Consequences hit twice here:

- `settings.kotlin.freeCompilerArgs` flows into TEST compilations and
  even konanc test LINK invocations — there is NO way to remove a
  common list entry per fragment. That is why `-Xexplicit-api=strict`
  cannot be enabled (test sources don't declare visibility) while
  `-Xexpect-actual-classes` can.
- A `test-settings@jvm` block does NOT stop common `test-settings`
  from applying elsewhere — qualify only when the setting must be
  fragment-specific.

## KSP

```yaml
test-settings:
  kotlin:
    ksp:
      processors: [ androidx.room3:room3-compiler:3.0.0 ]
      processorOptions: { room.schemaLocation: ./schemas }
```

- KSP2 only. Putting `ksp` under `test-settings` runs processors on
  TEST fragments only — the moral equivalent of Gradle's per-target
  `kspXxxTest` wiring; main compilations stay processor-free.
- KSP runs once per platform test fragment chain (log line: "Running
  KSP on fragments [commonTest, jvmTest, ...]"). Generated code is
  per-platform — never visible to common code (doc + KSP issue 567).
- Native-target KSP requires the ≥ 0.12.0-dev pin (ledger, bug 4).

## Android

```yaml
settings:
  android: { namespace: dev.seri.doltrooms, compileSdk: 36, minSdk: 24 }
```

SDK auto-provisions. There is no AAR jniLibs packaging hook and no
`java.library.path` control for test processes
(`settings.jvm.test.systemProperties` exists but is host-UNconditional
— unusable for host-specific fixture paths).

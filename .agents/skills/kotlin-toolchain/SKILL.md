---
name: kotlin-toolchain
description: The Kotlin Toolchain (JetBrains' declarative YAML build system, Amper successor) as used by this repo — the ./kotlin wrapper, project.yaml/module.yaml configuration, the build/test CLI, the build-plugins/doltlite native-build plugin, and the live ledger of toolchain bugs with in-repo workarounds. Use for ANY build task (build, test, adding dependencies or modules, KSP config, cinterop, CI commands), when a ./kotlin invocation fails, when editing module.yaml or plugin.yaml, or when re-evaluating a workaround after a toolchain upgrade. Triggers: kotlin toolchain, Amper, module.yaml, project.yaml, ./kotlin, kmp/lib, amper-plugin, cinteropDefinitions, KSP2, fragment modifier, KTC.
---

# Kotlin Toolchain (this repo's build system)

The build system since D12 (`ARCHITECTURE.md`): JetBrains' Kotlin
Toolchain — declarative YAML modules, no Gradle. Official docs:
<https://kotlin-toolchain.org/latest/> (source of the cited pages;
the toolchain is the renamed continuation of Amper, and internal
names still say `amper` in logs, task names, and the plugin product
type).

## This repo's setup, in one screen

- `./kotlin` / `kotlin.bat` — vendored wrapper; pins the distribution
  version + SHA-256 at the top of each script. **Currently pinned to a
  0.12.0-dev build** because two 0.11.1 bugs blocked native tests —
  see the ledger below; re-pin to the first stable ≥ 0.12.
- `project.yaml` — module list + plugin registration.
- One `module.yaml` per module: `driver`, `dolt-core`, `dolt-read`,
  `dolt-write`, `dolt-remotes` are `kmp/lib` (platforms jvm, android,
  iosArm64, iosSimulatorArm64, linuxX64); `processor`, `verifier` are
  `jvm/lib` with `layout: maven-like` (kept their `src/main/kotlin`
  layout).
- `build-plugins/doltlite` — local build plugin owning every custom
  native step (amalgamation download, host JNI lib, static engine
  archives incl. the mac→linux-x64 Konan cross-compile, generated
  cinterop `.def`, `fetchRemotesrv` command).
- KMP source layout: `src/`, `src@<platform-or-alias>/`, `test/`,
  `test@<...>/` — aliases declared per module (`jvmAndroid`,
  driver's `nonAndroid`).
- `publishing.module-template.yaml` — shared Maven publishing config
  (group/version/POM/mavenCentral/mavenLocal target), applied by every
  published module; per-module `settings.publishing.artifactId` only.
  Publishing is PREVIEW: see the ledger's publishing section before
  touching it.

## Daily commands

```
./kotlin build                      # all platforms this host can build
./kotlin test -p jvm                # also: -p linuxX64, -p iosSimulatorArm64
./kotlin test -p jvm -m driver      # scope to one module
./kotlin do fetchRemotesrv          # linux-x64 only; prints the JAVA_TOOL_OPTIONS
                                    # line jvm test runs need for RemoteServerSyncTest
./kotlin show modules|commands|tasks
```

Android is BUILD-only (host/device test runs parked — ledger).
Diagnostics: human log `build/logs/amper_*/info.log`, full detail in
`debug.log` (task command lines, KSP/konanc argument files under
`build/temp/java-args-*.txt`). Task outputs live under
`build/tasks/_<module>_<task>/`.

## Routing

| Need | Read |
|---|---|
| CLI verbs, logs, artifact/output layout, caches | `references/commands.md` |
| module.yaml: products, deps, settings/test-settings semantics, KSP, aliases | `references/module-yaml.md` |
| Plugin system + the doltlite plugin's tasks and contributions | `references/build-plugin.md` |
| The bug ledger: every toolchain bug we hit, status, workarounds, repro repos | `references/bugs-and-workarounds.md` |

## Standing rules

- **Before removing any workaround**, check its entry in
  `references/bugs-and-workarounds.md` for the removal condition, and
  re-verify against the pinned toolchain version.
- **When a `./kotlin` invocation fails mysteriously**, read
  `build/logs/amper_*/debug.log` before theorizing — the real command
  lines and classpaths are there (that's how every bug in the ledger
  was root-caused).
- **When bumping the toolchain pin**: update BOTH wrapper scripts
  (version + sha256), re-run the full matrix (`./kotlin build`,
  jvm/linuxX64/iosSimulatorArm64 tests on their hosts), and re-test
  each ledger entry marked fixed-in-dev.

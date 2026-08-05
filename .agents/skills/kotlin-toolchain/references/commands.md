# CLI, logs, and build layout

All facts verified in this repo, 2026-08-04, toolchain 0.11.1 and
0.12.0-dev-4213 unless noted. Official CLI docs:
<https://kotlin-toolchain.org/latest/> (cli section).

## Verbs

- `./kotlin build [-p <platform>]... [-m <module>]... [-v debug|release]`
  — compile AND link everything selected, including test binaries.
  Platforms not buildable on the host are skipped (Apple targets on
  Linux).
- `./kotlin test [-p ...] [-m ...] [--include-classes/--exclude-classes/--include-test <pattern>]`
  — run tests. jvm tests run under JUnit Platform; native tests run
  the linked test.kexe (iOS: on a simulator the toolchain boots).
- `./kotlin do <command>` — run a plugin custom command
  (`./kotlin show commands` lists them). Ours: `fetchRemotesrv`.
- `./kotlin show modules|tasks|commands`, `./kotlin init`.

The wrapper self-provisions: the distribution (pinned in the wrapper),
a JRE for the CLI, a JDK for compilation, the Android SDK
(cmdline-tools; observed auto-install), and Konan + its dependency
packages for native targets. `JAVA_HOME` is used when set and
suitable (`settings.jvm.jdk` controls this).

## Where things land

| Path | Contents |
|---|---|
| `build/logs/amper_<timestamp>_<pids>_<verb>/` | `info.log` (what the console showed) and `debug.log` (task [cmd] lines, dependency resolution, full compiler/KSP invocations) |
| `build/temp/java-args-*.txt` | `@argfile`s for spawned JVM tools (KSP, konanc) — the real, complete argument lists (`-libraries=`, `-library=`, output dirs) |
| `build/tasks/_<module>_<taskName>[@<plugin>]/` | per-task outputs (e.g. `_driver_jarJvm`, `_driver_prepareAmalgamation@doltlite`) |
| `build/artifacts/CinteropKlibsArtifact/<module><Target>/` | cinterop klibs (internal layout — do not reference from config; see bug 5 history) |
| `build/generated/<module>/<fragment>/src/ksp/kotlin/` | KSP-generated sources |

## Caches (what CI restores)

- Linux `~/.cache/JetBrains/Kotlin` / macOS
  `~/Library/Caches/JetBrains/Kotlin` — the CLI distributions
  (`cli/`), the maven-dependency cache (`.m2.cache/`), extracted JDKs.
- `~/.konan` — Kotlin/Native prebuilt compiler + `dependencies/`
  (llvm, sysroots; the mac→linux cross-compile in the doltlite plugin
  reads `llvm-*` and `x86_64-unknown-linux-gnu-gcc-*` from there).
- DoltLite downloads are re-verified by SHA-256 and skipped when the
  cached zip matches (plugin behavior), so caching
  `build/tasks/_driver_prepareAmalgamation@doltlite/*.zip` and
  `driver/native/build/*.zip` avoids the GitHub-release round-trips.

## Test-run notes

- RemoteServerSyncTest (jvm, linux-x64 hosts): run
  `./kotlin do fetchRemotesrv` once; it prints the exact
  `JAVA_TOOL_OPTIONS="-Ddev.seri.doltrooms.remotesrv=..."` prefix.
  Without it the test fails on linux-x64 BY DESIGN (and skips
  elsewhere).
- iOS simulator: occasional spawn flake at module handoff
  ("Process spawn via launchd failed because device is not booted",
  exit 149) — rerun, or run modules as separate invocations
  (`-m driver`, then `-m dolt-write`); tests themselves were green
  (52/52) when spawned.
- linuxX64 test binaries need `libcrypt.so.1` at runtime (Konan
  sysroot links it): present on Ubuntu; Fedora needs
  `libxcrypt-compat`.

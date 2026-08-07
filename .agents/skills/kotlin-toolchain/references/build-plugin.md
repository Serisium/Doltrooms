# Build plugins, and the doltlite plugin in particular

Reference: <https://kotlin-toolchain.org/latest/> —
user-guide/plugins (quick-start, topics/tasks, topics/structure).
Verified in this repo 2026-08-04.

## The plugin model (what we rely on)

- A plugin is a module with `product: jvm/amper-plugin`, registered in
  `project.yaml` `plugins:`, ENABLED per consuming module
  (`plugins: { <id>: enabled }` in its module.yaml). Plugin id =
  module directory name.
- `plugin.yaml` registers tasks; implementations are plain top-level
  Kotlin functions annotated `@TaskAction`
  (`org.jetbrains.amper.plugins.*`), with `Path` parameters marked
  `@Input`/`@Output`. Arbitrary code is allowed (ProcessBuilder,
  java.net) — "plugin authors are free to use whatever libraries they
  need" (tasks doc).
- Task ORDERING is inferred from matching `@Input`/`@Output` paths
  (equal or ancestor/descendant); there is no manual dependsOn.
  Execution avoidance is automatic from declared inputs/outputs
  (mtime-based).
- `generated:` blocks contribute outputs back to the build:
  `sources`, `resources`, `cinteropDefinitions` — each optionally
  scoped by `fragment: { modifier, isTest }`. **Write modifiers WITH
  the `@`** (`"@jvm"`, `"@native"`) — ledger bug 1.
- `commands:` exposes tasks as `./kotlin do <name>`.
- Tasks run on the toolchain's own JVM — a provisioned JRE. Anything
  needing JDK headers must resolve a real JDK itself (see
  `jdkHomeWithJniHeaders` in our tasks.kt).

## The doltlite plugin (build-plugins/doltlite)

Enabled in `driver` only. Tasks (wiring: `plugin.yaml`; actions:
`src/tasks.kt`, where the DoltLite version + SHA-256 pins live):

| Task | Does | Feeds |
|---|---|---|
| `prepareAmalgamation` | download + SHA-verify + unzip the pinned DoltLite amalgamation | every compile below |
| `compileHostJni` | amalgamation + `driver/native/jni/doltrooms_jni.cpp` → `libdoltroomsjni` for the HOST, plus (on macOS) the Konan-cross linux-x64 `.so` — the jar ships both classifiers, xerial-style | `generated.resources` fragment `@jvm`, as `natives/<classifier>/` — exactly where `NativeLibraryLoader` looks; consumers (dolt-write tests) get it via the driver dependency's classpath |
| `compileStaticArchives` | per-target `libdoltlite.a`: linux-x64 host → linuxX64 (gcc + objcopy C23-symbol remaps); macOS → iOS slices (xcrun) AND linuxX64 via Konan's cross toolchain (`~/.konan/dependencies` llvm + gcc sysroot; skipped with a warning until Konan provisions them — rebuild after the first linuxX64 build) | `generateDef` |
| `generateDef` | renders `driver/cinterop/doltlite.def.in` with the absolute include path + `staticLibraries`/`libraryPaths` for exactly the archives that exist | `generated.cinteropDefinitions` fragment `@native` |
| `fetchRemotesrv` | `./kotlin do fetchRemotesrv` (manual, linux-x64 only): the remotesrv test fixture; prints the `JAVA_TOOL_OPTIONS` run prefix | jvm RemoteServerSyncTest |

Design rules encoded there:

- Every failure path names the missing host dependency and its fix
  (JDK-with-jni.h resolution order; xcode-select vs full Xcode;
  gcc/binutils packages) — a fresh machine gets actionable errors
  from a plain `./kotlin build`.
- The `.def` is GENERATED because the def format takes literal paths
  only and the toolchain has no `extraOpts`/`includeDirs` hook; the
  committed template is the reviewable source.
- Host natives in the jvm jar: the jar carries the BUILD HOST's
  library (Gradle always shipped a cross-compiled linux-x64 .so) —
  fine for dev/test, revisit when publishing lands.

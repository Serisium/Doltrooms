# JNI and native-library packaging (Android + desktop JVM)

Verified 2026-07-17 against developer.android.com and precedent
repos.

## JNI essentials (https://developer.android.com/training/articles/perf-jni)

- Loading: "Call `System.loadLibrary` … from a static class
  initializer. The argument is the 'undecorated' library name, so to
  load `libfubar.so` you would pass in `"fubar"`."
- Registration: "Most apps should call `RegisterNatives()` from
  `JNI_OnLoad()`. This performs up-front registration of native
  methods with an explicit mapping" — mismatches fail at load time,
  and only `JNI_OnLoad` needs exporting: "Build with a version script
  (preferred) or use `-fvisibility=hidden`". (`javah` is gone since
  JDK 10; `javac -h` generates headers if you use the
  name-mangled-export style instead — androidx hand-writes
  `sqlite_bindings.cpp`.)
- Threads: "You cannot share a JNIEnv between threads"; native-created
  threads must `AttachCurrentThread()` / `DetachCurrentThread()`.
- Native handles: "use a `long` field rather than an `int` when
  storing a pointer to a native structure in a Java field" — every
  SQLite JNI binding models `sqlite3*`/`sqlite3_stmt*` as a Kotlin
  `Long`.

## Packaging: Android vs desktop are fundamentally different

sqlite-jdbc's usage doc states both halves
(https://github.com/xerial/sqlite-jdbc/blob/master/USAGE.md):

- **Desktop JVM**: "sqlite-jdbc extracts a native library for your OS
  to the directory specified by `java.io.tmpdir`" — the pattern is
  `.so`/`.dylib`/`.dll` under JAR resources (e.g.
  `natives/<os>-<arch>/`), extracted at runtime and `System.load`ed,
  with `-D` overrides (`org.sqlite.lib.path`, `org.sqlite.tmpdir`).
- **Android**: JAR-resource extraction does NOT work — "Android
  expects JNI native libraries to be bundled differently … place them
  in the `jniLibs` directory." The `.so` per ABI (arm64-v8a,
  armeabi-v7a, x86_64) goes in `src/androidMain/jniLibs/<abi>/` (or a
  CMake output dir wired as a jniLibs source directory) inside the
  AAR.

Hence two `NativeLibraryLoader` actuals, exactly like
`androidx.sqlite:sqlite-bundled`, whose build calls
`addNativeLibrariesToJniLibs(...)` for the AAR and
`addNativeLibrariesToResources(...)` for the JVM JAR
(https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-bundled,
build.gradle). androidx also cross-compiles the JVM desktop binaries
(macOS arm64/x64, Linux x64/arm64, Windows x64) with the
Kotlin/Native-bundled clang toolchains so one host builds everything.

## Sharing the Kotlin JNI declarations

Android and desktop JVM share one set of `external fun` declarations
in a custom `jvmAndAndroidMain` source set created with
`dependsOn(commonMain)` — androidx does exactly this
(`sqlite-bundled/src/jvmAndAndroidMain/`), with the single C++ file
`src/jvmAndAndroidMain/jni/sqlite_bindings.cpp` serving both.

## JNA — the shortcut

JNA (https://github.com/java-native-access/jna) "uses a small JNI
library stub to dynamically invoke native code" via libffi — declare a
Kotlin/Java interface mirroring the C API, no glue code, no NDK
build. Costs: "correctness and ease of use take priority" over
performance — per-call overhead is materially higher than hand JNI
even with direct mapping; on Android you need the `@aar` artifact
(`net.java.dev.jna:jna:<v>@aar`, per the JNA FAQ
https://github.com/java-native-access/jna/blob/master/www/FrequentlyAskedQuestions.md)
which adds `libjnidispatch.so` per ABI.

Relevance: `com.dolthub:doltlite-android` is already a JNA wrapper
over `libdoltlite.so` (`doltlite` skill). Reusing it is the zero-NDK
v1 path for the Android rung; hand-written JNI (androidx-style, with
`@FastNative` on hot methods) is the optimization path for the
`step()` loop.

## Recipe summary for the driver

1. One C/C++ binding file (or JNA interface) targeting the
   `sqlite3_*` symbols in `libdoltlite` — symbols are unchanged from
   sqlite3 (`doltlite` skill), so existing sqlite bindings port by
   relinking.
2. Android: NDK-compile `libdoltlite` + glue for arm64-v8a,
   armeabi-v7a, x86_64 → `jniLibs/`; `System.loadLibrary` in a static
   initializer; `RegisterNatives` in `JNI_OnLoad`;
   `-fvisibility=hidden`.
3. Desktop: same glue compiled per OS/arch → JAR resources → extract
   → `System.load`; ship `-D` overrides like sqlite-jdbc's.
4. Handles cross the boundary as `Long`; never cache `JNIEnv*` across
   threads.

---
name: kmp-native-interop
description: Kotlin Multiplatform native-library interop — everything needed to wrap a C library (libdoltlite) across the platform ladder. Use when writing cinterop .def files, Gradle cinterops blocks, CPointer/memScoped/usePinned/staticCFunction/StableRef code, JNI glue and System.loadLibrary packaging (jniLibs in AAR, JAR-resource extraction on desktop), JNA trade-offs, the AGP com.android.kotlin.multiplatform.library plugin, expect/actual and the default source-set hierarchy, XCFramework/CocoaPods consumption, or publishing KMP libraries with native pieces to Maven. Triggers: cinterop, .def file, Kotlin/Native, CPointer, memScoped, usePinned, staticCFunction, StableRef, JNI, jniLibs, JNA, RegisterNatives, expect actual, source set hierarchy, XCFramework, androidLibrary plugin, klib, native library packaging.
---

# KMP native interop — wrapping libdoltlite per platform

## What this skill is

The interop mechanisms for building a KMP library over a C library,
verified against kotlinlang.org and developer.android.com on
2026-07-17. Note a docs migration: KMP docs moved under
`kotlinlang.org/docs/multiplatform/…`; old `multiplatform-*.html`
URLs may render empty pages. Kotlin/Native docs remain at
`/docs/native-*.html`.

## Role in this project

The driver (ARCHITECTURE.md D1) needs one interop mechanism per rung
of the platform ladder (D4):

| Platform | Mechanism | Artifact shape |
|---|---|---|
| JVM desktop | JNI glue; libs in JAR resources, extracted at runtime | JAR |
| Android | Same JNI glue; `.so` per ABI in `jniLibs/` (or DoltLite's existing JNA AAR) | AAR |
| iOS/macOS | cinterop `.def` → klib; static lib per slice | klib (+ XCFramework for Xcode consumers) |
| JS/Wasm | No C interop exists — `external` bindings to an Emscripten build; **dropped** (D4 amendment, PLAN.md Step 9) | — |

The canonical precedent is `androidx.sqlite:sqlite-bundled`, which
does exactly this for stock SQLite — one `commonMain` API,
`jvmAndAndroidMain` JNI, `nativeMain` cinterop
(https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite-bundled).
See the `androidx-sqlite` skill for its internals.

## Decision tree

- Writing or editing a `.def` file, or reading generated bindings
  (`CPointer`, `memScoped`, `toKString`, out-params, callbacks,
  pinning): `references/cinterop.md`.
- JNI glue, native-library loading/packaging on Android vs desktop
  JVM, or weighing JNA vs JNI: `references/jni-and-packaging.md`.
- Target list, source-set hierarchy, expect/actual, the AGP KMP
  library plugin, XCFrameworks, CocoaPods, publishing:
  `references/targets-and-publishing.md`.
- How androidx/SQLiter/SQLDelight/sqlite-jdbc structure the same
  problem: `references/precedents.md`.

## Facts that shape the design (the short list)

- **Kotlin/Native memory model (2.x)**: objects live in a shared heap
  and "can be accessed from any thread"
  (https://kotlinlang.org/docs/native-memory-manager.html) — no
  freezing. Thread-safety of the driver is governed by the C
  library's threading mode plus our own confinement, not the language.
- **`staticCFunction` lambdas "must not capture any values"**
  (https://kotlinlang.org/docs/native-c-interop.html) — callback state
  goes through `StableRef.create(obj).asCPointer()` and must be
  `.dispose()`d.
- **Temporary pointers die early**: `memScoped` allocations and
  `.cstr.ptr` are invalid after scope exit; `CValuesRef` copies die
  when the callee returns. Never let the C side retain them — bind
  with `SQLITE_TRANSIENT` semantics (see `sqlite-c-api`).
- **Android and desktop load native libs differently**: JAR-resource
  extraction works on desktop but "Android expects JNI native
  libraries to be bundled differently … place them in the `jniLibs`
  directory"
  (https://github.com/xerial/sqlite-jdbc/blob/master/USAGE.md). Keep
  two `NativeLibraryLoader` actuals like androidx.
- **JNA is the shortcut, JNI the fast path**: DoltLite's Android AAR
  is a JNA wrapper (`doltlite` skill); JNA works on Android via the
  `@aar` artifact but adds per-ABI `libjnidispatch.so` and per-call
  marshalling overhead — androidx chose hand-written JNI with
  `@FastNative` for the hot `step()` loop.
- **The new AGP plugin** (`com.android.kotlin.multiplatform.library`,
  AGP ≥ 8.10, used by this repo's `library/build.gradle.kts`): single
  variant, no flavors/build types, tests off by default,
  "The legacy `src/main`, `src/test`, and `src/androidTest`
  directories are NOT supported"
  (https://developer.android.com/kotlin/multiplatform/plugin). The old
  `com.android.library`-for-KMP path is slated for removal in AGP 10.
- **expect/actual classes are Beta** — prefer expect
  functions/interfaces at API boundaries, or add
  `-Xexpect-actual-classes`
  (https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html).
- **Apple targets need a macOS host** for cinterop and publishing, and
  "publish all artifacts from a single host" — Maven Central forbids
  duplicate publications
  (https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html).
- **Web forces async**: Kotlin/Wasm interop is "JavaScript-only" — no
  linking against Emscripten C libraries
  (https://kotlinlang.org/docs/wasm-js-interop.html). OPFS drivers are
  suspend-first and single-connection (Room 3's `sqlite-web`). This
  repo's web rung was dropped (D4 amendment, PLAN.md Step 9): the
  common surface deliberately declares the nonWeb (synchronous)
  androidx.sqlite members, which cannot coexist with a web target —
  see `references/targets-and-publishing.md`.

## Authoritative URLs

- C interop: https://kotlinlang.org/docs/native-c-interop.html
- .def files: https://kotlinlang.org/docs/native-definition-file.html
- Gradle cinterops DSL: https://kotlinlang.org/docs/multiplatform/multiplatform-dsl-reference.html
- Hierarchy: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
- Publishing: https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html
- AGP KMP plugin: https://developer.android.com/kotlin/multiplatform/plugin
- JNI tips: https://developer.android.com/training/articles/perf-jni

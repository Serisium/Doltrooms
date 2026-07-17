# Kotlin/Native cinterop — .def files and generated bindings

Verified against kotlinlang.org on 2026-07-17.

## The .def file

Default location: `src/nativeInterop/cinterop/<name>.def`. Keys
(https://kotlinlang.org/docs/native-definition-file.html, quoted):

- `headers` — "The list of headers from a library to be included in
  the bindings."
- `headerFilter` — "Filters headers by globs and includes only them
  when importing a library." (`excludeFilter` takes priority.)
- `compilerOpts` / `linkerOpts` — options passed to the C compiler /
  linker.
- `staticLibraries` — "[Experimental]. Includes a static library into
  `.klib`." (`staticLibraries = libfoo.a`); `libraryPaths` —
  "[Experimental]. A space-separated list of directories where the
  cinterop tool searches for the library".
- `package` — package prefix for the generated Kotlin API.
- `excludedFunctions`, `noStringConversion` ("functions whose
  `const char*` parameters should not be auto-converted to Kotlin
  `String`s"), `strictEnums`/`nonStrictEnums`, `userSetupHint`.
- Per-target variants: `compilerOpts.linux_x64 = -DFOO=foo1` — "any
  definition file option can have both common and platform-specific
  parts."
- Custom C declarations may follow a `---` separator line at the end
  of the file (macro wrappers etc.).

Sketch for this project (mirrors androidx's trivially small
`androidXBundledSqlite.def` and SQLiter's sqlite3.def):

```
package = dev.doltrooms.doltlite.c
headers = doltlite.h
headerFilter = doltlite*.h
noStringConversion = sqlite3_prepare_v2 sqlite3_prepare_v3
staticLibraries = libdoltlite.a
libraryPaths.ios_arm64 = build/ios-arm64/lib
```

`noStringConversion` on the prepare functions is load-bearing: both
SQLiter and androidx disable auto-conversion there because prepare
needs explicit byte lengths and tail pointers. `staticLibraries` is
experimental; the fallback is `linkerOpts` + documenting consumer
linking (SQLiter's `-lsqlite3` approach) with `userSetupHint` for the
resulting linker errors.

## Gradle wiring

From the DSL reference
(https://kotlinlang.org/docs/multiplatform/multiplatform-dsl-reference.html):

```kotlin
kotlin {
    iosArm64 {
        compilations.getByName("main") {
            val doltlite by cinterops.creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/doltlite.def"))
                packageName("dev.doltrooms.doltlite.c")
                compilerOpts("-Ipath/to/headers")
                includeDirs.allHeaders("path1", "path2")
            }
        }
    }
}
```

`definitionFile` is the current property; older builds (SQLDelight's
Groovy) use `defFile`.

## Generated bindings (https://kotlinlang.org/docs/native-c-interop.html)

- "Pointers and arrays are mapped to `CPointer<T>?`" — C NULL is
  Kotlin `null`. `struct S*` → `CPointer<S>`; `char**` →
  `CPointer<CPointerVar<ByteVar>>`; `void*` → `COpaquePointer`.
- Lvalues: "The `.pointed` property for `CPointer<T>` returns the
  lvalue of type `T` … The reverse operation is `.ptr`."
- Function parameters of pointer type accept `CValuesRef<T>`; passing
  a values sequence passes "the pointer to the temporary copy … valid
  only until the function returns."
- Strings: `const char*` params auto-convert from Kotlin `String`
  (UTF-8 assumed); manual: `CPointer<ByteVar>.toKString()`,
  `String.cstr`.
- Allocation: `nativeHeap.alloc/allocArray` + `.free()`, or
  `memScoped { }` — "the allocated memory will be automatically freed
  after leaving the scope"; pointers become invalid at scope exit.
- Structs by value: `CValue<T>` + `useContents { }` / `cValue { }` /
  `readValue()` / `placeTo(scope)`.
- Integer portability: `convert<size_t>()` — Kotlin has no implicit
  integer casts.
- Everything requires `@OptIn(ExperimentalForeignApi::class)`.
- Forward-declared structs surface under `cnames.structs.<name>`.

The open pattern for `sqlite3**` out-params:

```kotlin
memScoped {
    val dbPtr = alloc<CPointerVar<sqlite3>>()
    val rc = sqlite3_open_v2(path, dbPtr.ptr, flags, null)
    // dbPtr.value is CPointer<sqlite3>?
}
```

## Callbacks, pinning, lifetimes

- `staticCFunction(::fn)` converts a Kotlin function to a C function
  pointer; "The function or lambda must not capture any values."
- State through `void*`: `StableRef.create(obj).asCPointer()` on the
  way in, `ptr.asStableRef<T>().get()` in the callback; every
  `StableRef` "must be manually disposed" with `.dispose()` or the
  Kotlin object leaks.
- Byte buffers: `.usePinned { }` "pins an object, executes a block,
  and unpins it on normal and exception paths";
  `pinned.addressOf(0)` for the address; `buffer.refTo(0)` is the
  pin-around-the-call shorthand. This is the blob read/write pattern.
- Never hand DoltLite a scoped pointer it might retain past the call:
  bind text/blobs with `SQLITE_TRANSIENT` (see `sqlite-c-api`) or
  allocate on `nativeHeap`.

## Consuming prebuilt Apple binaries

cinterop consumes **headers + static libs** (or `-framework` linker
opts / CocoaPods `pod()`), not XCFrameworks directly. For the
DoltLite XCFramework (`doltlite` skill), point cinterop at the
per-slice static archive and headers — or wrap it as a pod
(https://kotlinlang.org/docs/multiplatform/multiplatform-cocoapods-libraries.html:
`pod()` auto-generates bindings imported as
`import cocoapods.<PodName>.*`). XCFramework is the *output*
packaging for Xcode consumers of our library
(https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html —
`XCFramework()` helper, `assembleXCFramework` task).

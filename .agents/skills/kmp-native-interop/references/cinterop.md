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

The def this project settled on (PLAN.md Step 6; mirrors androidx's
trivially small `androidXBundledSqlite.def` and SQLiter's sqlite3.def)
is `library/src/nativeInterop/cinterop/doltlite.def`:

```
package = dev.seri.doltrooms.doltlite.c
headers = doltlite.h
headerFilter = doltlite*.h
noStringConversion = sqlite3_prepare_v2 sqlite3_prepare_v3
linkerOpts.linux_x64 = -lpthread -ldl -lm
```

The static archive is NOT in the def (its path lives under `build/`):
the Gradle cinterop block passes
`extraOpts("-staticLibrary", "libdoltlite.a", "-libraryPath", <dir>)`
per target instead — same embedding mechanism as the `staticLibraries`
def key, but with a computable path.

`noStringConversion` on the prepare functions is load-bearing: both
SQLiter and androidx disable auto-conversion there because prepare
needs explicit byte lengths and tail pointers. `staticLibraries` is
experimental; the fallback is `linkerOpts` + documenting consumer
linking (SQLiter's `-lsqlite3` approach) with `userSetupHint` for the
resulting linker errors.

## Gotchas verified in this repo (Kotlin 2.3.10, 2026-07-18)

- **Keep bindings headers-only.** Putting `#include "doltlite.c"` in
  the def's `---` section makes cinterop PARSE the amalgamation too:
  the full `struct sqlite3` definition then generates a real struct
  class in the package on that target, while headers-only targets keep
  the opaque `cnames.structs.sqlite3` — and the shared `nativeMain`
  source set no longer compiles against both. Bind headers only;
  compile the engine separately.
- **Apple targets die on Linux the moment they gain a cinterop.** KGP
  warning, verbatim: "cross compilation to target 'iosArm64' has been
  disabled because it contains cinterops: 'doltlite' which cannot be
  processed on host 'linux_x64'." The iOS compile tasks are SKIPPED
  (build stays green); klib cross-compilation from Linux only works
  for cinterop-free targets. iOS compile/link/test all need a Mac
  (`docs/deferred-verification.md`).
- **Konan links linuxX64 against its own bundled glibc-2.19 sysroot**,
  not the host's. A host-gcc-compiled static archive on a modern
  distro references newer glibc symbols and fails in `ld.lld`:
  `fcntl64` (glibc 2.28 redirect under `_FILE_OFFSET_BITS=64`, which
  sqlite self-defines — neutralized with `-DSQLITE_DISABLE_LFS`, a
  no-op on x86_64 where off_t is 64-bit anyway) and `__isoc23_strtol`
  (glibc 2.38 C23 redirect, forced by `_GNU_SOURCE` regardless of
  `-std` — neutralized with
  `objcopy --redefine-sym __isoc23_strtol=strtol`).
- **`-staticLibrary` archives are not tracked as cinterop task
  inputs.** Rebuilding the archive leaves a stale copy embedded in the
  klib; declare it explicitly
  (`inputs.files(<archiveTask>.map { it.outputs.files })` on the
  cinterop task).
- **Opaque handles import from `cnames.structs`**, not the def's
  package: `import cnames.structs.sqlite3` — the package gets no
  typealias when the typedef name equals the struct tag.

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

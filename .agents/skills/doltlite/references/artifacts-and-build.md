# DoltLite artifacts and building libdoltlite

Verified 2026-07-17. Core, Android, and wasm artifacts were in
lockstep at **0.11.33** (released that day); doltlite-swift lagged at
0.11.17. Releases land near-daily — re-check versions before pinning,
and pin **one** version across platforms (storage format may break
between versions; see SKILL.md).

## Published artifacts

| Platform | Coordinates | Notes |
|---|---|---|
| Android | `com.dolthub:doltlite-android:0.11.33` (Maven Central, confirmed via maven-metadata.xml; repo https://github.com/dolthub/doltlite-android) | A thin **JNA** layer over a bundled `libdoltlite.so`. ABIs: arm64-v8a, armeabi-v7a, x86_64, x86. Ships a Kotlin `Doltlite` class (`doltCommit()` etc.) but **no androidx.sqlite driver**. |
| iOS/macOS | SwiftPM `.package(url: "https://github.com/dolthub/doltlite-swift", from: "0.11.17")` | Prebuilt **XCFramework**; iOS 14+ / macOS 11+ / Catalyst 14+. `import Doltlite` (Swift wrapper) or `import CDoltlite` (raw sqlite3 C API); raw handle via `db.handle`. The XCFramework is the cinterop target for Kotlin/Native. |
| JS/WASM | `npm install @dolthub/doltlite-wasm` (browser; OPFS-backed) and `@dolthub/doltlite` (Node/Bun native) | Both confirmed on the npm registry at 0.11.33 (still `latest` on 2026-07-18 — lockstep with core, but the package versions independently). Browser positioning: "a `.wasm` is a full version-controlled SQL database backed by the browser's private filesystem" (https://www.dolthub.com/blog/2026-04-27-why-doltlite/). Package layout (0.11.33, per its registry `exports` map, https://registry.npmjs.org/@dolthub%2Fdoltlite-wasm): the standard sqlite-wasm `ext/wasm` build — `sqlite3.mjs` + `sqlite3.wasm`, `sqlite3-opfs-async-proxy.js`, `sqlite3-worker1(-promiser).mjs`, bundler-friendly + node variants; no TypeScript types. A Kotlin/Wasm binding would be hand-written `external` declarations over this prebuilt engine — the upstream-prebuilt category ARCHITECTURE.md D9 rejects; the repo's web rung was dropped (D4 amendment). |
| Python / Ruby | `pip install doltlite` / `gem install doltlite` | Listed in the README. |
| Prebuilt libs | GitHub Releases (https://github.com/dolthub/doltlite/releases) | Each release attaches: xcframework, **amalgamation**, autoconf tarball, Linux ARM64/x64, macOS ARM64/x64, and Windows x64 libraries. |

For the driver's platform ladder (ARCHITECTURE.md D4): JVM desktop
uses the release-attached shared/static libs (or a from-source build),
Android uses the AAR's bundled `.so` (or bypasses JNA against the same
`.so`), iOS uses the XCFramework via cinterop.

## Building from source

Standard SQLite autoconf flow (README,
https://github.com/dolthub/doltlite):

```sh
cd build && ../configure && make          # sqlite3-style build
make doltlite-lib                         # libdoltlite.a + libdoltlite.{so,dylib}
                                          #   "with the full prolly tree engine
                                          #    and all Dolt functions included"
make doltlite-remotesrv                   # the remote server
```

- **Amalgamation:** attached to every release — the natural input for
  NDK builds and Kotlin/Native cinterop, exactly as sqlite3.c is used
  by `androidx.sqlite:sqlite-bundled`. **It excludes the
  TLS/credential (mbedTLS/ed25519) stack** — doltlite.c 0.11.33:
  "excluded only from the single-file amalgamation, which links
  neither library" — so amalgamation builds have `file://` + plain
  `http://` remotes only, no https, no bearer auth (see
  `remotes-and-sync.md` "Probed facts"). The prebuilt
  `doltlite-lib-*`/`doltlite-tools-*` release artifacts carry the
  full stack; `doltlite-tools-<os>-<arch>-<v>.zip` contains the
  `doltlite` CLI + `doltlite-remotesrv`.
- WASM: `./configure && make sqlite3.c sqlite3.h`, then
  `make -C ext/wasm`.
- Windows: MSYS2/MINGW64.

## Headers

`doltlite.h` (plus `doltliteext.h`) replaces `sqlite3.h` /
`sqlite3ext.h`; **symbols stay `sqlite3_*`** (README — see SKILL.md
for the verbatim quote). A cinterop `.def` or JNI glue written for
sqlite3 needs only the header include and link target changed.

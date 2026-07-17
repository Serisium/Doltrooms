# Feasibility: Room 3 (KMP) backed by Dolt

**Date:** 2026-07-17
**Question:** Can Room 3's Kotlin Multiplatform library run with its database
backed by a Dolt server? Would Dolt need a "sqlite mode"? Could it run on
Android, iOS, and web clients?

## Verdict

**Room over a Dolt SQL server directly: no.** But DoltHub shipped **DoltLite**
in March 2026 — a SQLite fork with Dolt-style version control that exposes the
standard `sqlite3` C API — and it lines up almost exactly with Room 3's new
driver architecture and platform matrix. The catch: DoltLite does **not**
currently interoperate with a Dolt server; it syncs only with its own remote
server (`doltlite-remotesrv`).

## Why Room can't talk to a Dolt server

Room 3.0 (stable March 2026) went fully KMP — Android, iOS, JVM desktop,
native Mac/Linux, and newly JS/WASM — by rebasing entirely onto the
`androidx.sqlite` **SQLiteDriver** API. Every provided driver
(`BundledSQLiteDriver`, `AndroidSQLiteDriver`, `NativeSQLiteDriver`, the new
OPFS-backed `sqlite-web` worker driver) is a real, local SQLite. Room's
compiler generates and validates SQLite-dialect SQL at build time.

Dolt's server is MySQL: MySQL wire protocol on port 3306, MySQL dialect.
There is no "sqlite mode" for the Dolt server — the two are incompatible at
the dialect layer, the protocol layer, and the driver layer. Dolt *can* be
embedded serverlessly, but only in Go programs, which DoltHub themselves call
a bad fit for iOS/Android.

## The actual bridge: DoltLite as a Room driver

DoltLite replaces SQLite's B-tree/pager with a content-addressed prolly tree
chunk store, leaving the parser, planner, and VDBE untouched — ~18k new lines
of C against 8 modified SQLite lines, with the full SQLite acceptance suite
passing. Critically for Room: existing C programs port by changing
`#include "sqlite3.h"` to `#include <doltlite.h>` — the full `sqlite3_*` API
is preserved.

The integration path is a custom `SQLiteDriver` implementation that links
`libdoltlite` instead of sqlite3 — essentially a re-skin of
`BundledSQLiteDriver`. Room would neither know nor care; `SELECT
dolt_commit(...)`, branches, diffs, and three-way merges become ordinary SQL
calls from DAOs or `useWriterConnection`.

| Room 3 target | DoltLite artifact | Driver work needed |
|---|---|---|
| Android | `com.dolthub:doltlite-android` (AAR + JNA) | JNI/JNA-backed `SQLiteDriver` shim |
| iOS/macOS | SwiftPM XCFramework | Kotlin/Native cinterop against the xcframework |
| Web (JS/WASM) | `@dolthub/doltlite-wasm` npm package | Hardest: reimplement the `sqlite-web` worker driver over doltlite-wasm + OPFS |
| JVM/Linux | static/shared `libdoltlite` | straightforward JNI shim |

No such driver has been published — it would be original glue work, but
well-bounded: `SQLiteDriver` is three interfaces (driver, connection,
statement).

## The gap: DoltLite ≠ Dolt server

DoltLite has grown real sync since the launch post (which said "single
player"): `dolt_clone/push/pull/fetch` over `file://` and `http://` remotes,
served by a standalone `doltlite-remotesrv` (or its in-process embedding
API). But:

- The remote protocol is DoltLite's own; **nothing documents interop with
  Dolt's remotes, DoltHub, or `dolt sql-server`**. Same prolly-tree *design*,
  independent C implementation — the README's framing ("enabling local-first
  use cases for Dolt") reads as roadmap direction, not current compatibility.
- The remote protocol currently has **no auth, no TLS**
  ([doltlite#228](https://github.com/dolthub/doltlite/issues/228)); DoltHub
  says to run it only behind a trusted proxy.
- DoltLite is **alpha** (v0.11.x at time of writing), with an explicit
  warning that the storage format may change without backward compatibility,
  and a ~2.5x performance ceiling versus stock SQLite.

So "Room backed by a Dolt server" today really means "Room backed by
DoltLite, syncing to a `doltlite-remotesrv`" — a separate server, not an
existing Dolt (MySQL) deployment. Bridging DoltLite data into Dolt proper
would be application-level ETL across a SQLite↔MySQL dialect boundary.

## Bottom line

1. **Room ↔ Dolt SQL server: infeasible** — dialect, protocol, and
   driver-model mismatches, with no "sqlite mode" on the server.
2. **Room ↔ DoltLite: feasible and genuinely interesting** — sqlite3-API
   compatibility plus Room 3's pluggable driver design make a KMP driver shim
   a realistic, bounded project. Android and iOS are solid; web is the
   speculative frontier.
3. **The versioned-sync story ends at `doltlite-remotesrv`, not Dolt.** If
   the requirement is that clients push/pull against an existing Dolt store,
   that bridge doesn't exist yet. Given the alpha format-stability and
   security caveats, DoltLite is a watch-and-prototype candidate rather than
   a foundation. The conventional fallback (Room + plain SQLite locally,
   syncing through an application protocol to Dolt) remains the
   boring-but-safe path, at the cost of losing Git-style merge semantics on
   the client.

## Sources

- [Room 3.0 announcement](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html)
- [Room KMP setup](https://developer.android.com/kotlin/multiplatform/room)
- [androidx.sqlite driver docs](https://developer.android.com/kotlin/multiplatform/sqlite)
- [InfoQ on Room 3.0](https://www.infoq.com/news/2026/04/room-3-kotlin-async-sqlite/)
- [Introducing DoltLite](https://www.dolthub.com/blog/2026-03-25-doltlite/)
- [Why DoltLite?](https://www.dolthub.com/blog/2026-04-27-why-doltlite/)
- [dolthub/doltlite README](https://github.com/dolthub/doltlite)
- [Embedding Dolt in Go](https://www.dolthub.com/blog/2022-07-25-embedded/)
- [When NOT to use Dolt](https://www.dolthub.com/blog/2025-12-30-why-not-dolt/)

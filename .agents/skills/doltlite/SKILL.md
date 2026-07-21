---
name: doltlite
description: Reference for DoltLite, DoltHub's SQLite fork with Git-style version control — the native engine this project's Room driver links (ARCHITECTURE.md D2). Use when working with libdoltlite, doltlite.h, the dolt_* SQL functions (dolt_commit, dolt_add, dolt_branch, dolt_checkout, dolt_merge, dolt_diff, dolt_log, dolt_conflicts), remotes and sync (dolt_clone/push/pull/fetch, doltlite-remotesrv), platform artifacts (com.dolthub:doltlite-android AAR, doltlite-swift XCFramework, @dolthub/doltlite-wasm npm), building libdoltlite from source, or assessing version/format stability. Triggers: doltlite, DoltLite, libdoltlite, doltlite.h, prolly tree, dolt_commit, dolt_branch, doltlite-remotesrv, doltlite-android, doltlite-swift, doltlite-wasm, content-addressed storage, versioned SQLite.
---

# DoltLite — the native engine under the driver

## What this skill is

DoltLite (https://github.com/dolthub/doltlite, launch post at
https://www.dolthub.com/blog/2026-03-25-doltlite/) is DoltHub's fork
of SQLite. The README describes it as:

> "A SQLite fork that replaces the B-tree storage engine with a
> content-addressed prolly tree, enabling Git-like version control on
> a SQL database."

Launched 2026-03-25; per the launch post only 8 lines of original
SQLite C were changed, with ~18,000 new lines of C for the storage
engine. DoltLite additions are Apache-2.0; the SQLite core remains
public domain. **Releases land near-daily** — 0.11.33 as of
2026-07-17, when every claim in this skill was verified. Re-verify
versions before relying on them (`skill-maintenance` skill).

## Role in this project

`libdoltlite` is the engine our `androidx.sqlite` driver links instead
of sqlite3 (ARCHITECTURE.md D1/D2). Sync targets DoltLite's own remote
protocol (ARCHITECTURE.md D3) — there is **no documented interop with
Dolt-proper remotes or DoltHub**; treat any such assumption as false
until upstream documents it.

## The load-bearing facts

- **C API compatibility.** README verbatim
  (https://github.com/dolthub/doltlite):
  > "Doltlite exposes the full SQLite C API (`sqlite3_open`,
  > `sqlite3_exec`, `sqlite3_prepare_v2`, ...) through `doltlite.h`.
  > Existing C programs port by changing `#include "sqlite3.h"` to
  > `#include <doltlite.h>` and linking against `libdoltlite` instead
  > of `libsqlite3` — no other source changes — to get version
  > control."

  The **header is renamed** (`doltlite.h`, plus `doltliteext.h`) but
  **`sqlite3_*` symbol names are preserved** — existing sqlite3
  JNI/JNA/cinterop bindings should work symbol-for-symbol against
  `libdoltlite`, changing only the link target.
- **Dual-format engine.** README: DoltLite "detects the file format
  automatically from the header"; standard SQLite files route to the
  original B-tree engine, everything else to the prolly tree. Version
  control applies only to prolly-tree files.
- **Alpha, format-unstable.** Launch post: "Expect some newer versions
  to have a different, non-backwards compatible storage format." Never
  retracted; July release notes still contain storage correctness
  fixes. **Pin one DoltLite version across all platform artifacts** and
  plan a migration story.
- **Query engine is unchanged SQLite.** "DoltLite passes all 5.7M
  tests in sqllogictest"
  (https://www.dolthub.com/blog/2026-04-09-improving-doltlite/).
  Documented deviation: non-INTEGER-PK tables are stored as
  WITHOUT ROWID; divergences are cataloged in the repo's
  `test/known_testfixture_divergences.txt`.
- **Performance.** Reads ~1.20x, batched writes ~1.67x, autocommit
  single-row writes ~4.00x vs stock SQLite
  (https://www.dolthub.com/blog/2026-06-08-how-fast-is-doltlite/); CI
  enforces a 2.5x ceiling on read/write ratios (README).
- **Auth/TLS shipped mid-2026 — the README lags.** Issue
  https://github.com/dolthub/doltlite/issues/228 ("no TLS, no auth")
  was **closed 2026-07-09** by PR
  https://github.com/dolthub/doltlite/pull/1585 — TLS 1.3 minimum via
  mbedTLS plus bearer-JWT auth (release 0.11.28). The README's "run
  only on trusted networks" warning predates this. **Require ≥0.11.28
  for any network sync**; compression is still pending (issue #1584).
  **But: prebuilt artifacts only** — the amalgamation excludes the
  whole TLS/credential stack, so engines built from it (all of this
  repo's, D9) sync over `file://` + plain `http://` regardless of
  version (probed 0.11.33; `references/remotes-and-sync.md`).

## Gotchas for the Room driver

1. WAL/threading semantics are **undocumented**, and 0.11.27 shipped
   "WAL/index corruption fixes" — verify journal-mode behavior
   empirically before enabling anything in Room (see the
   `sqlite-c-api` skill's fork-divergence watchlist).
2. The content-addressed store grows with history; `dolt_gc()` exists
   and was hardened through 0.11.32 ("GC now streams rewrites for
   large databases") — a mobile driver must schedule explicit GC.
3. Schema objects (views/triggers/indexes) behaved incorrectly across
   version control until **0.11.33** — Room schema migrations across
   branches need at least that version.
4. Artifact skew is real: core/Android/wasm were at 0.11.33 on
   2026-07-17 while doltlite-swift's latest observed tag was 0.11.17.
5. `com.dolthub:doltlite-android` is a thin **JNA** wrapper over a
   bundled `libdoltlite.so` — it ships **no androidx.sqlite driver**;
   that is exactly the gap this project fills (ARCHITECTURE.md §1).
6. Foreign keys ARE enforced when `PRAGMA foreign_keys` is on (Room 3
   enables it per connection): a child insert with no parent row
   fails as a constraint violation at 0.11.33 — pinned in-repo by
   `samples/codelab` `CartDaoTest
   .insert_foreignKeyConstraintViolation_throwsException`, green on
   Android hardware 2026-07-21. Upstream docs are silent on FK
   support; treat this test as the citation.

## When to load reference files

- The `dolt_*` SQL surface (functions, system/virtual tables, conflict
  handling, syntax examples, differences from Dolt-proper):
  `references/version-control-sql.md`.
- Remotes, `doltlite-remotesrv`, the embedding API, the auth/TLS
  timeline, and the no-Dolt-interop verdict:
  `references/remotes-and-sync.md`.
- Platform artifacts (Maven/SwiftPM/npm coordinates), prebuilt
  release assets, and building `libdoltlite` from source:
  `references/artifacts-and-build.md`.

## Authoritative URLs

- Repo: https://github.com/dolthub/doltlite
- Demo/tutorial: https://github.com/dolthub/doltlite/blob/master/doc/doltlite/demo.md
- Releases: https://github.com/dolthub/doltlite/releases
- Blog: https://www.dolthub.com/blog/2026-03-25-doltlite/ ·
  https://www.dolthub.com/blog/2026-04-09-improving-doltlite/ ·
  https://www.dolthub.com/blog/2026-04-27-why-doltlite/ ·
  https://www.dolthub.com/blog/2026-06-08-how-fast-is-doltlite/

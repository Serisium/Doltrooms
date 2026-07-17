---
name: dolt
description: Reference for Dolt, the Git-for-data MySQL-compatible database — conceptual template for DoltLite's version-control SQL and the currently-unbridged sync target (ARCHITECTURE.md D2/D3). Use when comparing DoltLite's dolt_* surface against Dolt-proper's stored procedures and system tables, when asked why Room cannot talk to a Dolt SQL server or why there is no "sqlite mode", when reasoning about Dolt remotes/DoltHub/prolly-tree chunk storage and possible future DoltLite interop, or about Dolt's Go-only embedded mode. Triggers: dolt, dolt sql-server, DoltHub, Doltgres, CALL dolt_commit, dolt_diff, dolt_history, dolt system tables, prolly tree, chunk store, remotesapi, embedded dolt, git for data, MySQL wire protocol, cell-level merge.
---

# Dolt — the upstream this project deliberately does not connect to

## What this skill is

Dolt (https://github.com/dolthub/dolt, docs at
https://www.dolthub.com/docs/introduction/what-is-dolt) is a
MySQL-compatible OLTP database with Git semantics in the storage
engine — commits, branches, tags, remotes, diffs, cell-level merge —
all exposed through SQL. Verified 2026-07-17. Note:
`docs.dolthub.com/*` now 301-redirects to `https://www.dolthub.com/docs/*`;
cite the latter.

## Role in this project

Two roles, both indirect (ARCHITECTURE.md D2/D3):

1. **Conceptual template.** DoltLite mirrors Dolt's `dolt_*` SQL
   surface and prolly-tree storage design; Dolt's docs are the
   de-facto spec for what DoltLite's functions mean. When a DoltLite
   behavior is undocumented, read the matching Dolt procedure/system
   table doc — then verify DoltLite actually implements it (`doltlite`
   skill).
2. **Unbridged sync target.** Nothing syncs DoltLite to Dolt or
   DoltHub, and nothing here may assume otherwise.

## Why Room cannot talk to a Dolt server (settled, D2)

- Dolt is a server speaking the MySQL wire protocol and MySQL dialect:
  "To use Dolt from your application you stand up `dolt sql-server`,
  point a MySQL client at port 3306, and talk wire protocol. … It's
  the wrong model for local-first"
  (https://www.dolthub.com/blog/2026-04-27-why-doltlite/).
- There is no "sqlite mode": dialects are separate products at
  DoltHub — Dolt (MySQL), Doltgres (Postgres, 1.0 announced for
  August 2026,
  https://www.dolthub.com/blog/2026-06-26-doltgres-1-0-coming-this-fall/),
  DoltLite (SQLite).
- Embedded mode is Go-only: a `database/sql` driver (driver name
  `"dolt"`) exists since 2022
  (https://www.dolthub.com/blog/2022-07-25-embedded/), but "Go is
  awkward to link into iOS apps, Python C extensions, Ruby gems, Node
  addons…" and Go/WASM "produces multi-megabyte bundles"
  (https://www.dolthub.com/blog/2026-04-27-why-doltlite/). DoltHub's
  own why-not-Dolt post flags Go-only embedding as the iOS blocker
  (https://www.dolthub.com/blog/2025-12-30-why-not-dolt/).

## Interop outlook (watch, don't assume)

Dolt remotes support DoltHub, DoltLab, `file://`, Git remotes
(`git+https://`/`git+ssh://`, added v1.81.10, 2026-02), AWS/GCS/OCI,
Azure, SSH, HTTP(S), and other sql-servers via the gRPC remotesapi
(https://www.dolthub.com/docs/sql-reference/version-control/remotes).
Storage is a content-addressed prolly-tree chunk store — trees are
history-independent, "if the hash of the root chunk is the same the
entire subtree is the same", and push/pull is chunk transfer
(https://www.dolthub.com/docs/architecture/storage-engine/prolly-tree).

DoltLite shares that *concept* but not the implementation: chunk
boundaries, hashing, serialization, and wire protocol all differ, and
Dolt 2.0 (2026-05) diverged the format further with archive dictionary
compression (https://www.dolthub.com/blog/2026-05-11-dolt-2-dot-0/).
**No DoltHub statement through 2026-07-17 announces or roadmaps
Dolt↔DoltLite sync.** If that changes, it is a new ARCHITECTURE.md
decision, not an edit to existing ones.

## When to load reference files

- The full version-control SQL surface — stored procedures with
  syntax, system tables, conflict tables, table functions — the spec
  DoltLite mirrors: `references/version-control-sql.md`.

## Authoritative URLs

- Repo: https://github.com/dolthub/dolt
- What is Dolt: https://www.dolthub.com/docs/introduction/what-is-dolt
- SQL procedures: https://www.dolthub.com/docs/sql-reference/version-control/dolt-sql-procedures
- System tables: https://www.dolthub.com/docs/sql-reference/version-control/dolt-system-tables
- Remotes: https://www.dolthub.com/docs/sql-reference/version-control/remotes
- Prolly trees: https://www.dolthub.com/docs/architecture/storage-engine/prolly-tree
- Why not Dolt: https://www.dolthub.com/blog/2025-12-30-why-not-dolt/

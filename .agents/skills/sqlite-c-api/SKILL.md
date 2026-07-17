---
name: sqlite-c-api
description: The SQLite C API (sqlite3_*) from a driver-implementer's perspective — the surface our androidx.sqlite driver wraps over libdoltlite. Use when implementing or reviewing driver code that calls sqlite3_open_v2, sqlite3_prepare_v2/v3, sqlite3_step, sqlite3_bind_*, sqlite3_column_*, sqlite3_reset, sqlite3_finalize, sqlite3_errmsg, or when reasoning about threading modes (NOMUTEX/FULLMUTEX), SQLITE_BUSY vs SQLITE_LOCKED, WAL and journal modes, busy_timeout, autocommit/transactions, last_insert_rowid, PRAGMA user_version, or where a SQLite fork like DoltLite can diverge from stock behavior. Triggers: sqlite3, C API, prepared statement, bind, step, column, result code, extended error code, threading mode, WAL, busy handler, in-memory database, rowid.
---

# SQLite C API — for driver implementers

## What this skill is

The subset of the SQLite C API (https://www.sqlite.org/c3ref/intro.html)
that an `androidx.sqlite` driver implementation must wrap: the
open/close lifecycle, the prepare→bind→step→reset→finalize statement
state machine, column reads with SQLite's coercion rules, error
reporting, threading modes, and the connection-level accessors Room
depends on. All claims verified against sqlite.org on 2026-07-17.

## Role in this project

The bridge links `libdoltlite`, which preserves the `sqlite3_*` C API
(ARCHITECTURE.md D2); this skill documents *stock* SQLite semantics —
the contract our driver programs against — plus a watchlist of where a
storage-engine fork can diverge. For DoltLite-specific behavior, load
the `doltlite` skill; for the Kotlin interface being implemented, load
`androidx-sqlite`.

## The mental model

- A **prepared statement is a state machine**: prepared → executing
  (`SQLITE_ROW`\*) → done → reset. `sqlite3_step` returns `SQLITE_ROW`
  per row and `SQLITE_DONE` at the end, after which it "should not be
  called again … without first calling sqlite3_reset()"
  (https://www.sqlite.org/c3ref/step.html). Binding after a step
  without a reset returns `SQLITE_MISUSE`.
- **Bind indexes are 1-based; column indexes are 0-based**
  (https://www.sqlite.org/c3ref/bind_blob.html,
  https://www.sqlite.org/c3ref/column_blob.html).
- **Error detail lives on the connection**: capture `sqlite3_errmsg`
  immediately after a failing call, before any other call on that
  connection can overwrite it
  (https://www.sqlite.org/c3ref/errcode.html). Use `prepare_v2`/`v3`
  so `sqlite3_step` returns specific error codes instead of a generic
  `SQLITE_ERROR` (https://www.sqlite.org/c3ref/step.html).
- **`sqlite3_reset` does not clear bindings** — "Contrary to the
  intuition of many" — that is `sqlite3_clear_bindings`
  (https://www.sqlite.org/c3ref/clear_bindings.html).
- **Text/blob bind lifetime**: `SQLITE_TRANSIENT` makes SQLite copy
  the buffer before returning; `SQLITE_STATIC` requires the buffer to
  stay valid until finalize/rebind
  (https://www.sqlite.org/c3ref/bind_blob.html). From JNI/cinterop,
  `SQLITE_TRANSIENT` is the safe default.
- **Threading**: default mode is serialized; `SQLITE_OPEN_NOMUTEX`
  selects multi-thread mode where "no single database connection nor
  any object derived from [it] is used in two or more threads at the
  same time" (https://www.sqlite.org/threadsafe.html). If the driver
  opens NOMUTEX for speed, the driver itself must confine each
  connection to one thread at a time. Even serialized mode does not
  make step→column-read or step→errmsg *sequences* atomic.

## Implementation checklist (condensed)

1. Open: `sqlite3_open_v2` with `READWRITE|CREATE` (+ `NOMUTEX` if the
   driver confines connections, + `URI` for `file:` names). A handle
   usually comes back **even on error** and must still be passed to
   `sqlite3_close` (https://www.sqlite.org/c3ref/open.html).
2. Close: finalize all statements, then `sqlite3_close`; `SQLITE_BUSY`
   from close means a leaked statement. `close_v2` is the
   garbage-collected-language variant
   (https://www.sqlite.org/c3ref/close.html).
3. Prepare with `prepare_v2`, or `prepare_v3` +
   `SQLITE_PREPARE_PERSISTENT` for cached statements; `nByte = -1`
   for NUL-terminated SQL; check `*pzTail` to reject multi-statement
   strings (https://www.sqlite.org/c3ref/prepare.html).
4. Read `sqlite3_column_type` **before** any value accessor — "After a
   type conversion, the result of calling sqlite3_column_type() is
   undefined" (https://www.sqlite.org/c3ref/column_blob.html). For
   byte lengths, call `column_text`/`column_blob` first, then
   `column_bytes`.
5. Copy column names immediately — the pointer is invalidated by
   finalize, re-prepare, or the next `column_name` call
   (https://www.sqlite.org/c3ref/column_name.html).
6. Transactions are plain SQL (`BEGIN`/`COMMIT`/`ROLLBACK`); implement
   `inTransaction()` as `sqlite3_get_autocommit(db) == 0`, and after
   in-transaction errors check autocommit to detect automatic rollback
   (https://www.sqlite.org/c3ref/get_autocommit.html).
7. Set `sqlite3_busy_timeout` per connection; still handle
   `SQLITE_BUSY` (other connection, retryable) vs `SQLITE_LOCKED`
   (same connection / shared cache) (https://www.sqlite.org/rescode.html).
8. Room support surface: `PRAGMA user_version` (schema version),
   `sqlite3_last_insert_rowid`, `sqlite3_changes` — read immediately,
   on the same confined connection
   (https://www.sqlite.org/c3ref/last_insert_rowid.html,
   https://www.sqlite.org/c3ref/changes.html).
9. At init, fingerprint the library: `sqlite3_libversion_number()`
   and `sqlite3_compileoption_used()`/`_get()`
   (https://www.sqlite.org/c3ref/libversion.html,
   https://www.sqlite.org/c3ref/compileoption_get.html) — essential
   when the linked library is a fork.

## Fork-divergence watchlist

DoltLite replaces the pager/B-tree layer and keeps the parser/VDBE, so
divergence risk splits cleanly:

- **Likely to diverge (storage/pager/VFS-level):** journal modes and
  WAL (`PRAGMA journal_mode=WAL` may no-op or be rejected — test what
  the pragma actually returns), file locking and busy-timeout
  behavior, the on-disk header (`user_version` is defined as "offset
  60 in the database header",
  https://www.sqlite.org/pragma.html#pragma_user_version — a fork must
  emulate it; verify round-tripping), multi-connection access to one
  file, `:memory:`/`cache=shared`/temp databases, rowid identity.
- **Likely preserved (parser/VDBE-level):** the statement state
  machine, bind/column index bases, `SQLITE_TRANSIENT` copying, the
  type-coercion table, `column_count`/`data_count`, error plumbing,
  `get_autocommit`, threading-mode mutexes.

Prefer runtime probes (open a temp DB, try the pragma, check the
returned string) over trusting version numbers.

## When to load reference files

- Full lifecycle and statement semantics, with verbatim quotes (open
  flags, close vs close_v2, prepare variants, step return codes,
  bind/column contracts, coercion table):
  `references/lifecycle-and-statements.md`.
- Error model (primary vs extended result codes, errcode/errmsg/errstr
  rules) and threading modes in detail:
  `references/errors-and-threading.md`.
- Transactions, autocommit, journal modes, WAL, busy handling, and the
  in-memory/temp database forms:
  `references/transactions-and-concurrency.md`.

## Authoritative URLs

- C API index: https://www.sqlite.org/c3ref/intro.html
- Result codes: https://www.sqlite.org/rescode.html
- Threading: https://www.sqlite.org/threadsafe.html
- WAL: https://www.sqlite.org/wal.html
- Pragmas: https://www.sqlite.org/pragma.html

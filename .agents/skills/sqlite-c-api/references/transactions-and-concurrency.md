# Transactions, journal modes, and concurrency (verbatim contracts)

Verified against sqlite.org on 2026-07-17. Storage-level behaviors on
this page are the most likely to diverge in DoltLite (see SKILL.md's
fork-divergence watchlist) — probe at runtime.

## Autocommit and transactions

There is no C-API transaction function; `BEGIN`/`COMMIT`/`ROLLBACK`
are ordinary SQL executed via prepare/step.

https://www.sqlite.org/c3ref/get_autocommit.html:

> "Autocommit mode is on by default. Autocommit mode is disabled by a
> BEGIN statement. Autocommit mode is re-enabled by a COMMIT or
> ROLLBACK."

> "If certain kinds of errors occur … (errors including SQLITE_FULL,
> SQLITE_IOERR, SQLITE_NOMEM, SQLITE_BUSY, and SQLITE_INTERRUPT) then
> the transaction might be rolled back automatically. The only way to
> find out whether SQLite automatically rolled back the transaction
> after an error is to use this function [sqlite3_get_autocommit]."

Driver rules: implement `inTransaction()` as
`sqlite3_get_autocommit(db) == 0`; after an in-transaction error,
check autocommit before issuing your own ROLLBACK.

## Journal modes

https://www.sqlite.org/pragma.html#pragma_journal_mode:

- Modes: `DELETE | TRUNCATE | PERSIST | MEMORY | WAL | OFF`; "The
  DELETE journaling mode is the default."
- "The WAL journaling mode is persistent; after being set it stays in
  effect across multiple database connections and after closing and
  reopening the database."

## WAL

https://www.sqlite.org/wal.html:

> "WAL provides more concurrency as readers do not block writers and
> a writer does not block readers."

- "since there is only one WAL file, there can only be one writer at
  a time."
- "All processes using a database must be on the same host computer;
  WAL does not work over a network filesystem."
- "By default, SQLite does a checkpoint automatically when the WAL
  file reaches a threshold size of 1000 pages."

## Busy handling

https://www.sqlite.org/c3ref/busy_timeout.html:

> "This routine [sqlite3_busy_timeout] sets a busy handler that
> sleeps for a specified amount of time when a table is locked. The
> handler will sleep multiple times until at least 'ms' milliseconds
> of sleeping have accumulated."

- "Calling this routine with an argument less than or equal to zero
  turns off all busy handlers."
- "There can only be a single busy handler for a particular database
  connection at any given moment."
- BUSY can still surface from `step` and from `COMMIT`; distinguish
  BUSY (other connection, retryable) from LOCKED (same connection /
  shared cache — retry usually pointless). See
  `errors-and-threading.md`.

## Connection-level accessors Room relies on

- **`PRAGMA user_version`** — Room's schema-version store: "will get
  or set the value of the user-version integer at offset 60 in the
  database header. … SQLite makes no use of the user-version itself."
  (https://www.sqlite.org/pragma.html#pragma_user_version). Fork
  note: defined in terms of the on-disk header — verify DoltLite
  round-trips it.
- **`sqlite3_last_insert_rowid`** — "usually returns the rowid of the
  most recent successful INSERT into a rowid table or virtual table
  on database connection D"; 0 if none; WITHOUT ROWID inserts "are
  not recorded"; cross-thread use on one connection is
  "unpredictable". (https://www.sqlite.org/c3ref/last_insert_rowid.html)
  Fork note: DoltLite stores non-INTEGER-PK tables as WITHOUT ROWID
  (`doltlite` skill) — rowid-dependent code must account for it.
- **`sqlite3_changes`** — "the number of rows modified, inserted or
  deleted by the most recently completed INSERT, UPDATE or DELETE
  statement"; trigger/FK/REPLACE side effects "are not counted."
  (https://www.sqlite.org/c3ref/changes.html). `total_changes` counts
  all since the connection opened, including trigger programs
  (https://www.sqlite.org/c3ref/total_changes.html).

## In-memory and temp databases

https://www.sqlite.org/inmemorydb.html:

- "Every :memory: database is distinct from every other."
- Sharing requires URI filenames: `file::memory:?cache=shared`, or
  named: `file:memdb1?mode=memory&cache=shared` (URI docs:
  https://www.sqlite.org/uri.html).
- Empty-string filename: "a new temporary file is created to hold the
  database"; "Temporary databases are automatically deleted when the
  connection that created them closes."

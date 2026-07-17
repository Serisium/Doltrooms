# Lifecycle and statement semantics (verbatim contracts)

Verified against sqlite.org on 2026-07-17.

## Opening

`sqlite3_open_v2` requires one of three base flag combinations
(https://www.sqlite.org/c3ref/open.html):

> "The flags parameter to sqlite3_open_v2() must include, at a
> minimum, one of the following three flag combinations:
> SQLITE_OPEN_READONLY, SQLITE_OPEN_READWRITE,
> SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE"

Flag semantics (same page):

- `READWRITE`: "opened for reading and writing if possible, or
  reading only if the file is write protected … the database must
  already exist, otherwise an error is returned."
- `READWRITE|CREATE`: "created if it does not already exist. This is
  the behavior that is always used for sqlite3_open()."
- `NOMUTEX`: "the 'multi-thread' threading mode … separate threads
  are allowed to use SQLite at the same time, as long as each thread
  is using a different database connection."
- `FULLMUTEX`: "the 'serialized' threading mode … multiple threads
  can safely attempt to use the same database connection at the same
  time."
- `URI`: "The filename can be interpreted as a URI if this flag is
  set."
- `MEMORY`: "opened as an in-memory database … named by the
  'filename' argument for the purposes of cache-sharing."

Error path — the handle usually exists even on failure and must be
closed:

> "A database connection handle is usually returned in \*ppDb, even
> if an error occurs. The only exception is that if SQLite is unable
> to allocate memory to hold the sqlite3 object, a NULL will be
> written into \*ppDb"

> "Whether or not an error occurs when it is opened, resources
> associated with the database connection handle should be released
> by passing it to sqlite3_close() when it is no longer required."

## Closing

https://www.sqlite.org/c3ref/close.html:

> "If the database connection is associated with unfinalized prepared
> statements … sqlite3_close() will leave the database connection
> open and return SQLITE_BUSY. If sqlite3_close_v2() is called with
> unfinalized prepared statements … it returns SQLITE_OK regardless,
> but … marks the database connection as an unusable 'zombie' and
> makes arrangements to automatically deallocate the database
> connection after all prepared statements are finalized"

> "The sqlite3_close_v2() interface is intended for use with host
> languages that are garbage collected, and where the order in which
> destructors are called is arbitrary."

Calling either with NULL "is a harmless no-op."

## Preparing

https://www.sqlite.org/c3ref/prepare.html:

> "The sqlite3_prepare_v2(), sqlite3_prepare_v3() … interfaces are
> recommended for all new programs."

- `prepare_v3` adds `prepFlags` — `SQLITE_PREPARE_PERSISTENT` for
  statements that will be cached and reused.
- "If the nByte argument is negative, then zSql is read up to the
  first zero terminator."
- "These routines only compile the first statement in zSql, so
  \*pzTail is left pointing to what remains uncompiled." — check
  `*pzTail` to reject multi-statement strings.
- v2/v3 auto-reprepare on schema change: "instead of returning
  SQLITE_SCHEMA … sqlite3_step() will automatically recompile the
  SQL statement and try to run it again."

## Stepping

https://www.sqlite.org/c3ref/step.html:

> "If the SQL statement being executed returns any data, then
> SQLITE_ROW is returned each time a new row of data is ready"

> "SQLITE_DONE means that the statement has finished executing
> successfully. sqlite3_step() should not be called again on this
> virtual machine without first calling sqlite3_reset()"

> "SQLITE_BUSY means that the database engine was unable to acquire
> the database locks it needs to do its job."

Why v2 prepare matters for error codes:

> "In the legacy interface, the sqlite3_step() API always returns a
> generic error code, SQLITE_ERROR, following any error other than
> SQLITE_BUSY and SQLITE_MISUSE. … The problem has been fixed with
> the 'v2' interface … the more specific error codes are returned
> directly by sqlite3_step()."

Since 3.6.23.1, step after a non-ROW result auto-resets instead of
returning MISUSE — but drivers should reset explicitly, not rely on
this.

## Reset / clear_bindings / finalize

- "The sqlite3_reset(S) interface does not change the values of any
  bindings on the prepared statement S."
  (https://www.sqlite.org/c3ref/reset.html)
- "Contrary to the intuition of many, sqlite3_reset() does not reset
  the bindings on a prepared statement. Use this routine
  [sqlite3_clear_bindings] to reset all host parameters to NULL."
  (https://www.sqlite.org/c3ref/clear_bindings.html)
- Finalize (https://www.sqlite.org/c3ref/finalize.html): "If the most
  recent evaluation of statement S failed, then sqlite3_finalize(S)
  returns the appropriate error code" — an error return reports the
  last evaluation, not a finalize failure. "Any use of a prepared
  statement after it has been finalized can result in undefined and
  undesirable behavior such as segfaults and heap corruption."

## Binding (1-based)

https://www.sqlite.org/c3ref/bind_blob.html:

- "The leftmost SQL parameter has an index of 1."
- "SQLITE_RANGE is returned if the parameter index is out of range."
- "Unbound parameters are interpreted as NULL."
- Bind after step without reset → `SQLITE_MISUSE`.
- Negative length to `bind_blob` is **undefined** (unlike `bind_text`,
  where negative means NUL-terminated).

Lifetime contract — the key FFI decision:

> "SQLITE_STATIC … the object and the provided pointer to it must
> remain valid until either the prepared statement is finalized or
> the same SQL parameter is bound to something else"

> "SQLITE_TRANSIENT … the object is to be copied prior to the return
> from sqlite3_bind_*(). … SQLite will then manage the lifetime of
> its private copy."

## Reading columns (0-based)

https://www.sqlite.org/c3ref/column_blob.html:

- "The leftmost column of the result set has the index 0."
- `sqlite3_column_type` returns one of `SQLITE_INTEGER`,
  `SQLITE_FLOAT`, `SQLITE_TEXT`, `SQLITE_BLOB`, `SQLITE_NULL`.
- "These routines may only be called when the most recent call to
  sqlite3_step() has returned SQLITE_ROW"
- "After a type conversion, the result of calling
  sqlite3_column_type() is undefined" — call `column_type` first.

Coercion table (same page): NULL→INTEGER = 0; NULL→FLOAT = 0.0;
NULL→TEXT/BLOB = NULL pointer; INTEGER→FLOAT = convert; INTEGER→TEXT
= ASCII rendering; FLOAT→INTEGER = CAST; TEXT→INTEGER/FLOAT = CAST;
TEXT→BLOB = no change; BLOB→TEXT = "CAST to TEXT, ensure zero
terminator."

Safe byte-length pattern: call `column_text`/`column_blob` first to
force the format, then `column_bytes`; "Do not mix calls to
sqlite3_column_text() or sqlite3_column_blob() with calls to
sqlite3_column_bytes16()."

## Counts and names

- `sqlite3_column_count`: "Return the number of columns in the result
  set returned by the prepared statement" — 0 means the statement
  returns no data (e.g. UPDATE). Static metadata, usable before
  stepping. (https://www.sqlite.org/c3ref/column_count.html)
- `sqlite3_data_count`: "returns the number of columns in the current
  row", 0 after `SQLITE_DONE`. Per-row.
  (https://www.sqlite.org/c3ref/data_count.html)
- Column names (https://www.sqlite.org/c3ref/column_name.html): "the
  value of the 'AS' clause … If there is no AS clause then the name
  of the column is unspecified and may change from one release of
  SQLite to the next." The returned pointer is valid only until
  finalize, automatic re-prepare, or "the next call to
  sqlite3_column_name() … on the same column" — copy immediately.

# Error model and threading modes (verbatim contracts)

Verified against sqlite.org on 2026-07-17.

## Result codes

https://www.sqlite.org/rescode.html:

> "Result codes are signed 32-bit integers. The least significant 8
> bits of the result code define a broad category and are called the
> 'primary result code'. More significant bits … are called the
> 'extended result code.'"

- The primary code is always recoverable "merely by extracting the
  least significant 8 bits".
- "For historic compatibility, the C-language interfaces return
  primary result codes by default. … The
  sqlite3_extended_result_codes() interface can be used to put a
  database connection into a mode where it returns the extended
  result codes"

BUSY vs LOCKED (same page):

> "SQLITE_BUSY indicates a conflict with a separate database
> connection, probably in a separate process, whereas SQLITE_LOCKED
> indicates a conflict within the same database connection (or
> sometimes a database connection with a shared cache)."

## Reading error info

https://www.sqlite.org/c3ref/errcode.html:

- `sqlite3_errcode(D)`: "If the most recent sqlite3_* API call
  associated with database connection D failed, then the
  sqlite3_errcode(D) interface returns the numeric result code or
  extended result code for that API call."
- `sqlite3_extended_errcode(D)`: "always returns the extended result
  code even when extended result codes are disabled."
- `sqlite3_errmsg(D)`: English text describing the error — "the error
  string might be overwritten or deallocated by subsequent calls to
  other SQLite interface functions." **Capture it immediately.**
- `sqlite3_errstr(E)`: maps a result code to text with no connection
  required — good for building exception messages from codes alone.
- `sqlite3_error_offset()`: byte offset of the offending SQL token,
  or -1.

Where errors surface in a driver:

- `prepare_v2/v3` return real codes directly.
- `step` on a v2-prepared statement returns specific codes directly
  (see `lifecycle-and-statements.md`).
- `finalize`/`reset` echo the last evaluation's error — not new
  failures of their own.

## Threading modes

https://www.sqlite.org/threadsafe.html:

> Single-thread: "all mutexes are disabled and SQLite is unsafe to
> use in more than a single thread at once."

> Multi-thread: "SQLite can be safely used by multiple threads
> provided that no single database connection nor any object derived
> from database connection, such as a prepared statement, is used in
> two or more threads at the same time."

> Serialized: "API calls to affect or use any SQLite database
> connection or any object derived from such a database connection
> can be made safely from multiple threads. The effect on an
> individual object is the same as if the API calls had all been made
> in the same order from a single thread."

- "The default mode is serialized."
- Selection layers: compile-time `SQLITE_THREADSAFE` (0=single,
  2=multi, 1=serialized/default), start-time `sqlite3_config()`,
  per-connection `SQLITE_OPEN_NOMUTEX` (multi-thread) /
  `SQLITE_OPEN_FULLMUTEX` (serialized).
- `sqlite3_threadsafe()` "returns zero if and only if SQLite was
  compiled with mutexing code omitted due to the SQLITE_THREADSAFE
  compile-time option being set to 0" — it reports the compile-time
  setting only (https://www.sqlite.org/c3ref/threadsafe.html). Assert
  it is nonzero at driver init.

## Driver implications

- Opening with `NOMUTEX` (what androidx's bundled driver does for
  performance) shifts the burden to the driver: a connection and its
  statements must never be used from two threads concurrently —
  enforce with pooling + per-connection confinement.
- Serialized mode makes individual API calls safe but **not**
  multi-call sequences: step→column-reads and fail→errmsg are not
  atomic. Higher-level confinement is required for correct semantics
  regardless of mode.

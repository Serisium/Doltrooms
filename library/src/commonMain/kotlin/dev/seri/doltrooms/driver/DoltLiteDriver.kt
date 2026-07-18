package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

/**
 * A [SQLiteDriver] backed by DoltLite, DoltHub's SQLite fork with Git-style
 * version control — a re-skin of `BundledSQLiteDriver` over `libdoltlite`
 * (ARCHITECTURE.md D1/D2).
 *
 * The driver has no internal connection pool ([SQLiteDriver.hasConnectionPool]
 * stays `false`): the native library is compiled with `SQLITE_THREADSAFE=2`,
 * so connections are NOT thread-safe and confinement is the job of a higher
 * abstraction such as Room's connection pool.
 */
public expect class DoltLiteDriver() : SQLiteDriver

/**
 * A [SQLiteConnection] over a native DoltLite database handle
 * (`sqlite3*`). Created by [DoltLiteDriver]; not thread-safe.
 */
public expect class DoltLiteConnection : SQLiteConnection

/**
 * A [SQLiteStatement] over a native DoltLite prepared statement
 * (`sqlite3_stmt*`). Created by [DoltLiteConnection.prepare]; not
 * thread-safe.
 */
public expect class DoltLiteStatement : SQLiteStatement

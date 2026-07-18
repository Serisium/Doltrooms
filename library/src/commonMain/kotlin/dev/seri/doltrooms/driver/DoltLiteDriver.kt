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
public expect class DoltLiteDriver() : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection
}

/**
 * A [SQLiteConnection] over a native DoltLite database handle
 * (`sqlite3*`). Created by [DoltLiteDriver]; not thread-safe.
 */
public expect class DoltLiteConnection : SQLiteConnection {
    override fun prepare(sql: String): SQLiteStatement

    override fun close()
}

/**
 * A [SQLiteStatement] over a native DoltLite prepared statement
 * (`sqlite3_stmt*`). Created by [DoltLiteConnection.prepare]; not
 * thread-safe.
 */
public expect class DoltLiteStatement : SQLiteStatement {
    override fun bindBlob(index: Int, value: ByteArray)

    override fun bindDouble(index: Int, value: Double)

    override fun bindLong(index: Int, value: Long)

    override fun bindText(index: Int, value: String)

    override fun bindNull(index: Int)

    override fun getBlob(index: Int): ByteArray

    override fun getDouble(index: Int): Double

    override fun getLong(index: Int): Long

    override fun getText(index: Int): String

    override fun isNull(index: Int): Boolean

    override fun getColumnCount(): Int

    override fun getColumnName(index: Int): String

    override fun getColumnType(index: Int): Int

    override fun step(): Boolean

    override fun reset()

    override fun clearBindings()

    override fun close()
}

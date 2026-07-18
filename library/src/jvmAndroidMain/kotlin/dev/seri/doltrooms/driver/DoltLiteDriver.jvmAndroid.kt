package dev.seri.doltrooms.driver

import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.throwSQLiteException

// Open flags passed to sqlite3_open_v2 — BundledSQLiteDriver's defaults
// (androidx-sqlite skill, driver-interfaces reference).
private const val SQLITE_OPEN_READWRITE = 0x02
private const val SQLITE_OPEN_CREATE = 0x04

// sqlite3 result codes the driver branches on (https://www.sqlite.org/rescode.html).
private const val SQLITE_OK = 0
private const val SQLITE_MISUSE = 21
private const val SQLITE_RANGE = 25
private const val SQLITE_ROW = 100
private const val SQLITE_DONE = 101

public actual class DoltLiteDriver actual constructor() : SQLiteDriver {

    actual override fun open(fileName: String): SQLiteConnection {
        val rcOut = IntArray(1)
        val dbPointer = DoltLiteNative.nativeOpen(
            fileName,
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE,
            rcOut,
        )
        if (rcOut[0] != SQLITE_OK) {
            // A handle usually comes back even on error and must still be
            // released (https://www.sqlite.org/c3ref/open.html). Like
            // BundledSQLiteDriver, throw without errmsg — db may be null.
            if (dbPointer != 0L) {
                DoltLiteNative.nativeClose(dbPointer)
            }
            throwSQLiteException(rcOut[0], null)
        }
        return DoltLiteConnection(dbPointer)
    }
}

public actual class DoltLiteConnection internal constructor(
    private val dbPointer: Long,
) : SQLiteConnection {

    @Volatile
    private var isClosed = false

    actual override fun prepare(sql: String): SQLiteStatement {
        throwIfClosed()
        val stmtOut = LongArray(1)
        val rc = DoltLiteNative.nativePrepare(dbPointer, sql, stmtOut)
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
        return DoltLiteStatement(dbPointer, stmtOut[0])
    }

    actual override fun close() {
        if (!isClosed) {
            isClosed = true
            DoltLiteNative.nativeClose(dbPointer)
        }
    }

    private fun throwIfClosed() {
        if (isClosed) {
            throwSQLiteException(SQLITE_MISUSE, "connection is closed")
        }
    }
}

public actual class DoltLiteStatement internal constructor(
    private val dbPointer: Long,
    private val stmtPointer: Long,
) : SQLiteStatement {

    @Volatile
    private var isClosed = false

    actual override fun step(): Boolean {
        throwIfClosed()
        return when (val rc = DoltLiteNative.nativeStep(stmtPointer)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
    }

    actual override fun close() {
        // "Any use of a prepared statement after it has been finalized can
        // result in undefined and undesirable behavior such as segfaults"
        // (https://www.sqlite.org/c3ref/finalize.html) — the guard below is
        // load-bearing, not cosmetic.
        if (!isClosed) {
            isClosed = true
            DoltLiteNative.nativeFinalize(stmtPointer)
        }
    }

    actual override fun bindBlob(index: Int, value: ByteArray) {
        throwIfClosed()
        checkBindResult(DoltLiteNative.nativeBindBlob(stmtPointer, index, value))
    }

    actual override fun bindDouble(index: Int, value: Double) {
        throwIfClosed()
        checkBindResult(DoltLiteNative.nativeBindDouble(stmtPointer, index, value))
    }

    actual override fun bindLong(index: Int, value: Long) {
        throwIfClosed()
        checkBindResult(DoltLiteNative.nativeBindLong(stmtPointer, index, value))
    }

    actual override fun bindText(index: Int, value: String) {
        throwIfClosed()
        checkBindResult(DoltLiteNative.nativeBindText(stmtPointer, index, value))
    }

    actual override fun bindNull(index: Int) {
        throwIfClosed()
        checkBindResult(DoltLiteNative.nativeBindNull(stmtPointer, index))
    }

    actual override fun getBlob(index: Int): ByteArray {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return DoltLiteNative.nativeColumnBlob(stmtPointer, index)
    }

    actual override fun getDouble(index: Int): Double {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return DoltLiteNative.nativeColumnDouble(stmtPointer, index)
    }

    actual override fun getLong(index: Int): Long {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return DoltLiteNative.nativeColumnLong(stmtPointer, index)
    }

    actual override fun getText(index: Int): String {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return DoltLiteNative.nativeColumnText(stmtPointer, index)
            ?: throwSQLiteException(SQLITE_MISUSE, "no text value at column $index")
    }

    actual override fun isNull(index: Int): Boolean {
        return getColumnType(index) == SQLITE_DATA_NULL
    }

    actual override fun getColumnCount(): Int {
        throwIfClosed()
        return DoltLiteNative.nativeColumnCount(stmtPointer)
    }

    actual override fun getColumnName(index: Int): String {
        throwIfClosed()
        throwIfInvalidColumn(index)
        // Null column name means allocation failure (bundled-driver template).
        return DoltLiteNative.nativeColumnName(stmtPointer, index) ?: throw OutOfMemoryError()
    }

    actual override fun getColumnType(index: Int): Int {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return DoltLiteNative.nativeColumnType(stmtPointer, index)
    }

    actual override fun reset() {
        throwIfClosed()
        val rc = DoltLiteNative.nativeReset(stmtPointer)
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
    }

    actual override fun clearBindings() {
        throwIfClosed()
        val rc = DoltLiteNative.nativeClearBindings(stmtPointer)
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
    }

    private fun throwIfClosed() {
        if (isClosed) {
            throwSQLiteException(SQLITE_MISUSE, "statement is closed")
        }
    }

    // Value getters are only valid while positioned on a row (bundled-driver
    // template's throwIfNoRow via sqlite3_stmt_busy).
    private fun throwIfNoRow() {
        if (DoltLiteNative.nativeStmtBusy(stmtPointer) == 0) {
            throwSQLiteException(SQLITE_MISUSE, "no row")
        }
    }

    private fun throwIfInvalidColumn(index: Int) {
        if (index < 0 || index >= DoltLiteNative.nativeColumnCount(stmtPointer)) {
            throwSQLiteException(SQLITE_RANGE, "column index out of range")
        }
    }

    // Bind failures carry a real result code (e.g. SQLITE_RANGE) and the
    // connection has the detail message (bundled-driver template).
    private fun checkBindResult(rc: Int) {
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
    }
}

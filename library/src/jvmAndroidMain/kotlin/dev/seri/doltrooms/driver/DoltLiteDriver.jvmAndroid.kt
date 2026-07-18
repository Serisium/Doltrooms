package dev.seri.doltrooms.driver

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
private const val SQLITE_ROW = 100
private const val SQLITE_DONE = 101

public actual class DoltLiteDriver actual constructor() : SQLiteDriver {

    override fun open(fileName: String): SQLiteConnection {
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

    override fun prepare(sql: String): SQLiteStatement {
        val stmtOut = LongArray(1)
        val rc = DoltLiteNative.nativePrepare(dbPointer, sql, stmtOut)
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
        return DoltLiteStatement(dbPointer, stmtOut[0])
    }

    override fun close() {
        DoltLiteNative.nativeClose(dbPointer)
    }
}

public actual class DoltLiteStatement internal constructor(
    private val dbPointer: Long,
    private val stmtPointer: Long,
) : SQLiteStatement {

    override fun step(): Boolean {
        return when (val rc = DoltLiteNative.nativeStep(stmtPointer)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throwSQLiteException(rc, DoltLiteNative.nativeErrmsg(dbPointer))
        }
    }

    override fun close() {
        DoltLiteNative.nativeFinalize(stmtPointer)
    }

    // The full bind*/get*/reset surface is PLAN.md Step 3.
    override fun bindBlob(index: Int, value: ByteArray): Unit = TODO("PLAN.md Step 3")

    override fun bindDouble(index: Int, value: Double): Unit = TODO("PLAN.md Step 3")

    override fun bindLong(index: Int, value: Long): Unit = TODO("PLAN.md Step 3")

    override fun bindText(index: Int, value: String): Unit = TODO("PLAN.md Step 3")

    override fun bindNull(index: Int): Unit = TODO("PLAN.md Step 3")

    override fun getBlob(index: Int): ByteArray = TODO("PLAN.md Step 3")

    override fun getDouble(index: Int): Double = TODO("PLAN.md Step 3")

    override fun getLong(index: Int): Long = TODO("PLAN.md Step 3")

    override fun getText(index: Int): String = TODO("PLAN.md Step 3")

    override fun isNull(index: Int): Boolean = TODO("PLAN.md Step 3")

    override fun getColumnCount(): Int = TODO("PLAN.md Step 3")

    override fun getColumnName(index: Int): String = TODO("PLAN.md Step 3")

    override fun getColumnType(index: Int): Int = TODO("PLAN.md Step 3")

    override fun reset(): Unit = TODO("PLAN.md Step 3")

    override fun clearBindings(): Unit = TODO("PLAN.md Step 3")
}

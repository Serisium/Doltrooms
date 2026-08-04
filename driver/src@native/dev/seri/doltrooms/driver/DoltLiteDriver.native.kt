// The Kotlin/Native (cinterop) DoltLite driver — same semantics as the
// jvmAndroidMain actuals, calling dev.seri.doltrooms.doltlite.c bindings
// instead of DoltLiteNative; modeled on androidx sqlite-framework's
// NativeSQLiteDriver/Connection/Statement (androidx-sqlite skill,
// bundled-driver-internals: "JNI and native implement identically").
@file:OptIn(ExperimentalForeignApi::class)

package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.throwSQLiteException
// The opaque sqlite3/sqlite3_stmt handles are forward-declared structs, which
// cinterop surfaces under cnames.structs (kmp-native-interop skill, cinterop
// reference), not under the .def's package.
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import dev.seri.doltrooms.doltlite.c.SQLITE_DONE
import dev.seri.doltrooms.doltlite.c.SQLITE_MISUSE
import dev.seri.doltrooms.doltlite.c.SQLITE_NULL
import dev.seri.doltrooms.doltlite.c.SQLITE_OK
import dev.seri.doltrooms.doltlite.c.SQLITE_OPEN_CREATE
import dev.seri.doltrooms.doltlite.c.SQLITE_OPEN_READWRITE
import dev.seri.doltrooms.doltlite.c.SQLITE_RANGE
import dev.seri.doltrooms.doltlite.c.SQLITE_ROW
import dev.seri.doltrooms.doltlite.c.sqlite3_bind_blob
import dev.seri.doltrooms.doltlite.c.sqlite3_bind_double
import dev.seri.doltrooms.doltlite.c.sqlite3_bind_int64
import dev.seri.doltrooms.doltlite.c.sqlite3_bind_null
import dev.seri.doltrooms.doltlite.c.sqlite3_bind_text16
import dev.seri.doltrooms.doltlite.c.sqlite3_bind_zeroblob
import dev.seri.doltrooms.doltlite.c.sqlite3_clear_bindings
import dev.seri.doltrooms.doltlite.c.sqlite3_close_v2
import dev.seri.doltrooms.doltlite.c.sqlite3_column_blob
import dev.seri.doltrooms.doltlite.c.sqlite3_column_bytes
import dev.seri.doltrooms.doltlite.c.sqlite3_column_bytes16
import dev.seri.doltrooms.doltlite.c.sqlite3_column_count
import dev.seri.doltrooms.doltlite.c.sqlite3_column_double
import dev.seri.doltrooms.doltlite.c.sqlite3_column_int64
import dev.seri.doltrooms.doltlite.c.sqlite3_column_name
import dev.seri.doltrooms.doltlite.c.sqlite3_column_text16
import dev.seri.doltrooms.doltlite.c.sqlite3_column_type
import dev.seri.doltrooms.doltlite.c.sqlite3_errmsg
import dev.seri.doltrooms.doltlite.c.sqlite3_extended_result_codes
import dev.seri.doltrooms.doltlite.c.sqlite3_finalize
import dev.seri.doltrooms.doltlite.c.sqlite3_get_autocommit
import dev.seri.doltrooms.doltlite.c.sqlite3_open_v2
import dev.seri.doltrooms.doltlite.c.sqlite3_prepare16_v2
import dev.seri.doltrooms.doltlite.c.sqlite3_reset
import dev.seri.doltrooms.doltlite.c.sqlite3_step
import dev.seri.doltrooms.doltlite.c.sqlite3_stmt_busy
import kotlin.concurrent.Volatile
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.utf16
import kotlinx.cinterop.value

// The SQLITE_TRANSIENT sentinel ((sqlite3_destructor_type)-1) is a macro
// cast cinterop cannot expose; recreate it like SQLiter does. It makes
// sqlite copy text/blob buffers before returning, so pinned/temporary
// Kotlin memory is safe to release right after the bind call.
private val SQLITE_TRANSIENT: CPointer<CFunction<(COpaquePointer?) -> Unit>> =
    (-1L).toCPointer()!!

private fun errmsg(dbPointer: CPointer<sqlite3>): String? =
    sqlite3_errmsg(dbPointer)?.toKString()

public actual class DoltLiteDriver actual constructor() : SQLiteDriver {

    actual override fun open(fileName: String): SQLiteConnection = memScoped {
        val dbOut = allocPointerTo<sqlite3>()
        val rc = sqlite3_open_v2(
            fileName,
            dbOut.ptr,
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE,
            null,
        )
        val dbPointer = dbOut.value
        if (rc != SQLITE_OK) {
            // A handle usually comes back even on error and must still be
            // released (https://www.sqlite.org/c3ref/open.html). Like
            // BundledSQLiteDriver, throw without errmsg — db may be null.
            if (dbPointer != null) {
                sqlite3_close_v2(dbPointer)
            }
            throwSQLiteException(rc, null)
        }
        sqlite3_extended_result_codes(dbPointer, 1)
        DoltLiteConnection(dbPointer!!)
    }
}

public actual class DoltLiteConnection internal constructor(
    private val dbPointer: CPointer<sqlite3>,
) : SQLiteConnection {

    @Volatile
    private var isClosed = false

    actual override fun inTransaction(): Boolean {
        throwIfClosed()
        // "Autocommit mode is disabled by a BEGIN statement"
        // (https://www.sqlite.org/c3ref/get_autocommit.html).
        return sqlite3_get_autocommit(dbPointer) == 0
    }

    actual override fun prepare(sql: String): SQLiteStatement {
        throwIfClosed()
        return memScoped {
            val stmtOut = allocPointerTo<sqlite3_stmt>()
            // UTF-16 prepare, byte length excluding the terminator — the
            // same call shape as the JNI glue.
            val rc = sqlite3_prepare16_v2(dbPointer, sql.utf16, sql.length * 2, stmtOut.ptr, null)
            if (rc != SQLITE_OK) {
                throwSQLiteException(rc, errmsg(dbPointer))
            }
            DoltLiteStatement(dbPointer, stmtOut.value!!)
        }
    }

    actual override fun close() {
        if (!isClosed) {
            isClosed = true
            sqlite3_close_v2(dbPointer)
        }
    }

    private fun throwIfClosed() {
        if (isClosed) {
            throwSQLiteException(SQLITE_MISUSE, "connection is closed")
        }
    }
}

public actual class DoltLiteStatement internal constructor(
    private val dbPointer: CPointer<sqlite3>,
    private val stmtPointer: CPointer<sqlite3_stmt>,
) : SQLiteStatement {

    @Volatile
    private var isClosed = false

    actual override fun step(): Boolean {
        throwIfClosed()
        return when (val rc = sqlite3_step(stmtPointer)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throwSQLiteException(rc, errmsg(dbPointer))
        }
    }

    actual override fun close() {
        // "Any use of a prepared statement after it has been finalized can
        // result in undefined and undesirable behavior such as segfaults"
        // (https://www.sqlite.org/c3ref/finalize.html) — the guard below is
        // load-bearing, not cosmetic.
        if (!isClosed) {
            isClosed = true
            sqlite3_finalize(stmtPointer)
        }
    }

    actual override fun bindBlob(index: Int, value: ByteArray) {
        throwIfClosed()
        // A NULL data pointer would bind SQL NULL, so an empty array binds a
        // zero-length blob explicitly (and cannot be pinned at index 0).
        val rc = if (value.isEmpty()) {
            sqlite3_bind_zeroblob(stmtPointer, index, 0)
        } else {
            value.usePinned { pinned ->
                sqlite3_bind_blob(stmtPointer, index, pinned.addressOf(0), value.size, SQLITE_TRANSIENT)
            }
        }
        checkBindResult(rc)
    }

    actual override fun bindDouble(index: Int, value: Double) {
        throwIfClosed()
        checkBindResult(sqlite3_bind_double(stmtPointer, index, value))
    }

    actual override fun bindLong(index: Int, value: Long) {
        throwIfClosed()
        checkBindResult(sqlite3_bind_int64(stmtPointer, index, value))
    }

    actual override fun bindText(index: Int, value: String) {
        throwIfClosed()
        checkBindResult(
            sqlite3_bind_text16(stmtPointer, index, value.utf16, value.length * 2, SQLITE_TRANSIENT)
        )
    }

    actual override fun bindNull(index: Int) {
        throwIfClosed()
        checkBindResult(sqlite3_bind_null(stmtPointer, index))
    }

    actual override fun getBlob(index: Int): ByteArray {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        // Call column_blob before column_bytes
        // (https://www.sqlite.org/c3ref/column_blob.html). NULL values and
        // zero-length blobs both yield a NULL pointer.
        val blob = sqlite3_column_blob(stmtPointer, index)
            ?: return ByteArray(0)
        val length = sqlite3_column_bytes(stmtPointer, index)
        return if (length == 0) ByteArray(0) else blob.readBytes(length)
    }

    actual override fun getDouble(index: Int): Double {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return sqlite3_column_double(stmtPointer, index)
    }

    actual override fun getLong(index: Int): Long {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return sqlite3_column_int64(stmtPointer, index)
    }

    actual override fun getText(index: Int): String {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        // Call column_text16 before column_bytes16
        // (https://www.sqlite.org/c3ref/column_blob.html); length-based read
        // like the JNI glue, so embedded NULs survive.
        val text = sqlite3_column_text16(stmtPointer, index)
            ?.reinterpret<UShortVar>()
            ?: throwSQLiteException(SQLITE_MISUSE, "no text value at column $index")
        val chars = sqlite3_column_bytes16(stmtPointer, index) / 2
        return CharArray(chars) { text[it].toInt().toChar() }.concatToString()
    }

    actual override fun isNull(index: Int): Boolean {
        return getColumnType(index) == SQLITE_NULL
    }

    actual override fun getColumnCount(): Int {
        throwIfClosed()
        return sqlite3_column_count(stmtPointer)
    }

    actual override fun getColumnName(index: Int): String {
        throwIfClosed()
        throwIfInvalidColumn(index)
        // Null column name means allocation failure (bundled-driver template).
        return sqlite3_column_name(stmtPointer, index)?.toKString() ?: throw OutOfMemoryError()
    }

    actual override fun getColumnType(index: Int): Int {
        throwIfClosed()
        throwIfNoRow()
        throwIfInvalidColumn(index)
        return sqlite3_column_type(stmtPointer, index)
    }

    actual override fun reset() {
        throwIfClosed()
        val rc = sqlite3_reset(stmtPointer)
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, errmsg(dbPointer))
        }
    }

    actual override fun clearBindings() {
        throwIfClosed()
        val rc = sqlite3_clear_bindings(stmtPointer)
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, errmsg(dbPointer))
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
        if (sqlite3_stmt_busy(stmtPointer) == 0) {
            throwSQLiteException(SQLITE_MISUSE, "no row")
        }
    }

    private fun throwIfInvalidColumn(index: Int) {
        if (index < 0 || index >= sqlite3_column_count(stmtPointer)) {
            throwSQLiteException(SQLITE_RANGE, "column index out of range")
        }
    }

    // Bind failures carry a real result code (e.g. SQLITE_RANGE) and the
    // connection has the detail message (bundled-driver template).
    private fun checkBindResult(rc: Int) {
        if (rc != SQLITE_OK) {
            throwSQLiteException(rc, errmsg(dbPointer))
        }
    }
}

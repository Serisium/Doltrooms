package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

// Open flags passed to sqlite3_open_v2 — BundledSQLiteDriver's defaults
// (androidx-sqlite skill, driver-interfaces reference).
private const val SQLITE_OPEN_READWRITE = 0x02
private const val SQLITE_OPEN_CREATE = 0x04

public actual class DoltLiteDriver actual constructor() : SQLiteDriver {

    override fun open(fileName: String): SQLiteConnection {
        val rcOut = IntArray(1)
        val dbPointer = DoltLiteNative.nativeOpen(
            fileName,
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE,
            rcOut,
        )
        return DoltLiteConnection(dbPointer)
    }
}

public actual class DoltLiteConnection internal constructor(
    private val dbPointer: Long,
) : SQLiteConnection {

    override fun prepare(sql: String): SQLiteStatement {
        TODO("prepare arrives with the execSQL increment")
    }

    override fun close() {
        DoltLiteNative.nativeClose(dbPointer)
    }
}

package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

// Stub actuals: the native (cinterop) implementation is PLAN.md Step 6.
// These exist only so commonMain's expect declarations compile for the
// linuxX64/iOS targets until then.

public actual class DoltLiteDriver actual constructor() : SQLiteDriver {
    actual override fun open(fileName: String): SQLiteConnection {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }
}

public actual class DoltLiteConnection private constructor() : SQLiteConnection {
    actual override fun inTransaction(): Boolean {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }

    actual override fun prepare(sql: String): SQLiteStatement {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }

    actual override fun close() {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }
}

public actual class DoltLiteStatement private constructor() : SQLiteStatement {
    actual override fun bindBlob(index: Int, value: ByteArray): Unit = TODO("PLAN.md Step 6")

    actual override fun bindDouble(index: Int, value: Double): Unit = TODO("PLAN.md Step 6")

    actual override fun bindLong(index: Int, value: Long): Unit = TODO("PLAN.md Step 6")

    actual override fun bindText(index: Int, value: String): Unit = TODO("PLAN.md Step 6")

    actual override fun bindNull(index: Int): Unit = TODO("PLAN.md Step 6")

    actual override fun getBlob(index: Int): ByteArray = TODO("PLAN.md Step 6")

    actual override fun getDouble(index: Int): Double = TODO("PLAN.md Step 6")

    actual override fun getLong(index: Int): Long = TODO("PLAN.md Step 6")

    actual override fun getText(index: Int): String = TODO("PLAN.md Step 6")

    actual override fun isNull(index: Int): Boolean = TODO("PLAN.md Step 6")

    actual override fun getColumnCount(): Int = TODO("PLAN.md Step 6")

    actual override fun getColumnName(index: Int): String = TODO("PLAN.md Step 6")

    actual override fun getColumnType(index: Int): Int = TODO("PLAN.md Step 6")

    actual override fun step(): Boolean = TODO("PLAN.md Step 6")

    actual override fun reset(): Unit = TODO("PLAN.md Step 6")

    actual override fun clearBindings(): Unit = TODO("PLAN.md Step 6")

    actual override fun close(): Unit = TODO("PLAN.md Step 6")
}

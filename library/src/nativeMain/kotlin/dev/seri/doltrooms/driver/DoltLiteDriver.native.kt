package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

// Stub actuals: the native (cinterop) implementation is PLAN.md Step 6.
// These exist only so commonMain's expect declarations compile for the
// linuxX64/iOS targets until then.

public actual class DoltLiteDriver actual constructor() : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }
}

public actual class DoltLiteConnection private constructor() : SQLiteConnection {
    override fun prepare(sql: String): SQLiteStatement {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }

    override fun close() {
        TODO("DoltLite native (cinterop) driver arrives with PLAN.md Step 6")
    }
}

public actual class DoltLiteStatement private constructor() : SQLiteStatement {
    override fun bindBlob(index: Int, value: ByteArray): Unit = TODO("PLAN.md Step 6")

    override fun bindDouble(index: Int, value: Double): Unit = TODO("PLAN.md Step 6")

    override fun bindLong(index: Int, value: Long): Unit = TODO("PLAN.md Step 6")

    override fun bindText(index: Int, value: String): Unit = TODO("PLAN.md Step 6")

    override fun bindNull(index: Int): Unit = TODO("PLAN.md Step 6")

    override fun getBlob(index: Int): ByteArray = TODO("PLAN.md Step 6")

    override fun getDouble(index: Int): Double = TODO("PLAN.md Step 6")

    override fun getLong(index: Int): Long = TODO("PLAN.md Step 6")

    override fun getText(index: Int): String = TODO("PLAN.md Step 6")

    override fun isNull(index: Int): Boolean = TODO("PLAN.md Step 6")

    override fun getColumnCount(): Int = TODO("PLAN.md Step 6")

    override fun getColumnName(index: Int): String = TODO("PLAN.md Step 6")

    override fun getColumnType(index: Int): Int = TODO("PLAN.md Step 6")

    override fun step(): Boolean = TODO("PLAN.md Step 6")

    override fun reset(): Unit = TODO("PLAN.md Step 6")

    override fun clearBindings(): Unit = TODO("PLAN.md Step 6")

    override fun close(): Unit = TODO("PLAN.md Step 6")
}

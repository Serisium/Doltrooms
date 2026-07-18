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

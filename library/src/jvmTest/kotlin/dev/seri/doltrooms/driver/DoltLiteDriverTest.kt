package dev.seri.doltrooms.driver

import androidx.sqlite.execSQL
import kotlin.test.Test

// Step 2 test list (red-green; add cases as they occur):
// - [x] open(":memory:") returns a connection; close() releases it
// - [ ] execSQL runs DDL (CREATE TABLE)
// - [ ] open failure throws SQLiteException carrying the sqlite error code
// - [ ] prepare on a closed connection throws ("connection is closed")
// - [ ] connection close is idempotent
// - [ ] SELECT dolt_version() returns the pinned DoltLite version (queued from Step 1)
class DoltLiteDriverTest {

    @Test
    fun openAndCloseInMemoryConnection() {
        val driver = DoltLiteDriver()
        val connection = driver.open(":memory:")
        connection.close()
    }

    @Test
    fun execSqlRunsDdl() {
        val connection = DoltLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE test_table (id INTEGER PRIMARY KEY, name TEXT)")
        } finally {
            connection.close()
        }
    }
}

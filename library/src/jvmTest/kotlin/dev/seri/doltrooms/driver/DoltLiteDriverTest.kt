package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Step 2 test list (red-green; add cases as they occur):
// - [x] open(":memory:") returns a connection; close() releases it
// - [x] execSQL runs DDL (CREATE TABLE)
// - [x] open failure: DoltLite defers CANTOPEN to the first step (fork divergence)
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
    fun openFailureIsDeferredToFirstStatement() {
        // Fork divergence, probed 2026-07-17 (DoltLite 0.11.33 vs stock
        // 3.50.2): stock sqlite3_open_v2 fails eagerly with SQLITE_CANTOPEN
        // (14) for a file in a missing directory; DoltLite opens lazily and
        // surfaces the error at the first sqlite3_step. Eager open failures
        // still exist (e.g. READONLY on a missing file), so the driver maps
        // errors in both places.
        val connection = DoltLiteDriver().open("/doltrooms-nonexistent-dir/sub/db.sqlite")
        try {
            val e = assertFailsWith<SQLiteException> {
                connection.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY)")
            }
            assertTrue("Error code: 14" in (e.message ?: ""), "unexpected message: ${e.message}")
        } finally {
            connection.close()
        }
    }

    @Test
    fun prepareOnClosedConnectionThrows() {
        val connection = DoltLiteDriver().open(":memory:")
        connection.close()
        val e = assertFailsWith<SQLiteException> { connection.prepare("SELECT 1") }
        assertTrue("connection is closed" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test
    fun connectionCloseIsIdempotent() {
        val connection = DoltLiteDriver().open(":memory:")
        connection.close()
        // Contract: "Calling this function on an already closed database
        // connection is a no-op."
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

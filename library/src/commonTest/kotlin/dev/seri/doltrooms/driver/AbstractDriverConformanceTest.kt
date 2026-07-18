package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Step 3 differential conformance suite: the identical tests run against
// DoltLiteDriver AND BundledSQLiteDriver (the oracle) via per-platform
// concrete subclasses (room3 skill, testing reference; red-green skill,
// "differential green").
//
// Test list (red-green; add cases as they occur):
// - [ ] bindLong/getLong roundtrip through INSERT + SELECT
// - [ ] bindText/getText roundtrip
// - [ ] bindDouble/getDouble roundtrip
// - [ ] bindBlob/getBlob roundtrip
// - [ ] bindNull + isNull; unbound parameter reads as NULL
// - [ ] getColumnType for all five data types
// - [ ] getColumnCount/getColumnName/getColumnNames; 0 columns for non-query
// - [ ] step over multiple rows
// - [ ] reset retains bindings; re-execution after reset
// - [ ] rebind after reset replaces the old value
// - [ ] clearBindings makes parameters NULL
// - [ ] get* out of column range throws (SQLITE_RANGE)
// - [ ] get* with no row available throws (SQLITE_MISUSE "no row")
// - [ ] bind out of parameter range throws (SQLITE_RANGE)
// - [ ] statement close is idempotent; use after close throws
// - [ ] inTransaction false/true across BEGIN/COMMIT/ROLLBACK
// - [ ] ROLLBACK discards writes; COMMIT persists them
// - [ ] multi-connection file db: 4 readers + 1 writer (WAL shape)
// - [ ] connection is not thread-affine (sequential use across threads)
abstract class AbstractDriverConformanceTest {

    /** The driver under test — DoltLite or the Bundled oracle. */
    abstract fun driver(): SQLiteDriver

    private inline fun withConnection(block: (SQLiteConnection) -> Unit) {
        driver().open(":memory:").use(block)
    }

    @Test
    fun bindLongRoundTrips() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindLong(1, 42L)
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertEquals(42L, query.getLong(0))
                assertFalse(query.step())
            }
        }
    }
}

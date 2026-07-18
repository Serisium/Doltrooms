package dev.seri.doltrooms.driver

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Step 3 differential conformance suite: the identical tests run against
// DoltLiteDriver AND BundledSQLiteDriver (the oracle) via per-platform
// concrete subclasses (room3 skill, testing reference; red-green skill,
// "differential green").
//
// Test list (red-green; add cases as they occur):
// - [x] bindLong/getLong roundtrip through INSERT + SELECT
// - [x] bindText/getText roundtrip (incl. non-ASCII / surrogate pairs)
// - [x] bindDouble/getDouble roundtrip
// - [x] bindBlob/getBlob roundtrip (incl. empty blob)
// - [x] bindNull + isNull; unbound parameter reads as NULL
// - [x] getColumnType for all five data types
// - [ ] getColumnCount/getColumnName/getColumnNames; 0 columns for non-query
// - [ ] column metadata is readable before the first step
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
    fun bindTextRoundTrips() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v TEXT)")
            // Non-ASCII incl. a surrogate pair exercises the UTF-16 path.
            val value = "héllo wörld é世🚀"
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindText(1, value)
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertEquals(value, query.getText(0))
            }
        }
    }

    @Test
    fun bindDoubleRoundTrips() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v REAL)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindDouble(1, 3.14159265358979)
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertEquals(3.14159265358979, query.getDouble(0))
            }
        }
    }

    @Test
    fun bindBlobRoundTrips() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v BLOB)")
            val value = byteArrayOf(0, 1, -1, 127, -128, 42)
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindBlob(1, value)
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertContentEquals(value, query.getBlob(0))
            }
        }
    }

    @Test
    fun emptyBlobRoundTripsAsEmpty() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v BLOB)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindBlob(1, ByteArray(0))
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertFalse(query.isNull(0))
                assertContentEquals(ByteArray(0), query.getBlob(0))
            }
        }
    }

    @Test
    fun bindNullReadsAsNull() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v TEXT)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindNull(1)
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertTrue(query.isNull(0))
            }
        }
    }

    @Test
    fun unboundParameterReadsAsNull() {
        // "Unbound parameters are interpreted as NULL."
        // (https://www.sqlite.org/c3ref/bind_blob.html)
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v TEXT)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertTrue(query.isNull(0))
            }
        }
    }

    @Test
    fun getColumnTypeReportsAllFiveTypes() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (i INTEGER, f REAL, s TEXT, b BLOB, n TEXT)")
            connection.prepare("INSERT INTO t VALUES (?, ?, ?, ?, ?)").use { insert ->
                insert.bindLong(1, 7L)
                insert.bindDouble(2, 1.5)
                insert.bindText(3, "text")
                insert.bindBlob(4, byteArrayOf(1))
                insert.bindNull(5)
                assertFalse(insert.step())
            }
            connection.prepare("SELECT i, f, s, b, n FROM t").use { query ->
                assertTrue(query.step())
                assertEquals(SQLITE_DATA_INTEGER, query.getColumnType(0))
                assertEquals(SQLITE_DATA_FLOAT, query.getColumnType(1))
                assertEquals(SQLITE_DATA_TEXT, query.getColumnType(2))
                assertEquals(SQLITE_DATA_BLOB, query.getColumnType(3))
                assertEquals(SQLITE_DATA_NULL, query.getColumnType(4))
            }
        }
    }

    @Test
    fun columnMetadataIsReadableBeforeStepping() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (a INTEGER, b TEXT)")
            connection.prepare("SELECT a, b AS renamed FROM t").use { query ->
                // "Static metadata, usable before stepping"
                // (https://www.sqlite.org/c3ref/column_count.html).
                assertEquals(2, query.getColumnCount())
                assertEquals("a", query.getColumnName(0))
                assertEquals("renamed", query.getColumnName(1))
                assertEquals(listOf("a", "renamed"), query.getColumnNames())
            }
        }
    }

    @Test
    fun nonQueryStatementHasZeroColumns() {
        withConnection { connection ->
            connection.prepare("CREATE TABLE t (v INTEGER)").use { create ->
                assertEquals(0, create.getColumnCount())
                assertEquals(emptyList(), create.getColumnNames())
            }
        }
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

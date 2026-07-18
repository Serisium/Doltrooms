package dev.seri.doltrooms.driver

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertFailsWith
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
// - [x] getColumnCount/getColumnName/getColumnNames; 0 columns for non-query
// - [x] column metadata is readable before the first step
// - [x] step over multiple rows
// - [x] reset retains bindings; re-execution after reset
// - [x] rebind after reset replaces the old value
// - [x] clearBindings makes parameters NULL
// - [x] get* out of column range throws (SQLITE_RANGE), incl. negative index
// - [x] get* with no row available throws (SQLITE_MISUSE "no row")
// - [x] get* after DONE throws ("no row")
// - [x] bind out of parameter range throws (SQLITE_RANGE)
// - [x] preparing invalid SQL throws with the offending token in the message
// - [x] statement close is idempotent; use after close throws
// - [ ] inTransaction false/true across BEGIN/COMMIT/ROLLBACK
// - [ ] ROLLBACK discards writes; COMMIT persists them
// - [ ] inTransaction on a closed connection throws
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
    fun stepIteratesOverMultipleRows() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.execSQL("INSERT INTO t (v) VALUES (1), (2), (3)")
            connection.prepare("SELECT v FROM t ORDER BY v").use { query ->
                val seen = mutableListOf<Long>()
                while (query.step()) {
                    seen.add(query.getLong(0))
                }
                assertEquals(listOf(1L, 2L, 3L), seen)
            }
        }
    }

    @Test
    fun resetRetainsBindingsAndAllowsReExecution() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                // "sqlite3_reset(S) … does not change the values of any
                // bindings" (https://www.sqlite.org/c3ref/reset.html).
                insert.bindLong(1, 9L)
                assertFalse(insert.step())
                insert.reset()
                assertFalse(insert.step())
            }
            connection.prepare("SELECT COUNT(*), SUM(v) FROM t").use { query ->
                assertTrue(query.step())
                assertEquals(2L, query.getLong(0))
                assertEquals(18L, query.getLong(1))
            }
        }
    }

    @Test
    fun rebindAfterResetReplacesValue() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.execSQL("INSERT INTO t (v) VALUES (1), (2)")
            connection.prepare("SELECT v FROM t WHERE v = ?").use { query ->
                query.bindLong(1, 1L)
                assertTrue(query.step())
                assertEquals(1L, query.getLong(0))
                query.reset()
                query.bindLong(1, 2L)
                assertTrue(query.step())
                assertEquals(2L, query.getLong(0))
            }
        }
    }

    @Test
    fun clearBindingsMakesParametersNull() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                insert.bindLong(1, 5L)
                assertFalse(insert.step())
                insert.reset()
                // "Use this routine to reset all host parameters to NULL."
                // (https://www.sqlite.org/c3ref/clear_bindings.html)
                insert.clearBindings()
                assertFalse(insert.step())
            }
            connection.prepare("SELECT v FROM t ORDER BY v IS NULL").use { query ->
                assertTrue(query.step())
                assertEquals(5L, query.getLong(0))
                assertTrue(query.step())
                assertTrue(query.isNull(0))
            }
        }
    }

    @Test
    fun getOutOfColumnRangeThrows() {
        withConnection { connection ->
            connection.prepare("SELECT 1").use { query ->
                assertTrue(query.step())
                val e = assertFailsWith<SQLiteException> { query.getLong(1) }
                assertTrue(
                    "column index out of range" in (e.message ?: ""),
                    "unexpected message: ${e.message}",
                )
                assertFailsWith<SQLiteException> { query.getLong(-1) }
            }
        }
    }

    @Test
    fun getWithoutRowThrows() {
        withConnection { connection ->
            connection.prepare("SELECT 1").use { query ->
                // No step yet: not positioned on a row.
                val e = assertFailsWith<SQLiteException> { query.getLong(0) }
                assertTrue("no row" in (e.message ?: ""), "unexpected message: ${e.message}")
            }
        }
    }

    @Test
    fun getAfterDoneThrows() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.execSQL("INSERT INTO t (v) VALUES (1)")
            connection.prepare("SELECT v FROM t").use { query ->
                assertTrue(query.step())
                assertFalse(query.step())
                val e = assertFailsWith<SQLiteException> { query.getLong(0) }
                assertTrue("no row" in (e.message ?: ""), "unexpected message: ${e.message}")
            }
        }
    }

    @Test
    fun bindOutOfParameterRangeThrows() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.prepare("INSERT INTO t (v) VALUES (?)").use { insert ->
                // "SQLITE_RANGE is returned if the parameter index is out of
                // range" (https://www.sqlite.org/c3ref/bind_blob.html).
                assertFailsWith<SQLiteException> { insert.bindLong(2, 1L) }
                assertFailsWith<SQLiteException> { insert.bindLong(0, 1L) }
            }
        }
    }

    @Test
    fun preparingInvalidSqlThrows() {
        withConnection { connection ->
            val e = assertFailsWith<SQLiteException> { connection.prepare("NOT VALID SQL") }
            assertTrue("NOT" in (e.message ?: ""), "unexpected message: ${e.message}")
        }
    }

    @Test
    fun statementCloseIsIdempotent() {
        withConnection { connection ->
            val statement = connection.prepare("SELECT 1")
            statement.close()
            // Contract: "Calling this function on an already closed statement
            // is a no-op."
            statement.close()
        }
    }

    @Test
    fun statementUseAfterCloseThrows() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            val statement = connection.prepare("SELECT v FROM t")
            statement.close()
            for (call in listOf<Pair<String, () -> Unit>>(
                "step" to { statement.step() },
                "bindLong" to { statement.bindLong(1, 1L) },
                "getLong" to { statement.getLong(0) },
                "getColumnCount" to { statement.getColumnCount() },
                "reset" to { statement.reset() },
                "clearBindings" to { statement.clearBindings() },
            )) {
                val e = assertFailsWith<SQLiteException>(call.first) { call.second() }
                assertTrue(
                    "statement is closed" in (e.message ?: ""),
                    "unexpected message from ${call.first}: ${e.message}",
                )
            }
        }
    }

    @Test
    fun inTransactionTracksBeginCommit() {
        withConnection { connection ->
            // "Autocommit mode is on by default … disabled by a BEGIN …
            // re-enabled by a COMMIT or ROLLBACK."
            // (https://www.sqlite.org/c3ref/get_autocommit.html)
            assertFalse(connection.inTransaction())
            connection.execSQL("BEGIN")
            assertTrue(connection.inTransaction())
            connection.execSQL("COMMIT")
            assertFalse(connection.inTransaction())
            connection.execSQL("BEGIN")
            connection.execSQL("ROLLBACK")
            assertFalse(connection.inTransaction())
        }
    }

    @Test
    fun commitPersistsAndRollbackDiscards() {
        withConnection { connection ->
            connection.execSQL("CREATE TABLE t (v INTEGER)")
            connection.execSQL("BEGIN")
            connection.execSQL("INSERT INTO t (v) VALUES (1)")
            connection.execSQL("COMMIT")
            connection.execSQL("BEGIN")
            connection.execSQL("INSERT INTO t (v) VALUES (2)")
            connection.execSQL("ROLLBACK")
            connection.prepare("SELECT COUNT(*), MAX(v) FROM t").use { query ->
                assertTrue(query.step())
                assertEquals(1L, query.getLong(0))
                assertEquals(1L, query.getLong(1))
            }
        }
    }

    @Test
    fun inTransactionOnClosedConnectionThrows() {
        val connection = driver().open(":memory:")
        connection.close()
        val e = assertFailsWith<SQLiteException> { connection.inTransaction() }
        assertTrue(
            "connection is closed" in (e.message ?: ""),
            "unexpected message: ${e.message}",
        )
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

package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Documented DoltLite-vs-stock divergences (PLAN.md Current State table).
// These are contract, not bugs: each test asserts BOTH engines' observed
// behavior, so an upstream DoltLite change that heals (or worsens) a
// divergence turns up as a test failure. Probed 2026-07-17 against
// DoltLite 0.11.33 and androidx sqlite-bundled 2.7.0 (SQLite 3.50.1).
// The deferred-CANTOPEN open divergence is documented separately by
// DoltLiteDriverTest.openFailureIsDeferredToFirstStatement.
class KnownDivergenceTest {

    private fun tempDbPath(): String =
        File.createTempFile("doltrooms-divergence", ".db").also { it.delete() }.absolutePath

    private fun SQLiteConnection.queryText(sql: String): String {
        prepare(sql).use { statement ->
            assertTrue(statement.step(), "no row from: $sql")
            return statement.getText(0)
        }
    }

    private fun SQLiteConnection.queryLong(sql: String): Long {
        prepare(sql).use { statement ->
            assertTrue(statement.step(), "no row from: $sql")
            return statement.getLong(0)
        }
    }

    @Test
    fun defaultJournalModeDivergesOnFileDatabases() {
        // Stock SQLite: "The DELETE journaling mode is the default."
        // (https://www.sqlite.org/pragma.html#pragma_journal_mode)
        // DoltLite reports WAL out of the box on its prolly-tree files.
        DoltLiteDriver().open(tempDbPath()).use { connection ->
            assertEquals("wal", connection.queryText("PRAGMA journal_mode"))
        }
        BundledSQLiteDriver().open(tempDbPath()).use { connection ->
            assertEquals("delete", connection.queryText("PRAGMA journal_mode"))
        }
    }

    @Test
    fun lastInsertRowidDivergesOnNonIntegerPkTables() {
        // DoltLite stores non-INTEGER-PK tables as WITHOUT ROWID (doltlite
        // skill), and WITHOUT ROWID inserts "are not recorded"
        // (https://www.sqlite.org/c3ref/last_insert_rowid.html) — so Room-style
        // rowid-returning inserts only work against INTEGER PRIMARY KEY tables.
        fun probe(connection: SQLiteConnection): Long {
            connection.execSQL("CREATE TABLE r (k TEXT PRIMARY KEY, v INTEGER)")
            connection.execSQL("INSERT INTO r VALUES ('a', 1)")
            return connection.queryLong("SELECT last_insert_rowid()")
        }
        DoltLiteDriver().open(":memory:").use { connection ->
            assertEquals(0L, probe(connection))
        }
        BundledSQLiteDriver().open(":memory:").use { connection ->
            assertEquals(1L, probe(connection))
        }
    }

    @Test
    fun rowidColumnDivergesOnNonIntegerPkTables() {
        // Same WITHOUT ROWID storage: the implicit rowid column itself is
        // absent on DoltLite, present on stock.
        fun SQLiteConnection.createAndFill() {
            execSQL("CREATE TABLE r (k TEXT PRIMARY KEY, v INTEGER)")
            execSQL("INSERT INTO r VALUES ('a', 1)")
        }
        DoltLiteDriver().open(":memory:").use { connection ->
            connection.createAndFill()
            val e = assertFailsWith<SQLiteException> {
                connection.queryLong("SELECT rowid FROM r")
            }
            assertTrue("no such column" in (e.message ?: ""), "unexpected: ${e.message}")
        }
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.createAndFill()
            assertEquals(1L, connection.queryLong("SELECT rowid FROM r"))
        }
    }

    @Test
    fun lastInsertRowidConformsOnIntegerPkTables() {
        // The guard-rail leg: on INTEGER PRIMARY KEY (rowid) tables both
        // engines agree, which is what Room's generated inserts rely on.
        fun probe(connection: SQLiteConnection): Long {
            connection.execSQL("CREATE TABLE ri (id INTEGER PRIMARY KEY, v INTEGER)")
            connection.execSQL("INSERT INTO ri (v) VALUES (10)")
            return connection.queryLong("SELECT last_insert_rowid()")
        }
        DoltLiteDriver().open(":memory:").use { connection ->
            assertEquals(1L, probe(connection))
        }
        BundledSQLiteDriver().open(":memory:").use { connection ->
            assertEquals(1L, probe(connection))
        }
    }

    @Test
    fun walSynchronousShowsNoObservableDivergence() {
        // Step 1 dropped SQLITE_DEFAULT_WAL_SYNCHRONOUS=1 from the DoltLite
        // build (fork can't compile it); this probe shows the observable
        // setting matches bundled anyway on the plain open→WAL path (both
        // report 2/FULL), closing the Step 1 "probe at Step 3" follow-up.
        fun probe(connection: SQLiteConnection): Long {
            connection.execSQL("PRAGMA journal_mode=WAL")
            return connection.queryLong("PRAGMA synchronous")
        }
        DoltLiteDriver().open(tempDbPath()).use { connection ->
            assertEquals(2L, probe(connection))
        }
        BundledSQLiteDriver().open(tempDbPath()).use { connection ->
            assertEquals(2L, probe(connection))
        }
    }
}

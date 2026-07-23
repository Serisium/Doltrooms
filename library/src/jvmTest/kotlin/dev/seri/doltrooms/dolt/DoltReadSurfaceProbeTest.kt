package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import dev.seri.doltrooms.driver.DoltLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Probes of DoltLite's read-shaped version-control surface that the
// Room-entity design (docs/design/room-entity-dolt-primitives.md) builds
// on: TVF forms, system-table schemas, views over system tables, and
// trigger support. Each test pins an engine fact at the 0.11.33 pin —
// an upstream change that moves one of these facts turns up here.
class DoltReadSurfaceProbeTest {

    private fun withRepo(block: (SQLiteConnection) -> Unit): Unit =
        DoltLiteDriver().open(":memory:").use { conn ->
            conn.execSQL("CREATE TABLE item (id INTEGER PRIMARY KEY, name TEXT, count INTEGER)")
            conn.execSQL("INSERT INTO item VALUES (1, 'apple', 3)")
            conn.queryAll("SELECT dolt_commit('-Am', 'initial data')")
            block(conn)
        }

    private fun SQLiteConnection.queryAll(sql: String): List<Map<String, String?>> =
        prepare(sql).use { stmt ->
            buildList {
                while (stmt.step()) {
                    add(
                        (0 until stmt.getColumnCount()).associate { i ->
                            stmt.getColumnName(i) to (if (stmt.isNull(i)) null else stmt.getText(i))
                        }
                    )
                }
            }
        }

    private fun SQLiteConnection.columnNames(sql: String): List<String> =
        prepare(sql).use { stmt -> List(stmt.getColumnCount()) { stmt.getColumnName(it) } }

    // ── dolt_log: bare table only, no per-ref TVF form ─────────────────

    @Test
    fun doltLogHasNoRefArgumentForm() = withRepo { conn ->
        // Dolt-proper's dolt_log('<ref>') table function does NOT exist in
        // DoltLite 0.11.33: dolt_log is a plain system table of the
        // CONNECTION's current branch. Per-ref history needs another route
        // (dolt_commit_ancestors below, or a writer-connection checkout).
        val e = assertFailsWith<SQLiteException> {
            conn.queryAll("SELECT * FROM dolt_log('main')")
        }
        assertTrue("max 0" in e.message.orEmpty(), "actual: ${e.message}")
    }

    @Test
    fun doltCommitAncestorsSpansAllBranches() = withRepo { conn ->
        conn.queryAll("SELECT dolt_checkout('-b', 'feature')")
        conn.execSQL("INSERT INTO item VALUES (2, 'pear', 1)")
        conn.queryAll("SELECT dolt_commit('-Am', 'on feature')")
        conn.queryAll("SELECT dolt_checkout('main')")

        assertEquals(
            listOf("commit_hash", "parent_hash", "parent_index"),
            conn.columnNames("SELECT * FROM dolt_commit_ancestors"),
        )
        // The feature branch's head commit is present even though the
        // connection sits on main — the ancestry table is repo-wide, so a
        // recursive CTE from dolt_branches.hash can walk any ref's chain
        // (hashes only: there is no repo-wide commit-metadata table;
        // dolt_commits does not exist at 0.11.33).
        val featureHead = conn.queryAll("SELECT hash FROM dolt_branches WHERE name = 'feature'")
            .single()["hash"]
        val ancestors = conn.queryAll("SELECT * FROM dolt_commit_ancestors")
        assertTrue(ancestors.any { it["commit_hash"] == featureHead })
        assertFailsWith<SQLiteException> { conn.queryAll("SELECT * FROM dolt_commits") }
    }

    @Test
    fun doltDiffStatIsAParameterizedTvf() = withRepo { conn ->
        conn.queryAll("SELECT dolt_checkout('-b', 'feature')")
        conn.execSQL("INSERT INTO item VALUES (2, 'pear', 1)")
        conn.queryAll("SELECT dolt_commit('-Am', 'on feature')")
        conn.queryAll("SELECT dolt_checkout('main')")

        // Unlike dolt_log/dolt_diff (0-arg), dolt_diff_stat takes from/to
        // refs — and they bind as parameters.
        val stats = conn.prepare("SELECT table_name, rows_added FROM dolt_diff_stat(?, ?)").use { stmt ->
            stmt.bindText(1, "main")
            stmt.bindText(2, "feature")
            buildList {
                while (stmt.step()) add(stmt.getText(0) to stmt.getLong(1))
            }
        }
        assertEquals(listOf("item" to 1L), stats)
    }

    // ── dolt_tags ───────────────────────────────────────────────────────

    @Test
    fun doltTagsSchemaAndTagCreation() = withRepo { conn ->
        conn.queryAll("SELECT dolt_tag('v1')")
        val tags = conn.queryAll("SELECT * FROM dolt_tags")
        assertEquals(1, tags.size)
        assertEquals(
            listOf("tag_name", "tag_hash", "tagger", "email", "date", "message"),
            conn.columnNames("SELECT * FROM dolt_tags"),
        )
        assertEquals("v1", tags.single()["tag_name"])
    }

    // ── dolt_at_<table> / dolt_history_<table> ─────────────────────────

    @Test
    fun doltAtTvfReturnsTableColumnsAndBindsRef() = withRepo { conn ->
        conn.execSQL("UPDATE item SET count = 9 WHERE id = 1")
        conn.queryAll("SELECT dolt_commit('-Am', 'bump count')")

        assertEquals(listOf("id", "name", "count"), conn.columnNames("SELECT * FROM dolt_at_item('HEAD')"))
        val old = conn.prepare("SELECT count FROM dolt_at_item(?) WHERE id = 1").use { stmt ->
            stmt.bindText(1, "HEAD~1")
            assertTrue(stmt.step())
            stmt.getLong(0)
        }
        assertEquals(3L, old)
    }

    @Test
    fun doltHistoryTableSchema() = withRepo { conn ->
        assertEquals(
            listOf("id", "name", "count", "commit_hash", "committer", "commit_date"),
            conn.columnNames("SELECT * FROM dolt_history_item"),
        )
    }

    @Test
    fun doltDiffStatChokesOnSqliteSequenceRows() {
        // Engine limitation (0.11.33): when a diff window contains a
        // change to sqlite_sequence (AUTOINCREMENT bookkeeping — created
        // or updated by any insert into an AUTOINCREMENT table),
        // iterating dolt_diff_stat's rows fails with the bare
        // "SQL logic error" once the cursor reaches that row — no
        // projection or WHERE rescues it. Windows without a
        // sqlite_sequence change work. Room's autoGenerate=true maps to
        // AUTOINCREMENT, so diff_stat windows over Room-insert commits
        // are affected (docs/design/room-entity-dolt-primitives.md).
        DoltLiteDriver().open(":memory:").use { conn ->
            conn.execSQL("CREATE TABLE auto (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
            conn.execSQL("INSERT INTO auto (name) VALUES ('a')")
            conn.queryAll("SELECT dolt_commit('-Am', 'c1')")
            conn.execSQL("INSERT INTO auto (name) VALUES ('b')")
            conn.queryAll("SELECT dolt_commit('-Am', 'c2: sequence modified')")

            val insertWindow = assertFailsWith<SQLiteException> {
                conn.queryAll("SELECT table_name FROM dolt_diff_stat('HEAD~1', 'HEAD')")
            }
            assertTrue("SQL logic error" in insertWindow.message.orEmpty(), "actual: ${insertWindow.message}")

            conn.execSQL("UPDATE auto SET name = 'z' WHERE id = 1")
            conn.queryAll("SELECT dolt_commit('-Am', 'c3: update only')")
            val updateWindow =
                conn.queryAll("SELECT table_name, rows_modified FROM dolt_diff_stat('HEAD~1', 'HEAD')")
            assertEquals(listOf("auto"), updateWindow.map { it["table_name"] })
        }
    }

    @Test
    fun rootCommitDateIsImmutableButAmendWorksElsewhere() {
        // The engine mints "Initialize data repository" at db-creation
        // time and its date CANNOT be changed: dolt_commit supports
        // --amend (a fact this probe pins — Dolt-proper parity), but
        // refuses on the root. Backdated timelines therefore always
        // carry one modern-dated root; date-ordered reads should filter
        // it via its NULL parent in dolt_commit_ancestors.
        DoltLiteDriver().open(":memory:").use { conn ->
            conn.execSQL("CREATE TABLE lore (id INTEGER PRIMARY KEY, note TEXT)")
            val rootAmend = assertFailsWith<SQLiteException> {
                conn.queryAll(
                    "SELECT dolt_commit('-A', '--amend', '--date', '2019-05-13T12:00:00', '-m', 'lore epoch')"
                )
            }
            assertTrue("HEAD has no parent" in rootAmend.message.orEmpty(), "actual: ${rootAmend.message}")

            // Non-root commits amend fine, including a rewritten --date.
            conn.queryAll("SELECT dolt_commit('-Am', 'first draft')")
            conn.execSQL("INSERT INTO lore VALUES (1, 'original 4chan thread')")
            conn.queryAll(
                "SELECT dolt_commit('-A', '--amend', '--date', '2019-05-13T12:00:00', '-m', 'The Backrooms: lore epoch')"
            )
            val log = conn.queryAll("SELECT date, message FROM dolt_log")
            assertEquals("2019-05-13 12:00:00", log.first()["date"])
            assertEquals("The Backrooms: lore epoch", log.first()["message"])
        }
    }

    @Test
    fun commitGraphWalksFreezeAtTheSessionHead() {
        // Cross-connection visibility on a FILE database (0.11.33):
        // table data, dolt_branches, dolt_commit_ancestors, and
        // dolt_at_<t>(ref) always read FRESH state — but dolt_log and
        // dolt_history_<t> walk from the session head resolved at
        // connection OPEN (or checkout) and never refresh on their own:
        // not on new statements, not on transaction boundaries, not on a
        // same-branch re-checkout. Only a checkout AWAY and back (or a
        // new connection) re-resolves the head. Room's reader pool never
        // checks out, so DAO reads of dolt_log/dolt_history on file
        // databases are frozen at each reader's open time — route
        // commit-log reads through the writer connection instead
        // (docs/design/room-entity-dolt-primitives.md §6).
        val path = java.io.File.createTempFile("stale-probe", ".db").also { it.delete() }.absolutePath
        val driver = DoltLiteDriver()
        val a = driver.open(path)
        val b: SQLiteConnection
        try {
            a.execSQL("CREATE TABLE item (id INTEGER PRIMARY KEY, name TEXT, count INTEGER)")
            a.execSQL("INSERT INTO item VALUES (1, 'apple', 3)")
            a.queryAll("SELECT dolt_commit('-Am', 'c1')")
            b = driver.open(path)
        } catch (t: Throwable) {
            a.close()
            throw t
        }
        try {
            assertEquals(2, b.queryAll("SELECT message FROM dolt_log").size)
            a.execSQL("INSERT INTO item VALUES (2, 'pear', 1)")
            a.queryAll("SELECT dolt_commit('-Am', 'c2')")

            assertEquals(3, a.queryAll("SELECT message FROM dolt_log").size, "writer sees its commit")
            // B's DATA and refs are fresh...
            assertEquals(2, b.queryAll("SELECT * FROM item").size)
            assertEquals(
                a.queryAll("SELECT hash FROM dolt_branches"),
                b.queryAll("SELECT hash FROM dolt_branches"),
            )
            assertEquals(3, b.queryAll("SELECT * FROM dolt_commit_ancestors").size)
            // ...but its commit-graph walk is frozen at c1:
            assertEquals(2, b.queryAll("SELECT message FROM dolt_log").size)
            b.execSQL("BEGIN")
            b.queryAll("SELECT 1")
            b.execSQL("COMMIT")
            assertEquals(2, b.queryAll("SELECT message FROM dolt_log").size, "txn boundary: no refresh")
            b.queryAll("SELECT dolt_checkout('main')")
            assertEquals(2, b.queryAll("SELECT message FROM dolt_log").size, "same-branch checkout: no-op")

            // A round-trip checkout re-resolves the session head.
            a.queryAll("SELECT dolt_branch('side')")
            b.queryAll("SELECT dolt_checkout('side')")
            b.queryAll("SELECT dolt_checkout('main')")
            assertEquals(3, b.queryAll("SELECT message FROM dolt_log").size, "round-trip refreshes")
        } finally {
            a.close()
            b.close()
        }
    }

    // ── Views over dolt system tables ──────────────────────────────────

    @Test
    fun createViewOverDoltSystemTableWorks() = withRepo { conn ->
        conn.execSQL("CREATE VIEW branch_summary AS SELECT name, hash, dirty FROM dolt_branches")
        val rows = conn.queryAll("SELECT * FROM branch_summary")
        assertEquals(listOf("main"), rows.map { it["name"] })
    }

    @Test
    fun createViewOverDoltTvfWorks() = withRepo { conn ->
        conn.execSQL("CREATE VIEW head_items AS SELECT * FROM dolt_at_item('HEAD')")
        assertEquals(1, conn.queryAll("SELECT * FROM head_items").size)
    }

    @Test
    fun createViewDirtiesTheWorkingTree() = withRepo { conn ->
        assertTrue(conn.queryAll("SELECT * FROM dolt_status").isEmpty(), "clean before")
        conn.execSQL("CREATE VIEW branch_summary AS SELECT name FROM dolt_branches")
        val status = conn.queryAll("SELECT * FROM dolt_status")
        assertTrue(status.isNotEmpty(), "CREATE VIEW should appear in dolt_status (dolt_schemas)")
    }

    // ── Triggers (Room invalidation machinery) ─────────────────────────

    @Test
    fun tempTriggerOnUserTableWorks() = withRepo { conn ->
        conn.execSQL("CREATE TEMP TABLE observed (tbl TEXT)")
        conn.execSQL(
            "CREATE TEMP TRIGGER item_update AFTER UPDATE ON item BEGIN " +
                "INSERT INTO observed VALUES ('item'); END"
        )
        conn.execSQL("UPDATE item SET count = 4 WHERE id = 1")
        assertEquals(1, conn.queryAll("SELECT * FROM observed").size)
    }

    @Test
    fun tempTriggerOnDoltSystemTableIsRejected() = withRepo { conn ->
        assertFailsWith<SQLiteException> {
            conn.execSQL(
                "CREATE TEMP TRIGGER branches_update AFTER UPDATE ON dolt_branches BEGIN " +
                    "SELECT 1; END"
            )
        }
    }
}

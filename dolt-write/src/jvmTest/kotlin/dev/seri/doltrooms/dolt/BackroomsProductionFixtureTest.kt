package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteConnection
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Assertion suite for BackroomsProductionFixture — the reusable seed
// database whose COMMIT HISTORY retells the production timeline of the
// A24 film "Backrooms" (dir. Kane Parsons). See the fixture's KDoc for
// the timeline sources and the fetch date.
//
// Test list (red-green; add cases as they occur):
// - [x] one commit per real milestone, chronological, stamped with the
//       real date via dolt_commit('--date', ...) (probed: supported)
// - [x] milestones table mirrors the commit stamps 1:1
// - [x] parallel filming units live on branches that survive the merge
// - [x] the merge resolved real conflicts: same scene row touched by
//       both units, plus a deliberate auto-id collision in shoot_days
// - [x] dolt_diff_<table> across the merge shows the second unit's work
// - [x] dolt_at_<table> time travel: pre-branch / pre-merge / post-merge
// - [x] the theatrical release is tagged
// - [x] the fixture seeds a FILE database other tests can reopen
class BackroomsProductionFixtureTest {

    private fun <R> withFixture(block: (SQLiteConnection, BackroomsProductionFixture.Refs) -> R): R {
        val conn = DoltLiteDriver().open(":memory:")
        return try {
            block(conn, BackroomsProductionFixture.build(conn))
        } finally {
            conn.close()
        }
    }

    @Test
    fun commitHistoryRetellsProductionTimeline() = withFixture { conn, refs ->
        val log = conn.logEntries() // newest first: (hash, date, message)

        // 10 milestone commits + the main unit's branch commit (on main via
        // fast-forward) + the resolved merge commit + DoltLite's own init
        // commit. The SECOND unit's head is absent: a resolved conflicted
        // merge produces a SINGLE-parent commit at 0.11.33 (probed — with
        // COMMIT-then-dolt_commit and with dolt_commit inside the still-open
        // transaction alike), so the conflicted branch never joins main's
        // ancestry; only its row data does.
        assertEquals(10, refs.milestones.size)
        assertEquals(13, log.size)
        assertEquals("Initialize data repository", log.last().third)

        // Every milestone commit is on main, stamped with the REAL date
        // (dolt_commit --date, probed at 0.11.33) and named in the message.
        val byHash = log.associateBy { it.first }
        for (m in refs.milestones) {
            val entry = byHash.getValue(m.hash)
            assertEquals("${m.date} 12:00:00", entry.second, "date stamp for ${m.key}")
            assertEquals(m.message, entry.third)
            assertContains(m.message, m.date)
        }

        // Chronological order: the log (newest first) replays the milestone
        // list (oldest first) reversed.
        val milestoneHashesInLog = log.map { it.first }.filter { h -> refs.milestones.any { it.hash == h } }
        assertEquals(refs.milestones.map { it.hash }.reversed(), milestoneHashesInLog)

        // The story reads off the messages.
        assertContains(refs.milestones.first().message, "Announcement (2023-02-06)")
        assertContains(refs.milestones.last().message, "Digital release (2026-07-14)")
        assertTrue(refs.milestones.any { "Principal photography begins (2025-07-07)" in it.message })
        assertTrue(refs.milestones.any { "wraps (2025-08-14)" in it.message })

        // The main unit's head fast-forwarded onto main; the second unit's
        // head stays branch-only (see the comment above) while its work
        // lands through the merge commit.
        val hashes = log.map { it.first }
        assertContains(hashes, refs.mainUnitHead)
        assertContains(hashes, refs.mergeCommit)
        assertFalse(refs.secondUnitHead in hashes)
    }

    @Test
    fun milestonesTableMirrorsCommitStamps() = withFixture { conn, refs ->
        val rows = buildList {
            conn.prepare("SELECT name, date FROM milestones ORDER BY id").use { s ->
                while (s.step()) add(s.getText(0) to s.getText(1))
            }
        }
        assertEquals(refs.milestones.map { it.key to it.date }, rows)
    }

    @Test
    fun parallelUnitsAreBranchesThatSurviveTheMerge() = withFixture { conn, refs ->
        val branches = buildList {
            conn.prepare("SELECT name FROM dolt_branches").use { s ->
                while (s.step()) add(s.getText(0))
            }
        }
        assertEquals(
            setOf("main", refs.mainUnitBranch, refs.secondUnitBranch),
            branches.toSet(),
        )
        // Branch heads are where the fixture says they are.
        val heads = buildList {
            conn.prepare("SELECT name, hash FROM dolt_branches").use { s ->
                while (s.step()) add(s.getText(0) to s.getText(1))
            }
        }.toMap()
        assertEquals(refs.mainUnitHead, heads[refs.mainUnitBranch])
        assertEquals(refs.secondUnitHead, heads[refs.secondUnitBranch])
    }

    @Test
    fun mergeResolvedRealConflicts() = withFixture { conn, refs ->
        // Both units touched scene 'yellow-hallway': the resolution took the
        // second unit's status and concatenated both units' notes.
        conn.prepare("SELECT status, note FROM scenes WHERE slug = 'yellow-hallway'").use { s ->
            assertTrue(s.step())
            // The wrap milestone later marks every scene completed; the
            // resolved values are asserted via time travel below. Here the
            // merged note must still carry both units' reports.
            val note = s.getText(1)
            assertContains(note, "Main unit")
            assertContains(note, "Second unit")
        }

        // The deliberate auto-id collision in shoot_days (both branches mint
        // rowid 2) resolved --ours: the main unit kept id 2, and the second
        // unit's colliding day was re-inserted under an explicit id.
        val days = buildList {
            conn.prepare("SELECT id, unit, notes FROM shoot_days ORDER BY id").use { s ->
                while (s.step()) add(Triple(s.getLong(0), s.getText(1), s.getText(2)))
            }
        }
        assertEquals(listOf(1L, 2L, 101L, 102L, 201L, 202L, 203L), days.map { it.first })
        assertEquals("main", days.single { it.first == 2L }.second)
        val rekeyed = days.single { it.first == 203L }
        assertEquals("second", rekeyed.second)
        assertContains(rekeyed.third, "rekeyed")
    }

    @Test
    fun diffAcrossMergeShowsSecondUnitsWork() = withFixture { conn, refs ->
        val dolt = rawDiff(conn, "scenes", refs.mainUnitHead, refs.mergeCommit)
        val modified = dolt.single()
        assertEquals("modified", modified.diffType)
        assertEquals("needs-reshoot", modified.from["status"])
        assertEquals("shot", modified.to["status"])

        val dayDiff = rawDiff(conn, "shoot_days", refs.mainUnitHead, refs.mergeCommit)
        // The second unit's explicit days plus the rekeyed collision row;
        // the colliding id 2 kept OUR side, so it does not appear.
        assertEquals(setOf(201L, 202L, 203L), dayDiff.map { it.to["id"] }.toSet())
        assertTrue(dayDiff.all { it.diffType == "added" })
    }

    @Test
    fun timeTravelShowsPreAndPostMergeStates() = withFixture { conn, refs ->
        fun sceneAt(ref: String): Pair<String, String?> =
            conn.prepare(
                "SELECT status, note FROM dolt_at_scenes(?) WHERE slug = 'yellow-hallway'"
            ).use { s ->
                s.bindText(1, ref)
                assertTrue(s.step())
                s.getText(0) to (if (s.isNull(1)) null else s.getText(1))
            }

        // Branch point: filming just began, no unit reports yet.
        val (preBranch, preBranchNote) = sceneAt(refs.branchPoint)
        assertEquals("in-progress", preBranch)
        assertNull(preBranchNote)
        // Pre-merge main (after fast-forwarding the main unit): its verdict.
        assertEquals("needs-reshoot", sceneAt(refs.mainUnitHead).first)
        // Post-merge: the resolution took the second unit's status.
        assertEquals("shot", sceneAt(refs.mergeCommit).first)
        // Today (HEAD): the wrap milestone completed every scene.
        conn.prepare("SELECT status FROM scenes WHERE slug = 'yellow-hallway'").use { s ->
            assertTrue(s.step())
            assertEquals("completed", s.getText(0))
        }
    }

    @Test
    fun theatricalReleaseIsTagged() = withFixture { conn, refs ->
        val release = refs.milestones.single { it.key == "theatrical-release" }
        conn.prepare("SELECT tag_name, tag_hash FROM dolt_tags").use { s ->
            assertTrue(s.step())
            assertEquals(refs.releaseTag, s.getText(0))
            assertEquals(release.hash, s.getText(1))
        }
    }

    @Test
    fun fixtureSeedsAReopenableFileDatabase() {
        val path = File.createTempFile("doltrooms-backrooms", ".db")
            .also { it.delete() }.absolutePath
        val conn = DoltLiteDriver().open(path)
        val refs = try {
            BackroomsProductionFixture.build(conn)
        } finally {
            conn.close()
        }
        // A fresh connection (new session, back on main) sees the seeded
        // history — this is the "seed data for other tests" contract.
        val reopened = DoltLiteDriver().open(path)
        try {
            val log = reopened.logEntries()
            assertEquals(13, log.size)
            assertEquals(refs.mergeCommit, log.map { it.first }.single { it == refs.mergeCommit })
        } finally {
            reopened.close()
        }
    }

    // --- raw read helpers -------------------------------------------------

    /** dolt_log rows as (hash, date, message), newest first. */
    private fun SQLiteConnection.logEntries(): List<Triple<String, String, String>> =
        buildList {
            prepare("SELECT commit_hash, date, message FROM dolt_log").use { s ->
                while (s.step()) add(Triple(s.getText(0), s.getText(1), s.getText(2)))
            }
        }

    private data class DiffRow(
        val diffType: String,
        val from: Map<String, Any?>,
        val to: Map<String, Any?>,
    )

    /** Minimal dolt_diff_<table> reader (same shape DoltDatabase.diff maps). */
    private fun rawDiff(conn: SQLiteConnection, table: String, from: String, to: String): List<DiffRow> =
        conn.prepare("SELECT * FROM \"dolt_diff_$table\"(?, ?)").use { s ->
            s.bindText(1, from)
            s.bindText(2, to)
            val names = List(s.getColumnCount()) { s.getColumnName(it) }
            buildList {
                while (s.step()) {
                    var diffType = ""
                    val fromValues = mutableMapOf<String, Any?>()
                    val toValues = mutableMapOf<String, Any?>()
                    names.forEachIndexed { i, name ->
                        when {
                            name == "diff_type" -> diffType = s.getText(i)
                            name.startsWith("from_commit") || name.startsWith("to_commit") -> Unit
                            name.startsWith("from_") ->
                                fromValues[name.removePrefix("from_")] =
                                    if (s.isNull(i)) null else typed(s, i)
                            name.startsWith("to_") ->
                                toValues[name.removePrefix("to_")] =
                                    if (s.isNull(i)) null else typed(s, i)
                        }
                    }
                    add(DiffRow(diffType, fromValues, toValues))
                }
            }
        }

    private fun typed(s: androidx.sqlite.SQLiteStatement, i: Int): Any =
        when (s.getColumnType(i)) {
            androidx.sqlite.SQLITE_DATA_INTEGER -> s.getLong(i)
            androidx.sqlite.SQLITE_DATA_FLOAT -> s.getDouble(i)
            else -> s.getText(i)
        }
}

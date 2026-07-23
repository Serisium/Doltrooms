package dev.seri.doltrooms.prototype

import androidx.room3.useWriterConnection
import dev.seri.doltrooms.dolt.DoltCommit
import dev.seri.doltrooms.dolt.DoltDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Second-wave prop queries, seeded in the shape of the Backrooms
 * production-timeline fixture (library jvmTest,
 * `BackroomsProductionFixture`): milestone commits stamped with real
 * dates via `dolt_commit('--date', ...)`, per-unit branches, authors via
 * `dolt_config`, and a release tag. The reactive tests exercise the
 * design doc §6.1 anchor pattern: vcs verbs are not DML, so flows
 * observe the one-row [DoltEvent] entity that gets bumped after each
 * verb.
 */
class DoltPropQueriesTest {

    private var tick = 0L

    /** Seeded from the epoch database — history begins 2019-05-13. */
    private fun db(): PrototypeDatabase = epochSeededDatabase()

    private suspend fun PrototypeDatabase.writerSql(sql: String, vararg args: String) {
        useWriterConnection { tx ->
            tx.usePrepared(sql) { stmt ->
                args.forEachIndexed { i, a -> stmt.bindText(i + 1, a) }
                stmt.step()
            }
        }
    }

    /** Milestone commit stamped with the real date (noon UTC), Backrooms-style. */
    private suspend fun PrototypeDatabase.commitDated(isoDate: String, message: String) =
        writerSql("SELECT dolt_commit('--date', ?, '-Am', ?)", "${isoDate}T12:00:00", message)

    /** Sets the committer identity for subsequent commits (`dolt_config`). */
    private suspend fun PrototypeDatabase.author(name: String, email: String) {
        writerSql("SELECT dolt_config('user.name', ?)", name)
        writerSql("SELECT dolt_config('user.email', ?)", email)
    }

    /** Bumps the invalidation anchor after a vcs verb (see [DoltEvent]). */
    private suspend fun PrototypeDatabase.bumpAnchor() =
        doltPropQueriesDao().bump(++tick)

    private suspend fun PrototypeDatabase.seed(): PrototypeDatabase {
        doltPrimitivesDao().insert(Fruittie(name = "Apple", fullName = "Apple fruit", calories = "52"))
        commitDated("2025-07-07", "Principal photography begins (2025-07-07)")
        return this
    }

    // ── Flow<List<CommitRow>>: new commit → new emission ───────────────
    // On a file database (this suite — the epoch seed is a file) the
    // reader pool freezes dolt_log at each reader's open-time session
    // head, so the working recipe is the anchor's VERIFIED tick flow
    // mapped through the writer-side DoltDatabase.log(), always fresh.
    @Test
    fun liveCommitsEmitsWhenACommitIsAdded() = runTest {
        withContext(Dispatchers.Default) {
            val database = db().seed()
            try {
                val dolt = DoltDatabase(database)
                val emissions = Channel<List<DoltCommit>>(Channel.UNLIMITED)
                val collector = launch {
                    database.doltPropQueriesDao().commitTicks()
                        .map { dolt.log() }
                        .collect { emissions.send(it) }
                }
                val initial = withTimeout(10_000) { emissions.receive() }
                assertEquals(2, initial.size) // seed + epoch root commit

                database.doltPrimitivesDao()
                    .insert(Fruittie(name = "Pear", fullName = "Pear fruit", calories = "57"))
                database.commitDated("2025-07-18", "Main unit: days 2-4 logged")
                database.bumpAnchor()

                val updated = withTimeout(10_000) {
                    var latest = emissions.receive()
                    while (latest.size < 3) latest = emissions.receive()
                    latest
                }
                assertEquals("Main unit: days 2-4 logged", updated.first().message)
                collector.cancel()
            } finally {
                database.close()
            }
        }
    }

    // ── Flow<List<BranchRow>>: new branch → new emission ───────────────
    @Test
    fun liveBranchesEmitsWhenABranchIsAdded() = runTest {
        withContext(Dispatchers.Default) {
            val database = db().seed()
            try {
                val dao = database.doltPropQueriesDao()
                val emissions = Channel<List<BranchRow>>(Channel.UNLIMITED)
                val collector = launch {
                    dao.liveBranches(liveBranchesQuery()).collect { emissions.send(it) }
                }
                assertEquals(listOf("main"), withTimeout(10_000) { emissions.receive() }.map { it.name })

                DoltDatabase(database).branch("2025/second-unit")
                database.bumpAnchor()

                val updated = withTimeout(10_000) {
                    var latest = emissions.receive()
                    while (latest.size < 2) latest = emissions.receive()
                    latest
                }
                assertEquals(listOf("2025/second-unit", "main"), updated.map { it.name })
                collector.cancel()
            } finally {
                database.close()
            }
        }
    }

    // ── The verified-join flow: compiles, fails at collection ──────────
    @Test
    fun verifiedJoinFlowStillFailsAtRuntime() = runTest {
        val database = db().seed()
        try {
            // The @Query form observes its parsed FROM tables, and dolt_log
            // is not a Room table — the anchor join cannot rescue it. Pins
            // the fact that @RawQuery(observedEntities) is the right tool.
            val failure = assertFails {
                database.doltPropQueriesDao().liveCommitsViaVerifiedJoin().first()
            }
            assertTrue("dolt_log" in failure.message.orEmpty(), "actual: ${failure.message}")
        } finally {
            database.close()
        }
    }

    // ── WHERE committer = :author ──────────────────────────────────────
    @Test
    fun commitsByAuthorFilters() = runTest {
        val database = db()
        try {
            database.author("Kane Parsons", "kane@a24.example")
            database.seed()
            database.author("Will Soodik", "will@a24.example")
            database.doltPrimitivesDao()
                .insert(Fruittie(name = "Pear", fullName = "Pear fruit", calories = "57"))
            database.commitDated("2025-06-15", "Will Soodik takes over the script")

            val dao = database.doltPropQueriesDao()
            assertEquals(
                listOf("Principal photography begins (2025-07-07)"),
                dao.commitsByAuthor("Kane Parsons").map { it.message },
            )
            assertEquals(
                listOf("Will Soodik takes over the script"),
                dao.commitsByAuthor("Will Soodik").map { it.message },
            )
        } finally {
            database.close()
        }
    }

    // ── Branch name prefix ─────────────────────────────────────────────
    @Test
    fun branchesWithPrefixFindsThe2025Units() = runTest {
        val database = db().seed()
        try {
            val dolt = DoltDatabase(database)
            dolt.branch("2025/main-unit")
            dolt.branch("2025/second-unit")
            dolt.branch("2026/pickups")

            assertEquals(
                listOf("2025/main-unit", "2025/second-unit"),
                database.doltPropQueriesDao().branchesWithPrefix("2025").map { it.name },
            )
        } finally {
            database.close()
        }
    }

    // ── Scalar shapes ──────────────────────────────────────────────────
    @Test
    fun latestCommitAndCount() = runTest {
        val database = db().seed()
        try {
            val dao = database.doltPropQueriesDao()
            assertEquals(2, dao.commitCount()) // seed + engine root
            assertEquals("Principal photography begins (2025-07-07)", dao.latestCommit()?.message)
        } finally {
            database.close()
        }
    }

    // ── Date-window query over --date-stamped commits ──────────────────
    @Test
    fun commitsBetweenSelectsTheFilmingWindow() = runTest {
        val database = db().seed()
        try {
            val dao = database.doltPrimitivesDao()
            dao.insert(Fruittie(name = "Pear", fullName = "Pear fruit", calories = "57"))
            database.commitDated("2025-08-14", "Principal photography wraps (2025-08-14)")
            dao.insert(Fruittie(name = "Fig", fullName = "Fig fruit", calories = "74"))
            database.commitDated("2026-05-29", "US theatrical release (2026-05-29)")

            val filming = database.doltPropQueriesDao()
                .commitsBetween("2025-07-01", "2025-08-31 23:59:59")
            assertEquals(
                listOf(
                    "Principal photography begins (2025-07-07)",
                    "Principal photography wraps (2025-08-14)",
                ),
                filming.map { it.message },
            )
        } finally {
            database.close()
        }
    }

    // ── History begins at the lore epoch ───────────────────────────────
    @Test
    fun historyBeginsAtTheLoreEpoch() = runTest {
        val database = db().seed()
        try {
            val oldest = database.doltPropQueriesDao()
                .commitsBetween("2019-01-01", "2019-12-31 23:59:59")
            assertEquals(listOf("Initialize data repository"), oldest.map { it.message })
            assertEquals(listOf(LORE_EPOCH_DATE), oldest.map { it.date })
        } finally {
            database.close()
        }
    }

    // ── Date-ordered timeline, root excluded ───────────────────────────
    @Test
    fun timelineByDateListsMilestonesWithoutTheRoot() = runTest {
        val database = db().seed() // seed commit backdated 2025-07-07
        try {
            database.doltPrimitivesDao()
                .insert(Fruittie(name = "Pear", fullName = "Pear fruit", calories = "57"))
            database.commitDated("2025-08-14", "Principal photography wraps (2025-08-14)")

            // On the epoch seed the root is 2019-dated, so plain date
            // order is already coherent; the ancestry-filtered query
            // additionally drops the root bookkeeping entry (and stays
            // correct on databases NOT minted from the seed, where the
            // root carries its modern creation date).
            assertEquals(
                listOf(
                    "Principal photography wraps (2025-08-14)",
                    "Principal photography begins (2025-07-07)",
                ),
                database.doltPropQueriesDao().timelineByDate().map { it.message },
            )
        } finally {
            database.close()
        }
    }

    // ── Tag lookup ─────────────────────────────────────────────────────
    @Test
    fun tagNamedFindsTheReleaseTag() = runTest {
        val database = db().seed()
        try {
            database.writerSql("SELECT dolt_tag('theatrical-release', '-m', 'US theatrical release')")
            val tag = database.doltPropQueriesDao().tagNamed("theatrical-release")
            assertNotNull(tag)
            assertEquals("US theatrical release", tag.message)
            assertNull(database.doltPropQueriesDao().tagNamed("directors-cut"))
        } finally {
            database.close()
        }
    }

    // ── True merge commits via ancestry GROUP BY/HAVING ────────────────
    @Test
    fun mergeCommitHashesFindsTheCleanThreeWayMerge() = runTest {
        val database = db().seed()
        try {
            val dolt = DoltDatabase(database)
            // Divergent but non-conflicting work: side adds a row, main
            // adds a different row → clean three-way merge, two parents.
            dolt.checkout("2025/second-unit", create = true)
            database.doltPrimitivesDao()
                .insert(Fruittie(name = "Pear", fullName = "Pear fruit", calories = "57"))
            database.commitDated("2025-07-18", "Second unit: poolrooms days logged")
            dolt.checkout("main")
            database.doltPrimitivesDao()
                .insert(Fruittie(name = "Fig", fullName = "Fig fruit", calories = "74"))
            database.commitDated("2025-07-18", "Main unit: hallway days logged")
            val mergeHead = dolt.merge("2025/second-unit")

            assertEquals(listOf(mergeHead), database.doltPropQueriesDao().mergeCommitHashes())
        } finally {
            database.close()
        }
    }

    // ── Parameterized diff-stat TVF ────────────────────────────────────
    @Test
    fun diffStatBetweenRefs() = runTest {
        val database = db().seed()
        try {
            // UPDATE-only window: fruittie is AUTOINCREMENT, so an INSERT
            // here would put a sqlite_sequence change in the window and
            // trip the engine's diff_stat limitation (see the DAO KDoc
            // and the library probe suite).
            database.writerSql("UPDATE fruittie SET calories = '95' WHERE name = 'Apple'")
            database.commitDated("2025-07-18", "Recount apple calories")

            val stats = database.doltPropQueriesDao().diffStat("HEAD~1", "HEAD")
            val fruittie = stats.single { it.tableName == "fruittie" }
            assertEquals(1, fruittie.rowsModified)
            assertEquals(0, fruittie.rowsAdded)
        } finally {
            database.close()
        }
    }


    // ── Row history through dolt_history_<table> ───────────────────────
    @Test
    fun fruittieHistoryTracksRowVersions() = runTest {
        val database = db().seed()
        try {
            database.writerSql("UPDATE fruittie SET calories = '95' WHERE name = 'Apple'")
            database.commitDated("2025-08-14", "Recount apple calories at wrap")

            val history = database.doltPropQueriesDao().fruittieHistory()
            assertEquals(2, history.count { it.name == "Apple" })
            assertTrue(history.all { it.commitHash.isNotBlank() })
        } finally {
            database.close()
        }
    }
}

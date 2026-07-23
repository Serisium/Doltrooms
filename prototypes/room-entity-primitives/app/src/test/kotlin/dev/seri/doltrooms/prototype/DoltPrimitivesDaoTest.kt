package dev.seri.doltrooms.prototype

import androidx.room3.useWriterConnection
import dev.seri.doltrooms.dolt.DoltDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Runtime validation of the target queries on a real DoltLite database.
 * The compile-time half of the evidence is the DAO itself: every @Query
 * in [DoltPrimitivesDao] was verified against DoltLite by the shim.
 */
class DoltPrimitivesDaoTest {

    /** Seeded from the epoch database — history begins 2019-05-13. */
    private fun db(): PrototypeDatabase = epochSeededDatabase()

    private suspend fun PrototypeDatabase.seedAndCommit(): String {
        doltPrimitivesDao().insert(Fruittie(name = "Apple", fullName = "Apple fruit", calories = "52"))
        return DoltDatabase(this).commit("seed")
    }

    private suspend fun PrototypeDatabase.rawWriterSql(sql: String) {
        useWriterConnection { transactor ->
            transactor.usePrepared(sql) { it.step() }
        }
    }

    // ── Target query 1 ─────────────────────────────────────────────────
    @Test
    fun branchesMatchingFiltersAndOrders() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            val dolt = DoltDatabase(database)
            dolt.branch("feature/pricing")
            dolt.branch("feature/catalog")
            dolt.branch("archive/2026")

            val features = database.doltPrimitivesDao().branchesMatching("feature/%")
            assertEquals(listOf("feature/catalog", "feature/pricing"), features.map { it.name })
            assertEquals("seed", features.first().latestCommitMessage)
        } finally {
            database.close()
        }
    }

    // ── Target query 2 ─────────────────────────────────────────────────
    @Test
    fun searchRefsUnionsBranchesAndTags() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            DoltDatabase(database).branch("v-branch")
            database.rawWriterSql("SELECT dolt_tag('v1')")

            val refs = database.doltPrimitivesDao().searchRefs("v%")
            assertEquals(listOf("v-branch" to "branch", "v1" to "tag"), refs.map { it.name to it.kind })
            assertTrue(refs.all { it.hash.isNotBlank() })
        } finally {
            database.close()
        }
    }

    // ── Target query 3 (amended) ───────────────────────────────────────
    @Test
    fun currentBranchLogAndPerRefHistoryHashes() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            val dao = database.doltPrimitivesDao()
            val dolt = DoltDatabase(database)

            dolt.checkout("feature", create = true)
            dao.insert(Fruittie(name = "Pear", fullName = "Pear fruit", calories = "57"))
            val featureHead = dolt.commit("on feature")
            dolt.checkout("main")

            // Bare dolt_log follows the CONNECTION's branch; DAO reads run
            // on the reader pool, which always sits on main.
            val log = dao.currentBranchLog()
            assertEquals(listOf("seed", "Initialize data repository"), log.map { it.message })

            // Any ref's chain via the repo-wide ancestors table.
            val mainHistory = dao.historyHashes("main")
            val featureHistory = dao.historyHashes("feature")
            assertEquals(mainHistory.size + 1, featureHistory.size)
            assertTrue(featureHead in featureHistory)
            assertTrue(mainHistory.all { it in featureHistory })
        } finally {
            database.close()
        }
    }

    // ── Target query 4 ─────────────────────────────────────────────────
    @Test
    fun diffFlowEmitsInitiallyAndAfterWrite() = runTest {
        withContext(Dispatchers.Default) {
            val database = db()
            try {
                database.seedAndCommit()
                val dao = database.doltPrimitivesDao()

                val emissions = Channel<List<FruittieDiffRow>>(Channel.UNLIMITED)
                val collector = launch {
                    dao.diffs(tableDiffQuery("fruittie", "HEAD", "WORKING"))
                        .collect { emissions.send(it) }
                }
                assertEquals(emptyList(), withTimeout(10_000) { emissions.receive() })

                dao.insert(Fruittie(name = "Banana", fullName = "Banana fruit", calories = "89"))
                val seen = withTimeout(10_000) {
                    var latest = emissions.receive()
                    while (latest.isEmpty()) latest = emissions.receive()
                    latest
                }
                assertEquals(listOf("added"), seen.map { it.diffType })
                assertEquals(listOf("Banana"), seen.map { it.toName })
                collector.cancel()
            } finally {
                database.close()
            }
        }
    }

    // ── Target query 5 ─────────────────────────────────────────────────
    @Test
    fun timeTravelReturnsRealEntities() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            val dao = database.doltPrimitivesDao()
            dao.insert(Fruittie(name = "Cherry", fullName = "Cherry fruit", calories = "50"))
            DoltDatabase(database).commit("add cherry")

            val now = dao.fruittiesAt("HEAD").map { it.name }
            val before = dao.fruittiesAt("HEAD~1").map { it.name }
            assertEquals(listOf("Apple", "Cherry"), now.sorted())
            assertEquals(listOf("Apple"), before)
        } finally {
            database.close()
        }
    }

    // ── Target query 6: documents the invalidation gap ─────────────────
    @Test
    fun observeBranchesFlowFailsAtRuntime() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            // dolt_branches is not a Room entity or view, so the
            // InvalidationTracker cannot observe it — the Flow is expected
            // to fail at collection. If this ever starts PASSING, Room
            // changed behavior: re-evaluate target query 6.
            val failure = assertFails { database.doltPrimitivesDao().observeBranches().first() }
            println("observeBranches failure: ${failure::class.qualifiedName}: ${failure.message}")
        } finally {
            database.close()
        }
    }

    // ── Target query 7 ─────────────────────────────────────────────────
    @Test
    fun databaseViewOverDoltBranchesServesVerifiedQueries() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            val dao = database.doltPrimitivesDao()
            assertEquals(emptyList(), dao.dirtyBranches())

            dao.insert(Fruittie(name = "Durian", fullName = "Durian fruit", calories = "147"))
            assertEquals(listOf("main"), dao.dirtyBranches().map { it.name })
        } finally {
            database.close()
        }
    }

    // ── Target query 8 ─────────────────────────────────────────────────
    @Test
    fun joinDoltLogAgainstUserTable() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            val dao = database.doltPrimitivesDao()
            dao.insert(Fruittie(name = "Elderberry", fullName = "Elderberry fruit", calories = "73"))
            val head = DoltDatabase(database).commit("add elderberry")

            val rows = dao.fruittiesWithLastCommit()
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.lastCommit == head })
        } finally {
            database.close()
        }
    }

    // ── Target query 9 ─────────────────────────────────────────────────
    @Test
    fun multimapGroupsCommitsUnderBranches() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            DoltDatabase(database).branch("feature")

            val map = database.doltPrimitivesDao().commitsByBranch()
            assertEquals(setOf("feature", "main"), map.keys.map { it.name }.toSet())
            // Cross join: every branch key carries the connection's log.
            map.values.forEach { commits ->
                assertEquals(listOf("seed", "Initialize data repository"), commits.map { it.message })
            }
        } finally {
            database.close()
        }
    }

    // ── Status, remotes, and the dolt scalar ───────────────────────────
    @Test
    fun statusRemotesAndVersion() = runTest {
        val database = db()
        try {
            database.seedAndCommit()
            val dao = database.doltPrimitivesDao()
            assertEquals(emptyList(), dao.status())

            dao.insert(Fruittie(name = "Fig", fullName = "Fig fruit", calories = "74"))
            // AUTOINCREMENT bookkeeping (sqlite_sequence) diffs alongside
            // user tables (probed engine fact, doltlite skill).
            val status = dao.status()
            assertEquals(listOf("fruittie", "sqlite_sequence"), status.map { it.tableName }.sorted())

            assertEquals(emptyList(), dao.remotes())
            assertEquals("0.11.33", dao.doltVersion())
        } finally {
            database.close()
        }
    }
}

package dev.seri.doltrooms.dolt

import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import dev.seri.doltrooms.room.Person
import dev.seri.doltrooms.room.RoomConformanceDb
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

// Step 7 suite for the typed dolt_* helpers (DoltDatabase). Unlike the
// Step 3/4 conformance suites this is NOT differential — stock SQLite has
// no dolt_* surface, so there is no oracle leg. The engineSupportsDolt
// capability flag keeps the versioning tests DoltLite-only; the one
// Bundled concrete (jvm) exists to run the guard test proving a
// dolt-less engine fails cleanly, not crashes.
//
// Every dolt_* shape asserted here was probed against the pinned
// DoltLite (0.11.33, 2026-07-18) via a throwaway ScratchDoltProbeTest,
// per the Step 3/4 precedent. Version-sensitive; re-probe on upgrade.
//
// Test list (red-green; add cases as they occur):
// - [ ] commit returns the new head hash; log lists it newest-first and
//       ends with DoltLite's own "Initialize data repository" commit
// - [ ] guard: an engine without dolt_* support throws SQLiteException
//       (not a crash) from the helpers
// - [ ] status: new tables before first commit, empty tree after,
//       unstaged modification after a write; commit on a clean tree
//       throws "nothing to commit"
// - [ ] branch/checkout/currentBranch/branches/deleteBranch round-trip;
//       fast-forward merge returns the merged head
// - [ ] branch state is per-connection: Room's reader connections do NOT
//       follow a checkout made on the writer connection
// - [ ] clean three-way merge returns a new merge commit; conflicted
//       merge throws cleanly and leaves the working tree on the
//       pre-merge state (autocommit rollback)
// - [ ] diff between two commits types added/removed/modified rows
// - [ ] @SkipQueryVerification lets a DAO @Query call dolt_version()
abstract class AbstractDoltDatabaseTest {

    /** The driver under test. */
    abstract fun driver(): SQLiteDriver

    /** A fresh, non-existing file path for a temporary on-disk database. */
    abstract fun tempDbPath(): String

    /**
     * Capability flag: whether the engine under test implements the
     * dolt_* SQL surface. The versioning tests only run when true; the
     * clean-failure guard test only runs when false.
     */
    protected open val engineSupportsDolt: Boolean = true

    /** See AbstractDriverConformanceTest — false on Android host tests. */
    protected open val exceptionMessagesObservable: Boolean = true

    private fun fileDb(): RoomConformanceDb =
        Room.databaseBuilder<RoomConformanceDb>(name = tempDbPath())
            .setDriver(driver())
            .build()

    /** Runs a versioning test body, or nothing when the engine has no dolt_*. */
    private fun doltTest(block: suspend (RoomConformanceDb) -> Unit) = runTest {
        if (!engineSupportsDolt) return@runTest
        val db = fileDb()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun commitReturnsHeadHashAndLogListsIt() = doltTest { db ->
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        val hash = dolt.commit("first commit")

        val log = dolt.log()
        // Newest first; the fresh repo carries DoltLite's own init commit.
        assertEquals(hash, log.first().hash)
        assertEquals("first commit", log.first().message)
        assertEquals("Initialize data repository", log.last().message)
        // Room's schema setup plus our insert are all part of this commit —
        // exactly 2 entries: ours + init.
        assertEquals(2, log.size)
    }

    @Test
    fun statusReflectsStagingLifecycle() = doltTest { db ->
        val dolt = DoltDatabase(db)
        // Room's schema setup leaves uncommitted new tables in the working set.
        val fresh = dolt.status()
        val person = fresh.single { it.tableName == "Person" }
        assertEquals(false, person.staged)
        assertEquals("new table", person.status)

        dolt.commit("schema")
        assertEquals(emptyList(), dolt.status())

        // The insert also touches sqlite_sequence (AUTOINCREMENT bookkeeping),
        // so assert on the Person entry rather than the whole set.
        db.personDao().insert(Person(name = "Ada", age = 36))
        val afterWrite = dolt.status().single { it.tableName == "Person" }
        assertEquals(false, afterWrite.staged)
        assertEquals("modified", afterWrite.status)
    }

    @Test
    fun commitOnCleanWorkingTreeThrows() = doltTest { db ->
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        dolt.commit("first commit")
        val e = assertFailsWith<SQLiteException> { dolt.commit("nothing here") }
        if (exceptionMessagesObservable) assertContains(e.message ?: "", "nothing to commit")
    }

    @Test
    fun branchCheckoutAndBranchListRoundTrip() = doltTest { db ->
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        val base = dolt.commit("base")
        assertEquals("main", dolt.currentBranch())

        dolt.branch("side")
        val listed = dolt.branches()
        assertEquals(setOf("main", "side"), listed.map { it.name }.toSet())
        // A fresh branch points at the same head as its source.
        assertEquals(base, listed.single { it.name == "side" }.hash)

        dolt.checkout("side")
        assertEquals("side", dolt.currentBranch())
        db.personDao().insert(Person(name = "Bob", age = 17))
        val sideHead = dolt.commit("side work")
        val after = dolt.branches()
        assertEquals(sideHead, after.single { it.name == "side" }.hash)
        assertEquals(base, after.single { it.name == "main" }.hash)
        assertEquals("side work", after.single { it.name == "side" }.latestCommitMessage)

        // Back on main the writer connection sees only the base row.
        dolt.checkout("main")
        assertEquals("main", dolt.currentBranch())
        assertEquals(1L, writerPersonCount(db))

        // checkout(create = true) switches immediately; a branch equal to
        // head deletes cleanly with -d.
        dolt.checkout("tmp", create = true)
        assertEquals("tmp", dolt.currentBranch())
        dolt.checkout("main")
        dolt.deleteBranch("tmp")
        assertEquals(setOf("main", "side"), dolt.branches().map { it.name }.toSet())

        // Checking out something that isn't a branch fails cleanly.
        val e = assertFailsWith<SQLiteException> { dolt.checkout("nope") }
        if (exceptionMessagesObservable) assertContains(e.message ?: "", "no such branch")
    }

    @Test
    fun readerConnectionsDoNotFollowCheckout() = doltTest { db ->
        // DoltLite's checked-out branch is per-connection session state
        // (probed at 0.11.33: it does not even persist across reopen). The
        // helpers switch only Room's single writer connection; reader
        // connections open on / stay on the default branch. This test pins
        // that contract — it is documentation, not a bug.
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        dolt.commit("base")

        dolt.checkout("feature", create = true)
        db.personDao().insert(Person(name = "Bob", age = 17))
        dolt.commit("feature work")

        // The writer connection is on 'feature' and sees both rows...
        assertEquals("feature", dolt.currentBranch())
        assertEquals(2L, writerPersonCount(db))
        // ...but a DAO read runs on a reader connection, which is on 'main'.
        assertEquals(1, db.personDao().olderThan(-1).size)
    }

    private suspend fun writerPersonCount(db: RoomConformanceDb): Long =
        db.useWriterConnection { t ->
            t.usePrepared("SELECT COUNT(*) FROM Person") {
                it.step()
                it.getLong(0)
            }
        }

    @Test
    fun engineWithoutDoltSupportFailsCleanly() = runTest {
        if (engineSupportsDolt) return@runTest
        val db = fileDb()
        try {
            // Reads and writes both surface an ordinary SQLiteException
            // ("no such table/function"), never a crash.
            val fromLog = assertFailsWith<SQLiteException> { DoltDatabase(db).log() }
            if (exceptionMessagesObservable) assertContains(fromLog.message ?: "", "no such")
            val fromCommit = assertFailsWith<SQLiteException> { DoltDatabase(db).commit("nope") }
            if (exceptionMessagesObservable) assertContains(fromCommit.message ?: "", "no such")
            // The database is still usable afterwards.
            db.useWriterConnection { t ->
                t.usePrepared("SELECT 1") { it.step() }
            }
        } finally {
            db.close()
        }
    }
}

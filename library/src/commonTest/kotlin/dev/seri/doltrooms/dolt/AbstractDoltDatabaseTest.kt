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

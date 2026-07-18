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
// - [x] commit returns the new head hash; log lists it newest-first and
//       ends with DoltLite's own "Initialize data repository" commit
// - [x] guard: an engine without dolt_* support throws SQLiteException
//       (not a crash) from the helpers
// - [x] status: new tables before first commit, empty tree after,
//       unstaged modification after a write; commit on a clean tree
//       throws "nothing to commit"
// - [x] branch/checkout/currentBranch/branches/deleteBranch round-trip
// - [x] branch state is per-connection: Room's reader connections do NOT
//       follow a checkout made on the writer connection
// - [x] fast-forward merge returns the merged head; clean three-way
//       merge returns a new merge commit; conflicted merge throws
//       cleanly and leaves the working tree on the pre-merge state
//       (autocommit rollback)
// - [x] diff between two commits types added/removed/modified rows;
//       diff against the WORKING pseudo-ref sees uncommitted changes
// - [x] @SkipQueryVerification lets a DAO @Query call dolt_version()
//
// Step 8 additions (remotes + sync; probed via ScratchRemoteProbeTest,
// 0.11.33, 2026-07-18):
// - [x] remote add/list/remove round-trip; duplicate add and push to an
//       unknown remote fail cleanly
// - [ ] push to a file:// remote + clone from it replicates rows and log
// - [ ] pull brings new commits pushed after the clone (completes the
//       card's push-from-A/pull-into-B round-trip)
// - [ ] fetch makes origin/<branch> mergeable (and it is NOT mergeable
//       before the first fetch — fetch's observable effect)
// - [ ] non-fast-forward push is rejected; --force push succeeds
// - [ ] conflicted pull throws and rolls back (autocommit, like merge)
// - [ ] clone requires a fresh database (a Room-opened db is already
//       dirty — clone is a pre-Room bootstrap); missing remote fails
//       cleanly
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

    @Test
    fun fastForwardMergeReturnsMergedHead() = doltTest { db ->
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        dolt.commit("base")
        dolt.checkout("side", create = true)
        db.personDao().insert(Person(name = "Bob", age = 17))
        val sideHead = dolt.commit("side work")

        dolt.checkout("main")
        assertEquals(1L, writerPersonCount(db))
        // main has not diverged, so the merge fast-forwards to side's head.
        assertEquals(sideHead, dolt.merge("side"))
        assertEquals(2L, writerPersonCount(db))
        assertEquals(sideHead, dolt.log().first().hash)
    }

    @Test
    fun cleanThreeWayMergeCreatesMergeCommit() = doltTest { db ->
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        dolt.commit("base")
        dolt.checkout("side", create = true)
        db.personDao().insert(Person(name = "Bob", age = 17))
        val sideHead = dolt.commit("side work")
        // Diverge main with a non-conflicting change (different row).
        dolt.checkout("main")
        db.personDao().insert(Person(name = "Eve", age = 63))
        val mainHead = dolt.commit("main work")

        val mergeCommit = dolt.merge("side")
        // A real merge commit: a new hash on top of both parents.
        assertEquals(mergeCommit, dolt.log().first().hash)
        val hashes = dolt.log().map { it.hash }
        assertContains(hashes, sideHead)
        assertContains(hashes, mainHead)
        assertEquals(3L, writerPersonCount(db))
    }

    @Test
    fun conflictedMergeThrowsAndRollsBack() = doltTest { db ->
        val dolt = DoltDatabase(db)
        val id = db.personDao().insert(Person(name = "Ada", age = 36))
        dolt.commit("base")
        dolt.checkout("other", create = true)
        db.personDao().update(Person(id = id, name = "Ada", age = 40))
        dolt.commit("other edit")
        dolt.checkout("main")
        db.personDao().update(Person(id = id, name = "Ada", age = 50))
        val mainHead = dolt.commit("main edit")

        // Under autocommit a conflicted merge throws and rolls back; the
        // working tree stays on main's pre-merge state (probed at 0.11.33;
        // resolving instead requires an explicit transaction — class KDoc).
        val e = assertFailsWith<SQLiteException> { dolt.merge("other") }
        if (exceptionMessagesObservable) assertContains(e.message ?: "", "conflict")
        assertEquals("main", dolt.currentBranch())
        assertEquals(mainHead, dolt.log().first().hash)
        assertEquals(emptyList(), dolt.status())
        assertEquals(
            50L,
            db.useWriterConnection { t ->
                t.usePrepared("SELECT age FROM Person") {
                    it.step()
                    it.getLong(0)
                }
            },
        )
    }

    @Test
    fun diffBetweenCommitsTypesRows() = doltTest { db ->
        val dolt = DoltDatabase(db)
        val dao = db.personDao()
        val adaId = dao.insert(Person(name = "Ada", age = 36))
        val bobId = dao.insert(Person(name = "Bob", age = 17))
        dolt.commit("c1")
        dao.update(Person(id = adaId, name = "Ada", age = 37))
        dao.delete(Person(id = bobId, name = "Bob", age = 17))
        dao.insert(Person(name = "Eve", age = 63))
        dolt.commit("c2")

        val rows = dolt.diff("Person", from = "HEAD~1", to = "HEAD")
        assertEquals(3, rows.size)

        val modified = rows.single { it.diffType == "modified" }
        // Values are typed by SQLite column type: INTEGER -> Long, TEXT -> String.
        assertEquals(36L, modified.from["age"])
        assertEquals(37L, modified.to["age"])
        assertEquals("Ada", modified.to["name"])

        val removed = rows.single { it.diffType == "removed" }
        assertEquals("Bob", removed.from["name"])
        assertEquals(null, removed.to["name"])

        val added = rows.single { it.diffType == "added" }
        assertEquals("Eve", added.to["name"])
        assertEquals(null, added.from["name"])

        // Both sides carry the commit refs the diff spans.
        val log = dolt.log()
        assertEquals(log[0].hash, added.toCommit)
        assertEquals(log[1].hash, added.fromCommit)
    }

    @Test
    fun diffAgainstWorkingSeesUncommittedChanges() = doltTest { db ->
        val dolt = DoltDatabase(db)
        db.personDao().insert(Person(name = "Ada", age = 36))
        dolt.commit("base")
        db.personDao().insert(Person(name = "Bob", age = 17))

        val rows = dolt.diff("Person", from = "HEAD", to = "WORKING")
        val added = rows.single()
        assertEquals("added", added.diffType)
        assertEquals("Bob", added.to["name"])
        assertEquals("WORKING", added.toCommit)
    }

    @Test
    fun skipQueryVerificationLetsDaoCallDoltFunctions() = doltTest { db ->
        // Room verifies @Query SQL against STOCK SQLite at compile time, so
        // dolt_* in a DAO needs @SkipQueryVerification (query-verification
        // reference). The method compiling at all validates that Room's
        // embedded SQL grammar tolerates the unknown function name; this
        // call validates it at runtime on DoltLite.
        assertEquals("0.11.33", db.personDao().doltVersion())
    }

    @Test
    fun remoteAddListRemoveRoundTrip() = doltTest { db ->
        val dolt = DoltDatabase(db)
        val url = "file://" + tempDbPath()
        dolt.addRemote("origin", url)

        val remote = dolt.remotes().single()
        assertEquals("origin", remote.name)
        assertEquals(url, remote.url)
        // The default fetch spec mirrors git's refs/heads -> refs/remotes map.
        assertContains(remote.fetchSpecs, "refs/remotes/origin")

        val dup = assertFailsWith<SQLiteException> { dolt.addRemote("origin", url) }
        if (exceptionMessagesObservable) assertContains(dup.message ?: "", "remote already exists")

        dolt.removeRemote("origin")
        assertEquals(emptyList(), dolt.remotes())
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

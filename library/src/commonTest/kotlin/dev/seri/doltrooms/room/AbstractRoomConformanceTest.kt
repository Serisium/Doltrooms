package dev.seri.doltrooms.room

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// Step 4 differential Room suite: a real Room 3 database built with
// Room.inMemoryDatabaseBuilder().setDriver(...) runs the identical tests
// against DoltLiteDriver AND BundledSQLiteDriver (the oracle) via
// per-platform concrete subclasses — same shape as Step 3's
// AbstractDriverConformanceTest.
//
// Test list (red-green; add cases as they occur):
// - [x] suspend @Insert + @Query roundtrip through a generated DAO
// - [x] parameterized list @Query; @Update and @Delete return change counts
// - [x] @Transaction DAO method commits on success, rolls back on exception
// - [x] Flow @Query emits initially and re-emits after a write
//       (InvalidationTracker: temp table + triggers through the driver)
// - [ ] file DB + WAL journal mode: Room's 4-reader/1-writer pool serves
//       concurrent DAO readers while a writer transacts
abstract class AbstractRoomConformanceTest {

    /** The driver under test — DoltLite or the Bundled oracle. */
    abstract fun driver(): SQLiteDriver

    /** A fresh, non-existing file path for a temporary on-disk database. */
    abstract fun tempDbPath(): String

    private fun inMemoryDb(): RoomConformanceDb =
        Room.inMemoryDatabaseBuilder<RoomConformanceDb>()
            .setDriver(driver())
            .build()

    // WRITE_AHEAD_LOGGING is explicit (not left to the default) because it is
    // what selects Room's 4-reader/1-writer pool over our driver.
    private fun fileDb(path: String): RoomConformanceDb =
        Room.databaseBuilder<RoomConformanceDb>(name = path)
            .setDriver(driver())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Test
    fun listQueryUpdateAndDeleteCountChanges() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.personDao()
            val ada = Person(id = dao.insert(Person(name = "Ada", age = 36)), name = "Ada", age = 36)
            val bob = Person(id = dao.insert(Person(name = "Bob", age = 17)), name = "Bob", age = 17)
            val eve = Person(id = dao.insert(Person(name = "Eve", age = 63)), name = "Eve", age = 63)

            assertEquals(listOf(ada, eve), dao.olderThan(18))
            // @Update/@Delete return the number of affected rows — Room reads
            // this from the driver's changes count.
            assertEquals(1, dao.update(ada.copy(age = 37)))
            assertEquals(0, dao.update(Person(id = 999, name = "Nobody", age = 1)))
            assertEquals(37, dao.byId(ada.id)?.age)
            assertEquals(1, dao.delete(bob))
            assertEquals(listOf(ada.copy(age = 37), eve), dao.olderThan(0))
        } finally {
            db.close()
        }
    }

    @Test
    fun transactionDaoMethodCommitsOnSuccessRollsBackOnException() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.personDao()
            dao.insertPair(Person(name = "Ada", age = 36), Person(name = "Bob", age = 17))
            assertEquals(2, dao.olderThan(0).size)

            assertFailsWith<IllegalStateException> {
                dao.insertPair(Person(name = "Eve", age = 63), Person(name = "Mal", age = -1))
            }
            // The first insert of the failed pair must have rolled back.
            assertEquals(2, dao.olderThan(0).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun flowQueryEmitsInitiallyAndAfterWrite() = runTest {
        // Real dispatchers throughout: the Flow collector must run in real
        // time, and runTest's virtual clock would otherwise fast-forward the
        // withTimeout guards while the collector is still working.
        withContext(Dispatchers.Default) {
            val db = inMemoryDb()
            try {
                val dao = db.personDao()
                val emissions = Channel<List<Person>>(Channel.UNLIMITED)
                val collector = launch { dao.observeAll().collect { emissions.send(it) } }
                assertEquals(emptyList(), withTimeout(10_000) { emissions.receive() })

                val id = dao.insert(Person(name = "Ada", age = 36))
                // Invalidation may deliver intermediate emissions; wait for
                // the one that contains the write.
                val seen = withTimeout(10_000) {
                    var latest = emissions.receive()
                    while (latest.isEmpty()) latest = emissions.receive()
                    latest
                }
                assertEquals(listOf(Person(id = id, name = "Ada", age = 36)), seen)
                collector.cancel()
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun walFileDbServesConcurrentReadersWhileWriting() = runTest {
        withContext(Dispatchers.Default) {
            val db = fileDb(tempDbPath())
            try {
                val dao = db.personDao()
                dao.insert(Person(name = "seed", age = 1))
                val writer = launch {
                    repeat(20) { i ->
                        dao.insertPair(
                            Person(name = "a$i", age = i),
                            Person(name = "b$i", age = i),
                        )
                    }
                }
                val readers = List(4) {
                    launch { repeat(50) { dao.olderThan(-1) } }
                }
                writer.join()
                readers.forEach { it.join() }
                assertEquals(41, dao.olderThan(-1).size)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun insertAndQueryRoundTripsThroughGeneratedDao() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.personDao()
            val id = dao.insert(Person(name = "Ada", age = 36))
            assertEquals(Person(id = id, name = "Ada", age = 36), dao.byId(id))
        } finally {
            db.close()
        }
    }
}

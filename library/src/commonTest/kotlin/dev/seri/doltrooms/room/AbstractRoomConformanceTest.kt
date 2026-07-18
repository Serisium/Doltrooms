package dev.seri.doltrooms.room

import androidx.room3.Room
import androidx.sqlite.SQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

// Step 4 differential Room suite: a real Room 3 database built with
// Room.inMemoryDatabaseBuilder().setDriver(...) runs the identical tests
// against DoltLiteDriver AND BundledSQLiteDriver (the oracle) via
// per-platform concrete subclasses — same shape as Step 3's
// AbstractDriverConformanceTest.
//
// Test list (red-green; add cases as they occur):
// - [x] suspend @Insert + @Query roundtrip through a generated DAO
// - [ ] parameterized list @Query; @Update and @Delete return change counts
abstract class AbstractRoomConformanceTest {

    /** The driver under test — DoltLite or the Bundled oracle. */
    abstract fun driver(): SQLiteDriver

    /** A fresh, non-existing file path for a temporary on-disk database. */
    abstract fun tempDbPath(): String

    private fun inMemoryDb(): RoomConformanceDb =
        Room.inMemoryDatabaseBuilder<RoomConformanceDb>()
            .setDriver(driver())
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

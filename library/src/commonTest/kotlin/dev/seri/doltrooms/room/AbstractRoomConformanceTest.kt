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
// - [ ] suspend @Insert + @Query roundtrip through a generated DAO
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

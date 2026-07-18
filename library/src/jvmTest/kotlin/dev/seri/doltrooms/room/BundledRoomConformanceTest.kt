package dev.seri.doltrooms.room

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * The Step 4 Room suite against stock SQLite via `BundledSQLiteDriver` —
 * the differential oracle: a test failing here is a bad test, not a
 * DoltLite divergence.
 */
class BundledRoomConformanceTest : AbstractRoomConformanceTest() {
    override fun driver(): SQLiteDriver = BundledSQLiteDriver()

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-room", ".db").also { it.delete() }.absolutePath
}

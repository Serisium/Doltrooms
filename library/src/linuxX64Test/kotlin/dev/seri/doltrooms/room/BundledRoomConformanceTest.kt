package dev.seri.doltrooms.room

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.seri.doltrooms.driver.nativeTempDbPath

/**
 * The Step 4 Room suite against stock SQLite via `BundledSQLiteDriver` on
 * linuxX64 — the differential oracle: a test failing here is a bad test,
 * not a DoltLite divergence.
 */
class BundledRoomConformanceTest : AbstractRoomConformanceTest() {
    override fun driver(): SQLiteDriver = BundledSQLiteDriver()

    override fun tempDbPath(): String = nativeTempDbPath("doltrooms-room")
}

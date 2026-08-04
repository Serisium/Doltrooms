package dev.seri.doltrooms.room

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import dev.seri.doltrooms.driver.nativeTempDbPath

/** The Step 4 Room suite running on iOS against the cinterop driver. */
class DoltLiteRoomConformanceTest : AbstractRoomConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String = nativeTempDbPath("doltrooms-room")
}

package dev.seri.doltrooms.room

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File

/** The Step 4 Room suite against the driver this project builds. */
class DoltLiteRoomConformanceTest : AbstractRoomConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-room", ".db").also { it.delete() }.absolutePath
}

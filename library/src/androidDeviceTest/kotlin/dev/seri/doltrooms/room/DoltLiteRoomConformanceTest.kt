package dev.seri.doltrooms.room

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File

/** The Step 4 Room suite running ON DEVICE (see the driver concrete). */
class DoltLiteRoomConformanceTest : AbstractRoomConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-room", ".db").also { it.delete() }.absolutePath
}

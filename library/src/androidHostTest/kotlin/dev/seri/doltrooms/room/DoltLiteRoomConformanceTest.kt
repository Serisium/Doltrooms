package dev.seri.doltrooms.room

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File

/** The Step 4 Room suite running as an Android host test. */
class DoltLiteRoomConformanceTest : AbstractRoomConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    // The mockable android.jar drops exception messages (see the base class).
    override val exceptionMessagesObservable: Boolean = false

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-room", ".db").also { it.delete() }.absolutePath
}

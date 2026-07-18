package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File

/** The Step 7 dolt_* helper suite running as an Android host test. */
class DoltLiteDoltDatabaseTest : AbstractDoltDatabaseTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    // The mockable android.jar drops exception messages (see the base class).
    override val exceptionMessagesObservable: Boolean = false

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-dolt", ".db").also { it.delete() }.absolutePath
}

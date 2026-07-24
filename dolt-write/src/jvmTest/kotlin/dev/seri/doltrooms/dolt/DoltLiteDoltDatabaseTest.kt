package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File

/** The Step 7 dolt_* helper suite against the DoltLite driver. */
class DoltLiteDoltDatabaseTest : AbstractDoltDatabaseTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-dolt", ".db").also { it.delete() }.absolutePath
}

package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteDriver
import dev.seri.doltrooms.driver.DoltLiteDriver
import dev.seri.doltrooms.driver.nativeTempDbPath

/** The Step 7 dolt_* helper suite on linuxX64 against the cinterop driver. */
class DoltLiteDoltDatabaseTest : AbstractDoltDatabaseTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String = nativeTempDbPath("doltrooms-dolt")
}

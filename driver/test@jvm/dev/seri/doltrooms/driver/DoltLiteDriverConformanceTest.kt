package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver
import java.io.File

/** The Step 3 conformance suite against the driver this project builds. */
class DoltLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-conformance", ".db").also { it.delete() }.absolutePath
}

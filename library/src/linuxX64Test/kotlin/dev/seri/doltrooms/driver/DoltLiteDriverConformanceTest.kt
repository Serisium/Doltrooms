package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver

/**
 * The Step 3 conformance suite running on linuxX64 against the
 * Kotlin/Native (cinterop) DoltLite driver.
 */
class DoltLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String = nativeTempDbPath("doltrooms-conformance")
}

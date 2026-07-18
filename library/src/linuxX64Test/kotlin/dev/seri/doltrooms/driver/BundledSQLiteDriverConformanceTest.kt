package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * The Step 3 conformance suite against stock SQLite via
 * `BundledSQLiteDriver` on linuxX64 — the differential oracle: a test
 * failing here is a bad test, not a DoltLite divergence.
 */
class BundledSQLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = BundledSQLiteDriver()

    override fun tempDbPath(): String = nativeTempDbPath("doltrooms-conformance")
}

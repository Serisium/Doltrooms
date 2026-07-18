package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver

/** The Step 3 conformance suite against the driver this project builds. */
class DoltLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()
}

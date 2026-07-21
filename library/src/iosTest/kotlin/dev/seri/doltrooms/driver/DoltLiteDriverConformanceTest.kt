package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver

/**
 * The Step 3 conformance suite running on iOS against the Kotlin/Native
 * (cinterop) DoltLite driver — the deferred-verification iOS checklist,
 * item 4. No Bundled-oracle concrete here: two statically linked engines
 * in one binary silently resolve to one (the linuxX64 lesson).
 */
class DoltLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String = nativeTempDbPath("doltrooms-conformance")
}

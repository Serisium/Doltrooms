package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver
import java.io.File

/**
 * The Step 3 conformance suite running ON DEVICE (real android.jar, so —
 * unlike the host-test concrete — exception messages stay observable;
 * the AAR's per-ABI libdoltroomsjni.so serves the engine).
 */
class DoltLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-conformance", ".db").also { it.delete() }.absolutePath
}

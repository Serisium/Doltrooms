package dev.seri.doltrooms.driver

import androidx.sqlite.SQLiteDriver
import java.io.File

/**
 * The Step 3 conformance suite running as an Android host test (on the
 * host JVM, against the androidMain loader + android variant of the
 * androidx.sqlite stack).
 */
class DoltLiteDriverConformanceTest : AbstractDriverConformanceTest() {
    override fun driver(): SQLiteDriver = DoltLiteDriver()

    // The mockable android.jar drops exception messages (see the base class).
    override val exceptionMessagesObservable: Boolean = false

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-conformance", ".db").also { it.delete() }.absolutePath
}

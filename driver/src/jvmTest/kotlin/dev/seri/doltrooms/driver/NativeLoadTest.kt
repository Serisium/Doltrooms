package dev.seri.doltrooms.driver

import kotlin.test.Test
import kotlin.test.assertEquals

// Step 1 test list:
// - [ ] native library loads and reports the bundled engine version
class NativeLoadTest {

    @Test
    fun nativeLibraryLoadsAndReportsEngineVersion() {
        // DoltLite 0.11.33 amalgamation carries SQLITE_VERSION "3.54.0";
        // the DoltLite release version is only visible via SQL dolt_version().
        assertEquals("3.54.0", DoltLiteNative.libVersion())
    }
}

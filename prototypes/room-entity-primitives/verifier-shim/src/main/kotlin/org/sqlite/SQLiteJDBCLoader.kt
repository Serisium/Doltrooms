package org.sqlite

/**
 * Shadow of xerial's `org.sqlite.SQLiteJDBCLoader`. Room's verifier calls
 * [initialize] once (native-library extraction, in xerial). The DoltLite
 * engine loads its own native library lazily on the first
 * `DoltLiteDriver().open(...)`, so this is a no-op.
 */
public class SQLiteJDBCLoader {
    public companion object {
        @JvmStatic
        public fun initialize(): Boolean = true
    }
}

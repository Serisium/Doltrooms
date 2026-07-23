package org.sqlite

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverPropertyInfo
import java.util.Properties
import java.util.logging.Logger

/**
 * Shadow of xerial's `org.sqlite.JDBC`, backed by DoltLite instead of
 * stock SQLite. Room's `DatabaseVerifier` resolves this class by name;
 * with xerial excluded from the processor classpath, this is the only
 * `org.sqlite.JDBC` there and every verification query runs on DoltLite.
 */
public class JDBC : Driver {

    public companion object {
        internal const val PREFIX: String = "jdbc:sqlite:"

        @JvmStatic
        public fun isValidURL(url: String?): Boolean =
            url != null && url.lowercase().startsWith(PREFIX)

        @JvmStatic
        public fun createConnection(url: String, properties: Properties): SQLiteConnection {
            require(isValidURL(url)) { "invalid database url: $url" }
            return SQLiteConnection(DoltVerifierEngine.connect(url.trim()))
        }
    }

    override fun connect(url: String?, info: Properties?): Connection? =
        if (isValidURL(url)) createConnection(url!!, info ?: Properties()) else null

    override fun acceptsURL(url: String?): Boolean = isValidURL(url)

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> =
        emptyArray()

    override fun getMajorVersion(): Int = 1

    override fun getMinorVersion(): Int = 0

    override fun jdbcCompliant(): Boolean = false

    override fun getParentLogger(): Logger =
        Logger.getLogger("org.sqlite.doltrooms-verifier-shim")
}

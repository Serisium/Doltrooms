package org.sqlite

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.PreparedStatement
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Statement

/**
 * The minimum JDBC surface Room 3.0.0's `DatabaseVerifier` touches,
 * implemented over the public `DoltLiteDriver` (androidx.sqlite API).
 *
 * Contract pinned from room3-compiler 3.0.0 bytecode
 * (`androidx.room3.verifier.DatabaseVerifier` + `jdbc_ext.kt`):
 *
 *  - static init: `SQLiteJDBCLoader.initialize()`,
 *    `JDBC.isValidURL("jdbc:sqlite::memory:")`,
 *    `DriverManager.getDriver("jdbc:sqlite:")` (falls back to
 *    `new JDBC()` + registerDriver); warns unless the resolved driver is
 *    an `org.sqlite.JDBC` instance.
 *  - `create()`: `JDBC.createConnection("jdbc:sqlite::memory:", Properties)`
 *    — any Throwable here silently DISABLES verification
 *    (CANNOT_CREATE_VERIFICATION_DATABASE).
 *  - schema setup: `connection.createStatement().executeUpdate(ddl)` for
 *    every entity's CREATE TABLE, index, and @DatabaseView DDL.
 *  - `analyze(sql)`: `connection.prepareStatement(sql)` — an
 *    `SQLException` here is the "There is a problem with the query"
 *    compile error — then `getMetaData()` (never executed):
 *    `getColumnCount` / `getColumnName` / `getColumnTypeName` (matched
 *    against SQLTypeAffinity names NULL/TEXT/INTEGER/REAL/BLOB, anything
 *    else → NULL affinity) / `getTableName`.
 */
internal object DoltVerifierEngine {

    fun connect(url: String): java.sql.Connection {
        val path = url.removePrefix(JDBC.PREFIX).ifEmpty { ":memory:" }
        val connection = try {
            DoltLiteDriver().open(path)
        } catch (e: Throwable) {
            // Room swallows this into a silent "verification disabled"
            // warning — leave a loud trail before rethrowing.
            System.err.println(
                "doltrooms verifier-shim: cannot open DoltLite for query " +
                    "verification (${e.message}). Room will DISABLE query " +
                    "verification — dolt_* queries will not be checked."
            )
            throw SQLException("doltrooms verifier-shim: ${e.message}", e)
        }
        return jdkProxy<java.sql.Connection>(ConnectionHandler(connection))
    }

    /** Base handler: Object methods work, everything unimplemented throws. */
    private abstract class StubHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? =
            when (method.name) {
                "toString" -> "doltrooms-verifier-shim:${javaClass.simpleName}"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> {
                    val handled = handle(method, args)
                        ?: throw UnsupportedOperationException(
                            "verifier-shim does not implement " +
                                "${method.declaringClass.simpleName}.${method.name}"
                        )
                    handled.value
                }
            }

        /** Return the result boxed in [Handled], or null if unhandled. */
        abstract fun handle(method: Method, args: Array<out Any?>?): Handled?
    }

    /** Boxes a nullable return value so `handle` can signal "unhandled". */
    private class Handled(val value: Any?)

    private class ConnectionHandler(
        private val connection: androidx.sqlite.SQLiteConnection,
    ) : StubHandler() {
        private var closed = false

        override fun handle(method: Method, args: Array<out Any?>?): Handled? = when (method.name) {
            "createStatement" -> Handled(jdkProxy<Statement>(DdlStatementHandler(connection)))
            "prepareStatement" -> Handled(prepare(args!![0] as String))
            "isClosed" -> Handled(closed)
            "close" -> {
                if (!closed) connection.close()
                closed = true
                Handled(null)
            }
            else -> null
        }

        private fun prepare(sql: String): PreparedStatement {
            val columns = try {
                connection.prepare(sql).use { statement -> harvest(statement) }
            } catch (e: SQLiteException) {
                throw SQLException(e.message, e)
            }
            return jdkProxy<PreparedStatement>(PreparedStatementHandler(columns))
        }

        /**
         * Column names come from the prepared statement (sqlite3_column_name
         * needs no execution). Affinities would need sqlite3_column_decltype,
         * which androidx.sqlite does not expose — instead step() once and
         * read the first row's storage classes; on an empty result (or a
         * step error) every column reports NULL affinity, which Room treats
         * as "unknown" exactly like xerial's expression columns. Stepping
         * executes the query against the throwaway in-memory verification
         * repo, whose user tables are empty.
         */
        private fun harvest(statement: SQLiteStatement): List<ColumnMeta> {
            val names = List(statement.getColumnCount()) { statement.getColumnName(it) }
            val types = try {
                if (statement.step()) {
                    List(names.size) { index ->
                        when (statement.getColumnType(index)) {
                            SQLITE_DATA_INTEGER -> "INTEGER"
                            SQLITE_DATA_FLOAT -> "REAL"
                            SQLITE_DATA_TEXT -> "TEXT"
                            SQLITE_DATA_BLOB -> "BLOB"
                            else -> "NULL"
                        }
                    }
                } else {
                    List(names.size) { "NULL" }
                }
            } catch (_: SQLiteException) {
                List(names.size) { "NULL" }
            }
            return names.zip(types) { name, type -> ColumnMeta(name, type) }
        }
    }

    private class DdlStatementHandler(
        private val connection: androidx.sqlite.SQLiteConnection,
    ) : StubHandler() {
        override fun handle(method: Method, args: Array<out Any?>?): Handled? = when (method.name) {
            "executeUpdate" -> {
                val sql = args!![0] as String
                try {
                    connection.prepare(sql).use { statement -> statement.step() }
                } catch (e: SQLiteException) {
                    throw SQLException(e.message, e)
                }
                // DoltLite materializes the generated per-table TVFs
                // (dolt_at_<t>, dolt_diff_<t>, dolt_history_<t>, ...) for
                // COMMITTED tables only — verified empirically: without
                // this commit, @Query("... FROM dolt_at_fruittie(...)")
                // fails verification with "no such table". Commit the
                // verification repo after every schema statement.
                try {
                    connection.prepare("SELECT dolt_commit('-Am', 'verifier schema')")
                        .use { statement -> statement.step() }
                } catch (_: SQLiteException) {
                    // "nothing to commit" — e.g. a re-run or a no-op DDL.
                }
                Handled(0)
            }
            "close" -> Handled(null)
            else -> null
        }
    }

    private class PreparedStatementHandler(
        private val columns: List<ColumnMeta>,
    ) : StubHandler() {
        override fun handle(method: Method, args: Array<out Any?>?): Handled? = when (method.name) {
            "getMetaData" -> Handled(jdkProxy<ResultSetMetaData>(MetaDataHandler(columns)))
            "close" -> Handled(null)
            else -> null
        }
    }

    private class MetaDataHandler(
        private val columns: List<ColumnMeta>,
    ) : StubHandler() {
        // JDBC metadata indexes are 1-based.
        override fun handle(method: Method, args: Array<out Any?>?): Handled? = when (method.name) {
            "getColumnCount" -> Handled(columns.size)
            "getColumnName" -> Handled(columns[args!![0] as Int - 1].name)
            "getColumnTypeName" -> Handled(columns[args!![0] as Int - 1].typeName)
            // sqlite3_column_table_name is not reachable through
            // androidx.sqlite; null means "unknown origin", which Room
            // accepts (it then resolves ambiguous columns by parsing).
            "getTableName" -> Handled(null)
            else -> null
        }
    }

    private data class ColumnMeta(val name: String, val typeName: String)

    private inline fun <reified T> jdkProxy(handler: InvocationHandler): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java), handler) as T
}

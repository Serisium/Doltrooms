# The androidx.sqlite interfaces, verbatim (2.7.0, nonWeb)

Copied from androidx-main on 2026-07-17. Sources:
`sqlite/sqlite/src/commonMain/kotlin/androidx/sqlite/SQLiteDriver.kt`,
`…/nonWebMain/kotlin/androidx/sqlite/SQLiteDriver.nonWeb.kt`,
`SQLiteConnection.nonWeb.kt`, `SQLiteStatement.nonWeb.kt`,
`…/commonMain/kotlin/androidx/sqlite/SQLite.kt`,
`SQLiteException.kt`, `…/nonWebMain/kotlin/androidx/sqlite/SQLite.nonWeb.kt`
— all under
https://github.com/androidx/androidx/tree/androidx-main/sqlite/sqlite/src.
KDoc trimmed to load-bearing lines.

## SQLiteDriver

```kotlin
// commonMain (expect) — note: no open() here
public expect interface SQLiteDriver {
    /**
     * Identifies whether the driver has an internal connection pool or not.
     *
     * A driver with an internal pool should be capable of opening connections that are safe to be
     * used in a multi-thread and concurrent environment whereas a driver that does not have an
     * internal pool will require the application to manage connections in a thread-safe manner. A
     * driver might not report containing a connection pool but might still be safe to be used in a
     * multi-thread environment, such behavior will depend on the driver implementation.
     *
     * The value returned should be used as a signal to higher abstractions in order to determine if
     * the driver and its connections should be managed by an external connection pool or not.
     */
    @Suppress("INAPPLICABLE_JVM_NAME") // Due to KT-31420
    @get:JvmName("hasConnectionPool")
    public open val hasConnectionPool: Boolean
}

// nonWebMain actual
public actual interface SQLiteDriver {
    public actual val hasConnectionPool: Boolean
        get() = false

    /**
     * Opens a new database connection.
     *
     * To open an in-memory database use the special name `:memory:` as the [fileName].
     */
    public fun open(fileName: String): SQLiteConnection
}
```

(webMain actual is identical except `suspend fun open`.)

## SQLiteConnection

```kotlin
@Suppress("NotCloseable")
public actual interface SQLiteConnection : AutoCloseable {

    /** Returns true if the connection has an active transaction, false otherwise. */
    public actual fun inTransaction(): Boolean {
        throw NotImplementedError("$this does not implement inTransaction().")
    }

    /**
     * Prepares a new SQL statement.
     * See also [Compiling a SQL statement](https://www.sqlite.org/c3ref/prepare.html)
     */
    public fun prepare(sql: String): SQLiteStatement

    /**
     * Closes the database connection.
     * Once a connection is closed it should no longer be used. Calling this function on an already
     * closed database connection is a no-op.
     */
    public actual override fun close()
}
```

## SQLiteStatement (complete)

Bind indices **1-based**; column indices **0-based** (per-method KDoc:
"the 1-based index of the parameter" / "the 0-based index of the
column").

```kotlin
@Suppress("NotCloseable")
public actual interface SQLiteStatement : AutoCloseable {
    public actual fun bindBlob(@IntRange(from = 1) index: Int, value: ByteArray)
    public actual fun bindDouble(@IntRange(from = 1) index: Int, value: Double)
    public actual fun bindFloat(@IntRange(from = 1) index: Int, value: Float) {
        bindDouble(index, value.toDouble())
    }
    public actual fun bindLong(@IntRange(from = 1) index: Int, value: Long)
    public actual fun bindInt(@IntRange(from = 1) index: Int, value: Int) {
        bindLong(index, value.toLong())
    }
    public actual fun bindBoolean(@IntRange(from = 1) index: Int, value: Boolean) {
        bindLong(index, if (value) 1L else 0L)
    }
    public actual fun bindText(@IntRange(from = 1) index: Int, value: String)
    public actual fun bindNull(@IntRange(from = 1) index: Int)

    public actual fun getBlob(@IntRange(from = 0) index: Int): ByteArray
    public actual fun getDouble(@IntRange(from = 0) index: Int): Double
    public actual fun getFloat(@IntRange(from = 0) index: Int): Float {
        return getDouble(index).toFloat()
    }
    public actual fun getLong(@IntRange(from = 0) index: Int): Long
    public actual fun getInt(@IntRange(from = 0) index: Int): Int {
        return getLong(index).toInt()
    }
    public actual fun getBoolean(@IntRange(from = 0) index: Int): Boolean {
        return getLong(index) != 0L
    }
    public actual fun getText(@IntRange(from = 0) index: Int): String
    public actual fun isNull(@IntRange(from = 0) index: Int): Boolean

    public actual fun getColumnCount(): Int
    public actual fun getColumnName(@IntRange(from = 0) index: Int): String
    public actual fun getColumnNames(): List<String> {
        return List(getColumnCount()) { i -> getColumnName(i) }
    }
    /**
     * The data type can be used to determine the preferred `get*()` function to be used for the
     * column but other getters may perform data type conversion.
     */
    @DataType public actual fun getColumnType(@IntRange(from = 0) index: Int): Int

    /**
     * Executes the statement and evaluates the next result row if available.
     *
     * A statement is initially prepared and compiled but is not executed until one or more calls to
     * this function. If the statement execution produces result rows then this function will return
     * `true` indicating there is a new row of data ready to be read.
     *
     * @return true if there are more rows to evaluate or false if the statement is done executing
     */
    public fun step(): Boolean

    /**
     * Resets the prepared statement back to initial state so that it can be re-executed via [step].
     * Any parameter bound via the `bind*()` APIs will retain their value.
     */
    public actual fun reset()

    /** Clears all parameter bindings. Unset bindings are treated as NULL. */
    public actual fun clearBindings()

    /**
     * Closes the statement.
     * Once a statement is closed it should no longer be used. Calling this function on an already
     * closed statement is a no-op.
     */
    public actual override fun close()
}
```

## Constants, exception, helpers (SQLite.kt / SQLite.nonWeb.kt)

```kotlin
public const val SQLITE_DATA_INTEGER: Int = 1
public const val SQLITE_DATA_FLOAT: Int = 2
public const val SQLITE_DATA_TEXT: Int = 3
public const val SQLITE_DATA_BLOB: Int = 4
public const val SQLITE_DATA_NULL: Int = 5

/** Throws a [SQLiteException] with its message formed by the given [errorCode] amd [errorMsg]. */
public fun throwSQLiteException(errorCode: Int, errorMsg: String?): Nothing {
    val message = buildString {
        append("Error code: $errorCode")
        if (errorMsg != null) {
            append(", message: $errorMsg")
        }
    }
    throw SQLiteException(message)
}

public expect class SQLiteException
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(message: String) : RuntimeException

/** Executes a single SQL statement that returns no values. */
public fun SQLiteConnection.execSQL(sql: String) {
    this.prepare(sql).use { it.step() }
}
```

On Android the API surface aliases the exception:
`public typealias SQLiteException = android.database.SQLException`
(`sqlite/sqlite/api/current.txt`). Since the constructor is
`@RestrictTo`, third-party drivers throw via `throwSQLiteException`.

## sqlite-async (2.7.0) — web-portable bridges

`sqlite/sqlite-async/src/commonMain/kotlin/androidx/sqlite/async/SQLiteAsync.kt`:

```kotlin
public expect suspend fun SQLiteDriver.open(fileName: String): SQLiteConnection
public expect suspend fun SQLiteConnection.prepare(sql: String): SQLiteStatement
public expect suspend fun SQLiteConnection.executeSQL(sql: String)
public expect suspend fun SQLiteStatement.step(): Boolean
```

## Open flags (sqlite-bundled extension, not core contract)

The core `open(fileName)` contract only specifies `:memory:`.
`BundledSQLiteDriver.open(fileName, flags)` (default
`READWRITE or CREATE`) defines in `BundledSQLite.kt`:
`SQLITE_OPEN_READONLY (0x01)`, `READWRITE (0x02)`, `CREATE (0x04)`,
`URI (0x40)`, `MEMORY (0x80)`, `NOMUTEX (0x8000)`,
`FULLMUTEX (0x10000)`, `NOFOLLOW (0x01000000)`,
`EXRESCODE (0x02000000)`.

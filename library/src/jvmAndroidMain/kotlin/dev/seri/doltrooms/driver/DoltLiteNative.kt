package dev.seri.doltrooms.driver

/**
 * JNI entry points into `libdoltroomsjni` — the DoltLite amalgamation
 * statically linked with the doltrooms glue
 * (`src/jvmAndroidMain/jni/doltrooms_jni.cpp`). Methods are registered
 * from `JNI_OnLoad` via `RegisterNatives`, so a signature mismatch
 * fails at library-load time rather than first call.
 */
internal object DoltLiteNative {
    init {
        loadNativeLibrary()
    }

    fun libVersion(): String = nativeLibVersion()

    private external fun nativeLibVersion(): String

    /**
     * `sqlite3_open_v2` (+ `sqlite3_extended_result_codes(db, 1)` on
     * success). Returns the `sqlite3*` handle — non-null even on most
     * failures, per https://www.sqlite.org/c3ref/open.html — and writes
     * the result code into `rcOut[0]`.
     */
    external fun nativeOpen(fileName: String, flags: Int, rcOut: IntArray): Long

    /** `sqlite3_close_v2`. */
    external fun nativeClose(dbPointer: Long)

    /**
     * `sqlite3_prepare16_v2` (UTF-16, like BundledSQLiteDriver). Returns
     * the result code and writes the `sqlite3_stmt*` handle into
     * `stmtOut[0]` (0 on failure).
     */
    external fun nativePrepare(dbPointer: Long, sql: String, stmtOut: LongArray): Int

    /** `sqlite3_step`. Returns the raw result code (SQLITE_ROW/DONE/error). */
    external fun nativeStep(stmtPointer: Long): Int

    /** `sqlite3_finalize`. Result code deliberately ignored — close is a no-op contract. */
    external fun nativeFinalize(stmtPointer: Long)

    /** `sqlite3_stmt_busy` — nonzero while the statement is positioned on a row. */
    external fun nativeStmtBusy(stmtPointer: Long): Int

    /** `sqlite3_reset`. Returns the result code (echoes the last evaluation's error). */
    external fun nativeReset(stmtPointer: Long): Int

    /** `sqlite3_clear_bindings`. Returns the result code. */
    external fun nativeClearBindings(stmtPointer: Long): Int

    /** `sqlite3_errmsg16` for the connection's most recent failure, or null. */
    external fun nativeErrmsg(dbPointer: Long): String?

    /** `sqlite3_get_autocommit` — zero while a transaction is open. */
    external fun nativeGetAutocommit(dbPointer: Long): Int

    /** `sqlite3_bind_int64`. Returns the result code. */
    external fun nativeBindLong(stmtPointer: Long, index: Int, value: Long): Int

    /** `sqlite3_bind_double`. Returns the result code. */
    external fun nativeBindDouble(stmtPointer: Long, index: Int, value: Double): Int

    /** `sqlite3_bind_text16` with `SQLITE_TRANSIENT`. Returns the result code. */
    external fun nativeBindText(stmtPointer: Long, index: Int, value: String): Int

    /**
     * `sqlite3_bind_blob` with `SQLITE_TRANSIENT` (`bind_zeroblob` for an
     * empty array, which would otherwise bind NULL). Returns the result code.
     */
    external fun nativeBindBlob(stmtPointer: Long, index: Int, value: ByteArray): Int

    /** `sqlite3_bind_null`. Returns the result code. */
    external fun nativeBindNull(stmtPointer: Long, index: Int): Int

    /** `sqlite3_column_int64` (applies SQLite's coercion rules). */
    external fun nativeColumnLong(stmtPointer: Long, index: Int): Long

    /** `sqlite3_column_double` (applies SQLite's coercion rules). */
    external fun nativeColumnDouble(stmtPointer: Long, index: Int): Double

    /**
     * `sqlite3_column_blob` + `sqlite3_column_bytes` (blob first, then bytes,
     * per https://www.sqlite.org/c3ref/column_blob.html), copied into a new
     * array. NULL values and zero-length blobs both return an empty array.
     */
    external fun nativeColumnBlob(stmtPointer: Long, index: Int): ByteArray

    /** `sqlite3_column_type` — one of the five SQLITE_DATA_* fundamental types. */
    external fun nativeColumnType(stmtPointer: Long, index: Int): Int

    /** `sqlite3_column_count` — static metadata, usable before stepping. */
    external fun nativeColumnCount(stmtPointer: Long): Int

    /** `sqlite3_column_name` (copied immediately). Null only on out-of-memory. */
    external fun nativeColumnName(stmtPointer: Long, index: Int): String?

    /**
     * `sqlite3_column_text16` + `sqlite3_column_bytes16` (text first, then
     * bytes, per https://www.sqlite.org/c3ref/column_blob.html). Returns
     * null for SQL NULL or out-of-memory.
     */
    external fun nativeColumnText(stmtPointer: Long, index: Int): String?
}

/**
 * Loads `libdoltroomsjni`. Desktop JVM extracts it from JAR resources;
 * Android resolves it from the AAR's jniLibs.
 */
internal expect fun loadNativeLibrary()

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
}

/**
 * Loads `libdoltroomsjni`. Desktop JVM extracts it from JAR resources;
 * Android resolves it from the AAR's jniLibs.
 */
internal expect fun loadNativeLibrary()

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
}

/**
 * Loads `libdoltroomsjni`. Desktop JVM extracts it from JAR resources;
 * Android resolves it from the AAR's jniLibs.
 */
internal expect fun loadNativeLibrary()

package dev.seri.doltrooms.driver

import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual fun loadNativeLibrary() = NativeLibraryLoader.load()

/**
 * Extracts `libdoltroomsjni` from JAR resources
 * (`natives/<os>-<arch>/`) to a temp file and `System.load`s it.
 * Override with `-Ddev.seri.doltrooms.lib.path=/abs/path/to/lib` to
 * load a locally built library instead.
 */
internal object NativeLibraryLoader {
    private const val LIB_PATH_PROPERTY = "dev.seri.doltrooms.lib.path"

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        val explicit = System.getProperty(LIB_PATH_PROPERTY)
        if (explicit != null) {
            System.load(explicit)
            loaded = true
            return
        }
        val resource = "natives/${osClassifier()}/${libFileName()}"
        val stream = NativeLibraryLoader::class.java.classLoader.getResourceAsStream(resource)
            ?: error(
                "doltrooms native library not found on classpath: $resource " +
                    "(override with -D$LIB_PATH_PROPERTY=/abs/path)"
            )
        val tmp = Files.createTempFile("doltroomsjni", libFileName().substringAfterLast('.').let { ".$it" })
        stream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
        tmp.toFile().deleteOnExit()
        System.load(tmp.toAbsolutePath().toString())
        loaded = true
    }

    private fun osClassifier(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = when (val a = System.getProperty("os.arch").lowercase()) {
            "amd64", "x86_64" -> "x64"
            "aarch64", "arm64" -> "arm64"
            else -> a
        }
        val osName = when {
            "linux" in os -> "linux"
            "mac" in os || "darwin" in os -> "osx"
            "windows" in os -> "win"
            else -> error("Unsupported OS for doltrooms native library: $os")
        }
        return "$osName-$arch"
    }

    private fun libFileName(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            "mac" in os || "darwin" in os -> "libdoltroomsjni.dylib"
            "windows" in os -> "doltroomsjni.dll"
            else -> "libdoltroomsjni.so"
        }
    }
}

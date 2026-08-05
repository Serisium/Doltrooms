// Task actions for the doltlite build plugin — the Kotlin Toolchain
// replacement for the custom Gradle tasks (downloadDoltliteAmalgamation,
// compileDoltliteJni*, compileDoltliteStatic*) and the interim
// prepare-doltlite.sh. Wiring lives in plugin.yaml; every failure path
// throws with a message that tells the operator exactly what to install
// or fix, because these tasks run automatically as part of `./kotlin build`.
package dev.seri.doltrooms.build

import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction

// One pinned DoltLite version across all platforms (AGENTS.md); this was
// gradle/libs.versions.toml's `doltlite` entry.
private const val DOLTLITE_VERSION = "0.11.33"

// SHA-256 of doltlite-amalgamation-0.11.33.zip, recorded 2026-07-17.
private const val AMALGAMATION_SHA256 =
    "12e47892ead2b8016234eed3377e9e659bd61a9a6e932f9364d7326dbc095d13"

// SHA-256 of doltlite-tools-linux-x64-0.11.33.zip, recorded 2026-07-18.
private const val TOOLS_SHA256 =
    "6d9b2353f051ce79d3637d57facae293cacb320cfb5b3eebe896c18af1338932"

private const val RELEASES = "https://github.com/dolthub/doltlite/releases/download"

// androidx sqlite-bundled's flag set minus the two flags DoltLite's fork
// cannot build with (SQLITE_OMIT_SHARED_CACHE, SQLITE_DEFAULT_WAL_SYNCHRONOUS);
// SQLITE_THREADSAFE=2 because connection confinement is the pool's job.
private val COMPILE_FLAGS = listOf(
    "-DHAVE_USLEEP=1",
    "-DSQLITE_DEFAULT_AUTOVACUUM=1",
    "-DSQLITE_DEFAULT_MEMSTATUS=0",
    "-DSQLITE_ENABLE_COLUMN_METADATA",
    "-DSQLITE_ENABLE_FTS3",
    "-DSQLITE_ENABLE_FTS3_PARENTHESIS",
    "-DSQLITE_ENABLE_FTS4",
    "-DSQLITE_ENABLE_FTS5",
    "-DSQLITE_ENABLE_JSON1",
    "-DSQLITE_ENABLE_MATH_FUNCTIONS",
    "-DSQLITE_ENABLE_NORMALIZE",
    "-DSQLITE_ENABLE_RTREE",
    "-DSQLITE_ENABLE_STAT4",
    "-DSQLITE_HAVE_ISNAN",
    "-DSQLITE_OMIT_BUILTIN_TEST",
    "-DSQLITE_OMIT_DEPRECATED",
    "-DSQLITE_OMIT_PROGRESS_CALLBACK",
    "-DSQLITE_SECURE_DELETE",
    "-DSQLITE_TEMP_STORE=3",
    "-DSQLITE_THREADSAFE=2",
    "-DDOLTLITE_VERSION=\"$DOLTLITE_VERSION\"",
)

private enum class Host { MAC, LINUX_X64, OTHER_LINUX }

private val host: Host = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.startsWith("mac") -> Host.MAC
        os.contains("linux") && arch in setOf("amd64", "x86_64") -> Host.LINUX_X64
        os.contains("linux") -> Host.OTHER_LINUX
        else -> error(
            "Unsupported build host for the doltlite plugin: $os/$arch. " +
                "The DoltLite native pieces build on macOS and Linux hosts only."
        )
    }
}

/** Loader classifier for the host, matching NativeLibraryLoader.osClassifier(). */
private val hostClassifier: String = when (host) {
    Host.MAC ->
        if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) "osx-arm64"
        else "osx-x64"
    Host.LINUX_X64 -> "linux-x64"
    Host.OTHER_LINUX -> "linux-arm64"
}

private fun sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(path.readBytes())
        .joinToString("") { "%02x".format(it) }

private fun downloadVerified(url: String, sha256: String, target: Path) {
    if (target.isRegularFile() && sha256(target) == sha256) return
    println("Downloading $url")
    target.createParentDirectories()
    URI(url).toURL().openStream().use { input ->
        target.toFile().outputStream().use { input.copyTo(it) }
    }
    val actual = sha256(target)
    if (actual != sha256) {
        target.deleteIfExists()
        error(
            "SHA-256 mismatch for $url: expected $sha256, got $actual. " +
                "If the DoltLite pin changed, update the checksum constants in " +
                "build-plugins/doltlite/src/tasks.kt (AGENTS.md: pin versions deliberately)."
        )
    }
}

/** Unzips flattening directories: `doltlite-amalgamation-<v>/doltlite.c` -> `doltlite.c`. */
private fun unzipFlat(zip: Path, into: Path) {
    into.createDirectories()
    ZipInputStream(zip.toFile().inputStream().buffered()).use { stream ->
        generateSequence { stream.nextEntry }.forEach { entry ->
            if (entry.isDirectory) return@forEach
            val name = entry.name.substringAfterLast('/')
            if (name.isEmpty()) return@forEach
            into.resolve(name).toFile().outputStream().use { stream.copyTo(it) }
        }
    }
}

private fun exec(vararg command: String, hint: (exit: Int) -> String) {
    val process = try {
        ProcessBuilder(*command).redirectErrorStream(true).start()
    } catch (e: java.io.IOException) {
        error("Cannot execute '${command.first()}' (${e.message}). ${hint(-1)}")
    }
    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    if (output.isNotBlank()) println(output.trim())
    check(exit == 0) { "'${command.joinToString(" ")}' exited with $exit. ${hint(exit)}" }
}

/**
 * A JDK home that carries include/jni.h. The toolchain runs task actions on
 * its own provisioned JRE (no headers), so java.home is NOT good enough.
 */
private fun jdkHomeWithJniHeaders(): Path {
    val candidates = buildList {
        System.getenv("JAVA_HOME")?.let { add(Path.of(it)) }
        // The JRE running this task — works when the operator points the
        // wrapper at a full JDK.
        add(Path.of(System.getProperty("java.home")))
        if (host == Host.MAC) {
            runCatching {
                val p = ProcessBuilder("/usr/libexec/java_home").start()
                val out = p.inputStream.bufferedReader().readText().trim()
                if (p.waitFor() == 0 && out.isNotEmpty()) add(Path.of(out))
            }
        } else {
            // Resolve a JDK from javac on the PATH (readlink through
            // alternatives symlinks, as on Fedora/Debian).
            runCatching {
                val p = ProcessBuilder("sh", "-c", "readlink -f \"$(command -v javac)\"").start()
                val out = p.inputStream.bufferedReader().readText().trim()
                if (p.waitFor() == 0 && out.isNotEmpty()) add(Path.of(out).parent.parent)
            }
        }
    }
    return candidates.firstOrNull { it.resolve("include/jni.h").exists() } ?: error(
        "No JDK with JNI headers (include/jni.h) found. The doltlite JNI glue " +
            "needs a full JDK, not a JRE. Install a JDK and either set JAVA_HOME " +
            "to it or make javac available on the PATH. Checked: " +
            candidates.joinToString { it.absolutePathString() }
    )
}

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
@TaskAction
public fun prepareAmalgamation(@Output outputDir: Path) {
    val zip = outputDir / "doltlite-amalgamation-$DOLTLITE_VERSION.zip"
    downloadVerified(
        url = "$RELEASES/v$DOLTLITE_VERSION/doltlite-amalgamation-$DOLTLITE_VERSION.zip",
        sha256 = AMALGAMATION_SHA256,
        target = zip,
    )
    val src = outputDir / "src"
    src.deleteRecursively()
    unzipFlat(zip, src)
    check((src / "doltlite.c").isRegularFile() && (src / "doltlite.h").isRegularFile()) {
        "Amalgamation zip did not contain doltlite.c/doltlite.h — layout change upstream?"
    }
}

/**
 * Compiles the amalgamation + JNI glue into libdoltroomsjni for the build
 * host, under natives/<classifier>/ where NativeLibraryLoader finds it on
 * the classpath (the directory is contributed as jvm resources).
 *
 * NOTE (parity): the Gradle build shipped ONLY a cross-compiled linux-x64
 * .so in the jvm jar regardless of host; here the jar gets the HOST's
 * library. Fine for dev/test; revisit for publishing (which kmp/lib does
 * not support yet anyway) — see the migration PR description.
 */
@TaskAction
public fun compileHostJni(
    @Input amalgamationDir: Path,
    @Input jniSource: Path,
    // Split outputs: only resourcesDir is contributed as jvm resources;
    // intermediate .o files stay in workDir so they don't get bundled.
    @Output resourcesDir: Path,
    @Output workDir: Path,
) {
    val src = amalgamationDir / "src"
    val jdk = jdkHomeWithJniHeaders()
    val (cc, cxx) = when (host) {
        Host.MAC -> "cc" to "c++"
        else -> "gcc" to "g++"
    }
    val toolHint = { _: Int ->
        if (host == Host.MAC) "Install the Xcode command line tools: xcode-select --install"
        else "Install gcc/g++ (e.g. dnf install gcc gcc-c++ / apt install build-essential)."
    }
    val jniMd = if (host == Host.MAC) "darwin" else "linux"
    val libName = if (host == Host.MAC) "libdoltroomsjni.dylib" else "libdoltroomsjni.so"
    val out = resourcesDir / "natives" / hostClassifier
    out.createDirectories()
    workDir.createDirectories()
    val engineObj = workDir / "doltlite.o"
    val glueObj = workDir / "doltrooms_jni.o"
    exec(
        cc, "-c", "-fPIC", "-O3", *COMPILE_FLAGS.toTypedArray(),
        "-I$src", "-o", "$engineObj", "$src/doltlite.c",
        hint = toolHint,
    )
    exec(
        cxx, "-c", "-fPIC", "-O3", "-fvisibility=hidden", *COMPILE_FLAGS.toTypedArray(),
        "-I$src", "-I$jdk/include", "-I$jdk/include/$jniMd",
        "-o", "$glueObj", "$jniSource",
        hint = toolHint,
    )
    exec(
        cxx, "-shared", "-o", "${out / libName}", "$engineObj", "$glueObj",
        "-lpthread", "-ldl", "-lm",
        hint = toolHint,
    )
    println("Built ${out / libName}")
}

/**
 * Static libdoltlite.a engine archives for the cinterop'd native targets
 * this HOST can produce: linuxX64 on a linux-x64 host (gcc against host
 * glibc, with the glibc-2.19-Konan-sysroot symbol remaps — rationale in the
 * Gradle build's git history), the iOS slices on a macOS host (SDK clang +
 * libtool). Other-host targets are simply absent; generateDef only embeds
 * archives that exist.
 */
@TaskAction
public fun compileStaticArchives(
    @Input amalgamationDir: Path,
    @Output outputDir: Path,
) {
    val src = amalgamationDir / "src"
    when (host) {
        Host.LINUX_X64 -> {
            val dir = (outputDir / "linuxX64").apply { createDirectories() }
            val obj = outputDir / "doltlite-linuxX64.o"
            val hint = { _: Int ->
                "Install gcc, binutils (objcopy, ar): dnf install gcc binutils."
            }
            exec(
                "gcc", "-c", "-fPIC", "-O3", "-DSQLITE_DISABLE_LFS",
                *COMPILE_FLAGS.toTypedArray(), "-I$src",
                "-o", "$obj", "$src/doltlite.c",
                hint = hint,
            )
            // glibc-2.38+ C23 strto* redirects don't exist in Konan's 2.19
            // sysroot; remap back to the classic symbols.
            exec(
                "objcopy",
                "--redefine-sym", "__isoc23_strtol=strtol",
                "--redefine-sym", "__isoc23_strtoul=strtoul",
                "--redefine-sym", "__isoc23_strtoll=strtoll",
                "--redefine-sym", "__isoc23_strtoull=strtoull",
                "$obj",
                hint = hint,
            )
            (dir / "libdoltlite.a").deleteIfExists()
            exec("ar", "rcs", "${dir / "libdoltlite.a"}", "$obj", hint = hint)
        }
        Host.MAC -> {
            val hint = { _: Int ->
                "Full Xcode with the iOS SDK is required for the iOS engine " +
                    "archives: install Xcode and run xcodebuild -runFirstLaunch " +
                    "(xcrun --sdk iphoneos must work)."
            }
            for ((sdk, triple, target) in listOf(
                Triple("iphoneos", "arm64-apple-ios12.0", "iosArm64"),
                Triple("iphonesimulator", "arm64-apple-ios14.0-simulator", "iosSimulatorArm64"),
            )) {
                val dir = (outputDir / target).apply { createDirectories() }
                val obj = outputDir / "doltlite-$target.o"
                exec(
                    "xcrun", "--sdk", sdk, "clang", "-c", "-O3", "-target", triple,
                    *COMPILE_FLAGS.toTypedArray(), "-I$src",
                    "-o", "$obj", "$src/doltlite.c",
                    hint = hint,
                )
                (dir / "libdoltlite.a").deleteIfExists()
                exec(
                    "xcrun", "--sdk", sdk, "libtool", "-static",
                    "-o", "${dir / "libdoltlite.a"}", "$obj",
                    hint = hint,
                )
            }
            compileLinuxX64CrossArchive(src, outputDir)
        }
        Host.OTHER_LINUX -> println(
            "No static engine archives are produced on this host (only " +
                "linux-x64 and macOS recipes exist); native targets will " +
                "cinterop headers-only."
        )
    }
}

/**
 * macOS -> linux-x64 cross-compile of the engine archive, so a Mac builds
 * the full platform set (the Gradle build's single-host property). Uses the
 * SAME split Konan itself uses: clang/llvm-ar from the provisioned llvm-*
 * dependency, retargeted at the x86_64-unknown-linux-gnu-gcc-* package,
 * which serves only as the SYSROOT. -DSQLITE_DISABLE_LFS matches the
 * linux-x64 host build; the glibc-2.19 sysroot predates the C23 strto*
 * redirects, so no objcopy remap is needed here.
 *
 * The packages exist only after Konan has provisioned them (any linuxX64
 * cinterop/compile does this); until then the archive is skipped with a
 * warning and the generated .def simply carries no linux_x64 entries —
 * rebuild after the first `./kotlin build -p linuxX64` to pick it up.
 */
private fun compileLinuxX64CrossArchive(src: Path, outputDir: Path) {
    val deps = Path.of(
        System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan"
    ) / "dependencies"
    fun konanDep(prefix: String): Path? = deps.toFile()
        .listFiles { f -> f.isDirectory && f.name.startsWith(prefix) }
        ?.sortedBy { it.name }?.lastOrNull()?.toPath()
    val llvmBin = konanDep("llvm-")?.let { it / "bin" }
    val gcc = konanDep("x86_64-unknown-linux-gnu-gcc-")
    if (llvmBin == null || gcc == null) {
        println(
            "Skipping the linuxX64 engine archive: Konan's Linux cross " +
                "packages are not provisioned yet under $deps (llvm-*, " +
                "x86_64-unknown-linux-gnu-gcc-*). Run a linuxX64 build once " +
                "(e.g. ./kotlin build -p linuxX64) and rebuild to include it."
        )
        return
    }
    val hint = { _: Int ->
        "Konan cross toolchain failed; re-provision by deleting $deps and " +
            "running a linuxX64 build, or build this archive on a linux-x64 host."
    }
    val retarget = arrayOf(
        "--target=x86_64-unknown-linux-gnu",
        "--sysroot=$gcc/x86_64-unknown-linux-gnu/sysroot",
        "--gcc-toolchain=$gcc",
    )
    val dir = (outputDir / "linuxX64").apply { createDirectories() }
    val obj = outputDir / "doltlite-linuxX64.o"
    exec(
        "$llvmBin/clang", *retarget, "-c", "-fPIC", "-O3", "-DSQLITE_DISABLE_LFS",
        *COMPILE_FLAGS.toTypedArray(), "-I$src",
        "-o", "$obj", "$src/doltlite.c",
        hint = hint,
    )
    (dir / "libdoltlite.a").deleteIfExists()
    exec("$llvmBin/llvm-ar", "rcs", "${dir / "libdoltlite.a"}", "$obj", hint = hint)
    println("Built ${dir / "libdoltlite.a"} (Konan cross toolchain)")
}

/**
 * Generates the cinterop .def from the committed template: substitutes the
 * absolute amalgamation include path and appends staticLibraries/libraryPaths
 * entries for exactly the engine archives this host produced (the .def
 * format takes literal paths only, hence generation).
 */
@TaskAction
public fun generateDef(
    @Input templateFile: Path,
    @Input amalgamationDir: Path,
    @Input archivesDir: Path,
    @Output outputFile: Path,
) {
    // def target suffixes for the archive dirs compileStaticArchives may fill.
    val defTargets = mapOf(
        "linuxX64" to "linux_x64",
        "iosArm64" to "ios_arm64",
        "iosSimulatorArm64" to "ios_simulator_arm64",
    )
    val src = amalgamationDir / "src"
    check(templateFile.isRegularFile()) { "cinterop def template missing: $templateFile" }
    val embedded = buildString {
        appendLine()
        appendLine("# --- generated: per-target engine archives present on this host ---")
        defTargets.forEach { (dir, target) ->
            if ((archivesDir / dir / "libdoltlite.a").isRegularFile()) {
                appendLine("staticLibraries.$target = libdoltlite.a")
                appendLine("libraryPaths.$target = ${(archivesDir / dir).absolutePathString()}")
            }
        }
    }
    outputFile.createParentDirectories()
    outputFile.writeText(
        templateFile.readText().replace("@DOLTLITE_SRC@", src.absolutePathString()) + embedded
    )
    println("Generated $outputFile")
}

/**
 * `./kotlin do fetchRemotesrv` — downloads the doltlite-remotesrv fixture
 * that RemoteServerSyncTest uses on linux-x64 hosts (the only platform with
 * a recorded tools checksum). Deliberately a manual command, not a build
 * dependency: the test property must stay unset on other hosts, and
 * module.yaml has no host-conditional systemProperties. Run jvm tests with:
 *   JAVA_TOOL_OPTIONS="-Ddev.seri.doltrooms.remotesrv=<module>/native/build/tools/doltlite-remotesrv" ./kotlin test -p jvm
 */
@TaskAction
public fun fetchRemotesrv(@Output outputDir: Path) {
    check(host == Host.LINUX_X64) {
        "The doltlite-remotesrv fixture is only pinned for linux-x64 hosts " +
            "(doltlite-tools-linux-x64-$DOLTLITE_VERSION.zip); on this host the " +
            "RemoteServerSyncTest self-skips instead."
    }
    val zip = outputDir / "doltlite-tools-linux-x64-$DOLTLITE_VERSION.zip"
    downloadVerified(
        url = "$RELEASES/v$DOLTLITE_VERSION/doltlite-tools-linux-x64-$DOLTLITE_VERSION.zip",
        sha256 = TOOLS_SHA256,
        target = zip,
    )
    unzipFlat(zip, outputDir)
    outputDir.toFile().listFiles()?.forEach { it.setExecutable(true) }
    println(
        "Fixture ready. Run: JAVA_TOOL_OPTIONS=\"-Ddev.seri.doltrooms.remotesrv=" +
            "${(outputDir / "doltlite-remotesrv").absolutePathString()}\" ./kotlin test -p jvm"
    )
}

private operator fun Path.div(other: String): Path = resolve(other)

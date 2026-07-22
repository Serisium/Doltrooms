import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
    // Room 3 is a TEST-ONLY consumer here: the library ships only the
    // androidx.sqlite driver (D1); the Step 4 Room suite lives in commonTest.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

group = "dev.seri.doltrooms"
version = "0.1.0-SNAPSHOT"

// --- DoltLite amalgamation acquisition + host JNI build -------------------
// One pinned DoltLite version across all platforms (AGENTS.md). The
// amalgamation is the same input androidx.sqlite:sqlite-bundled uses for
// stock SQLite; we compile it together with our JNI glue into a single
// libdoltroomsjni, packaged as a JVM resource (xerial/powersync pattern).

val doltliteVersion: String = libs.versions.doltlite.get()
val doltliteAmalgamationUrl =
    "https://github.com/dolthub/doltlite/releases/download/v$doltliteVersion/doltlite-amalgamation-$doltliteVersion.zip"
// SHA-256 of doltlite-amalgamation-0.11.33.zip, recorded 2026-07-17.
val doltliteAmalgamationSha256 = "12e47892ead2b8016234eed3377e9e659bd61a9a6e932f9364d7326dbc095d13"

// Compile flags: androidx sqlite-bundled's set (bundled-driver-internals
// skill reference), minus two flags DoltLite's fork cannot build with:
// SQLITE_OMIT_SHARED_CACHE (empties the sqlite3BtreeEnter/Leave macros the
// fork's btree shim redefines and calls) and SQLITE_DEFAULT_WAL_SYNCHRONOUS=1
// (compiles setDefaultSyncFlag, which dereferences the Btree struct the fork
// makes opaque). SQLITE_THREADSAFE=2: connections are NOT thread-safe;
// confinement is the pool's job. DOLTLITE_VERSION makes SQL dolt_version()
// report our pin (the amalgamation's fallback is "doltlite-amalgamation").
val doltliteCompileFlags = listOf(
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
    "-DDOLTLITE_VERSION=\"$doltliteVersion\"",
)

val downloadDoltliteAmalgamation by tasks.registering {
    val url = doltliteAmalgamationUrl
    val sha256 = doltliteAmalgamationSha256
    val zipFile = layout.buildDirectory.file("doltlite/doltlite-amalgamation-$doltliteVersion.zip")
    inputs.property("url", url)
    inputs.property("sha256", sha256)
    outputs.file(zipFile)
    doLast {
        val target = zipFile.get().asFile
        fun fileSha256() = MessageDigest.getInstance("SHA-256")
            .digest(target.readBytes())
            .joinToString("") { "%02x".format(it) }
        // A pre-seeded zip that already matches the pin (CI restores it from
        // actions/cache, where Gradle task history doesn't survive the runner)
        // is accepted without a network round-trip.
        if (target.exists() && fileSha256() == sha256) return@doLast
        target.parentFile.mkdirs()
        URI(url).toURL().openStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val actual = fileSha256()
        if (actual != sha256) {
            target.delete()
            error("SHA-256 mismatch for $url: expected $sha256, got $actual")
        }
    }
}

val unpackDoltliteAmalgamation by tasks.registering(Copy::class) {
    from(zipTree(downloadDoltliteAmalgamation.map { it.outputs.files.singleFile })) {
        // Flatten doltlite-amalgamation-<v>/doltlite.c -> doltlite.c
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("doltlite/src"))
}

// --- macOS-host cross toolchain for the Linux-targeted artifacts ----------
// The jvm jar's linux-x64 .so and the linuxX64 cinterop archive must be
// Linux ELF even on the macOS publishing host (docs/deferred-verification
// .md, Maven Central entry). Kotlin/Native's provisioned dependencies
// supply the cross setup: the x86_64-unknown-linux-gnu-gcc-* package serves
// only as the SYSROOT — its gcc/binutils are Linux-host executables — and
// clang/clang++/llvm-ar/ld.lld come from the llvm-* package, the same split
// Konan itself uses (konan.properties: targetToolchain.macos_arm64-linux_x64,
// targetSysRoot.linux_x64). Globbed so Konan version bumps keep resolving,
// and lazy because the packages exist only after provisioning — guaranteed
// on a clean machine by the macOS-only
// dependsOn(downloadKotlinNativeDistribution) at the registration sites.
val hostIsMac = System.getProperty("os.name").lowercase().startsWith("mac")

val konanDependenciesDir: Provider<File> =
    providers.environmentVariable("KONAN_DATA_DIR")
        .orElse(providers.systemProperty("user.home").map { "$it/.konan" })
        .map { File(it, "dependencies") }

fun konanDependency(namePrefix: String): Provider<String> = konanDependenciesDir.map { deps ->
    deps.listFiles { f: File -> f.isDirectory && f.name.startsWith(namePrefix) }
        ?.sortedBy { it.name }
        ?.lastOrNull()
        ?.absolutePath
        ?: error(
            "Kotlin/Native dependency '$namePrefix*' not found in $deps. Run " +
                "'./gradlew :library:downloadKotlinNativeDistribution' once to provision it."
        )
}

val konanLlvmBin: Provider<String> = konanDependency("llvm-").map { "$it/bin" }

/** Konan clang/clang++ retargeted at linux-x64 against Konan's own sysroot. */
fun linuxCrossCompiler(tool: String): Provider<List<String>> =
    konanLlvmBin.zip(konanDependency("x86_64-unknown-linux-gnu-gcc-")) { llvm, gcc ->
        listOf(
            "$llvm/$tool",
            "--target=x86_64-unknown-linux-gnu",
            "--sysroot=$gcc/x86_64-unknown-linux-gnu/sysroot",
            "--gcc-toolchain=$gcc",
        )
    }

abstract class CompileDoltliteJniTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val amalgamationDir: DirectoryProperty

    @get:InputFile
    abstract val jniSource: RegularFileProperty

    @get:Input
    abstract val compileFlags: ListProperty<String>

    @get:Input
    abstract val javaHome: Property<String>

    @get:Input
    abstract val osClassifier: Property<String>

    /** C compiler command plus retargeting args ("gcc" on a Linux host). */
    @get:Input
    abstract val cc: ListProperty<String>

    /** C++ compiler command plus retargeting args ("g++" on a Linux host). */
    @get:Input
    abstract val cxx: ListProperty<String>

    /** Link-step-only flags (the macOS cross build adds -fuse-ld=lld). */
    @get:Input
    abstract val linkFlags: ListProperty<String>

    /** $JAVA_HOME/include subdirectory that carries jni_md.h. */
    @get:Input
    abstract val jniMdSubdir: Property<String>

    /** Produced library file name (libdoltroomsjni.so / .dylib). */
    @get:Input
    abstract val libFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val src = amalgamationDir.get().asFile
        val out = outputDir.get().asFile.resolve("natives/${osClassifier.get()}")
        out.mkdirs()
        val obj = temporaryDir.resolve("doltlite.o")
        val flags = compileFlags.get()
        val jdkInclude = "${javaHome.get()}/include"
        execOps.exec {
            commandLine(
                cc.get() + listOf("-c", "-fPIC", "-O3") + flags +
                    listOf("-I$src", "-o", obj.absolutePath, "$src/doltlite.c")
            )
        }
        val jniObj = temporaryDir.resolve("doltrooms_jni.o")
        execOps.exec {
            commandLine(
                cxx.get() + listOf("-c", "-fPIC", "-O3", "-fvisibility=hidden") + flags +
                    listOf(
                        "-I$src", "-I$jdkInclude", "-I$jdkInclude/${jniMdSubdir.get()}",
                        "-o", jniObj.absolutePath, jniSource.get().asFile.absolutePath,
                    )
            )
        }
        execOps.exec {
            commandLine(
                cxx.get() + linkFlags.get() + listOf(
                    "-shared",
                    "-o", out.resolve(libFileName.get()).absolutePath,
                    obj.absolutePath, jniObj.absolutePath,
                    "-lpthread", "-ldl", "-lm",
                )
            )
        }
    }
}

// The published jvm jar carries ONLY natives/linux-x64 (README's platform
// table) on every host. On a Linux host that is a plain host build; on
// macOS (the single-host publishing environment) the same .so is
// cross-compiled with the Konan toolchain above. JNI headers for the cross
// compile: jni.h is platform-independent and OpenJDK ships ONE POSIX
// jni_md.h (src/java.base/unix/native/include/jni_md.h, installed as both
// include/linux and include/darwin; only _LP64 affects its typedefs), so
// the host JDK's darwin copy serves the Linux build.
val compileDoltliteJni by tasks.registering(CompileDoltliteJniTask::class) {
    dependsOn(unpackDoltliteAmalgamation)
    amalgamationDir = layout.buildDirectory.dir("doltlite/src")
    jniSource = layout.projectDirectory.file("src/jvmAndroidMain/jni/doltrooms_jni.cpp")
    compileFlags = doltliteCompileFlags
    javaHome = providers.systemProperty("java.home")
    osClassifier = "linux-x64"
    libFileName = "libdoltroomsjni.so"
    outputDir = layout.buildDirectory.dir("nativeLibs/jvm")
    if (hostIsMac) {
        dependsOn("downloadKotlinNativeDistribution")
        cc = linuxCrossCompiler("clang")
        cxx = linuxCrossCompiler("clang++")
        linkFlags = listOf("-fuse-ld=lld")
        jniMdSubdir = "darwin"
    } else {
        cc = listOf("gcc")
        cxx = listOf("g++")
        linkFlags = listOf<String>()
        jniMdSubdir = "linux"
    }
}

// macOS host-test twin: the linux-x64 ELF .so above cannot load into the
// local JVM, so jvmTest and testAndroidHostTest (wired below) get a host
// dylib on the side. Deliberately NOT packaged — the shipped jvm jar stays
// linux-x64-only on every host (README's platform table).
val compileDoltliteJniHost = if (!hostIsMac) null else
    tasks.register<CompileDoltliteJniTask>("compileDoltliteJniHost") {
        dependsOn(unpackDoltliteAmalgamation)
        amalgamationDir = layout.buildDirectory.dir("doltlite/src")
        jniSource = layout.projectDirectory.file("src/jvmAndroidMain/jni/doltrooms_jni.cpp")
        compileFlags = doltliteCompileFlags
        javaHome = providers.systemProperty("java.home")
        osClassifier = providers.systemProperty("os.arch").map {
            if (it in setOf("aarch64", "arm64")) "osx-arm64" else "osx-x64"
        }
        libFileName = "libdoltroomsjni.dylib"
        cc = listOf("cc")
        cxx = listOf("c++")
        linkFlags = listOf<String>()
        jniMdSubdir = "darwin"
        outputDir = layout.buildDirectory.dir("nativeLibs/jvmHost")
    }

// --- Android device ABIs: NDK cross-compile into AAR jniLibs --------------
// The Android artifact ships our OWN NDK build of the amalgamation + glue
// (same pinned version and compile flags as every other platform) instead of
// reusing the JNA-based com.dolthub:doltlite-android AAR — decision recorded
// in ARCHITECTURE.md. NDK r28+ aligns to 16 KB pages by default; the glue
// uses no STL, so libc++ is linked statically and bionic provides pthread.

abstract class CompileDoltliteAndroidJniTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val amalgamationDir: DirectoryProperty

    @get:InputFile
    abstract val jniSource: RegularFileProperty

    @get:Input
    abstract val compileFlags: ListProperty<String>

    @get:Input
    abstract val ndkDir: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    /** ABI name (jniLibs dir) -> NDK clang target triple prefix. */
    @get:Input
    abstract val abiTriples: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val src = amalgamationDir.get().asFile
        // The NDK ships host prebuilts under an OS tag; macOS uses
        // darwin-x86_64 even on Apple Silicon (Rosetta-run toolchain).
        val hostTag =
            if (System.getProperty("os.name").lowercase().startsWith("mac")) "darwin-x86_64"
            else "linux-x86_64"
        val toolchain = "${ndkDir.get()}/toolchains/llvm/prebuilt/$hostTag/bin"
        val api = minSdk.get()
        val flags = compileFlags.get()
        abiTriples.get().forEach { (abi, triple) ->
            val out = outputDir.get().asFile.resolve(abi)
            out.mkdirs()
            val obj = temporaryDir.resolve("doltlite-$abi.o")
            execOps.exec {
                commandLine(
                    listOf("$toolchain/$triple$api-clang", "-c", "-fPIC", "-O3") + flags +
                        listOf("-I$src", "-o", obj.absolutePath, "$src/doltlite.c")
                )
            }
            val jniObj = temporaryDir.resolve("doltrooms_jni-$abi.o")
            execOps.exec {
                commandLine(
                    listOf("$toolchain/$triple$api-clang++", "-c", "-fPIC", "-O3", "-fvisibility=hidden") + flags +
                        listOf("-I$src", "-o", jniObj.absolutePath, jniSource.get().asFile.absolutePath)
                )
            }
            execOps.exec {
                commandLine(
                    "$toolchain/$triple$api-clang++", "-shared", "-static-libstdc++",
                    "-o", out.resolve("libdoltroomsjni.so").absolutePath,
                    obj.absolutePath, jniObj.absolutePath,
                    "-lm", "-ldl",
                )
            }
        }
    }
}

// --- Native targets: static libdoltlite.a for cinterop (PLAN.md Step 6) ---
// Kotlin/Native consumes the amalgamation as headers + a static archive:
// cinterop embeds libdoltlite.a into the klib (-staticLibrary), so linking a
// linuxX64 test binary — or a consumer's binary — needs no external library.
// On a Linux host the archive is built with host gcc, whose output Konan's
// linker consumes, BUT Konan links against its own bundled glibc-2.19
// sysroot, so the object must not reference newer glibc symbols than that:
//   -DSQLITE_DISABLE_LFS avoids _FILE_OFFSET_BITS=64 (self-defined by the
//     amalgamation), whose glibc-2.28+ headers redirect fcntl->fcntl64 —
//     semantically a no-op on x86_64, where off_t is 64-bit regardless;
//   objcopy --redefine-sym maps the glibc-2.38+ C23 strto* redirects (the
//     amalgamation defines _GNU_SOURCE, which forces them regardless of
//     -std) back to the classic symbols — the __isoc23_* variants differ
//     only in accepting C23 0b binary literals, which no DoltLite input
//     relies on.
// On a macOS host (the publishing environment) the same archive is
// cross-compiled by Konan's clang against that very 2.19 sysroot, where the
// host-glibc skew cannot arise: the C23 redirects live in glibc-2.38+
// HEADERS the sysroot predates, so the objcopy remap is unset/skipped there
// (macOS ships no objcopy, and neither does Konan's LLVM package), while
// -DSQLITE_DISABLE_LFS stays for one identical compile command on both
// hosts (a no-op against 2.19 headers, which have no fcntl64 redirect).
// iOS archives cannot be built on a Linux host (no Apple sysroot) — the iOS
// cinterop is headers-only and linking is deferred to a Mac (Step 6 log).

abstract class CompileDoltliteStaticTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val amalgamationDir: DirectoryProperty

    @get:Input
    abstract val compileFlags: ListProperty<String>

    /** C compiler command plus retargeting args ("gcc" on a Linux host). */
    @get:Input
    abstract val cc: ListProperty<String>

    /** Archiver ("ar" on a Linux host, llvm-ar on macOS). */
    @get:Input
    abstract val ar: Property<String>

    /** Unset on macOS: no remap needed there (see the comment block above). */
    @get:Input
    @get:Optional
    abstract val objcopy: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val src = amalgamationDir.get().asFile
        val out = outputDir.get().asFile
        out.mkdirs()
        val obj = temporaryDir.resolve("doltlite.o")
        execOps.exec {
            commandLine(
                // -fPIC: Konan produces position-independent test executables.
                cc.get() + listOf("-c", "-fPIC", "-O3", "-DSQLITE_DISABLE_LFS") +
                    compileFlags.get() +
                    listOf("-I$src", "-o", obj.absolutePath, "$src/doltlite.c")
            )
        }
        if (objcopy.isPresent) {
            execOps.exec {
                commandLine(
                    objcopy.get(),
                    "--redefine-sym", "__isoc23_strtol=strtol",
                    "--redefine-sym", "__isoc23_strtoul=strtoul",
                    "--redefine-sym", "__isoc23_strtoll=strtoll",
                    "--redefine-sym", "__isoc23_strtoull=strtoull",
                    obj.absolutePath,
                )
            }
        }
        val archive = out.resolve("libdoltlite.a")
        archive.delete()
        execOps.exec {
            commandLine(ar.get(), "rcs", archive.absolutePath, obj.absolutePath)
        }
    }
}

val compileDoltliteStaticLinuxX64 by tasks.registering(CompileDoltliteStaticTask::class) {
    dependsOn(unpackDoltliteAmalgamation)
    amalgamationDir = layout.buildDirectory.dir("doltlite/src")
    compileFlags = doltliteCompileFlags
    outputDir = layout.buildDirectory.dir("nativeLibs/linuxX64")
    if (hostIsMac) {
        dependsOn("downloadKotlinNativeDistribution")
        cc = linuxCrossCompiler("clang")
        ar = konanLlvmBin.map { "$it/llvm-ar" }
    } else {
        cc = listOf("gcc")
        ar = "ar"
        objcopy = "objcopy"
    }
}

// --- Apple engine archives (docs/deferred-verification.md, iOS item 3) ---
// The same pinned amalgamation and compile flags as every other platform,
// compiled per Apple target slice with the SDK's clang and archived with
// libtool. Apple libSystem has none of the glibc symbol-skew workarounds
// the linuxX64 task needs (no LFS define, no objcopy). Deployment targets
// sit at or below Kotlin/Native's minimums so the archives link into any
// Konan-produced binary without version warnings.
abstract class CompileDoltliteAppleStaticTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val amalgamationDir: DirectoryProperty

    @get:Input
    abstract val compileFlags: ListProperty<String>

    /** Apple SDK name for xcrun (iphoneos, iphonesimulator). */
    @get:Input
    abstract val sdkName: Property<String>

    /** clang -target triple, e.g. arm64-apple-ios12.0 */
    @get:Input
    abstract val targetTriple: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val src = amalgamationDir.get().asFile
        val out = outputDir.get().asFile
        out.mkdirs()
        val obj = temporaryDir.resolve("doltlite.o")
        execOps.exec {
            commandLine(
                listOf(
                    "xcrun", "--sdk", sdkName.get(), "clang",
                    "-c", "-O3", "-target", targetTriple.get(),
                ) + compileFlags.get() +
                    listOf("-I$src", "-o", obj.absolutePath, "$src/doltlite.c")
            )
        }
        val archive = out.resolve("libdoltlite.a")
        archive.delete()
        execOps.exec {
            commandLine(
                "xcrun", "--sdk", sdkName.get(), "libtool", "-static",
                "-o", archive.absolutePath, obj.absolutePath,
            )
        }
    }
}

// Registered on macOS hosts only: on Linux KGP disables Apple compilations
// that carry cinterops (docs/deferred-verification.md), and the xcrun tasks
// must not be reachable from the CI task graph.
if (hostIsMac) {
    tasks.register<CompileDoltliteAppleStaticTask>("compileDoltliteStaticIosArm64") {
        dependsOn(unpackDoltliteAmalgamation)
        amalgamationDir = layout.buildDirectory.dir("doltlite/src")
        compileFlags = doltliteCompileFlags
        sdkName = "iphoneos"
        targetTriple = "arm64-apple-ios12.0"
        outputDir = layout.buildDirectory.dir("nativeLibs/iosArm64")
    }
    tasks.register<CompileDoltliteAppleStaticTask>("compileDoltliteStaticIosSimulatorArm64") {
        dependsOn(unpackDoltliteAmalgamation)
        amalgamationDir = layout.buildDirectory.dir("doltlite/src")
        compileFlags = doltliteCompileFlags
        sdkName = "iphonesimulator"
        // arm64 simulator slices exist from iOS 14 onward.
        targetTriple = "arm64-apple-ios14.0-simulator"
        outputDir = layout.buildDirectory.dir("nativeLibs/iosSimulatorArm64")
    }
}

// Resolved the same way AGP finds the SDK: local.properties sdk.dir, then
// the ANDROID_HOME environment (CI).
val localPropertiesFile = rootProject.file("local.properties")
val androidNdkDir: Provider<String> = providers.provider {
    val sdkDir = localPropertiesFile
        .takeIf { it.exists() }
        ?.let { file ->
            Properties()
                .apply { file.inputStream().use { load(it) } }
                .getProperty("sdk.dir")
        }
        ?: System.getenv("ANDROID_HOME")
        ?: error("Android SDK not found: set sdk.dir in local.properties or ANDROID_HOME")
    "$sdkDir/ndk/${libs.versions.android.ndk.get()}"
}

val compileDoltliteAndroidJni by tasks.registering(CompileDoltliteAndroidJniTask::class) {
    dependsOn(unpackDoltliteAmalgamation)
    amalgamationDir = layout.buildDirectory.dir("doltlite/src")
    jniSource = layout.projectDirectory.file("src/jvmAndroidMain/jni/doltrooms_jni.cpp")
    compileFlags = doltliteCompileFlags
    ndkDir = androidNdkDir
    minSdk = libs.versions.android.minSdk.get().toInt()
    abiTriples = mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "x86_64" to "x86_64-linux-android",
    )
    outputDir = layout.buildDirectory.dir("nativeLibs/android")
}

kotlin {
    // The custom jvmAndroidMain dependsOn edges below disable Kotlin's
    // default source-set hierarchy, so re-apply it explicitly: it provides
    // nativeMain/iosMain, which hold the Step 6 cinterop driver (stubs today).
    applyDefaultHierarchyTemplate()

    // The driver classes are expect/actual (the bundled-driver template);
    // acknowledge the Beta status like androidx does.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Published library: every public declaration must state its visibility
    // and type explicitly (kotlin-audit-baseline skill; ARCHITECTURE.md D11).
    explicitApi()

    jvm()
    androidLibrary {
        namespace = "dev.seri.doltrooms"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    // All native targets share one cinterop over doltlite.h; with
    // kotlin.mpp.enableCInteropCommonization=true (gradle.properties) the
    // bindings commonize into nativeMain, where the actuals live. Each
    // target embeds its static engine archive (from nativeLibs/<dir>) so
    // binaries link self-contained. On a Linux host the iOS archives
    // cannot be built (no Apple sysroot) and the iOS cinterops stay
    // headers-only — KGP disables those compilations there anyway
    // (docs/deferred-verification.md).
    val doltliteCinterop: org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation.(archiveDir: String?) -> Unit =
        { archiveDir ->
            cinterops.create("doltlite") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/doltlite.def"))
                includeDirs(layout.buildDirectory.dir("doltlite/src"))
                if (archiveDir != null) {
                    extraOpts(
                        "-staticLibrary", "libdoltlite.a",
                        "-libraryPath",
                        layout.buildDirectory.dir("nativeLibs/$archiveDir").get().asFile.absolutePath,
                    )
                }
            }
        }
    iosArm64 { compilations.getByName("main") { doltliteCinterop(if (hostIsMac) "iosArm64" else null) } }
    iosSimulatorArm64 { compilations.getByName("main") { doltliteCinterop(if (hostIsMac) "iosSimulatorArm64" else null) } }
    linuxX64 { compilations.getByName("main") { doltliteCinterop("linuxX64") } }

    sourceSets {
        commonMain.dependencies {
            // The driver contract we implement (ARCHITECTURE.md D1); api because
            // DoltLiteDriver's public surface exposes the androidx.sqlite types.
            api(libs.androidx.sqlite)
            // Step 7's typed dolt_* helpers (DoltDatabase) take a RoomDatabase
            // and run over useWriterConnection — Room types are in the public
            // surface, so the runtime is an api dependency (the revisit the
            // Step 4 test-only decision anticipated; ARCHITECTURE.md D10).
            // The Room compiler still serves tests only: no annotations, no
            // generated Room code in the shipped artifact.
            api(libs.room3.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // runTest + Dispatchers.Default for the not-thread-affine
            // conformance case (room3 skill, testing reference).
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            // The differential-conformance oracle: the same commonTest suite
            // runs against BundledSQLiteDriver and DoltLiteDriver (PLAN.md
            // Step 3; room3 skill, testing reference).
            implementation(libs.androidx.sqlite.bundled)
        }

        getByName("androidDeviceTest").dependencies {
            // The AndroidJUnitRunner the instrumentation declares must be
            // packaged into the device-test APK; without it the run dies at
            // instrumentation init with ClassNotFoundException (first real
            // device run, 2026-07-21).
            implementation(libs.androidx.test.runner)
        }

        // linuxX64Test deliberately does NOT get sqlite-bundled: on native
        // both engines would be STATICALLY linked into the one test binary,
        // and libdoltlite.a and androidx's libandroidXBundledSqlite.a export
        // the same unprefixed sqlite3_* symbols — the linker silently
        // resolves BOTH drivers to whichever archive comes first (observed:
        // DoltLite locally, androidx's sqlite on CI, where every dolt_* test
        // then failed with "no such function"). The differential oracle
        // lives on jvmTest, where the engines are separate dynamic
        // libraries; the dolt suite pins engine identity here.

        // Shared JNI declarations for desktop JVM + Android, exactly like
        // androidx sqlite-bundled's jvmAndroidMain (kmp-native-interop skill).
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependsOn(jvmAndroidMain)
            resources.srcDir(compileDoltliteJni.map { it.outputDir })
        }
        androidMain {
            dependsOn(jvmAndroidMain)
        }
    }
}

// The Room fixture database lives in commonTest, so every target's TEST
// compilation needs the Room compiler to generate the actual
// RoomConformanceDbConstructor + database impl (room3 skill, kmp-setup
// reference: per-target ksp configurations). Main compilations get none —
// the shipped library has no Room code.
dependencies {
    add("kspJvmTest", libs.room3.compiler)
    add("kspAndroidHostTest", libs.room3.compiler)
    add("kspAndroidDeviceTest", libs.room3.compiler)
    add("kspLinuxX64Test", libs.room3.compiler)
    add("kspIosArm64Test", libs.room3.compiler)
    add("kspIosSimulatorArm64Test", libs.room3.compiler)
}

// Package the NDK-built per-ABI libs into the AAR's jniLibs via the variant
// API — the generated-directory equivalent of androidx sqlite-bundled's
// addNativeLibrariesToJniLibs (kmp-native-interop skill).
androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            compileDoltliteAndroidJni,
            CompileDoltliteAndroidJniTask::outputDir,
        )
    }
}

room3 {
    // Required whenever the Room Gradle plugin is applied; exportSchema
    // output for the commonTest fixture database (room3 skill, kmp-setup
    // reference).
    schemaDirectory("$projectDir/schemas")
}

// The cinterop tasks consume build/-generated inputs (headers for every
// target, plus the linuxX64 static archive), so the producing tasks must be
// explicit dependencies — includeDirs/extraOpts carry plain paths only.
tasks.matching { it.name.startsWith("cinteropDoltlite") }.configureEach {
    dependsOn(unpackDoltliteAmalgamation)
}
tasks.matching { it.name == "cinteropDoltliteLinuxX64" }.configureEach {
    dependsOn(compileDoltliteStaticLinuxX64)
    // -staticLibrary embeds the archive into the klib, but cinterop does not
    // track it as an input — declare it so archive rebuilds re-run cinterop.
    inputs.files(compileDoltliteStaticLinuxX64.map { it.outputs.files })
}
if (hostIsMac) {
    // Same wiring for the Apple slices; the archive tasks exist only on
    // macOS hosts (see their registration above).
    listOf("IosArm64", "IosSimulatorArm64").forEach { slice ->
        val archiveTask = tasks.named("compileDoltliteStatic$slice")
        tasks.matching { it.name == "cinteropDoltlite$slice" }.configureEach {
            dependsOn(archiveTask)
            inputs.files(archiveTask.map { it.outputs.files })
        }
    }
}

// --- iOS physical-device test runner (docs/deferred-verification.md) ------
// KGP runs Kotlin/Native test binaries on simulators only; no device-run
// task exists for iosArm64. This one wraps the linked test.kexe in a
// minimal .app bundle, borrows the bundle id + provisioning profile from an
// already-built-and-signed host app (a free personal team can mint profiles
// only through Xcode; the samples/codelab Fruitties device build provides
// one), signs the bundle, installs and launches it on a connected device
// via devicectl, and fails unless the captured console output carries a
// green Kotlin/Native test summary. Because the bundle id is borrowed,
// installing the runner REPLACES the host app (and its data) on the device
// — rebuild/reinstall the sample afterwards if you want it back.
//
// Usage (macOS host; device paired, Developer Mode on, profile trusted):
//   ./gradlew :library:iosArm64DeviceTest \
//     -Pdoltrooms.iosDeviceTest.udid=<devicectl UDID> \
//     -Pdoltrooms.iosDeviceTest.hostApp=<path to a signed Fruitties.app>
abstract class RunIosDeviceTestsTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {

    /** The linked iosArm64 Kotlin/Native test executable (test.kexe). */
    @get:InputFile
    abstract val testExecutable: RegularFileProperty

    /** Built + signed .app whose bundle id and profile the runner borrows. */
    @get:InputDirectory
    abstract val hostApp: DirectoryProperty

    /** devicectl device identifier (UDID from `devicectl list devices`). */
    @get:Input
    abstract val deviceUdid: Property<String>

    /** codesign identity; any unique substring ("Apple Development") works. */
    @get:Input
    abstract val signingIdentity: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private fun run(vararg cmd: String, ignoreExit: Boolean = false): String {
        val stdout = ByteArrayOutputStream()
        execOps.exec {
            commandLine(*cmd)
            standardOutput = stdout
            isIgnoreExitValue = ignoreExit
        }
        return stdout.toString()
    }

    @TaskAction
    fun runTests() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val host = hostApp.get().asFile
        val profile = host.resolve("embedded.mobileprovision")
        require(profile.exists()) { "hostApp carries no embedded.mobileprovision: $host" }
        val bundleId = run(
            "/usr/libexec/PlistBuddy", "-c", "Print :CFBundleIdentifier",
            host.resolve("Info.plist").absolutePath,
        ).trim()

        // Assemble the bundle around the bare test executable. The binary is
        // a plain console main() (no UIApplication): FrontBoard treats the
        // quick exit as a crash-at-checkin, but the process runs to
        // completion first and its stdout reaches the attached console.
        val app = out.resolve("DoltroomsTests.app").apply { mkdirs() }
        val exe = app.resolve("DoltroomsTests")
        testExecutable.get().asFile.copyTo(exe, overwrite = true)
        exe.setExecutable(true)
        profile.copyTo(app.resolve("embedded.mobileprovision"), overwrite = true)
        app.resolve("Info.plist").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0"><dict>
                <key>CFBundleExecutable</key><string>DoltroomsTests</string>
                <key>CFBundleIdentifier</key><string>$bundleId</string>
                <key>CFBundleName</key><string>DoltroomsTests</string>
                <key>CFBundlePackageType</key><string>APPL</string>
                <key>CFBundleShortVersionString</key><string>1.0</string>
                <key>CFBundleVersion</key><string>1</string>
                <key>CFBundleSupportedPlatforms</key><array><string>iPhoneOS</string></array>
                <key>LSRequiresIPhoneOS</key><true/>
                <key>MinimumOSVersion</key><string>12.0</string>
                <key>UIDeviceFamily</key><array><integer>1</integer><integer>2</integer></array>
                <key>UILaunchScreen</key><dict/>
            </dict></plist>
            """.trimIndent()
        )

        // Entitlements (application-identifier, team, get-task-allow) come
        // from the borrowed profile itself: decode its CMS envelope, then
        // lift the Entitlements dict into a standalone plist for codesign.
        val profilePlist = out.resolve("profile.plist")
        profilePlist.writeText(run("security", "cms", "-D", "-i", profile.absolutePath))
        val entitlements = out.resolve("entitlements.plist")
        entitlements.writeText(
            run("/usr/libexec/PlistBuddy", "-x", "-c", "Print :Entitlements", profilePlist.absolutePath)
        )
        run(
            "codesign", "--force", "--sign", signingIdentity.get(),
            "--entitlements", entitlements.absolutePath, app.absolutePath,
        )

        run(
            "xcrun", "devicectl", "device", "install", "app",
            "--device", deviceUdid.get(), app.absolutePath,
        )
        // --console attaches and streams the process's stdio until exit.
        val console = run(
            "xcrun", "devicectl", "device", "process", "launch",
            "--console", "--terminate-existing",
            "--device", deviceUdid.get(), bundleId,
            ignoreExit = true,
        )
        val log = out.resolve("console.log").apply { writeText(console) }
        logger.lifecycle(console)

        // Kotlin/Native's default test listener prints a gtest-style summary:
        //   [==========] 52 tests from 8 test cases ran. (…)
        //   [  PASSED  ] 52 tests.
        val ran = Regex("""\[==========] (\d+) tests from .* ran""").find(console)
        val passed = Regex("""\[  PASSED {2}] (\d+) tests?""").find(console)
        when {
            ran == null -> error(
                "No Kotlin/Native test summary in the device console output " +
                    "(launch failure or watchdog kill?) — see $log"
            )
            console.contains("[  FAILED  ]") || passed == null ->
                error("Device test run FAILED — see $log")
            else -> logger.lifecycle(
                "iosArm64DeviceTest: ${passed.groupValues[1]}/${ran.groupValues[1]} " +
                    "tests passed on device ${deviceUdid.get()}"
            )
        }
    }
}

if (hostIsMac) {
    val linkIosArm64Test =
        tasks.named("linkDebugTestIosArm64", org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink::class)
    tasks.register<RunIosDeviceTestsTask>("iosArm64DeviceTest") {
        group = "verification"
        description =
            "Runs the iosArm64 Kotlin/Native test binary on a connected physical device via devicectl."
        dependsOn(linkIosArm64Test)
        testExecutable = layout.file(linkIosArm64Test.flatMap { it.outputFile })
        hostApp = layout.dir(
            providers.gradleProperty("doltrooms.iosDeviceTest.hostApp").map { File(it) }
        )
        deviceUdid = providers.gradleProperty("doltrooms.iosDeviceTest.udid")
        signingIdentity =
            providers.gradleProperty("doltrooms.iosDeviceTest.identity").orElse("Apple Development")
        outputDir = layout.buildDirectory.dir("iosDeviceTest")
    }
}

// --- doltlite-remotesrv test fixture (Step 8) -----------------------------
// The jvm RemoteServerSyncTest spawns a real doltlite-remotesrv (from the
// pinned release's doltlite-tools zip) to prove http(s) sync. Wired only on
// linux-x64 hosts — the only platform whose zip checksum is verified here;
// elsewhere the test skips (docs/deferred-verification.md).
val hostIsLinuxX64 =
    System.getProperty("os.name").lowercase().contains("linux") &&
        System.getProperty("os.arch") in setOf("amd64", "x86_64")

val doltliteToolsUrl =
    "https://github.com/dolthub/doltlite/releases/download/v$doltliteVersion/doltlite-tools-linux-x64-$doltliteVersion.zip"
// SHA-256 of doltlite-tools-linux-x64-0.11.33.zip, recorded 2026-07-18.
val doltliteToolsSha256 = "6d9b2353f051ce79d3637d57facae293cacb320cfb5b3eebe896c18af1338932"

val downloadDoltliteTools by tasks.registering {
    // Local copy: an onlyIf referencing the script-level val would capture
    // the script object, which the configuration cache cannot serialize.
    val linuxX64Host = hostIsLinuxX64
    onlyIf { linuxX64Host }
    val url = doltliteToolsUrl
    val sha256 = doltliteToolsSha256
    val zipFile = layout.buildDirectory.file("doltlite/doltlite-tools-linux-x64-$doltliteVersion.zip")
    inputs.property("url", url)
    inputs.property("sha256", sha256)
    outputs.file(zipFile)
    doLast {
        val target = zipFile.get().asFile
        fun fileSha256() = MessageDigest.getInstance("SHA-256")
            .digest(target.readBytes())
            .joinToString("") { "%02x".format(it) }
        // Same pre-seeded-zip acceptance as downloadDoltliteAmalgamation
        // (kept inline: a shared script-level helper would capture the script
        // object in the task action, breaking the configuration cache).
        if (target.exists() && fileSha256() == sha256) return@doLast
        target.parentFile.mkdirs()
        URI(url).toURL().openStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val actual = fileSha256()
        if (actual != sha256) {
            target.delete()
            error("SHA-256 mismatch for $url: expected $sha256, got $actual")
        }
    }
}

val unpackDoltliteTools by tasks.registering(Copy::class) {
    val linuxX64Host = hostIsLinuxX64
    onlyIf { linuxX64Host }
    from(zipTree(downloadDoltliteTools.map { it.outputs.files.singleFile })) {
        // Flatten doltlite-tools-linux-x64-<v>/doltlite-remotesrv -> doltlite-remotesrv
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("doltlite/tools"))
    doLast {
        destinationDir.listFiles()?.forEach { it.setExecutable(true) }
    }
}

tasks.withType<Test>()
    .matching { it.name == "jvmTest" }
    .configureEach {
        if (hostIsLinuxX64) {
            dependsOn(unpackDoltliteTools)
            systemProperty(
                "dev.seri.doltrooms.remotesrv",
                layout.buildDirectory.file("doltlite/tools/doltlite-remotesrv")
                    .get().asFile.absolutePath,
            )
        }
        // On macOS the classpath resource is a Linux ELF .so the local JVM
        // cannot load; point the loader's explicit-path override at the
        // host-test twin's dylib instead.
        if (compileDoltliteJniHost != null) {
            dependsOn(compileDoltliteJniHost)
            val hostLibPath = compileDoltliteJniHost.map {
                it.outputDir.get().asFile
                    .resolve("natives/${it.osClassifier.get()}/${it.libFileName.get()}")
                    .absolutePath
            }
            doFirst { systemProperty("dev.seri.doltrooms.lib.path", hostLibPath.get()) }
        }
    }

// Android host tests run on the host JVM, where androidMain's
// System.loadLibrary("doltroomsjni") searches java.library.path — reuse the
// desktop native lib instead of packaging a host lib into the AAR
// (kmp-native-interop skill: jniLibs is a device mechanism). On macOS the
// desktop .so is a Linux ELF cross build, so the host-test twin's dylib
// serves instead.
tasks.withType<Test>()
    .matching { it.name == "testAndroidHostTest" }
    .configureEach {
        val nativesTask = compileDoltliteJniHost ?: compileDoltliteJni
        dependsOn(nativesTask)
        val hostNativesDir = nativesTask.map {
            it.outputDir.get().dir("natives/${it.osClassifier.get()}").asFile.absolutePath
        }
        doFirst {
            // Prepend rather than replace: AGP already sets a path that ends
            // with the (unused) src/androidHostTest/jniLibs convention dir.
            val existing = systemProperties["java.library.path"]?.toString()
            systemProperty(
                "java.library.path",
                listOfNotNull(hostNativesDir.get(), existing).joinToString(File.pathSeparator),
            )
        }
    }

mavenPublishing {
    // Credentials and the GPG key arrive via environment on the publishing
    // host (ORG_GRADLE_PROJECT_mavenCentralUsername/-Password,
    // ORG_GRADLE_PROJECT_signingInMemoryKey/-KeyId/-KeyPassword — vanniktech
    // 0.36.0 central docs); without a key the signing tasks are skipped, so
    // publishToMavenLocal works unsigned in dev environments. NOTE: actual
    // Maven Central publication must run from a macOS host — the iOS klibs
    // carry cinterops, and all artifacts must publish from a single host
    // (docs/deferred-verification.md).
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "doltrooms", version.toString())

    // The -javadoc jar Maven Central requires, generated by Dokka. The
    // AGP KMP library plugin needs no androidVariantsToPublish parameter
    // (vanniktech 0.36.0 docs, Kotlin Multiplatform Library section).
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )

    pom {
        name = "doltrooms"
        description = "A Room 3 (androidx.room3) SQLiteDriver backed by DoltLite, giving KMP apps a local, version-controlled database."
        inceptionYear = "2026"
        url = "https://github.com/Serisium/Doltrooms/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "Serisium"
                name = "Seri Greenwood"
                url = "https://github.com/Serisium/"
            }
        }
        scm {
            url = "https://github.com/Serisium/Doltrooms/"
            connection = "scm:git:git://github.com/Serisium/Doltrooms.git"
            developerConnection = "scm:git:ssh://git@github.com/Serisium/Doltrooms.git"
        }
    }
}

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
        target.parentFile.mkdirs()
        URI(url).toURL().openStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(target.readBytes())
            .joinToString("") { "%02x".format(it) }
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
                listOf("gcc", "-c", "-fPIC", "-O3") + flags +
                    listOf("-I$src", "-o", obj.absolutePath, "$src/doltlite.c")
            )
        }
        val jniObj = temporaryDir.resolve("doltrooms_jni.o")
        execOps.exec {
            commandLine(
                listOf("g++", "-c", "-fPIC", "-O3", "-fvisibility=hidden") + flags +
                    listOf(
                        "-I$src", "-I$jdkInclude", "-I$jdkInclude/linux",
                        "-o", jniObj.absolutePath, jniSource.get().asFile.absolutePath,
                    )
            )
        }
        execOps.exec {
            commandLine(
                "g++", "-shared",
                "-o", out.resolve("libdoltroomsjni.so").absolutePath,
                obj.absolutePath, jniObj.absolutePath,
                "-lpthread", "-ldl", "-lm",
            )
        }
    }
}

val compileDoltliteJni by tasks.registering(CompileDoltliteJniTask::class) {
    dependsOn(unpackDoltliteAmalgamation)
    amalgamationDir = layout.buildDirectory.dir("doltlite/src")
    jniSource = layout.projectDirectory.file("src/jvmAndroidMain/jni/doltrooms_jni.cpp")
    compileFlags = doltliteCompileFlags
    javaHome = providers.systemProperty("java.home")
    osClassifier = "linux-x64" // host build; cross-compilation lands with later PLAN.md steps
    outputDir = layout.buildDirectory.dir("nativeLibs/jvm")
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
        val toolchain = "${ndkDir.get()}/toolchains/llvm/prebuilt/linux-x86_64/bin"
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
// The archive is built with host gcc, whose output Konan's linker consumes,
// BUT Konan links against its own bundled glibc-2.19 sysroot, so the object
// must not reference newer glibc symbols than that:
//   -DSQLITE_DISABLE_LFS avoids _FILE_OFFSET_BITS=64 (self-defined by the
//     amalgamation), whose glibc-2.28+ headers redirect fcntl->fcntl64 —
//     semantically a no-op on x86_64, where off_t is 64-bit regardless;
//   objcopy --redefine-sym maps the glibc-2.38+ C23 strto* redirects (the
//     amalgamation defines _GNU_SOURCE, which forces them regardless of
//     -std) back to the classic symbols — the __isoc23_* variants differ
//     only in accepting C23 0b binary literals, which no DoltLite input
//     relies on.
// iOS archives cannot be built on a Linux host (no Apple sysroot) — the iOS
// cinterop is headers-only and linking is deferred to a Mac (Step 6 log).

abstract class CompileDoltliteStaticTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val amalgamationDir: DirectoryProperty

    @get:Input
    abstract val compileFlags: ListProperty<String>

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
                listOf("gcc", "-c", "-fPIC", "-O3", "-DSQLITE_DISABLE_LFS") +
                    compileFlags.get() +
                    listOf("-I$src", "-o", obj.absolutePath, "$src/doltlite.c")
            )
        }
        execOps.exec {
            commandLine(
                "objcopy",
                "--redefine-sym", "__isoc23_strtol=strtol",
                "--redefine-sym", "__isoc23_strtoul=strtoul",
                "--redefine-sym", "__isoc23_strtoll=strtoll",
                "--redefine-sym", "__isoc23_strtoull=strtoull",
                obj.absolutePath,
            )
        }
        val archive = out.resolve("libdoltlite.a")
        archive.delete()
        execOps.exec {
            commandLine("ar", "rcs", archive.absolutePath, obj.absolutePath)
        }
    }
}

val compileDoltliteStaticLinuxX64 by tasks.registering(CompileDoltliteStaticTask::class) {
    dependsOn(unpackDoltliteAmalgamation)
    amalgamationDir = layout.buildDirectory.dir("doltlite/src")
    compileFlags = doltliteCompileFlags
    outputDir = layout.buildDirectory.dir("nativeLibs/linuxX64")
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
    // bindings commonize into nativeMain, where the actuals live. linuxX64
    // additionally embeds the static archive so binaries link
    // self-contained; iOS is headers-only on a Linux host (see the static
    // task's comment).
    val doltliteCinterop: org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation.(embedStaticLib: Boolean) -> Unit =
        { embedStaticLib ->
            cinterops.create("doltlite") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/doltlite.def"))
                includeDirs(layout.buildDirectory.dir("doltlite/src"))
                if (embedStaticLib) {
                    extraOpts(
                        "-staticLibrary", "libdoltlite.a",
                        "-libraryPath",
                        layout.buildDirectory.dir("nativeLibs/linuxX64").get().asFile.absolutePath,
                    )
                }
            }
        }
    iosArm64 { compilations.getByName("main") { doltliteCinterop(false) } }
    iosSimulatorArm64 { compilations.getByName("main") { doltliteCinterop(false) } }
    linuxX64 { compilations.getByName("main") { doltliteCinterop(true) } }

    sourceSets {
        commonMain.dependencies {
            // The driver contract we implement (ARCHITECTURE.md D1); api because
            // DoltLiteDriver's public surface exposes the androidx.sqlite types.
            api(libs.androidx.sqlite)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // runTest + Dispatchers.Default for the not-thread-affine
            // conformance case (room3 skill, testing reference).
            implementation(libs.kotlinx.coroutines.test)
            // Room 3 stays OUT of commonMain: this library is a driver Room
            // consumes (D1), not a Room extension — the runtime is only a
            // dependency of the Step 4 differential Room suite.
            implementation(libs.room3.runtime)
        }

        jvmTest.dependencies {
            // The differential-conformance oracle: the same commonTest suite
            // runs against BundledSQLiteDriver and DoltLiteDriver (PLAN.md
            // Step 3; room3 skill, testing reference).
            implementation(libs.androidx.sqlite.bundled)
        }

        linuxX64Test.dependencies {
            // The oracle also runs on linuxX64 — sqlite-bundled is KMP, so
            // the differential pattern carries to the native rung (PLAN.md
            // Step 6).
            implementation(libs.androidx.sqlite.bundled)
        }

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

// Android host tests run on the host JVM, where androidMain's
// System.loadLibrary("doltroomsjni") searches java.library.path — reuse the
// desktop .so from compileDoltliteJni instead of packaging a host lib into
// the AAR (kmp-native-interop skill: jniLibs is a device mechanism).
tasks.withType<Test>()
    .matching { it.name == "testAndroidHostTest" }
    .configureEach {
        dependsOn(compileDoltliteJni)
        val hostNativesDir = compileDoltliteJni.map {
            it.outputDir.get().dir("natives/linux-x64").asFile.absolutePath
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
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "doltrooms", version.toString())

    pom {
        name = "doltrooms"
        description = "A Room 3 (androidx.room3) SQLiteDriver backed by DoltLite, giving KMP apps a local, version-controlled database."
        inceptionYear = "2026"
        url = "https://github.com/Serisium/Doltrooms/"
        licenses {
            license {
                name = "XXX"
                url = "YYY"
                distribution = "ZZZ"
            }
        }
        developers {
            developer {
                id = "XXX"
                name = "YYY"
                url = "ZZZ"
            }
        }
        scm {
            url = "XXX"
            connection = "YYY"
            developerConnection = "ZZZ"
        }
    }
}

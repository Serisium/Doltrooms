import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
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

kotlin {
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
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // The driver contract we implement (ARCHITECTURE.md D1); api because
            // DoltLiteDriver's public surface exposes the androidx.sqlite types.
            api(libs.androidx.sqlite)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
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

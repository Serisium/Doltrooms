import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
    // Room 3 is a TEST-ONLY consumer here: :dolt-write ships the DoltDatabase
    // facade (plain SQL through Room's raw-connection API); the Room compiler
    // serves only the RoomConformanceDb fixture the verb suites run against.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.detekt)
}

group = "dev.seri.doltrooms"
version = "0.1.0-SNAPSHOT"

// --- Where the version-control WRITE surface lives ------------------------
// The git verbs: the `DoltDatabase` facade plus its sealed result types,
// moved from the pre-split `:library` AS AN INTERIM WHOLE
// (docs/design/module-architecture.md — the rev-4 facade rework that splits
// out read/remotes members is a separate future task). Runtime deps are the
// dolt kernel and androidx.sqlite ONLY — no compile-time dependency on
// :driver (the DAG's deliberate property: the facade issues SQL through
// Room's connection APIs; the engine contract is the dolt_* SQL surface).
//
// The verb TEST suites, however, need a concrete engine and the shared Room
// fixture: they run over :driver's DoltLiteDriver and a duplicated copy of
// the RoomConformanceDb fixture + nativeTempDbPath helpers. That test-only
// coupling to :driver — and the fixture duplication — is the tension the
// module-architecture doc's future `:testing` module resolves; noted here,
// not redesigned.

kotlin {
    compilerOptions {
        // RoomConformanceDbConstructor (the duplicated test fixture) is an
        // expect object whose actual KSP generates per target.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Published library: explicit visibility + type on every public
    // declaration (ARCHITECTURE.md D11).
    explicitApi()

    // Per-module ABI gate (D11): checkLegacyAbi fails the build when the
    // public binary API drifts from the committed api/ dump; regenerate
    // deliberately with updateLegacyAbi.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
        legacyDump {
            referenceDumpDir.set(layout.projectDirectory.dir("api"))
        }
    }

    jvm()
    androidLibrary {
        namespace = "dev.seri.doltrooms.dolt.write"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava()
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    // The verb suites exercise every platform :driver ships; the native test
    // binaries link :driver's klib, which embeds the DoltLite static engine
    // via cinterop (-staticLibrary), so no cinterop is needed here.
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // The shared vcs kernel (Room runtime + the shipped row/anchor
            // types). :dolt-write -> :dolt-core, per the DAG.
            api(project(":dolt-core"))
            // DoltDatabase.clone(driver: SQLiteDriver, ...) exposes androidx
            // .sqlite types on its public surface, so api (also arrives via
            // Room through :dolt-core, but declared explicitly).
            api(libs.androidx.sqlite)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // The verb suites drive DoltDatabase over a real Room database on
            // the concrete DoltLiteDriver engine — test-only coupling to
            // :driver (see the header note).
            implementation(project(":driver"))
            implementation(libs.room3.runtime)
        }

        jvmTest.dependencies {
            // BundledDoltDatabaseTest asserts the facade throws cleanly on a
            // non-Dolt engine (stock SQLite); the differential-oracle twin of
            // the driver conformance suite.
            implementation(libs.androidx.sqlite.bundled)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
}

// Gate `check` on the ABI dump and on detekt for the source set that holds
// code (the facade lives in commonMain; the per-target Main sets are empty).
tasks.named("check") {
    dependsOn(
        "checkLegacyAbi",
        "detektCommonMainSourceSet",
    )
}

dependencies {
    add("kspJvmTest", libs.room3.compiler)
    add("kspAndroidHostTest", libs.room3.compiler)
    add("kspAndroidDeviceTest", libs.room3.compiler)
    add("kspLinuxX64Test", libs.room3.compiler)
    add("kspIosArm64Test", libs.room3.compiler)
    add("kspIosSimulatorArm64Test", libs.room3.compiler)
    detektPlugins(libs.detekt.rules.libraries)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

// --- Native engine for the JVM-host verb suites ---------------------------
// The verb tests run DoltLiteDriver, whose JNI engine must be loadable on the
// test host. :driver's jvm artifact (on the test classpath via the project
// dependency above) packages ONLY the linux-x64 .so, which loads on a Linux
// host directly. On macOS that ELF cannot load into the local JVM, so point
// the loader's explicit-path override at :driver's host-dylib twin
// (compileDoltliteJniHost) — the same wiring :driver's own jvmTest uses.
val hostIsMac = System.getProperty("os.name").lowercase().startsWith("mac")
val osArch = System.getProperty("os.arch").lowercase()
val hostJniClassifier = if (osArch in setOf("aarch64", "arm64")) "osx-arm64" else "osx-x64"
val driverBuildDir = project(":driver").layout.buildDirectory

if (hostIsMac) {
    val hostDylib: Provider<File> = driverBuildDir.map {
        it.dir("nativeLibs/jvmHost/natives/$hostJniClassifier")
            .file("libdoltroomsjni.dylib").asFile
    }
    tasks.withType<Test>()
        .matching { it.name == "jvmTest" }
        .configureEach {
            dependsOn(":driver:compileDoltliteJniHost")
            doFirst { systemProperty("dev.seri.doltrooms.lib.path", hostDylib.get().absolutePath) }
        }
    // Android host tests: androidMain's System.loadLibrary searches
    // java.library.path — point it at :driver's host-dylib directory.
    tasks.withType<Test>()
        .matching { it.name == "testAndroidHostTest" }
        .configureEach {
            dependsOn(":driver:compileDoltliteJniHost")
            doFirst {
                val dir = hostDylib.get().parentFile.absolutePath
                val existing = systemProperties["java.library.path"]?.toString()
                systemProperty(
                    "java.library.path",
                    listOfNotNull(dir, existing).joinToString(File.pathSeparator),
                )
            }
        }
}

// --- doltlite-remotesrv test fixture (Step 8) -----------------------------
// RemoteServerSyncTest spawns a real doltlite-remotesrv (from the pinned
// release's doltlite-tools zip) to prove http sync. Wired only on linux-x64
// hosts — the only platform whose zip checksum is verified here; elsewhere
// the test skips (docs/deferred-verification.md).
val doltliteVersion: String = libs.versions.doltlite.get()
val hostIsLinuxX64 =
    System.getProperty("os.name").lowercase().contains("linux") &&
        System.getProperty("os.arch") in setOf("amd64", "x86_64")

val doltliteToolsUrl =
    "https://github.com/dolthub/doltlite/releases/download/v$doltliteVersion/doltlite-tools-linux-x64-$doltliteVersion.zip"
// SHA-256 of doltlite-tools-linux-x64-0.11.33.zip, recorded 2026-07-18.
val doltliteToolsSha256 = "6d9b2353f051ce79d3637d57facae293cacb320cfb5b3eebe896c18af1338932"

val downloadDoltliteTools by tasks.registering {
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
    }

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "doltrooms-dolt-write", version.toString())
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )
    pom {
        name = "doltrooms-dolt-write"
        description = "The doltrooms version-control WRITE surface: the DoltDatabase git-verb facade (commit, branch, merge, diff) over a DoltLite-backed Room database."
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

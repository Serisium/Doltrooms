import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
}

group = "dev.seri.doltrooms"
version = "0.1.0-SNAPSHOT"

// --- :dolt-core — the shared kernel of the version-control surface --------
// SCAFFOLD. Populated when the rev-4 contract implementation lands
// (docs/design/module-architecture.md; the prototypes under prototypes/ are
// the seeds). Intended contents: the DoltRef sealed hierarchy, the six
// DoltEvents anchor entities, the shipped row types (BranchRow, CommitRow,
// TagRow, StatusRow, RemoteRow, DiffStatRow), and DoltRowId/DoltEntityBase
// (the blessed key supertype, contract §13). Depends on Room runtime only,
// so :dolt-read and :dolt-write stay siblings (no write->read edge).

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    // ABI gate deferred until this module has public API (matches
    // :verifier/:processor): with klib cross-compilation off, non-mac
    // hosts must infer unavailable targets from the golden dump, and
    // KGP rejects an empty dump ("File is empty"). Enabling it is part
    // of the deliberate D11 event that lands this module's first API.
    abiValidation {
        enabled.set(false)
        legacyDump {
            referenceDumpDir.set(layout.projectDirectory.dir("api"))
        }
    }

    jvm()
    androidLibrary {
        namespace = "dev.seri.doltrooms.dolt.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {}

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // Room annotations + entities: the shipped row/anchor types are
            // Room entities (docs/design/module-architecture.md).
            api(libs.room3.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
}

dependencies {
    // The opt-in ruleset for published libraries the detekt.yml overlay tunes.
    detektPlugins(libs.detekt.rules.libraries)
}

// Per-module gates: the ABI dump and detekt on the (currently empty)
// commonMain source set.
tasks.named("check") {
    dependsOn("checkLegacyAbi", "detektCommonMainSourceSet")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "doltrooms-dolt-core", version.toString())
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )
    pom {
        name = "doltrooms-dolt-core"
        description = "The shared kernel of the doltrooms version-control surface: refs, the DoltEvents anchor entities, shipped row types, and the blessed key supertype."
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

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

// --- :dolt-remotes — sync (the opt-in, trusted-network-only surface) ------
// SCAFFOLD. Populated when the rev-4 contract implementation lands
// (docs/design/module-architecture.md). Intended contents: the Remotes
// collection, fetch/push/pull, and the clone bootstrap. Separate because
// D3/D9 make sync an opt-in surface (plain file:///http://). An API-hygiene
// split, not binary-size: the engine in :driver contains the sync code
// regardless.

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
        legacyDump {
            referenceDumpDir.set(layout.projectDirectory.dir("api"))
        }
    }

    jvm()
    androidLibrary {
        namespace = "dev.seri.doltrooms.dolt.remotes"
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
            // pull = fetch + merge; reuses MergeResult/ConflictPolicy from the
            // write surface (docs/design/module-architecture.md).
            api(project(":dolt-write"))
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
    detektPlugins(libs.detekt.rules.libraries)
}

tasks.named("check") {
    dependsOn("checkLegacyAbi", "detektCommonMainSourceSet")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "doltrooms-dolt-remotes", version.toString())
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )
    pom {
        name = "doltrooms-dolt-remotes"
        description = "The doltrooms sync surface: the Remotes collection plus fetch/push/pull and the clone bootstrap over trusted-network file:// and http:// remotes."
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

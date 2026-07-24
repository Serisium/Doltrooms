import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
}

group = "dev.seri.doltrooms"
version = "0.1.0-SNAPSHOT"

// --- :verifier — Room's verification-database shim (host JVM) --------------
// SCAFFOLD. Host-JVM, KSP-classpath-only; NEVER ships in an app. Populated
// when the read-path design lands (docs/design/module-architecture.md).
// Intended contents: the `org.sqlite` shim that swaps Room's verification
// database to DoltLite at compile time.

kotlin {
    // No jvmToolchain pin: build with the Gradle JVM, like the KMP modules —
    // a pinned toolchain is a hidden host dependency (KSP-classpath tools run
    // in the build JVM anyway).
    explicitApi()
}

dependencies {
    // Room's compiler runs against DoltLite's host natives — the jvm artifact
    // of :driver (docs/design/module-architecture.md DAG).
    implementation(project(":driver"))
    detektPlugins(libs.detekt.rules.libraries)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
}

tasks.named("check") {
    dependsOn("detekt")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "doltrooms-verifier", version.toString())
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        )
    )
    pom {
        name = "doltrooms-verifier"
        description = "The doltrooms Room verification-database shim (host JVM, KSP classpath only): swaps Room's compile-time verification database to DoltLite. Never ships in an app."
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

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

// --- :processor — the library's own KSP processor (host JVM) ---------------
// SCAFFOLD. Host-JVM, KSP-classpath-only; NEVER ships in an app. Populated
// when the rev-4 implementation lands (docs/design/module-architecture.md;
// the P5/P6/P7 prototype harnesses are the seeds). Intended contents:
// @DoltQuery codegen, the anchor lint (contract §8), and the DDL emitter
// with its pinned feature ceiling (contract §12 D-d + §13 blessed shape).

kotlin {
    // No jvmToolchain pin: build with the Gradle JVM, like the KMP modules —
    // a pinned toolchain is a hidden host dependency (KSP-classpath tools run
    // in the build JVM anyway).
    explicitApi()
}

dependencies {
    // Knows :dolt-read's annotations by qualified name (no compile dependency
    // required); depends on :driver's jvm artifact for the host engine
    // (docs/design/module-architecture.md DAG).
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
    coordinates(group.toString(), "doltrooms-processor", version.toString())
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        )
    )
    pom {
        name = "doltrooms-processor"
        description = "The doltrooms KSP processor (host JVM, KSP classpath only): @DoltQuery codegen, the anchor lint, and the DDL emitter. Never ships in an app."
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

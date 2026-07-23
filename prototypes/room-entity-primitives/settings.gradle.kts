// Design-evidence prototype for docs/design/room-entity-dolt-primitives.md:
// verifier-shim swaps Room's compile-time query-verification engine from
// stock SQLite (xerial) to DoltLite, so dolt_* system tables and TVFs
// verify like ordinary schema; app holds the target DAOs and runtime tests.
// Run with the ROOT wrapper:
//   cd prototypes/room-entity-primitives && ../../gradlew :app:test
pluginManagement {
    repositories {
        google {
            @Suppress("UnstableApiUsage")
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "room-entity-primitives"
include(":verifier-shim")
include(":app")

// Consume the doltrooms library from source, like samples/codelab.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("dev.seri.doltrooms:doltrooms")).using(project(":library"))
    }
}

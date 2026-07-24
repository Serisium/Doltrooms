pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "doltrooms"

// The multi-module split (docs/design/module-architecture.md). The DAG is
// enforced by Gradle by construction — a cycle fails configuration.
//   :driver        SQLite/Room parity (natives, cinterop); androidx.sqlite only
//   :dolt-core     shared vcs kernel (refs, anchors, row types); Room runtime
//   :dolt-read     @DoltQuery read machinery            -> :dolt-core
//   :dolt-write    the git verbs (DoltDatabase facade)  -> :dolt-core
//   :dolt-remotes  sync (fetch/push/pull/clone)         -> :dolt-write
//   :verifier      Room verification shim (host JVM)    -> :driver(jvm)
//   :processor     @DoltQuery codegen (host JVM)        -> :driver(jvm)
include(":driver")
include(":dolt-core")
include(":dolt-read")
include(":dolt-write")
include(":dolt-remotes")
include(":verifier")
include(":processor")

// The verifier shim: shadows xerial's org.sqlite.JDBC / SQLiteJDBCLoader /
// SQLiteConnection with a minimal JDBC facade over DoltLiteDriver. Goes on
// a consumer's KSP (annotation-processor) classpath ONLY — never the app
// runtime — with org.xerial:sqlite-jdbc excluded so exactly one org.sqlite
// package exists there.
plugins {
    kotlin("jvm")
}

dependencies {
    // Brings androidx.sqlite and the DoltLite engine (substituted to the
    // root build's :library). The jvm jar packages natives/linux-x64 only.
    implementation("dev.seri.doltrooms:doltrooms:0.1.0-SNAPSHOT")
}

// On a macOS dev host the library jar's packaged .so is a Linux ELF; the
// KSP process needs the host-arch dylib. Borrow the library's host-test
// twin (compileDoltliteJniHost) and package it into this jar's resources,
// where dev.seri.doltrooms' NativeLibraryLoader finds it by classifier.
val hostIsMac = System.getProperty("os.name").lowercase().startsWith("mac")
if (hostIsMac) {
    // The included build's name is its root DIRECTORY name (repo checkout
    // or worktree dir), so resolve it structurally: this build includes
    // exactly one other build — the doltrooms root.
    val hostJni = gradle.includedBuilds.single().task(":library:compileDoltliteJniHost")
    tasks.processResources {
        dependsOn(hostJni)
        from(rootDir.resolve("../../library/build/nativeLibs/jvmHost"))
    }
}

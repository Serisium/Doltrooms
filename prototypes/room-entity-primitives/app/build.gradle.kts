plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("dev.seri.doltrooms:doltrooms:0.1.0-SNAPSHOT")
    implementation("androidx.room3:room3-runtime:3.0.0")
    implementation("androidx.room3:room3-paging:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    ksp("androidx.room3:room3-compiler:3.0.0")
    // Approach D: put the DoltLite JDBC facade on the PROCESSOR classpath
    // so Room's DatabaseVerifier prepares @Query SQL against DoltLite.
    ksp(project(":verifier-shim"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// On a macOS dev host the library jar packages only the linux-x64 .so;
// point the loader's explicit-path override at the host-test dylib the
// root build compiles (same wiring the library's own jvmTest uses).
val hostIsMac = System.getProperty("os.name").lowercase().startsWith("mac")
if (hostIsMac) {
    val hostJni = gradle.includedBuilds.single().task(":library:compileDoltliteJniHost")
    val hostArch =
        if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "osx-arm64" else "osx-x64"
    tasks.test {
        dependsOn(hostJni)
        systemProperty(
            "dev.seri.doltrooms.lib.path",
            rootDir.resolve("../../library/build/nativeLibs/jvmHost/natives/$hostArch/libdoltroomsjni.dylib").absolutePath,
        )
    }
}

// Exactly one org.sqlite package may exist on the processor classpath:
// drop xerial (room3-compiler's transitive dependency) so class loading
// cannot race the shim. This is deterministic, not ordering-dependent.
configurations.matching { it.name.startsWith("ksp") }.configureEach {
    exclude(group = "org.xerial", module = "sqlite-jdbc")
}

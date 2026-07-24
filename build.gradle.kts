plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    // Same kotlin-gradle-plugin artifact as the KMP plugin; declared here so
    // the host-JVM tool modules (:verifier, :processor) resolve it from the
    // classpath instead of re-requesting a version (multi-module conflict).
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}

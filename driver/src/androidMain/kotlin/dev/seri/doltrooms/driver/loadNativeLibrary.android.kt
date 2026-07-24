package dev.seri.doltrooms.driver

internal actual fun loadNativeLibrary() {
    // jniLibs packaging lands with the Android step of PLAN.md; until
    // then this resolves nothing at runtime but keeps the target green.
    System.loadLibrary("doltroomsjni")
}

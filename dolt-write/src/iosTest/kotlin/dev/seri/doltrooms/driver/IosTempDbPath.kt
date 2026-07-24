package dev.seri.doltrooms.driver

import kotlin.random.Random
import platform.Foundation.NSTemporaryDirectory

/**
 * A unique, not-yet-existing database path inside the app/test sandbox —
 * the iOS analogue of linuxX64Test's `/tmp`-based helper (the simulator
 * sandbox does not guarantee a writable literal `/tmp`).
 */
internal fun nativeTempDbPath(prefix: String): String =
    NSTemporaryDirectory().trimEnd('/') +
        "/$prefix-${Random.nextLong().toULong().toString(16)}.db"

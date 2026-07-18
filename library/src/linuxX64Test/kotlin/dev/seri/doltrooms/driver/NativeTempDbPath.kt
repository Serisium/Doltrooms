package dev.seri.doltrooms.driver

import kotlin.random.Random

/**
 * A unique, not-yet-existing database path — the native analogue of the
 * jvmTest concretes' `File.createTempFile(...).also { it.delete() }`.
 */
internal fun nativeTempDbPath(prefix: String): String =
    "/tmp/$prefix-${Random.nextLong().toULong().toString(16)}.db"

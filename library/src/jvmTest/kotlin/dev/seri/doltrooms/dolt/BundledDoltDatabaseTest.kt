package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Stock SQLite has no dolt_* surface, so this concrete is NOT an oracle
 * leg (there is none for the dolt suite): via `engineSupportsDolt =
 * false` it runs only the guard test proving the helpers fail cleanly
 * on an engine without version control.
 */
class BundledDoltDatabaseTest : AbstractDoltDatabaseTest() {
    override fun driver(): SQLiteDriver = BundledSQLiteDriver()

    override val engineSupportsDolt: Boolean = false

    override fun tempDbPath(): String =
        File.createTempFile("doltrooms-dolt", ".db").also { it.delete() }.absolutePath
}

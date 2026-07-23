package dev.seri.doltrooms.dolt

import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Backrooms epoch seed: `resources/dolt/backrooms-epoch-2019.db` is
 * a DoltLite database whose history begins at **2019-05-13 12:00:00** —
 * the Backrooms lore epoch (the original 4chan thread). Its root commit
 * ("Initialize data repository") carries that date, so every timeline
 * built on top is date-coherent from the very first commit.
 *
 * The root commit's date is fixed at database creation (probed:
 * `--amend` refuses on the root), so the seed was minted at creation
 * time: on a Linux host, the pinned tools-zip `doltlite` CLI opens a
 * new file under an `LD_PRELOAD` clock shim pinning `time()`/
 * `gettimeofday()`/`clock_gettime()` to epoch 1557748800:
 *
 * ```
 * LD_PRELOAD=$PWD/fakeclock.so ./doltlite backrooms-epoch-2019.db "SELECT dolt_version();"
 * ```
 *
 * Minted 2026-07-22 at the 0.11.33 pin (1148 bytes; re-mint on a pin
 * bump — the file is engine-version-sensitive). Root commit hash
 * `44dda288bd7062e63a7c8d10ba54c286b73360e9`, reproducible because the
 * committer ("doltlite") and clock are both fixed.
 */
class BackroomsEpochSeedTest {

    private fun materialize(): String {
        val resource = checkNotNull(
            BackroomsEpochSeedTest::class.java.classLoader
                .getResourceAsStream("dolt/backrooms-epoch-2019.db")
        ) { "seed resource missing" }
        val file = File.createTempFile("backrooms-epoch", ".db")
        resource.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file.absolutePath
    }

    @Test
    fun historyBeginsAtTheLoreEpoch() {
        DoltLiteDriver().open(materialize()).use { conn ->
            val log = conn.prepare("SELECT commit_hash, date, message FROM dolt_log").use { s ->
                buildList {
                    while (s.step()) add(Triple(s.getText(0), s.getText(1), s.getText(2)))
                }
            }
            assertEquals(1, log.size)
            val (hash, date, message) = log.single()
            assertEquals("2019-05-13 12:00:00", date)
            assertEquals("Initialize data repository", message)
            assertEquals("44dda288bd7062e63a7c8d10ba54c286b73360e9", hash)
        }
    }

    @Test
    fun backroomsTimelineBuildsOnTopWithFullyCoherentDateOrder() {
        DoltLiteDriver().open(materialize()).use { conn ->
            BackroomsProductionFixture.build(conn)

            // With history starting at the 2019 lore epoch, date order
            // and topological order agree over the ENTIRE history — a
            // database created today can't have this (its root carries
            // the modern creation date and outranks every backdated
            // milestone under ORDER BY date).
            val topological = conn.prepare("SELECT message FROM dolt_log").use { s ->
                buildList { while (s.step()) add(s.getText(0)) }
            }
            val byDate = conn.prepare("SELECT message FROM dolt_log ORDER BY date DESC").use { s ->
                buildList { while (s.step()) add(s.getText(0)) }
            }
            assertEquals(topological, byDate)
            assertEquals("Initialize data repository", topological.last())
            assertTrue(topological.size > 10, "full fixture history expected")
        }
    }
}

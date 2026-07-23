package dev.seri.doltrooms.prototype

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.seri.doltrooms.driver.DoltLiteDriver
import java.io.File

/**
 * Every prototype test database starts from the Backrooms epoch seed
 * (`library/src/jvmTest/resources/dolt/backrooms-epoch-2019.db`, path
 * via the `doltrooms.epochSeed` system property): a DoltLite database
 * whose history begins at the lore epoch, 2019-05-13 12:00:00. Each
 * call copies the seed to a fresh temp file, so tests stay isolated,
 * and opens Room on it file-backed with WAL — the real 4-reader/
 * 1-writer pool, unlike the single-connection in-memory special case.
 */
fun epochSeededDatabase(): PrototypeDatabase {
    val seed = File(checkNotNull(System.getProperty("doltrooms.epochSeed")) { "doltrooms.epochSeed not set" })
    val copy = File.createTempFile("backrooms-epoch", ".db")
    seed.copyTo(copy, overwrite = true)
    return Room.databaseBuilder<PrototypeDatabase>(name = copy.absolutePath)
        .setDriver(DoltLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
}

/** The epoch the seed's history begins at. */
const val LORE_EPOCH_DATE: String = "2019-05-13 12:00:00"

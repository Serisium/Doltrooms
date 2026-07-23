package dev.seri.doltrooms.prototype

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [Fruittie::class, DoltEvent::class],
    views = [Branch::class],
    version = 1,
    exportSchema = false,
)
abstract class PrototypeDatabase : RoomDatabase() {
    abstract fun doltPrimitivesDao(): DoltPrimitivesDao
    abstract fun doltPropQueriesDao(): DoltPropQueriesDao
}

/**
 * Library-side builder for target query 4's RoomRawQuery: the per-table
 * diff TVF with the table name quoted into the SQL (it is part of the
 * TVF's NAME and cannot bind) and the refs bound as real parameters.
 */
fun tableDiffQuery(table: String, from: String, to: String): androidx.room3.RoomRawQuery {
    val tvf = "\"dolt_diff_" + table.replace("\"", "\"\"") + "\""
    return androidx.room3.RoomRawQuery("SELECT * FROM $tvf(?, ?)") {
        it.bindText(1, from)
        it.bindText(2, to)
    }
}

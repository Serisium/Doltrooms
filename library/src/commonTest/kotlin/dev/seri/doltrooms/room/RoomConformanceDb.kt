package dev.seri.doltrooms.room

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

// The Step 4 fixture database: a real Room 3 schema exercised identically
// against DoltLiteDriver and BundledSQLiteDriver (room3 skill, testing
// reference). INTEGER autoGenerate PK deliberately — Room's generated
// inserts rely on last_insert_rowid, which DoltLite only preserves for
// INTEGER-PK (rowid) tables (Current State divergence table).

@Entity
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int,
)

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: Person): Long

    @Query("SELECT * FROM Person WHERE id = :id")
    suspend fun byId(id: Long): Person?
}

@Database(entities = [Person::class], version = 1, exportSchema = true)
@ConstructedBy(RoomConformanceDbConstructor::class)
abstract class RoomConformanceDb : RoomDatabase() {
    abstract fun personDao(): PersonDao
}

// KSP generates the per-target actuals (room3 skill, kmp-setup reference).
@Suppress("KotlinNoActualForExpect")
expect object RoomConformanceDbConstructor : RoomDatabaseConstructor<RoomConformanceDb> {
    override fun initialize(): RoomConformanceDb
}

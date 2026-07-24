package dev.seri.doltrooms.room

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.SkipQueryVerification
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM Person WHERE age > :age ORDER BY id")
    suspend fun olderThan(age: Int): List<Person>

    @Query("SELECT * FROM Person ORDER BY id")
    fun observeAll(): Flow<List<Person>>

    @Update
    suspend fun update(person: Person): Int

    @Delete
    suspend fun delete(person: Person): Int

    // Room wraps default-body @Transaction functions in a database
    // transaction; a thrown exception must roll back the earlier insert.
    @Transaction
    suspend fun insertPair(first: Person, second: Person) {
        insert(first)
        check(second.age >= 0) { "invalid second person" }
        insert(second)
    }

    // Room verifies @Query against stock SQLite at compile time, which has
    // no dolt_* functions — @SkipQueryVerification is the documented escape
    // for read-shaped dolt_* SQL in DAOs (Step 7; only meaningful on the
    // DoltLite legs, but it must COMPILE for every driver's test classes).
    @SkipQueryVerification
    @Query("SELECT dolt_version()")
    suspend fun doltVersion(): String
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

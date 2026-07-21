/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.fruitties.android.database

import androidx.room3.Room
import com.example.fruitties.kmptutorial.android.database.AppDatabase
import com.example.fruitties.kmptutorial.android.database.FruittieDao
import com.example.fruitties.kmptutorial.android.model.Fruittie
import dev.seri.doltrooms.driver.DoltLiteDriver
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class FruittieDaoTest {

    companion object {
        private const val TEST_FRUITTIE_ID = 123L
    }

    private lateinit var db: AppDatabase
    private lateinit var fruittieDao: FruittieDao
    private val testFruittie =
        Fruittie(TEST_FRUITTIE_ID, "Test Fruittie", "AndroidTest Fruittie", "0")

    @Before
    fun createDb() {
        // Room 3's builder is context-free; the driver is DoltLite —
        // in-memory databases support the full dolt_* surface too.
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(DoltLiteDriver())
            .build()
        fruittieDao = db.fruittieDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun getAll_noItems_returnsEmptyList() = runTest {
        assertEquals(0, fruittieDao.getAll().first().size)
    }

    @Test
    fun getAll_itemsExist_returnsListOfItems() = runTest {
        fruittieDao.insert(listOf(testFruittie))
        assertEquals(1, fruittieDao.getAll().first().size)
        assertEquals(TEST_FRUITTIE_ID, fruittieDao.getAll().first()[0].id)
    }

    @Test
    fun count_itemsExist_returnsCorrectCount() = runTest {
        fruittieDao.insert(listOf(testFruittie))
        assertEquals(1, fruittieDao.count())
    }

    @Test
    fun insert_sameIdTwice_replacesItemAndCountRemainsOne() = runTest {
        val testFruittie2Name = "Test Fruittie 2"
        val testFruittie2 =
            Fruittie(TEST_FRUITTIE_ID, testFruittie2Name, "AndroidTest Fruittie 2", "0")
        fruittieDao.insert(listOf(testFruittie, testFruittie2))
        assertEquals(1, fruittieDao.count())
        assertEquals(testFruittie2Name, fruittieDao.getAll().first()[0].name)
    }
}

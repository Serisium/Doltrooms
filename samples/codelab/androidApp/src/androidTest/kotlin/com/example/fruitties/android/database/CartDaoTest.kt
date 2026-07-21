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
import androidx.sqlite.SQLiteException
import com.example.fruitties.kmptutorial.android.database.AppDatabase
import com.example.fruitties.kmptutorial.android.database.CartDao
import com.example.fruitties.kmptutorial.android.database.FruittieDao
import com.example.fruitties.kmptutorial.android.model.CartItem
import com.example.fruitties.kmptutorial.android.model.Fruittie
import dev.seri.doltrooms.driver.DoltLiteDriver
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CartDaoTest {

    companion object {
        private const val TEST_FRUITTIE_ID = 123L
    }

    private lateinit var db: AppDatabase
    private lateinit var cartDao: CartDao
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
        cartDao = db.cartDao()
        fruittieDao = db.fruittieDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun getAll_noItems_returnsEmptyList() = runTest {
        assertEquals(0, cartDao.getAll().first().size)
    }

    /**
     * Adding a cart item without having corresponding Fruittie in the DB would cause exception.
     * With a pluggable driver Room 3 surfaces constraint violations as
     * androidx.sqlite.SQLiteException, not android.database.sqlite's type.
     */
    @Test
    fun insert_foreignKeyConstraintViolation_throwsException() = runTest {
        assertThrows(SQLiteException::class.java) {
            runBlocking {
                cartDao.insert(CartItem(1))
            }
        }
    }

    @Test
    fun getAll_itemsExist_returnsListOfItems() = runTest {
        fruittieDao.insert(listOf(testFruittie))
        cartDao.insert(CartItem(TEST_FRUITTIE_ID))
        assertEquals(1, cartDao.getAll().first().size)
        assertEquals(TEST_FRUITTIE_ID, cartDao.getAll().first()[0].fruittie.id)
    }

    @Test
    fun findById_existingId_returnsMatchingItem() = runTest {
        fruittieDao.insert(listOf(testFruittie))
        cartDao.insert(CartItem(TEST_FRUITTIE_ID))
        assertEquals(TEST_FRUITTIE_ID, cartDao.findById(TEST_FRUITTIE_ID)?.id)
    }
}

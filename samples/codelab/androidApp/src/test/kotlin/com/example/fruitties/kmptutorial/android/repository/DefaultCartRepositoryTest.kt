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
package com.example.fruitties.kmptutorial.android.repository

import com.example.fruitties.kmptutorial.android.database.CartDao
import com.example.fruitties.kmptutorial.android.fakes.FakeCartDao
import com.example.fruitties.kmptutorial.android.model.Fruittie
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DefaultCartRepositoryTest {

    companion object {
        private const val TEST_FRUITTIE_ID = 123L
    }

    private lateinit var repository: CartRepository
    private lateinit var cartDao: CartDao
    private val testFruittie =
        Fruittie(TEST_FRUITTIE_ID, "Test Fruittie", "AndroidTest Fruittie", "0")

    @Before
    fun setup() {
        cartDao = FakeCartDao(listOf(testFruittie))
        repository = DefaultCartRepository(cartDao)
    }

    @Test
    fun test_addToCart() = runTest {
        assertEquals(0, repository.cartData.first().size)
        repository.addToCart(testFruittie)
        with(repository.cartData.first()[0]) {
            assertEquals(TEST_FRUITTIE_ID, cartItem.id)
            assertEquals(1, cartItem.count)
        }
        // Add item again - should increase the count value
        repository.addToCart(testFruittie)
        with(repository.cartData.first()[0]) {
            assertEquals(TEST_FRUITTIE_ID, cartItem.id)
            assertEquals(2, cartItem.count)
        }
    }
}

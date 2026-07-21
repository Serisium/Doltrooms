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
package com.example.fruitties.kmptutorial.android.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.example.fruitties.kmptutorial.android.model.CartItem
import com.example.fruitties.kmptutorial.android.model.CartItemWithFruittie
import com.example.fruitties.kmptutorial.android.model.Fruittie
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(cartItem: CartItem)

    @Update
    suspend fun update(cartItem: CartItem)

    @Transaction
    @Query("SELECT * FROM cartitem")
    fun getAll(): Flow<List<CartItemWithFruittie>>

    @Query("SELECT * FROM cartitem WHERE id = :id")
    suspend fun findById(id: Long): CartItem?

    @Transaction
    suspend fun insertOrIncreaseCount(fruittie: Fruittie) {
        val cartItemInDb = findById(fruittie.id)
        if (cartItemInDb == null) {
            // New item
            insert(CartItem(fruittie.id))
        } else {
            // Existing item
            update(cartItemInDb.copy(count = cartItemInDb.count + 1))
        }
    }
}

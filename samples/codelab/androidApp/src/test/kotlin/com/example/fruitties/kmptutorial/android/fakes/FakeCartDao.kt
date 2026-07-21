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
package com.example.fruitties.kmptutorial.android.fakes

import com.example.fruitties.kmptutorial.android.database.CartDao
import com.example.fruitties.kmptutorial.android.model.CartItem
import com.example.fruitties.kmptutorial.android.model.CartItemWithFruittie
import com.example.fruitties.kmptutorial.android.model.Fruittie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeCartDao(val fruitties: List<Fruittie>) : CartDao {

    private val cartFlow = MutableStateFlow(mutableSetOf<CartItemWithFruittie>())

    override suspend fun insert(cartItem: CartItem) {
        cartFlow.update {
            it.add(CartItemWithFruittie(cartItem, fruitties.find { it.id == cartItem.id }!!))
            it
        }
    }

    override suspend fun update(cartItem: CartItem) {
        cartFlow.update {
            it.removeIf {
                it.cartItem.id == cartItem.id
            }
            it.add(CartItemWithFruittie(cartItem, fruitties.find { it.id == cartItem.id }!!))
            it
        }
    }

    override fun getAll(): Flow<List<CartItemWithFruittie>> = cartFlow.map { it.toList() }
    override suspend fun findById(id: Long): CartItem? =
        cartFlow.value.find { it.cartItem.id == id }?.cartItem
}

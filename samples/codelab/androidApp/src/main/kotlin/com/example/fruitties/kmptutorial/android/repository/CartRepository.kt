/*
 * Copyright 2024 The Android Open Source Project
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
import com.example.fruitties.kmptutorial.android.model.CartItemWithFruittie
import com.example.fruitties.kmptutorial.android.model.Fruittie
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface CartRepository {
    val cartData: Flow<List<CartItemWithFruittie>>
    suspend fun addToCart(fruittie: Fruittie)
}

class DefaultCartRepository @Inject constructor(private val cartDao: CartDao) : CartRepository {
    override val cartData: Flow<List<CartItemWithFruittie>> = cartDao.getAll()
    override suspend fun addToCart(fruittie: Fruittie) = cartDao.insertOrIncreaseCount(fruittie)
}

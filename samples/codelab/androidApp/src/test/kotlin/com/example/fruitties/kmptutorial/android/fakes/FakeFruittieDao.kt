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

import com.example.fruitties.kmptutorial.android.database.FruittieDao
import com.example.fruitties.kmptutorial.android.model.Fruittie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeFruittieDao : FruittieDao {

    private var idCounter = 1L

    private val fruittieFlow = MutableStateFlow(mutableSetOf<Fruittie>())

    override suspend fun insert(fruitties: List<Fruittie>): List<Long> {
        val fruittiesWithUpdatedIds = fruitties.map { fruittie ->
            if (idCounter <= fruittie.id) {
                idCounter = fruittie.id + 1
            }
            if (fruittie.id == 0L) {
                fruittie.copy(id = idCounter++)
            } else {
                fruittie
            }
        }
        fruittieFlow.update {
            it.addAll(fruittiesWithUpdatedIds)
            it
        }
        return fruittiesWithUpdatedIds.map { it.id }
    }

    override fun getAll(): Flow<List<Fruittie>> = fruittieFlow.map { it.toList() }

    override suspend fun count(): Int = fruittieFlow.value.size
}

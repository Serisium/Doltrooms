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

import com.example.fruitties.kmptutorial.android.model.Fruittie
import com.example.fruitties.kmptutorial.android.repository.FruittieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeFruittieRepository : FruittieRepository {

    private val fruittieFlow = MutableStateFlow(mutableSetOf<Fruittie>())

    override fun getData(): Flow<List<Fruittie>> = fruittieFlow.map { it.toList() }

    // Test only function
    fun addData(list: List<Fruittie>) {
        fruittieFlow.update {
            it.addAll(list)
            it
        }
    }
}

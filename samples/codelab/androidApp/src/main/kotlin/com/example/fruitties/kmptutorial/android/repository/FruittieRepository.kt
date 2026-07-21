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

import com.example.fruitties.kmptutorial.android.database.FruittieDao
import com.example.fruitties.kmptutorial.android.model.Fruittie
import com.example.fruitties.kmptutorial.android.network.FruittieApi
import com.example.fruitties.kmptutorial.android.network.toModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

interface FruittieRepository {
    fun getData(): Flow<List<Fruittie>>
}

class DefaultFruittieRepository @Inject constructor(
    private val api: FruittieApi,
    private val fruittieDao: FruittieDao,
    private val scope: CoroutineScope,
) : FruittieRepository {
    override fun getData(): Flow<List<Fruittie>> {
        scope.launch {
            if (fruittieDao.count() < 1) {
                refreshData()
            }
        }
        return loadData()
    }

    private fun loadData(): Flow<List<Fruittie>> = fruittieDao.getAll()

    private suspend fun refreshData() {
        val response = api.getData()
        fruittieDao.insert(response.feed.map { it.toModel() })
    }
}

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

import com.example.fruitties.kmptutorial.android.database.FruittieDao
import com.example.fruitties.kmptutorial.android.fakes.FakeFruittieApi
import com.example.fruitties.kmptutorial.android.fakes.FakeFruittieDao
import com.example.fruitties.kmptutorial.android.network.FruittieApi
import com.example.fruitties.kmptutorial.android.util.FruittieTestData
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DefaultFruittieRepositoryTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var repository: FruittieRepository
    private lateinit var fruittieDao: FruittieDao
    private lateinit var fruittieApi: FruittieApi

    @Before
    fun setup() {
        fruittieDao = FakeFruittieDao()
        fruittieApi = FakeFruittieApi(listOf(FruittieTestData.fruittie))
        repository = DefaultFruittieRepository(fruittieApi, fruittieDao, testScope)
    }

    @Test
    fun test_getData() = runTest {
        assertEquals(0, fruittieDao.count())
        assertEquals(1, repository.getData().first().size)
        assertEquals(1, fruittieDao.count())
    }
}

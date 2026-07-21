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
package com.example.fruitties.kmptutorial.android.viewmodel

import app.cash.turbine.test
import com.example.fruitties.kmptutorial.android.fakes.FakeCartRepository
import com.example.fruitties.kmptutorial.android.fakes.FakeFruittieRepository
import com.example.fruitties.kmptutorial.android.ui.MainViewModel
import com.example.fruitties.kmptutorial.android.util.FruittieTestData
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val cartRepository = FakeCartRepository()
    private var fruittieRepository = FakeFruittieRepository()
    private lateinit var viewModel: MainViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(cartRepository, fruittieRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_cartState() = runTest {
        viewModel.cartUiState.test {
            var state = awaitItem()
            assertEquals(0, state.itemList.size)
            viewModel.addItemToCart(FruittieTestData.fruittie)
            state = awaitItem()
            assertEquals(1, state.itemList.size)
        }
    }

    @Test
    fun test_uiState() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            assertEquals(0, state.itemList.size)
            fruittieRepository.addData(listOf(FruittieTestData.fruittie))
            state = awaitItem()
            assertEquals(1, state.itemList.size)
        }
    }
}

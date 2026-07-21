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
package com.example.fruitties.kmptutorial.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fruitties.kmptutorial.android.model.CartItemWithFruittie
import com.example.fruitties.kmptutorial.android.model.Fruittie
import com.example.fruitties.kmptutorial.android.repository.CartRepository
import com.example.fruitties.kmptutorial.android.repository.FruittieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    fruittieRepository: FruittieRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        fruittieRepository.getData().map { HomeUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = HomeUiState(),
            )

    val cartUiState: StateFlow<CartUiState> =
        cartRepository.cartData.map { CartUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = CartUiState(),
            )

    fun addItemToCart(fruittie: Fruittie) {
        viewModelScope.launch {
            cartRepository.addToCart(fruittie)
        }
    }
}

/**
 * Ui State for ListScreen
 */
data class HomeUiState(val itemList: List<Fruittie> = listOf())

/**
 * Ui State for Cart
 */
data class CartUiState(val itemList: List<CartItemWithFruittie> = listOf())

private const val TIMEOUT_MILLIS = 5_000L

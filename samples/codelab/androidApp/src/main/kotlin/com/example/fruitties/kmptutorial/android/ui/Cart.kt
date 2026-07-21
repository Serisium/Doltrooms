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
package com.example.fruitties.kmptutorial.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fruitties.kmptutorial.android.model.CartItemWithFruittie

@Composable
fun Cart(cartItems: List<CartItemWithFruittie>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Cart has ${cartItems.sumOf {
                it.cartItem.count
            }} items of ${cartItems.count()} types of fruit",
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
        )
        Button(onClick = { expanded = !expanded }) {
            Text(text = if (expanded) "collapse" else "expand")
        }
    }
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(1000)),
        exit = fadeOut(animationSpec = tween(1000)),
    ) {
        Column(
            modifier.padding(horizontal = 32.dp),
        ) {
            if (cartItems.isEmpty()) {
                Text("Cart is empty, add some items")
            } else {
                cartItems.forEach { item ->
                    Text(text = "${item.fruittie.name} : ${item.cartItem.count}")
                }
            }
        }
    }
}

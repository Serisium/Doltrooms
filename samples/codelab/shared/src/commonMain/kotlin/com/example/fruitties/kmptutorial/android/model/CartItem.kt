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
package com.example.fruitties.kmptutorial.android.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Fruittie::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@ObjCName("CartItemEntity")
data class CartItem(@PrimaryKey val id: Long, val count: Int = 1)

data class CartItemWithFruittie(
    @Embedded val cartItem: CartItem,
    // Room 3 renamed @Relation's singular parentColumn/entityColumn
    // attributes to the plural array forms.
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
    )
    val fruittie: Fruittie,
)

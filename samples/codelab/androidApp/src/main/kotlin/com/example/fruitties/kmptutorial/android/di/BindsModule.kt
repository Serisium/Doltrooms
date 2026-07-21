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
package com.example.fruitties.kmptutorial.android.di

import com.example.fruitties.kmptutorial.android.network.FruittieApi
import com.example.fruitties.kmptutorial.android.network.FruittieNetworkApi
import com.example.fruitties.kmptutorial.android.repository.CartRepository
import com.example.fruitties.kmptutorial.android.repository.DefaultCartRepository
import com.example.fruitties.kmptutorial.android.repository.DefaultFruittieRepository
import com.example.fruitties.kmptutorial.android.repository.FruittieRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface BindsModule {
    @Binds
    fun bindsCartRepository(defaultCartRepository: DefaultCartRepository): CartRepository

    @Binds
    fun bindsFruittieRepository(
        defaultFruittieRepository: DefaultFruittieRepository,
    ): FruittieRepository

    @Binds
    fun bindsFruittieApi(fruittieNetworkApi: FruittieNetworkApi): FruittieApi
}

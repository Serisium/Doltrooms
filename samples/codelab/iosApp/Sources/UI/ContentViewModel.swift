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

import Combine

class ContentViewModel: ObservableObject {
    private let fruittieRepository: FruittieRepository
    private let cartRepository: CartRepository

    @Published
    private(set) var fruitties: [Fruittie] = []

    @Published
    private(set) var cartItems: [CartItem] = []

    init(fruittieRepository: FruittieRepository, cartRepository: CartRepository) {
        self.fruittieRepository = fruittieRepository
        self.cartRepository = cartRepository
    }

    func addToCart(fruittie: Fruittie) async {
        try? await cartRepository.addToCart(fruittie: fruittie)
    }

    @MainActor
    func activate() async {
        async let db: () = observeDatabase()
        async let cartUpdate: () = watchCart()
        _ = await (db, cartUpdate)
    }

    @MainActor
    private func observeDatabase() async {
        for await fruitties in fruittieRepository.getData() {
            self.fruitties = fruitties
        }
    }

    @MainActor
    private func watchCart() async {
        for await cartItems in cartRepository.getCartItems() {
            self.cartItems = cartItems
        }
    }
}

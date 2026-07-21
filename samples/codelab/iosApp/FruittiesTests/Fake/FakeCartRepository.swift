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

@testable import Fruitties
import Foundation
import CoreData

class FakeCartRepository: CartRepository {
    private let managedObjectContext: NSManagedObjectContext
    private var items: [CartItem] = [] {
        didSet {
            // We don't want to access items each time in case it changes while we're running this.
            let itemsCopy = items
            streamContinuations.values.forEach { continuation in
                continuation.yield(itemsCopy)
            }
        }
    }
    private var streamContinuations: [UUID: AsyncStream<[CartItem]>.Continuation] = [:]

    init(managedObjectContext: NSManagedObjectContext) {
        self.managedObjectContext = managedObjectContext
    }

    func addToCart(fruittie: Fruittie) async throws {
        try await Task.sleep(for: .milliseconds((1...100).randomElement() ?? 1))

        if let existingItemIndex = items.firstIndex(where: { $0.fruittie == fruittie }) {
            items[existingItemIndex].count += 1
        } else {
            let newItem = CartItem(context: managedObjectContext)
            newItem.fruittie = fruittie
            newItem.count = 1
            items.append(newItem)
        }
    }

    func getCartItems() -> AsyncStream<[CartItem]> {
        let uuid = UUID()
        let (stream, continuation) = AsyncStream<[CartItem]>.makeStream()
        streamContinuations[uuid] = continuation
        continuation.onTermination = { [weak self] _ in
            self?.streamContinuations.removeValue(forKey: uuid)
        }
        return stream
    }
}

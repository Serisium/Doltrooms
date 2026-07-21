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

import XCTest
import ConcurrencyExtras
import CoreData
@testable import Fruitties


final class DefaultCartRepositoryTests: XCTestCase {
    private var managedObjectContext: NSManagedObjectContext!
    private var fruittie: Fruittie!

    override func invokeTest() {
        withMainSerialExecutor {
            super.invokeTest()
        }
    }

    override func setUp() async throws {
        try await super.setUp()

        managedObjectContext = try NSManagedObjectContext.contextForTests()

        fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test Fruittie"
    }

    override func tearDown() async throws {
        managedObjectContext = nil
        fruittie = nil
    }

    func testAddToCart() async throws {
        let repository = DefaultCartRepository(managedObjectContext: managedObjectContext)
        var cartIterator = repository.getCartItems().makeAsyncIterator()

        let initialCart = await cartIterator.next()
        XCTAssertEqual(initialCart, [])

        try await repository.addToCart(fruittie: fruittie)

        let cartAfterAdding = await cartIterator.next()
        XCTAssertNotNil(cartAfterAdding)
        XCTAssertEqual(cartAfterAdding?.count, 1)
        XCTAssertEqual(cartAfterAdding?.first?.fruittie, fruittie)
        XCTAssertEqual(cartAfterAdding?.first?.count, 1)

        try await repository.addToCart(fruittie: fruittie)

        XCTAssertNotNil(cartAfterAdding)
        XCTAssertEqual(cartAfterAdding?.count, 1)
        XCTAssertEqual(cartAfterAdding?.first?.fruittie, fruittie)
        XCTAssertEqual(cartAfterAdding?.first?.count, 2)
    }
}

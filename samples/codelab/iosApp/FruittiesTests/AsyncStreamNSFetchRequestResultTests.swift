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

final class AsyncStreamNSFetchRequestResultTests: XCTestCase {
    override func invokeTest() {
        withMainSerialExecutor {
            super.invokeTest()
        }
    }

    func testProducesEmptyArrayItem() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let stream = AsyncStream.resultsStream(request: Fruittie.fetchRequest(), in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [])
    }

    func testProducesNewItemWhenAdded() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let stream = AsyncStream.resultsStream(request: Fruittie.fetchRequest(), in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [])

        let fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test fruittie"

        let secondItem = await iterator.next()

        XCTAssertEqual(secondItem, [fruittie])
    }

    func testProducesExistingValues() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test fruittie"

        let stream = AsyncStream.resultsStream(request: Fruittie.fetchRequest(), in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [fruittie])
    }

    func testProducesEmptyWhenDeleted() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test fruittie"

        let stream = AsyncStream.resultsStream(request: Fruittie.fetchRequest(), in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [fruittie])

        managedObjectContext.delete(fruittie)

        let secondItem = await iterator.next()

        XCTAssertEqual(secondItem, [])
    }

    func testProducesNewItemWhenUpdated() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test fruittie"

        let stream = AsyncStream.resultsStream(request: Fruittie.fetchRequest(), in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [fruittie])

        fruittie.name = "New Name"

        let secondItem = await iterator.next()

        XCTAssertEqual(secondItem, [fruittie])
    }

    @MainActor
    func testProducesNoItemsOnInactivity() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test fruittie"

        let stream = AsyncStream.resultsStream(request: Fruittie.fetchRequest(), in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [fruittie])

        let secondItemExpectation = expectation(description: "Wait for second item")
        secondItemExpectation.isInverted = true

        let task = Task {
            _ = await iterator.next()
            secondItemExpectation.fulfill()
        }

        await fulfillment(of: [secondItemExpectation], timeout: 5)

        task.cancel()
    }

    @MainActor
    func testProducesNoItemsOnOtherRequests() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let fruittie = Fruittie(context: managedObjectContext)
        fruittie.name = "Test"
        fruittie.fullName = "Test fruittie"

        let request = Fruittie.fetchRequest()
        request.predicate = NSPredicate(format: "name == %@", "Test")
        let stream = AsyncStream.resultsStream(request: request, in: managedObjectContext)
        var iterator = stream.makeAsyncIterator()

        let firstItem = await iterator.next()

        XCTAssertEqual(firstItem, [fruittie])

        let secondFruittie = Fruittie(context: managedObjectContext)
        secondFruittie.name = "Not test"
        secondFruittie.fullName = "Second fruittie"

        let secondItemExpectation = expectation(description: "Wait for second item")
        secondItemExpectation.isInverted = true

        let task = Task {
            _ = await iterator.next()
            secondItemExpectation.fulfill()
        }

        await fulfillment(of: [secondItemExpectation], timeout: 5)

        task.cancel()
    }
}

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

import ConcurrencyExtras
import CoreData
import XCTest

@testable import Fruitties

final class DefaultFruittieRepositoryTests: XCTestCase {
    override func invokeTest() {
        withMainSerialExecutor {
            super.invokeTest()
        }
    }

    func testGetDataUpdatesWhenNewItemAdded() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let repository = DefaultFruittieRepository(
            managedObjectContext: managedObjectContext,
            api: MockFruittieApi { _ in
                FruittiesResponse(
                    feed: [],
                    totalPages: 0,
                    currentPage: 1
                )
            }
        )

        var fruittieStreamIterator = repository.getData().makeAsyncIterator()
        let initialFruities = await fruittieStreamIterator.next()

        XCTAssertEqual(initialFruities, [])

        let newFruitttie = Fruittie(context: managedObjectContext)
        newFruitttie.name = "Test Fruittie"
        newFruitttie.fullName = "A testing Fruittie"

        let fruitties = await fruittieStreamIterator.next()
        XCTAssertEqual(fruitties, [newFruitttie])
    }

    func testDataLoadedFromApi() async throws {
        let repository = DefaultFruittieRepository(
            managedObjectContext: try NSManagedObjectContext.contextForTests(),
            api: MockFruittieApi { _ in
                FruittiesResponse(
                    feed: [
                        FruittiesResponse.Item(
                            name: "Test", fullName: "Hello World",
                            calories: "95 calories")
                    ],
                    totalPages: 4,
                    currentPage: 1
                )
            }
        )

        var fruitttiesStreamIterator = repository.getData().makeAsyncIterator()
        let initialFruitties = await fruitttiesStreamIterator.next()
        XCTAssertEqual(initialFruitties, [])

        let fruitties = await fruitttiesStreamIterator.next()

        XCTAssertNotNil(fruitties)
        XCTAssertEqual(fruitties?.count, 1)
        XCTAssertEqual(fruitties?.first?.name, "Test")
        XCTAssertEqual(fruitties?.first?.fullName, "Hello World")
    }

    func testNoRequestMadeWhenEntriesExist() async throws {
        let managedObjectContext = try NSManagedObjectContext.contextForTests()

        let apiRequestExpectation = expectation(
            description: "Mock Fruittie API request")
        apiRequestExpectation.isInverted = true

        let repository = DefaultFruittieRepository(
            managedObjectContext: managedObjectContext,
            api: MockFruittieApi { _ in
                apiRequestExpectation.fulfill()
                return FruittiesResponse(
                    feed: [],
                    totalPages: 0,
                    currentPage: 1
                )
            }
        )

        let oldFruitttie = Fruittie(context: managedObjectContext)
        oldFruitttie.name = "Test Fruittie"
        oldFruitttie.fullName = "A testing Fruittie"

        var fruittieStreamIterator = repository.getData().makeAsyncIterator()
        let initialFruities = await fruittieStreamIterator.next()

        XCTAssertEqual(initialFruities, [oldFruitttie])

        await fulfillment(of: [apiRequestExpectation], timeout: 5)
    }
}

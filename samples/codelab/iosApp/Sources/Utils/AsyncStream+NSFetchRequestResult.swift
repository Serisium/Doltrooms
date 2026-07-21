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

import CoreData

extension AsyncStream where Element: NSFetchRequestResult {
    static func resultsStream(
        request: NSFetchRequest<Element>,
        in context: NSManagedObjectContext,
        sectionNameKeyPath: String? = nil,
        cacheName: String? = nil
    ) -> AsyncStream<[Element]> {
        if request.sortDescriptors == nil {
            request.sortDescriptors = []
        }
        return AsyncStream<[Element]> { continuation in
            let controller = NSFetchedResultsController(
                fetchRequest: request,
                managedObjectContext: context,
                sectionNameKeyPath: sectionNameKeyPath,
                cacheName: cacheName
            )
            let delegate = AsyncStreamNSFetchedResultsControllerDelegate(continuation: continuation)
            controller.delegate = delegate

            do {
                try controller.performFetch()
                if let initialContent = controller.fetchedObjects {
                    continuation.yield(initialContent)
                } else {
                    print("Warning: \(controller).fetchedObjects was still nil after calling performFetch.")
                    continuation.finish()
                    return
                }
            } catch {
                print("Warning: Couldn't get initial content. Error: \(error.localizedDescription)")
                continuation.finish()
                return
            }

            continuation.onTermination = { _ in
                withExtendedLifetime(delegate) {
                    controller.delegate = nil
                }
            }
        }
    }

    private class AsyncStreamNSFetchedResultsControllerDelegate: NSObject, NSFetchedResultsControllerDelegate {
        private let continuation: AsyncStream<[Element]>.Continuation

        init(continuation: AsyncStream<[Element]>.Continuation) {
            self.continuation = continuation
        }

        nonisolated func controllerDidChangeContent(_ controller: NSFetchedResultsController<any NSFetchRequestResult>) {
            guard let items = controller.fetchedObjects as? [Element] else { return }
            continuation.yield(items)
        }
    }
}

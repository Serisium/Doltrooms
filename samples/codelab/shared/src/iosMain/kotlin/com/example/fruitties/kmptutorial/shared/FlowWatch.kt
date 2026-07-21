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
package com.example.fruitties.kmptutorial.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Manual Flow-to-Swift bridge, replacing the SKIE interop the codelab's
 * end state relies on (`for await` over DAO Flows): no SKIE release
 * supports Kotlin 2.3.x yet, and this sample must build with the same
 * Kotlin as the doltrooms library it consumes.
 *
 * Swift wraps [watch] in an `AsyncStream` and cancels the returned
 * handle from `onTermination` — see `iosApp/Sources/Repository`.
 * Callbacks are delivered on the main dispatcher.
 */
class FlowWatcher internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

fun <T> watch(flow: Flow<T>, onEach: (T) -> Unit): FlowWatcher = FlowWatcher(
    watchScope.launch {
        flow.collect { onEach(it) }
    },
)

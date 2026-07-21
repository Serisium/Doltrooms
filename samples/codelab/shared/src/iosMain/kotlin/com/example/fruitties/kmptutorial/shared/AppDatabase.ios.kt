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

import androidx.room3.Room
import com.example.fruitties.kmptutorial.android.database.AppDatabase
import dev.seri.doltrooms.driver.DoltLiteDriver
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun getPersistentDatabase(): AppDatabase {
    val dbFilePath = documentDirectory() + "/" + "fruits.db"
    return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
        .setDriver(DoltLiteDriver())
        .build()
}

fun getInMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(DoltLiteDriver())
    .build()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun documentDirectory(): String {
    memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = errorPtr.ptr,
        )
        if (documentDirectory != null) {
            return requireNotNull(documentDirectory.path) {
                """Couldn't determine the document directory.
                  URL $documentDirectory does not conform to RFC 1808.
                """.trimIndent()
            }
        } else {
            val error = errorPtr.value
            val localizedDescription = error?.localizedDescription ?: "Unknown error occurred"
            error("Couldn't determine document directory. Error: $localizedDescription")
        }
    }
}

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

import android.content.Context
import androidx.room3.Room
import com.example.fruitties.kmptutorial.android.database.AppDatabase
import dev.seri.doltrooms.driver.DoltLiteDriver

fun appDatabase(context: Context): AppDatabase {
    val dbFile = context.getDatabasePath("sharedfruits.db")
    // DoltLite reports a missing parent directory only at the first
    // statement step, not at open (doltrooms USAGE.md, divergence #1) —
    // create it eagerly instead of relying on an open-time failure.
    dbFile.parentFile?.mkdirs()
    return Room
        .databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
        .setDriver(DoltLiteDriver())
        .build()
}

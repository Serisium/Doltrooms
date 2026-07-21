/*
 * Copyright 2026 Seri Greenwood
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
package com.example.fruitties.android.database

import androidx.room3.Room
import com.example.fruitties.kmptutorial.android.database.AppDatabase
import com.example.fruitties.kmptutorial.android.model.Fruittie
import dev.seri.doltrooms.dolt.DoltDatabase
import dev.seri.doltrooms.driver.DoltLiteDriver
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Not part of the upstream codelab: demonstrates what running Fruitties
 * on DoltLite buys — Git-style versioning of the app database, driven
 * through the doltrooms `DoltDatabase` helpers over the app's own
 * Room database (full tour: docs/USAGE.md at the doltrooms repo root).
 */
class DoltVersioningTest {

    private lateinit var db: AppDatabase
    private lateinit var dolt: DoltDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(DoltLiteDriver())
            .build()
        dolt = DoltDatabase(db)
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun commit_appearsAtHeadOfLog() = runTest {
        db.fruittieDao().insert(listOf(Fruittie(1L, "Apple", "Alice Apple", "52")))
        val hash = dolt.commit("add Apple")
        val head = dolt.log().first()
        assertEquals(hash, head.hash)
        assertEquals("add Apple", head.message)
    }

    @Test
    fun branchCommitMerge_bringsFeatureCommitToMain() = runTest {
        db.fruittieDao().insert(listOf(Fruittie(1L, "Apple", "Alice Apple", "52")))
        dolt.commit("base")

        dolt.checkout("feature", create = true)
        db.fruittieDao().insert(listOf(Fruittie(2L, "Banana", "Bob Banana", "89")))
        dolt.commit("add Banana on feature")

        dolt.checkout("main")
        val mergedHead = dolt.merge("feature")

        assertTrue(mergedHead.isNotEmpty())
        val messages = dolt.log().map { it.message }
        assertTrue("add Banana on feature" in messages)
    }
}

package dev.seri.doltrooms.dolt

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL

/**
 * Reusable seed data: a film-production database whose COMMIT HISTORY
 * retells the real production timeline of the A24 film **Backrooms**
 * (dir. Kane Parsons, 2026) — announcement, casting, a filming window
 * with two parallel units on branches, a conflicted-then-resolved merge,
 * and the release milestones.
 *
 * Facts are deliberately light (names, dates, places) and were collected
 * 2026-07-22. IMDb blocks anonymous fetches (HTTP 403), so the timeline
 * comes from IMDb's news feed headlines surfaced via search plus the
 * Wikipedia article "Backrooms (film)" and Hollywood Reporter/Deadline
 * coverage: announced 2023-02-06 (Deadline); Ejiofor/Milioti
 * negotiations May 2025; Ejiofor confirmed, Reinsve replacing Milioti,
 * and Will Soodik taking over the script June 2025; supporting cast July
 * 2025; filmed in Vancouver under the working title "Effigy" 2025-07-07
 * to 2025-08-14 on four sound stages (~30,000 sq ft of sets); premiere
 * at the Aero Theatre 2026-05-07; US theatrical release 2026-05-29
 * ($81.4M opening weekend); "Everything Must Go Edition" 2026-07-03;
 * digital release 2026-07-14.
 *
 * Version-control shape produced on the given connection:
 * - One commit per milestone in chronological order, stamped with the
 *   real date via `dolt_commit('--date', ...)` (probed at 0.11.33 —
 *   supported, and `dolt_log.date` echoes the stamp). The `milestones`
 *   table records the same dates relationally.
 * - Two unit branches forked at the filming-begins commit; each logs its
 *   own shoot days and both touch the same `scenes` row (update/update
 *   conflict) AND both mint the same auto-generated `shoot_days` rowid
 *   (insert/insert collision — the probed concurrent-auto-id fact, used
 *   deliberately; every non-colliding row uses explicit ids).
 * - The main unit merges into main as a fast-forward; the second unit's
 *   merge conflicts and is resolved with the documented recipe (KDoc of
 *   [DoltDatabase] and the doltlite skill's version-control-sql
 *   reference): explicit `BEGIN` on the writer connection → `dolt_merge`
 *   throws "Merge has N conflict(s)" but leaves the transaction open →
 *   resolve (row-level DML against `dolt_conflicts_scenes`, wholesale
 *   `dolt_conflicts_resolve('--ours', 'shoot_days')` plus a rekeyed
 *   re-insert) → `COMMIT` → `dolt_commit` creates the merge commit.
 *   Probed nuance (0.11.33): that commit has a SINGLE parent — unlike a
 *   clean three-way merge, the resolved path never records the merged
 *   branch as a second parent (the same holds when `dolt_commit` runs
 *   inside the still-open transaction), so [Refs.secondUnitHead] stays
 *   reachable only through its branch.
 * - The theatrical-release commit is tagged [Refs.releaseTag].
 *
 * The caller owns the connection (`:memory:` or a file path both work —
 * the dolt_* surface is identical); [build] leaves it checked out on
 * `main` with a clean working tree and returns the interesting refs.
 */
object BackroomsProductionFixture {

    const val MAIN_UNIT_BRANCH: String = "unit/stage-a-main"
    const val SECOND_UNIT_BRANCH: String = "unit/stage-b-second"
    const val RELEASE_TAG: String = "theatrical-release"

    /** One milestone commit: [key] names it, [date] is the real ISO date. */
    data class MilestoneCommit(
        val key: String,
        val date: String,
        val message: String,
        val hash: String,
    )

    /** Everything downstream tests need to navigate the seeded history. */
    data class Refs(
        /** Milestone commits, oldest first, one per real production event. */
        val milestones: List<MilestoneCommit>,
        /** The filming-begins commit both unit branches forked from. */
        val branchPoint: String,
        val mainUnitBranch: String = MAIN_UNIT_BRANCH,
        val secondUnitBranch: String = SECOND_UNIT_BRANCH,
        /** Head of the main-unit branch (= main after the fast-forward merge). */
        val mainUnitHead: String,
        /** Head of the second-unit branch (the conflicted side). */
        val secondUnitHead: String,
        /** The resolved merge commit joining both units on main. */
        val mergeCommit: String,
        val releaseTag: String = RELEASE_TAG,
    )

    fun build(conn: SQLiteConnection): Refs {
        val milestones = mutableListOf<MilestoneCommit>()
        var nextMilestoneId = 1

        fun milestone(key: String, date: String, message: String, note: String? = null) {
            conn.prepare("INSERT INTO milestones (id, name, date, note) VALUES (?, ?, ?, ?)").use {
                it.bindLong(1, nextMilestoneId.toLong())
                it.bindText(2, key)
                it.bindText(3, date)
                if (note == null) it.bindNull(4) else it.bindText(4, note)
                it.step()
            }
            nextMilestoneId++
            milestones += MilestoneCommit(key, date, message, conn.commitDated(date, message))
        }

        // --- 2023-02-06: the project is announced --------------------------
        conn.execSQL(
            """
            CREATE TABLE milestones (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                date TEXT NOT NULL,
                note TEXT
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE people (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                department TEXT NOT NULL
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE cast_roles (
                id INTEGER PRIMARY KEY,
                person_id INTEGER NOT NULL REFERENCES people(id),
                character_name TEXT,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE locations (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                city TEXT NOT NULL
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE scenes (
                id INTEGER PRIMARY KEY,
                slug TEXT NOT NULL,
                set_description TEXT,
                location_id INTEGER REFERENCES locations(id),
                status TEXT NOT NULL DEFAULT 'planned',
                note TEXT
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE shoot_days (
                id INTEGER PRIMARY KEY,
                day_date TEXT NOT NULL,
                unit TEXT NOT NULL,
                location_id INTEGER REFERENCES locations(id),
                scene_id INTEGER REFERENCES scenes(id),
                notes TEXT
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE budget_lines (
                id INTEGER PRIMARY KEY,
                category TEXT NOT NULL,
                description TEXT,
                amount_usd INTEGER NOT NULL
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            INSERT INTO people (id, name, department) VALUES
                (1, 'Kane Parsons', 'directing'),
                (2, 'Roberto Patino', 'writing'),
                (3, 'James Wan', 'producing'),
                (4, 'Shawn Levy', 'producing')
            """.trimIndent()
        )
        milestone(
            key = "announcement",
            date = "2023-02-06",
            message = "Announcement (2023-02-06): A24 boards the Backrooms adaptation; " +
                "Kane Parsons to direct from a Roberto Patino script",
            note = "21 Laps / Chernin / Atomic Monster producing; reported by Deadline",
        )

        // --- 2025-05: lead casting negotiations ----------------------------
        conn.execSQL(
            """
            INSERT INTO people (id, name, department) VALUES
                (5, 'Chiwetel Ejiofor', 'cast'),
                (6, 'Cristin Milioti', 'cast')
            """.trimIndent()
        )
        conn.execSQL(
            """
            INSERT INTO cast_roles (id, person_id, character_name, status) VALUES
                (1, 5, 'Clark', 'negotiating'),
                (2, 6, NULL, 'negotiating')
            """.trimIndent()
        )
        milestone(
            key = "casting-begins",
            date = "2025-05-15",
            message = "Casting begins (2025-05-15): Chiwetel Ejiofor and Cristin Milioti " +
                "enter negotiations",
        )

        // --- 2025-06: leads locked, script changes hands -------------------
        conn.execSQL("UPDATE cast_roles SET status = 'confirmed' WHERE id = 1")
        conn.execSQL("DELETE FROM cast_roles WHERE id = 2")
        conn.execSQL("DELETE FROM people WHERE id = 6")
        conn.execSQL("INSERT INTO people (id, name, department) VALUES (7, 'Renate Reinsve', 'cast')")
        conn.execSQL(
            "INSERT INTO cast_roles (id, person_id, character_name, status) " +
                "VALUES (3, 7, 'Dr. Mary Kline', 'confirmed')"
        )
        conn.execSQL("INSERT INTO people (id, name, department) VALUES (8, 'Will Soodik', 'writing')")
        conn.execSQL("UPDATE people SET department = 'writing (early drafts)' WHERE id = 2")
        milestone(
            key = "casting-locked",
            date = "2025-06-15",
            message = "Casting locked (2025-06-15): Ejiofor confirmed as Clark, Renate Reinsve " +
                "joins as Dr. Mary Kline after Milioti departs; Will Soodik takes over the script",
        )

        // --- 2025-07: supporting cast, Vancouver stages, budget ------------
        conn.execSQL(
            """
            INSERT INTO people (id, name, department) VALUES
                (9, 'Mark Duplass', 'cast'),
                (10, 'Finn Bennett', 'cast'),
                (11, 'Lukita Maxwell', 'cast'),
                (12, 'Avan Jogia', 'cast')
            """.trimIndent()
        )
        conn.execSQL(
            """
            INSERT INTO cast_roles (id, person_id, character_name, status) VALUES
                (4, 9, 'Phil', 'confirmed'),
                (5, 10, 'Bobby', 'confirmed'),
                (6, 11, 'Kat', 'confirmed'),
                (7, 12, NULL, 'confirmed')
            """.trimIndent()
        )
        conn.execSQL(
            """
            INSERT INTO locations (id, name, city) VALUES
                (1, 'Sound Stage A', 'Vancouver'),
                (2, 'Sound Stage B', 'Vancouver'),
                (3, 'Sound Stage C', 'Vancouver'),
                (4, 'Sound Stage D', 'Vancouver')
            """.trimIndent()
        )
        conn.execSQL(
            """
            INSERT INTO scenes (id, slug, set_description, location_id, status) VALUES
                (1, 'yellow-hallway', 'Endless yellow-wallpaper hallway run', 1, 'planned'),
                (2, 'furniture-showroom', 'Basement doorway of the furniture showroom', 2, 'planned'),
                (3, 'poolrooms', 'Flooded tile rooms', 3, 'planned'),
                (4, 'therapy-office', 'Office of Dr. Mary Kline', 4, 'planned')
            """.trimIndent()
        )
        conn.execSQL(
            """
            INSERT INTO budget_lines (id, category, description, amount_usd) VALUES
                (1, 'sets', 'Backrooms sets across four sound stages (over 30,000 sq ft)', 2500000),
                (2, 'materials', 'Wallpaper, 37,000 sq ft', 150000),
                (3, 'materials', 'Carpet, 29,000 sq ft', 120000),
                (4, 'above-the-line', 'Director and principal cast', 4000000)
            """.trimIndent()
        )
        milestone(
            key = "supporting-cast",
            date = "2025-07-01",
            message = "Production ramp-up (2025-07-01): Duplass, Bennett, Maxwell and Jogia " +
                "join; four Vancouver sound stages dressed as the Backrooms",
        )

        // --- 2025-07-07: cameras roll (working title "Effigy") -------------
        conn.execSQL("UPDATE scenes SET status = 'in-progress' WHERE id IN (1, 2)")
        conn.execSQL(
            "INSERT INTO shoot_days (id, day_date, unit, location_id, scene_id, notes) VALUES " +
                "(1, '2025-07-07', 'main', 1, 1, 'Day 1 of principal photography, working title Effigy')"
        )
        milestone(
            key = "filming-begins",
            date = "2025-07-07",
            message = "Principal photography begins (2025-07-07) in Vancouver under the " +
                "working title Effigy",
        )
        val branchPoint = milestones.last().hash

        // --- Parallel units: one branch per unit, forked at the same head --
        // Non-colliding rows use explicit ids (100-range for the main unit,
        // 200-range for the second unit). Each branch ALSO inserts one row
        // WITHOUT an id first: both mint rowid 2 (max+1 from the shared
        // branch point) — the probed concurrent-auto-id collision, kept
        // deliberately so the merge below has an insert/insert conflict.
        conn.execSQL("SELECT dolt_checkout('-b', '$MAIN_UNIT_BRANCH')")
        conn.execSQL(
            "INSERT INTO shoot_days (day_date, unit, location_id, scene_id, notes) VALUES " +
                "('2025-07-08', 'main', 1, 1, 'Hallway coverage, auto-keyed slot')"
        )
        conn.execSQL(
            """
            INSERT INTO shoot_days (id, day_date, unit, location_id, scene_id, notes) VALUES
                (101, '2025-07-09', 'main', 1, 1, 'Hallway run, steadicam'),
                (102, '2025-07-10', 'main', 2, 2, 'Showroom basement doorway reveal')
            """.trimIndent()
        )
        conn.execSQL(
            "UPDATE scenes SET status = 'needs-reshoot', " +
                "note = 'Main unit: flicker continuity broke in the hallway run' WHERE id = 1"
        )
        val mainUnitHead = conn.commitDated(
            "2025-07-18",
            "Main unit (Stage A): days 2-4 logged; yellow-hallway flagged for reshoot",
        )

        conn.execSQL("SELECT dolt_checkout('main')")
        conn.execSQL("SELECT dolt_checkout('-b', '$SECOND_UNIT_BRANCH')")
        conn.execSQL(
            "INSERT INTO shoot_days (day_date, unit, location_id, scene_id, notes) VALUES " +
                "('2025-07-08', 'second', 3, 3, 'Poolrooms splinter unit, auto-keyed slot')"
        )
        conn.execSQL(
            """
            INSERT INTO shoot_days (id, day_date, unit, location_id, scene_id, notes) VALUES
                (201, '2025-07-09', 'second', 3, 3, 'Poolrooms plates'),
                (202, '2025-07-10', 'second', 1, 1, 'Hallway inserts and plates')
            """.trimIndent()
        )
        conn.execSQL(
            "UPDATE scenes SET status = 'shot', " +
                "note = 'Second unit: hallway inserts and plates complete' WHERE id = 1"
        )
        val secondUnitHead = conn.commitDated(
            "2025-07-18",
            "Second unit (Stage C): poolrooms days logged; yellow-hallway inserts complete",
        )

        // --- Merge the units back into main --------------------------------
        conn.execSQL("SELECT dolt_checkout('main')")
        // main has not advanced since the branch point, so the main unit
        // merges as a fast-forward (no new commit; main == mainUnitHead).
        conn.scalarText("SELECT dolt_merge('$MAIN_UNIT_BRANCH')")

        // The second unit's merge conflicts on BOTH tables. Under autocommit
        // it would just throw and roll back, so run the documented recipe:
        // explicit transaction, merge throws but stays open, resolve, COMMIT,
        // then dolt_commit finalizes the merge commit.
        conn.execSQL("BEGIN")
        val thrown = runCatching { conn.scalarText("SELECT dolt_merge('$SECOND_UNIT_BRANCH')") }
            .exceptionOrNull()
        check(thrown is SQLiteException && "conflict" in (thrown.message ?: "")) {
            "expected a conflicted merge, got $thrown"
        }
        val conflicted = buildList {
            conn.prepare("SELECT * FROM dolt_conflicts").use { s ->
                while (s.step()) add(s.getText(0))
            }
        }
        check(conflicted.toSet() == setOf("scenes", "shoot_days")) {
            "expected conflicts in scenes + shoot_days, got $conflicted"
        }
        // scenes: row-level resolution — take the second unit's status, keep
        // both units' notes, then clear the conflict rows.
        val (theirStatus, ourNote, theirNote) =
            conn.prepare(
                "SELECT their_status, our_note, their_note FROM dolt_conflicts_scenes"
            ).use { s ->
                check(s.step()) { "expected one conflicted scenes row" }
                Triple(s.getText(0), s.getText(1), s.getText(2))
            }
        conn.prepare("UPDATE scenes SET status = ?, note = ? WHERE id = 1").use {
            it.bindText(1, theirStatus)
            it.bindText(2, "$ourNote | $theirNote")
            it.step()
        }
        conn.execSQL("DELETE FROM dolt_conflicts_scenes")
        // shoot_days: wholesale --ours keeps the main unit's rowid-2 day;
        // re-insert the second unit's colliding day under an explicit id.
        conn.execSQL("SELECT dolt_conflicts_resolve('--ours', 'shoot_days')")
        conn.execSQL(
            "INSERT INTO shoot_days (id, day_date, unit, location_id, scene_id, notes) VALUES " +
                "(203, '2025-07-08', 'second', 3, 3, " +
                "'Poolrooms splinter unit (rekeyed after merge collision)')"
        )
        conn.execSQL("COMMIT")
        val mergeCommit = conn.commitDated(
            "2025-07-21",
            "Merge second unit into main: took second-unit status for yellow-hallway, " +
                "kept both unit notes, rekeyed the colliding shoot day",
        )

        // --- 2025-08-14: wrap ----------------------------------------------
        conn.execSQL("UPDATE scenes SET status = 'completed'")
        milestone(
            key = "filming-wraps",
            date = "2025-08-14",
            message = "Principal photography wraps (2025-08-14) after a five-week " +
                "Vancouver shoot",
        )

        // --- Release milestones --------------------------------------------
        milestone(
            key = "premiere",
            date = "2026-05-07",
            message = "Premiere (2026-05-07) at the Aero Theatre, Santa Monica",
        )
        milestone(
            key = "theatrical-release",
            date = "2026-05-29",
            message = "US theatrical release (2026-05-29)",
            note = "81.4M USD opening weekend",
        )
        conn.execSQL("SELECT dolt_tag('$RELEASE_TAG', '-m', 'US theatrical release')")
        milestone(
            key = "extended-edition",
            date = "2026-07-03",
            message = "Extended cut (2026-07-03): Everything Must Go Edition adds 15 minutes",
        )
        milestone(
            key = "digital-release",
            date = "2026-07-14",
            message = "Digital release (2026-07-14)",
        )

        return Refs(
            milestones = milestones,
            branchPoint = branchPoint,
            mainUnitHead = mainUnitHead,
            secondUnitHead = secondUnitHead,
            mergeCommit = mergeCommit,
        )
    }

    /**
     * Commits the working set stamped with the real milestone date
     * (`--date`, probed as supported at 0.11.33; noon UTC keeps the
     * `dolt_log` date column unambiguous) and returns the new hash.
     */
    private fun SQLiteConnection.commitDated(isoDate: String, message: String): String =
        prepare("SELECT dolt_commit('--date', ?, '-Am', ?)").use {
            it.bindText(1, "${isoDate}T12:00:00")
            it.bindText(2, message)
            it.step()
            it.getText(0)
        }

    private fun SQLiteConnection.scalarText(sql: String): String =
        prepare(sql).use {
            it.step()
            it.getText(0)
        }
}

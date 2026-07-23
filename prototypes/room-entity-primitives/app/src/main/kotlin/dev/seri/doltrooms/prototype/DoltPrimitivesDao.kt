package dev.seri.doltrooms.prototype

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import kotlinx.coroutines.flow.Flow

/**
 * The design's target queries, written as ORDINARY `@Query` functions —
 * no `@SkipQueryVerification` anywhere in this file. They compile only
 * because the verifier shim swaps Room's verification database from
 * stock SQLite to DoltLite (approach D in
 * docs/design/room-entity-dolt-primitives.md).
 */
@Dao
interface DoltPrimitivesDao {

    // ── Target query 1: feature branches for client display ────────────
    @Query("SELECT * FROM dolt_branches WHERE name LIKE :pattern ORDER BY name")
    suspend fun branchesMatching(pattern: String): List<BranchRow>

    // ── Target query 2: ref search across branches + tags ──────────────
    @Query(
        """SELECT name, hash, 'branch' AS kind FROM dolt_branches WHERE name LIKE :pattern
           UNION ALL
           SELECT tag_name AS name, tag_hash AS hash, 'tag' AS kind FROM dolt_tags
             WHERE tag_name LIKE :pattern
           ORDER BY name"""
    )
    suspend fun searchRefs(pattern: String): List<RefRow>

    // ── Target query 3 (amended): history ──────────────────────────────
    // Dolt-proper's dolt_log('<ref>') TVF does NOT exist in DoltLite
    // 0.11.33 (probed: "too many arguments on dolt_log() - max 0"), so
    // per-ref history with metadata is an engine gap. Two verified
    // substitutes: the CURRENT branch's log, and any ref's hash chain
    // walked over the repo-wide dolt_commit_ancestors table.
    @Query("SELECT * FROM dolt_log ORDER BY date DESC")
    suspend fun currentBranchLog(): List<CommitRow>

    @Query(
        """WITH RECURSIVE history(hash) AS (
             SELECT hash FROM dolt_branches WHERE name = :branch
             UNION
             SELECT a.parent_hash FROM dolt_commit_ancestors a
               JOIN history h ON a.commit_hash = h.hash
               WHERE a.parent_hash IS NOT NULL
           )
           SELECT hash FROM history"""
    )
    suspend fun historyHashes(branch: String): List<String>

    // ── Target query 4: typed per-table diff, observable ───────────────
    // The table name is part of the TVF's NAME (not bindable), so the SQL
    // arrives as a RoomRawQuery built by a library-side helper; refs bind
    // normally. observedEntities gives DML-driven re-emission.
    @RawQuery(observedEntities = [Fruittie::class])
    fun diffs(query: RoomRawQuery): Flow<List<FruittieDiffRow>>

    // ── Target query 5: time travel returning the real entity type ─────
    @Query("SELECT * FROM dolt_at_fruittie(:ref)")
    suspend fun fruittiesAt(ref: String): List<Fruittie>

    // ── Target query 6: observable branch list ─────────────────────────
    @Query("SELECT * FROM dolt_branches ORDER BY name")
    fun observeBranches(): Flow<List<BranchRow>>

    // ── Target query 7: verified query against the @DatabaseView ───────
    @Query("SELECT * FROM branches WHERE dirty = 1")
    suspend fun dirtyBranches(): List<Branch>

    // ── Target query 8: JOIN dolt metadata against a user table ────────
    @Query(
        """SELECT f.*, l.commit_hash AS lastCommit FROM fruittie f
           JOIN dolt_log l ON l.commit_hash =
             (SELECT to_commit FROM dolt_diff_fruittie('HEAD~1', 'HEAD') LIMIT 1)"""
    )
    suspend fun fruittiesWithLastCommit(): List<FruittieWithCommit>

    // ── Target query 9: multimap grouping commits under branches ───────
    @Query("SELECT * FROM dolt_branches b JOIN dolt_log c")
    suspend fun commitsByBranch(): Map<BranchRow, List<CommitRow>>

    // ── Status + remotes coverage ──────────────────────────────────────
    @Query("SELECT * FROM dolt_status")
    suspend fun status(): List<StatusRow>

    @Query("SELECT * FROM dolt_remotes")
    suspend fun remotes(): List<RemoteRow>

    // Verified dolt scalar — impossible on stock SQLite without
    // @SkipQueryVerification; proof the shim is doing the verifying.
    @Query("SELECT dolt_version()")
    suspend fun doltVersion(): String

    // ── Target query 10: paged history — Android-only ──────────────────
    // Attempted here (room3-paging 3.0.0 has a jvm variant) and rejected
    // by the Room compiler on this JVM module (recorded 2026-07-22):
    //   "Only suspend functions are allowed in DAOs declared in source
    //    sets targeting non-Android platforms."
    // PagingSource DAO functions are an Android-target feature in Room
    // 3.0.0. Under the verifier shim the QUERY itself verifies fine, so
    // on an Android source set this would be:
    //   @Query("SELECT * FROM dolt_log ORDER BY date DESC")
    //   fun pagedLog(): PagingSource<Int, CommitRow>

    // ── Plumbing for tests ─────────────────────────────────────────────
    @Insert
    suspend fun insert(fruittie: Fruittie): Long
}

/**
 * Negative control for the shim's silent-fallback risk: Room DISABLES
 * verification with only a warning (CANNOT_CREATE_VERIFICATION_DATABASE)
 * if the verifier connection cannot be created, and a green build then
 * proves nothing. To confirm verification is live, temporarily add
 * `abstract fun canaryDao(): CanaryDao` to [PrototypeDatabase] — the
 * build must FAIL with two DoltLite-sourced errors (recorded 2026-07-22,
 * Room 3.0.0 + shim):
 *
 *   "There is a problem with the query: … no such table: table_that_does_not_exist"
 *   "There is a problem with the query: … no such column: definitely_not_a_column"
 *
 * Unattached DAOs are not processed by Room, so this stays green here.
 */
@Dao
interface CanaryDao {
    @Query("SELECT * FROM table_that_does_not_exist")
    suspend fun badTable(): List<String>

    @Query("SELECT definitely_not_a_column FROM dolt_branches")
    suspend fun badColumn(): List<String>
}

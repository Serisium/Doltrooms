package dev.seri.doltrooms.prototype

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import kotlinx.coroutines.flow.Flow

/**
 * Second wave of prop queries: reactive flows over version-control state
 * (via the [DoltEvent] anchor) and filtered reads shaped like the
 * Backrooms production-timeline fixture's data (dated milestone commits,
 * per-unit branches, tags). All @Query functions verify under the shim;
 * the two anchored flows are @RawQuery because their observable table
 * (dolt_event) is not the table they read.
 */
@Dao
interface DoltPropQueriesDao {

    // ── The anchor ─────────────────────────────────────────────────────
    // Bumped (ordinary, trigger-firing DML) after each vcs verb. INSERT
    // OR REPLACE keeps it a single row; verified like any other query.
    @Query("INSERT OR REPLACE INTO dolt_event (id, tick) VALUES (1, :tick)")
    suspend fun bump(tick: Long)

    // ── Reactive: commits on the current branch ────────────────────────
    // Room re-runs the query whenever dolt_event changes; the SQL itself
    // reads dolt_log. Callers pass liveCommitsQuery() below (the SQL is
    // fixed — RawQuery is used purely for its observedEntities escape).
    @RawQuery(observedEntities = [DoltEvent::class])
    fun liveCommits(query: RoomRawQuery): Flow<List<CommitRow>>

    // ── Reactive: branch list ──────────────────────────────────────────
    @RawQuery(observedEntities = [DoltEvent::class])
    fun liveBranches(query: RoomRawQuery): Flow<List<BranchRow>>

    // Negative control for the docs: the VERIFIED flow form — joining the
    // anchor into plain @Query SQL — compiles fine, but Room derives the
    // observed-table set from the parsed FROM clause, so the generated
    // code asks the InvalidationTracker to observe dolt_log too, which is
    // not a Room table. Expected to fail at collection; the test pins the
    // actual behavior.
    @Query("SELECT c.* FROM dolt_log c, dolt_event e ORDER BY c.date DESC")
    fun liveCommitsViaVerifiedJoin(): Flow<List<CommitRow>>

    // ── Filtered reads over the timeline shape ─────────────────────────

    /** All commits by a single author (`dolt_log.committer`). */
    @Query("SELECT * FROM dolt_log WHERE committer = :author ORDER BY date DESC")
    suspend fun commitsByAuthor(author: String): List<CommitRow>

    /** Branches whose name starts with [prefix] (e.g. "2025"). */
    @Query("SELECT * FROM dolt_branches WHERE name LIKE :prefix || '%' ORDER BY name")
    suspend fun branchesWithPrefix(prefix: String): List<BranchRow>

    /**
     * Newest commit on the current branch, if any. Deliberately relies on
     * dolt_log's inherent newest-first TOPOLOGICAL order rather than
     * `ORDER BY date`: timeline fixtures backdate commits with `--date`,
     * and the engine's root commit keeps its creation date, so date order
     * ranks the root above every backdated milestone (observed).
     */
    @Query("SELECT * FROM dolt_log LIMIT 1")
    suspend fun latestCommit(): CommitRow?

    /** Commit count on the current branch — scalar shape. */
    @Query("SELECT COUNT(*) FROM dolt_log")
    suspend fun commitCount(): Long

    /**
     * Commits in a date window, inclusive. Timeline fixtures stamp real
     * milestone dates via dolt_commit('--date', ...), and dolt_log.date
     * is a sortable `YYYY-MM-DD hh:mm:ss` TEXT, so BETWEEN works.
     */
    @Query("SELECT * FROM dolt_log WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun commitsBetween(from: String, to: String): List<CommitRow>

    /**
     * The timeline in true DATE order, root commit excluded. The engine
     * mints "Initialize data repository" at db-creation time and refuses
     * to amend it (probed: "cannot --amend: HEAD has no parent"), so a
     * backdated timeline always carries one modern-dated root that would
     * outrank every milestone under ORDER BY date — filter it out via
     * its NULL parent in the ancestry table.
     */
    @Query(
        """SELECT l.* FROM dolt_log l
           JOIN dolt_commit_ancestors a ON a.commit_hash = l.commit_hash
           WHERE a.parent_hash IS NOT NULL
           ORDER BY l.date DESC"""
    )
    suspend fun timelineByDate(): List<CommitRow>

    /** A tag by exact name, or null. */
    @Query("SELECT * FROM dolt_tags WHERE tag_name = :name")
    suspend fun tagNamed(name: String): TagRow?

    /**
     * Hashes of true merge commits (2+ parents), repo-wide — GROUP BY /
     * HAVING over the ancestry table. (Probed: a RESOLVED conflicted
     * merge has a single parent, so it deliberately won't appear here.)
     */
    @Query(
        """SELECT commit_hash FROM dolt_commit_ancestors
           GROUP BY commit_hash HAVING COUNT(parent_hash) > 1"""
    )
    suspend fun mergeCommitHashes(): List<String>

    /**
     * Per-table change stats between two refs — parameterized TVF
     * (`HEAD~1`-style ancestry refs bind fine). Engine caveat (0.11.33,
     * pinned by the library's DoltReadSurfaceProbeTest): iterating the
     * result fails with "SQL logic error" if the window contains a
     * sqlite_sequence change — i.e. any insert into an AUTOINCREMENT
     * (Room autoGenerate) table. Safe for update/delete-only windows.
     */
    @Query("SELECT table_name, rows_added, rows_deleted, rows_modified FROM dolt_diff_stat(:from, :to)")
    suspend fun diffStat(from: String, to: String): List<DiffStatRow>

    /** Every historical version of every fruittie row on this branch. */
    @Query("SELECT id, name, commit_hash, committer, commit_date FROM dolt_history_fruittie ORDER BY commit_date")
    suspend fun fruittieHistory(): List<FruittieHistoryRow>
}

/**
 * Fixed SQL for [DoltPropQueriesDao.liveCommits]. No ORDER BY: dolt_log
 * is newest-first topologically, which stays correct for backdated
 * (`--date`) timeline commits where date order would not (see
 * [DoltPropQueriesDao.latestCommit]).
 */
fun liveCommitsQuery(): RoomRawQuery =
    RoomRawQuery("SELECT * FROM dolt_log")

/** Fixed SQL for [DoltPropQueriesDao.liveBranches]. */
fun liveBranchesQuery(): RoomRawQuery =
    RoomRawQuery("SELECT * FROM dolt_branches ORDER BY name")

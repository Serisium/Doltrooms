package dev.seri.doltrooms.prototype

import androidx.room3.ColumnInfo
import androidx.room3.DatabaseView
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** The one user table; mirrors the Fruitties sample's shape. */
@Entity(tableName = "fruittie")
data class Fruittie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fullName: String,
    val calories: String,
)

/**
 * The invalidation ANCHOR (design doc §6.1): version-control verbs are
 * not DML on any observed entity, so Room flows cannot see them. This
 * one-row entity is bumped (ordinary DML) after each vcs operation,
 * giving every dolt-reading Flow an observable table via
 * `@RawQuery(observedEntities = [DoltEvent::class])`.
 */
@Entity(tableName = "dolt_event")
data class DoltEvent(
    @PrimaryKey val id: Long = 1,
    val tick: Long,
)

/** `dolt_branches` row (probed schema, DoltLite 0.11.33). */
data class BranchRow(
    val name: String,
    val hash: String,
    @ColumnInfo(name = "latest_committer") val latestCommitter: String,
    @ColumnInfo(name = "latest_committer_email") val latestCommitterEmail: String,
    @ColumnInfo(name = "latest_commit_date") val latestCommitDate: String,
    @ColumnInfo(name = "latest_commit_message") val latestCommitMessage: String,
    val remote: String,
    val branch: String,
    val dirty: Boolean,
)

/** One branch-or-tag hit from the UNION ref search (target query 2). */
data class RefRow(
    val name: String,
    val hash: String,
    val kind: String,
)

/** `dolt_log` row (probed schema, DoltLite 0.11.33). */
data class CommitRow(
    @ColumnInfo(name = "commit_hash") val commitHash: String,
    val committer: String,
    val email: String,
    val date: String,
    val message: String,
)

/** `dolt_status` row. */
data class StatusRow(
    @ColumnInfo(name = "table_name") val tableName: String,
    val staged: Boolean,
    val status: String,
)

/** `dolt_remotes` row. */
data class RemoteRow(
    val name: String,
    val url: String,
    @ColumnInfo(name = "fetch_specs") val fetchSpecs: String,
    val params: String,
)

/** One `dolt_diff_fruittie` row, typed per side (target query 4). */
data class FruittieDiffRow(
    @ColumnInfo(name = "diff_type") val diffType: String,
    @ColumnInfo(name = "to_commit") val toCommit: String,
    @ColumnInfo(name = "from_id") val fromId: Long?,
    @ColumnInfo(name = "from_name") val fromName: String?,
    @ColumnInfo(name = "to_id") val toId: Long?,
    @ColumnInfo(name = "to_name") val toName: String?,
)

/** Fruittie plus the head commit that last touched it (target query 8). */
data class FruittieWithCommit(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "fullName") val fullName: String,
    val calories: String,
    @ColumnInfo(name = "lastCommit") val lastCommit: String,
)

/** One `dolt_tags` row (probed schema). */
data class TagRow(
    @ColumnInfo(name = "tag_name") val tagName: String,
    @ColumnInfo(name = "tag_hash") val tagHash: String,
    val tagger: String,
    val email: String,
    val date: String,
    val message: String,
)

/** One `dolt_diff_stat(from, to)` row — the parameterized diff-stat TVF. */
data class DiffStatRow(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "rows_added") val rowsAdded: Long,
    @ColumnInfo(name = "rows_deleted") val rowsDeleted: Long,
    @ColumnInfo(name = "rows_modified") val rowsModified: Long,
)

/** One `dolt_history_fruittie` row: the entity's columns plus commit metadata. */
data class FruittieHistoryRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "commit_hash") val commitHash: String,
    val committer: String,
    @ColumnInfo(name = "commit_date") val commitDate: String,
)

/**
 * Target query 7: a @DatabaseView over a dolt system table. Compiles only
 * when the verifier knows dolt_branches — i.e. under the verifier shim.
 */
@DatabaseView(
    "SELECT name, hash, dirty FROM dolt_branches",
    viewName = "branches",
)
data class Branch(
    val name: String,
    val hash: String,
    val dirty: Boolean,
)

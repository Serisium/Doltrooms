package dev.seri.doltrooms.dolt

import androidx.room3.RoomDatabase
import androidx.room3.Transactor
import androidx.room3.useWriterConnection

/**
 * Typed helpers over DoltLite's version-control SQL surface for a Room
 * database opened on [dev.seri.doltrooms.driver.DoltLiteDriver].
 *
 * Everything here is plain SQL underneath (`SELECT dolt_commit('-Am', ?)`
 * and friends) issued through Room's sanctioned raw-connection API,
 * [useWriterConnection] — the driver itself adds no version-control API
 * (ARCHITECTURE.md D1). On an engine without the dolt_* surface (e.g.
 * stock SQLite via BundledSQLiteDriver) every helper throws an ordinary
 * [androidx.sqlite.SQLiteException] ("no such function/table").
 *
 * ### Connection semantics — read this before branching
 *
 * DoltLite's checked-out branch is **per-connection session state** (it
 * does not persist across reopen; probed at 0.11.33). All helpers run on
 * the pool's single writer connection, so:
 *
 * - Helper reads ([log], [status], [branches], [diff], [currentBranch])
 *   and DAO **writes** observe the branch checked out via [checkout].
 * - Room's **reader** connections do NOT follow: DAO reads keep seeing
 *   the database's default branch (`main`). After switching branches,
 *   treat DAO query results as main-branch reads unless the work stays
 *   on the writer connection.
 *
 * ### Transactions
 *
 * Helpers deliberately do NOT wrap their SQL in an explicit transaction:
 * `dolt_commit`/`dolt_merge` manage the autocommit transaction
 * themselves, and a `dolt_commit` inside an open `BEGIN` commits and
 * *ends* that transaction (probed at 0.11.33 — a subsequent `COMMIT`
 * fails with "cannot commit - no transaction is active"), which would
 * break `Transactor.withTransaction`'s bookkeeping.
 *
 * A **conflicted** [merge] under autocommit rolls back and throws. To
 * resolve conflicts instead, run the merge inside an explicit
 * transaction on the writer connection (raw SQL, not `withTransaction` —
 * see above): the merge still throws "Merge has N conflict(s)…" but
 * leaves the transaction open with `dolt_conflicts` /
 * `dolt_conflicts_<table>` populated; resolve (e.g.
 * `SELECT dolt_conflicts_resolve('--ours', '<table>')`), `COMMIT`, then
 * [commit].
 *
 * ### dolt_* in DAO queries
 *
 * Room verifies `@Query` SQL against *stock* SQLite at compile time, so
 * any dolt_* reference in a DAO fails compilation with "no such
 * function". Annotate such functions (or the DAO/database) with
 * `@androidx.room3.SkipQueryVerification`:
 *
 * ```
 * @SkipQueryVerification
 * @Query("SELECT dolt_version()")
 * suspend fun doltVersion(): String
 * ```
 *
 * Write-shaped dolt_* calls (`dolt_commit`, …) are writes-in-SELECT and
 * belong on [useWriterConnection] (these helpers), never on `@RawQuery`
 * or a DAO read.
 *
 * Result shapes below were probed against the pinned DoltLite 0.11.33;
 * dolt_* names and columns are version-sensitive.
 */
public class DoltDatabase(private val db: RoomDatabase) {

    /**
     * Commits the working set as a new commit on the current branch and
     * returns its hash. With [stageAll] (default) all changes including
     * new tables are staged first (`dolt_commit('-Am', …)`); otherwise
     * only previously staged changes commit (`-m`).
     *
     * Throws [androidx.sqlite.SQLiteException] "nothing to commit" when
     * the working tree is clean.
     */
    public suspend fun commit(message: String, stageAll: Boolean = true): String =
        writer { conn ->
            conn.usePrepared("SELECT dolt_commit(?, ?)") { stmt ->
                stmt.bindText(1, if (stageAll) "-Am" else "-m")
                stmt.bindText(2, message)
                stmt.step()
                stmt.getText(0)
            }
        }

    /**
     * The commit history of the current branch, newest first. A fresh
     * database ends with DoltLite's own "Initialize data repository"
     * commit.
     */
    public suspend fun log(): List<DoltCommit> =
        writer { conn ->
            conn.usePrepared(
                "SELECT commit_hash, committer, email, date, message FROM dolt_log"
            ) { stmt ->
                buildList {
                    while (stmt.step()) {
                        add(
                            DoltCommit(
                                hash = stmt.getText(0),
                                committer = stmt.getText(1),
                                email = stmt.getText(2),
                                date = stmt.getText(3),
                                message = stmt.getText(4),
                            )
                        )
                    }
                }
            }
        }

    /**
     * The uncommitted working-set state, one entry per changed table
     * (`dolt_status`); empty when the working tree is clean.
     */
    public suspend fun status(): List<DoltStatusEntry> =
        writer { conn ->
            conn.usePrepared("SELECT table_name, staged, status FROM dolt_status") { stmt ->
                buildList {
                    while (stmt.step()) {
                        add(
                            DoltStatusEntry(
                                tableName = stmt.getText(0),
                                staged = stmt.getLong(1) != 0L,
                                status = stmt.getText(2),
                            )
                        )
                    }
                }
            }
        }

    private suspend fun <R> writer(block: suspend (Transactor) -> R): R =
        db.useWriterConnection(block)
}

/**
 * One `dolt_log` entry. [date] is DoltLite's text timestamp
 * (`YYYY-MM-DD hh:mm:ss`, UTC), passed through verbatim.
 */
public data class DoltCommit(
    val hash: String,
    val committer: String,
    val email: String,
    val date: String,
    val message: String,
)

/**
 * One `dolt_status` entry: a table with uncommitted changes. [status] is
 * DoltLite's text ("new table", "modified", …).
 */
public data class DoltStatusEntry(
    val tableName: String,
    val staged: Boolean,
    val status: String,
)

package dev.seri.doltrooms.dolt

import androidx.room3.RoomDatabase
import androidx.room3.Transactor
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

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

    /** The branch checked out on the writer connection (`active_branch()`). */
    public suspend fun currentBranch(): String =
        writer { conn ->
            conn.usePrepared("SELECT active_branch()") { stmt ->
                stmt.step()
                stmt.getText(0)
            }
        }

    /** Creates a branch at the current head without switching to it. */
    public suspend fun branch(name: String): Unit =
        writer { conn ->
            conn.usePrepared("SELECT dolt_branch(?)") { stmt ->
                stmt.bindText(1, name)
                stmt.step()
            }
        }

    /**
     * Deletes a fully merged branch (`dolt_branch('-d', …)`); throws for
     * an unmerged or checked-out branch.
     */
    public suspend fun deleteBranch(name: String): Unit =
        writer { conn ->
            conn.usePrepared("SELECT dolt_branch('-d', ?)") { stmt ->
                stmt.bindText(1, name)
                stmt.step()
            }
        }

    /**
     * Switches the writer connection to [branch], creating it at the
     * current head first when [create] is set (`dolt_checkout('-b', …)`).
     * Reader connections do NOT follow — see the class KDoc.
     */
    public suspend fun checkout(branch: String, create: Boolean = false): Unit =
        writer { conn ->
            val sql =
                if (create) "SELECT dolt_checkout('-b', ?)" else "SELECT dolt_checkout(?)"
            conn.usePrepared(sql) { stmt ->
                stmt.bindText(1, branch)
                stmt.step()
            }
        }

    /** All local branches (`dolt_branches`). */
    public suspend fun branches(): List<DoltBranch> =
        writer { conn ->
            conn.usePrepared(
                "SELECT name, hash, latest_committer, latest_committer_email, " +
                    "latest_commit_date, latest_commit_message, remote, branch, dirty " +
                    "FROM dolt_branches"
            ) { stmt ->
                buildList {
                    while (stmt.step()) {
                        add(
                            DoltBranch(
                                name = stmt.getText(0),
                                hash = stmt.getText(1),
                                latestCommitter = stmt.getText(2),
                                latestCommitterEmail = stmt.getText(3),
                                latestCommitDate = stmt.getText(4),
                                latestCommitMessage = stmt.getText(5),
                                remote = stmt.getText(6),
                                remoteBranch = stmt.getText(7),
                                dirty = stmt.getLong(8) != 0L,
                            )
                        )
                    }
                }
            }
        }

    /**
     * Merges [branch] into the current branch and returns the resulting
     * head hash: the merged branch's head on a fast-forward, or a new
     * merge commit after a clean three-way merge.
     *
     * A conflicted merge under autocommit throws
     * [androidx.sqlite.SQLiteException] ("Merge conflict detected…") and
     * rolls back, leaving the working tree untouched — see the class
     * KDoc for the explicit-transaction resolution recipe.
     */
    public suspend fun merge(branch: String): String =
        writer { conn ->
            conn.usePrepared("SELECT dolt_merge(?)") { stmt ->
                stmt.bindText(1, branch)
                stmt.step()
                stmt.getText(0)
            }
        }

    /**
     * Row-level differences in [table] between two refs (commit hashes,
     * branch names, `HEAD~1`, or the pseudo-refs `WORKING`/`STAGED`),
     * via the generated `dolt_diff_<table>` table-valued function.
     *
     * Each row's [DoltDiffRow.from]/[DoltDiffRow.to] map the table's own
     * columns to values typed by SQLite storage class (INTEGER → [Long],
     * FLOAT → [Double], TEXT → [String], BLOB → [ByteArray], NULL →
     * null); the absent side of an added/removed row is all-null.
     */
    public suspend fun diff(table: String, from: String, to: String): List<DoltDiffRow> =
        writer { conn ->
            // The table name is part of the TVF's NAME, so it cannot be a
            // bound parameter — quote it as an identifier; refs bind normally.
            val tvf = "\"dolt_diff_" + table.replace("\"", "\"\"") + "\""
            conn.usePrepared("SELECT * FROM $tvf(?, ?)") { stmt ->
                stmt.bindText(1, from)
                stmt.bindText(2, to)
                val names = List(stmt.getColumnCount()) { stmt.getColumnName(it) }
                buildList {
                    while (stmt.step()) {
                        var diffType = ""
                        var fromCommit = ""
                        var fromCommitDate = ""
                        var toCommit = ""
                        var toCommitDate = ""
                        val fromValues = mutableMapOf<String, Any?>()
                        val toValues = mutableMapOf<String, Any?>()
                        names.forEachIndexed { i, name ->
                            when {
                                name == "diff_type" -> diffType = stmt.getText(i)
                                name == "from_commit" -> fromCommit = stmt.getText(i)
                                name == "from_commit_date" -> fromCommitDate = stmt.getText(i)
                                name == "to_commit" -> toCommit = stmt.getText(i)
                                name == "to_commit_date" -> toCommitDate = stmt.getText(i)
                                name.startsWith("from_") ->
                                    fromValues[name.removePrefix("from_")] = stmt.typedValue(i)
                                name.startsWith("to_") ->
                                    toValues[name.removePrefix("to_")] = stmt.typedValue(i)
                            }
                        }
                        add(
                            DoltDiffRow(
                                diffType = diffType,
                                fromCommit = fromCommit,
                                fromCommitDate = fromCommitDate,
                                toCommit = toCommit,
                                toCommitDate = toCommitDate,
                                from = fromValues,
                                to = toValues,
                            )
                        )
                    }
                }
            }
        }

    /**
     * Registers [url] as remote [name] (`dolt_remote('add', …)`). Only
     * `file://` and `http(s)://` URLs are supported (probed at 0.11.33:
     * "URL must start with file:// or http://"); a duplicate name throws
     * "remote already exists".
     */
    public suspend fun addRemote(name: String, url: String): Unit =
        writer { conn ->
            conn.usePrepared("SELECT dolt_remote('add', ?, ?)") { stmt ->
                stmt.bindText(1, name)
                stmt.bindText(2, url)
                stmt.step()
            }
        }

    /** Removes remote [name] (`dolt_remote('remove', …)`). */
    public suspend fun removeRemote(name: String): Unit =
        writer { conn ->
            conn.usePrepared("SELECT dolt_remote('remove', ?)") { stmt ->
                stmt.bindText(1, name)
                stmt.step()
            }
        }

    /** All configured remotes (`dolt_remotes`). */
    public suspend fun remotes(): List<DoltRemote> =
        writer { conn ->
            conn.usePrepared("SELECT name, url, fetch_specs, params FROM dolt_remotes") { stmt ->
                buildList {
                    while (stmt.step()) {
                        add(
                            DoltRemote(
                                name = stmt.getText(0),
                                url = stmt.getText(1),
                                fetchSpecs = stmt.getText(2),
                                params = stmt.getText(3),
                            )
                        )
                    }
                }
            }
        }

    /**
     * Pushes [branch] to [remote] (`dolt_push`). Throws
     * [androidx.sqlite.SQLiteException] "remote not found" for an unknown
     * remote and "push failed (not a fast-forward?)" when the remote has
     * commits this database lacks; [force] (`'--force'`) overwrites the
     * remote branch instead. A `file://` remote that does not exist yet
     * is created by the first push.
     */
    public suspend fun push(remote: String, branch: String, force: Boolean = false): Unit =
        writer { conn ->
            val sql =
                if (force) "SELECT dolt_push(?, ?, '--force')" else "SELECT dolt_push(?, ?)"
            conn.usePrepared(sql) { stmt ->
                stmt.bindText(1, remote)
                stmt.bindText(2, branch)
                stmt.step()
            }
        }

    /**
     * Fetches [branch] from [remote] and merges it into the current
     * branch (`dolt_pull`). Fast-forwards when possible; diverged
     * histories produce an automatic merge commit
     * ("Merge branch 'origin/…' …"). A **conflicted** pull behaves
     * exactly like a conflicted [merge]: under autocommit it throws and
     * rolls back, leaving the local branch untouched (see the class KDoc
     * for the explicit-transaction resolution recipe). Pulling with
     * nothing new is a no-op.
     */
    public suspend fun pull(remote: String, branch: String): Unit =
        writer { conn ->
            conn.usePrepared("SELECT dolt_pull(?, ?)") { stmt ->
                stmt.bindText(1, remote)
                stmt.bindText(2, branch)
                stmt.step()
            }
        }

    public companion object {
        /**
         * Clones the DoltLite remote at [url] into a new database file at
         * [path] — including all branches, history, and the remote
         * configuration (the source becomes the clone's `origin`).
         *
         * This is a **pre-Room bootstrap**, not a [DoltDatabase] member:
         * the engine refuses to clone into anything but a fresh database
         * ("clone into a fresh database", probed at 0.11.33), and a
         * `RoomDatabase` is never fresh — Room's schema DDL dirties it at
         * open. Clone first with the bare [driver], then open Room on
         * [path].
         *
         * Blocking; call off the main thread.
         */
        public fun clone(driver: SQLiteDriver, url: String, path: String) {
            val conn = driver.open(path)
            try {
                conn.prepare("SELECT dolt_clone(?)").use { stmt ->
                    stmt.bindText(1, url)
                    stmt.step()
                }
            } finally {
                conn.close()
            }
        }
    }

    private fun SQLiteStatement.typedValue(index: Int): Any? =
        when (getColumnType(index)) {
            SQLITE_DATA_INTEGER -> getLong(index)
            SQLITE_DATA_FLOAT -> getDouble(index)
            SQLITE_DATA_TEXT -> getText(index)
            SQLITE_DATA_BLOB -> getBlob(index)
            else -> null // SQLITE_DATA_NULL
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

/**
 * One `dolt_branches` row. [remote]/[remoteBranch] map the `remote` and
 * `branch` columns (the upstream, empty until remotes are configured);
 * [dirty] reports uncommitted changes on that branch's working set.
 */
public data class DoltBranch(
    val name: String,
    val hash: String,
    val latestCommitter: String,
    val latestCommitterEmail: String,
    val latestCommitDate: String,
    val latestCommitMessage: String,
    val remote: String,
    val remoteBranch: String,
    val dirty: Boolean,
)

/**
 * One `dolt_remotes` row. [fetchSpecs] and [params] pass DoltLite's JSON
 * text through verbatim — a git-style refspec array mapping `refs/heads`
 * to `refs/remotes/<name>`, and `{}`. (The literal refspec glob can't
 * appear here: Kotlin block comments nest, so a slash-star inside KDoc
 * comments out the rest of the file.)
 */
public data class DoltRemote(
    val name: String,
    val url: String,
    val fetchSpecs: String,
    val params: String,
)

/**
 * One row-level difference from `dolt_diff_<table>`. [diffType] is
 * "added", "modified", or "removed"; [from]/[to] hold the table's own
 * column values on each side (see [DoltDatabase.diff] for the value
 * typing; BLOB values compare by reference, so prefer per-key asserts
 * over whole-row equality when blobs are involved).
 */
public data class DoltDiffRow(
    val diffType: String,
    val fromCommit: String,
    val fromCommitDate: String,
    val toCommit: String,
    val toCommitDate: String,
    val from: Map<String, Any?>,
    val to: Map<String, Any?>,
)

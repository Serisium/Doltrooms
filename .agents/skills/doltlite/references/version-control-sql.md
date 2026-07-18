# DoltLite's version-control SQL surface

Verified against the DoltLite README (https://github.com/dolthub/doltlite)
and `doc/doltlite/demo.md`
(https://github.com/dolthub/doltlite/blob/master/doc/doltlite/demo.md)
on 2026-07-17 at version 0.11.33. DoltLite moves fast — re-verify
against the README when precision matters.

Everything is reached through ordinary SQL — functions called with
`SELECT`, plus system/virtual tables read with `SELECT ... FROM`. This
is what makes the Room integration work: DAOs and raw queries can
drive version control with no bridge-level API (ARCHITECTURE.md D1).

## Function-call style: `SELECT`, not `CALL`

Dolt-proper exposes these as MySQL stored procedures
(`CALL dolt_commit(...)`); DoltLite uses SQLite's function-call style:

```sql
select dolt_add('teams', 'employees', 'employees_teams');
select dolt_commit('-m', 'Created initial schema');   -- or dolt_commit('-Am', 'msg')
```

Arguments are git-style string flags, with named args passed as two
sequential strings (`'-m', 'message'`) — the convention copied from
Dolt (https://www.dolthub.com/docs/sql-reference/version-control/dolt-sql-procedures).

## Implemented surface (README enumeration, 0.11.33)

- **Staging/committing:** `dolt_config()`, `dolt_add()`,
  `dolt_commit()`, `dolt_status`, `dolt_workspace_<table>`,
  `dolt_ignore`
- **Branching/tagging:** `dolt_branch()`, `dolt_checkout()`,
  `active_branch()`, `dolt_branches`, `dolt_tag()`, `dolt_tags`
- **Merging/history rewriting:** `dolt_merge()`, `dolt_cherry_pick()`,
  `dolt_rebase()`, `dolt_merge_base()`, `dolt_reset()`, `dolt_revert()`
- **Diff/history:** `dolt_diff()`, `dolt_diff_stat()`,
  `dolt_diff_summary()`, `dolt_schema_diff()`, `dolt_diff_<table>`,
  `dolt_history_<table>`, `dolt_blame_<table>`, `dolt_at_<table>()`
  (time-travel; cross-branch `dolt_at` landed in 0.11.30), `dolt_log`,
  `dolt_schemas`
- **Conflicts:** `dolt_conflicts`, `dolt_conflicts_<table>`,
  `dolt_conflicts_resolve()`, `dolt_constraint_violations`,
  `dolt_constraint_violations_<table>`
- **Utility:** `dolt_hashof()`, `dolt_hashof_table()`,
  `dolt_hashof_db()`, `dolt_gc()`, `dolt_version()`
- **Remotes:** `dolt_remote()`, `dolt_clone()`, `dolt_push()`,
  `dolt_fetch()`, `dolt_pull()`, `dolt_remotes` table — see
  `remotes-and-sync.md`.

## Worked examples (from the launch post and demo doc)

```sql
select dolt_commit('-Am', 'initial data');
select * from dolt_log;
select dolt_branch('modifications');
select dolt_checkout('modifications');
-- ...make changes...
select dolt_commit('-am', 'changes on branch');
select dolt_checkout('main');
select dolt_merge('modifications');

SELECT * FROM dolt_diff_evidence('ref1', 'ref2');   -- per-table diff TVF
SELECT * FROM dolt_at_evidence('HEAD~1');           -- time-travel read
SELECT dolt_conflicts_resolve('--ours', 'evidence');
SELECT dolt_hashof_db();
```

(`evidence` is the demo's table name; the `dolt_diff_<table>` /
`dolt_at_<table>` / `dolt_conflicts_<table>` names are generated per
table.)

## Conflict handling

Merge conflicts surface as data, mirroring Dolt's model
(https://www.dolthub.com/docs/sql-reference/version-control/dolt-system-tables):
`dolt_conflicts` lists tables with conflict counts;
`dolt_conflicts_<table>` exposes per-row `base_*` / `our_*` /
`their_*` columns. Resolve row-by-row with DELETE/UPDATE against that
table, or wholesale with
`SELECT dolt_conflicts_resolve('--ours'|'--theirs', '<table>')`.

## Differences from Dolt-proper worth remembering

- `SELECT dolt_*()` functions instead of `CALL` procedures;
  `dolt_at_<table>()` table-valued functions instead of MySQL
  `AS OF` queries.
- Dolt's long tail is absent or server-oriented: `dolt_backup`,
  branch-per-connection via connection string, stash, undrop,
  reflog-style table functions. Check the README before assuming any
  Dolt feature exists in DoltLite.
- Views/triggers/indexes only began behaving correctly across
  version-control operations in 0.11.33 ("Schema Objects Behave
  Correctly Across Version Control",
  https://github.com/dolthub/doltlite/releases).

## Probed facts (0.11.33, 2026-07-18, via this repo's driver)

Observed empirically with throwaway probe programs against the pinned
amalgamation (PLAN.md Step 7 log) — version-sensitive, re-probe on
upgrade:

- **Return values:** `dolt_commit(...)` returns the new commit hash
  (TEXT); `dolt_merge(branch)` returns the resulting head hash (the
  merged branch's head on fast-forward, a new merge commit after a
  clean three-way merge); `dolt_add`/`dolt_branch`/`dolt_checkout`/
  `dolt_conflicts_resolve` return INTEGER `0`. Errors are ordinary
  SQLite errors (code 1), e.g. "nothing to commit, working tree clean
  (use dolt_add to stage changes)", "no such branch or table: X".
- **Column schemas:** `dolt_log` = commit_hash, committer, email,
  date, message (all TEXT; date `YYYY-MM-DD hh:mm:ss`; a fresh repo
  carries an "Initialize data repository" root commit).
  `dolt_status` = table_name, staged (0/1), status ("new table",
  "modified", …); empty when clean. `dolt_branches` = name, hash,
  latest_committer, latest_committer_email, latest_commit_date,
  latest_commit_message, remote, branch, dirty. `dolt_diff_<table>` =
  to_<col>…, to_commit, to_commit_date, from_<col>…, from_commit,
  from_commit_date, diff_type ("added"/"modified"/"removed"); accepts
  refs, `HEAD~1`, and the `WORKING` pseudo-ref; the TVF name may be
  double-quoted and its ref arguments may be bound parameters.
- **Branch state is per-connection session state.** A checkout affects
  only the issuing connection; other open connections and *new*
  connections stay on / open on `main`, and the checked-out branch
  does NOT persist across a full close+reopen. There is no
  default-branch knob: `dolt_config()` accepts only `user.name` /
  `user.email` ("unknown config key (valid: user.name, user.email)").
- **Transactions:** `dolt_commit` inside an open `BEGIN` succeeds but
  commits and ENDS that transaction — a subsequent `COMMIT` fails
  "cannot commit - no transaction is active". A conflicted
  `dolt_merge` under autocommit throws and rolls back ("Merge conflict
  detected, @autocommit transaction rolled back…"), leaving
  `dolt_conflicts` empty; inside an explicit transaction it throws
  "Merge has N conflict(s). Resolve and then commit with dolt_commit."
  but leaves the transaction OPEN with `dolt_conflicts` /
  `dolt_conflicts_<table>` (base_*/our_*/their_* + our_diff_type/
  their_diff_type/from_root_ish/dolt_conflict_id) populated for
  resolution, after which `COMMIT` + `dolt_commit` complete the merge.
- **`:memory:` databases support the full dolt_* surface** (version
  control is not file-only).
- AUTOINCREMENT bookkeeping (`sqlite_sequence`) shows up as a changed
  table in `dolt_status` and diffs alongside user tables.

## Implication for Room usage

Because everything is `SELECT`-shaped, version control is reachable
three ways from Room: `@Query`-annotated DAO methods (if the Room
compiler accepts the unknown function names — verify against the
`room3` skill), `RoomDatabase.useWriterConnection` +
`SQLiteConnection.prepare`, or raw statements on the driver. The
driver itself adds no version-control API (ARCHITECTURE.md D1).

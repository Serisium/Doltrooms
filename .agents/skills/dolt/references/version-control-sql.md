# Dolt-proper's version-control SQL surface

The spec DoltLite mirrors. Verified 2026-07-17 against
https://www.dolthub.com/docs/sql-reference/version-control/dolt-sql-procedures
and
https://www.dolthub.com/docs/sql-reference/version-control/dolt-system-tables.
When comparing with DoltLite, remember: Dolt uses `CALL dolt_*(...)`
stored procedures and MySQL `AS OF` queries; DoltLite uses
`SELECT dolt_*(...)` functions and `dolt_at_<table>()` TVFs (see the
`doltlite` skill).

## Stored procedures

Args are git-style string flags; named args are two sequential
strings, e.g. `('-m', 'This is a message')`.

| Procedure | Representative syntax | Returns |
|---|---|---|
| `DOLT_ADD` | `CALL DOLT_ADD('-A');` / `CALL DOLT_ADD('t1','t2');` | status int |
| `DOLT_COMMIT` | `CALL DOLT_COMMIT('-a','-m','msg','--author','A <a@x>');` flags: `--allow-empty`, `--skip-empty`, `--amend`, `--date` | commit hash |
| `DOLT_BRANCH` | `CALL DOLT_BRANCH('new');` / `('-c','main','f1')` / `('-d','b')` / `('-m','old','new')` | status int |
| `DOLT_CHECKOUT` | `CALL DOLT_CHECKOUT('-b','new-branch');` / `('branch')` / `('table')` | status, message |
| `DOLT_MERGE` | `CALL DOLT_MERGE('feature','--no-ff','-m','msg');` / `('--abort')`; flags `--squash`, `--ff-only`, `--no-commit` | hash, fast_forward int, conflicts int, message |
| `DOLT_RESET` | `CALL DOLT_RESET('--hard','ref');` (soft by default) | status int |
| `DOLT_REVERT` | `CALL DOLT_REVERT('HEAD~2');` | status int |
| `DOLT_CHERRY_PICK` | `CALL DOLT_CHERRY_PICK('branch~2');` | hash, data_conflicts, schema_conflicts, constraint_violations |
| `DOLT_REBASE` | `CALL DOLT_REBASE('-i','main');` / `('--continue')` / `('--abort')` | status, message |
| `DOLT_CONFLICTS_RESOLVE` | `CALL DOLT_CONFLICTS_RESOLVE('--ours','t1');` / `('--theirs','.')` | status int |
| `DOLT_TAG` | `CALL DOLT_TAG('-m','msg','v1','ref');` / `('-d','v1')` | status int |
| `DOLT_CLONE` | `CALL DOLT_CLONE('dolthub/us-jails','myName');` flags `--branch`, `--depth`, `--single-branch` | status int |
| `DOLT_REMOTE` | `CALL DOLT_REMOTE('add','origin','url');` / `('remove','name')` | status int |
| `DOLT_FETCH` / `DOLT_PULL` / `DOLT_PUSH` | `CALL DOLT_PUSH('origin','main');` flags `--force`, `--prune`, `--set-upstream`, `--user` | status / fast_forward / conflicts / message |
| Others | `DOLT_STASH('push'/'pop'/'drop'/'clear', name)`, `DOLT_CLEAN`, `DOLT_RM`, `DOLT_GC('--shallow')`, `DOLT_BACKUP`, `DOLT_UNDROP`, `DOLT_VERIFY_CONSTRAINTS`, `DOLT_COMMIT_HASH_OUT(@out, ...)` | — |

## System tables (plain `SELECT`)

- **History:** `dolt_log` (commit_hash, committer, date, message),
  `dolt_commits`, `dolt_commit_ancestors`, `dolt_history_<table>`
  (row values at every commit), `dolt_blame_<table>`
- **Diffs:** `dolt_diff` (tables changed per commit),
  `dolt_diff_<table>` (adjacent commits; `from_X`/`to_X` columns +
  `diff_type`), `dolt_commit_diff_<table>` (any two commits),
  `dolt_column_diff`
- **Working set:** `dolt_status` (table_name, staged, status),
  `dolt_merge_status` (is_merging, source, target, unmerged_tables),
  `dolt_workspace_<table>`
- **Conflicts:** `dolt_conflicts` (table, num_conflicts);
  `dolt_conflicts_<table>` — per-row `base_X` / `our_X` / `their_X`
  columns + `dolt_conflict_id`, resolved by DELETE/UPDATE against it
  or `DOLT_CONFLICTS_RESOLVE`; `dolt_schema_conflicts`;
  `dolt_constraint_violations(_<table>)`
- **Refs/config:** `dolt_branches`, `dolt_remote_branches`,
  `dolt_remotes` (name, url, fetch_specs, params), `dolt_tags`,
  `dolt_ignore`, `dolt_stashes`, `dolt_rebase`
- **Functions:** `active_branch()`, `HASHOF()`, `dolt_merge_base()`;
  table functions `DOLT_DIFF()`, `DOLT_LOG()`, `DOLT_PATCH()`,
  `DOLT_DIFF_STAT()`, `DOLT_REFLOG()`, `DOLT_SCHEMA_DIFF()`

## What DoltLite covers (comparison shortcut)

DoltLite implements the core subset — add/commit/branch/checkout/
merge/reset/revert/cherry-pick/rebase, diff and history tables,
conflict tables, clone/push/pull/fetch against DoltLite-native
remotes — while Dolt's long tail (stash, backup, undrop, reflog,
branch-per-connection connection strings) is absent or server-only.
The authoritative implemented list lives in the DoltLite README; the
`doltlite` skill's `references/version-control-sql.md` tracks it.

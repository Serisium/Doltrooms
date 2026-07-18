# DoltLite remotes, doltlite-remotesrv, and sync

Verified 2026-07-17 at DoltLite 0.11.33; "Probed facts" below observed
empirically 2026-07-18 (PLAN.md Step 8). The sync stance for this
project is ARCHITECTURE.md D3: DoltLite's own remote protocol only.

## The SQL surface

```sql
SELECT dolt_remote('add', 'origin', 'file:///path/to/remote.doltlite');
SELECT dolt_push('origin', 'main');
SELECT dolt_clone('file:///path/to/source.doltlite');
SELECT dolt_fetch('origin', 'main');
SELECT dolt_pull('origin', 'main');
```

Remotes are listed in the `dolt_remotes` table. Supported schemes:
**`file://` and `http(s)://` only** (e.g.
`http://myserver:8080/mydb.db`). Remotes shipped in v0.3.0 — the
launch post's "DoltLite is single player today" was superseded two
weeks later ("DoltLite supports file and http remotes and ships with
an http remote server",
https://www.dolthub.com/blog/2026-04-09-improving-doltlite/).

## Probed facts (0.11.33, 2026-07-18, via this repo's driver + release CLI)

Observed empirically with throwaway probes (PLAN.md Step 8 log) —
version-sensitive, re-probe on upgrade:

- **The amalgamation has no TLS/auth.** `doltlite.c` (0.11.33): "The
  credential + TLS stack (ed25519, mbedtls) is compiled everywhere it
  is linked: it is excluded only from the single-file amalgamation,
  which links neither library." An amalgamation-built engine — every
  build of this repo, per ARCHITECTURE.md D9 — accepts only `file://`
  and plain `http://` remote URLs; anything else fails at first use
  (push/fetch/pull/clone, NOT at `dolt_remote('add',…)`) with "failed
  to open remote (URL must start with file:// or http://)". The
  release's PREBUILT binaries (doltlite-tools/-lib) do speak TLS 1.3:
  verified against a `--cert/--key` remotesrv with the
  `DOLTLITE_CA_FILE=<pem>` env var trusting a self-signed cert
  (default trust: the system bundle; `/etc/ssl/cert.pem` and
  `/etc/ssl/ca-bundle.pem` appear in the binaries). An untrusted cert
  surfaces as the *generic* "push failed (not a fast-forward?)".
- **Return values:** `dolt_remote('add'|'remove',…)`, `dolt_push`,
  `dolt_fetch`, `dolt_pull`, `dolt_clone` all return INTEGER `0`;
  errors are ordinary SQLite errors (code 1).
- **Arities:** `dolt_push(remote, branch [, '--force'])` and
  `dolt_pull(remote, branch)` are fixed (wrong arity → usage error);
  `dolt_fetch(remote [, branch])` — one arg fetches all branches.
- **`dolt_remotes`** columns: name, url, fetch_specs (JSON array,
  default `["refs/heads/*:refs/remotes/<name>/*"]`), params (JSON,
  `{}`). Errors: duplicate add "remote already exists", unknown
  subcommand "unknown action: use 'add' or 'remove'", unknown remote
  on push/fetch/pull "remote not found".
- **Push:** the first push to a `file://` URL *creates* the remote
  file; re-push when up-to-date is a no-op; a non-fast-forward push
  fails "push failed (not a fast-forward?)"; `'--force'` overwrites
  the remote branch.
- **Clone** (`dolt_clone(url)`) requires a **fresh database**: any
  uncommitted change → "database has uncommitted changes — clone into
  a fresh database"; any local commit → "database is not empty — …".
  (Hence a Room-opened database can never clone — Room's schema DDL
  dirties it; clone must happen on a raw driver connection before
  Room opens the file.) A clone auto-configures the source as remote
  `origin` and checks out `main`; a missing source → "clone failed".
- **Fetch is what materializes remote-tracking refs:**
  `<remote>/<branch>` is NOT resolvable before the first
  `dolt_fetch` — even immediately after a clone, `dolt_merge
  ('origin/main')` fails "merge source not found"; after a fetch it
  fast-forwards/merges normally.
- **Pull = fetch + merge** with merge's exact transaction semantics:
  diverged-but-disjoint histories auto-merge ("Merge branch
  'origin/main' into main"); a conflicted pull under autocommit
  throws the same "Merge conflict detected, @autocommit transaction
  rolled back…" as `dolt_merge` and leaves the local branch
  untouched; up-to-date pull is a no-op.
- **AUTOINCREMENT is merge-hostile across replicas:** two replicas
  inserting concurrently mint the same rowid, and the identical PK
  with different cell values is a row conflict on pull/merge. With
  distinct explicit ids the same divergence merges cleanly, and
  `sqlite_sequence` itself does not conflict (observed resolving to
  the incoming/larger seq). Syncing apps need collision-free keys
  (UUIDs, explicit ids, per-replica ranges).
- **remotesrv 0.11.33 CLI:** `doltlite-remotesrv [-p PORT] [--bind
  ADDR] [--cert FILE --key FILE] [--auth-keys DIR] [--audience AUD]
  DIRECTORY`; `-p 0` picks a free port; it prints
  `doltlite-remotesrv serving <dir> on http(s)://127.0.0.1:<port>` on
  stdout; pushing to `<base>/<name>.db` creates `<name>.db` in the
  served directory. The binary ships in
  `doltlite-tools-<os>-<arch>-<version>.zip` (with the `doltlite`
  CLI); linux-x64 0.11.33 SHA-256
  `6d9b2353f051ce79d3637d57facae293cacb320cfb5b3eebe896c18af1338932`.

## doltlite-remotesrv

- Build: `make doltlite-remotesrv` in the DoltLite repo
  (https://github.com/dolthub/doltlite).
- Run: `./doltlite-remotesrv -p 8080 /path/to/databases/` — every
  `.db` file in the directory is served as a remote.
- **In-process embedding:** `doltliteServeAsync` in
  `doltlite_remotesrv.h` lets an application host remotes itself —
  relevant if the KMP driver ever wants device-to-device sync.

## Security timeline (get this right — the docs disagree)

1. **2026-03-29** — issue
   https://github.com/dolthub/doltlite/issues/228 described the
   protocol as raw sockets with hand-built HTTP/1.1, **no TLS, no
   auth**, one chunk per request.
2. **2026-07-09** — PR https://github.com/dolthub/doltlite/pull/1585
   merged: "Remote: batch chunk download + pin TLS 1.3 (closes #228)"
   — TLS 1.3 minimum enforced via mbedTLS, batched `/get-chunks`
   (256 hashes/POST). Bearer-JWT authentication landed in an earlier
   stack; release 0.11.28 notes "remote authentication support".
3. **Still true at 0.11.33:** the README continues to carry the stale
   "no authentication … run only on trusted networks" warning, and
   compression remains open
   (https://github.com/dolthub/doltlite/issues/1584).

Practical rule: **require DoltLite ≥ 0.11.28 for any network sync**;
below that, or when in doubt, keep the remotesrv behind a trusted
proxy. Note this repo's `README.md`/`docs/FEASIBILITY.md` were written
against the pre-0.11.28 state. **And the TLS/auth half only applies to
prebuilt artifacts:** amalgamation-built engines (all of this repo's —
D9) carry neither, so their network sync is plain http on trusted
networks regardless of version (see "Probed facts" above).

## No interop with Dolt-proper — verified absence

DoltLite's remote protocol is its own HTTP chunk protocol over its own
single-file chunk store. Dolt-proper uses a Go/noms-based gRPC
remotesapi with DoltHub/AWS/GCS/OCI/SSH/Git backends
(https://www.dolthub.com/docs/sql-reference/version-control/remotes),
and Dolt 2.0 (2026-05) further diverged the storage format with
archive dictionary compression
(https://www.dolthub.com/blog/2026-05-11-dolt-2-dot-0/). Both sides
share the prolly-tree *concept* — content-addressed chunks, sync =
"transfer missing chunks" — but chunk boundaries, hashing,
serialization, and wire protocol all differ.

**No DoltHub blog post, doc, or roadmap issue through 2026-07-17
announces Dolt↔DoltLite sync.** The "enabling local-first use cases
for Dolt" framing in the README is direction, not compatibility. Any
bridge today would be application-level ETL across the SQLite↔MySQL
dialect boundary (ARCHITECTURE.md D2/D3).

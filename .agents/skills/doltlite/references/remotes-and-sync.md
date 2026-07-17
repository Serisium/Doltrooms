# DoltLite remotes, doltlite-remotesrv, and sync

Verified 2026-07-17 at DoltLite 0.11.33. The sync stance for this
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
against the pre-0.11.28 state.

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

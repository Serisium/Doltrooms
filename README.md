# doltlite-room-bridge

An exploration of backing [Room 3](https://developer.android.com/jetpack/androidx/releases/room3)
(Kotlin Multiplatform) with [DoltLite](https://github.com/dolthub/doltlite) — a
SQLite fork with Git-style version control — via a custom
`androidx.sqlite` `SQLiteDriver` implementation.

> Status: research only. Nothing is implemented and this project is not yet a
> git repository. Spun out of feasibility research done in the
> [trinisphere](../trinisphere) project on 2026-07-17.

The founding research is in [docs/FEASIBILITY.md](docs/FEASIBILITY.md). The
one-paragraph version:

Room 3 cannot talk to a Dolt SQL server (MySQL dialect/wire protocol vs.
Room's SQLite-only driver model), and Dolt has no "sqlite mode". But DoltLite
preserves the standard `sqlite3` C API while swapping the storage engine for a
content-addressed prolly tree, and Room 3 accepts pluggable drivers through
three small interfaces. A driver shim linking `libdoltlite` instead of sqlite3
would give KMP apps on Android, iOS, and (speculatively) web a local,
version-controlled database with branch/merge/diff as plain SQL — syncing to a
`doltlite-remotesrv`, not to a Dolt server, which remains the open gap.

## Candidate first milestones

1. JVM proof of concept: implement `SQLiteDriver`/`SQLiteConnection`/`SQLiteStatement`
   over `libdoltlite` via JNI on desktop; run an existing Room 3 test suite
   against it.
2. Android driver using the `com.dolthub:doltlite-android` AAR.
3. iOS driver via Kotlin/Native cinterop against the DoltLite XCFramework.
4. Exercise `dolt_commit`/`dolt_branch`/`dolt_merge` from Room DAOs and
   `useWriterConnection`.
5. Sync experiment against `doltlite-remotesrv` (behind a trusted proxy — the
   remote protocol has no auth/TLS yet).

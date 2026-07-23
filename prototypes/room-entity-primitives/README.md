# room-entity-primitives — design-evidence prototype

Working prototype for `docs/design/room-entity-dolt-primitives.md`
(approach D): Entity-grade, **compile-time-verified** `@Query` /
`@DatabaseView` SQL over DoltLite's version-control read surface
(`dolt_branches`, `dolt_tags`, `dolt_log`, `dolt_at_<table>(ref)`,
`dolt_diff_<table>(from,to)`, …) — no `@SkipQueryVerification` anywhere.

- **`verifier-shim/`** — shadows xerial's `org.sqlite.JDBC` /
  `SQLiteJDBCLoader` / `SQLiteConnection` with a ~200-line JDBC facade
  over the public `DoltLiteDriver`, so Room's `DatabaseVerifier`
  prepares every `@Query` against an in-memory **DoltLite** instead of
  stock SQLite. Goes on the KSP classpath only (with
  `org.xerial:sqlite-jdbc` excluded); never ships in the app.
- **`app/`** — the design's target queries as an ordinary Room DAO
  (`DoltPrimitivesDao`), a `@DatabaseView` over `dolt_branches`, and
  runtime tests on a real DoltLite database. `CanaryDao` documents the
  negative control for Room's silent-fallback risk.

This is a standalone composite build consuming the doltrooms library
from source (same pattern as `samples/codelab`). Run with the ROOT
wrapper:

```
cd prototypes/room-entity-primitives && ../../gradlew :app:test
```

Not a shipping artifact: productization steps live in the design doc §7.

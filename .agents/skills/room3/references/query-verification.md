# Compile-time query verification vs dolt_* functions

Verified 2026-07-17 from `room3-compiler` source on androidx-main
(https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:room3/room3-compiler/src/main/kotlin/androidx/room3/verifier/DatabaseVerifier.kt).

## How verification works

The Room compiler verifies each `@Query` by **preparing it against a
real in-memory stock SQLite** over JDBC:

- `DatabaseVerifier.kt`: `private const val CONNECTION_URL =
  "jdbc:sqlite::memory:"`, using `org.xerial:sqlite-jdbc` (3.50.3.0
  on the current branch — the exact version tracks androidx's
  `libs.versions.toml`; the durable fact is "recent stock SQLite via
  xerial JDBC"). KDoc: "Builds an in-memory version of the database
  and verifies the queries against it."
- `analyze(sql)` calls `connection.prepareStatement(...)`; any
  `SQLException` is reported in `QueryFunctionProcessor.kt` via
  `context.logger.e(...)` → "There is a problem with the query: %s".
  `logger.e` is a **compile error**, not a warning.

**Consequence: a `@Query` containing `dolt_commit(...)` fails
compilation** — stock SQLite raises "no such function: dolt_commit"
at prepare time. (Verified from source, not observed empirically.)

## The escape: @SkipQueryVerification

From `room3-common/…/SkipQueryVerification.kt`, verbatim:

> "Skips database verification for the annotated element. If it is a
> class annotated with [Database], none of the queries for the
> database will be verified at compile time. If it is a class
> annotated with [Dao], none of the queries in the DAO class will be
> verified at compile time. If it is a function in a DAO class, just
> the method's SQL verification will be skipped. … You should use
> this as the last resort if Room cannot properly understand your
> query, and you are 100% sure it works. Removing validation may
> limit the functionality of Room since it won't be able to
> understand the query response."

```kotlin
@SkipQueryVerification
@Query("SELECT dolt_commit('-a', '-m', :msg)")
suspend fun commit(msg: String): String
```

Even skipped, the SQL must parse under Room's embedded ANTLR SQLite
grammar (`room3-external-antlr`); an unknown function name is
ordinary call syntax and should parse. **Caveat:** grammar tolerance
under `@SkipQueryVerification` is inferred from compiler structure
and Room 2 behavior, not doc-quoted — validate with a one-line
compile test early in the JVM PoC (ARCHITECTURE.md D4).

Recommendation for this project: scope the annotation per-function
(keep verification for all ordinary queries), and prefer
`useWriterConnection` for write-shaped `dolt_*` calls anyway
(`raw-connections-and-transactions.md`).

## Environment pitfalls

- The verifier extracts sqlite-jdbc's native library to
  `java.io.tmpdir` (`org.sqlite.tmpdir` overrides). In sandboxed CI
  this can fail and **silently disable verification** with warning
  `CANNOT_CREATE_VERIFICATION_DATABASE` ("Room cannot create an
  SQLite connection to verify the queries. Query verification will
  be disabled.") — don't mistake a green build there for verified
  queries.
- The verifier knows only stock SQLite: custom FTS tokenizers are
  already special-cased out; DoltLite-only constructs will never
  verify — that's what the annotation is for.
- Room 2 and Room 3 can coexist in one project; `DatabaseVerifier`
  even guards against the two versions' JDBC driver registration
  colliding in one Gradle daemon.

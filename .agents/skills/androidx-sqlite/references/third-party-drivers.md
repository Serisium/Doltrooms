# Third-party SQLiteDriver implementations worth studying

Verified 2026-07-17 (GitHub search). Ordered by relevance to this
project.

## powersync-ja/powersync-kotlin — a fork re-skin, our closest cousin

https://github.com/powersync-ja/powersync-kotlin — full KMP driver
over **SQLite3MultipleCiphers** (a SQLite fork), structurally
identical to what a DoltLite driver needs:
`sqlite3multipleciphers/src/*/kotlin/com/powersync/encryption/BundledSQLiteDriver.*`
with its own clone of `jni/sqlite_bindings.cpp` (under
`internal/prebuild-binaries/`), plus
`static-sqlite-driver/…/StaticSqliteDriver.kt`. Best real-world proof
that "re-skin bundled over a fork" works.

## danysantiago/androidx-driver-samples — the maintainer's tutorial

https://github.com/danysantiago/androidx-driver-samples (by the
Room/androidx.sqlite maintainer, pushed 2025-11):
`sqlcipher-driver/src/main/kotlin/org/dany/sqlcipher/driver/SQLCipherDriver.kt`
plus `logging-driver` and `copy-driver` wrapper samples — the closest
thing to an official "write your own driver" guide, including the
delegating-driver pattern useful for tracing/debugging our driver
under Room.

## Others

- **getsentry/sentry-java** —
  `sentry-android-sqlite/…/SentrySQLiteDriver.kt`: minimal
  delegating/wrapping driver. https://github.com/getsentry/sentry-java
- **eygraber/sqldelight-androidx-driver** — SQLDelight driver built
  *on top of* androidx `SQLiteDriver`; consumer-side interop
  reference (our driver would serve SQLDelight users for free).
  https://github.com/eygraber/sqldelight-androidx-driver
- **Desquared/kmp-encrypted-room-database** —
  `SQLCipherNativeDriver.kt`: iOS SQLCipher via cinterop implementing
  `SQLiteDriver`. https://github.com/Desquared/kmp-encrypted-room-database
- Caution: **sqlcipher/sqlcipher-android** implements the older
  `SupportSQLite*` interfaces, not `SQLiteDriver` (verified by code
  search) — use Dany Santiago's SQLCipherDriver sample for the
  driver-API path instead.

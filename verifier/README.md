# :verifier — `dev.seri.doltrooms:doltrooms-verifier`

**Status: SCAFFOLD.** Host-JVM module, KSP classpath only; **never ships in
an app**. Contents land with the read-path implementation
(`docs/design/module-architecture.md`).

The `org.sqlite` shim that swaps Room's verification database to DoltLite, so
Room's compile-time `@Query` verification runs against the real engine's SQL
surface (not stock SQLite).

## Dependencies

- `:driver` (jvm target, host natives).

## Proof gate

The canary: a deliberately-bad query must fail the build with a
DoltLite-sourced error, automated as an expected-failure compile test.

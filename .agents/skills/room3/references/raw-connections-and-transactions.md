# Raw connections, Transactor, and @RawQuery

Verified 2026-07-17 against `room3-runtime` source
(`RoomDatabase.kt`, `Transactor.kt` on androidx-main) and
https://developer.android.com/training/data-storage/room/room-kmp-migration.
This is the sanctioned surface for DoltLite's `dolt_*` SQL from Room
(ARCHITECTURE.md D1).

## API surface (signatures verified in source)

```kotlin
public suspend fun <R> RoomDatabase.useReaderConnection(block: suspend (Transactor) -> R): R
public suspend fun <R> RoomDatabase.useWriterConnection(block: suspend (Transactor) -> R): R
public suspend fun <R> RoomDatabase.withReadTransaction(block: suspend TransactionScope<R>.() -> R): R  // reader + DEFERRED
public suspend fun <R> RoomDatabase.withWriteTransaction(block: suspend TransactionScope<R>.() -> R): R // writer + IMMEDIATE

public interface PooledConnection {
    public suspend fun <R> usePrepared(sql: String, block: suspend (SQLiteStatement) -> R): R
}
public suspend fun PooledConnection.executeSQL(sql: String)  // = usePrepared(sql) { it.step() }

public interface Transactor : PooledConnection {
    public suspend fun <R> withTransaction(type: SQLiteTransactionType, block: suspend TransactionScope<R>.() -> R): R
    public suspend fun inTransaction(): Boolean
    public enum class SQLiteTransactionType { DEFERRED, IMMEDIATE, EXCLUSIVE }
}
public interface TransactionScope<T> : PooledConnection {
    public suspend fun <R> withNestedTransaction(block: suspend TransactionScope<R>.() -> R): R  // SAVEPOINT-based
    public suspend fun rollback(result: T): Nothing
}
// Extensions: Transactor.deferredTransaction { }, immediateTransaction { }, exclusiveTransaction { }
```

## Constraints (KDoc verbatim, RoomDatabase.kt)

> "A [RoomDatabase] will have only one WRITE connection." /
> "one or more READ connections."

> "The connection will be confined to the coroutine on which [block]
> executes, attempting to use the connection from a different
> coroutine will result in an error."

> "If a caller has to wait too long to acquire a connection a
> [SQLiteException] will be thrown due to a timeout."

- Readers: "Only deferred transactions are allowed in reader
  connections. Attempting to start an immediate or exclusive
  transaction in a reader connection will throw an exception"
  (room-kmp-migration page).
- Nested `useWriterConnection` inside a reader context (an upgrade
  attempt) throws `SQLiteException`.
- `useWriterConnection` calls `invalidationTracker.refreshAsync()`
  after the block — Flow observers see raw writes.

## The dolt_commit pattern

```kotlin
db.useWriterConnection { transactor ->
    transactor.immediateTransaction {
        usePrepared("SELECT dolt_add('-A')") { it.step() }
        usePrepared("SELECT dolt_commit('-m', ?)") { stmt ->
            stmt.bindText(1, message)
            stmt.step()
            stmt.getText(0)          // commit hash
        }
    }
}
```

**Do NOT wrap `dolt_commit` in `immediateTransaction`** (settled
empirically at DoltLite 0.11.33, PLAN.md Step 7): `dolt_commit` inside
an open `BEGIN` commits and *ends* that transaction, so the wrapper's
closing `COMMIT` fails with "cannot commit - no transaction is
active", breaking `Transactor.withTransaction` bookkeeping. The plain
`usePrepared` form (no transaction wrapper) is the settled shape —
this repo's `DoltDatabase` helpers use it for every dolt_* call. Also
remember DoltLite branch state is per-connection: only the writer
connection follows a `dolt_checkout`; Room reader connections stay on
the default branch (`doltlite` skill, probed facts).

## @RawQuery — reads only

From `room3-common/…/RawQuery.kt` (androidx-main), verbatim:

> "`@RawQuery` serves as an escape hatch where you can build your own
> SQL query at runtime but still use Room to convert it into
> objects." … "`@RawQuery` functions can only be used for read
> queries, using a write query will lead to undefined behavior. For
> write queries, use [androidx.room3.RoomDatabase.useWriterConnection]".

DAO functions take a `RoomRawQuery(sql, onBindStatement = { … })`;
observability needs `@RawQuery(observedEntities = […])`. Since
`SELECT dolt_commit(...)` is a write-in-select, it belongs on
`useWriterConnection`, not `@RawQuery`.

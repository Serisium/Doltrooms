# Coroutines audit rules

For suspend functions, coroutine scopes, and blocking-call review.
Primary source: Android's coroutines best-practices page
(https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
— framed upstream as adaptable app guidelines; "must" below is the
audit operationalization. Cancellation rules from
https://kotlinlang.org/docs/cancellation-and-timeouts.html.
Verified 2026-07-22 unless marked blog-grade.

## Dispatchers

> "Don't hardcode `Dispatchers` when creating new coroutines or
> calling `withContext`."
> (https://developer.android.com/kotlin/coroutines/coroutines-best-practices)

Inject dispatchers so tests can substitute a `TestDispatcher` — a
default-valued constructor parameter
(`dispatcher: CoroutineDispatcher = Dispatchers.IO`) satisfies the
rule. `Dispatchers.setMain` exists only for Main; injection is the
ONLY test override for IO/Default. **Audit rule:** any
`Dispatchers.IO`/`Default` at a creation or `withContext` call site
in library code is a finding. (This repo's main sources currently
have zero `Dispatchers` references — the suspend surface rides
Room's transactor, whose query context the consumer configures; see
the `room3` skill.)

Kotlin/Native test caveat: "In unit tests, nothing processes the
main thread queue, so don't use Dispatchers.Main unless it was
mocked" via `Dispatchers.setMain` from kotlinx-coroutines-test
(kotlinx.coroutines docs,
https://github.com/Kotlin/kotlinx.coroutines). `Dispatchers.Main` in
Native/iOS test suites without `setMain` is a concrete, greppable
defect.

## Main-safety

> "Suspend functions should be main-safe" — a class doing
> long-running blocking work inside a coroutine "is in charge of
> moving the execution off the main thread using withContext",
> not its callers.
> (https://developer.android.com/kotlin/coroutines/coroutines-best-practices)

Directly applicable here: every JNI/cinterop sqlite call blocks.
Data-layer classes should expose suspend functions for one-shot
operations and Flow for streams (same page). In this repo the
blocking calls execute under Room's connection pool and configured
context — new suspend APIs that bypass Room's transactor must handle
their own main-safety.

## Cancellation

- Cancellation is cooperative
  (https://kotlinlang.org/docs/cancellation-and-timeouts.html): a
  coroutine reacts only at suspension points or explicit checks.
  Long CPU-bound or blocking loops must periodically call
  `ensureActive()`/`isActive`/`yield()` to be cancellable. Blocking
  native calls (JNI/cinterop) never observe cancellation — a
  cancelled Room query still runs its current native call to
  completion; audit long-running `dolt_*` calls (clone, pull, merge)
  with this in mind.
- **Never swallow `CancellationException`:** any `catch` of it — or
  a broad `catch (e: Exception/Throwable)` around suspending code —
  must rethrow it, or cancellation propagation breaks (same page;
  echoed by the Android best-practices page).
- Custom suspending wrappers over callbacks must use
  `suspendCancellableCoroutine`, not `suspendCoroutine` — only the
  former cooperates with cancellation (same page). Relevant to any
  future async bridge over native callbacks.
- Prompt cancellation: a cancelled coroutine resumes with
  `CancellationException` even if a value (e.g. an opened resource)
  is already available — resources acquired across suspension points
  leak unless cleanup is in `finally` (same page).
- Suspending cleanup that must complete after cancellation runs in
  `withContext(NonCancellable)`; `NonCancellable` with
  `launch`/`async` breaks structured concurrency and is a finding
  (same page).

## Structural smells (checkable in review)

Blog-grade but widely corroborated
(https://www.droidcon.com/2024/11/22/top-10-coroutine-mistakes-we-all-have-made-as-android-developers/,
https://krossovochkin.com/posts/2026_01_11_kotlin_coroutines_cancellation_and_exception_handling/,
https://www.thedevtavern.com/blog/posts/structured-concurrency-exceptions-and-cancellations/):

- `GlobalScope` in production code — red flag; coroutines detached
  from any lifecycle.
- `runBlocking` inside a suspend function, or on the main thread —
  design defect. (Test code and `main()` entry points are the
  legitimate homes.)
- `try { launch { ... } } catch` — ineffective; builder exceptions
  don't propagate to the enclosing try. Exceptions surface through
  the job hierarchy instead.
- `async` without `await` (fire-and-forget) — misuse; use `launch`.
- A failing child cancels its siblings under a regular `Job`;
  catching at the `await()` site does not contain it.
  `supervisorScope`/`SupervisorJob` prevents sibling cancellation but
  then requires explicit per-child exception handling.
- Passing a standalone `Job()`/`SupervisorJob()` into a builder to
  change error propagation breaks structured concurrency.
- Shared mutable state across coroutines needs explicit
  synchronization (`Mutex`, atomics, confinement) — coroutines do
  not make access safe by themselves. In this repo, connection
  confinement is Room's job (`SQLITE_THREADSAFE=2`, ARCHITECTURE.md
  D1) — flag any code sharing a `DoltLiteConnection` across
  coroutines without going through Room's pool.

## Testing

Coroutine code is tested with kotlinx-coroutines-test's `runTest`
(virtual time), not ad-hoc `runBlocking` loops;
`TestCoroutineDispatcher`/`runBlockingTest` are deprecated
predecessors — flag them in new tests
(https://github.com/Kotlin/kotlinx.coroutines).

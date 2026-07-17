---
name: red-green-testing
description: Test-first (red/green/refactor) methodology for building new classes and features from scratch in this KMP project. Load before writing any net-new production class — a SQLiteDriver/SQLiteConnection/SQLiteStatement implementation, a per-platform binding, a helper — and when deciding how to sequence tests, whether a test comes before or after code, or how red/green maps to commonTest and differential validation against BundledSQLiteDriver. Covers the red-green-refactor cycle, the three laws of TDD, the test list, seeing the test fail first, minimum-code-to-pass, and refactor-on-green. Triggers: red/green, red-green-refactor, TDD, test-driven development, test-first, write a failing test, unit test first, new class, new feature, build from scratch, how to test, testing methodology.
---

# Red/green testing for new code

## What this skill is

The working discipline for building **new-from-scratch code test-first**
in this repo: the red/green/refactor cycle of test-driven development
(TDD), "often summarized as Red - Green - Refactor"
(https://martinfowler.com/bliki/TestDrivenDevelopment.html). It is the
methodology mandated by ARCHITECTURE.md D7 for any class or feature
written from scratch. Load it before writing a net-new production class —
a `SQLiteDriver`/`SQLiteConnection`/`SQLiteStatement` implementation, a
per-platform binding, a helper — not when editing existing code or
retrofitting tests onto code that already works.

## Role in this project

- **ARCHITECTURE.md D7** settles *that* new classes/features are built
  red/green. This skill is the *how*.
- **D4** makes "running existing Room test suites against the new driver"
  the validation at every platform rung. Those suites already exist — they
  are an acceptance/differential gate, not red-first tests. Red/green
  governs the code the project authors underneath them (driver internals,
  bindings, helpers). The two compose: the differential check (the same
  suite passing against both `BundledSQLiteDriver` and the DoltLite driver
  — the `room3` skill's `references/testing.md`) is itself a red→green
  target at the conformance level.
- **§4 scope.** The current iteration is research; no driver code exists
  yet, so D7 binds the first implementation iteration onward. Do not
  scaffold test infrastructure ahead of that (AGENTS.md working rules).

## The cycle

The mantra is "red/green/refactor, where red means fail and green means
pass" (https://en.wikipedia.org/wiki/Test-driven_development). Every few
minutes, in order:

1. **Red — write one failing test, and watch it fail.** Write the smallest
   test that specifies the next increment of behavior, then run it and
   *see it fail*. "It's very important that each test be run - and shown to
   fail - before the code it is testing is implemented … It's entirely too
   easy to write a test that cannot fail"
   (https://deviq.com/practices/red-green-refactor/). Predict how it should
   fail before running; a surprise means the test or your mental model is
   wrong (https://tdd.mooc.fi/1-tdd/). The first test may not even compile
   because the class it names does not exist yet — that compile error *is*
   the red (https://en.wikipedia.org/wiki/Test-driven_development).
2. **Green — write the minimum code to pass, then stop.** "Make the test
   pass, as simply as possible, and then stop. Avoid the temptation to
   write more code, to anticipate future needs … or to further generalize
   or optimize" (https://deviq.com/practices/red-green-refactor/).
   Hard-coding a return value is acceptable here; the next test forces the
   generalization — "triangulation"
   (https://tdd.mooc.fi/1-tdd/).
3. **Refactor — clean up while green.** Only refactor with all tests
   passing, "that way you'll be certain any tests that break after your
   refactoring are because you screwed up"
   (https://deviq.com/practices/red-green-refactor/). Remove duplication,
   improve names, generalize. Refactoring is continuous — "not something
   you do at the end of the project; it's something you do on a
   minute-by-minute basis"
   (https://blog.cleancoder.com/uncle-bob/2014/12/17/TheCyclesOfTDD).

Repeat until the test list is empty. James Shore's timing: each step is
"about 30 seconds"; "20-40 cycles in an hour is not unreasonable"
(https://www.jamesshore.com/v2/blog/2005/red-green-refactor).

## The three laws (the second-by-second constraint)

The three laws of TDD, from the same essay
(https://blog.cleancoder.com/uncle-bob/2014/12/17/TheCyclesOfTDD):

1. You must write a failing test before you write any production code.
2. You must not write more of a test than is sufficient to fail (a
   compile error counts as failing).
3. You must not write more production code than is sufficient to make the
   one currently failing test pass.

## Start with a test list

Before the first red, "write out a list of test cases first … then pick
one of these tests, apply red-green-refactor to it, and once we're done
pick the next" (https://martinfowler.com/bliki/TestDrivenDevelopment.html).
Keep the list in the test file as TODO comments or a scratch note; add to
it whenever a new case or edge occurs to you
(https://tdd.mooc.fi/1-tdd/). Sequencing the list to drive quickly to the
salient design points is the actual skill.

## How this maps to this repo's KMP layout

- **Where the red test goes.** Put the failing test in `commonTest`
  (`FibiTest.kt` today) — it "runs on every target" (ARCHITECTURE.md
  §3.3), so one red test drives JVM, iOS-simulator, and linuxX64 at once.
  Use plain `kotlin.test` + `runTest` for the suspend DAO/driver calls
  (the `room3` skill's `references/testing.md`).
- **expect/actual seams.** A red test against a `commonMain` API may not
  compile until each platform source set has an `actual`; write the
  minimum `actual` to reach red-then-green, one per target — the same
  shape as the template's `firstElement`/`lastElement` pair
  (ARCHITECTURE.md §3.3).
- **Differential green.** For a driver increment the passing bar can be
  "the same `commonTest` suite passes against both `BundledSQLiteDriver`
  and the DoltLite driver" — the differential-testing pattern in the
  `room3` skill. That diff is your green; a DoltLite fork-divergence (the
  `sqlite-c-api` skill's watchlist) surfaces as a red you did not write.
- **Commit on green.** "Once you've written a failing test and then made
  it pass, it's a good time to make a commit"
  (https://deviq.com/practices/red-green-refactor/) — matches AGENTS.md's
  small, single-topic commits (but never commit/push unless asked).

## When NOT to apply this

Red/green is for **new classes/features written from scratch** (D7's
scope), not a mandate to:

- Retrofit tests onto the template placeholder code (`CustomFibi`,
  `fibiprops.*`) — it is slated for deletion at the first driver iteration
  (ARCHITECTURE.md D5), not test-driven.
- Test-drive a trivial `expect val`/`actual val` property that carries no
  behavior.
- Replace D4's existing Room acceptance suites with hand-written red tests
  — those suites are reused as-is.

When unsure whether a change is "new from scratch," treat it as if it is:
default to writing the failing test first.

## Authoritative URLs

- Martin Fowler, "Test Driven Development":
  https://martinfowler.com/bliki/TestDrivenDevelopment.html
- Robert C. Martin, "The Cycles of TDD" (three laws, red/green/refactor):
  https://blog.cleancoder.com/uncle-bob/2014/12/17/TheCyclesOfTDD
- James Shore, "Red-Green-Refactor":
  https://www.jamesshore.com/v2/blog/2005/red-green-refactor
- DevIQ, "Red, Green, Refactor":
  https://deviq.com/practices/red-green-refactor/
- TDD MOOC, ch. 1 "What is TDD":
  https://tdd.mooc.fi/1-tdd/
- Wikipedia, "Test-driven development":
  https://en.wikipedia.org/wiki/Test-driven_development

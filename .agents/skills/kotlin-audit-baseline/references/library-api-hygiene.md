# Library API hygiene — audit rules for the public surface

Rules for anything `public` in `library/src/*Main`. All claims
verified 2026-07-22 against the cited pages.

## Explicit visibility and explicit types

The Kotlin coding conventions' "Coding conventions for libraries"
section (https://kotlinlang.org/docs/coding-conventions.html):

> "Always explicitly specify member visibility (to avoid accidentally
> exposing declarations as public API)"

> "Always explicitly specify function return types and property types
> (to avoid accidentally changing the return type when the
> implementation changes)"

Google's Android Kotlin style guide independently requires the same
for types (https://developer.android.com/kotlin/style-guide):

> "When writing a library, retain the explicit type declaration when
> it is part of the public API."

**Audit rule:** an implicit-`public` member or an inferred type on a
public declaration is a finding. Mechanical enforcement exists —
Kotlin's Explicit API mode (`explicitApi()` in the `kotlin {}` block)
was designed to enforce exactly these two rules at compile time
(KEEP: https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md).
This repo does not enable it (SKILL.md gap list); until it does,
check by hand.

## KDoc on all public members

JetBrains (https://kotlinlang.org/docs/coding-conventions.html):

> "Provide KDoc comments for all public members, except for overrides
> that do not require any new documentation"

Google (https://developer.android.com/kotlin/style-guide):

> "At the minimum, KDoc is present for every public type, and every
> public or protected member of such a type"

with narrow exceptions (supertype overrides; trivially obvious
members — and the guide explicitly disallows abusing the "obvious"
exception).

**Audit rule:** undocumented public API is a finding. Tool-checkable
via Dokka warnings or detekt's `UndocumentedPublicClass` /
`UndocumentedPublicFunction` (comments ruleset). Additionally,
JetBrains' library-authors' guidelines set a higher bar than
declaration-restating: docs must include examples balancing
explanatory text with code samples
(https://kotlinlang.org/docs/api-guidelines-introduction.html).

## Idioms worth flagging in review

From https://kotlinlang.org/docs/idioms.html and the conventions page:

- Read-only collections (`listOf`, `mapOf`, `List`, `Map`) as the
  default; the conventions page states "Prefer using immutable data
  to mutable" (https://kotlinlang.org/docs/coding-conventions.html).
- Null handling via safe-call `?.` and Elvis `?:` rather than
  explicit `!= null` branching (idioms: "If-not-null shorthand",
  "If-not-null-else shorthand").
- These are idiom preferences, not prohibitions of the alternatives
  — flag deviations only where the idiomatic form is clearly simpler.

Google's mechanical rules — useful as lint defaults, not blockers
(https://developer.android.com/kotlin/style-guide): no wildcard
imports; constants are `UPPER_SNAKE_CASE`, deeply immutable `val`s,
`const` when scalar, top-level or inside an `object`; 100-char column
limit, 4-space indent. (JetBrains' own conventions use a 120-char
line; pick one and keep it consistent — this repo has no formatter
config, so match surrounding code.)

## detekt's libraries ruleset

detekt ships a dedicated ruleset for library maintainers, NOT bundled
by default — it needs the `detekt-rules-libraries` plugin dependency
(https://detekt.dev/docs/rules/libraries/). Its three rules map
directly onto this repo's shape:

- `ForbiddenPublicDataClass` — public `data class` in a library
  compromises binary compatibility (copy/componentN churn);
  `*.internal` packages excluded by default. Note: this repo's
  `DoltCommit`/`DoltBranch`/`DoltStatusEntry`/`DoltRemote`/
  `DoltDiffRow` are public data classes — a deliberate API choice
  that this rule would flag; an audit should record the
  justification (value-semantics result rows) rather than silently
  accept or mechanically reject it.
- `LibraryCodeMustSpecifyReturnType` — explicit return types on
  public API (same rationale as Explicit API mode; requires type
  resolution).
- `LibraryEntitiesShouldNotBePublic` — flags public classes/
  typealiases so authors deliberately choose the public surface.

detekt's docs scope these to library modules only — they are noise
for applications, signal for this repo.

## Binary-compatibility validation

The Kotlin/binary-compatibility-validator Gradle plugin dumps the
public binary API and fails the build when it changes incompatibly
(https://github.com/Kotlin/binary-compatibility-validator):

- `apiDump` writes a human-readable `.api` golden file; `apiCheck`
  verifies against it and wires into `check`. **Audit rule for a
  published library:** `.api` dumps committed and `apiCheck` in CI.
- Non-JVM KMP targets (this repo's iOS/Linux Native) are covered
  only by experimental KLib ABI validation (`klib.enabled`,
  Kotlin ≥ 1.9.20); Apple targets can only be dumped on macOS hosts.
- Exclusion knobs (`ignoredPackages`, `ignoredClasses`,
  `nonPublicMarkers` for `@InternalApi`-style annotations) are
  themselves audit targets — check exclusions are intentional, not
  hiding real public surface.
- The plugin is in maintenance mode; new development moved to the
  experimental ABI validation built into the Kotlin Gradle Plugin —
  prefer the KGP-integrated successor for new setups (same README).

## Library-authors' guidelines (design-level bar)

JetBrains' official guidelines for Kotlin library authors
(https://kotlinlang.org/docs/api-guidelines-introduction.html) are
organized into four areas, each a follow-on page: minimizing mental
complexity, backward compatibility, informative documentation, and
building Kotlin libraries for multiplatform. Design-level audit
criteria drawn from them:

- APIs must be clear, consistent, predictable, easy to debug.
- A new version keeps the existing API operational; breaking changes
  are communicated well in advance with a gradual migration path
  (https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html).
- Design the API to work in both common and platform-specific code
  across all supported targets — directly relevant to this repo's
  common `DoltLiteDriver`/`DoltDatabase` surface.

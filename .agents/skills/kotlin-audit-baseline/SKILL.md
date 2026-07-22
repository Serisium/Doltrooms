---
name: kotlin-audit-baseline
description: Kotlin audit baseline for this KMP library — verified (2026-07-22) coding-convention, API-hygiene, KMP/interop, and coroutines rules to check when reviewing or writing Kotlin here. Use when auditing or reviewing Kotlin code, preparing a code review, enabling or debating detekt/ktlint/explicitApi()/binary-compatibility-validator, checking expect/actual usage, cinterop safety (@ExperimentalForeignApi, reinterpret, memScoped, nativeHeap), the Kotlin/Native memory model, JNI binding hygiene, coroutines discipline (Dispatchers injection, main-safety, CancellationException, GlobalScope, runBlocking), KDoc coverage of public API, or writing AI-agent guidelines (AGENTS.md conventions). Triggers - kotlin audit, code review, coding conventions, style guide, explicit API mode, detekt, ktlint, Konsist, binary compatibility, apiDump, expect actual, cinterop, ExperimentalForeignApi, memScoped, nativeHeap, freeze, structured concurrency, dispatcher, cancellation, KDoc, library API design, api-guidelines, agent guidelines.
---

# Kotlin audit baseline for doltrooms

## What this skill is

A checkable baseline of Kotlin best-practice rules for auditing code
in this repo, assembled 2026-07-22 from primary sources (JetBrains
Kotlin docs, Google's Android Kotlin style guide, official tool
repos) via an adversarially-verified research pass — 23 sources
fetched, 25 top claims verified by 3-vote panels, 23 confirmed. Each
rule below cites its source inline; quotes are verbatim. Rules from
official docs are mostly framed upstream as *recommendations*
("we recommend", "should"), so treat every rule as a checkable
convention with justified-exception allowances, not a hard mandate.

## Role in this project

This library is a published KMP artifact (a custom `androidx.sqlite`
driver, ARCHITECTURE.md D1) with a hand-maintained `public` API
surface, expect/actual platform splits, JNI on JVM/Android and
cinterop on iOS/Native (D5, D9). The audit rules here complement the
contract-level skills (`androidx-sqlite`, `sqlite-c-api`,
`kmp-native-interop`, `room3`): those say what the code must *do*;
this one says what well-written Kotlin *looks like* while doing it.

## Audit decision tree

Reviewing or writing code that touches the **public API surface**
(anything `public` in `library/src/*Main`):
→ `references/library-api-hygiene.md` — explicit visibility/types,
KDoc coverage, detekt's libraries ruleset, Explicit API mode,
binary-compatibility validation, JetBrains library-authors'
guidelines.

Reviewing **source-set structure, expect/actual, cinterop, or JNI
code**:
→ `references/kmp-interop-audit.md` — interfaces-over-expect/actual,
Beta status of expect/actual classes, platform file-suffix naming,
`@ExperimentalForeignApi` as a greppable surface map, `.reinterpret`
casts, `memScoped` vs `nativeHeap` lifetimes, the current
Kotlin/Native memory model, JVM JNI hygiene.

Reviewing **suspend functions, coroutine scopes, or anything that
blocks** (all JNI/cinterop calls block):
→ `references/coroutines-audit.md` — dispatcher injection,
main-safety, cooperative cancellation, CancellationException
handling, GlobalScope/runBlocking/async smells, shared mutable
state, test discipline.

Writing or updating **guidelines for AI agents** (AGENTS.md, skills):
→ `references/agent-guidelines-landscape.md` — the AGENTS.md
convention, JetBrains' four-part guideline template, what official
Kotlin agent-skill material exists (and what does not).

## Repo status against this baseline (observed 2026-07-22)

Conforming today:

- Platform file suffixes follow the convention
  (`DoltLiteDriver.native.kt`, `DoltLiteDriver.jvmAndroid.kt`,
  `loadNativeLibrary.android.kt`; unsuffixed files in `commonMain`).
- Public API carries hand-written `public` modifiers and KDoc on
  members (`DoltLiteDriver.kt`, `DoltDatabase.kt`).
- Main sources contain no `Dispatchers.*`, `GlobalScope`,
  `runBlocking`, or `withContext` — the suspend surface delegates to
  Room's transactor, whose query context is caller-configurable (see
  the `room3` skill).
- JNI methods bind via `RegisterNatives` from `JNI_OnLoad`
  (`DoltLiteNative.kt`), so signature mismatches fail at load time.
- expect/actual classes are used deliberately, mirroring
  `androidx.sqlite`'s own expect-class driver contract
  (`androidx-sqlite` skill), with `-Xexpect-actual-classes` set —
  the Beta-status caveat in `kmp-interop-audit.md` applies.

Gaps (candidate improvements — tooling additions are new work and
need a human-opened iteration per ARCHITECTURE.md §4; suggest, don't
implement unasked):

- No `explicitApi()` mode: the `public` discipline is manual and
  unenforced.
- No detekt (including its opt-in `libraries` ruleset), no ktlint.
- No binary-compatibility validation (`apiDump`/`apiCheck` golden
  files) despite being a published library.
- No Dokka-warning or KDoc-coverage gate in CI.

## When to load reference files

- Public API / KDoc / tooling questions:
  `references/library-api-hygiene.md`
- expect/actual, cinterop, memory model, JNI:
  `references/kmp-interop-audit.md`
- Coroutines and blocking-call review:
  `references/coroutines-audit.md`
- Agent-guidelines authoring or comparison:
  `references/agent-guidelines-landscape.md`

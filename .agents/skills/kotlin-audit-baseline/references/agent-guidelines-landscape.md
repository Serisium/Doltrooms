# The AI-agent Kotlin-guidelines landscape

What first-party material exists for feeding Kotlin guidelines to AI
coding agents, and what does not. Snapshot verified 2026-07-22; both
cited repos are active — re-verify before relying on the negative
findings.

## The AGENTS.md convention (JetBrains-supported)

JetBrains' officially supported mechanism for project guidelines to
its Junie agent is the AGENTS.md convention: `.junie/AGENTS.md` or
root `AGENTS.md`, or a custom path in IDE settings;
`.junie/guidelines.md` is the legacy format
(https://junie.jetbrains.com/docs/guidelines-and-memory.html,
https://github.com/JetBrains/junie-guidelines). This repo's root
`AGENTS.md` already matches the convention.

JetBrains' recommended guideline-file structure (community-driven,
explicitly modifiable template — May 2025 blog,
https://blog.jetbrains.com/idea/2025/05/coding-guidelines-for-your-ai-agents/):

1. Preferred coding styles and conventions
2. Dos and don'ts — best practices vs anti-patterns
3. Common gotchas
4. Real-world code examples with explanations

Useful as a completeness check when editing this repo's `AGENTS.md`
or authoring skills: does the doc cover all four?

## What official material does NOT exist (negative findings)

Verified by recursive git-tree listings on 2026-07-22:

- **JetBrains/junie-guidelines contains no Kotlin guidelines** —
  only go/gin, java, python/django, typescript/vue/nuxt. Two claims
  that JetBrains publishes Kotlin-specific Junie guidelines were
  adversarially refuted (0-3 and 1-2 votes). Do not cite that repo
  or the May 2025 blog as a source of Kotlin agent rules.
- **Kotlin/kotlin-agent-skills** (official, Apache-2.0, follows the
  agentskills.io standard — the same format this repo's skills use)
  contains only six narrow task/migration skills
  (https://github.com/Kotlin/kotlin-agent-skills): JPA entity
  mapping, AGP9 migration, CocoaPods→SPM migration,
  immutable-collections 0.5.x migration, Java→Kotlin conversion,
  Native build performance — under a `kotlin-<category>-<name>`
  naming convention. No general coding-conventions or audit skill.

Consequence: an audit baseline must be assembled from the official
conventions/docs (as this skill does), not adopted from an existing
first-party agent-rules file.

## Reusable pieces from the official skills

From Kotlin/kotlin-agent-skills
(https://github.com/Kotlin/kotlin-agent-skills):

- The `kotlin-tooling-java-to-kotlin` skill's checklist doubles as
  JVM-surface audit rules: an explicit "Nullability & mutability
  audit" step, and a Common Pitfalls list — platform types from Java
  interop, SAM conversion ambiguity, `@JvmStatic`/`@JvmField`/
  `@JvmOverloads`/`@Throws` usage. (It is a conversion skill; the
  pitfalls are repurposed here as audit checks.)
- The `kotlin-tooling-native-build-performance` skill audits
  Kotlin/Native build health: disabled caches, overly broad link
  tasks, `transitiveExport`, KSP on the native path — applicable to
  this repo's iOS/Native targets.
- Transferable verification principle encoded in those skills:
  measured before/after comparisons, and "never trade away required
  release behavior" — a local speedup must not change what CI
  produces.

## Community baseline (blog-grade)

https://github.com/mmiani/kotlin-kmp-claude-agent-skills packages
KMP guidance as Claude agent skills with a validation pipeline that
gates agent output on compile + detekt + tests — corroborating
detekt as the standard mechanical gate for agent-written Kotlin. Its
KMP rules (narrow expect/actual, interfaces for complex
abstractions, shared code as high in the source-set hierarchy as
valid) restate the official positions cited in
`kmp-interop-audit.md`; cite those primary sources, not this repo.

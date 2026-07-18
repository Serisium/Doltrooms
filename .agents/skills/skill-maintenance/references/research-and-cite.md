# Research and citation conventions

Detailed workflow for verifying a load-bearing claim against official documentation and writing the citation back into a SKILL.md or `references/<topic>.md`.

## Prompt templates for `WebFetch`

The model behind `WebFetch` summarises by default. Force it to quote by writing the prompt as a request for verbatim text. Templates that work well:

### Library API surface

> "Quote the exact Kotlin declaration of `<symbol>` as published in `<artifact>`. Quote any version notes about when it was added, deprecated, or renamed. Do not paraphrase."

Example: for the `androidx-sqlite` skill, ask the API reference (or the androidx source on GitHub/cs.android.com) to "quote the exact interface declaration of `SQLiteStatement` including every member". Paraphrased method lists are how signature drift starts.

### C API behaviour

> "Quote the documentation's exact wording about what `<sqlite3_function>` returns when `<condition>`. Do not paraphrase."

sqlite.org's c3ref pages are stable and quotable; the verbatim sentence about, e.g., `SQLITE_ROW`/`SQLITE_DONE` is what belongs in the skill.

### Capability / stability facts

> "Does `<library>` support `<feature>`? Quote the README or documentation verbatim, including any warnings or caveats."

Used for DoltLite claims (storage-format stability, remote protocol auth/TLS status). For an alpha project, also record the version the claim was verified against — the claim may be false one release later.

### CLI flags / Gradle DSL

> "Confirm whether `<flag or DSL block>` exists, what it does, and which version added it. Quote exact text."

## When to drop down to source

Public docs are sometimes thin or out of date. When the doc page does not describe the feature you are documenting, fetch the **version-pinned source** on GitHub:

```
https://github.com/<org>/<repo>/blob/<tag>/<path-in-repo>
```

Pin to the version this repo uses (see `gradle/libs.versions.toml` once dependencies exist; until then, pin to the latest stable tag and say so). For androidx, `cs.android.com` and the `androidx/androidx` GitHub mirror are the sources of truth; for DoltLite, the repo README and headers are the only documentation there is.

Source-derived citations should still cite the URL inline. The pinned version in the URL is part of the citation — it freezes the claim against future edits.

## Citation style

Three inline patterns, plus one for repo documents:

### Pattern A — first-mention parenthetical

Use at the first mention of the library in the SKILL.md body:

```markdown
DoltLite (https://github.com/dolthub/doltlite, launch post at
https://www.dolthub.com/blog/2026-03-25-doltlite/) is a SQLite fork...
```

Multiple URLs in one parenthetical is fine when they are complementary (repo + key blog post + doc page).

### Pattern B — at-the-claim parenthetical

Use at the point of any load-bearing claim:

```markdown
`sqlite3_bind_*` indices are **1-based** while `sqlite3_column_*`
indices are 0-based (https://www.sqlite.org/c3ref/bind_blob.html).
```

The URL is on the same sentence as the claim it backs. A reader who doubts the sentence can click the link and verify in seconds.

### Pattern C — verbatim blockquote with citation

For exact strings (signatures, warnings, error messages), quote verbatim and cite on the adjacent line:

```markdown
The DoltLite README (https://github.com/dolthub/doltlite) warns:

> "<exact warning text>"
```

### Pattern D — repo documents, by decision id or section

When a claim rests on this repo's own architecture, cite `ARCHITECTURE.md` by decision id or section instead:

```markdown
The driver never exposes bridge-level version-control APIs
(ARCHITECTURE.md D1); the web target must not be scaffolded
(ARCHITECTURE.md D4, §4).
```

**Never cite a repo document by file:line.** Line numbers are brittle; decision ids (D1–D8) and section numbers (§1–§4) survive edits.

## What never to do

- **Do not write a `## Sources` footer.** This repo's convention is inline-only. Footers and inline citations together is two places to keep in sync. (A closing "Authoritative URLs" routing list of anchor pages is allowed; it is navigation, not citation.)
- **Do not invent URLs.** If `WebFetch` failed or returned 404, write that in the body instead of fabricating a link.
- **Do not cite the homepage as proof of a specific claim.** Pick the most-specific URL that supports the claim.
- **Do not paraphrase a quote and keep the citation.** Either quote verbatim (and keep the cite), or paraphrase and drop the cite — never paraphrase under the guise of a quote.
- **Do not answer from memory about Room 3 or DoltLite.** Both shipped in March 2026, at or past most training cutoffs; memory-derived "facts" about them are the highest-risk drift source in this repo.

## Re-grounding cadence

Skills do not need re-grounding on a fixed schedule. Re-ground when:

- A pinned library version changes (a DoltLite version bump can invalidate storage-format, sync, and artifact claims wholesale — it is alpha).
- A reader points out that a SKILL.md claim contradicts what they observe.
- You are about to make a change that depends on the claim and want to be sure.

A re-grounding pass is the same workflow as initial authoring: fetch, quote, cite, sync `AGENTS.md` only if the public surface of the skill changed.

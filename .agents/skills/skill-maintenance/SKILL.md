---
name: skill-maintenance
description: Workflow for keeping doltlite-room-bridge skills aligned with official docs and the Agent Skills format. **Auto-load whenever ANY skill is created, modified, renamed, moved, or deleted** (any file under `.agents/skills/`), and whenever WebFetch or WebSearch is used on a project library — Room 3, androidx.sqlite, Kotlin Multiplatform, Kotlin/Native cinterop, SQLite, DoltLite, Dolt, doltlite-remotesrv. Summarizes Anthropic's level 1/2/3 progressive-disclosure guidance and the agentskills.io open specification (SKILL.md frontmatter fields, directory layout, .agents/skills/ location convention, skills-ref validation). Captures the verify→quote→cite→sync→validate workflow: fetch authoritative docs, quote load-bearing claims verbatim, add inline URL citations, sync AGENTS.md's skills list, run skills-ref validate. Triggers: skill maintenance, SKILL.md, agent skills spec, agentskills.io, skills-ref, progressive disclosure, citation sync, skill drift, AGENTS.md sync.
---

# Skill maintenance for doltlite-room-bridge

## What this skill is

A documented routine for the meta-task of authoring and maintaining the `.agents/skills/` tree itself. It exists because the SKILL.md / reference files in this repo make load-bearing factual claims (API signatures, artifact coordinates, version-specific behaviour) that go stale as upstream libraries evolve. Without a discipline for re-grounding, the skills drift from reality and start misleading future agents.

This danger is acute here: the two libraries at the heart of this project — **Room 3** (stable March 2026) and **DoltLite** (launched March 2026) — postdate most models' training cutoffs, and DoltLite is alpha software whose surface changes release to release. Claims about them must come from fetched documents, never from memory.

## When to invoke this skill

**Always** when one of these conditions fires:

1. You are about to create, edit, rename, move, or delete **any** file under `.agents/skills/` — including adding a new skill directory.
2. You are about to call `WebFetch` or `WebSearch` against any library this repo uses — Room 3, androidx.sqlite, Kotlin Multiplatform / Kotlin/Native, SQLite, DoltLite, Dolt.
3. You notice a SKILL.md claim that looks unverified ("from memory", paraphrased, no URL) and you suspect it might be wrong.

In all of these, read this SKILL.md first (you are doing that now), then proceed.

## The format contract

Every skill must conform to the Agent Skills format — Anthropic's
level 1/2/3 progressive-disclosure model and the vendor-neutral
agentskills.io specification. The short version:

- **Level 1**: frontmatter `name` (≤64 chars, lowercase/digits/hyphens,
  must match the directory name) + `description` (≤1024 chars, says
  what the skill does *and* when to use it, keyword-packed). This is
  all the matcher ever sees at startup.
- **Level 2**: the SKILL.md body — a decision tree under 500 lines
  (< 5k tokens) that routes to deeper files rather than inlining them.
- **Level 3**: `references/` (and optionally `scripts/`, `assets/`)
  loaded only when routed to; relative paths from the skill root, one
  level deep.

Skills live in `.agents/skills/` — the cross-client location
convention. Full field tables, constraints, and the loading model:
`references/agent-skills-spec.md`.

## The five-step workflow

### Step 1 — Identify the load-bearing claims

A *load-bearing claim* is a sentence the reader will rely on to make a decision. For library skills these are typically:

- API names and signatures (e.g. `SQLiteConnection.prepare`, `sqlite3_prepare_v2`, `staticCFunction`).
- Artifact coordinates and versions (e.g. the exact Maven coordinates of `androidx.sqlite:sqlite-bundled` or the DoltLite Android AAR).
- "Does X support Y?" — boolean facts about library capability (does Room's compiler accept unknown SQL functions; does DoltLite support WAL).
- Version-specific behaviour and stability caveats (DoltLite's storage-format warning, the remote protocol's missing auth/TLS).
- Verbatim error-message strings the agent will grep for.

Non-load-bearing prose ("SQLite is a C library") does not need a citation.

### Step 2 — Fetch authoritative docs

Per-library starting points for `WebFetch`:

| Library | Anchor URL |
|---|---|
| Room 3 | `https://developer.android.com/jetpack/androidx/releases/room3`, `https://developer.android.com/kotlin/multiplatform/room` |
| androidx.sqlite | `https://developer.android.com/kotlin/multiplatform/sqlite`, `https://developer.android.com/reference/kotlin/androidx/sqlite/package-summary`, source at `https://github.com/androidx/androidx/tree/androidx-main/sqlite` |
| Kotlin Multiplatform / Native | `https://kotlinlang.org/docs/multiplatform.html`, `https://kotlinlang.org/docs/native-c-interop.html`, `https://kotlinlang.org/docs/native-definition-file.html` |
| SQLite C API | `https://www.sqlite.org/c3ref/intro.html`, per-function pages `https://www.sqlite.org/c3ref/<function>.html` |
| DoltLite | `https://github.com/dolthub/doltlite` (README is primary), `https://www.dolthub.com/blog/` |
| Dolt | `https://docs.dolthub.com/`, `https://github.com/dolthub/dolt` |

When the public docs are thin (androidx KDoc often lags the source), drop down to the **version-pinned source** on GitHub or cs.android.com: `https://github.com/<org>/<repo>/blob/<tag>/<path>`. Pin to the version this repo uses (the version catalog in `gradle/libs.versions.toml`).

Phrase the WebFetch prompt as a request for **quoted text and signatures**, not a paraphrase: "Quote the exact Kotlin declaration of X" / "Quote the README's exact wording about Y". Paraphrased fetches reintroduce the same drift you are trying to remove.

See `references/research-and-cite.md` for prompt templates and citation patterns.

### Step 3 — Patch the skill

For each verified claim:

- If the existing SKILL.md text is correct, **add an inline citation**: a parenthetical URL at the point of the claim, e.g. `(https://www.sqlite.org/c3ref/step.html)`.
- If the existing text is wrong, rewrite it and add the citation.
- For exact strings (signatures, coordinates, stability warnings), **quote verbatim** with `> blockquote` syntax and cite the URL on the same line.

Citation convention: inline parenthetical URLs at first mention of each library and at the point of each load-bearing claim. **Never** add a "## Sources" footer — the citations live next to the claims they support. (The "Authoritative URLs" closing section of a skill is a routing list of anchor URLs, not a citation footer — claims still cite inline.)

Repo-doc citations follow a different convention: cite `ARCHITECTURE.md` by decision id or section — `(ARCHITECTURE.md D1)`, `(ARCHITECTURE.md §4)` — **never by file:line**. Line numbers are brittle and go stale on the next edit; decision ids and section numbers survive rewrites.

Do not invent URLs. If WebFetch failed, say so in the body ("URL X returned 404 at time of writing") rather than fabricating a link.

### Step 4 — Sync `AGENTS.md`

`AGENTS.md`'s `## Skills` section is a flat bullet list — one `- [`name`](.agents/skills/<name>/SKILL.md) — <one-line summary>` bullet per skill. For *any* change that adds, removes, or renames a skill:

- Add, fix, or delete the skill's bullet.
- Keep the one-line summary specific — name the files / APIs / concerns that should make a reader load it.

For changes that only modify content inside an existing skill, `AGENTS.md` does not usually need to change — reference files are routed from the SKILL.md body, not listed in `AGENTS.md`. Do update the bullet's summary if the skill's scope shifted.

See `references/authoring-conventions.md` for the full repo-specific authoring checklist (frontmatter rules, file-size targets, AGENTS.md sync checklist).

### Step 5 — Validate

Run the reference validator against every skill directory you touched
(https://github.com/agentskills/agentskills/tree/main/skills-ref):

```sh
skills-ref validate .agents/skills/<name>
```

It checks frontmatter validity and naming constraints (name/directory
match, char limits). Setup instructions are in
`references/agent-skills-spec.md`. If the tool isn't installable in
the current environment, hand-check against the frontmatter table in
that reference instead, and say so.

## What "auto-load" means here

A skill is "loaded" by the matcher when its `description` frontmatter overlaps the conversation's topic. To make this skill fire on any library-docs search, the description above lists every library name in the project plus the verbs that signal a search workflow. If you add a new library to the project, **edit this skill's description to add its name and anchor URLs to the table above** — that keeps the matcher hot for the new library.

## Common pitfalls

- **Answering from memory about post-cutoff libraries.** Room 3 and DoltLite shipped in March 2026. A model's memory of them is at best partial and at worst confabulated. Fetch, quote, cite.
- **Paraphrasing instead of quoting.** The verbatim quote is what survives a future doc reorganisation; the paraphrase rots.
- **Citing the homepage instead of the specific page.** `https://www.sqlite.org` proves nothing about `sqlite3_step`'s return codes; the c3ref page does.
- **Forgetting version pinning.** DoltLite is alpha with an explicit storage-format instability warning — always record which version a claim was verified against.
- **Skipping `AGENTS.md` sync.** A new skill with no bullet in the `## Skills` list is invisible to future agents.
- **Citing repo docs by line number.** Cite decision ids (`ARCHITECTURE.md D3`) or sections (`§4`) instead.
- **Writing a `## Sources` footer.** This repo cites inline. Footers create two places to keep in sync.

## When to load reference files

- Choosing what to fetch and how to phrase the prompt: `references/research-and-cite.md`.
- Authoring a brand-new skill from scratch (this repo's conventions + AGENTS.md checklist): `references/authoring-conventions.md`.
- The format itself — Anthropic's level-1/2/3 loading model, the agentskills.io spec (frontmatter fields, directory layout, 500-line rule), the `.agents/skills/` convention, and `skills-ref` setup: `references/agent-skills-spec.md`.

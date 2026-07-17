# Authoring conventions for doltlite-room-bridge skills

The full checklist for creating a new skill or auditing an existing one. Combines the Agent Skills format (Anthropic's progressive-disclosure model + the agentskills.io open spec — full field tables and constraints in `agent-skills-spec.md`) with this repo's specific layout.

## Anthropic's three-level model

From the official Agent Skills overview (https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview):

| Level | When loaded                  | Token budget                | Content                                       |
|-------|------------------------------|-----------------------------|-----------------------------------------------|
| 1     | Always (at startup)          | ~100 tokens / skill         | YAML frontmatter `name` + `description`       |
| 2     | When triggered               | Under 5k tokens             | SKILL.md body — instructions and decision tree |
| 3     | As needed                    | Effectively unlimited       | Reference markdown, code, schemas, examples   |

The doc's exact framing:

> "Progressive disclosure ensures only relevant content occupies the context window at any given time."

> "Skills can include comprehensive API documentation, large datasets, extensive examples, or any reference materials you need. There's no context penalty for bundled content that isn't used."

The repo's `.agents/skills/` tree mirrors this exactly — frontmatter, SKILL.md body, and `references/<topic>.md` files for level-3 depth. `.agents/skills/` is the cross-client location convention (see `agent-skills-spec.md`).

## Frontmatter rules

From the same doc and the agentskills.io spec:

- `name`: ≤64 chars, lowercase letters / digits / hyphens only, no leading/trailing hyphen, **must match the directory name**, no XML tags, must not contain reserved words `anthropic` or `claude`.
- `description`: non-empty, ≤1024 chars, no XML tags, must encode **both what the skill does and when to use it**.
- Optional spec fields (`license`, `compatibility`, `metadata`, `allowed-tools`) are allowed but unused in this repo so far; see `agent-skills-spec.md`.

This repo's additional convention: pack the `description` with library names and trigger phrases so the matcher fires reliably. The matcher only sees the frontmatter at startup; an under-keyworded description means the skill never loads.

Verify char count after editing:

```sh
awk 'NR==1{p=1;next} /^---$/&&p{p=0;exit} p{print}' .agents/skills/<name>/SKILL.md | wc -c
```

## SKILL.md body

Aim for ≤5k tokens (~5KB plain markdown is a fine proxy; the spec's guidance is under 500 lines). Structure:

1. **What this skill is** — one short paragraph.
2. **Role in this project** — connect it to the relevant decision or section in `ARCHITECTURE.md`, cited by decision id or section where load-bearing: `(ARCHITECTURE.md D1)`, `(ARCHITECTURE.md §4)`. **Never cite by file:line** — line numbers are brittle and go stale on the next edit; decision ids survive rewrites.
3. **The procedural body** — the actual decision tree.
4. **When to load reference files** — bullet list mapping decision branches to `references/<topic>.md`.

The body should *route* the agent to deeper references, not duplicate them. If a section grows past ~30 lines it probably belongs in a reference.

## Reference files

`.agents/skills/<name>/references/<topic>.md`. Each file is a single topic so the SKILL.md decision tree can route to it precisely. Length budget is loose — Anthropic notes "effectively unlimited" — but in practice 50–200 lines per reference is the sweet spot. Beyond that, split.

References should be loadable independent of each other. If `references/A.md` only makes sense after reading `references/B.md`, that is a sign B should be merged into the SKILL.md decision tree (since SKILL.md is always loaded when triggered) and A can stand alone.

Citations follow the inline-parenthetical convention (see `research-and-cite.md`). No `## Sources` footers.

## File-naming rules (this repo)

- One topic per reference file. File name mirrors the topic (`driver-interfaces.md`, not `details.md`).
- No internal barrel files. Each reference is reached directly via the SKILL.md routing list.

## AGENTS.md sync checklist

`AGENTS.md`'s `## Skills` section is a flat bullet list — one `- [`<name>`](.agents/skills/<name>/SKILL.md) — <one-line summary>` bullet per skill. There is no table and no per-skill heading.

For a new skill:

1. Add the skill's bullet to the list.
2. Write the one-line summary so a reader knows when to load it — name the files, APIs, or concerns the skill covers.

For a renamed or removed skill, fix or delete its bullet. When an existing skill's scope shifts, update its summary. Reference files are routed from the SKILL.md body, not listed in `AGENTS.md`, so adding a `references/<topic>.md` needs no `AGENTS.md` edit.

If the project ever accumulates skills that should *not* load during the current iteration's work, adopt trinisphere's dormant-skill convention: a separate Dormant paragraph in `AGENTS.md`, load conditions written in the negative, and a dormant banner at the top of each such SKILL.md.

## Naming & placement examples in this repo

| Good | Why |
|---|---|
| `.agents/skills/doltlite/SKILL.md` | Library name as skill name |
| `.agents/skills/kmp-native-interop/` | Concept name when no single library owns it |
| `references/driver-interfaces.md` | Topic name, not "details" |
| `references/version-control-sql.md` | Specific surface the agent will look up |

| Avoid | Why |
|---|---|
| `.agents/skills/database/` | Too generic; `doltlite` / `sqlite-c-api` are searchable |
| `references/misc.md` | Not a topic |
| `references/index.md` | Barrel files are explicitly avoided |
| `.agents/skills/claude-helper/` | Reserved word `claude` in name — frontmatter check will fail |

## Pre-merge checklist

Before considering a new or patched skill done:

- [ ] Frontmatter `name` matches `.agents/skills/<name>/`.
- [ ] Frontmatter `description` is ≤1024 chars and lists trigger phrases (libraries, APIs, error messages).
- [ ] No reserved words (`anthropic`, `claude`) in the name.
- [ ] SKILL.md body is a decision tree, not a manual; depth is in `references/`.
- [ ] Every load-bearing factual claim has an inline URL citation.
- [ ] Claims about post-cutoff libraries (Room 3, DoltLite) were verified by fetching, not recalled from memory.
- [ ] No file:line citations into repo docs — cite ARCHITECTURE.md decision ids instead.
- [ ] No `## Sources` footer.
- [ ] `AGENTS.md` Skills bullet added/updated with an accurate one-line summary.
- [ ] All `references/<topic>.md` files mentioned in the SKILL.md actually exist.
- [ ] `skills-ref validate .agents/skills/<name>` passes (setup in `agent-skills-spec.md`; hand-check the frontmatter table if the tool is unavailable).

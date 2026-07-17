# The Agent Skills format — Anthropic guidance + the open spec

What this repo's skills must conform to. Two sources, one format:
Anthropic's Agent Skills overview
(https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
describes how agents *load* skills; the vendor-neutral **Agent Skills
specification** (https://agentskills.io/specification, maintained at
https://github.com/agentskills/agentskills) defines what a skill
*contains*. The spec is implemented beyond Claude — GitHub Copilot /
VS Code, Cursor, Gemini CLI, and Microsoft's Agent Framework consume
the same layout — which is why this repo keeps its skills under the
cross-client `.agents/skills/` directory.

## The three levels (Anthropic guidance, mirrored by the spec)

Skills are loaded progressively so only relevant content occupies the
context window:

| Level | Content                          | When loaded            | Budget                  |
|-------|----------------------------------|------------------------|-------------------------|
| 1     | frontmatter `name` + `description` | at startup, for all skills | ~100 tokens per skill |
| 2     | the `SKILL.md` body              | when the skill triggers | < 5000 tokens recommended |
| 3     | bundled files (`references/`, `scripts/`, `assets/`) | as needed, on demand | effectively unlimited |

Anthropic's framing: "Progressive disclosure ensures only relevant
content occupies the context window at any given time" — and there is
"no context penalty for bundled content that isn't used."

Consequences for authors:

- The `description` is the *only* thing the matcher sees at startup —
  it must say both what the skill does and when to use it, packed with
  trigger keywords.
- The body is a decision tree that *routes* to level-3 files; depth
  lives in `references/`, one topic per file.
- In this repo no harness auto-injects level 1: `AGENTS.md`'s skill
  list plays that role, and progressive disclosure is a convention
  agents follow (read a SKILL.md only when its trigger hits).

## The open spec (agentskills.io), condensed

A skill is a directory containing, at minimum, a `SKILL.md`:

```
skill-name/
├── SKILL.md      # Required: frontmatter + instructions
├── scripts/      # Optional: executable code
├── references/   # Optional: documentation loaded on demand
├── assets/       # Optional: templates, static resources
└── ...
```

Frontmatter fields (https://agentskills.io/specification):

| Field           | Required | Constraints |
|-----------------|----------|-------------|
| `name`          | yes      | ≤64 chars; lowercase letters, digits, hyphens; no leading/trailing hyphen; **must match the directory name** |
| `description`   | yes      | 1–1024 chars; what the skill does *and* when to use it, with task keywords |
| `license`       | no       | license name or bundled-file reference |
| `compatibility` | no       | ≤500 chars; environment requirements (product, packages, network) |
| `metadata`      | no       | arbitrary key-value map |
| `allowed-tools` | no       | space-separated pre-approved tools (experimental) |

Body rules: Markdown, no format restrictions, but keep `SKILL.md`
**under 500 lines** (< 5000 tokens) and move detail to separate files.
Reference other files with **relative paths from the skill root**, kept
**one level deep** — no nested reference chains.

## Location convention

The spec deliberately does not mandate where skills live; it defines
only what goes inside a skill directory. For discovery, the
cross-client convention is **`<project>/.agents/skills/`** (project
scope) and `~/.agents/skills/` (user scope), which compliant clients
scan alongside their native directories such as `.claude/skills/`
(https://github.com/agentskills/agentskills/blob/main/docs/client-implementation/adding-skills-support.mdx).
This repo uses `.agents/skills/`.

## Validation

The reference implementation ships a CLI
(https://github.com/agentskills/agentskills/tree/main/skills-ref) —
a Python package (demo-grade, not production):

```sh
git clone https://github.com/agentskills/agentskills
cd agentskills/skills-ref && python -m venv .venv && . .venv/bin/activate && pip install -e .
skills-ref validate <path/to/skill>        # frontmatter + naming checks
skills-ref read-properties <path>          # parsed metadata as JSON
skills-ref to-prompt <path>...             # <available_skills> XML for system prompts
```

Run `skills-ref validate` against every skill directory you create or
modify before considering the change done.

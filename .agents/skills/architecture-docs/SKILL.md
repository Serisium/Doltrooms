---
name: architecture-docs
description: Conventions for writing and maintaining ARCHITECTURE.md, based on matklad's ARCHITECTURE.md essay (the rust-analyzer convention). Use before editing ARCHITECTURE.md, when recording or renumbering a decision (D1–D7), when the README and ARCHITECTURE.md disagree, or when auditing docs for consistency. Triggers: ARCHITECTURE.md, architecture document, codemap, architectural invariant, settled decision, decision record, doc consistency, matklad.
---

# Maintaining ARCHITECTURE.md

## What this skill is

The working conventions for this repo's `ARCHITECTURE.md`, grounded in
matklad's essay that popularized the file
(https://matklad.github.io/2021/02/06/ARCHITECTURE.md) — the
convention rust-analyzer's architecture.md exemplifies.

## The essay's rules, as they apply here

- **Keep it short; every recurring contributor reads it.** "The
  shorter it is, the less likely it will be invalidated by some future
  change… only specify things that are unlikely to frequently change."
  Decisions (D1–D7) qualify; implementation detail does not — that
  belongs in code comments or `.agents/skills/`.
- **Bird's-eye problem statement first, then a codemap.** §1 answers
  "what is this"; §3's layout table answers "where's the thing that
  does X". A codemap is "a map of a country, not an atlas of maps of
  its states."
- **Call out architectural invariants explicitly**, especially the
  ones expressed as an *absence*: "Room is never forked", "no MySQL
  client anywhere", "nothing scaffolded for the web target". These are
  impossible to divine from reading code — they're what the document
  is for.
- **Name important things; don't deep-link them.** Line-number and
  URL links into moving targets go stale; names (`SQLiteDriver`,
  `libdoltlite`, `doltlite-remotesrv`) stay searchable.

## This repo's additions

- **README.md wins.** README.md is human-curated and newer by
  definition; when it and ARCHITECTURE.md disagree, update
  ARCHITECTURE.md to follow it (see AGENTS.md "Governing documents").
- **Decisions only.** Every entry is settled; if something isn't
  there, it isn't decided. No "might later", no options. The
  "Candidate first milestones" in README.md are explicitly *not*
  decisions — they enter ARCHITECTURE.md only when promoted (§4).
- **Stable decision ids.** D-numbers are referenced from AGENTS.md,
  docs/FEASIBILITY.md commentary, and skills — never renumber an
  existing decision; append new ones.
- **§4 is the scope gate.** "Current iteration" bounds what may be
  scaffolded (AGENTS.md working rules). Update it when an iteration
  lands, and fix every cross-reference when section numbers move.

## Editing checklist

1. Is the change a settled decision? If not, it doesn't go in.
2. Does it contradict README.md? Then the README decision stands —
   write the ARCHITECTURE.md text to match it.
3. New decision → new D-number at the end; amended decision → edit in
   place, keep the number.
4. Update the **Status** date.
5. Sweep cross-references: AGENTS.md (§ and D refs),
   `.agents/skills/*/SKILL.md` and their `references/`. A dangling
   reference is a bug (AGENTS.md working rules).

## Authoritative URLs

- matklad, "ARCHITECTURE.md": https://matklad.github.io/2021/02/06/ARCHITECTURE.md
- rust-analyzer's example: https://github.com/rust-lang/rust-analyzer/blob/master/docs/book/src/contributing/architecture.md

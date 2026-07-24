# :dolt-core — `dev.seri.doltrooms:doltrooms-dolt-core`

**Status: SCAFFOLD.** Empty module skeleton; contents land with the rev-4
contract implementation (`docs/design/module-architecture.md`). The
prototypes under `prototypes/` are the seeds.

The shared kernel of the version-control surface — the types both the read
and write sides need, so `:dolt-read` and `:dolt-write` stay siblings (no
write→read dependency).

## Intended contents (per the architecture doc)

- The `DoltRef` sealed hierarchy (`+ parent`/`minus`).
- The six `DoltEvents` anchor entities.
- The shipped row types: `BranchRow`, `CommitRow`, `TagRow`, `StatusRow`,
  `RemoteRow`, `DiffStatRow`.
- `DoltRowId` / `DoltEntityBase` — the blessed key supertype (contract §13).

## Dependencies

- Room runtime (annotations + entities) only.

## Proof gate

Row-type schema tests against the pinned engine (schemas probe-pinned at
DoltLite 0.11.33).

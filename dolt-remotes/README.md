# :dolt-remotes — `dev.seri.doltrooms:doltrooms-dolt-remotes`

**Status: SCAFFOLD.** Empty module skeleton; contents land with the rev-4
contract implementation (`docs/design/module-architecture.md`). The
`RemoteServerSyncTest` currently in `:dolt-write` is the round-trip seed.

Sync — the opt-in, trusted-network-only surface. Separate because D3/D9 make
sync opt-in (plain `file://` / `http://`). An API-hygiene split, not
binary-size: the engine in `:driver` contains the sync code regardless.

## Intended contents (per the architecture doc)

- The `Remotes` collection.
- `fetch` / `push` / `pull`.
- The `clone` bootstrap.

## Dependencies

- `:dolt-write` (pull = fetch + merge; reuses `MergeResult` / `ConflictPolicy`).

## Proof gate

Remote round-trip suites against `file://` and `doltlite-remotesrv` fixtures.

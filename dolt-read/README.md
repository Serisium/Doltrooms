# :dolt-read — `dev.seri.doltrooms:doltrooms-dolt-read`

**Status: SCAFFOLD.** Empty module skeleton; contents land with the rev-4
contract implementation (`docs/design/module-architecture.md`). The P1/P2/P4
probe suites are the seeds.

The reactive/declared read machinery — NOT the queries themselves (those are
consumer DAOs).

## Intended contents (per the architecture doc)

- The `@DoltQuery` / `DoltConnection` annotations.
- The runtime support the generated impls call into.
- The runtime-dynamic-table `RoomRawQuery` builders.

## Dependencies

- `:dolt-core`.

## Proof gate

Generated-flow behavior tests (anchor observation, Writer routing, distinct)
— the P1/P2/P4 probe suites seed these.

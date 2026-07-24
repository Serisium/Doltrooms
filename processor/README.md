# :processor — `dev.seri.doltrooms:doltrooms-processor`

**Status: SCAFFOLD.** Host-JVM module, KSP classpath only; **never ships in
an app**. Contents land with the rev-4 implementation
(`docs/design/module-architecture.md`); the P5/P6/P7 prototype harnesses are
the seeds.

The library's own KSP processor.

## Intended contents (per the architecture doc)

- `@DoltQuery` codegen.
- The anchor lint (contract §8).
- The DDL emitter with its pinned feature ceiling (contract §12 D-d + §13
  blessed shape).

## Dependencies

- `:driver` (jvm); knows `:dolt-read`'s annotations by qualified name (no
  compile dependency required).

## Proof gate

The P5 (`dolt-anchor-lint`) and P6/P7 (`ksp-ddl-verify`,
`entity-supertype-probe`) prototype harnesses, promoted into module tests.

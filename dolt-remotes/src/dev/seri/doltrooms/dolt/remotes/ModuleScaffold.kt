// SCAFFOLD MARKER — this module's real contents land with the rev-4
// contract implementation (docs/design/module-architecture.md). The
// marker exists because the module must PUBLISH before then (the module
// DAG's POMs reference it), and toolchain 0.12.0-dev crashes publishing
// a module with zero Kotlin sources (kotlin-toolchain skill, bug ledger).
// internal on purpose: no public API before the D11 gate returns.
package dev.seri.doltrooms.dolt.remotes

internal object ModuleScaffold

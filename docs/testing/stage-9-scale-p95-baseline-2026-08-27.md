# Stage 9 scale P95 baseline - 2026-08-27

The nightly scale fixtures now collect 20 samples in addition to the initial
and repeat smoke measurements.

- `ProcedureIrScaleGateTest` records JSON and Blockly XML round-trip P95.
- `WorkspaceReferenceIndexScaleTest` records reference projection P95 for
  2,000 elements and 10,000 references and asserts the 300 ms target.

The generated JSON is written under `build/nightly-results/` by the tests. The
measurements are Core-side baselines; the fixed-hardware Stage 9 gate still
requires UI list/filter/reference-query interaction timing and a real JCEF host.

## Local execution - 2026-08-27

The conditional fixtures were executed with
`-Dcopperbench.stage9.scale=true` using the repository-bundled JBR 25.

- Procedure 500-node fixture: passed. Validation 4 ms, first JSON round trip
  11 ms, first Blockly XML round trip 231 ms, JSON P95 1 ms, XML P95 226 ms.
- Workspace reference fixture: passed for 2,000 elements and 10,000
  references. Initial projection 198 ms, repeat 32 ms, P95 30 ms.

These numbers are useful regression baselines only. They do **not** promote
`procedure-500` or `workspace-2000-10000`, because those gates require the
fixed-hardware UI/JCEF interaction path defined by FR-S9-01/02.

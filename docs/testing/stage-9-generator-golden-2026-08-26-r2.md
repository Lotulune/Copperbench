# Stage 9 eight-generator golden evidence - 2026-08-26 rerun

This record closes only `FR-S9-03`: Function, Loot Table, and Advancement
generation plus Gradle compilation across the eight workspace generators.

- Workflow: `Nightly product gates`
- Run: <https://github.com/Lotulune/Copperbench/actions/runs/32956081891>
- Source commit: `6fa8e942e636c7e4cb7c3a003cc5d6b8b85f4b92`
- Matrix result: 8/8 generator jobs passed
- Product regression result: the separate product-regression job had one
  pre-existing JCEF bridge E2E timing failure; this does not invalidate the
  independently successful generator matrix.

Each matrix job created and persisted a Function, Loot Table, and Advancement,
generated the workspace, ran its generator-specific Gradle build, and required
a non-sources JAR. Per-generator diagnostics were uploaded by the workflow.

| Generator | Result |
| --- | --- |
| Fabric 26.2 | Passed |
| NeoForge 26.2 | Passed |
| Fabric 26.1.2 | Passed |
| NeoForge 26.1.2 | Passed |
| Fabric 1.21.1 | Passed |
| NeoForge 1.21.1 | Passed |
| Fabric 1.20.1 | Passed |
| NeoForge 1.20.1 | Passed |

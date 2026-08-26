# Stage 9 eight-generator golden evidence - 2026-08-26 rerun

This record closes only `FR-S9-03`: Function, Loot Table, and Advancement
generation plus Gradle compilation across the eight workspace generators.

- Workflow: `Nightly product gates`
- Run: <https://github.com/Lotulune/Copperbench/actions/runs/32970451959>
- Source commit: `d622676f67a70255ac9fa32a8e9048705569d05c`
- Matrix result: 8/8 generator jobs passed
- Product regression result: passed, including the 500-node Procedure scale
  smoke and full Playwright suite.

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

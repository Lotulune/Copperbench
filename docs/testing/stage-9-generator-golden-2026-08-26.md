# Stage 9 eight-generator golden evidence - 2026-08-26

This record closes only `FR-S9-03`, the Function, Loot Table, and Advancement
golden generation and compilation matrix. It does not close the dedicated
editors, language tools, large Procedure/workspace, server readiness, real JCEF
accessibility, or clean Windows 11 gates.

## Fixed evidence

- Workflow: `Nightly product gates`
- Run: <https://github.com/Lotulune/Copperbench/actions/runs/32898153354>
- Source commit: `c34bed3bc3293a6bd9e592625b844332cc2c1381`
- Conclusion: product regression passed; generator matrix passed 8/8
- Build policy: Gradle Wrapper with `--no-build-cache`; matrix `fail-fast: false`

Each matrix job created and persisted a Function, Loot Table, and Advancement,
generated the workspace, ran its generator-specific Gradle build, and required
a non-sources JAR. Diagnostics were retained as per-generator artifacts.

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

The same run also passed the full Java/Javadoc and scale regression, UI-Core
tests, UI Shell production build, complete Chromium Playwright suite, product
status validation, Markdown link validation, and MCP conformance.

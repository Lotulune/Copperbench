# Copperbench remaining work

## Current baseline

- Public prerelease: `v0.1.0-beta.4`.
- Stage 11 / Public Beta V1 product closure is complete.
- Stage 12 / complex element depth is complete in the current implementation line: all 37 first-party Java Mod Element types now share the structured editor/schema path, unknown fields survive save/reopen, field diagnostics can locate the affected editor field, and the eight supported Fabric/NeoForge generator tracks pass the Stage 12 edited-fixture golden build. See [Stage 12 closure evidence](./testing/stage12-complex-element-depth-2026-09-05.md).
- Stage 13 / creator productivity is complete on the current development line. Procedure Workbench 2.0, Asset Center, Diagnostics 2.0, Local History/Recovery, Migration/Refactor and Workspace Health all satisfy the Stage 13 Definition of Done, including the final clean-installed Windows Explorer drag/drop and failure → location → recovery-protected repair → rebuild product replays. See [Stage 13 closure evidence](./testing/stage13-closure-2026-09-07.md).
- The installed-product P0 hardening for bundled JDK resolution, real Run Client lifecycle, desktop MCP integration, and the external-Agent product loop is complete and represented by Beta 4.
- All current beta-blocking gates in `product-status.json` are `passed`; `product.betaEligible=true`.
- Current formal desktop platform support remains Windows 11 x64. Linux formal support is now an explicit Stage 15 deliverable and must not be advertised as completed before its own clean-Linux candidate evidence exists.

The historical Stage 9–11 evidence remains authoritative for the Beta 4 baseline. New development should reopen and revalidate the corresponding installed-product gate whenever it changes the validated JDK, Run Client, Desktop MCP, or external-Agent product path, or when regression evidence shows the validated behavior changed; otherwise the existing passed gate state remains the baseline.

## Next product work

The active roadmap remains `PRD-NEXT.md`. Stages 12 and 13 are closed; Stage 14 is now the next product-development stage. Stage 13 closure is summarized in [Stage 13 closure evidence](./testing/stage13-closure-2026-09-07.md), with specialist evidence retained for [Procedure Workbench 2.0](./testing/stage13-procedure-productivity-2026-09-05.md), [Asset Center](./testing/stage13-asset-center-2026-09-05.md), [Diagnostics 2.0](./testing/stage13-diagnostics-2026-09-05.md), [Local History / Recovery](./testing/stage13-history-recovery-2026-09-06.md), [Migration / Refactor](./testing/stage13-migration-refactor-2026-09-06.md) and [Workspace Health](./testing/stage13-workspace-health-2026-09-07.md).

The remaining roadmap work is:

1. **Stage 14 — advanced developer / AI-native workflows**: generated/manual source ownership boundaries, IDE Bridge, AI Plan Review, higher-level MCP workflows, reusable templates and experimental extension-developer entry points.
2. **Stage 15 — Linux formal platform support**: Linux x86_64 packaging, bundled JDK/JCEF, desktop integration, Gradle/Run Client, desktop MCP/external-Agent parity, clean-Linux VM validation, and release/provenance closure.
3. **Continuous maintenance**: Minecraft/loader/generator/toolchain compatibility and regression coverage.

## Non-blocking follow-up

The following remain useful quality work but are not current Beta 4 release blockers:

- real JCEF accessibility certification on a physical or otherwise known-good Windows accessibility environment;
- broader external-tester trials;
- Authenticode signing;
- macOS and any platform targets outside the current Windows 11 x64 baseline and the explicitly planned Stage 15 Linux x86_64 target.

These items must not be described as passed until their own evidence exists.

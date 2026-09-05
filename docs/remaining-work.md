# Copperbench remaining work

## Current baseline

- Public prerelease: `v0.1.0-beta.4`.
- Stage 11 / Public Beta V1 product closure is complete.
- Stage 12 / complex element depth is complete in the current implementation line: all 37 first-party Java Mod Element types now share the structured editor/schema path, unknown fields survive save/reopen, field diagnostics can locate the affected editor field, and the eight supported Fabric/NeoForge generator tracks pass the Stage 12 edited-fixture golden build. See [Stage 12 closure evidence](./testing/stage12-complex-element-depth-2026-09-05.md).
- The installed-product P0 hardening for bundled JDK resolution, real Run Client lifecycle, desktop MCP integration, and the external-Agent product loop is complete and represented by Beta 4.
- All current beta-blocking gates in `product-status.json` are `passed`; `product.betaEligible=true`.
- Current formal desktop platform support remains Windows 11 x64. Linux formal support is now an explicit Stage 15 deliverable and must not be advertised as completed before its own clean-Linux candidate evidence exists.

The historical Stage 9–11 evidence remains authoritative for the Beta 4 baseline. New development should reopen and revalidate the corresponding installed-product gate whenever it changes the validated JDK, Run Client, Desktop MCP, or external-Agent product path, or when regression evidence shows the validated behavior changed; otherwise the existing passed gate state remains the baseline.

## Next product work

The active roadmap remains `PRD-NEXT.md`. Stage 12 is closed and Stage 13 is now active. `FR-PRODUCTIVITY-01 Procedure Workbench 2.0` is complete on the current development line: graph discovery/navigation, Core-owned variable/resource/call symbols and readable relationships, node-level validation/location, protected variable/extraction/call/resource refactors, signed affected-object/field plan review, shared UI/MCP Core semantics, the 500-node Procedure gate, the 2,000-element / 10,000-reference index gate, and a fresh-export failure → locate → fix → rebuild replay all have executable evidence. See [Stage 13 Procedure 2.0 closure evidence](./testing/stage13-procedure-productivity-2026-09-05.md).

The remaining Stage 13 work is:

1. **Stage 13 / `FR-PRODUCTIVITY-02` — Asset Center**: unify asset search/filter/preview, reverse usage, missing/invalid/unused asset diagnostics, safe import, Blockbench refresh, and reference-safe rename/move.
2. **Stage 13 / `FR-PRODUCTIVITY-03` — Diagnostics 2.0**: map build/generator/resource/migration/MCP failures to stable IDs and concrete element/field/Procedure-node/asset locations with actionable repair guidance.
3. **Stage 13 / `FR-PRODUCTIVITY-04`–`06`**: deepen history/recovery UX, Migration / Refactor Workbench, and Workspace Health.
4. **Stage 14 — advanced developer / AI-native workflows**: IDE bridge, AI Plan Review, higher-level MCP workflows, templates and extension-developer entry points.
5. **Stage 15 — Linux formal platform support**: Linux x86_64 packaging, bundled JDK/JCEF, desktop integration, Gradle/Run Client, desktop MCP/external-Agent parity, clean-Linux VM validation, and release/provenance closure.
6. **Continuous maintenance**: Minecraft/loader/generator/toolchain compatibility and regression coverage.

## Non-blocking follow-up

The following remain useful quality work but are not current Beta 4 release blockers:

- real JCEF accessibility certification on a physical or otherwise known-good Windows accessibility environment;
- broader external-tester trials;
- Authenticode signing;
- macOS and any platform targets outside the current Windows 11 x64 baseline and the explicitly planned Stage 15 Linux x86_64 target.

These items must not be described as passed until their own evidence exists.

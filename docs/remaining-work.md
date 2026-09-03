# Copperbench remaining work

## Current baseline

- Public prerelease: `v0.1.0-beta.4`.
- Stage 11 / Public Beta V1 product closure is complete.
- The installed-product P0 hardening for bundled JDK resolution, real Run Client lifecycle, desktop MCP integration, and the external-Agent product loop is complete and represented by Beta 4.
- All current beta-blocking gates in `product-status.json` are `passed`; `product.betaEligible=true`.

The historical Stage 9–11 evidence remains authoritative for the Beta 4 baseline. New development should not reopen those gates unless a regression changes the validated behavior.

## Next product work

The active roadmap is `PRD-NEXT.md`:

1. **Stage 12 — complex element depth**: deepen `livingentity`, `biome`, `dimension`, `gui`, then related entity/worldgen/UI element types.
2. **Stage 13 — creator productivity**: Procedure 2.0, Asset Center, Diagnostics 2.0, history/migration/refactor workflows.
3. **Stage 14 — advanced developer / AI-native workflows**: IDE bridge, AI Plan Review, higher-level MCP workflows, templates and extension-developer entry points.
4. **Continuous maintenance**: Minecraft/loader/generator/toolchain compatibility and regression coverage.

## Non-blocking follow-up

The following remain useful quality work but are not current Beta 4 release blockers:

- real JCEF accessibility certification on a physical or otherwise known-good Windows accessibility environment;
- broader external-tester trials;
- Authenticode signing;
- additional operating-system/platform support outside the current Windows 11 x64 target.

These items must not be described as passed until their own evidence exists.

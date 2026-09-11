# Copperbench remaining work

## Current baseline

- Public prereleases: Windows `v0.1.0-beta.4`; Linux `v0.1.0-linux-preview.2`.
- Stage 11 / Public Beta V1 product closure is complete.
- Stage 12 / complex element depth is complete in the current implementation line: all 37 first-party Java Mod Element types now share the structured editor/schema path, unknown fields survive save/reopen, field diagnostics can locate the affected editor field, and the eight supported Fabric/NeoForge generator tracks pass the Stage 12 edited-fixture golden build. See [Stage 12 closure evidence](./testing/stage12-complex-element-depth-2026-09-05.md).
- Stage 13 / creator productivity is complete on the current development line. Procedure Workbench 2.0, Asset Center, Diagnostics 2.0, Local History/Recovery, Migration/Refactor and Workspace Health all satisfy the Stage 13 Definition of Done, including the final clean-installed Windows Explorer drag/drop and failure → location → recovery-protected repair → rebuild product replays. See [Stage 13 closure evidence](./testing/stage13-closure-2026-09-07.md).
- The installed-product P0 hardening for bundled JDK resolution, real Run Client lifecycle, desktop MCP integration, and the external-Agent product loop is complete and represented by Beta 4.
- All current beta-blocking gates in `product-status.json` are `passed`; `product.betaEligible=true`.
- Formal desktop support covers Windows 11 x64 and Ubuntu 24.04 LTS x86_64 with GNOME Wayland/Xorg. Linux release and certification evidence is recorded in [Stage15 closure](./testing/stage15-closure-2026-09-11.md); the Linux channel remains Preview.
- Stage 14 / native-first general-Agent workbench is complete on the current development line: 14A source integrity, 14B native bootstrap/authoring, 14C layered packaged runtime/gameplay evidence, and 14D review/reuse are all closed by their own DoD. See [Stage 14C runtime/gameplay closure evidence](./testing/stage14-runtime-gameplay-closure-2026-09-08.md).

The historical Stage 9–11 evidence remains authoritative for the Beta 4 baseline. New development should reopen and revalidate the corresponding installed-product gate whenever it changes the validated JDK, Run Client, Desktop MCP, or external-Agent product path, or when regression evidence shows the validated behavior changed; otherwise the existing passed gate state remains the baseline.

## Next product work

The active roadmap remains `PRD-NEXT.md`. Stages 12, 13, 14 and **Stage15 Linux formal-support work are complete** within their accepted scope. Stage15 closure binds Run42 installed evidence to the signed, production-approved public Linux Preview 2 and its unchanged binary digests.

The remaining roadmap work is continuous maintenance: Minecraft/loader/generator/toolchain compatibility, regression coverage, and the independent semantic check for the historical Fabric Commands remap warning. Broader Linux distributions, architectures and installed game-version tracks need their own evidence before support expands.

## Confirmed integrity/runtime follow-up

- **CB-AUDIT-01 — closed for Stage 14A**: direct and Workspace Plan writes reject physical-path ownership collisions; non-code targets are predicted from the upstream generator on all eight supported Java tracks; rejected creates clean orphan files; late-failure rollback removes new element/shared generated files and restores existing base files; recovery replay restores live source bytes; generated/manual takeover survives reopen/regenerate and requires explicit approved reattach. The full FR-ADV-10 lifecycle now passes on all eight Java tracks, and the current Windows candidate passes the installed-product Desktop MCP unmanaged-source ownership replay.
- **CB-AUDIT-02 — closed for Stage 14A**: code projection refreshes primary/helper contents from disk, metadata-only updates do not replay stale source, code-locked regeneration preserves external IDE edits, and explicit writes use per-file SHA-256 fingerprints. The eight-track lifecycle passes stale-source and reopen/regenerate coverage, while the installed-product Desktop MCP replay rejects stale direct source writes with `SOURCE_CONTENT_CONFLICT` and stale Workspace Plans with `WORKSPACE_PLAN_SOURCE_CONFLICT` without changing newer IDE bytes.
- **CB-AUDIT-03 — fixed for Fabric 1.21.1 source generation**: `ServerPlayerMixin` now targets the boolean `drop(Z)Z` descriptor. A real 1.21.1 development-server rerun advanced past this previously fatal injection.
- **CB-AUDIT-04 — reproduced and fixed for Fabric 1.21.1 source generation**: the next real bootstrap exposed `RepairItemRecipeMixin` using the obsolete one-argument `assemble` descriptor. The 1.21.1 template now targets `assemble(CraftingInput, HolderLookup.Provider)` with a cancellable handler. With both mixin fixes mirrored into the disposable audit fixture, `runServer` initialized `SurveyPulseMod`, emitted `SURVEY_PULSE_INITIALIZED`, reached the intentional unaccepted-EULA boundary, and Gradle reported `BUILD SUCCESSFUL`.
- The prior Commands remap warning still requires independent semantic verification. It remains a continuous-maintenance item; it is no longer used to infer or block the independently executed Stage 14C packaged-JAR/gameplay matrices.
- CB-AUDIT-01/02 were originally reproduced through real Desktop MCP HTTP with production-source components at `eaf18606`, in disposable workspaces. Historical Stage 12/13/Beta gate closures remain scoped evidence; affected product-path gates must be revalidated before the next candidate claims the repaired native workflow.
- The [Survey Pulse comparison](./testing/native-agent-survey-pulse-comparison-2026-09-07.md) separates plain-Fabric authoring, Copperbench source-runtime results and cached build measurements. Its original gameplay limitations remain historical facts; the later [Stage 14C closure](./testing/stage14-runtime-gameplay-closure-2026-09-08.md) adds independent runtime behavior evidence rather than rewriting the old comparison.
- The [eight-track Survey Pulse audit](./testing/stage14-eight-track-survey-pulse-2026-09-07.md) binds results to the current uncommitted Stage 14A source delta, not just the historical HEAD. Direct Core source-integrity checks, generated/native compilation, default bootstrap, server-only bootstrap and gameplay are separate cells. Source-harness corrections and asset-download timeouts are not counted as product failures.
- **CB-AUDIT-05 — closed for field materialization/generation**: current-source eight-track replay verifies documented `initialValues.fields.maxStackSize=1` and `/fields/maxStackSize=7` become real upstream values 1→7, generated source contains `.stacksTo(1/7)`, and reopen remains 7. Gameplay/runtime-value verification stays in Stage 14C rather than being inferred from generated source.
- **CB-AUDIT-06 — closed for the reproduced Fabric 1.20.1 Loader contract**: generated `fabric.mod.json` now requires `fabricloader >=0.15.11`, matching the generated build dependency; a real Loader 0.15.11 `runServer` replay loaded the mod, executed `SURVEY_PULSE_INITIALIZED`, and then reached the intentional EULA boundary. This proves initializer/loading for that development-source fixture, not server readiness, packaged-JAR deployment or gameplay.
- **CB-AUDIT-07 — closed for modern NeoForge runtime generation**: `neoforge-1.21.1`, `neoforge-26.1.2`, and `neoforge-26.2` no longer register the generated main class on `NeoForge.EVENT_BUS` when it has no generated `@SubscribeEvent` method. The three failed Copperbench packaged gameplay cells were regenerated through the production generator path and now pass initializer, packaged-JAR load, server-ready and gameplay verification.
- Cross-track Stage 14A source-integrity and the affected Windows installed-product Desktop MCP path have been revalidated. Stage 14C now records Fabric 8/8 behavior-passed cells and NeoForge 6 behavior-passed cells plus two explicit NeoForge 1.20.1 EULA authorization blocks; the NeoForge matrix is 8/8 closure-satisfied without accepting EULA or relabeling `not_run` gameplay as passed.
- **Stage 14B/14D — closed on the current development line**: product bootstrap discovery/local approval, native multi-file failure-repair-build-reopen, Workspace Plan high-impact review/read-only/recovery/tamper gates, explicit experimental-extension compatibility boundaries, and real MCreator local-template Procedure+asset round-trip all pass targeted regression. Template target-path conflict and signed-plan tampering are rejected before mutation.
- **Stage 14C / Stage 14 overall — closed on the current development line**: packaged runtime layers remain separate, the same-Agent comparison retains its cache/timing caveats, and the full closure rationale is recorded in [Stage 14C runtime/gameplay closure evidence](./testing/stage14-runtime-gameplay-closure-2026-09-08.md). Stage15 has subsequently completed its independent Linux acceptance and public release.

## Non-blocking follow-up

The following remain useful quality work but are not current Beta 4 release blockers:

- real JCEF accessibility certification on a physical or otherwise known-good Windows accessibility environment;
- broader external-tester trials;
- Authenticode signing;
- macOS and platform targets outside Windows 11 x64 and the certified Ubuntu 24.04 GNOME x86_64 scope.

These items must not be described as passed until their own evidence exists.

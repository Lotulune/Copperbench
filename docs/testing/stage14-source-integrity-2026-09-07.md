# Stage 14A source-integrity closure evidence — 2026-09-07 to 2026-09-08

## Scope

This record started as the first implementation slice of Stage 14A / FR-ADV-01 and was extended through the 2026-09-08 closure replay. Stage 14A is closed on the current development line: its source-integrity lifecycle passes the FR-ADV-10 eight-track Core layer and the affected current Windows candidate passes a real installed-product Desktop MCP replay. This closure does **not** validate packaged-JAR deployment, server readiness or in-world/gameplay behavior; those remain separate Stage 14C evidence layers.

## Source ownership and coexistence

- `MCreatorWorkspaceMutationGateway` now distinguishes source changes from metadata-only element changes. Unchanged primary/helper source is not rewritten from stale `code` / `codeFiles` metadata.
- Managed helper paths are normalized to physical path keys and checked against every other element's associated files and managed helpers before deletion or write. Duplicate helper entries and attempts to replace the current element's primary source are rejected.
- Workspace Plan planning, preview and apply share a durable-backend ownership preflight through `WorkspaceMutationGateway.validateWorkspacePlan`; persistence repeats the ownership check rather than trusting an older preview.
- A rejected direct create removes newly generated files that are not owned by another element, preventing an orphan primary Java file from surviving a failed ownership transaction.
- `MCreatorWorkspaceStateMapper` refreshes code-element primary and helper source from the live filesystem. This makes disk content authoritative when an IDE or external Agent edits a managed code file between Copperbench operations.
- Code-locked regeneration remains a no-op for externally edited source, and metadata-only changes preserve those edits.
- Explicit source writes now carry per-file content fingerprints. A write based on stale primary/helper bytes is rejected with `SOURCE_CONTENT_CONFLICT`, including the expected and actual fingerprints, instead of silently overwriting the external edit. Unrelated helper changes do not block a write to a different helper.
- Recovery points capture the live primary/helper bytes, not only element metadata. Replaying a recovery point restores externally edited source content byte-for-byte.
- `SET_SOURCE_MANAGEMENT` now exposes an explicit ownership lifecycle for generated elements. `mode=manual` maps to the upstream `codeLock`, survives reopen, makes the structured editor read-only, and causes regeneration to skip that element. Returning to `mode=generated` requires `userApproved=true`, clears the upstream lock and regenerates the element so the generator becomes authoritative again. Repeating the current mode is idempotent and does not advance the revision.
- Non-code ownership prediction now uses upstream `Generator.getModElementGeneratorTemplatesList(...)` rather than hand-written path guesses. Direct create and Workspace Plan therefore know the real conditional/list-template targets for generated Item/Block/etc. outputs before writing; unmanaged files and cross-type claims are rejected before mutation.
- Workspace Plan rollback snapshots now include planned element outputs plus generator global/base template targets, including targets that did not exist before the plan. A late persistence/revision failure therefore deletes newly generated element/shared files and restores pre-existing generated base files byte-for-byte.

## Focused regressions

The focused Gradle run used the repository JBR and a short workspace-local `TEMP` / `TMP` path because the Windows host otherwise fails Gradle startup with `Unable to establish loopback connection`.

Validated cases include:

1. direct helper-versus-other-primary ownership collision rejection;
2. same-plan two-new-code-element collision rejected by `PLAN_WORKSPACE_CHANGES` before any file is written;
3. failed direct create leaves neither a Mod Element nor orphan primary source;
4. external edits to both primary and helper source are returned by workspace projection;
5. code-locked regenerate preserves both external edits;
6. `/displayName`-only update preserves both external edits;
7. existing multi-file code-bundle lifecycle regressions continue to pass;
8. Fabric 1.21.1 generator-template checks cover the corrected `ServerPlayerMixin` and `RepairItemRecipeMixin` descriptors.
9. stale primary/helper writes are rejected using content fingerprints while unrelated helper edits remain independent;
10. recovery-point replay restores the live externally edited primary/helper bytes;
11. generated -> manual takeover survives reopen and regeneration, manual structured updates are rejected, unapproved reattach is rejected, and explicit approved reattach restores generator ownership and generated output.
12. unmanaged generated Item targets are rejected before direct create, and a code helper colliding with a predicted Item target is rejected during Workspace Plan preview before any write;
13. the unmanaged Item-target preflight passes on all eight supported Java generator tracks (`fabric` / `neoforge` across `1.20.1`, `1.21.1`, `26.1.2`, `26.2`);
14. a real Fabric 1.21.1 late-failure replay proves rollback removes the newly generated Item source and shared `ModItems` file while restoring an existing base file byte-for-byte.

Focused command result: `BUILD SUCCESSFUL`.

## 2026-09-08 eight-track closure replay

The final FR-ADV-10 lifecycle regression is `WorkspacePersistenceCompatibilityTest.sourceIntegrityLifecycleSemanticsHoldAcrossAllEightTracks`. It runs independently against all eight supported Java tracks (`fabric` / `neoforge` across `1.20.1`, `1.21.1`, `26.1.2`, `26.2`) and, per track, verifies:

- generated -> manual takeover, reopen/regenerate preservation, and explicit approved reattach;
- external primary/helper edits followed by a metadata-only update without source replay;
- reopen plus generator regeneration with the code lock still preserving external bytes;
- stale explicit source writes rejecting with `SOURCE_CONTENT_CONFLICT` without revision or byte changes;
- recovery points restoring live primary/helper bytes and removing files created after the point;
- cross-element physical-path collision rejection and failed-create cleanup.

The separate `generatedPathOwnershipPredictionRejectsUnmanagedItemSourceAcrossAllEightTracks` regression verifies non-code generated target prediction on the same eight tracks. The final aggregate Stage 14A run also includes the complete `CodeElementPersistenceTest` suite, stale Workspace Plan rejection, cross-type code-helper/generated-Item collision, late generated-source rollback, documented Item field materialization and the Fabric 1.20.1 Loader template contract. Result: `BUILD SUCCESSFUL`.

The field-contract harness was then rerun against the current source on all eight tracks. Every track committed create `maxStackSize=1` and update `maxStackSize=7`, materialized real upstream `Item.stackSize` values `1 -> 7`, generated `.stacksTo(1)` / `.stacksTo(7)`, and reopened at `7`. This closes CB-AUDIT-05 at the documented field/persistence/generation layer; it is not gameplay evidence.

Fabric 1.20.1 was also regenerated and replayed through a real development `runServer`. Fabric Loader `0.15.11` accepted the generated descriptor, loaded the mod, executed `SurveyPulseMod`, emitted `SURVEY_PULSE_INITIALIZED track=fabric-1.20.1`, and then reached the intentional unaccepted-EULA boundary with Gradle `BUILD SUCCESSFUL`. This closes the reproduced CB-AUDIT-06 Loader-contract failure at the initializer/loading layer only.

## Installed-product Desktop MCP closure replay

The current Windows installer was built from the Stage 14A product-source delta and installed into the clean Windows 11 G7 validation VM through `scripts/Invoke-Stage14SourceIntegrityGuestGate.ps1`. The gate acquires the one-time Desktop MCP configuration through the real product UI, rather than injecting a test token or replacing the desktop server.

Evidence: `evidence/stage14/2026-09-08/source-integrity-clean-windows11.json`.

- source HEAD: `eaf18606e683fcc37a395de83c53e538c947760e`;
- bound product-source delta SHA-256: `0c75d518fb615946aede91e71b3e789b68cbf0c5ef89de38c5416e7f396993e6`;
- installer SHA-256: `713c96ff924cdc848506d63305e78182b6e46373fd63de9685bd85a763d6f4c2`;
- gate result: `passed=true`, six machine-checked steps.

The installed replay proves that a pre-existing unmanaged Java target is not silently claimed, an external IDE edit survives metadata-only MCP mutation, a later stale explicit source write rejects with `SOURCE_CONTENT_CONFLICT`, and a Workspace Plan issued before another external edit fails preview/apply with `WORKSPACE_PLAN_SOURCE_CONFLICT`. Newer source bytes and workspace revision remain unchanged on both stale-write paths. The same replay verifies that the Desktop MCP descriptor does not persist the bearer token, the automation audit does not contain it, and normal desktop close removes the descriptor.

## Fabric 1.21.1 runtime follow-up

The original source-generated Survey Pulse development server failed in `ServerPlayerMixin` because the template targeted `drop(Z)V`. After changing the 1.21.1 generator template to `drop(Z)Z`, the same real `runServer` probe advanced beyond that mixin and exposed a second independent failure: `RepairItemRecipeMixin` expected the obsolete `assemble(CraftingInput)` signature.

The 1.21.1 RepairItemRecipe template was then changed to target `assemble(CraftingInput, HolderLookup.Provider)` and its handler now receives the provider and is cancellable. The two template fixes were mirrored into the disposable previously generated audit fixture solely to re-run the runtime probe; the product fixes live in the generator templates themselves.

The final probe loaded Minecraft 1.21.1 / Fabric Loader, initialized the Survey Pulse mod, emitted `SURVEY_PULSE_INITIALIZED`, and reached the intentional `eula=false` boundary. Gradle reported `BUILD SUCCESSFUL`. This verifies the two reproduced mixin failures for this track and development-source fixture, but not other tracks, a deployed JAR, world creation, multiplayer or gameplay semantics.

## Closure decision and remaining scope

Stage 14A is **closed on the current development line**. The closure is based on the combined focused regressions, full eight-track lifecycle replay, non-code ownership prediction matrix, late rollback replay, current-source field-contract replay, Fabric 1.20.1 real Loader replay, and the current Windows candidate's installed-product Desktop MCP gate. It does not rely on one-track extrapolation or historical Beta 4 product evidence.

The following are deliberately **not** filled in by the Stage 14A closure:

- the earlier Commands remap warning still requires independent semantic/runtime investigation;
- packaged-JAR loading is not proven by development-source startup;
- EULA-boundary initialization is not `server_ready`;
- At the time this Stage 14A evidence was captured, world creation, right-click/range/cooldown behavior, multiplayer isolation and other gameplay were still Stage 14C `not_run`/open; those cells were later exercised independently rather than inferred from this source-integrity record.
- At the time this Stage 14A evidence was captured, Stage 14B, 14C and 14D remained open. Later on 2026-09-08, 14B/14D closed independently and Stage 14C subsequently closed through its separate packaged runtime/gameplay matrices, completing Stage 14 overall. See [Stage 14B/14D closure evidence](./stage14-native-authoring-review-reuse-2026-09-08.md) and [Stage 14C runtime/gameplay closure evidence](./stage14-runtime-gameplay-closure-2026-09-08.md).


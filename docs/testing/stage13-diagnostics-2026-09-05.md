# Stage 13 Diagnostics 2.0 evidence — 2026-09-05

## Scope

This page records closure evidence for `FR-PRODUCTIVITY-03 Diagnostics 2.0` on the Stage 13 development line. The implementation and the final clean-installed Windows failure-to-repair replay are complete.

The first Diagnostics 2.0 slice turns Java compiler failures from file/line-only task output into user-addressable diagnostics while preserving conservative ownership semantics:

- `JAVA_COMPILE_ERROR` keeps its stable diagnostic code, workspace-relative generated-source path and concrete compiler message;
- the task captures the exact workspace snapshot used for generation, so source-to-element resolution is based on the same revision that produced the failed build rather than a later mutable workspace state;
- generated Procedure and one-element generated source files can resolve to the owning stable Mod Element ID when the filename has exactly one unambiguous workspace match;
- aggregate generated classes such as block/item registries deliberately remain unbound to an element instead of guessing ownership;
- compiler diagnostics carry their concrete message through localized message arguments, so translated UI text does not discard the actual compiler error;
- an unambiguously element-bound compiler diagnostic exposes a `定位到元素` action, while every path-bound task diagnostic exposes the current task-log action;
- task-scoped diagnostics are now stored separately from the global diagnostic projection in both the native JCEF bridge and the mock bridge;
- `get_task` refresh/reconnect restores the exact diagnostics belonging to that task instead of requiring the UI to infer ownership from the latest global diagnostic set;
- Task Drawer renders structured diagnostic cards with stable code, localized message, source path and actions, alongside the raw log stream;
- the normal product path is therefore `build failure → failed task → Task Drawer → structured diagnostic → owning Mod Element / task logs`;
- existing Hub behavior remains intentionally scoped: element/path diagnostics are not duplicated as top-level operational alerts merely to make them clickable.

The slice also exposed and closed one previously uncovered localization gap for the production `task.build.building` stage.

## Conservative source ownership

The generated-source resolver is intentionally narrower than a filename fuzzy search:

- Procedure source names resolve through the `<ElementName>Procedure.java` convention;
- generic one-element source files resolve through `<ElementName>Element.java`;
- code elements accept their own normalized source stem or the generic `Element` suffix;
- block/item aggregate classes are excluded because one Java source can represent multiple Mod Elements;
- resolution succeeds only when exactly one workspace element matches; zero or multiple matches leave `elementId` unset.

An unresolved compiler source still retains its source path, concrete message and task-log action. The absence of a safe owning element must never be converted into a speculative navigation target.

## Second slice: bounded generated-source preview

The next Diagnostics 2.0 slice adds the PRD-required `查看生成源码` path without introducing a browser-to-filesystem escape hatch:

- generated Java task diagnostics now expose an `open_source` action in addition to element-location and task-log actions;
- the UI reuses `get_task` with `taskId + sourcePath`; there is no new native bridge that accepts arbitrary local filesystem paths;
- the requested source path must exactly match a path already present in a diagnostic belonging to that same task;
- when the compiler error is emitted, the task gateway normalizes the path inside that task's `executionRoot`, requires `src/main/java/*.java`, verifies the real resolved file remains inside the real task root, and captures the source immediately;
- source snapshots are capped at 256 KiB and `get_task` later returns only that task-time UTF-8 snapshot plus path, language, byte size and diagnostic line metadata; it does not re-read a mutable live workspace file when the user clicks the action;
- Fabric, NeoForge and Resource Pack Gradle-backed task adapters all delegate the same source-preview boundary rather than implementing loader-specific file access;
- Task Drawer renders the preview as read-only text and does not fall back to browser file APIs when the preview is unavailable;
- UI-Core schema and TypeScript contracts model the optional task source preview and the new `open_source` action explicitly;
- the mock bridge only returns a source preview when the requested path exactly matches a task diagnostic, so browser regression evidence follows the same ownership rule as Core.

The security regression deliberately requests an existing generated Java file from the same task staging tree that is **not** referenced by the task diagnostic. Core rejects it, proving that root containment alone is not enough to authorize a source preview. The regression also rewrites the diagnosed live Java file after the failed build and confirms that the returned preview remains the compiler-time snapshot, not the later file contents.

## Third slice: migration review locations

The next slice reuses migration facts that already existed in `MigrationReport` instead of inventing a parallel error classifier:

- every non-`SUPPORTED` migration item is projected into a structured diagnostic whose stable code is the item's existing `reasonCode`;
- `BLOCKED` and `LOST` items are errors, while `MANUAL` and `SUBSTITUTE` items are warnings, so a successful copy that only needs human review remains visibly successful;
- the diagnostic preserves `name`, `type`, `disposition`, `reasonCode` and the report's concrete `nextStep` as localized message arguments;
- migration items already expressed as `/elements/<UUID>` keep that exact path and stable Mod Element ID; no name matching or fuzzy ownership inference is added;
- exact element paths expose an `open_migration_element` action, while future deeper `/elements/<UUID>/...` paths can reuse the existing field-location action contract;
- non-element migration paths remain diagnostic-only rather than being assigned a speculative Mod Element;
- `TracksAndMigrationView` now renders actionable warning-only results with warning styling rather than presenting every actionable diagnostic as an error, and shows the stable reason code beside the localized guidance;
- the existing `MIGRATION_INCOMPLETE` summary is retained for genuinely incomplete reports, so item-level diagnostics add location/detail without replacing the migration-level outcome.

This keeps copy-only migration semantics unchanged: the source workspace is still not mutated, the success/incomplete result is still decided by the migration service, and Diagnostics 2.0 only makes already-proven review locations actionable.

## Fourth slice: resource diagnostics and stable asset locations

Asset health diagnostics now use the same shared `UiCore.Diagnostic` contract as build, element and migration failures instead of remaining Asset Center-only records:

- `INVALID_ASSET_DOCUMENT`, `REFERENCE_PATH_ESCAPE` and `MISSING_ASSET_REFERENCE` are projected by a shared Core mapper with localized message arguments, stable severity and actions;
- the location always points at the existing source asset that contains the invalid document or reference: `/assets/<stableAssetId>` with an `open_asset` action targeting that stable ID;
- a missing target is never assigned a fabricated asset identity; the unresolved target remains message context while navigation goes to the source that can actually be repaired;
- `LIST_ASSETS` returns the same structured diagnostics both inside the asset projection and on the successful `QueryResult`, while MCP `list_assets` and `inspect_asset_references` reuse the exact same mapper;
- Asset Center renders the shared diagnostic code/message/action and `open_asset` clears filters, selects the stable source asset, scrolls it into view and focuses its card;
- the v1.0 asset schema and canonical fixture were brought forward to the already-shipped Stage 13 health/reference shape, including per-asset health, rich reference locations, workspace health summary and shared diagnostic actions.

The source-asset rule is intentionally conservative: a diagnostic may identify an unresolved target path, but only an asset that actually exists can become a navigation target.

## Fifth slice: generator validation field locations

Generator pre-validation already carried stable element IDs and exact internal paths, but the task adapter previously reduced that information to an element-only action. The shared task diagnostic now preserves the stronger proven location:

- validation diagnostics keep their original producer path such as `/elements/<UUID>/values/fields/maxStackSize`;
- when that path is under the diagnosed element, the action becomes `locate_generator_field` and targets the Element Inspector contract path `/fields/maxStackSize`;
- the internal `/values` storage layer is removed only from the action target, not from the diagnostic itself, so producer evidence remains lossless while UI navigation remains valid;
- element-bound diagnostics without a proven editor field continue to use element-only navigation;
- Java compiler diagnostics remain unchanged: generated-source paths still expose owning-element, bounded source-preview and task-log actions rather than being incorrectly treated as generator field paths.

This closes the information-loss gap between Fabric/NeoForge validation producers and the shared Diagnostics 2.0 UI without inventing field ownership for runtime failures that only have logs.

## Sixth slice: deterministic safe repair through WorkspacePlan

Diagnostics 2.0 now has its first repair workflow, deliberately limited to validation failures where the generator can prove one safe bounded value:

- shared diagnostic actions can carry an optional structured payload and use the `preview_repair` action kind; existing actions remain source-compatible and schema-compatible;
- Fabric emits repair values only for bounded numeric validation failures whose nearest legal value is deterministic: item stack size is clamped to `1..64` and block luminance to `0..15`; recipe targets, missing Procedure content and other semantic choices remain navigation-only;
- NeoForge preserves the same explicit repair value while mapping the diagnostic code to its loader-specific identity;
- the task diagnostic packages the proven value as one `update_mod_element` WorkspacePlan operation, binds it to the workspace revision that was actually validated, and requires a recovery point; it does not mutate the workspace directly;
- Task Drawer asks Core to create the plan, shows affected-object counts, aggregate changed paths and semantic diff, and enables apply only while `safety.ready` confirms the required recovery protection is available;
- the generic WorkspacePlan apply path still performs simulation, plan-integrity/stale checks, one atomic revision, one recovery point and persistence rollback on failure;
- element `fields` semantic diffs are now leaf-addressable (for example `/values/fields/maxStackSize`) while conflict `changedPaths` intentionally remain element-granular and Procedure IR remains coarse-grained, avoiding noisy node dumps in existing refactor reviews.

The repair rule is stricter than the location rule: Copperbench may offer a location whenever ownership is proven, but it offers `preview_repair` only when the producer supplies an explicit replacement value. No diagnostic message parsing or heuristic target inference is used.

## Seventh slice: runtime task identity and proven process facts

Diagnostics 2.0 now preserves task ownership for generic runtime failures and distinguishes runtime facts that the process boundary can prove directly:

- generic task failures keep `open_logs.target = failureId`, preserving the native application-log search contract, while the action payload now carries the stable `taskId` used by UI/MCP task projections;
- the real JCEF bridge and Mock bridge group diagnostics by `payload.taskId` first and retain the legacy target-as-task fallback for older events;
- non-zero `runClient`, `runDatagen` and `runGameTest` results are emitted as loader-specific `*_EXITED` diagnostics with the exact exit code;
- `runServer` distinguishes a non-zero process exit from `exitCode=0` without the readiness marker (`*_RUN_SERVER_NOT_READY`);
- these runtime diagnostics deliberately keep element/path ownership unset and expose logs only; no Mod Element, field or repair is inferred from process status alone;
- exceptions that do not expose a deterministic `ProcessResult` fact remain on the existing generic task-failure path instead of being mislabeled.

This slice also aligns external MCP and reconnect/replay behavior with the desktop UI: a task diagnostic can remain searchable by failure ID while still belonging unambiguously to one task.

## Eighth slice: Procedure node and port ownership

Procedure diagnostics now preserve the strongest ownership already proven by the Core-owned Procedure IR validator instead of collapsing graph failures to an element or persistence path:

- `ProcedureIr.ValidationIssue` node IDs and ports are exposed through a shared `open_procedure_node` diagnostic action with structured `{ nodeId, port }` payload;
- desktop navigation stores a route-safe Procedure focus request, opens the owning Procedure, then selects and centers the matching Blockly node after the workbench is mounted; it does not depend on a short DOM timing guess;
- Fabric generator pre-validation reuses the same `ProcedureIrCodec` only for workspaces that already carry modern `procedureIr` or non-empty `procedurexml`, preserving legacy Procedure compatibility while adding exact node/port diagnostics for modern graphs;
- NeoForge keeps the same Procedure diagnostic identity because its loader mapping changes only Fabric-prefixed codes;
- desktop MCP query tools serialize the same Core result unchanged, so `preview_procedure_change` and task projections retain the same node/port action contract for external Agents;
- node-level location remains navigation-only. A missing Procedure target is a semantic choice, so no repair value or automatic mutation is invented;
- the reference index now excludes serialized `procedurexml` from generic text scanning when canonical Procedure IR is available, preventing duplicate resource/reference edges beside `ProcedureIr.dependencies`.

This closes the current Procedure node-location gap while retaining the conservative Diagnostics rule: stable node and port identities come from the Procedure producer, never from diagnostic-message parsing.

## Ninth slice: migration rebuild generator-field ownership

Loader migration rebuild validation now preserves the exact ownership facts already emitted by the destination generator instead of collapsing every failed rebuild into the generic `MIGRATION_REBUILD_FAILED` diagnostic:

- Fabric/NeoForge validation failures retain their stable loader-specific diagnostic code, exact `/elements/<UUID>/...` path, stable Mod Element ID and any explicit deterministic `repairValue` supplied by the generator;
- the migration application layer projects those producer facts directly into the shared diagnostic contract rather than wrapping them in an implementation exception;
- element field paths reuse the existing `locate_generator_field` action and translate internal `/values/...` storage paths into valid Element Inspector field targets;
- Procedure node/port paths reuse the shared `open_procedure_node` action when the producer supplies a valid node identity;
- rebuild failures without proven element/path ownership keep the existing generic migration failure path, so exception-only failures do not acquire speculative navigation targets;
- copy-only migration semantics remain unchanged: the source workspace is not mutated and a failed destination rebuild remains a failed rebuild projection rather than an implicit repair.

This extends Diagnostics 2.0 across the migration rebuild boundary without adding a second ownership classifier. The generator remains the source of truth for location and safe-repair facts.

## Verification

- `Fabric1211TaskGatewayTest.failedBuildExtractsJavaCompilerErrorsIntoStructuredDiagnostics` — passed; a Procedure compiler error resolves to stable element ID `00000000-0000-4000-8000-000000000004`, preserves path/line/message, exposes element-location/generated-source/task-log actions, returns its bounded compiler-time source snapshot even after the live file is rewritten, and rejects a different existing generated Java file that is not referenced by the task diagnostic.
- `Fabric1211TaskGatewayTest.unresolvedCompilerSourceKeepsFileAndLogsButNeverGuessesAnElement` — passed; aggregate `ModBlocks.java` keeps path/log navigation while leaving `elementId` unset and omitting the locate-element action.
- the forced Gradle targeted run completed `BUILD SUCCESSFUL`; its normal `buildUiShell` dependency also passed TypeScript/Vite and the Chinese localization gate (`206/206`).
- `npm test` in `ui-core` — `20/20` passed after installing that package's declared test dependencies; all schemas and all canonical mock scenarios, including the new `compile-diagnostic` scenario, validate.
- `npx playwright test e2e/scenarios.spec.ts --grep compile-diagnostic` — `2/2` passed across Chromium and compact-1366; the UI follows failed task → Task Drawer → `JAVA_COMPILE_ERROR` → bounded generated-source preview → owning Mod Element.
- `npx playwright test e2e/scenarios.spec.ts` — `28/28` passed across Chromium and compact-1366, preserving existing validation, permission, bridge-recovery, external-process and task scenarios.
- `npm test` in `ui-core` — final `20/20` passed with the current Stage 13 asset health/reference schema and a canonical shared `MISSING_ASSET_REFERENCE -> open_asset` fixture.
- `AssetQueryProjectionTest` and the authenticated `McpHttpServerTest` asset path — passed; UI-Core `LIST_ASSETS`, MCP `list_assets` and MCP `inspect_asset_references` all expose the same stable source-asset diagnostic.
- `npx playwright test e2e/asset-browser.spec.ts` — `36/36` passed across Chromium and compact-1366, including `MISSING_ASSET_REFERENCE -> open_asset -> stable source asset` navigation while preserving import, batch, move, safe-unused and Blockbench flows.
- forced `--rerun-tasks` verification of the asset projection, authenticated MCP asset tooling, generator validation field location and Java compile diagnostic regression — `BUILD SUCCESSFUL`; the generator validation action targets `/fields/maxStackSize` while compiler-source ownership remains unchanged.
- the forced Gradle run rebuilt the UI shell and passed the Chinese localization gate at `212/212`.
- `Stage67ApplicationServiceTest.loaderMigrationSurfacesManualItemsAsElementAddressableDiagnostics` — passed; a successful Fabric-to-NeoForge copy with a loader-exclusive Procedure field remains `committed` while surfacing `LOADER_EXCLUSIVE_FIELDS_PRESERVED` as a warning bound to the exact `/elements/<UUID>` path and `open_migration_element` action.
- `Stage67ApplicationServiceTest.loaderMigrationRequiresApprovalAndDoesNotMutateTheSourceWorkspace` — passed unchanged, preserving explicit approval, source immutability and generated target evidence.
- `npm run build` in `ui-shell` — passed TypeScript/Vite and the Chinese localization gate (`207/207`).
- `npx playwright test e2e/u3-tracks-migration.spec.ts --grep "previews loader migration"` — `2/2` passed across Chromium and compact-1366; a successful migration renders the review warning and the action navigates to `Copper Lamp`.
- `npx playwright test e2e/u3-tracks-migration.spec.ts` — `12/12` passed across Chromium and compact-1366.
- the post-migration full `npx playwright test e2e/scenarios.spec.ts` run — `28/28` passed, preserving the compiler-diagnostic and all canonical scenario behavior.
- `Fabric1211TaskGatewayTest.deterministicValidationRepairPreviewsAndAppliesThroughRecoveryProtectedWorkspacePlan` — passed; `maxStackSize=0` produces an explicit repair value `1`, Core plans an `update_mod_element`, semantic diff reaches `/values/fields/maxStackSize`, apply advances exactly one workspace revision, stores `1`, and creates exactly one recovery point. The same test then changes the live value to `32` after validation and proves the old repair is rejected as `WORKSPACE_PLAN_STALE` without overwriting `32`.
- `NeoForge1211GeneratorTest.deterministicNumericValidationRepairsSurviveNeoForgeCodeMapping` — passed; `maxStackSize=99` maps to `NEOFORGE_ITEM_STACK_INVALID` while preserving the deterministic repair value `64` and exact element field path.
- the combined `Fabric1211TaskGatewayTest` + `NeoForge1211GeneratorTest` + `WorkspacePlanEngineTest` run — `BUILD SUCCESSFUL`; existing intentional export/path, compiler, JDK and datagen failure tests remained expected failures inside passing tests, and protected Procedure plan regressions remained green.
- `npm test` in `ui-core` — final `20/20` passed with the canonical `generator-repair` scenario and the shared `preview_repair` action payload schema.
- `npx playwright test e2e/scenarios.spec.ts --grep generator-repair` — `2/2` passed across Chromium and compact-1366; failed task → structured diagnostic → repair plan → semantic diff → explicit apply is visible in Task Drawer.
- full `npx playwright test e2e/scenarios.spec.ts` — `30/30` passed across Chromium and compact-1366 after adding the safe-repair scenario.
- full `npx playwright test e2e/accessibility.spec.ts` — `22/22` passed across Chromium and compact-1366, including the >=32px interaction-target baseline used by the new repair controls.
- the final UI build passed TypeScript/Vite and the Chinese localization gate at `220/220` referenced keys.
- `Fabric1211TaskGatewayTest.runtimeProcessFailuresExposeExitAndReadinessFactsWithoutGuessingElements` — passed; `runClient` exit `7` emits `FABRIC_RUN_CLIENT_EXITED` with `exitCode=7`, while an otherwise successful server process without readiness emits `FABRIC_RUN_SERVER_NOT_READY`; neither diagnostic invents element ownership.
- `Fabric1211TaskGatewayTest.missingBundledJdkBecomesStructuredTaskDiagnostic` — passed with the generic failure action retaining the failure ID target and carrying the real task ID in its payload.
- the full `Fabric1211TaskGatewayTest` + `DesktopMcpAgentLoopTest` run — `BUILD SUCCESSFUL`; existing compile, export, missing-JDK, datagen, safe-repair and MCP fail/locate/repair/rebuild behavior remained green.
- `npm test` in `ui-core` — `20/20` passed after updating the canonical external-process scenario to the production-shaped task/failure identity contract.
- `npx playwright test e2e/scenarios.spec.ts --grep external-process-exited` — `2/2` passed across Chromium and compact-1366; the runtime diagnostic is visible inside the correct failed-task drawer even though the log target remains a failure ID.
- the final full `npx playwright test e2e/scenarios.spec.ts` run — `30/30` passed; the final accessibility run — `22/22` passed.
- the final runtime-slice UI build passed TypeScript/Vite and the Chinese localization gate at `221/221` referenced keys.
- `WorkspaceApplicationServiceTest.procedurePreviewExposesStableNodeAndPortNavigationAction` — passed; Core Procedure preview preserves the exact node/port path and exposes `open_procedure_node` with stable node ID and `procedureId` port payload.
- `Fabric1211TaskGatewayTest.modernProcedureValidationPreservesNodeAndPortThroughTaskDiagnostics` — passed; modern Procedure IR validation reaches the failed task without losing node or port ownership.
- `NeoForge1211GeneratorTest.procedureNodeAndPortIdentitySurvivesNeoForgeValidationMapping` — passed; NeoForge parity keeps the shared Procedure diagnostic identity and exact node/port path.
- `DesktopMcpAgentLoopTest.externalAgentCanPlanAndApplyProtectedProcedureExtraction` — passed with an external MCP `preview_procedure_change` assertion for the same `open_procedure_node` target/payload, while protected extraction remains green.
- `WorkspaceReferenceIndexTest.indexesProcedureDependenciesWithoutTreatingGraphNodeIdsAsWorkspaceReferences` and `stage13ProcedureEditorProjectsSharedVariableResourceAndCallSymbols` — passed after preventing serialized Blockly XML from duplicating canonical Procedure dependency edges.
- `npm test` in `ui-core` — `20/20` passed with the new `open_procedure_node` action kind and canonical `procedure-node-diagnostic` scenario.
- `npm run build` in `ui-shell` — passed TypeScript/Vite and the Chinese localization gate at `222/222` referenced keys.
- `npx playwright test e2e/scenarios.spec.ts --grep procedure-node-diagnostic` — `2/2` passed; failed task → Procedure diagnostic → exact Procedure node selection/centering works in Chromium and compact-1366.
- full `npx playwright test e2e/scenarios.spec.ts` — `32/32` passed; full `e2e/accessibility.spec.ts` — `22/22` passed.
- the final combined `WorkspaceApplicationServiceTest` + `WorkspaceReferenceIndexTest` + `Fabric1211TaskGatewayTest` + `NeoForge1211GeneratorTest` + `DesktopMcpAgentLoopTest` run — `BUILD SUCCESSFUL`.
- forced `--rerun-tasks` `Stage67ApplicationServiceTest` + `LoaderMigrationRebuildServiceTest` — `BUILD SUCCESSFUL`; `loaderMigrationRebuildPreservesGeneratorFieldLocation` keeps `NEOFORGE_ITEM_STACK_INVALID` bound to the exact element and `/fields/maxStackSize` editor target, while `validationFailurePreservesStableElementFieldAndRepairFact` proves the rebuild service retains the generator's path, element ID and explicit repair value `1`.
- `git -c core.whitespace=cr-at-eol diff --check` — passed before evidence finalization.

## Installed-product closure evidence

The final product boundary was replayed by `scripts/Invoke-Stage13DiagnosticsGuestGate.ps1` on clean Windows 11 against exact installed candidate `09bda9c6d8c6a37bbcf15d7cf7c964c68abea2a4`, installer SHA-256 `bc59026c08c635b7b06c90e434fe3de80d639e4042288b0a20b2445e16ad6b1a`. The default gate returned `passed=true` with seven machine-checked steps:

1. installed validation returned `FABRIC_ITEM_STACK_INVALID` bound to stable element `00000000-0000-4000-8000-000000013001`, exact producer path `/elements/.../values/fields/maxStackSize`, editor target `/fields/maxStackSize` and explicit safe repair value `1`;
2. the real `Workspace setup for selected generator` progress dialog was observed and allowed to finish naturally before UI interaction, reusing the established clean-Windows generator-setup rule instead of clicking through a modal progress boundary;
3. the installed product's window-scoped `Ctrl+Shift+M` path opened the real AI/MCP page and `Copy URL` matched the active Desktop MCP descriptor;
4. the one-time bearer credential came only from the visible installed UI, the persisted descriptor contained no token, and the clipboard was cleared before repair;
5. the external MCP client previewed/applied the producer-supplied WorkspacePlan with a recovery point, moving workspace revision `1 -> 2` and changing only `/values/fields/maxStackSize`;
6. installed headless `validate` and `build` both succeeded after repair; the build process returned authoritative exit code `0` and its Gradle log contained a successful build;
7. installed migration preview retained `LOADER_EXCLUSIVE_FIELDS_PRESERVED` with exact `/elements/00000000-0000-4000-8000-000000013001` review location and `manual` disposition.

Machine evidence is stored at `evidence/stage-13/2026-09-07/diagnostics-clean-windows11.json`. It records final revision `2`, no persisted bearer credential, and an empty clipboard at gate completion.

`FR-PRODUCTIVITY-03` is therefore closed. Future diagnostics may add more proven locations or producer-supplied repairs, but they remain additive follow-up work. The governing rule remains conservative: a diagnostic may lose a navigation shortcut when ownership cannot be proven, but it must not invent an element, field, Procedure node or asset target.

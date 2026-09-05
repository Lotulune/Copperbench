# Stage 13 Diagnostics 2.0 evidence — 2026-09-05

## Scope

This page tracks `FR-PRODUCTIVITY-03 Diagnostics 2.0` on the active Stage 13 development line. The requirement is **not closed yet**.

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

## Verification

- `Fabric1211TaskGatewayTest.failedBuildExtractsJavaCompilerErrorsIntoStructuredDiagnostics` — passed; a Procedure compiler error resolves to stable element ID `00000000-0000-4000-8000-000000000004`, preserves path/line/message, exposes element-location/generated-source/task-log actions, returns its bounded compiler-time source snapshot even after the live file is rewritten, and rejects a different existing generated Java file that is not referenced by the task diagnostic.
- `Fabric1211TaskGatewayTest.unresolvedCompilerSourceKeepsFileAndLogsButNeverGuessesAnElement` — passed; aggregate `ModBlocks.java` keeps path/log navigation while leaving `elementId` unset and omitting the locate-element action.
- the forced Gradle targeted run completed `BUILD SUCCESSFUL`; its normal `buildUiShell` dependency also passed TypeScript/Vite and the Chinese localization gate (`206/206`).
- `npm test` in `ui-core` — `20/20` passed after installing that package's declared test dependencies; all schemas and all canonical mock scenarios, including the new `compile-diagnostic` scenario, validate.
- `npx playwright test e2e/scenarios.spec.ts --grep compile-diagnostic` — `2/2` passed across Chromium and compact-1366; the UI follows failed task → Task Drawer → `JAVA_COMPILE_ERROR` → bounded generated-source preview → owning Mod Element.
- `npx playwright test e2e/scenarios.spec.ts` — `28/28` passed across Chromium and compact-1366, preserving existing validation, permission, bridge-recovery, external-process and task scenarios.
- `Stage67ApplicationServiceTest.loaderMigrationSurfacesManualItemsAsElementAddressableDiagnostics` — passed; a successful Fabric-to-NeoForge copy with a loader-exclusive Procedure field remains `committed` while surfacing `LOADER_EXCLUSIVE_FIELDS_PRESERVED` as a warning bound to the exact `/elements/<UUID>` path and `open_migration_element` action.
- `Stage67ApplicationServiceTest.loaderMigrationRequiresApprovalAndDoesNotMutateTheSourceWorkspace` — passed unchanged, preserving explicit approval, source immutability and generated target evidence.
- `npm run build` in `ui-shell` — passed TypeScript/Vite and the Chinese localization gate (`207/207`).
- `npx playwright test e2e/u3-tracks-migration.spec.ts --grep "previews loader migration"` — `2/2` passed across Chromium and compact-1366; a successful migration renders the review warning and the action navigates to `Copper Lamp`.
- `npx playwright test e2e/u3-tracks-migration.spec.ts` — `12/12` passed across Chromium and compact-1366.
- the post-migration full `npx playwright test e2e/scenarios.spec.ts` run — `28/28` passed, preserving the compiler-diagnostic and all canonical scenario behavior.
- `git -c core.whitespace=cr-at-eol diff --check` — passed before evidence finalization.

## Remaining `FR-PRODUCTIVITY-03` work

This slice does not close Diagnostics 2.0. Remaining work includes:

- extend the stable-location model beyond compiler and migration review items to generator, resource and MCP failures rather than leaving them as task/log-only errors;
- deepen element locations into field paths, Procedure node IDs and asset IDs where the producer can prove that relationship;
- provide repair guidance for deterministic failure classes without turning heuristics into false guarantees;
- route eligible automatic repairs through previewed semantic workspace plans and recovery points instead of direct mutation;
- preserve the same diagnostic identities and locations through UI, desktop MCP, headless and reconnect/replay paths;
- add representative installed-product failure → location → repair evidence for the broader generator/resource/migration classes before formal closure.

The governing rule remains conservative: a diagnostic may lose a navigation shortcut when ownership cannot be proven, but it must not invent an element, field, Procedure node or asset target.

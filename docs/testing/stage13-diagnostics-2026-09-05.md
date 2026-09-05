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

## Verification

- `Fabric1211TaskGatewayTest.failedBuildExtractsJavaCompilerErrorsIntoStructuredDiagnostics` — passed; a Procedure compiler error resolves to stable element ID `00000000-0000-4000-8000-000000000004`, preserves path/line/message, and exposes element-location plus task-log actions.
- `Fabric1211TaskGatewayTest.unresolvedCompilerSourceKeepsFileAndLogsButNeverGuessesAnElement` — passed; aggregate `ModBlocks.java` keeps path/log navigation while leaving `elementId` unset and omitting the locate-element action.
- the forced Gradle targeted run completed `BUILD SUCCESSFUL`; its normal `buildUiShell` dependency also passed TypeScript/Vite and the Chinese localization gate (`204/204`).
- `npm test` in `ui-core` — `20/20` passed after installing that package's declared test dependencies; all schemas and all canonical mock scenarios, including the new `compile-diagnostic` scenario, validate.
- `npx playwright test e2e/scenarios.spec.ts --grep compile-diagnostic` — `2/2` passed across Chromium and compact-1366; the UI follows failed task → Task Drawer → `JAVA_COMPILE_ERROR` → owning Mod Element.
- `npx playwright test e2e/scenarios.spec.ts` — `28/28` passed across Chromium and compact-1366, preserving existing validation, permission, bridge-recovery, external-process and task scenarios.
- `git -c core.whitespace=cr-at-eol diff --check` — passed before evidence finalization.

## Remaining `FR-PRODUCTIVITY-03` work

This slice does not close Diagnostics 2.0. Remaining work includes:

- map generator, resource, migration and MCP failures to the same stable diagnostic-location model rather than leaving them as task/log-only errors;
- add a first-class `查看生成源码` action and safe source-location surface for path diagnostics;
- deepen element locations into field paths, Procedure node IDs and asset IDs where the producer can prove that relationship;
- provide repair guidance for deterministic failure classes without turning heuristics into false guarantees;
- route eligible automatic repairs through previewed semantic workspace plans and recovery points instead of direct mutation;
- preserve the same diagnostic identities and locations through UI, desktop MCP, headless and reconnect/replay paths;
- add representative installed-product failure → location → repair evidence for the broader generator/resource/migration classes before formal closure.

The governing rule remains conservative: a diagnostic may lose a navigation shortcut when ownership cannot be proven, but it must not invent an element, field, Procedure node or asset target.

# Stage 14B / 14D closure evidence — 2026-09-08

## Scope

This record closes **Stage 14B native authoring** and **Stage 14D review/reuse** on the current development line. At the time this evidence was first captured, Stage 14C and Stage 14 overall were still open; they were subsequently closed by the independent [Stage 14C runtime/gameplay closure evidence](./stage14-runtime-gameplay-closure-2026-09-08.md). This document does not retroactively use 14B/14D engineering evidence as gameplay evidence.

## Stage 14B — native authoring loop

The product now supports cold start without an already-open `.mcreator` workspace:

- `bootstrap list-generators` works without an existing workspace.
- `bootstrap create-workspace` requires a local user approval prompt; there is no agent self-approval flag.
- normal headless/Desktop MCP entry points reopen the created workspace.
- `environment` / `get_workspace_environment` exposes generator, Loader, Gradle, JDK and source/resource roots.
- the application stays on JBR 25 while Java-21 workspace tracks use the bundled `jdk21` sidecar.
- native Java/resources/tests use normal files; deliberate compile failure → diagnosis → file repair → rebuild → reopen is covered by product entry points.

Primary regressions: `Stage14BNativeAuthoringLoopTest`, `BootstrapProductLauncherTest`, the opt-in eight-track `NewWorkspaceGeneratorGoldenBuildTest.stage14BNativeMultiFileModulesBuildAcrossEveryJavaTrack`, and `WindowsDistributionLayoutTest`. The eight-track golden preserves native bytes/hooks through regeneration, builds each real Gradle project, and checks native classes in the JAR. This is engineering/build evidence, not gameplay evidence.

## Stage 14D — review, permission and recovery

Workspace Plan remains the single atomic mutation boundary:

- semantic diff, changed paths, grouped review and high-impact summaries derive from the validated target state.
- workspace-authorized plans do not receive an invented AI-only approval step; read-only profiles fail before mutation.
- protected plans require recovery; durable failure leaves workspace state unchanged.
- ordered content is bound by `planId` and a session-issued HMAC `planToken`; stale, forged or tampered plans fail before mutation.
- the experimental extension fixture explicitly declares an experimental compatibility boundary rather than a stable third-party ABI.

Primary regressions are the high-impact, read-only/stale, recovery, durable-failure and tamper cases in `WorkspacePlanEngineTest`, plus `ExperimentalExtensionManifestTest`.

## FR-ADV-05 — local reusable templates

The missing reusable-template requirement is now implemented through the same signed Workspace Plan boundary:

- `create_local_template` stores a local template under `~/.copperbench/templates` by default.
- `list_local_templates` exposes the local catalog.
- `preview_local_template_instantiation` verifies the template, requires a matching target generator, allocates fresh element IDs, rewrites contained UUID references, and returns a normal signed Workspace Plan.
- selected elements/Procedures must be self-contained for structured element/registry dependencies; dangling or external dependencies are rejected.
- embedded assets are bounded to known workspace asset roots, reject workspace escape/symlink traversal, and carry content plus SHA-256.
- asset bytes/path/hash and template metadata participate in `planId` / `planToken`, semantic diff and changed paths.
- real MCreator persistence uses the same file snapshot as element mutations; target asset conflicts fail during preview, while apply uses symlink checks, atomic writes and rollback.

Regression coverage added in this change:

- `LocalWorkspaceTemplateServiceTest.reusableBundleIsIntegrityCheckedAndRemapsContainedElementIds`
- `LocalTemplateWorkspacePlanIntegrationTest.procedureAndAssetRoundTripThroughSignedWorkspacePlan`

The real-workspace integration creates a Procedure plus an asset in one source MCreator workspace, exports the bundle, lists and previews it from a second workspace, proves an existing target asset is rejected with `WORKSPACE_PLAN_SOURCE_CONFLICT`, proves signed-plan Base64 tampering is rejected before mutation, then applies the original plan with a recovery point and verifies the Procedure/asset survive reopen with a fresh element ID.


The opt-in Stage 14B eight-track native multi-file golden was rerun on the final source state after the template work: **8 tests, 0 failures, 0 errors, 0 skipped** (`fabric/neoforge` for `26.2`, `26.1.2`, `1.21.1`, and `1.20.1`). The Java routing observed during that run also exercised JBR 25 for current tracks and compatibility JDKs for older tracks rather than forcing every workspace onto the application runtime.

During this integration run a real fresh-workspace defect was also found and fixed: generated-path prediction could dereference a generator whose `generatorConfiguration` had not initialized yet. Fresh workspace planning now treats that state as no predicted generated paths instead of throwing `NullPointerException`.

## Current-source regression results

The targeted `LocalWorkspaceTemplateServiceTest`, `LocalTemplateWorkspacePlanIntegrationTest`, and `WorkspacePlanEngineTest` suite completed with `BUILD SUCCESSFUL` after the template implementation and fresh-workspace fix.
The wider affected-area `WorkspacePersistenceCompatibilityTest`, `CodeElementPersistenceTest`, and `McpHttpServerTest` suite also completed with `BUILD SUCCESSFUL`.

Expected conflict/rollback error logs inside these tests are deliberate assertions and are not test failures.

## Closure boundary

- **14B: closed** on the current development line.
- **14D: closed** on the current development line.
- **14C: later closed** by the separate packaged runtime/gameplay matrix; see [Stage 14C closure evidence](./stage14-runtime-gameplay-closure-2026-09-08.md).
- **Stage 14 overall: now closed** on the current development line because 14A/14B/14C/14D each satisfy their own DoD.

A release/installer candidate must still be rebuilt and have its installed-product gate rebound to the final source state after any Stage 14 source change. Source-level Wave closure is not a substitute for final candidate provenance.

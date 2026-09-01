# Stage 11 Java Mod Element Support Evidence

Date: 2026-08-31

Final local generator verification: 2026-09-01

## Implemented scope

- `ElementCoverageCatalog` contains the seven existing first-party types plus all 30 Java types listed in PRD-NEXT section 7.4.
- `WorkspaceApplicationService` exposes the same type list to UI, MCP, and headless CRUD. New elements receive type-specific primitive defaults and editor controls.
- `MCreatorWorkspaceMutationGateway` resolves the upstream `ModElementType` and storage class for all Java types, maps editable primitive fields, and preserves unknown fields through metadata merge.
- `UpstreamWorkspaceImportService` and `LoaderMigrationService` use the shared coverage catalog instead of the former four-type slice.
- `Fabric1211Generator` validates the complete Java catalog and emits a compile-safe representation source for long-tail types. `code` elements retain their dedicated source-file lifecycle.
- Long-tail generator output also includes `copperbench/elements/<name>.json` with the complete type, identity, and value projection; the MCP create schema exposes the same 37-type enum.
- Diagnostic bundles recursively redact sensitive JSON keys, including nested and whitespace-containing values, before adding user-confirmed workspace files.
- Task snapshots use the same recursive JSON redactor as workspace reproduction files.

## Automated evidence

- A machine comparison of PRD-NEXT 7.4 and `product-status.json` reports `prd30=30`, with no missing or extra Java types.
- `npm run build` in `ui-shell`: passed.
- `node ui-core/scripts/validate.mjs`: 12 schemas and 15 scenarios passed.
- `node scripts/verify-product-status.mjs`: passed.
- `git diff --check`: passed.
- Playwright full UI matrix: 150/150 passed in Chromium, compact viewport, accessibility, JCEF bridge, migration, Stage 9, and visual/DPI projects. The four visual baselines were regenerated after the intentional all-type filter expansion and then revalidated.
- Direct JDK 25 compilation of changed Java service/gateway/generator sources: passed.
- Direct generator smoke with all 37 catalog types: generated 33 long-tail representations; all 34 generated `*Element.java` files compiled with JDK 21.
- Direct diagnostic bundle smoke: nested `password` and `token` values were replaced with `[REDACTED]`.
- MCP contract test covers `get_element_coverage` and asserts 37 first-party types with no unsupported Java types.
- 2026-09-01 final status/UI verification: `node scripts/verify-product-status.mjs` passed (8 generators, 19 gates), `node ui-core/scripts/validate.mjs` passed (12 schemas / 15 scenarios), and `ui-shell` production build passed with the Chinese localization gate at 125/125.
- 2026-09-01 Stage 11 core Java regression: 25 targeted tests passed across `ElementCoverageCatalogTest`, `GeneratorElementCapabilityCatalogTest`, `WorkspacePersistenceCompatibilityTest`, `UpstreamWorkspaceImportServiceTest`, `LoaderMigrationServiceTest`, `Fabric1211GeneratorTest`, and `ReleaseManifestTest`. The compact evidence summary is [stage11-core-regression.json](../../evidence/stage-11/2026-09-01/stage11-core-regression.json).

## Final generator gate

Plugin-workspace Minecraft registration now runs through `MCreatorWorkspaceMutationGateway` when a generator configuration and source root exist. First-party Fabric/NeoForge projection no longer overwrites an existing `.mcreator` plugin workspace.

Full-type unknown-field persist/update round-trip is covered by `WorkspacePersistenceCompatibilityTest.copperbenchSavePreservesUnknownFieldsForEveryFirstPartyType`. Generator × type inapplicability uses explicit reason codes from `GeneratorElementCapabilityCatalog`.

The 8-generator Gradle/JAR matrix is `NewWorkspaceGeneratorGoldenBuildTest` with `-Dcopperbench.stage11.workspaceGeneratorBuild=true`. The final 2026-09-01 local evidence is in [eight-generator-matrix.json](../../evidence/stage-11/2026-09-01/eight-generator-matrix.json), with per-generator capability JSON and Gradle logs in the same directory.

All eight generators now pass end-to-end workspace generation, Java compilation, resource processing, JAR/remap/build tasks, and final JAR discovery: `fabric-26.2`, `neoforge-26.2`, `fabric-26.1.2`, `neoforge-26.1.2`, `fabric-1.21.1`, `neoforge-1.21.1`, `fabric-1.20.1`, and `neoforge-1.20.1`. The outer Stage 11 run completed with `BUILD SUCCESSFUL` and 8/8 dynamic tests passing.

The golden harness now also validates generated mixin configuration against the produced JAR: every class named by `mixins`, `client`, or `server` entries must exist in the JAR. This caught two compile-green/runtime-broken cases during finalization: stale Fabric 1.20.1 Armor/Biome mixin declarations and a NeoForge 1.21.1 `BiomeSourcePresetMixin` generated as a per-element template instead of a global template. Both are fixed and the complete 8-generator matrix passes with the new gate enabled.

Direct `gradlew.bat` in a restricted job may still fail at JDK loopback `PipeImpl`. The successful 2026-09-01 Stage 11 matrix was executed through the host-capable Gradle path used by the golden harness.

Linux and macOS installers remain outside Stage 11 and are deferred to the next PRD stage.

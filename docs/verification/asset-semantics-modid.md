# Asset semantics and GameTest mod identity regression

Baseline: `48e6626a11cece44af5d9613d369f9441374a9b1`. Verified on Windows, 2026-09-13.

## Changes

- `BbmodelDocument.parse` is the shared reader for texture tables, image sources and face bindings. Texture metadata is never recursively interpreted as a filename. Malformed tables, unresolved faces, missing sources and corrupt embedded images produce explicit diagnostics.
- `BbmodelRoundTripService` tracks texture identity separately from image sources. Deletion, changed image/reference and changed face bindings require review. Its report provides inspection, acceptance of intentional changes and restoration options; comparison does not mutate or reject imports.
- `AssetWorkspaceService` resolves real local files relative to the model and preserves exact JSON pointers. Embedded images and alternate existing paths can satisfy missing-path fallbacks. External namespaces/files are reported as unverified warnings, not missing local PNGs. Local image references have a distinct `FILE_PATH` graph kind, including in the UI contract; moves preserve those references.
- `WorkspaceModIdentity.resolve` reads `workspaceSettings.modid`, the field written by workspace creation. Legacy `copperbench.modId` is used only without native settings. Malformed native settings never fall back to the display name. The historical name default remains solely for old projections with neither settings container. Fabric/NeoForge metadata, GameTest preparation and generation logs use this resolution.

## Evidence

The final targeted Java run completed with **114 passed, 0 failed, 5 skipped**
(27 suites, 119 cases). Skips are existing platform/opt-in probes. The separate
real loading smoke passed. UI contract tests: **22 passed**. The UI production
build, TypeScript compilation and localization gate also passed.

`src/test/resources/assets/blockbench/README.md` records the provenance and hashes of the original models and the unmodified output of **File > Save Project As** in desktop Blockbench 5.1.6. The native saved model has format version `5.0`, 13 cubes, 78 face bindings and one embedded PNG.

Regression tests cover both review models and the native editor save; removing the only texture; corrupt/removing/replacing embedded image data; texture reorder with corrected indices; UUID bindings; intentional element deletion; malformed/ambiguous IDs; local files with spaces; missing local images; external paths/namespaces/URLs; fallback availability; and texture/model moves.

`WorkspaceModIdentityTest` covers display name `Copper Signal Lantern` and ID `copper_signal` across all eight Java tracks, including stale product overrides, malformed native settings, generated metadata, starter source and the actual `PREPARE_GAME_TESTS` operation.

The opt-in `GameTestModIdentitySmokeTest` copies the review project's native implementation into a temporary workspace without its existing tests, invokes product test preparation and runs the packaged JAR through the product gateway. The new assertion passes: **1 acceptance test executed, 1 passed, 0 failed, 0 skipped**, process exit 0, `sourceCurrentAtCompletion=true`.

- Tested JAR SHA-256: `d8d717633ec6ffe08ec0a96eb967308e7e05b4ee5f4e1382cdecaae4759503fe`.
- Report SHA-256: `9ece69238c724bbbc4c56f91e730c61554685f3da39e69d89827933e5fc0e344`.
- Preserved local evidence: `build/mod-identity-smoke/` (generated test, product task JSON/log and native XML report).
- This new live check verifies mod loading on Fabric 1.21.1. It does not claim eight-track gameplay execution or a rerun of the original eight behavior assertions.

## Reproduction

Run the asset tests, `WorkspaceModIdentityTest`, `GameTestWorkflowTest`, Fabric/NeoForge generator tests, workspace creation/mapping tests and `BlockbenchEditApplicationServiceTest` with Gradle. Run `npm --prefix ui-core test` for the schema contract. The Gradle resource task also runs the UI production build and its localization/type checks.

For the live check, set `COPPERBENCH_MOD_ID_SMOKE_SOURCE` to the original `copper_signal` review project and run `GameTestModIdentitySmokeTest`. The original project is read only. On this machine the test invocation needed a process-local short `jdk.net.unixdomain.tmpdir` via `JAVA_TOOL_OPTIONS` for JDK/Gradle IPC; no global environment configuration was changed.

Layer compositing is not reconstructed by this reader and is explicitly marked for review. External assets are not read outside the workspace or fetched over the network.

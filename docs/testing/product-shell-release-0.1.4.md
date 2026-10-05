# Copperbench 0.1.4 Windows Product Shell release

## Authorization and scope

On 2026-10-06 the user directly authorized pushing the accepted work and publishing a new release. The target is Windows 11 x64, tag `v0.1.4`. `delivery.stableRelease.status = ready` and `decision = approved` record that authorization; they do not claim that final CI, packaging or publication has already completed.

Linux remains at the published [0.1.3 release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.3-linux-stable). This change does not authorize or build a new Linux release. The original Linux 0.1.2 installed-acceptance records and the published Beta provenance remain unchanged.

## Integrated changes

- Product Shell navigation, workspace overview, compact layouts and concise Chinese/English copy; version/loader support states and migration/refactor actions remain available.
- Generic workspace source browsing, bounded entrypoint/registration/reference evidence, manual-file editing, generated-file ownership, revision/hash conflicts, history/events and unsaved-draft protection.
- A relationship graph built from existing Core projections, with bounded rendering, search/reveal, unresolved references and display-only node movement.
- Core-backed image/model-document previews and desktop appearance/editor preferences.
- The remote CI/reliability changes from PR #88: check selection with unchanged required contexts, removal of duplicate UI builds, shared export/migration file boundaries, SDK response validation and read-only retry rules, and ordered task-state handling. See the [CI and MCP review](../maintenance/ci-and-mcp-review-2026-10-05.md) for its original source and run references.

## Development verification already observed

These are targeted receipts from the development working tree before the release integration. They do not establish that the final merged commit or its packaged binaries passed the same checks. Counts below describe separate focused runs, not a combined full suite.

| Area | Observed result | Coverage/source |
| --- | --- | --- |
| Source service, application integration and generated ownership | 10/10 Java tests passed | File boundaries, UTF-8/size limits, evidence limits, revision/hash conflicts, no-op saves, rollback/history/events and generated language/tag ownership; [service](../../src/test/java/dev/copperbench/core/application/WorkspaceSourceServiceTest.java), [application](../../src/test/java/dev/copperbench/core/application/WorkspaceSourceApplicationTest.java) |
| Asset preview and window bridge | A subsequent focused run passed 5 asset-preview tests and 3 window-bridge tests, alongside 9 source service/application tests | Real image bytes, stale hashes, size limits, unsupported formats and host dispatch; [asset preview](../../src/test/java/dev/copperbench/core/application/AssetPreviewServiceTest.java), [window bridge](../../src/test/java/dev/copperbench/bridge/JcefWindowBridgeTransportTest.java) |
| Source editor | 18/18 focused Playwright cases passed across the two existing desktop viewport projects during initial integration | Drafts, save/conflict handling, read-only generated files and line navigation; [source workbench](../../ui-shell/e2e/source-workbench.spec.ts). Later additions require the final integration run below. |
| Relationship graph | 18/18 focused Playwright cases passed across the two viewport projects | Bounded graph interactions, zoom-correct dragging, edge movement, click suppression and restoration; [graph E2E](../../ui-shell/e2e/relationship-graph.spec.ts), [model/layout tests](../../ui-shell/tests/relationshipGraph.test.mjs) |
| Versions and refactoring | 16/16 focused cases passed during page simplification; subsequent version-page coverage passed 14/14, then the final support-state subset passed 4/4 | [Version/migration tests](../../ui-shell/e2e/u3-tracks-migration.spec.ts), [refactoring tests](../../ui-shell/e2e/stage13-refactor-workbench.spec.ts) |
| UI compilation and localization | TypeScript and Chinese/English checks passed during the focused page work | Development checks, not a fresh packaged-host acceptance |

The development browser checks include simulated-host tests; they are not evidence of installing and exercising a new EXE/MSIX. No fresh full installed Windows acceptance or complete Minecraft gameplay replay was performed for 0.1.4. Historical `product-status.json` gates, the snapshot commit, Fast CI/Nightly runs and 0.1.2 hashes retain their original scope.

## Release integration and publication

- Final integrated source commit: pending.
- Required GitHub checks on that source: pending; record the actual run link and conclusion here.
- Release preparation checks passed on 2026-10-06: release-history tests 3/3, `node scripts/verify-product-status.mjs` (8 generators, 27 gates), and `node scripts/verify-markdown-links.mjs`. A direct consistency check confirmed all 0.1.4 version fields agree and historical snapshot/gates/provenance/CI records are unchanged. The workflow's extracted notes selector passed stable-version selection, missing-file rejection and preview-template preservation; the scoped whitespace check also passed.
- Signed `v0.1.4` tag and protected Windows packaging run: pending.
- Verified public assets and checksums: pending.

The existing Windows workflow must verify the signed tag against latest `main`, run its Java/Javadoc and UI contract checks, build the Windows payload, verify draft asset digests and only then publish. Its draft creation now selects the checked-in [v0.1.4 notes](../releases/v0.1.4.md) from the release tag and fails if the selected notes file is missing. An existing draft's notes are preserved and must already match this release.

The workflow builds the UI via Gradle's resource dependency; it does not itself run the full UI unit or Playwright suites. The final integration record must state the checks actually run rather than treating historical green gates as new evidence.

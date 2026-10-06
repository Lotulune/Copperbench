# Copperbench 0.1.4 Windows Product Shell release

## Scope and source

The user directly authorized pushing and publishing this Windows 11 x64 release on 2026-10-06. The signed, pushed `v0.1.4` tag points to [aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f](https://github.com/Lotulune/Copperbench/commit/aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f), after [PR #90](https://github.com/Lotulune/Copperbench/pull/90) and [PR #91](https://github.com/Lotulune/Copperbench/pull/91) merged. The production environment approval was granted. Publication is recorded separately below.

Linux remains at published [0.1.3](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.3-linux-stable); no new Linux release is authorized. Historical snapshot/gates, Beta provenance, Linux support records and 0.1.2 binary hashes keep their original scope.

## Changes

- Simplified Product Shell navigation, overview, compact forms and Chinese/English copy; version/migration/refactor operations remain available.
- Generic source browsing and entrypoint/registration evidence, manual-file editing, generated ownership, revision/hash conflicts, history/events and draft protection.
- Bounded relationship graph from existing Core projections, including unresolved references and display-only node movement; Core-backed image/model-document previews and desktop/editor preferences.
- PR #88 CI and reliability integration: change-scoped check selection, unchanged required contexts, fewer duplicate UI builds, export/migration file boundaries, strict SDK response handling/read-only retries and ordered task updates. See the [original review](../maintenance/ci-and-mcp-review-2026-10-05.md).
- PR #91 reads both workspace-card bounds in one browser evaluation during their shared entrance animation. This fixes a cross-frame measurement race; the existing less-than-1-px alignment and control-size assertions are unchanged. There is no product-layout or tolerance change in that fix.

## Verification

| Source and scope | Result |
| --- | --- |
| Final source `aabd70bc`; [Build and test #37391113372](https://github.com/Lotulune/Copperbench/actions/runs/37391113372) | Success: Java tests/Javadoc, UI contract/build/smoke and MCP conformance; confirmed against this exact head SHA |
| Final source `aabd70bc`; [Generate documentation #37391168449](https://github.com/Lotulune/Copperbench/actions/runs/37391168449) | Success; exact head SHA matches the release source |
| Version synchronization fix `e8fc80c2`, before merge | UI-Core 35/35, UI unit tests 28/28, build/TypeScript/i18n, Markdown and scoped whitespace checks passed. After the two documented host-JDK path normalizations, the release fixture exactly matched CI actual JSON; only 14 current-version values changed. About/navigation passed 4/4 browser cases across two desktop viewports. |
| Native JCEF and release-manifest development checks on `e8fc80c2` plus the three test fixes committed as `4fb1ecf0` | 6/6 across separate focused runs: accessibility 1, scale 1, JCEF smoke 3 and release manifest 1. Real Windows/JBR 25/JCEF production shell, isolated test user directory and fixture workspace; not a rerun on `aabd70bc` or an installed package. The old accessibility selector first failed, was fixed, and its focused rerun passed. |
| PR #91 focused browser alignment regression, integration worktree before merge | 20/20 in 31.3 s: the two `columns` cases, `chromium` and `compact-1366`, repeated five times, covering 1382/1366/720/520-px widths. [PR CI #37390775306](https://github.com/Lotulune/Copperbench/actions/runs/37390775306) passed the selected UI job; final main CI above passed all required jobs. |

Earlier development runs passed source service/application/ownership (10 tests), asset preview (5), window bridge (3), source editor (18 browser cases), graph interactions (18), and focused versions/refactoring checks. These were separate development-tree runs, not an aggregate full suite or acceptance of the final packaged binaries. The initial source/graph/version checks and their tests are described in the [source](../../src/test/java/dev/copperbench/core/application/WorkspaceSourceServiceTest.java), [asset](../../src/test/java/dev/copperbench/core/application/AssetPreviewServiceTest.java), [graph](../../ui-shell/e2e/relationship-graph.spec.ts) and [version](../../ui-shell/e2e/u3-tracks-migration.spec.ts) suites.

Release preparation also passed the 3 release-history tests, product-status validation (8 generators, 27 gates), version consistency, historical-record preservation and notes selection checks. None of these receipts constitutes a fresh installed EXE/MSIX acceptance. No full installed Windows or complete Minecraft gameplay replay was performed for 0.1.4; no new Nightly/eight-generator-golden pass is inferred from main CI.

The native runs used `Stage9NativeJcefAccessibilityTest`, `Stage9NativeJcefScaleGateTest`, `CopperbenchProductShellJcefSmokeTest` and `ReleaseManifestTest`, with an isolated `user.home` init script. Local receipts are `build/tmp/native-jcef-tests/{stage9,accessibility,smoke-manifest}.log` and `build/nightly-results/stage9-native-jcef-{accessibility,scale}.json`. The scale test retains the 2,000-element/10,000-reference/500-node thresholds; this does not replace physical high-DPI or screen-reader acceptance. The alignment command was `npx playwright test --config=../output/release-about.playwright.config.ts e2e/new-workspace.spec.ts --grep 'columns' --project=chromium --project=compact-1366 --repeat-each=5 --output=../output/release-0.1.4/atomic-layout-check` from the integration worktree's `ui-shell` directory.

## Publication

| Field | Recorded value |
| --- | --- |
| Tag and source | `v0.1.4` → `aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f` |
| Windows release workflow | [#37392359625](https://github.com/Lotulune/Copperbench/actions/runs/37392359625) |
| Workflow conclusion | success |
| Public release state | public stable (draft=false; prerelease=false) |
| Published at (UTC) | 2026-10-06T00:35:06Z |
| Public asset/checksum proof | [10 assets: hashes and provenance verified](../../evidence/maintenance/2026-10-06/publication-0.1.4/windows-public-verification.json) |

The protected workflow verifies the signed/latest-main source, runs release Java/Javadoc and UI-Core checks, builds Windows packages and UI resources, then verifies draft assets before publication. It does not itself run the full UI unit or Playwright suites. Public asset names, sizes and SHA-256 evidence must come from the completed payload and release inspection, not from the existence of a draft or a running job.

## Known limitation

If an asset preview fails and the user then changes the UI language, the existing error text can remain in the previous language. The request error is translated before it is stored in component state; changing language alone does not refetch it. Retry preview or reopening the asset refreshes that message. Asset content is unaffected. See [AssetPreview](../../ui-shell/src/components/AssetPreview.tsx) and [assetPreviewBridge](../../ui-shell/src/bridge/assetPreviewBridge.ts).

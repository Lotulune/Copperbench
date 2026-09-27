# Copperbench maintenance acceptance — 2026-09-27

## Baseline and scope

Base commit: `77a45d51`. This record covers local source changes after the published Windows/Linux 0.1.1 stable packages; no release, commit or push is included. Existing Minecraft-control bridge and AGENTS.md edits are unrelated and preserved.

The original Chromium `blockbench-import.spec.ts` continuity test failed at the post-import status assertion before implementation. `verify-product-status.mjs` passed despite stale current documentation, so document facts also require explicit source/evidence review.

## Batch 1 — interface continuity and current documentation

- Keep Blockbench setup/tasks mounted during asset loading, empty, error and ready states; reset local state on workspace identity changes.
- Confirm clipboard writes before displaying success, bind feedback to the selected asset and discard obsolete asynchronous completions.
- Align English/Chinese README, quick start, user guide and remaining-work entry with the 37-type catalog, eight generator tracks and scoped Windows/Ubuntu stable release evidence. Historical release/evidence records remain unchanged.
- UI build (localization, TypeScript, Vite): passed.
- Targeted Playwright: 74/74 passed across Chromium 1920×1080 and compact 1366×768, including the unchanged original import/bind assertion, refresh/error/retry, first-asset import, workspace switch and clipboard cases.
- Product status: passed (8 generators, 26 gates). All local links in the six changed documentation files resolve. The unmodified full Markdown scanner fails on existing ignored `output/` documents and the Minecraft bridge `.venv` license notice; this is not recorded as a full-repository pass.
- Manual fact review used `ElementCoverageCatalog`, `product-status.json`, the current Linux support record and Stage 17 publication/installed evidence; historical acceptance was not broadened.

## Batch 2 — internal asset projection extraction

- Extracted package-private `AssetProjectionService`: asset scan, health calculation, asset/reference/diagnostic projection and the established snapshot input. List queries, Workspace Health and post-import/move/modeling projections share it.
- The facade retains query error handling, authorization, transactions, revisions, recovery and events. No protocol, SDK or storage schema changes.
- Before extraction, `AssetProjectionCompatibilityTest` and `AssetQueryProjectionTest` passed 4/4 on the original service. They cover UI/MCP/headless equality, missing/duplicate references, conservative native-code cleanup, missing-root/index failures and unchanged snapshot calculation, including external bytes changing without a revision.
- After extraction, full Java + Javadoc passed: 238 suites, 987 cases, 928 passed, 59 skipped, zero failures/errors. The skipped opt-in/runtime cases are not new acceptance evidence. XML reports were retained under `build/maintenance-verification/java-full/` before the separate desktop run.
- Full Chromium regression: 196/196 passed. UI-Core contract suite: 29/29 passed.
- Direct Gradle startup encountered the documented Windows loopback limitation. The existing `scripts/run-gradle-external.ps1` launcher completed the checks; no system configuration was changed.
- MCP conformance: 6 applicable scenarios / 8 checks passed (`@modelcontextprotocol/conformance@0.1.16`, spec `2025-11-25`). Both the command receipt and every `checks.json` report were checked. The initial direct launch hit the same Windows loopback limitation; an isolated PowerShell process using the repository's existing conformance script completed it.

## Desktop acceptance and retained evidence

`AssetMaintenanceJcefTest` passed on the final harness in a real Windows JCEF window using the built UI, native `JcefCoreBridgeTransport`, a disk-backed `.mcreator` workspace and the production session factory with local history and persistence.

- Preparation: create the disposable block/modeling task and JSON/PNG export files in Java. This does not claim an external Blockbench editing session.
- UI actions: preview → apply import → bind → focus-triggered asset refresh. Buttons are invoked by the test through their real DOM handlers. The refresh assertion waits for the actual asset-query response and rendered ready state, and checks that the same expanded task-panel node remains mounted.
- Persistence: close and reopen the workspace in a new session; the task still reports `binding.state=bound`.
- Inspected screenshot: [task panel after binding and completed refresh](../../evidence/maintenance/2026-09-27/bound-refresh.png).
- Receipts: [verification and source hashes](../../evidence/maintenance/2026-09-27/verification.json), [bound task](../../evidence/maintenance/2026-09-27/bound.json), [reopened task](../../evidence/maintenance/2026-09-27/reopened.json), [MCP summary](../../evidence/maintenance/2026-09-27/mcp-summary.json).
- The accepted local fixture remains under `build/maintenance-jcef/a2065cef-9493-44e7-b2d4-c13d09a41eb1/`.

Non-acceptance attempts are kept separate: an unquoted PowerShell `-D` argument failed before tests; the first native fixture used the legacy session overload without a workspace root/history and was rejected during preparation. The initial successful desktop replay had an insufficient wait for the focus refresh; the final run above strengthened that wait and captured the ready asset list. None required changing product behavior or weakening assertions.

## Reproduction

Run from the repository root; Playwright runs from `ui-shell`:

```powershell
npm run build --prefix ui-shell
npm test --prefix ui-core
pwsh -NoProfile -File ./scripts/run-gradle-external.ps1 --no-daemon test javadoc
pwsh -NoProfile -File ./scripts/run-gradle-external.ps1 --no-daemon test --tests dev.copperbench.shell.AssetMaintenanceJcefTest '-Dcopperbench.stage4.jcefSmoke=true'
node scripts/verify-product-status.mjs
node scripts/verify-markdown-links.mjs
```

```powershell
npm exec -- playwright test --project=chromium --reporter=line
```

MCP uses `scripts/verify-mcp-conformance.ps1 -OutputDirectory build/maintenance-verification/mcp-conformance`; in this restricted Windows parent process it was launched through the same hidden external-process mechanism used by the Gradle helper. No tracked launcher or CI configuration was changed.

## Evidence limits

- Browser native-host fixtures and the additional real JCEF replay are distinct results.
- The desktop test validates this source build's UI/model-binding flow, not an installed release, physical mouse/keyboard interaction or an external Blockbench editing roundtrip.
- No new gameplay, cold-cache, Linux installed-product or external-user certification is implied.
- External-user trials are deferred; they do not block this maintenance scope.

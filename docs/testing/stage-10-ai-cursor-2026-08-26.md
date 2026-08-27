# Stage 10 AI unified list evidence - 2026-08-26

This record covers the implementation and remote closure evidence for the
unbounded-workspace-list portion of `FR-AI-02`. It does not claim the full AI
Developer Kit or the separate fixed-hardware `workspace-2000-10000` P95 gate is
closed.

## Implemented

- `list_mod_elements` accepts `cursor`, `limit`, `sort`, `filter`, and `fields`
  while retaining legacy `page` / `pageSize` compatibility.
- Successful list responses expose `nextCursor`; the final page returns
  `nextCursor: null`.
- Cursors are opaque URL-safe values bound to workspace revision, limit, sort,
  filter, and field projection.
- Query-mismatched or malformed cursors return `LIST_CURSOR_INVALID`; cursors
  from an older workspace revision return `LIST_CURSOR_STALE`.
- Cursor query binding uses a SHA-256 digest of the full normalized query
  signature. PR #14 added a regression fixture with two valid queries that
  intentionally collide under Java `String.hashCode()`; cross-query cursor reuse
  is still rejected.
- Field projection can request a strict subset of element summary fields.
- The JCEF bridge now uses cursor traversal for full element refreshes.
- MCP `list_mod_elements` exposes the unified list arguments.
- `list_recovery_points` and `list_publish_batches` use the same cursor contract
  for MCP callers while empty UI payloads retain the legacy full projection.
- `list_workspace_registries` enters cursor mode when one `registry` is selected;
  the no-argument all-registry desktop projection remains unchanged.
- Recovery-point and publish-batch cursor signatures include the current dataset
  IDs because those collections can change without advancing workspace revision.

## Automated evidence

`WorkspaceElementListCursorTest` constructs 2,000 elements and traverses them
with a page limit of 137. The test requires all 2,000 IDs to be observed exactly
once, validates projected fields and descending sort order, and verifies stable
errors for changed queries and stale workspace revisions.

The 2,000-element traversal writes `build/nightly-results/stage10-element-cursor.json`.
The Nightly product-regression job runs the full Java test suite and uploads
`build/nightly-results/`, retaining this evidence on the merged main commit.

Validated locally with the repository JDK 25:

```text
gradlew.bat test --tests dev.copperbench.core.application.WorkspaceElementListCursorTest --tests dev.copperbench.release.ElementCoverageCatalogTest
```

Direct cursor behavior is covered by `WorkspaceElementListCursorTest`,
`HistoryListCursorTest`, and `SecondaryListCursorTest`. The combined targeted run
passed 11/11 tests, including legacy history behavior, 2,000-element traversal,
selected-registry pagination, recovery-point pagination, and publish-batch
pagination/dataset invalidation.

Additional validation:

- `ui-core`: 18/18 schema and fixture tests passed at FR-AI-02 merge time,
  including explicit unified
  request validation for elements, selected registries, recovery points, and
  publish batches, plus a list-result contract case for an imported read-only
  `livingentity`.
- `ui-shell`: production TypeScript/Vite build passed and bridge logic tests
  passed 4/4.
- MCP conformance script exited successfully; standard initialize, logging,
  ping, tools-list, multi-stream SSE, and DNS-rebinding scenarios all passed.

## Remote closure

- PR #14 merged the list contract and the collision-hardening follow-up into
  `main@515c212cb4c026fe74a619bae2de670124020de4`.
- Protected PR CI passed Java/Javadoc, UI contract/build/smoke, and MCP
  conformance on the final PR head.
- Merged-main CI run `32997587858` passed all three required checks.
- Scheduled Nightly run `32998281437` on the exact merged main commit passed the
  product regression and all eight generator golden jobs; the
  `nightly-product-regression` artifact was uploaded successfully.
- With implementation, protected PR CI, merged-main CI, and merged-main Nightly
  all present, `FR-AI-02` is passed as of 2026-08-27.

## Separate follow-up gates

- Keep bounded catalogs (`list_new_workspace_generators`, installed plugins)
  outside the unbounded-list gate unless their product constraints change.
- Treat `get_workspace_references` as a graph/performance contract, not a list
  pagination endpoint.
- Keep the separate fixed-hardware UI/reference-query P95 acceptance criteria;
  cursor correctness does not replace that performance gate.

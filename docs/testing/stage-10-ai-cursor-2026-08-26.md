# Stage 10 AI unified list evidence - 2026-08-26

This record covers the local implementation of the unbounded-workspace-list
portion of `FR-AI-02`. It does not claim remote Nightly proof, the full AI
Developer Kit, or the fixed-hardware `workspace-2000-10000` P95 gate is closed.

## Implemented

- `list_mod_elements` accepts `cursor`, `limit`, `sort`, `filter`, and `fields`
  while retaining legacy `page` / `pageSize` compatibility.
- Successful list responses expose `nextCursor`; the final page returns
  `nextCursor: null`.
- Cursors are opaque URL-safe values bound to workspace revision, limit, sort,
  filter, and field projection.
- Query-mismatched or malformed cursors return `LIST_CURSOR_INVALID`; cursors
  from an older workspace revision return `LIST_CURSOR_STALE`.
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
The existing Nightly product-regression job already runs the full Java test suite
and uploads `build/nightly-results/`, so this evidence will be retained on every
remote Nightly run after the change is merged.

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

- `ui-core`: 18/18 schema and fixture tests passed, including explicit unified
  request validation for elements, selected registries, recovery points, and
  publish batches, plus a list-result contract case for an imported read-only
  `livingentity`.
- `ui-shell`: production TypeScript/Vite build passed and bridge logic tests
  passed 4/4.
- MCP conformance script exited successfully; standard initialize, logging,
  ping, tools-list, multi-stream SSE, and DNS-rebinding scenarios all passed.

## Remaining FR-AI-02 work

- Merge this branch and obtain remote CI/Nightly evidence before promoting the
  requirement from local implementation to passed.
- Keep bounded catalogs (`list_new_workspace_generators`, installed plugins)
  outside the unbounded-list gate unless their product constraints change.
- Treat `get_workspace_references` as a graph/performance contract, not a list
  pagination endpoint.
- Keep the separate fixed-hardware UI/reference-query P95 acceptance criteria;
  cursor correctness does not replace that performance gate.

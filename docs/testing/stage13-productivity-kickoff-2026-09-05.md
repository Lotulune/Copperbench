# Stage 13 Productivity Kickoff — 2026-09-05

Baseline branch: `codex/stage13a-procedure2`

Baseline commit: `bcba44c33ae4ec81e2eae37717e8ea09f76c71b1` (PR #61 / Stage 12 merge)

## Product scope

Stage 13 is a productivity stage. It does not add new Mod Element types. The product goal is to reduce the time spent finding project objects, editing logic, managing assets, understanding failures, and recovering changes.

The PRD scope is:

1. Procedure Workbench 2.0.
2. Asset Center.
3. Diagnostics 2.0.
4. Local History / Recovery UX.
5. Migration / Refactor Workbench.
6. Workspace Health.

Stage 13A starts with Procedure Workbench 2.0 because Copperbench already has a structured Procedure IR, `get_procedure_editor`, a Blockly workbench, reference projection, node diagnostics, an outline panel, and the existing 500-node performance baseline.

## Stage 13A first implementation slice

### A. Node discovery

- Keep the Core `nodeCatalog` as the source of truth for available node types and capability reasons.
- Add category filtering in addition to free-text search.
- Add a recent-use projection owned by the workbench/session layer; do not encode recent UI state into Procedure IR.
- Search results must be keyboard navigable and must not require scanning the Blockly canvas.
- Unavailable catalog entries remain visible with a stable reason code.

### B. Node navigation

- Reuse one `selectNode(nodeId)` path for outline navigation, search-result navigation, and diagnostic navigation.
- A node navigation action must select the Blockly block and center it in the viewport.
- Procedure diagnostics already carry a node path; expose a stable `nodeId` in the editor projection/action metadata so the UI does not need to parse arbitrary display text.
- Unknown/plugin nodes must remain navigable even when their content is read-only.

### C. Diagnostics 2.0 bridge for Procedure

- Node-level validation issues remain Core-owned and revision-bound.
- Each node diagnostic needs an explicit UI action to locate the affected node.
- Port-level diagnostics should identify both node and port when available.
- No UI-only validation result may bypass Core diagnostics for save/generate decisions.

### D. Reference sidebar

- Continue using the shared workspace reference index.
- Group Procedure references into at least: variables, Procedure/function calls, Mod Element references, and resource references when the index can identify them.
- Clicking an in-Procedure reference should navigate to the relevant node when a node source is known.
- Cross-element references must expose the target identity without duplicating a second reference model inside the UI.

### E. Large Procedure regression guard

The existing 500-node baseline remains a hard regression floor. Stage 13A must preserve:

- 500-node open usability ceiling.
- palette/search interaction performance baseline.
- node select/center interaction responsiveness.
- structured edit/save/reopen round-trip fidelity.
- unknown-node preservation.

Any new search/outline/reference derivation over the Procedure graph should be memoized or pre-projected so ordinary interactions do not repeatedly perform avoidable O(N x M) scans over a 500-node graph.

## First acceptance tests

### Core

1. `GET_PROCEDURE_EDITOR` returns node navigation metadata for diagnostics without changing Procedure IR serialization.
2. Port-level diagnostics retain node + port identity.
3. Node catalog filtering metadata remains capability-aware and stable.
4. Reference projection remains the same shared workspace reference index used outside the Procedure UI.
5. Existing `ProcedureIrScaleGateTest` remains green.

### UI / Playwright

1. Search by node label/type and navigate a result to the Blockly node.
2. Filter catalog by category and restore all categories.
3. Add a node and verify it appears in recent-use UI without mutating Procedure IR solely for recency.
4. Click a diagnostic and verify the affected node becomes selected/centered.
5. Click an outline entry and use the same navigation path.
6. Verify unavailable nodes remain visible with capability reason text.
7. Run the same flows in Chromium and compact-1366.

### Native scale / accessibility

- Extend the existing Stage 9 native JCEF 500-node gate rather than creating a duplicate benchmark.
- Preserve keyboard access for search results, category controls, diagnostics, references, and outline navigation.

## Non-goals for the first slice

The following belong to later Stage 13 slices and should not block the initial Procedure Workbench 2.0 navigation/search work:

- Extract reusable logic refactor.
- Batch reference replacement.
- Rename impact preview.
- Asset Center bulk import/move.
- Workspace Health dashboard.
- Migration Workbench redesign.

Those operations must eventually use semantic diff + recovery point before applying changes.

## Implementation order

1. Extend Procedure editor contract for explicit node navigation metadata and diagnostics actions.
2. Add category filter + recent-use UI state.
3. Unify outline/search/diagnostic node navigation.
4. Group references and add navigation where source-node identity is available.
5. Add focused Core/UI tests.
6. Re-run existing 500-node Core/native gates before Stage 13A is considered stable.

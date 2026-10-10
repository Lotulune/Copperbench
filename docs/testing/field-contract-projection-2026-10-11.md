# AR-09 field-contract projection extraction

The bounded maintenance follow-up to the
[Agent readiness PRD](../roadmap/agent-readiness-prd-2026-10-09.md) is complete
on the development branch. The source baseline is `1aae44bf` plus this change.

`FieldContractProjection` now selects the contracts exposed by the workspace
environment and element editors. It is package-private, uses the existing
contract providers, and takes only an element type and generator ID. It has no
workspace store, task gateway, transaction or authorization dependency.
Discovery request validation and diagnostic mapping remain in the application
service; the underlying field metadata and validators are unchanged.

The compatibility behavior remains explicit:

- The environment retains its nine legacy capability entries and adds item and
  recipe only when their creation contracts are complete.
- Existing item/recipe editors retain incomplete contracts with their reason
  codes. Block creation and existing-block editors retain the legacy block
  capabilities, whose shape differs from per-type discovery.
- Other editors continue to omit `fieldContract`. Each returned JSON object
  remains independent, including nested fields modified by a caller.

## Verification

Before extraction, 19 characterization cases passed through the original Core
query service at `1aae44bf`. The same assertions then passed directly against
the extracted component. They compare full contract JSON with the established
providers across all eight supported Java generator tracks, check missing and
empty generator IDs, distinguish omitted editor fields, and exercise caller
mutation isolation. The baseline query harness and XML are retained locally.

The final focused run passed **65 tests, zero failures/errors/skips**:

| Test class | Passed |
| --- | ---: |
| `FieldContractProjectionTest` | 19 |
| `WorkspaceApplicationServiceTest` | 27 |
| `ElementFieldDiscoveryTest` | 11 |
| `BlockFieldContractTest` | 4 |
| `ProcedureFieldContractTest` | 4 |

The service regression checks block creation/existing editor equivalence under
read-only access. The existing discovery integration checks exercise UI, MCP
and headless entry points, unchanged files/revision during discovery, actual
item/recipe creation, template generation, save and reopen on all eight tracks.
These are adapter/template checks, not eight Minecraft builds or gameplay runs.

The first verification command stopped before tests because this worktree had
no UI `node_modules` and could not import TypeScript. `npm ci --prefix ui-shell`
restored the locked dependencies without changing package files. Both ensuing
Gradle runs passed; their normal `buildUiShell` dependency also built the UI.
No public Java API changed. Markdown links and diff whitespace checks passed.

Local evidence is under `build/ar09-field-contract-projection`: the original
query harness, baseline XML/log, final suite XML/log, source patch and summary.
Reproduce the focused run on Windows with:

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 --no-daemon test --tests dev.copperbench.core.application.FieldContractProjectionTest --tests dev.copperbench.core.WorkspaceApplicationServiceTest --tests dev.copperbench.core.workspace.mcreator.ElementFieldDiscoveryTest --tests dev.copperbench.core.application.BlockFieldContractTest --tests dev.copperbench.core.application.ProcedureFieldContractTest
node scripts/verify-markdown-links.mjs
```

This extraction does not reopen the owner-ended
[independent-agent study](agent-readiness-m3-study-2026-10-10.md). Installed
acceptance, cold-cache limits and the deferred unfamiliar-user study retain
their recorded scope; no new candidate package or release is claimed.

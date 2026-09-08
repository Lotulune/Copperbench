# Copperbench SDK and AI evals

> **Compatibility boundary:** the MCP protocol clients in this directory are the
> supported Preview client surface described below. Generator/plugin extension
> authoring is **experimental**, is not a stable third-party ABI/SDK, and may
> change incompatibly before a dedicated compatibility ADR is approved. See
> [`experimental-extension/README.md`](experimental-extension/README.md).

The TypeScript and Python clients in this directory use only their runtimes'
built-in HTTP and JSON support. They speak the local Copperbench MCP
Streamable HTTP endpoint, negotiate protocol `2025-11-25`, retain the session
ID, parse JSON or SSE responses, and raise stable diagnostic codes for rejected
tool calls.

Both clients expose the same minimum surface: workspace reads, Cursor-based
element traversal, element and Procedure writes, reference-aware registry
rename preview, protected Procedure refactor planning, asset discovery and
reference-safe asset move preview/apply, atomic Workspace Plan calls, build
task start/status/cancel, and recovery point create/restore. `get_task` remains
the compatible polling path while native JCEF clients additionally receive
task events.

The MCP catalog also exposes the Stage 14D local-template workflow:
`create_local_template`, `list_local_templates`, and
`preview_local_template_instantiation`; the resulting signed plan is committed
with the existing `apply_workspace_plan` operation. Templates are local-only and the generator/plugin extension authoring surface remains experimental.

Desktop endpoints use a random loopback port. Do not assume `8787`: both SDKs
can read `<workspace>/.copperbench/mcp-connection.json` (or accept the
`.mcreator` file path) via `read_workspace_connection` / `readWorkspaceConnection`,
and `CopperbenchClient.from_workspace` / `CopperbenchClient.fromWorkspace`
construct a client from that non-secret metadata plus the one-time token shown
by the Copperbench UI. The connection file never contains the bearer token.

The ten-case manifest in `sdk/evals/manifest.json` is the stable evaluation
coverage contract. Validate its shape with:

```powershell
node scripts/verify-ai-evals.mjs
```

The manifest defines the scenarios that a connected test harness must execute;
it does not claim that a networkless checkout has run those live scenarios.

Protocol retry, idempotency, revision-conflict, and preview-version rules are
specified in [protocol.md](protocol.md).

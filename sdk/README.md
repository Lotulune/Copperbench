# Copperbench SDK and AI evals

The TypeScript and Python clients in this directory use only their runtimes'
built-in HTTP and JSON support. They speak the local Copperbench MCP
Streamable HTTP endpoint, negotiate protocol `2025-11-25`, retain the session
ID, parse JSON or SSE responses, and raise stable diagnostic codes for rejected
tool calls.

Both clients expose the same minimum surface: workspace reads, Cursor-based
element traversal, element and Procedure writes, registry rename, atomic
Workspace Plan calls, build task start/status/cancel, and recovery point
create/restore. `get_task` remains the compatible polling path while native
JCEF clients additionally receive task events.

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

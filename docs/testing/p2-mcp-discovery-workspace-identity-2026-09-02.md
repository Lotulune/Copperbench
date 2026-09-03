# P2 MCP discovery / workspace identity — 2026-09-02

## Scope and evidence boundary

This record covers the local FR-PROD-06 cleanup for MCP UI state, SDK/quickstart endpoint discovery, and generated workspace identity. It is local implementation evidence, not a release-candidate promotion record.

## MCP runtime state is native-driven

`AIControlView` consumes `useMcpRuntimeState`, which obtains state from the dedicated native MCP bridge. The UI labels a listening service as `服务已启动`; it does not infer that an external client is connected. The browser fallback reports `not_started` when no native host exists.

Focused Playwright verification:

```text
e2e/mcp-runtime.spec.ts
browser fallback never claims an MCP client is connected       PASSED
native host state drives endpoint display and token reveal     PASSED
compact-1366 variants                                          PASSED
4/4 passed
```

## SDK and quickstart connection discovery

Python and TypeScript SDKs expose workspace connection discovery from:

```text
<workspace>/.copperbench/mcp-connection.json
```

Both accept either the workspace folder or the `.mcreator` file path. Client factories combine the non-secret connection metadata with the one-time token supplied by the user. The connection file is never expected to contain credentials.

The Python and TypeScript readers validate a listening v1.0 endpoint, require a loopback `http://127.0.0.1:<port>/mcp` URL and workspaceId, and reject credential-bearing metadata. The quickstarts use `COPPERBENCH_WORKSPACE` and the connection file by default; a manually supplied endpoint remains an explicit override rather than a baked-in port.

Static SDK/eval contract verification:

```text
node scripts/verify-ai-evals.mjs
AI eval manifest passed: 10 cases, 10 coverage targets.
```

The verifier rejects quickstarts that hard-code the legacy `127.0.0.1:8787` endpoint and requires both SDK workspace-discovery APIs.

## Generated workspace identity

The eight formally supported Fabric/NeoForge workspace templates use the user workspace modid for Gradle artifact identity. `WorkspaceIdentityTemplateTest` creates a real workspace for every track and verifies:

```text
base.archivesName = "${modid}"
```

for all eight tracks, and additionally for Fabric:

```text
actualmodid=${modid}
```

The historical placeholders `base.archivesName = "modid"` and `potatoesAreBetterThanEggs` are explicitly rejected by the test.

Verification result:

```text
fabric-26.2       PASSED
neoforge-26.2     PASSED
fabric-26.1.2     PASSED
neoforge-26.1.2   PASSED
fabric-1.21.1     PASSED
neoforge-1.21.1   PASSED
fabric-1.20.1     PASSED
neoforge-1.20.1   PASSED
BUILD SUCCESSFUL
```

## Remaining candidate proof

FR-PROD-06 is locally implemented and verified. Candidate-specific evidence still requires replay from the selected frozen Windows candidate so SDK discovery, UI runtime state and generated artifact identity can be tied to that candidate SHA. This P2 completion does not bypass the four P0 installed-product gates.

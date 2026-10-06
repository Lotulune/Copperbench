# AI client protocol rules

The SDKs target the local Copperbench MCP Streamable HTTP endpoint and the
preview UI-Core contract. Clients must treat workspace revisions as optimistic
concurrency tokens and must never retry a mutation against a newer revision
without rereading the workspace.

## Retries and idempotency

- The MCP SDKs automatically retry transport failures (connection reset,
  timeout, interrupted response body, or HTTP 502/503/504) only for an explicit
  allowlist of audited reads: `get_workspace`, `get_workspace_environment`,
  `get_workspace_health`, `get_task`, `list_mod_elements`, `read_mod_element`,
  `get_procedure`, `list_workspace_registries`, `get_workspace_references`,
  `list_recovery_points`, and `list_task_authorizations`. The limit is two
  retries (three attempts total), with bounded backoff. Retry options can lower
  this limit; they cannot enable retries for other tools.
- Mutations, unknown/new tools, initialization, and queries that can issue
  plans or contact another service are sent once. A `get_` or `preview_` name
  alone does not establish that a request is safe to repeat. JSON-RPC errors,
  `4xx` responses, permission/diagnostic rejections, and malformed responses
  are never automatically retried, including for allowlisted reads.
- The current MCP tool surface does not accept a caller-owned
  `clientMutationId` or provide general mutation deduplication. The server
  assigns a new Core mutation ID for each call. If a response is lost, a write
  or task start may already have completed; reread workspace/task state before
  deciding what to do. Reusing a JSON-RPC request ID does not make a write
  idempotent. The SDKs do not change `expectedRevision` to retry a conflict.
- Multi-step content changes use `plan_workspace_changes` and the returned
  `planId`/`planToken`. Apply the exact validated plan once; an exact replay is
  idempotent while its session token remains valid; the SDK still sends each
  explicit apply call once. A stale revision requires a fresh read and plan.
- `build_workspace`, client/server runs, datagen, and GameTest are long tasks.
  Start them once, retain the task ID, and use `get_task` polling with the last
  received `afterLogSequence` to request only new log entries. Native JCEF
  clients may additionally consume task events and reconnect by sequence.

## Errors and compatibility

Stable diagnostic codes are the machine contract. Human-readable fallback text
is for display only. Unknown fields must be rejected, and clients should
negotiate protocol/schema versions before calling tools. Preview `0.x` fields
may gain optional properties; removal or semantic changes require a new major
schema version and a migration note.

MCP `isError: true` always raises `CopperbenchError`, including the audit-log
failure response that only contains a top-level `code`. The SDKs preserve a
nonempty top-level code or the first valid diagnostic code and retain the
decoded error payload in `details`; an error without a code uses
`MCP_TOOL_ERROR`. Rejected/failed payloads also raise when `isError` is absent.
Malformed JSON-RPC envelopes, mismatched request IDs, and missing/non-object
results use `MCP_RESPONSE_INVALID`. Missing/non-JSON tool content, non-object
payloads, invalid error flags, or unrecognized result statuses use
`MCP_TOOL_RESULT_INVALID`. Successful `succeeded`, `committed`, `accepted`,
`completed`, and `cancelled` payloads remain unchanged. A task returned by a
successful query may itself be failed; callers must still inspect task state.

This is a behavior change for integrations that relied on automatic write
replay or treated error-only payloads as success. Python Native `Workspace`
requests continue to execute once; this MCP change does not alter its transport
or the Core Workspace Plan integrity and replay checks.

Run the shared transport/error regression fixtures against both production
SDKs with `python -m unittest discover -s sdk/python -p 'test_*.py'` and
`node --test sdk/typescript/tests/*.test.mjs`. The Node tests use the repository's
existing `ui-shell` TypeScript development dependency (`npm ci --prefix
ui-shell`); the shipped SDKs still need no third-party runtime dependencies.

## Task authority and acceptance evidence

Mutating tools accept optional `taskAuthorizationId` metadata. The authorization
is issued by the local user, scoped to a directory and operation categories,
expires within 24 hours, and can be revoked. It supplements the existing MCP
token/profile. It cannot elevate a read-only connection or authorize external
publication. Specifying an expired, revoked, invalid or out-of-scope grant fails
closed; cancellation and revocation remain available. Existing permissions of a
Workspace token are not removed when this separate grant expires.

`run_gametest` now requires a fresh structured test report. Exit code zero alone
is insufficient, and zero executed tests cannot pass. Optional task properties
`sourceSnapshot` and `verification` bind counts and cases to the tested files;
packaged-JAR mode also records the deployed artifact hash. These fields are an
additive preview schema extension. Custom GameTest tasks that previously only
printed success must write JUnit/GameTest XML and configure its path. See the
[configuration and migration notes](../docs/ai/task-authorization-and-acceptance.md).

# AI client protocol rules

The SDKs target the local Copperbench MCP Streamable HTTP endpoint and the
preview UI-Core contract. Clients must treat workspace revisions as optimistic
concurrency tokens and must never retry a mutation against a newer revision
without rereading the workspace.

## Retries and idempotency

- Retry only transport failures (connection reset, timeout, or HTTP 502/503/504)
  with at most two attempts and bounded backoff.
- Do not retry a JSON-RPC error, a `4xx` response, a permission denial, or a
  diagnostic rejection.
- Every mutation carries a caller-owned `clientMutationId`; keep it stable for
  a transport retry of the same request.
- Multi-step content changes use `plan_workspace_changes` and the returned
  `planId`/`planToken`. Apply the exact validated plan once; an exact replay is
  idempotent, while a stale revision requires a fresh read and plan.
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

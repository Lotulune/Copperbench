# Stage 10 AI SDK live eval evidence - 2026-08-27

This record covers the executable slice of `FR-AI-05`. The gate remains
**in progress** until merged-main evidence exists.

## Harness

- `scripts/run-ai-live-evals.py` drives the dependency-free Python SDK against
  a real loopback HTTP MCP session.
- `scripts/verify-ai-live-evals.ps1` starts the repository test MCP host twice:
  once with `workspace` permission and once with `read_only` permission.
- The test MCP host writes a scoped connection file containing its random
  loopback port, short-lived token, workspace ID, and permission profile. The
  runner does not print the token.
- `InMemoryWorkspaceTaskGateway` provides deterministic datagen staging for
  this fixture so preview/publish/cancel can be tested without launching a
  Minecraft process. Real generator/server validation remains a separate
  Stage 9 gate.

## Result

Command:

```text
pwsh -NoProfile -File scripts/verify-ai-live-evals.ps1
```

Result: **10/10 passed**.

Protected PR #16 CI also passed on head `564b2ed4`: `Build and test` run
`33068820470` completed successfully across Java/Javadoc, UI, MCP conformance,
and the JUnit report job.

Workspace-profile cases:

1. create element;
2. Procedure modification;
3. registry rename/reference-aware mutation path;
4. build task acceptance and cancellation;
5. stale revision conflict;
6. datagen cancellation;
7. datagen preview and publish with manifest hash;
8. protected recovery restore rejection with `USER_APPROVAL_REQUIRED`;
9. reconnect-compatible `get_task` polling.

Read-only-profile case:

10. unauthorized create is rejected with `PERMISSION_DENIED`.

The static manifest verifier also passes: 10 cases and 10 required coverage
targets. Together with protected PR CI, this proves that the SDK/eval contract
executes over the real MCP HTTP boundary on the reviewed PR head. It does not
by itself satisfy merged-main/Nightly, real native JCEF task-event validation,
or external-user gates.

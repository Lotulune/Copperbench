# Stage 10 AI atomic Workspace Plan evidence - 2026-08-27

This record covers the first implementation slice of `FR-AI-03`. The gate is
still **in progress** until protected PR CI, merged-main CI/Nightly, and review
of the supported operation boundary are complete.

## Contract

The MCP/UI-Core contract exposes three operations:

- `plan_workspace_changes`: simulates an ordered content mutation list against
  one `expectedRevision` without changing the real workspace.
- `preview_workspace_plan`: revalidates the plan, its `planId`, target digest,
  permission assessment, semantic diff, and current revision.
- `apply_workspace_plan`: applies the validated target as one workspace
  revision, with one plan-level recovery point and durable rollback protection.

The current atomic content scope is intentionally bounded to:

- mod element create/update/delete;
- Procedure graph update;
- registry create/update/delete/rename.

Build/run tasks, datagen publication, loader migration, upstream import, asset
publication, and other approval/external side-effect operations stay outside
this content transaction boundary.

## Atomicity and idempotency

- Create steps receive stable `plannedId` values during planning so preview and
  apply target the same identities.
- A SHA-256 `planId` binds workspace ID, base revision, idempotency key, ordered
  operations, operation count, target digest, semantic diff, and changed paths.
- A separate per-process HMAC-SHA-256 `planToken` authenticates that `planId` as
  server-issued. This prevents an untrusted client from recomputing a content
  hash and forging an `alreadyApplied` receipt. Plans are intentionally
  ephemeral across Copperbench restarts and must be regenerated after restart.
- Preview/apply normalize the ordered operations and recompute derived semantic
  metadata before trusting it. Tampered `operationCount`, `semanticDiff`, or
  `changedPaths` is rejected with `WORKSPACE_PLAN_INTEGRITY_FAILED` before any
  recovery point or durable write.
- Simulation uses a shadow `RevisionedWorkspaceStore` and the same application
  validation paths as normal element/registry mutations, with persistence
  disabled.
- Apply replaces the validated target inside one real store transaction, so a
  successful multi-step plan advances the workspace revision exactly once.
- The MCreator persistence gateway snapshots every affected element/workspace
  file once, applies the complete delta, saves once, and restores the snapshot
  if any durable write fails.
- Plan deletes suppress per-element MCreator history checkpoints; the plan owns
  exactly one Copperbench recovery point.
- Exact target-state replay of a validated plan is idempotent and does not
  advance the revision or create another recovery point.

## Local automated evidence

`WorkspacePlanEngineTest` covers:

1. two ordered create operations apply as one revision;
2. preview reports write permission and the semantic target;
3. replay leaves the revision unchanged and does not persist again;
4. an invalid second ordered step rejects the plan before mutation;
5. a stale plan is rejected after another writer advances the revision;
6. Read Only preview reports `allowed=false`;
7. synthetic durable persistence failure leaves store content/revision
   unchanged and creates only one recovery point;
8. client-tampered derived plan metadata is rejected before mutation.

Targeted Java result:

```text
WorkspacePlanEngineTest: 5/5 passed
```

`WorkspacePersistenceCompatibilityTest` also exercises the real MCreator-backed
gateway:

- a two-element plan writes both real `.mod.json` definitions, reloads them from
  disk, and advances Copperbench product revision only once;
- a synthetic final product-revision conflict after both element writes restores
  the complete file snapshot, reloads the upstream workspace, leaves neither
  element behind, and preserves the pre-existing disk revision.

Targeted MCreator persistence result: **2/2 passed**.

UI-Core contract tests include explicit valid plan/preview/apply envelopes and
unknown-field rejection. After adding the plan contract the suite is 19/19.

The UI shell TypeScript/Vite production build and Chinese localization gate also
pass; all referenced Chinese keys are translated.

The Windows MCP conformance suite exits `0`. Initialize, logging, ping,
`tools/list`, multiple concurrent SSE streams, and DNS-rebinding protection all
pass with the three Workspace Plan tools present. The existing Windows libuv
`UV_HANDLE_CLOSING` assertion noise is still printed by the conformance client,
but no conformance scenario fails.

## Remaining closure work

- Obtain protected PR CI and address review findings.
- Obtain merged-main CI and Nightly evidence before changing
  `ai-workspace-plan` to `passed`.
- Do not use this gate to close Stage 9 fixed-hardware performance, JCEF,
  server, clean-VM, or external-tester gates.

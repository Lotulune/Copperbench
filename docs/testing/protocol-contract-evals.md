# MCP protocol-contract evaluations

The legacy `run-ai-live-evals.py` and `verify-ai-live-evals.ps1` entry points run
ten SDK cases against a real loopback HTTP MCP transport. Their test host is
`McpConformanceServerMain`, backed by `InMemoryWorkspaceTaskGateway` and
`WorkspaceMutationGateway.noOp()`. They do not run a model, Gradle repair,
transport reconnection or Minecraft gameplay.

The scope clarification originally proposed in PRs #98/#99 is retained alongside
the current [independent Nightly gates](agent-readiness-m2.md) and
[real M1 delivery](agent-readiness-m1.md). Those gates retain their separate
execution and evidence; the older five-job Nightly proposal is not substituted
for the current implementation.

## Compatible reports

Existing suite/schema identifiers, ten case IDs, `covers` labels and result
counts remain available. Reports add `displayName`, `scope` and
`observedCoverage` so consumers can describe the checks actually executed.

| Legacy case ID | Observed checks |
| --- | --- |
| `create-element` | Fixture creation and revision update |
| `procedure-edit` | Fixture procedure update |
| `rename-reference` | Fixture registry rename |
| `build-repair` | Task acceptance, running-task query and cancellation |
| `revision-conflict` | Stale revision rejection |
| `readonly-denial` | Read-only mutation rejection |
| `datagen-cancel` | Fixture task acceptance and cancellation |
| `datagen-publish` | Fixture preview, manifest-hash shape and publication |
| `recovery-restore` | Recovery point creation and restore approval rejection |
| `task-reconnect` | Repeated polling in the same client session |

`scope.kind` is `protocol-contract`. `modelDriven`, `realBuild`, `repairLoop`,
`transportReconnect` and `gameplay` are false. Legacy names alone are not proof
of those capabilities. The manifest verifier rejects unsupported claims and
misleading observed-coverage labels.

## Verification

```sh
python -m unittest discover -s scripts/tests -p 'test_eval_contracts.py'
node scripts/verify-ai-evals.mjs
```

These ten regressions use controlled responses and temporary manifests. CI's
Windows MCP job runs them; they do not start a Core process or establish real
delivery. For the actual HTTP transport harness, prepare JDK 25/JCEF, Node,
Python and PowerShell 7, then run:

```powershell
.\gradlew.bat --no-daemon testClasses
pwsh -NoProfile -File ./scripts/verify-ai-live-evals.ps1 -OutputDirectory build/ai-live-evals
```

The workspace profile contributes nine cases and read-only contributes one.
The revision-conflict case checks the SDK's `REVISION_CONFLICT` code and the
original Core `WORKSPACE_REVISION_CONFLICT` diagnostic independently.
Reports preserve nonzero failure exits and explicit scope. Output retention
copies reports and server logs, not the connection file containing the token.
The consolidated PR's actual results are in the
[integration record](pr-consolidation-2026-10-11.md).

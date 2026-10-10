# Generation preflight — AR-03

Date: 2026-10-09 (Japan time). Development baseline: PR #99 commit
`436a41761d885fa5ad0d76500e2257438137d75f`; this record describes the local
`codex/generation-preflight` source delta on that baseline, not a published package.

## Delivered behavior

- `preview_generation` is an additive UI-Core/MCP read with an empty payload.
  Native Python and Python MCP expose `preview_generation()`; TypeScript MCP
  exposes `previewGeneration()`. Read-only Core adapters can use it.
- The result identifies the revision, generator, workspace-input fingerprint,
  planned managed paths and structured conflicts. `ready` means the observed
  source plan passed its guards; `conflicted` and `unknown` must not be treated
  as permission to generate. Unsupported backends report unknown explicitly.
- Conflicts expose `relativePath`, `reasonCode`, `expectedOwnership` and
  `observedOwnership`. Paths outside the workspace are redacted to null. Links
  are rejected before reading their targets. At most 100 conflicts and 200
  managed paths are returned, with totals and explicit truncation flags.
- Discovery and task preparation use the same
  [source planner](../../src/main/java/dev/copperbench/core/workspace/mcreator/MCreatorGenerationPlan.java).
  Execution constructs a fresh plan and retains revision coordination,
  per-file fingerprints, pending generation and rollback. A prior preview is
  not a lock, approval or ownership transfer.
- Existing conflicts are rejected before dependency preparation and before
  restoring a missing `mcreator.gradle`. Native build paths that do not request
  managed regeneration retain their existing behavior. No ownership records
  are rewritten by preflight.
- Definition headers are inspected before upstream template discovery can
  invoke its converting reader. The actual `ConverterRegistry` determines
  whether conversion would run; such inputs return unknown with
  `ELEMENT_CONVERSION_REQUIRED` without saving or warming a converted cache.
  Compatible older formats remain readable. Future, malformed or mismatched
  definitions also stop inspection without automatic conversion.
- Gradle task failures retain readable bounded details and raw exception/log
  evidence, and carry structured conflicts in `diagnostics[].message.args`.
  Existing exception constructors remain compatible; details are copied at
  the exception boundary.

`inputFingerprint` uses the existing `WorkspaceExecutionSnapshot` inventory
rules, including its cache/runtime exclusions and file/byte limits. It can be
null when inspection is unsafe or unavailable. A non-null `reasonCode` means
the observation could not be completed; counts then describe only the portion
that could be safely planned. `nextSteps` contains decision labels, not Core
operation names. It does not provide a force flag or automatic migration.

## Validation environment and scope

Local Windows 11 x64 (10.0.26100), JBR 25.0.3+1-b329.124 with JCEF,
Python 3.13.14, Node 24.19.0, root Gradle Wrapper 9.6.0. Existing Java/Gradle
dependency caches were reused; UI dependencies were installed from the lockfiles.
The direct child Gradle daemon hit the documented Windows loopback limitation;
the repository's `scripts/run-gradle-external.ps1` launcher ran the Java checks.

The real MCreator adapter tests use writable disposable Fabric 1.21.1 workspaces
and the product generator metadata. They cover stable read-only projections,
unchanged workspace bytes/revision, unowned base/element files, source edits
after preview, valid and damaged user-code regions, symlinks, escaping ownership,
non-file targets, malformed metadata, legacy conversion boundaries, bounded conflict lists and actual task
failure diagnostics before Gradle starts. The actual task test uses the product
task gateway/session and rejects any reached build runner. Direct preparation
regressions separately reject any dependency-runner invocation.

The format-boundary fixtures use real temporary Block definition files with
`_fv=0` (conversion required) and `_fv=84` (compatible without a Block
converter). These verify preflight's no-write boundary and compatibility, not
the correctness of migrating every historical MCreator format.

The byte inventory records the presence and size of the active Windows writer
lease instead of reading its locked contents. Source, definition and other
metadata files are hashed. Early test attempts exposed a missing required
`initialValues` fixture and the locked-file inventory issue; both were corrected.
An obsolete first attempt was stopped and is not a passing verification result.
The real-task test also corrected an assumption that a particular conflicting
file would always be first: the bounded log must match the reported first path,
while the structured list must include the protected main source. Its focused
rerun passed with no dependency-preparation log and unchanged source/definition
bytes.

## Observed results

| Check | Result |
| --- | --- |
| Python SDK, ResourceWarning treated as an error | 62 passed |
| TypeScript SDK and shared retry fixtures | 13 passed |
| UI-Core schema/fixture tests | 36 passed |
| UI bridge/localization unit tests | 28 passed |
| Real adapter query envelopes checked against UI-Core schema | 24 accepted across focused runs |
| Java supporting generation/ownership/task regressions | 37 passed |
| MCP HTTP tests | 9 passed |
| New generation preflight adapter/task tests | 13 passed after focused reruns |
| UI production build / TypeScript / localization gates | Passed through Gradle resource build |
| Public Java API Javadoc | Passed |
| Markdown links, product-status consistency, diff whitespace | Passed |

The Java total is **59 distinct cases, latest result 59 passed / 0 failed /
0 skipped**, collected across focused runs, not a claim that the initial full
selection was green. `build/reports/generation-preflight-verification.json`
retains the report used for each case. Supporting results came from
`.tmp/gradle-external/5c0d4200c63b42ec837ddb6a29f73a43.log`; the adapter/MCP run is
`4460fe318e6e433eba73080e3cfe20a1.log`; the first real-task rerun is
`c6dc43c396614ba0bbfef99acaff1ef7.log`. After adding the definition-format guard,
the 12 preflight and 7 preparation cases passed in
`163aab5c39484e18abbc00628602f574.log`; the final two format-boundary cases and
Javadoc passed in `8c918cbd9814405a9c81403a34555655.log`. All are in the same
directory. Original failed attempts remain in those logs and copied XML reports.

## Reproduction

```powershell
python -W error::ResourceWarning -m unittest discover -s sdk/python -p 'test_*.py'
npm test --prefix ui-core
npm test --prefix ui-shell
npm run test:sdk --prefix ui-shell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 --no-daemon test --tests '*GenerationPreflightTest' --tests '*GenerationPreparationDiagnosticsTest' --tests '*GenerationConflictDiagnosticTest' --tests '*MCreatorGenerationPreparationTest' --tests '*WorkspaceSourceOwnershipTest' --tests '*Fabric1211TaskGatewayTest' --tests '*McpHttpServerTest' javadoc
node scripts/verify-markdown-links.mjs
```

Gradle builds the UI through `processResources -> buildUiShell`. Java reports
are under `build/test-results/test` and `build/reports/tests/test`; actual query
envelopes emitted by the adapter regression are under
`build/reports/generation-preflight`. Earlier passing supporting suites are
retained locally under `build/preflight-supporting-results` and
`build/preflight-adapter-results`; the format-guard pass is under
`build/preflight-format-results`. Full execution logs remain in `.tmp/gradle-external`.

No real Gradle mod build, packaged-JAR/GameTest run, installed desktop, Minecraft
client interaction, cold dependency download, Linux run or other generator-track
acceptance is claimed here. These remain separate PRD layers. AR-01 complete
item/recipe discovery, AR-05 delivery gates and the other milestones are not
marked complete by this change.

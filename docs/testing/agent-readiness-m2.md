# M2 environment and integrity gates

Implementation and consolidated local validation were performed on 2026-10-09.
M2 remains open: official-source cold downloads failed on the local network,
and hosted fault injection has not run. See the
[milestone ledger](../roadmap/agent-readiness-m2.md).

## Environment discovery

Before opening a workspace, run
`copperbench headless --workspace <path.mcreator> doctor`.
In an existing Native or MCP session, use `workspace.doctor()` /
`client.doctor()`; TypeScript exposes `client.doctor()`. The command and Core
query share one report implementation and the same execution configuration as
Fabric/NeoForge task runners. Neither doctor path starts Gradle or Minecraft.

Findings separate the product's Java 25 from the workspace JDK, declared compile
release, actual JDK release metadata, working directory, wrapper runtime and
distribution digest. Missing/unsupported/blocked/unknown states are explicit.
An overridden Gradle launcher is unknown until independently checked.
A proxy setting or DISPLAY declaration is not connectivity or OpenGL evidence.

Cold CLI doctor bypasses product bootstrap and workspace opening: it must not
create metadata, history, a writer lease, authorization or an EULA file. The
ordinary Native SDK session opener retains its existing behavior. Network
probing is not an option on this read-only command. Task errors, including
local Loom IPC failures, remain available separately through `get_task`.

## Nightly isolation and evidence

SDK Windows/Linux, UI, Core/Javadoc/scale, MCP, wrapper and each of the eight
generator matrix shards execute independently. The summary consumes actual
hosted job conclusions and each gate's raw log and receipt hashes. Success
requires every expected gate, current source/run/attempt identity and every
required receipt. Missing artifacts produce a reason and cannot turn green.
The summary preserves skipped/cancelled states and fails overall.

The dispatch-only `inject_failure=sdk-windows` scenario deliberately fails that
SDK job with exit 97. It must leave the other jobs observable. No failure is
masked with continue-on-error. Re-run **all jobs** for complete evidence after
a failed attempt; old successful artifacts cannot satisfy a new attempt.

The original PR workflow's three required check names are unchanged.
Editing this workflow does not execute hosted CI. Local summary regressions
are orchestration tests; the actual hosted fault-injection run remains a
separate acceptance item.

SDK timeout repetition runs each existing target operation-timeout case 100
times on each OS without enlarging its deadline. Initialization remains
separate. Raw unittest output and per-round failure/error/skip counts survive.

## Wrapper and dependency policy

The shared [integrity manifest](../../src/main/resources/dev/copperbench/generator/gradle-integrity.properties)
was reviewed against [Gradle's official release checksums](https://gradle.org/release-checksums/).
It pins the existing 9.6.0, 9.7.0, 8.8 and 8.3 bin distributions. It also
recognizes the existing official wrapper JARs; no wrapper binary or Gradle
version was upgraded. Root, current templates and active native examples are
checked. Historical test evidence is excluded and must never be silently
rewritten. Dynamic generation requires the same manifest digest.

`python scripts/ci/wrapper_integrity.py check` rejects missing/mismatched
digests, unreviewed sources/versions or modified wrapper JARs.
`python scripts/ci/wrapper_integrity.py live --output <directory>` uses
fresh isolated homes for each distinct distribution/wrapper-JAR pair:

- official-source cold download and a dependency-free Java build;
- supported Huawei mirror cold download and the same build;
- a deliberately altered ZIP served on loopback, rejected by the real wrapper
  against the reviewed digest before any build executes;
- an offline clean build reusing the official-source cache.

This tests real wrapper and cache behavior. It does not replace a complete
product build or Minecraft validation. The negative loopback payload is
deliberately modified test data, not an official distribution. Every case
retains source, expected digest, exit code, raw log hash and artifact identity.
Java 17 supports this wrapper-only matrix, including Gradle 8.3; the product
build still requires its Java 25/JCEF toolchain. Version changes must update
the independently reviewed manifest and rerun this whole matrix.

npm uses committed lockfiles and `npm ci`; lock integrity hashes identify the
fetched archives. The pre-change audit identified source-map-js 1.2.1 and
undici 7.29.0. The targeted lock update selects 1.2.2 and 7.30.0 within existing
constraints, without changing direct dependency ranges. source-map-js enters
through Vite/PostCSS and Blockly's jsdom/CSS stack; undici enters through
jsdom. npm labels alone do not establish shipped-browser reachability:
Blockly explicitly disables jsdom in browser builds. Every production UI
build now records rendered module IDs, package versions, lock hash and chunk
hashes in `build/reports/ui-dependencies.json`, outside the delivered assets.
The post-update audit and rendered graph must be reviewed at acceptance.

Gradle plugin/direct versions remain as declared in the source build; this
milestone does not claim complete transitive dependency locking or reviewed
checksums for every Maven artifact. Introducing strict dependency verification
requires an independently reviewed resolution inventory, including local JCEF
artifacts, rather than trusting checksums generated from an arbitrary existing
cache. During upgrades: inspect the resolution diff, verify new artifacts from
publisher sources, review the proposed verification/locking baseline, then
run cold and warm builds. Unreviewed metadata is never auto-approved.

The source Java build's `processResources -> buildUiShell` dependency remains:
the complete product build compiles and packages the UI. A Java-only quick
path cannot serve as release-build evidence.

## Consolidated local results

Source is `436a41761d885fa5ad0d76500e2257438137d75f` plus the uncommitted M1/M2
changes in `codex/generation-preflight`. Evidence is under
`build/m2-validation/`. The harness's `sha=local` fields are not commit-bound
CI evidence; `source-inventory.json` records the actual HEAD, dirty diff and
individual file hashes after validation. Earlier failures remain alongside
their corrective reruns.

| Gate | Observed result | Local evidence |
| --- | --- | --- |
| Windows SDK | Python 72/72; TypeScript 16/16 | `python-contract.*`, `typescript-contract.*` |
| Ubuntu 24.04 VM SDK | Python 72/72; TypeScript 16/16; copied archive hash matched | `linux/*contract-linux.*`, `linux-transfer-final.json` |
| Native timeout repetition | Each of two cases ran 100 times per OS; 0 failures/errors/skips | `timeout-windows-rounds.json`, `linux/timeout-linux-rounds.json` |
| Nightly/wrapper helper regressions | 8/8, including an actual Windows batch path with spaces | `harness.*` |
| Workflow structure | Six independent job definitions, 14 gate shards, always summary/upload, no failure masking | `nightly-yaml.*` |
| Java and Javadoc | 107 distinct focused cases passed after corrections; Javadoc and full classes/resources passed | `java-coverage-final.json`, `java-recheck.*`, `doctor-launcher.*`, `doctor-final.*`, `mcp-doctor-final.*` |
| Doctor schema | Two actual backend/product-launcher reports conform | `doctor-schema-final.*`; `build/reports/workspace-doctor/` |
| MCP conformance | Six protocol scenarios, eight checks, no failures | `mcp-conformance.*`, `mcp/summary.json` |
| UI-Core and UI unit | 38/38 and 30/30 | `ui-contract.*`, `ui-state-unit.*` |
| Affected browser scenarios | 29 distinct cases across the 17-case diagnostics run, 22-case state/locale run and 6-case preview run; overlaps counted once | `playwright.*`, `playwright-state.*`, `asset-preview-final.*` |
| Production UI | Build, TypeScript and i18n gate passed | `ui-final-build.*` |
| Documentation and diff | Local Markdown links resolve; scoped whitespace check passes; existing PR workflow unchanged | `documentation-final.*`; `git -c core.whitespace=cr-at-eol diff --check` |
| npm and shipped graph | Audit reports 0 vulnerabilities; eight browser chunks contain 266 rendered module IDs, with no undici/jsdom/source-map-js | `npm-audit-after.json`; `build/reports/ui-dependencies.json` |
| Wrapper manifest | 17 current properties files checked against independent official hashes | `wrapper-manifest.*`; `build/m2-implementation-evidence/gradle-official-checksums.json` |
| Wrapper live matrix | Six mirror cold builds and six incorrect-digest rejections passed; six official cold failed; six official warm were not attempted | `wrapper-cache/cache-run-iqgjb4lm/results.json` |
| Mirror warm follow-up | Six offline clean builds passed using those verified mirror caches | `warm-mirror-results.json` |

Windows used Python 3.13.14; the Ubuntu guest used Python 3.12.3 and Node
22.23.3. The timeout runs took 274.702 seconds and 265.293 seconds respectively.
The VM was shut down and its original 2/3/5 GiB minimum/startup/maximum memory
settings restored after the final SDK run. Its Openbox/Xvfb desktop is not
Ubuntu GNOME installed-package certification.

### Corrections discovered during validation

- The CLI doctor now exits through `Launcher` before logging, preferences,
  IPC probing and normal workspace bootstrap. A real subprocess check confirms
  it creates no product user state, logs, cache or workspace metadata. Selecting
  a backend with a mismatched/missing generator ID remains unknown.
- The real Java MCP server returns plain-text input errors for unknown doctor
  arguments. Both SDKs preserve that reason and the complete tool result while
  retaining the existing `MCP_TOOL_RESULT_INVALID` code. Structured Core error
  codes, conflict and task state still survive. The old malformed-result
  regression fixtures were not weakened; each error is sent once. The real MCP
  test also confirms the rejected query leaves the workspace revision unchanged.
- UI save and asset-preview failures now retain raw diagnostics and rerender
  them after a locale change. The failure, draft and session state survive;
  the change does not repeat a save or preview request. Actual browser tests
  cover these transitions. The unrelated static Function snippet labels still
  have an existing locale inconsistency; this is not a whole-UI localization
  certification.
- The first Java launch met the known local JDK Unix-domain IPC limitation.
  Validation used the existing runtime-compatible setting at process scope,
  with a 1 GiB test heap, and did not change global Java/proxy settings. A CLI
  usage assertion was corrected for the new doctor command before rerunning.

### Open gates

Official `services.gradle.org` downloads timed out on the local path (the
first wrapper case reached the 600-second deadline). The mirror results do
not satisfy this gate: `wrapper-cache/result.json` remains failed. A temporary
SSH download-forwarding attempt was rejected by automatic approval review with
only `blocked by policy`; no forwarding channel was created. This download
failure does not establish a recurrence of the independently repaired Codex
streaming failure.

Hosted SDK-failure injection and the normal full Nightly still need an
authorized push/dispatch. The three PR required checks remain unchanged;
no local receipt is substituted for those checks. M1 player crafting/save/
reopen, M3 same-candidate installers and unfamiliar-user trials remain
unverified. The current chat's successful `mc_doctor` call still reported
`platform=windows`; the project configuration points to the VM, but the MCP
connection must be reloaded before game input. No host-desktop gameplay input
was performed during these M2 checks.

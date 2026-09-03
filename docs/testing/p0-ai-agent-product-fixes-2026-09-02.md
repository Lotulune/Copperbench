# P0 AI Agent / installed-layout product fixes — 2026-09-02

## Scope and claim boundary

This record covers the local implementation and pre-candidate replay for the four P0 items reopened by the external Agent installed-product evaluation in [`docs/handoffs/ai-agent-experience-fixes.md`](../handoffs/ai-agent-experience-fixes.md).

It is **not** a release-gate promotion record. The working tree is ahead of the last verified commit, and the repository policy requires commit-bound code/evidence plus a replay on the selected install/release candidate before a blocked product gate can become `passed`.

The replay used a fresh `exportWin64` tree and an isolated copy of `D:\AICoding\testmod2`; it did not modify or terminate the user's already-running `C:\Program Files\Copperbench` instance.

## Implemented changes

### P0-1 — bundled JDK resolution

- Added shared `BundledJdkLocator`.
- Resolution order is installed flat layout `jdk`, source Java 25 `jdk/jbr25_win_64`, source Java 21 `jdk/jdk21_win_64`, then a usable running `java.home`.
- Fabric and NeoForge workspace task gateways use the shared resolver.
- NeoForge generated toolchain configuration uses the same resolved Java home instead of re-deriving a source-tree path. The generator's default constructor also uses the shared resolver, so direct callers such as loader-migration rebuild cannot reintroduce the installed-layout path bug.
- Missing Java now becomes `BUNDLED_JDK_MISSING`, with attempted paths in the diagnostic/log detail.
- Windows release metadata now distinguishes installed and source-tree JDK layouts instead of claiming the source-tree directory is the packaged JDK.

### P0-2 / P0-3 — desktop MCP and external Agent loop

- `CopperbenchProductShell` starts one workspace-scoped loopback `DesktopMcpRuntime` and closes it with the workspace shell.
- `.copperbench/mcp-connection.json` contains URL/workspace/permission/expiry metadata but no bearer token.
- The bearer token is exposed to the desktop UI once through the dedicated JCEF MCP bridge.
- The AI/MCP view reports `listening` / `not started` from the native runtime instead of a hard-coded connected state.
- MCP serialization preserves explicit final-page `nextCursor: null`.
- HTTP regression covers `initialize`, `get_workspace`, cursor pagination, item creation, code-element workspace plan/preview/apply, build + incremental `get_task`, and revision-conflict reread/retry.
- [`docs/ai/agent-playbook.md`](../ai/agent-playbook.md) documents the supported Agent sequence, `initialValues` examples, Procedure IR edits, `code` escape hatch, token hygiene and conflict handling.

### P0-4 — interactive `runClient`

- Desktop `RUN_CLIENT` no longer treats readiness markers as its success contract and no longer kills Minecraft when a marker is seen.
- The task remains running until the client process exits.
- Server/CI readiness semantics remain separate and still use marker/readiness checks.
- A missing JDK is no longer wrapped as a client-readiness failure.

### User-owned code preservation

- A materialized MCreator/plugin workspace (`*.mcreator` + existing `src`) is treated as user/plugin-owned projection input and is not overwritten by first-party generator projection.
- Regression coverage keeps a `// Start of user code block` sentinel, an independent `wanjian` Java package, and a user recipe byte-exact.

## Automated verification

Focused Java regression after the changes:

```text
JcefMcpBridgeTransportTest                               PASSED
BundledJdkLocatorTest (4 cases)                         PASSED
Fabric1211TaskGatewayTest                               PASSED
NeoForge1211TaskGatewayTest                             PASSED
DesktopMcpAgentLoopTest                                 PASSED
DesktopMcpRuntimeTest                                   PASSED
WindowsDistributionLayoutTest                          PASSED
BUILD SUCCESSFUL
```

Additional focused regressions:

```text
Fabric1211TaskGatewayTest.missingBundledJdkBecomesStructuredTaskDiagnostic   PASSED
Fabric1211GeneratorTest.materializedPluginWorkspaceGenerationPreservesUserCodeAndIndependentPackagesByteExact   PASSED
NeoForge1211GeneratorTest.installedFlatJdkLayoutIsWrittenIntoGeneratedToolchainProperties   PASSED
LoaderMigrationRebuildServiceTest regular rebuild cases                     PASSED
```

The task-level missing-JDK test exercises the real `RUN_CLIENT` task path and observes `BUNDLED_JDK_MISSING`, including the installed `jdk`, source `jdk21_win_64`, and fallback attempts.

UI/schema/document gates:

```text
ui-core: 12 schemas / 15 scenarios validated
Playwright existing suite: 150 passed
Playwright MCP runtime additions: 4 passed
Chinese localization: 126/126 referenced keys translated
Markdown links: all local links resolve
product-status verifier: 8 generators / 23 gates valid, betaEligible=false
```

The full Java suite was also started, but the development command host stopped it at its 10-minute command limit while it was in the historical `ModElementUITest` matrix. No assertion failure had appeared before the host cutoff; this run is therefore **not** recorded as a full-suite pass.

## Fresh Windows export-layout replay

Command:

```text
gradlew.bat exportWin64 -x test
```

Result:

```text
BUILD SUCCESSFUL in 57s
```

The resulting `build/export/win64` layout was inspected directly:

```text
jdk/bin/java.exe                              present
jdk/jbr25_win_64/bin/java.exe                absent
jdk/bin/java.exe -version                     OpenJDK/JBR 25.0.3
```

This is the same flat JDK layout that triggered the installed-product defect.

### Isolated product-shell start

Because another installed Copperbench instance was already open on the user's real `D:\AICoding\testmod2`, the replay did not kill it and did not reuse its workspace. A separate copy was made under `build/p0-export-replay-isolated/workspace` and the fresh export's own:

```text
build/export/win64/jdk/bin/java.exe
```

started `net.mcreator.Launcher -workspace <isolated copy>` with isolated `user.home` and `java.io.tmpdir`.

The clean isolated profile initially stopped at Copperbench's normal mainland-China first-run network-choice modal. The coding-tool GUI session is not attached to an interactive desktop, so keyboard automation could not dismiss it. Only the **isolated test profile** was then pre-seeded with the values produced by accepting the default China-mirror choice; the user's real profile was not changed.

The restarted fresh-export product log records:

```text
Current JAVA_HOME for running instance: ...\build\export\win64\jdk
Installation path: ...\build\export\win64
Loaded workspace file ...\build\p0-export-replay-isolated\workspace\testmod2.mcreator
Opening MCreator workspace: testmod2
Starting ProtocolHandler ["http-nio-127.0.0.1-auto-1-50790"]
Copperbench product shell enabled for workspace testmod2
```

The workspace connection file was created as:

```json
{
  "schemaVersion": "1.0",
  "status": "listening",
  "url": "http://127.0.0.1:50790/mcp",
  "workspaceId": "2fe545df-81c2-4521-ad18-32e2ecc7e698",
  "permissionProfile": "workspace",
  "tokenDelivery": "ui-once"
}
```

The actual file also contains `expiresAt`; it contains no token.

The isolated product window was then closed normally. The test process exited through `CloseMainWindow()` without a forced kill, and the workspace's `.copperbench/mcp-connection.json` was removed. The user's pre-existing installed Copperbench process remained running throughout the replay.

Windows UI Automation can see the product window but only exposes the JCEF area as `Chrome Legacy Window`, so this tool host cannot safely click the in-page “reveal token once” control. No test backdoor was added. Consequently this replay proves the fresh export desktop runtime listens and publishes non-secret metadata, but it does **not** claim a fresh-export external process completed authenticated MCP calls using a UI-retrieved token.

## Real `testmod2` flat-JDK build

The isolated copy was built with:

```text
JAVA_HOME=...\build\export\win64\jdk
GRADLE_USER_HOME=...\build\p0-export-replay-isolated\home\.copperbench\gradle
gradlew.bat --no-daemon build
```

Result:

```text
Fabric Loom: 1.17.20
BUILD SUCCESSFUL in 30s
build/libs/modid-1.0.jar          produced
build/libs/modid-1.0-sources.jar  produced
```

`WanJianClient.java` reports only an existing deprecated-API compiler note. Source hashes were compared before product-shell launch, after shell/MCP startup, and after the flat-JDK build:

```text
source files before:                 27
source files after shell start:      27
hash differences after shell start: 0
hash differences after build:       0
.mcreator hash unchanged:            true
```

The isolated `wanjian` package still contains `FlyingSwordEntity.java`, `WanJianGuiZongItem.java`, and `WanJianRegistries.java`.

## Fresh-export `runClient` lifecycle replay

The same isolated workspace was then launched with the fresh export's flat JDK and the isolated Gradle home:

```text
JAVA_HOME=...\build\export\win64\jdk
GRADLE_USER_HOME=...\build\p0-export-replay-isolated\home\.copperbench\gradle
gradlew.bat runClient --no-daemon
```

The first isolated run had to finish Loom's normal `downloadAssets` step. After that, the client started normally and the log recorded:

```text
> Task :runClient
Loading Minecraft 26.1.2 with Fabric Loader 0.19.3
Loading 51 mods
  - fabricloader 0.19.3
  - minecraft 26.1.2
  - testmod2 1.0.0
Setting user: Player453
Initializing Testmod2Mod
Backend library: LWJGL version 3.4.1-snapshot
```

While those readiness/application markers were already present, the Gradle `runClient` command session was still running. The Minecraft client JVM was independently inspected as PID `12540`; its executable was:

```text
...\build\export\win64\jdk\bin\java.exe
```

and its command line used Fabric's `KnotClient` launch path from the isolated workspace. The visible window title was `Minecraft* 26.1.2`.

The isolated client was closed through the window's normal `CloseMainWindow()` path. The log then recorded `Minecraft) Stopping!`, and only after the client exited did Gradle finish:

```text
BUILD SUCCESSFUL in 3m 39s
```

This replay therefore demonstrates the fixed long-lived lifecycle on a fresh Windows export layout: readiness does not terminate the client, and the task remains alive until the Minecraft process exits. Source hashes before and after this `runClient` replay were identical.

This is still a **pre-candidate export replay**, not the final release-gate proof: the selected signed/frozen install candidate must repeat the same behavior through the actual Copperbench desktop Run Client control.

## Remaining release proof

The four product gates remain blocked until the implementation is committed and the selected next Windows install/release candidate is replayed. In particular:

- launch `runClient` through the actual desktop UI on that candidate and observe Minecraft remain running until the user closes it;
- retrieve the one-time token through the real product UI and use an external process/Agent to `initialize` + `get_workspace`;
- run the complete installed Agent read/write/plan/build/conflict loop against the same candidate;
- attach candidate identity/hash and replay evidence to `product-status.json` before promoting any gate.

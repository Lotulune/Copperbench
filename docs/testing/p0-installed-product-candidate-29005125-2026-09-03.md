# Installed-product P0 exact-candidate replay — `29005125`

Date: 2026-09-03

> Historical candidate status: this exact binary completed the P0 replay below, but later PR review identified token-expiry renewal and SDK loopback-URL parsing hardening. Those fixes change the deliverable, so `29005125` is now superseded evidence rather than the final merge candidate. The installed-product gates remain blocked until the post-review exact candidate is rebuilt and replayed.

## Candidate identity

- Binary source commit: `29005125cacb888591a126af9487764b6633df0d`
- PR: #56, `codex/installed-agent-product-hardening`
- Windows installer SHA256: `B39A5D3B2FB6B51E9AA0A42EEA8B5CDEB8C4AD076EAE456C6B5A1175FF6095A2`
- ZIP SHA256: `8D079747C443865ABCF237950C3DC84B405CF8E95F2945CACEAC4B6F6EFD90D7`
- MSIX SHA256: `4B5E07FE2527CA9EA6ED141502D74096BBDBB486EB5AEA0145FC8CAF8C6A58C2`
- Portable `copperbench.exe` SHA256: `71E5C48DB769C820EBBA258DDD2F6EF6E4524BAA9B4CBBD35BE8CC91825A8651`
- Guest installation directory: `C:\Copperbench-P0-29005125`

The evidence below is bound to the binary-source commit above. A later documentation/status commit may record the promotion, but it is a release-control commit rather than a claim that a different binary source was replayed.

## Automated exact-SHA prerequisites

- Build and test #171, run `33676088550`: **passed** on `29005125cacb888591a126af9487764b6633df0d`.
- Nightly product gates #27, run `33676688082`: **passed** on the same SHA, including the product regression and the eight-generator golden matrix.

## Clean Windows 11 install and bundled JDK

The exact installer was exercised in the clean `Copperbench-G7` Windows 11 guest. Before installation the guest had no system `java`, `javac`, `gradle`, or `git` on `PATH`.

Observed result:

- clean guest: **passed**
- installer exit: **passed**
- product process startup: **passed**
- bundled `jdk\bin\java.exe`: **present**
- IPC failure scan: **no failure detected**
- desktop screenshot: **captured**

This satisfies the installed bundled-JDK resolution requirement without relying on a host Java installation.

## Real desktop Run Client lifecycle

The installed product was foregrounded and the real JCEF title-bar `测试客户端` control was located through the native window hit-test map rather than an internal bridge.

The visible UI click landed at screen coordinate `(521, 55)` and produced a new child process with command line:

`cmd.exe /c gradlew.bat --no-daemon runClient`

The task used the candidate's bundled JDK and remained alive after startup instead of being terminated by a readiness marker. The Run Client lifecycle gate is therefore satisfied by real installed-product UI evidence.

## Desktop MCP real UI credential boundary

The installed product exposed a workspace-scoped loopback MCP descriptor with:

- status `listening`
- `tokenDelivery=ui-once`
- no token field in the descriptor

The external validation process used only the real visible product UI to enter `AI 与 MCP`, exercise `复制 URL`, activate `显示一次令牌`, and then activate `复制配置`.

Security properties observed during the replay:

- copied URL matched the active workspace descriptor
- copied `workspaceId` matched the active workspace descriptor
- the token was never serialized into evidence, logs, or the connection descriptor
- the Windows clipboard was cleared before the first network request
- the automation audit did not contain the token
- the clipboard was empty at completion

## External Agent installed-product loop

The authenticated external process completed the following against the installed Fabric 26.1.2 workspace:

1. `initialize` and `notifications/initialized`
2. `get_workspace`
3. cursor-based `list_mod_elements`
4. direct `create_mod_element` for an item
5. `plan_workspace_changes`
6. `preview_workspace_plan`
7. `apply_workspace_plan`
8. real `build_workspace` plus incremental `get_task(afterLogSequence)` polling — **succeeded**
9. a stale-revision mutation — **rejected** with `WORKSPACE_REVISION_CONFLICT`
10. `get_workspace` reread and retry
11. real projectile creation after the retry — **committed**
12. code-element compile verification — **succeeded** with a filename/classname-consistent validation payload
13. final workspace build — **succeeded**
14. final workspace reread/list — required elements present at revision 8
15. audit credential-redaction check — **passed**

During the first code-validation attempt the harness supplied a public Java class name that did not match the generated element filename. `javac` correctly rejected that payload. The invalid validation-only element was deleted through the public MCP API, a filename/classname-consistent code element was created through the same API, compile verification succeeded, and the final workspace build succeeded. This was a validation-payload error rather than a Copperbench product failure.

## Workspace-scoped shutdown and multi-window behavior

MCreator intentionally supports multiple workspace windows in one JVM. During validation another workspace window was also open, so two workspace-scoped MCP endpoints legitimately belonged to the same JVM process.

The final shutdown assertion therefore targeted the exact `P0 Fabric Candidate - Copperbench 0.1.0` top-level window by process ID plus window title instead of requiring the whole JVM to exit.

After a normal close of that exact workspace window:

- the P0 workspace window disappeared
- the P0 `.copperbench/mcp-connection.json` descriptor was removed
- the P0 old endpoint/token was no longer usable
- the JVM was allowed to remain alive for the other intentionally open workspace

This is the expected workspace-scoped lifecycle.

## Gate conclusion

The exact candidate `29005125cacb888591a126af9487764b6633df0d` provides sufficient candidate-bound evidence to promote:

- `installed-bundled-jdk-resolution`
- `interactive-run-client-lifecycle`
- `desktop-mcp-product-integration`
- `external-agent-installed-product-loop`

This document contains no MCP token or Authorization header.

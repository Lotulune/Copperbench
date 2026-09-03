# Installed-product P0 candidate replay — d0d96877 — 2026-09-03

## Candidate identity

- Remote binary-source commit: `d0d96877afdc00c98cdfeab20524f2f2551b73f9`.
- Git tree: `e775f8ac0579caa7d5f2e073ac553b68751ec844`.
- Build and test #173: `https://github.com/Lotulune/Copperbench/actions/runs/33696803966` — passed.
- Nightly product gates #28: `https://github.com/Lotulune/Copperbench/actions/runs/33697324598` — passed, including product regression and 8/8 generator golden jobs.
- Fixed package build date: `202609030828`.

The host could not fetch the remote commit object from `github.com:443` during the package rehearsal. The locally tested commit object was `1ecbee405f96d449152d1fbda50cc8822e18f8f4`, but its tree is exactly the same `e775f8ac0579caa7d5f2e073ac553b68751ec844` tree stored by remote commit `d0d96877`. The Windows package tasks do not consume or embed `git rev-parse HEAD`; they consume the source tree plus the explicit build date. Release-control metadata therefore identifies `d0d96877` as the binary source while preserving this tree-equivalence fact instead of pretending the local commit object was fetched.

## Windows artifacts

| Artifact | SHA-256 |
| --- | --- |
| Installer EXE | `595105EC34C0A9EBE12A9F0B5189594F05B975D468DD071F15DD690F06ABD9F1` |
| ZIP | `6BE1674176164E349BA35F28623F6266E3AD54B98CBE4627465D154B184D6953` |
| MSIX | `5B9036888AA8B91218039519653C8778FC6DBAD717FF7FB7E767E26B8F89A6C7` |
| Portable `copperbench.exe` | `279E7CA27DFCF4F546B9EC758DAEB0736179DCB39C86648B56E21B0C7E092105` |

The guest copy of the installer was hashed independently before installation and matched the host installer SHA-256 exactly.

## Clean Windows 11 installation and bundled JDK

The package was installed to the isolated guest path `C:\Copperbench-P0-d0d96877` on `Copperbench-G7`.

- clean guest baseline: passed;
- no system `git`, `java`, `javac`, or `gradle` was available on `PATH` before installation;
- silent installer: passed;
- `copperbench.exe`: present;
- packaged `jdk\bin\java.exe`: present;
- product started in the interactive guest session;
- text-log IPC failure scan: no failure detected;
- desktop screenshot capture: passed.

## Real desktop Run Client

A fresh Fabric 26.1.2 workspace was created with revision 0 and no mod elements. The installed JCEF window was foregrounded and the native Windows `WM_NCHITTEST` map was used to locate the actual titlebar controls instead of relying on an unverified coordinate guess. The `测试客户端` control occupied screen x `471..570`; the visible click at `(521,55)` spawned `cmd.exe /c gradlew.bat --no-daemon runClient`.

The task remained alive instead of being killed by a readiness marker. The Gradle wrapper and daemon used `C:\Copperbench-P0-d0d96877\jdk\bin\java.exe`. After the workspace had been built once by the Agent loop, a warmed visible-UI rerun reached the real Fabric client JVM (`net.fabricmc.devlaunchinjector.Main`) using the same bundled JDK. Ending the client caused `runClient` to finish while the Copperbench JVM remained alive.

The earlier binary-source candidate `29005125` additionally has the full visible Minecraft-window normal-close observation. The post-review `d0d96877` code changes are confined to desktop MCP token renewal and SDK connection-URL validation; they do not touch the Run Client implementation.

## Desktop MCP credential boundary

The final Agent replay used the installed Fabric 26.1.2 workspace `p0fabricfinald0d`.

- descriptor status: `listening`;
- `tokenDelivery`: `ui-once`;
- descriptor contained no token;
- loopback endpoint accepted connections;
- visible `AI 与 MCP` navigation and non-secret Copy URL calibration matched the active descriptor;
- the one-time token was revealed through the real product UI;
- Copy Config returned a configuration matching the active URL and workspace ID;
- the credential was never serialized into the descriptor, evidence, or audit log;
- the system clipboard was cleared before the first network request and was empty at completion.

Token-expiry behavior is covered deterministically by `DesktopMcpRuntimeTest`: after advancing a mutable clock to the renewal window, the runtime publishes a renewed credential/expiry without restarting the workspace; the previous token remains valid through its original expiry, becomes unauthorized afterward, and the renewed token continues to authenticate. This avoids requiring a 12-hour wall-clock installed test while exercising the exact renewal boundary.

## Authenticated external-Agent loop

The credential was consumed only in the guest process and the following installed-product operations passed:

1. `initialize` and session establishment;
2. `get_workspace` with matching workspace ID and revision 0;
3. initial cursor listing;
4. direct `item` creation;
5. `plan_workspace_changes`;
6. `preview_workspace_plan`;
7. `apply_workspace_plan`;
8. multi-page cursor listing;
9. `build_workspace` plus incremental `get_task` logs — succeeded;
10. forced stale mutation — rejected with `WORKSPACE_REVISION_CONFLICT`;
11. workspace reread and `projectile` retry — committed;
12. `code` creation with a filename/classname-consistent Java public class;
13. code compile verification task — succeeded;
14. final `build_workspace` plus `get_task` — succeeded;
15. final readback — four required elements present at revision 4;
16. automation audit token-redaction check — passed;
17. normal desktop close — descriptor removed;
18. old endpoint/token probe — unusable after close;
19. clipboard empty at completion.

## Review hardening that superseded 29005125

Two unresolved P1 review findings were fixed before this candidate was built:

- desktop MCP credentials renew before the fixed 12-hour expiry instead of leaving a long-lived workspace in a misleading `listening` state with only an expired token;
- Python and TypeScript workspace-connection readers structurally parse the URL and require exact loopback semantics (`http`, hostname `127.0.0.1`, explicit valid port, exact `/mcp` path, no userinfo/query/fragment), rejecting userinfo-based off-host URL confusion.

Regression coverage includes the desktop renewal overlap/expiry behavior, malicious URL metadata for Python and TypeScript, the broader desktop MCP Agent loop, HTTP authentication, JCEF bridge tests, and AI eval manifest checks.

## Gate conclusion

The post-review source tree represented by `d0d96877` satisfies the four installed-product P0 gates:

- `installed-bundled-jdk-resolution`;
- `interactive-run-client-lifecycle`;
- `desktop-mcp-product-integration`;
- `external-agent-installed-product-loop`.

This evidence promotes engineering eligibility only. It does not authorize merging, tagging, signing, asset promotion, or public release by itself.

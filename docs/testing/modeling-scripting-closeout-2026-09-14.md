# Modeling and scripting integration — 2026-09-14

The completed native-window fixes, Blockbench workflow and Python workbench were extracted from a shared working directory into `codex/modeling-scripting-closeout`, based on `57f0275ef4121e054b0d785797ab09efe676d2ef`. The user authorized committing and pushing completed work while explicitly excluding the ongoing `feature/ui-ux-redesign` branch.

The redesign branch, its index and working files were left in place. The integration retains the original title bar, navigation, workspace overview and asset header, adding only the completed pointer gestures, Python navigation and Blockbench setup/task entry points. Redesign colors, banners, command palette, feedback utilities and new IDE/build-run views are excluded. Local conversation archives, credentials, test VM files and external application binaries are not part of the commits.

## Verified integration

The implementation tree is recorded by `0d8134aac388d710e77baabec475fe883af91b25`, following the native-window fix commit. Documentation and selected acceptance evidence are committed separately.

| Check | Result |
| --- | --- |
| Java tests for Blockbench, headless/native APIs, JCEF bridges, Fabric generation, MCP HTTP and window hit testing | 149 discovered; 142 passed, 7 skipped, zero failures/errors |
| Actual CPython product smoke (`runNativePythonSmoke`) | Passed: independent product process, live desktop session attachment, managed console, persistence, context/operators/timers, errors/completion and stop/restart |
| Python SDK unit tests | 23 passed |
| UI-Core schema and localization scanner tests | 30 passed |
| Browser checks for Blockbench, Python/live-edit and native window gestures at 1920 and 1366 widths | 40 passed |
| Production UI build | Passed: 365/365 localization keys, TypeScript and Vite |
| Whitespace and credential-pattern scan | Passed; no private-key or service-token matches in selected source/documentation files |

The Java skip count is reported separately, not counted as passes. The original R7 installed-product and game checks remain documented in the [cross-platform Blockbench acceptance report](blockbench-m4-2026-09-13.md); [selected screenshots and hash checks](../../evidence/blockbench/2026-09-14/README.md) are now versioned. That installed candidate predates the integration-only UI extraction, which was rechecked by the build and browser suite above. No new installer or public release is claimed for this integration tree.

## Reproduction and retained failures

Java runs use the repository's `scripts/run-gradle-external.ps1` wrapper with `--offline --no-daemon --console=plain`. The passing test log is `.tmp/gradle-external/3b230794912446fd857f0a306f98e9fc.log`; the passing CPython smoke log is `.tmp/gradle-external/5e3babb9be2e455abf9ef4e396f92d26.log` in the integration worktree. Browser verification used an isolated server at port 5197 so it could not reuse the ongoing redesign preview.

An initial extraction still included the asset header redesign and failed TypeScript because its search props were unused. The original asset header was restored in the integration copy while retaining the Blockbench controls; the final build and all browser checks then passed. The first smoke invocation supplied an executable path that the PowerShell wrapper split incorrectly; it failed task selection before executing a test. Re-running with the existing `python` executable on PATH passed. Neither failed invocation is reported as successful.

The environment reuses existing JDK and Node dependency directories through worktree-local junctions. No global Git/proxy settings or CI files were changed. The configured proxy initially reset GitHub connections; a command-local direct Git connection succeeded. Only the integration branch is intended for push; the redesign branch and `main` are not merge or push targets for this operation.

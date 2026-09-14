# Python Core API and live desktop validation — 2026-09-13

## Changes under test

- Recognize Core's `cancelled` command result as successful cancellation.
- Cover pipe writes and response waits with the request deadline; allow another thread to close a blocked call.
- Preserve task logs collected before the terminal poll.
- Add `Workspace.connect(file)` using an authenticated loopback native protocol against the desktop's existing Core and writer lease, without MCP.
- Store discovery credentials under the OS user's `.copperbench/python-sessions` directory, protected by POSIX permissions or Windows ACLs; disconnect clients and remove credentials when the host closes.
- Add named element handles, immediate field assignment and multi-field updates with observed-revision conflict checks.
- Publish scripting mutations to desktop Core event subscribers.
- Refresh clean generic element inspector fields after external edits; preserve dirty drafts and require an explicit reload after a conflict.

## Executed checks

| Check | Result |
| --- | --- |
| `python -m unittest discover -s sdk/python -v` | 17 passed |
| Focused Java tests: NativeApiSessionTest, NativeApiProductTest, DesktopPythonRuntimeTest, WorkspaceTaskEventTest, JcefBridgeEndpointTest | 13 passed |
| `gradlew runNativePythonSmoke --offline` | Passed: independent Python/product process and live attachment to an already writer-leased product session |
| Live attachment smoke | Named object edits, stale-handle rejection, refresh, reconnect, authorization rejection, persistence after reopen, exactly two update events and one create event; no MCP server |
| `npx playwright test e2e/commands.spec.ts e2e/python-live-edit.spec.ts --project=chromium --project=compact-1366` | 30 passed |
| `npx tsc --noEmit` and `npx vite build` | Passed |
| `node --test ui-shell/tests/localization-keys.test.mjs` | 5 passed: Java path literals, real translation uses, comments, JSON/TypeScript keys, actual Blockbench manifest |
| Complete `npm run build` after the localization scanner fix | Passed: localization gate (365 keys), TypeScript, Vite |
| `git diff --check` | Passed |

The browser test exercises the real JCEF bridge and React inspector using the canonical mock Core behind the host boundary. The Java/Python smoke exercises actual workspace persistence and the desktop event subscription boundary. An installed executable and a real JCEF window were not used for this validation.

## Resolved workspace build blocker

The localization gate previously treated the filename `task.json` in `BlockbenchModelingService.java` as a translation key. The follow-up fixes the scanner to recognize direct Java path arguments, while retaining checks for the same string when used as localized text. The modeling service and translation catalog were not changed for this fix. Complete `npm run build` now passes. The 13 focused Java tests and both real Python product smoke paths were rerun successfully after this fix.

## Scope

The initial iteration enabled shared-session scripting and object handles. The subsequent [application Python workbench](python-workbench-2026-09-13.md) adds a managed CPython worker, an in-application console and scripting context. It does not embed CPython in Java or expose arbitrary Java objects. The existing MCP client remains available. Changes remain uncommitted alongside other ongoing workspace edits.

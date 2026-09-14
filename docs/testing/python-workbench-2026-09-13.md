# Application Python workbench validation — 2026-09-13

## Implemented scope

The desktop owns one persistent CPython worker per workspace, using the already opened Core session through the native API. The workbench supplies a script editor, file open/save dialogs, a persistent interactive console, history, completion, traceback line navigation, bounded streaming output, stop/reset, examples and registered-operator actions. Browser recovery and navigation do not own the interpreter lifetime.

`cb.context`, `cb.data`, `cb.ops`, `cb.types`, `cb.utils` and `cb.app` provide current selection, stable element handles, validated natural field attributes, Core operators, custom operator registration, timers and workspace/selection handlers. Python and MCP external commands publish to the same desktop event subscribers. No MCP service is required by Python.

Interpreter readiness and the last script result are separate fields. A script exception leaves the interpreter usable but records `lastResult=failed`; cancellation records a separate result. Script mutations completed before an exception are retained. A failed script still reports any operators it registered before failing.

## Evidence

| Check | Result |
| --- | --- |
| `python -m unittest discover -s sdk/python -v` | 23 passed |
| Native API, product persistence, desktop runtime, JCEF Python boundary, Core event and MCP HTTP tests | 25 passed |
| Real managed CPython smoke via `runNativePythonSmoke` | Passed |
| Existing editor command regressions, both desktop sizes | 28 passed |
| Live-edit and Python-workbench browser tests, both desktop sizes | 6 passed after waiting explicitly for interpreter readiness before requesting completion |
| Full UI build: localization gate, TypeScript and Vite | Passed |
| Markdown links and whitespace diff checks | Passed |

The real Python probe verifies retained variables and expression output; live context selection; persisted element updates using both JSON pointers and natural properties; rejection of unknown attributes; custom operators; timers and handlers; error line numbers and failed-script status; completion; interruption after an actual infinite loop has started; clean variables after restart; and workspace availability after stopping Python. The original independent-process and attached-session probes are also retained.

Java reports were isolated under `build/python-workbench-validation` using a temporary Gradle init script because another task was concurrently writing the default Gradle test-report directory. The initial shared-report failure was a missing binary report file; the isolated run completed successfully.

Browser tests exercise the real React workbench against an injected native-host fixture. Actual Core persistence and CPython execution are verified separately by the product smoke. A graphical installed JCEF executable was not used for the combined end-to-end test.

## Runtime and compatibility boundaries

- CPython 3.11+ must be available; users can select its executable in the workbench. The application does not silently install a runtime.
- Scripts run as the current OS user in a managed child process. The JCEF execution bridge is restricted to the packaged workbench's main frame; there is no remote Python-execution operation in MCP or the native workspace protocol.
- Stop terminates the interpreter and its owned descendants. Already submitted Core build/game tasks remain separately cancellable.
- The event loop pumps callbacks while idle. Events may coalesce; this is not an event-history replay API.
- Asset/registry/field dictionaries are snapshots; writes go through field assignment or Core operations. Text blocks are interpreter-local and do not silently overwrite editor drafts or files.
- This is the Copperbench scripting model, not a Blender `bpy` plugin compatibility layer or arbitrary Java-object bridge.

Usage is documented in [Python 工作台](../user/python-workbench.md). These changes remain in the shared working directory, uncommitted and not installed over the existing application.

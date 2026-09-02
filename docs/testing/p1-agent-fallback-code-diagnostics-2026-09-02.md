# P1 Agent fallback / code diagnostics — 2026-09-02

## Scope and evidence boundary

This record covers the local implementation and fresh Windows export replay for the P1 items in
[`FR-PROD-05`](../../PRD-NEXT.md): the installed-product headless fallback, direct Java `code` elements,
structured Java compilation diagnostics, JDK-resolution diagnostics, Agent guidance, and preservation of
user-owned code.

It is **not** a release-candidate promotion record. The implementation is still in the working tree and must
be bound to a commit and replayed on the selected Windows candidate before any candidate-specific claim is
made.

## Installed-product headless fallback

The product launcher now recognizes:

```text
copperbench.exe headless --workspace <path.mcreator> <command> [options]
```

before entering the desktop single-instance/JCEF path. This means an already-running GUI instance does not
capture the automation request. `HeadlessProductLauncher` opens the real MCreator workspace, attaches the same
Copperbench application service used by the desktop/MCP paths, and waits accepted asynchronous tasks to a
terminal state before returning one JSON result.

Normal application console logging is removed from headless stdout so stdout remains machine-readable.

### Fresh `exportWin64` replay

The export was regenerated from the current working tree and its actual executable was used against the
isolated `testmod2` copy:

```text
build/export/win64/copperbench.exe headless --workspace <isolated testmod2.mcreator> validate
```

Result:

```text
process exit: 0
stdout: one UTF-8 JSON command_result
status: succeeded
operation: validate_workspace
task.state: succeeded
stderr: empty
```

The same command with `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` also exits 0 and returns the same machine
JSON contract. The only stderr line in that forced-AWT-headless run is the JVM's own
`Picked up JAVA_TOOL_OPTIONS` notice; no Swing/JCEF initialization failure occurs.

Real exported-product build:

```text
build/export/win64/copperbench.exe headless --workspace <isolated testmod2.mcreator> build
```

Result:

```text
process exit: 0
status: succeeded
task.state: succeeded
Gradle log: BUILD SUCCESSFUL in 11s
stderr: empty
```

This also verifies that the product-level headless entry waits for the asynchronous Gradle task rather than
returning immediately after task acceptance.

## Direct Java `code` elements

`create_mod_element` accepts `elementType=code` and `initialValues.code`. The MCreator mutation gateway writes
the supplied Java text to the upstream CustomElement-associated `.java` source and marks the element code-locked.

`CodeElementPersistenceTest` verifies that the Java text written to the associated source is byte-for-byte the
Agent-supplied string and that the resulting ModElement is code-locked.

For a direct MCP `create_mod_element(type=code)` call, Copperbench starts a real `build_workspace` compile
verification task at the new workspace revision and returns it under `data.compileVerification`. Agents must
follow that task with `get_task(taskId, afterLogSequence)` until terminal state. Workspace plans that contain a
code element do not implicitly build; the Agent playbook requires an explicit build after apply.

## Structured Java compilation diagnostics

The Gradle task gateway parses javac diagnostics into stable `JAVA_COMPILE_ERROR` entries. The diagnostic
contains a workspace-relative Java source path and line-number-bearing fallback text, while the ordinary backend
failure diagnostic remains present for the overall build failure.

Automated coverage verifies the full API chain:

```text
create_mod_element(type=code)
  -> data.compileVerification
  -> get_task
  -> task.state=failed
  -> diagnostics contains JAVA_COMPILE_ERROR
  -> /src/main/java/net/example/Broken.java
  -> Line 1: cannot find symbol
```

`Fabric1211TaskGatewayTest` independently verifies parsing and deduplication of real javac-style output,
including localized Chinese `错误: 找不到符号` output.

### Fresh exported-product failure replay

A separate copy of `testmod2` was made under `build/p1-code-diagnostic-replay`. A deliberately invalid independent
source file was added only to that disposable copy:

```text
src/main/java/net/example/BrokenAgentBehavior.java
```

Running the actual fresh export:

```text
build/export/win64/copperbench.exe headless --workspace <diagnostic replay workspace> build
```

returns:

```text
process exit: 10
status: failed
task.state: failed
diagnostics.error: 2
JAVA_COMPILE_ERROR
path: /src/main/java/net/example/BrokenAgentBehavior.java
fallback: Line 3: 找不到符号
FABRIC_BUILD_FAILED
```

The JSON also preserves the complete Gradle/javac log, including the original source line, symbol description,
`compileJava FAILED`, and `BUILD FAILED`. This proves the structured diagnostic is present in the exported product,
not only in a mocked task gateway.

## JDK/run-task diagnostic context

`BundledJdkLocator` now logs both successful and failed resolution with:

```text
distributionRoot
javaRelease
resolvedJavaHome (on success)
attempted paths
java.home
user.dir
```

Missing Java still maps to stable `BUNDLED_JDK_MISSING`; the resolver never injects a non-existent `JAVA_HOME`.

## User-owned code and Agent guidance

The P0/P1 regression set continues to verify that generation/build/runClient do not delete user-code blocks,
independent user Java packages such as `wanjian`, or user-owned resources. The supported Agent sequence,
`initialValues`, Procedure IR, `code` fallback, revision-conflict recovery, task polling, and token hygiene are
documented in [`docs/ai/agent-playbook.md`](../ai/agent-playbook.md).

## Verification summary

```text
HeadlessCliTest                                             PASSED
HeadlessProductLauncherTest                                PASSED
CodeElementPersistenceTest                                 PASSED
BundledJdkLocatorTest (4 cases)                            PASSED
Fabric1211TaskGatewayTest                                  PASSED
DesktopMcpAgentLoopTest (including failed code compile)    PASSED
fresh export copperbench.exe headless validate             exit 0
fresh export copperbench.exe headless build                exit 0
forced java.awt.headless=true validate                     exit 0
fresh export invalid-Java headless build                   exit 10 + JAVA_COMPILE_ERROR
```

The repository intentionally stores some Java sources as CRLF (`.gitattributes` disables line-ending
normalization). For whitespace verification use `git -c core.whitespace=cr-at-eol diff --check`; plain
`git diff --check` treats the intentional CR byte on newly added CRLF lines as trailing whitespace.

## Remaining candidate proof

FR-PROD-05 is locally implemented and fresh-export replayed. If the selected next candidate claims this P1
fallback as supported, repeat at least `headless validate`, `headless build`, and one failing Java build against
that exact candidate/installer identity and record the executable SHA-256. This P1 evidence does not promote or
bypass the four P0 installed-product gates.

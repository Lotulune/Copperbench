# NeoForge 26.3 structured function slice

The [Native runner](../../../sdk/python/structured_263_function_delivery.py) creates and edits the function through the product, generates and builds its JAR, runs an independent host, and reopens the product before a second verified export. This fixture contains only test code and resources; the function body is created through the Native API.

Use the existing `get_workspace_environment` response's `fieldContracts.function` contract and its `minimalExample`. Function discovery remains specialized; it does not use the generic item/recipe field-contract endpoint.

| Input | Scope |
| --- | --- |
| `name`, `displayName`, `description` | Function identifier and creator metadata. The preview resource identifier uses a lowercase letter followed by up to 63 lowercase letters, digits or underscores. |
| `namespace` | `mod`, the workspace's mod namespace. The editor displays it read-only. |
| `code` | Exact text body; empty creation defaults to `# New Copperbench function` followed by LF. |
| `commands` | Array of strings, joined by LF with a final LF; an empty array yields an empty body. |

The two body representations must agree if both are supplied. Input checks reject incorrect JSON types, conflicting aliases, NUL characters, leading command slashes, unsupported namespaces and fields. Other command syntax and registry resolution belong to Minecraft's parser; the product does not claim complete static command validation. Tags and automatic tick/load registration are outside this slice.

The [fixed manifest](required-cases.json) contains only T13 under `camp-kit-function-v1`, minimum 1, SHA-256 `0631c25a606d553a868fed4c0e02adda1d95852f1ac9cd26e6097a3c367d02c1`.

The [host](src/gametest/java/copperbench/acceptance/AcceptanceTests.java) loads the generated function from the final JAR and executes it through the real server function manager. It independently reads score `camp_probe / camp_count = 11` and the gold block at `(8,80,8)`. It also preserves the real parser's line-1 diagnostics for an unknown command and missing item reference; these negatives do not increase the business case count.

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 runStructured263FunctionDelivery '-Pp263Generator=neoforge-26.3' '-Pp263GradleHome=C:/Users/Administrator/.gradle' --no-daemon --console=plain
```

Evidence is written under `output/p263-21/native`. The run uses isolated task authority and revokes it in cleanup. Player controls, installation and the complete scoped-support gate remain separate acceptance tickets.

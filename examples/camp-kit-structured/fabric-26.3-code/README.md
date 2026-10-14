# Fabric 26.3 structured code slice

The [Native runner](../../../sdk/python/structured_263_code_delivery.py) creates a primary Java class and two auxiliary source files through the product. It deliberately edits source outside the product, injects a compiler error, reopens the workspace, repairs the error explicitly, then builds, tests and exports the same JAR. A third product process verifies that regeneration preserves the sources and that the prior verified export can be repeated.

Discovery uses `get_workspace_environment.data.fieldContracts.code`, the existing specialized code contract. Its six input names are `name`, `displayName`, `description`, `code`, `codeFiles` and `sourceFingerprints`. The empty default example creates an editable, manually owned source bundle. `codeFiles` contains relative Java paths and exact source text. Ownership, path and fingerprint checks remain enforced by persistence. Source text is not a claim of Java validity.

The editor attaches the fingerprints observed with the draft only when saving source changes. Metadata edits do not resend source text or fingerprints. Stale workspace revisions, stale source fingerprints, conflicting file ownership, incorrect types and escaping paths are rejected. The preview extension does not grant source takeover.

The [manifest](required-cases.json) fixes T12 under `camp-kit-code-v1`, minimum 1, SHA-256 `907576949f1b9f3b2e29d1cdcac277975788e9003a36c35f8f60eababa4f4dad`.

The [independent host](src/gametest/java/copperbench/acceptance/AcceptanceTests.java) loads the final JAR's manual classes and checks nine fixed boundary inputs, rejection of a negative input and the independently edited helper's value of 41. The fixture contains only test code and resources; the implementation is created through the Native API. The runner compares source bytes, including CRLF and a Chinese comment, before and after reopen/regeneration. The deliberate compiler failure must identify `BundleMath.java` and `missing_p263_symbol`.

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 runStructured263CodeDelivery '-Pp263Generator=fabric-26.3' '-Pp263GradleHome=C:/Users/Administrator/.gradle' --no-daemon --console=plain
```

Evidence is written under `output/p263-22/native`. The isolated task authority is revoked in cleanup. Player controls, installation and the complete scoped-support gate remain separate tickets.

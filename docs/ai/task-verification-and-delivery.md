# Task verification and portable delivery

`get_task`, its strict v1.0 `task.verification`, and `task.verifiedExport` remain unchanged.
Hosts advertising `get_task_verification` in Native ready operations, MCP `tools/list`, or doctor queries support the separate `resultVersion: "1"` result.
The environment's `agentWorkflow.taskVerificationVersions` also advertises the supported versions.
Request `{"taskId": "<UUID>", "resultVersion": "1"}`; unknown versions are rejected without rerunning anything.
The [result schema](../../ui-core/schemas/v1.0/task-verification.schema.json) defines `subreason`, nullable coverage counts and a language-neutral `message` with parameters.

Native Python and MCP Python expose `get_task_verification(task_id)`; TypeScript exposes `getTaskVerification(taskId)`.
Call these only on an advertised host. Existing `get_task` / `getTask` methods retain their old requests.
The UI uses the Core result and keeps the original message when switching language.
For older/disconnected hosts it can explain recorded counts, but does not claim a fresh match to current inputs.
Missing counts remain unknown, not zero. English `detail` is never a machine protocol.

Reasons distinguish running, cancelled/unconfirmed, missing/invalid/stale reports, failed cases,
zero or insufficient business tests, and passed current/historical/unconfirmed input.
Current-input labels describe the read-time fingerprint observation; export always rechecks the original XML, JAR and input.
Test failures take precedence over sufficient counts; skipped and framework self-tests never satisfy the minimum.
Repeated `(classname, name)` identities invalidate a report.

## Export and offline verification

A successful verified export publishes these five files in one directory:

| File | Meaning |
| --- | --- |
| `manifest.json` | Bundle version 1, shared task/input/acceptance identities and actual file sizes/SHA-256 |
| `verified-mod.jar` | The exact accepted mod artifact |
| `gametest-results.xml` | The original test report |
| `verification.json` | Portable evidence version 1, acceptance conditions and recomputed coverage |
| `application-identity.json` | Allowlisted application facts observed during acceptance; explicitly unavailable for older records |

The application JAR's digest is separate from the mod digest. Source commits not observed by the running application are not invented.
New exports stage all files beside their destination and publish using an atomic directory move.
An existing destination is rejected; failures remove the temporary files.
The exported API receipt still has its v1.0 shape.

Copy the directory to a clean location, or ZIP exactly these five files at the archive root, then run:

```sh
python scripts/verify-delivery-bundle.py /path/to/delivery
python scripts/verify-delivery-bundle.py /path/to/delivery.zip
```

The verifier uses only Python's standard library. It does not extract or execute the JAR, launch Gradle/Minecraft,
write output files, or access the network. It recomputes hashes, unique case counts, failures/skips and the required minimum.
It checks task/input/application references and preserves the input status **at export**, rather than claiming a match to a live workspace.
Absolute/traversal paths, duplicate/extra members, links/reparse points, DTDs/entities, unknown versions and truncated archives are rejected.
Limits: 256 MiB mod JAR, 8 MiB XML, 1 MiB receipt, 64 KiB application identity and 256 KiB manifest; ZIP members have the same expanded limits.

Exit `0` means `consistent`; exit `1` means `rejected`; exit `2` means `legacy_incomplete` (an older receipt without a manifest).
Unknown application identity is reported as `unavailable`, never upgraded to an observed release binary.
Consistency is not authentication: `provenance: "not_authenticated"` explicitly states that hashes alone do not prove who produced a bundle or that its tests actually ran.
Compare the application digest with a separately frozen, trusted candidate manifest before release acceptance.

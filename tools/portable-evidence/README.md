# Portable verified-export evidence

Run with Python 3.10 or newer; only the standard library is required. Select the actual directory returned by `VerifiedArtifactExporter`, after the export task completes:

```text
python tools/portable-evidence/copperbench_evidence.py collect <export-directory> <new-delivery-directory>
```

The destination must not exist and its parent must exist. Collection preserves the original bytes of `verification.json`, `verified-mod.jar` and `gametest-results.xml`. It copies no other files from the export directory. The original receipt must be the portable file on disk, not a reconstructed API response.

The collector checks the selected receipt's hashes, reads the sources again after copying, and publishes the sibling staging directory with one rename. A missing, changed or unsupported input returns exit 2 with a stable JSON error code; ordinary failures remove the staging directory and leave no completed delivery. A process forcibly terminated during copying can leave a `.copperbench-evidence-*` staging directory, which is not a completed delivery.

`manifest.json` has its own `bundleVersion: "1"`, separate from the exporter's `portableEvidenceVersion`. It contains the exact three relative members, sizes and SHA-256 hashes, plus the task/input identity, observed environment and current-at-export or historical-at-export scope. Required-case evidence also carries the original manifest and task/workspace binding. The original frozen-file hash is retained as an opaque producer reference; collection cannot reconstruct its path-bearing source file from the portable projection.

Compatibility is explicit:

| Input | Collection result |
| --- | --- |
| Portable v1 with `requiredCases/1` | `required_cases_v1`; preserves the selected manifest |
| Portable v1 without required cases, such as the five-test Fabric 1.21.1 M1 | `minimum_only_legacy`; never promotes it to the 26.3 gate |
| 26.3 receipt without required cases | Rejected |
| Unversioned receipt or API envelope | Rejected as unsupported legacy input; re-export the original task |
| Unknown bundle, portable or required-case version | Rejected; no guessed migration |

Absent Java/API versions stay absent. Unsupported fields and local paths are rejected instead of silently rewritten. Collection establishes byte binding and complete delivery; use the independent consumer below to recheck raw testcase semantics.

## Offline verification

Copy the single `copperbench_evidence.py` file and a collected directory or ZIP to a clean directory, then run:

```text
python copperbench_evidence.py verify <delivery-directory-or-zip>
```

No project checkout, Java, Gradle, Minecraft, SDK, network call or ZIP extraction is involved. ZIP members must be the same four files at the archive root. Stored and Deflate compression are supported; encrypted, split and ZIP64 central directories are rejected. Inputs are read without writing a success file or modifying the bundle.

Exit 0 returns `status: "integrity_verified"`, the original task/input/environment identity and recalculated `rawCounts`. Exit 2 returns `status: "failed"` with a stable `EVIDENCE_*` code. The consumer recomputes every member's length and hash, checks the portable receipt and manifest against each other, parses original XML identities and verifies the minimum and every required business case. Repeated attempts/suites cannot add coverage. Framework self-checks and skipped cases do not count. A declared success or coverage total cannot override the raw report.

JUnit identity is the original `(className, name)` pair, with an absent class preserved as an empty string. Required GameTest resource identity uses the complete resource name independent of the structure class. Fields are never truncated before comparison. [Shared identity vectors](identity-vectors.json) run against both the Java product parser and the independent Python reader, including the length-prefixed UTF-8 manifest digest and Java UTF-16 sort order.

Limits are fixed for bundle v1: four root members; 256 MiB for the JAR, 8 MiB for XML, and 4 MiB each for receipt/manifest JSON; 10,000 testcases; 64 levels of XML/JSON nesting; 64 KiB of ZIP central-directory bytes; compression expansion at most 200×; archive bytes at most the sum of member limits plus 1 MiB. Extra members, absolute/traversing paths, ambiguous or duplicate normalized names, NUL names, filesystem links/junctions, ZIP links/special files, corrupt CRC/headers, and unsupported versions fail before acceptance. XML DTD/entities are disabled.

`current_at_export` preserves the producer's claim about its input at export time; `historical_at_export` remains historical. The offline consumer does not know the recipient's current workspace. The frozen binding hash is an opaque reference because the original path-bearing frozen file is not bundled. Hash consistency and semantic checks are not producer authentication: a party that rewrites all unsigned evidence coherently can author a different claim. Results explicitly set `recipientWorkspaceChecked` and `producerAuthenticated` to false. Gameplay and client acceptance remain scoped to the original evidence.

Focused checks:

```text
python -m unittest discover -s tools/portable-evidence -p "test_*.py"
```

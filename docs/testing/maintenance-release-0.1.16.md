# Windows 0.1.16 publication and integration record

[Windows v0.1.16](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.16) was published at **2026-10-22T05:50:38Z** from signed source `c87d2e0f0e924adc6ad31575aa5b06fe6da9dcf0`. The complete [release workflow](https://github.com/Lotulune/Copperbench/actions/runs/39726326742) passed tests, Javadoc, packaging, branding, hashes, SBOM/provenance, draft asset verification and publication.

## Changes and review

The release includes guided Simple creation, bilingual runtime messages and tours, model catalogs/manual IDs, effort, file/image attachments, session permissions, Markdown/copy and history recovery. The [independent review](simple-mode-independent-review-2026-10-22.md) fixed five stop/draft/history/cleanup issues. [PR #146](https://github.com/Lotulune/Copperbench/pull/146) added README badges; [PR #147](https://github.com/Lotulune/Copperbench/pull/147) retained native failure evidence and omitted inactive sidebar rendering. Both are merged. [PR #148](https://github.com/Lotulune/Copperbench/pull/148) integrated the reviewed creator workflow, preserving PR #147 ancestry.

[PR #149](https://github.com/Lotulune/Copperbench/pull/149) corrected the missing upstream UI dependency license texts found during package finalization. It adds 55 packages' provided texts, retaining the existing Blockly notice and accounting for all 56 shipped packages. Runtime code is unchanged by this packaging follow-up. ZIP/tar consumer tests require source-declared notices to be present and byte-identical.

## Verification

| Layer | Actual result |
| --- | --- |
| Local integration | 74 UI unit, 50 UI-Core, 36 browser, seven comparison-harness and six Windows native/evidence tests passed; native reference-refresh P95 114.3 ms against the unchanged 300 ms gate. [Receipt](../../evidence/maintenance/2026-10-22/release-0.1.16/integration/verification.json). |
| Native PR #147 comparison | [Full nightly / A-B](https://github.com/Lotulune/Copperbench/actions/runs/39704919193) passed; all six original source/hash/sample/XML receipts were checked. [Comparison](../../evidence/maintenance/2026-10-22/release-0.1.16/pr147-native-scale/comparison.json). |
| Windows source preflight | [Run 39712617227](https://github.com/Lotulune/Copperbench/actions/runs/39712617227) on `8df6e6430e9a6b4944b021a0808a5ea3d8975602`: JUnit XML reports 1,493 passed, 73 skipped, zero failures/errors; native diagnostics and accessibility executed. The later notice-only correction is separate; the final tagged release reran its full tests. |
| Final source | [Main CI](https://github.com/Lotulune/Copperbench/actions/runs/39723695133) and [documentation](https://github.com/Lotulune/Copperbench/actions/runs/39723704177) passed on `c87d2e0f0e924adc6ad31575aa5b06fe6da9dcf0`; main Chromium reported 498 passed. |
| Final Windows release | [Run 39726326742](https://github.com/Lotulune/Copperbench/actions/runs/39726326742) passed all release steps on `v0.1.16`. The Windows executables remain Authenticode unsigned. |
| Public assets | All 15 API asset digests match [SHA256SUMS.txt](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16/SHA256SUMS.txt). Ten public files, including EXE/ZIP, were downloaded and byte-hashed. Four update components match the downloaded 0.1.16 descriptor. |
| Public provenance and ZIP | Verified SLSA signature and exact source/tag/workflow/run for all ten downloaded files. ZIP inspection checked six source-bound entries, including the application version, legacy markers/template and complete UI notice. |

The original updated-base PR #147 Windows job had a native load timeout; its targeted retry passed. PR #148's first MCP job passed native UI but timed out preparing the external conformance CLI before protocol cases; its targeted retry passed all six applicable scenarios / eight checks. These failures remain recorded rather than being counted as a single clean run.

## Published files and source identity

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Windows EXE](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16/Copperbench.0.1.16.Windows.64bit.exe) | 503302189 | `6b67632a2fb89d33811993ad1bb75bbf8ee691b989d23cea998b188ad704ecb1` |
| [Portable ZIP](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16/Copperbench.0.1.16.Windows.64bit.zip) | 606486136 | `4c742427040e3b4b54846f4b214dd029837ef521b0b8eb1f9217913a795d9cba` |
| Product JAR inside the ZIP | 8536730 | `c10ccfef42dd4ac4dce85bdc1d2365c093b4332a9e8bc6405ea54262371f738f` |
| UI dependency license text inside the ZIP | 69123 | `e0bf667519d677902d6aa3ccde353a79e6bfad80f7bd1c410f98fafa2b1312b5` |

[RELEASE-METADATA.json](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16/RELEASE-METADATA.json) records the signed source and producer run. The ten downloaded files include the EXE, ZIP, checksum file, release metadata, update descriptor, SBOM, product-status and license/notice documents. MSIX and the component ZIPs were checked through their public API digests and checksum/descriptor bindings; they were not independently executed or locally installed.

Raw public API receipts, downloaded-byte verification, provenance and archive inspection are retained locally under `output/release-0.1.16/public/windows/`; large downloads are in `output/release-0.1.16/windows-public-payload/`. The official release assets and workflow links above are permanent retrieval points for the published identities.

## Installed and gameplay scope

The [local Windows + Codex candidate](simple-mode-final-package-2026-10-22.md) completed actual attachments, approval, stop, creation, Minecraft placement and same-world reopen, ordinary export, application restart and explicit continuation. Its [archived evidence](../../evidence/maintenance/2026-10-22/release-0.1.16/simple-mode-candidate/archived-files.json) retains its own candidate hashes. That record is not relabeled as a new full installed acceptance of the official rebuild.

Full official installer upgrade/uninstall, 360 scanning, cold-cache installation, independent novice usability, Linux player interaction and other CLI full workflows were not newly repeated. Linux publication is recorded [separately](linux-publication-0.1.16.md). Tagged source declarations and shipped documents retain their build-time text; later publication records do not alter released bytes.

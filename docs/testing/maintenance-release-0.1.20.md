# Windows 0.1.20 publication

[Windows v0.1.20](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.20) was published at **2026-11-11T07:03:25Z** from signed source `5e1ab97f0a10ebf2792e2622ad0d563f8b4ffc29`, the merge of the release preparation in [PR #176](https://github.com/Lotulune/Copperbench/pull/176). [Main regression](https://github.com/Lotulune/Copperbench/actions/runs/42973804084) passed on that source.

## Public package verification

The [Windows release workflow](https://github.com/Lotulune/Copperbench/actions/runs/42973844537) passed release tests/Javadoc, packaging, installer branding, SBOM, provenance, payload and draft checks before publication; its production deployment was approved on the repository owner's instruction. The [independent public verifier](https://github.com/Lotulune/Copperbench/actions/runs/42976906932) downloaded and hashed all 15 public assets, checked checksums/API digests and exact source/run provenance, and inspected packaged version, source documents, UI dependency license notices and template/class markers. Its 11 evidence files were re-hashed locally, and the 14 entries of `SHA256SUMS.txt` match the release API digests.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.20.Windows.64bit.exe](https://github.com/Lotulune/Copperbench/releases/download/v0.1.20/Copperbench.0.1.20.Windows.64bit.exe) | 503099129 | `5dd183e3cf9ee9d287aadee11f7d407feb59a64b278bfa0cb070f636951fead4` |
| [Copperbench.0.1.20.Windows.64bit.zip](https://github.com/Lotulune/Copperbench/releases/download/v0.1.20/Copperbench.0.1.20.Windows.64bit.zip) | 606253949 | `eafb1b5bbf974f7b13ea7e0ac874397d243011611b5fe131c14295a67cb2068a` |
| [Copperbench.0.1.20.Windows.64bit.msix](https://github.com/Lotulune/Copperbench/releases/download/v0.1.20/Copperbench.0.1.20.Windows.64bit.msix) | 626713502 | `6e33976df86d427106044ede2c7a187486a8ec701f41867502e0bd7a89297881` |

Installation, upgrade, user-file retention and removal results are recorded separately in [public-package acceptance](installed-release-0.1.20.md). The source checks below retain their source-candidate identity. Windows packages remain Authenticode unsigned; MSIX installation, UAC and full Windows 11 desktop/player acceptance are not inferred from cloud automation.

[Linux publication](linux-publication-0.1.20.md) and [current downloads](../releases/current.md) provide the other platform and public entry points. Tagged/shipped documentation retains its build-time preparation text; this record does not alter released bytes.

## Release preparation

The user requested merging the open pull requests and publishing new installation packages on 2026-11-11, including the Notebook interface refinements of [PR #173](https://github.com/Lotulune/Copperbench/pull/173). Three pull requests were merged into `main` in this order:

- [PR #175](https://github.com/Lotulune/Copperbench/pull/175) as `1a60a3edea56b8644de612214b99948d3fc866cf`: the MCP conformance script stops process trees with `Process.Kill($true)` instead of matching `Win32_Process.ParentProcessId`, which could select Windows session processes through a reused parent id and fail every later step of the job with `0xC0000142`.
- [PR #173](https://github.com/Lotulune/Copperbench/pull/173) as `89e0e962483b3f6859aff5127eaebb6f9ee82e86`: the new-workspace view, settings switches, narrower dialogs, restored controls in views embedded in simple creation, and composer option menus that open below when there is no room above.
- [PR #174](https://github.com/Lotulune/Copperbench/pull/174) as `9632b323a7fe9728984a5c8fc03920f14e13934f`: Fabric and NeoForge 26.2 generator files identical to the 26.1.2 baseline are inherited instead of copied, and the vendored Fabric generator source copy, unreferenced gates for closed stages, duplicated NeoForge preview fixtures and unreferenced `evidence/stage16` payloads were removed from the working tree.

Product identity, first-party release fixtures, About/help and the UI handshake advance to 0.1.20 together.

### Source verification

Each pull request was brought up to date with `main` before merging, and the required checks passed on the merged head: [PR #173 Build and test](https://github.com/Lotulune/Copperbench/actions/runs/42963331552) and [PR #174 Build and test](https://github.com/Lotulune/Copperbench/actions/runs/42966056970) (Java tests and Javadoc, UI contract, build and smoke tests, MCP conformance). The PR #174 [Agent readiness M1 gates](https://github.com/Lotulune/Copperbench/actions/runs/42966057121) passed. Its [Linux candidate smoke](https://github.com/Lotulune/Copperbench/actions/runs/42966057000) timed out after 420 s while the NeoForge 1.21.1 fixture bootstrapped and passed on re-run; the same job passed on the previous PR #174 head and on the `main` push of PR #173.

On the release-preparation source, 81 UI unit tests, 50 UI-Core contracts and `verify-product-status` passed locally. Before merging, PR #174 passed its generator, release and acceptance-diagnostics Java tests (72 passed, 3 skipped), the script tests and the five preview Playwright specs on Chromium (23). PR #173 reports its own browser-preview runs; they are not repeated here. The [retained source summary](../../evidence/maintenance/2026-11-11/release-0.1.20/source-verification.json) records these counts and CI runs.

A native JCEF window was launched from the combined source with an isolated `COPPERBENCH_HOME`. The 0.1.20 launcher, the first-run download-mirror prompt and tour, and the Swing new-workspace dialog appeared and closed normally. The refined product-shell views open only inside a workspace, and no workspace was created, so the switches, dialogs and embedded views of PR #173 were checked in browser previews only.

### Package and publication boundary

This section records the source state before packaging; the publication sections above identify the actual bytes.

This release changes interface styling, layout and copy, and the layout of generator templates; workspace formats and Minecraft gameplay code are unchanged. Generator output for 26.2 was compared through template resolution in source tests, not by compiling and running a 26.2 mod. Full installer upgrades, Linux GNOME/Wayland certification and player-controlled gameplay were not repeated. Linux retains `formalSupportClaim=false`; historical support records keep their original scope. Windows packages remain Authenticode unsigned.

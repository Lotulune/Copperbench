# Windows and Linux 0.1.20 release preparation

The user requested merging the open pull requests and publishing new installation packages on 2026-11-11, including the Notebook interface refinements of [PR #173](https://github.com/Lotulune/Copperbench/pull/173). Three pull requests were merged into `main` in this order:

- [PR #175](https://github.com/Lotulune/Copperbench/pull/175) as `1a60a3edea56b8644de612214b99948d3fc866cf`: the MCP conformance script stops process trees with `Process.Kill($true)` instead of matching `Win32_Process.ParentProcessId`, which could select Windows session processes through a reused parent id and fail every later step of the job with `0xC0000142`.
- [PR #173](https://github.com/Lotulune/Copperbench/pull/173) as `89e0e962483b3f6859aff5127eaebb6f9ee82e86`: the new-workspace view, settings switches, narrower dialogs, restored controls in views embedded in simple creation, and composer option menus that open below when there is no room above.
- [PR #174](https://github.com/Lotulune/Copperbench/pull/174) as `9632b323a7fe9728984a5c8fc03920f14e13934f`: Fabric and NeoForge 26.2 generator files identical to the 26.1.2 baseline are inherited instead of copied, and the vendored Fabric generator source copy, unreferenced gates for closed stages, duplicated NeoForge preview fixtures and unreferenced `evidence/stage16` payloads were removed from the working tree.

Product identity, first-party release fixtures, About/help and the UI handshake advance to 0.1.20 together.

## Source verification

Each pull request was brought up to date with `main` before merging, and the required checks passed on the merged head: [PR #173 Build and test](https://github.com/Lotulune/Copperbench/actions/runs/42963331552) and [PR #174 Build and test](https://github.com/Lotulune/Copperbench/actions/runs/42966056970) (Java tests and Javadoc, UI contract, build and smoke tests, MCP conformance). The PR #174 [Agent readiness M1 gates](https://github.com/Lotulune/Copperbench/actions/runs/42966057121) passed. Its [Linux candidate smoke](https://github.com/Lotulune/Copperbench/actions/runs/42966057000) timed out after 420 s while the NeoForge 1.21.1 fixture bootstrapped and was re-run; the same job passed on the previous PR #174 head and on the `main` push of PR #173.

On the release-preparation source, 81 UI unit tests, 50 UI-Core contracts and `verify-product-status` passed locally. Before merging, PR #174 passed its generator, release and acceptance-diagnostics Java tests (72 passed, 3 skipped), the script tests and the five preview Playwright specs on Chromium (23). PR #173 reports its own browser-preview runs; they are not repeated here. The [retained source summary](../../evidence/maintenance/2026-11-11/release-0.1.20/source-verification.json) records these counts and CI runs.

A native JCEF window was launched from the combined source with an isolated `COPPERBENCH_HOME`. The 0.1.20 launcher, the first-run download-mirror prompt and tour, and the Swing new-workspace dialog appeared and closed normally. The refined product-shell views open only inside a workspace, and no workspace was created, so the switches, dialogs and embedded views of PR #173 were checked in browser previews only.

## Package and publication boundary

Official 0.1.20 packages are not published at this source snapshot. The signed Windows latest-main build and the frozen Linux producer/consumer/promotion must pass before [current downloads](../releases/current.md) advances from 0.1.19. Package metadata, checksums, provenance and post-publication receipts will identify the actual bytes; source verification does not establish installer acceptance.

This release changes interface styling, layout and copy, and the layout of generator templates; workspace formats and Minecraft gameplay code are unchanged. Generator output for 26.2 was compared through template resolution in source tests, not by compiling and running a 26.2 mod. Full installer upgrades, Linux GNOME/Wayland certification and player-controlled gameplay were not repeated. Linux retains `formalSupportClaim=false`; historical support records keep their original scope. Windows packages remain Authenticode unsigned.

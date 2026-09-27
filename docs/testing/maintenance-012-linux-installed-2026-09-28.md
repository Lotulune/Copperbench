# Copperbench 0.1.2 Linux installed acceptance

The new 0.1.2 Linux candidate passed all ten installed release gates and both legacy-preferences migration cases. Acceptance completed on 2026-09-28 in the maintainer's Asia/Tokyo timezone (2026-09-27 UTC). Publication remains a separate signed-tag and production-approved workflow step.

## Exact candidate

- Source: `9bc6d104688c24512468880abdc2a96828fcaab8`, merged maintenance PR [#83](https://github.com/Lotulune/Copperbench/pull/83).
- Required main CI: [36321809512](https://github.com/Lotulune/Copperbench/actions/runs/36321809512), passed. Candidate build: [36321809542](https://github.com/Lotulune/Copperbench/actions/runs/36321809542), passed.
- Candidate ID: `sha256:fb28520326b33dfe6dd51cfb7a801d5fb936a43cdafc088ca84bfe155abecb48`.
- Debian package: 888622228 bytes; SHA-256 `fd43bd23926a2f37042f14d6a65a7d606b59b08035a92d490ff5ded156931aef`.
- Portable archive: 968869856 bytes; SHA-256 `14576bfafd24cf773b689fecb448aa7dd2f78cf05fd80e79c25eefe874e408fa`.
- Installed and portable product JAR: `042b68b13fb1eede795b2e506b3ef5f7b4b5d1a15c99aededb01b93d13af6ce7`.

All six frozen assets passed GitHub provenance verification against this source and the candidate workflow. The original candidate metadata retains its development classification. The separate [release authorization](../../release-control/linux-candidate-authorization.json) binds this report and the accepted results by hash; release promotion must preserve these tested binary bytes.

## Accepted results

The [evidence directory](../../evidence/stage15/2026-09-28/maintenance-012-9bc6/) contains distinct original result files, supporting build/probe records, inspected screenshots, provenance, control receipts and a checksum inventory.

| Gate | Result | Evidence subdirectory |
| --- | --- | --- |
| Wayland Fabric 1.21.1 installed build/render | Passed | `waylandFabric` |
| Wayland NeoForge 1.21.1 installed build/render | Passed | `waylandNeoForge` |
| Xorg Fabric 1.21.1 installed build/render | Passed | `xorgFabric` |
| Xorg NeoForge 1.21.1 installed build/render | Passed | `xorgNeoForge` |
| Wayland external Agent and normal closes | Passed | `waylandAgent` |
| Xorg external Agent and normal closes | Passed | `xorgAgent` |
| Managed Blockbench edit/save/close | Passed | `managedBlockbench` |
| Installed Asset Center opens real Blockbench | Passed | `assetCenter` |
| UI create/save/close/recent-list reopen | Passed | `uiPersistence` |
| Portable build outside its installation directory | Passed | `portableBuild` |
| Modern and old legacy-preferences migration | Both passed | `preferences-migration` |

The dedicated Ubuntu 24.04 x86_64 GNOME guest had no system Java, Gradle or Git. Installed preflights checked actual session type, native JCEF, private MCP descriptor permissions, real builds and fresh client rendering/stability. Their automated process cleanup is not normal-close evidence. `dpkg --verify` was clean after acceptance, the installed JAR remained unchanged, and the four protected original fixture files were unchanged after installation. All mutations used fresh disposable copies or the new UI-created workspace.

The installed UI created `maintenance_012_ui` on Fabric 1.21.1, created function `maintenance_persistence`, and saved `say Maintenance 0.1.2 persistence` with a trailing newline. The application closed normally and removed its MCP descriptor. A fresh launch selected the workspace from the native recent list and displayed the same saved function. Saved, closed and reopened definition hashes all equal `f865ecad2da865ca33a4385c32be4a73036624b0370fb153c96331b53222d266`; both product closes exited zero.

Accepted independent Agent runs advanced revisions 5→8 on Wayland and 8→11 on Xorg. Each exercised initialize/read/list, mutation, Workspace Plan preview/apply, two real builds, deliberate stale-revision rejection, committed retry and final readback. Each launched Minecraft through the installed Desktop MCP, verified fresh render markers and ten seconds of continued running, then observed a successful task after normal title-menu exit. Normal Copperbench closure removed the descriptor and rejected the old connection. Both verifier and product processes exited zero. One-time UI credentials were copied without capturing the revealed token, passed only through private stdin, and cleared from the clipboard; credential persistence and audit-leak checks passed.

The host Minecraft bridge reported a Windows-only backend. The user explicitly authorized the Wayland/Xorg title-menu exit exception through Hyper-V. New screenshots had to match planner-inspected title/menu/button pixels before each bounded click; animation regions were excluded from matching. Unexpected markers stopped input. The lead reviewed the retained game and terminal screenshots together with the unchanged verifier's results. No world was entered or modified, no parallel Minecraft controller was attached, and Jev was not used. The initial timed-out Wayland run was normally closed for cleanup and then fully replayed. Input receipts are separate from the acceptance results and do not establish an efficiency claim.

Managed Blockbench displayed the new probe model, accepted a visible cuboid mesh edit and save, rejected a competing asset lease, detected the changed hash with `ASSET_CHANGED_EXTERNALLY`, and exited zero. The separate Asset Center button opened that saved model in `/usr/bin/blockbench` PID 222196, directly parented by installed Copperbench PID 221639. Live executable/arguments, parent PID and unchanged installed JAR were checked; no product classpath override was used. Both applications closed normally.

The original Wayland desktop was restored after all gates; no Copperbench, Minecraft or Blockbench process remained from acceptance. Historical 0.1.1 authorization/support files are archived byte-for-byte in the evidence `history` directory.

## Retained failures and limits

The [retained-attempt record](../../evidence/stage15/2026-09-28/maintenance-012-9bc6/failures/retained-attempts.json) separates unsuccessful preparation and retries from accepted results. These include a locked-keyring graphical timeout, an unchanged first Blockbench probe caused by closing before confirming Add Mesh, a Wayland Agent normal-close timeout while the control exception was pending, and a misnamed Xorg attempt whose actual session was Wayland. Original success conditions and timeouts were not relaxed. A finalizer's old fixture filename was corrected to the already saved new function; runtime save/reopen evidence was unchanged. Authentication used the existing local credential; passwords, keyrings and system policies were not reset.

Builds used warmed caches. This is not cold-cache, default-network or offline certification, nor additional gameplay acceptance. Linux scope remains Ubuntu 24.04 LTS x86_64 GNOME Wayland/Xorg with installed Fabric and NeoForge 1.21.1 runtime coverage. Other distributions, architectures and additional Linux generator tracks are not certified by this run. Blockbench remains an external installation. External-user trials and Agent efficiency comparisons remain deferred.

The Windows native JCEF import→bind→refresh regression and all source-level Java, UI, contract and MCP checks are separately recorded in [the maintenance record](maintenance-2026-09-27.md). Only documentation, evidence and release declarations follow the frozen Linux candidate; no product, protocol, storage, build or packaging behavior changes are included in its promotion delta.

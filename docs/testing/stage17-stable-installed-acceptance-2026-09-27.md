# Stage17 stable installed acceptance — 2026-09-27

The rebuilt 0.1.1 Linux candidate passes all ten required installed gates and both legacy-preferences migration cases. This closes the installed function-save blocker from PR #80. Public publication remains a separate signed-tag, production-approved workflow step.

## Exact binaries

- Candidate source: `b813e6cf19cbc0af43b645608e0d7dc5d7e0f243`; [candidate run 36253171862](https://github.com/Lotulune/Copperbench/actions/runs/36253171862).
- Candidate identity: `sha256:ceb87d913c04e1ee3a2fc44c902b56acd96301c9b75351d94678b589e794aae3`.
- Debian: `8ce8acf5af8fe74623201fc10e278042bfd755d4f060c30f6409250296b97744`, 888617650 bytes.
- Portable: `d1feb9803ba5ebc1f57134d23bf870aaad1a8e56844a9c5908bd738a5d34f5d1`, 968868584 bytes.
- Installed and portable JAR: `b96e68dbc697230ca98d82339edbd5cb4c3b12ee7124b861053208d06e270bb8`.

All six original assets passed GitHub provenance verification against the exact source and candidate workflow. The immutable candidate metadata retains its original development classification. The separate [release authorization](../../release-control/linux-candidate-authorization.json) binds the accepted result files and this report by SHA-256; promotion must preserve the tested bytes.

## Results

Evidence is under [stable-011-b813](../../evidence/stage15/2026-09-27/stable-011-b813/). Each gate has its own `result.json`.

| Gate | Result | Evidence directory |
| --- | --- | --- |
| Xorg Fabric 1.21.1 installed build/render | Passed | `xorgFabric` |
| Xorg NeoForge 1.21.1 installed build/render | Passed | `xorgNeoForge` |
| Wayland Fabric 1.21.1 installed build/render | Passed | `waylandFabric` |
| Wayland NeoForge 1.21.1 installed build/render | Passed | `waylandNeoForge` |
| Xorg external Agent and normal closes | Passed | `xorgAgent` |
| Wayland external Agent and normal closes | Passed | `waylandAgent` |
| Managed Blockbench edit/save/close | Passed | `managedBlockbench` |
| Installed Asset Center opens real Blockbench | Passed | `assetCenter` |
| UI create/save/close/recent-list reopen | Passed | `uiPersistence` |
| Portable build outside installation directory | Passed | `portableBuild` |
| Old and modern preference migration | Both passed | `preferences-migration` |

The dedicated Ubuntu 24.04 x86_64 GNOME guest had no system Java, Gradle or Git. Preflights verified the real session type, installed JCEF, build success, fresh Minecraft render logs and stability. Their automated cleanup is not normal-close evidence. Package verification was clean, installed/portable JARs matched, and the four protected original fixture files remained unchanged.

The installed UI created `stable_011_ui_fixed`, saved function `stable_persistence` as `say Stage17 stable 0.1.1 persistence`, closed normally, selected the workspace from the recent list and reopened the saved function. Saved/reopened definition SHA-256 is `e9398cd4e4c796fddb0df3db8b14d9d4a29eb00de82940a2ea1bc21116c34f7d`. Both product closes exited zero and removed the MCP descriptor. This directly verifies the corrected projection-based function save without submitting unsupported tags.

Independent Agent runs used that UI-created Fabric workspace at revisions 2→5 (Wayland) and 5→8 (Xorg). Each passed plan/preview/apply, two builds, deliberate stale-revision rejection and committed retry, real client rendering for at least ten seconds, title-menu Quit Game, and normal product close. Both task terminal states were `succeeded`; descriptor removal and old-connection rejection passed. Both verifier and product processes exited zero. One-time UI credentials passed through clipboard and private stdin; clipboard was cleared, no credential was persisted, and the automation audit did not contain the token.

The host Minecraft bridge reports a Windows backend and cannot control the guest Wayland desktop. The user explicitly authorized a bounded Hyper-V exception for these two observed title-menu exits. No world was opened or modified, no parallel Minecraft input controller was attached, and each action used a freshly inspected screenshot. The screenshots and helper receipts record actual runtime outcomes rather than treating an input receipt as acceptance. Jev was not used. The original Wayland desktop was restored afterward.

Managed Blockbench opened the probe model, rejected a competing lease, detected an externally saved mesh and exited zero. The separate Asset Center action launched `/usr/bin/blockbench` PID 177385 directly from installed Copperbench PID 177134 with the selected model path. Live process parent/arguments and installed JAR hash were checked; no product classpath override was used. Reviewed screenshots show the saved mesh. Both applications closed normally.

## Retained failures and scope

The earlier pre-fix candidate and its save rejection remain historical evidence and are not promoted. A locked GNOME keyring caused initial graphical preflight timeouts; normal unlock with the existing local credential resolved them, and fresh evidence directories preserve the successful retries. The separately authorized account-password recovery retained a root-only rollback record; no keyring password or policy was reset.

An initial Agent run against an imported fixture was rejected with `WORKSPACE_PLAN_SOURCE_CONFLICT`: its existing `resonance_forge.json` was outside Copperbench ownership. The [diagnostic](../../evidence/stage15/2026-09-27/stable-011-b813/failures/imported-source-conflict.json) is preserved. The source guard was not weakened and imported resources were not deleted; final Agent acceptance used the independently UI-created workspace instead. The original verifier was unchanged.

Builds used warmed caches; this is not cold-cache, default-network or offline certification. Scope remains Ubuntu 24.04 LTS x86_64 GNOME Wayland/Xorg, with installed Fabric and NeoForge 1.21.1 runtime coverage. Other distributions/architectures and additional Linux tracks are not certified. Blockbench remains external. Existing Windows certification exclusions remain explicit in the release notes.

Only evidence, release declarations and documentation follow the frozen candidate. Product code, packaged resources, release signatures, production approval and asset/provenance checks remain enforced. Stable publication must use signed latest-main tags after required CI passes.

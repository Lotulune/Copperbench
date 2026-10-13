# P263-36 Ubuntu GNOME Wayland Fabric client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for this G1 preview cell. Player/persistence and supplementary focus evidence were produced by separate ordinary installed Native runs of the same frozen candidate and mod artifact.

| Binding | Value |
| --- | --- |
| Candidate | `sha256:d7baeec4304b79ed0900395a9a60be95f76e390a950dad61d1d4b090dc41e6cf` |
| Mod JAR SHA-256 | `85efba8002bf9ebf16a109a4b955a3a3bc34d43ddb103649761d01af442723d8` |
| Primary installation receipt SHA-256 | `afd59bcf7311c4a617314ffbe23a297ca6bd91bc7ee46c3372e595804ad25e94` |
| Supplement installation receipt SHA-256 | `b50e3208f83361bca234b4fe3443a71e766fa17602a06b27e4c00f71e5d37c2c` |
| Client receipt SHA-256 | `3ae788456388983e97cb22df9a166f2ed16b7fd3929f4c4e44b546ccd49ce146` |
| World | `P263-fabric-wayland-38378587219` |
| Player / reopened / focus-only PIDs | `11498` / `14314` / `11268` |

The [primary run](https://github.com/Lotulune/Copperbench/actions/runs/38378587219) used real Ubuntu 24.04 GNOME and a native Wayland Minecraft window. Actual text entry, keyboard movement, mouse look, a manual full-stack combine attempt retaining 16+1, stick-over-string crafting and taking two outputs, and right-click Camp Post placement all passed. Materials, time and difficulty commands were preparation. Both clients exited normally with succeeded Native tasks and host exit code zero.

The second process reentered the exact world. Screenshots retained the placed block and all four inventory entries. Read-only NBT comparison passed world identity, position, rotation, inventory, selected slot, game mode and blocks. The final inventory is Trail Kit 16, 1 and 2 plus one Camp Post, placed at `(-4, -60, 5)`. Fabric development UUID/appearance changed; these seven persisted-state checks remained equal. Original source-input assertions and the mod hash remained intact.

The [supplementary focus run](https://github.com/Lotulune/Copperbench/actions/runs/38422771055) separately verified the actual native Wayland Minecraft window. GNOME overview changed focused true to false; the following bounded movement was rejected with `FOCUS_LOST` before input. Held keys/buttons were empty. The session was detached, explicitly reattached for normal title-screen exit, and finished as `supplementary_focus_only`. It supplies no new gameplay or persistence claim. Its fresh installation receipt was independently evaluated against the same complete frozen candidate before combining evidence.

The task-local bounded engine was used under the user's direct-operation authorization, not represented as a registered MCP connection. Bridge version 0.2.0 retained its 120-second frame, 2-second/32-action and foreground limits. The main run recorded 63 logical requests, 50 hash-verified bridge files, 51.717 seconds of input/waits, 22.580 seconds of capture, 4,520.994 seconds lifecycle time, and two rejected stale frames. The focus supplement recorded 15 requests, 11 hash-verified bridge files, 1.632 seconds input/waits, 2.002 seconds capture, 2,078.354 seconds lifecycle time, and one focus rejection. Jev calls: zero. Transport polling is outside these logical request counts; no speedup is inferred.

Evidence roots under `output/p263-remaining/`:

- `cloud/fabric-g1-wayland-player-run/`: original player run, installation evidence and both world snapshots.
- `cloud/fabric-g1-wayland-focus-run/`: original focus supplement, its installation evidence and desktop diagnostics.
- `wayland-fabric-g1-client-cell/`: 87 hashed evidence entries, both world archives, `cell.json` and the passing client cell in `evaluation.json`.

The candidate evaluator rehashed the complete frozen artifact and accepted the exact installation/client linkage. Other missing candidate cells remain `not_run` in this selected evaluation. Visible sample texture/model/localization gaps are outside this behavior pass. No Xorg, G2 or formal release claim follows from this result.

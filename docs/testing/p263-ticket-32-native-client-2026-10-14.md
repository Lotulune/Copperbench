# P263-32 Windows Fabric ordinary Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for this G1 preview cell. The installed Native Python SDK invoked ordinary `run_client` with preview opt-in and no task grant. This closes the outstanding new-candidate condition in the [earlier implementation qualification](p263-ticket-32-fabric-client-qualification-2026-10-13.md).

| Binding | Value |
| --- | --- |
| Candidate | `sha256:16b82f6aeaf0bf8af5784a5444e83353d6bb0f2ef3a0a402d22e0614f357a193` |
| Mod JAR SHA-256 | `85efba8002bf9ebf16a109a4b955a3a3bc34d43ddb103649761d01af442723d8` |
| Installation receipt SHA-256 | `41adcbc0261d3f675e9aa61c2b37f1e1fdc9c2f54f765001ef09ad10a9a39e36` |
| Client receipt SHA-256 | `c18ee693933c96ee10c05aca0678fc729515fc57ffe9a10698249cc65d5f73b4` |
| World | `P263-fabric-windows-38377909493` |
| Selected player / reopen PIDs | `2556` / `21068` |
| Host exit codes | `0` / `0` |

The player entered the exact world name, moved and turned, attempted to add one Trail Kit to a full sixteen-item stack, and retained 16+1. Manual stick-over-string crafting produced two outputs; taking them consumed both ingredients. A real right click placed one Camp Post and reduced its held count from two to one. Give and difficulty commands were preparation only. Alt+Tab caused actual focus loss and interrupted the batch; a subsequent movement request was rejected with `FOCUS_LOST` before input. Control was detached before Computer Use restored the exact window.

Normal Save and Quit to Title and Quit Game were corroborated by client logs, process exit and succeeded Native task states. An interruption occurred before the originally planned second client entered the world; that process exited from the title and is not credited as persistence evidence. A separately bound `supplementary_reopen_only` run later entered the same saved world. Its actual screenshots retained the placed block and all four inventory entries. Both original runs and the supplement remain separate; no failed evidence was rewritten.

Read-only NBT comparison matched world identity, player position, rotation, inventory, selected slot, game mode and blocks. The final inventory is Trail Kit 16, 1 and 2, plus one Camp Post. The placed Camp Post is at `(-9, -60, 3)`. Mod source inventories before and after both selected runs were identical. Development launch UUID/appearance changed; singleplayer persisted state remained the same. Missing sample models/textures and localization remain visible and are not accepted as visual quality.

The direct Windows bridge reported 0.3.0 and retained its 120-second frame, 2-second batch and focus restrictions. Primary / supplementary lifecycle times were 998.691 / 311.222 seconds; these exclude the interruption gap and include the unused title-only launch in the primary lifecycle. The recorded queues contain 38 / 15 requests, with startup doctor/list calls recorded separately. Input plus declared waits were 21.866 / 6.584 seconds; capture totals were 1.167 / 0.378 seconds. One primary batch was rejected before input. Jev calls: zero. Computer Use focus handoffs are separate from bridge timing. No speedup is inferred.

Original evidence roots under `output/p263-remaining/`:

- `windows-g1-fabric-installed-direct/`: cold/warm Native, business, negative and portable gates.
- `windows-g1-fabric-native-client-attempt-2/`: reviewed player actions, focus rejection and first saved world.
- `windows-g1-fabric-native-reopen-20261014/`: actual new-process reentry, normal exit and serialized comparison.
- `windows-fabric-g1-client-cell/`: 65 hashed evidence entries, both world snapshots, `cell.json` and the passing candidate `evaluation.json`.

The first screenshot-only reopen verification was retained as `unverified`; a second assessment included the original action log and was recorded `passed`. Bridge manifests verified 31 primary and 10 supplementary files. The candidate evaluator independently accepted the exact installation/client linkage. This is one Windows Fabric G1 preview cell; it does not qualify G2, another loader, another desktop, or publish formal support.

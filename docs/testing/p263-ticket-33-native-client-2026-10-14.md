# P263-33 Windows NeoForge ordinary Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for this G1 preview cell. The installed Native Python SDK invoked ordinary `run_client` twice, with preview opt-in and no task grant.

| Binding | Value |
| --- | --- |
| Candidate | `sha256:16b82f6aeaf0bf8af5784a5444e83353d6bb0f2ef3a0a402d22e0614f357a193` |
| Mod JAR SHA-256 | `59cb2a585035747188778325006a2fb91b1459e95da9cff64a74bcf4c6cd5e42` |
| Installation receipt SHA-256 | `535e4b8fd4828aba9f7489e9a58b881f5fc1ee10e73bfb246cda00a83bd3eb6e` |
| Client receipt SHA-256 | `5229eb397af527a0f144b5961a6a8dbd5447cfe7d4fd7e6409f4a7084a80c74b` |
| World | `P263-neo-win-20261014-G1` |
| Player / reopened PIDs | `31380` / `41956` |

The reviewed Windows 11 client accepted the exact world-name text, keyboard movement and mouse look. A manual attempt to combine one Trail Kit with a full sixteen-item stack retained 16+1. Placing a stick above string in the player crafting grid produced two Trail Kits; taking the output consumed both ingredients. A real right click placed one Camp Post and reduced its count from two to one. Give commands and peaceful world creation were preparation only.

Actual Alt+Tab focus loss interrupted input. A subsequent movement request was rejected with `FOCUS_LOST` before execution. The input session was detached before Computer Use recovered the exact test window; foreground state was confirmed before reattaching. Both normal Save and Quit / Quit Game sequences ended in succeeded Native tasks. Closing-window observation errors are corroborated by task terminal states and client logs; they are not counted as focus-loss evidence.

The second process entered the reviewed exact saved world. Screenshots showed the same placed Camp Post and inventory. Read-only NBT comparison passed all seven checks: world identity, player position, rotation, inventory, selected slot, game mode and blocks. Player UUID also remained unchanged. Final inventory: Trail Kit 16, 1 and 2, and one Camp Post. The placed Camp Post is at `(-6, -60, 12)`. Source inventories before/after were identical; the accepted JAR hash stayed unchanged. Host exit code was zero.

The first process identity is recorded in the original bridge session (PID, process-start identity, window and game directory), alongside the Native lifecycle binding. The second additionally has a Win32 process command-line receipt. The bridge's empty `game_dir/mods` inventory is not a claim about the complete development classpath.

The bridge reported 0.3.0 and kept the 120-second frame and 2-second/32-action limits. Lifecycle: 1,748.643 seconds; 43 queued requests; 35 bridge files hash-verified; input plus waits: 25.199 seconds; capture: 1.094 seconds; one rejected batch; Jev calls: zero. Computer Use focus handoffs are separate. No speedup is inferred.

Evidence roots under `output/p263-remaining/`:

- `windows-g1-neoforge-installed-proxy/`: passing cold/warm installed Native and portable gates. The task-local JVM proxy addressed the previously recorded repository connection timeout; TLS verification stayed enabled.
- `windows-g1-neoforge-native-restarted-20261014/`: two normal client runs, original screenshots/actions, source manifests and both saved-world snapshots.
- `windows-neoforge-g1-client-cell/`: 53 hashed evidence entries, world archives, `cell.json` and a passing cell in `evaluation.json`.
- `windows-g1-neoforge-native-client-20261014/`: prior title-only attempt that exhausted its lifecycle budget. It is preserved as failed and supplies no acceptance pass.

The exact frozen candidate evaluator accepted the installation/client link. Missing sample textures/models and some localization remain visible and are not accepted as visual quality. This result qualifies only the Windows NeoForge G1 preview cell; G2 and other desktops remain separate.

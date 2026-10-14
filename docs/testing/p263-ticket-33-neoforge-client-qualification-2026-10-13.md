# P263-33 neoforge Windows client implementation qualification

Date: 2026-10-13. Result: actual frozen-library lifecycle and player persistence passed; ticket 33 remains in progress pending an ordinary Native launch on a newly frozen candidate.

The external harness invoked the installed `MCreatorWorkspaceSession` and `LoaderRoutingWorkspaceTaskGateway` while keeping the host session alive. This is implementation qualification, not a claim that the historical package's Native preview API accepted `run_client`. That API was blocked by `PREVIEW_NATIVE_OPERATION_UNVERIFIED`. The current change adds this proven operation to the capability catalog, keeps support stage `registered`, and retains preview opt-in, workspace permission and validation of any supplied task authority. The established run_client policy does not require a separate grant when no grant is supplied.

| Binding | Actual value |
| --- | --- |
| Candidate | `sha256:a90e1fd9e1381ddec64cecb5f75eeadf101a0adc33ecb5f76f1311722783fb4a` |
| Mod JAR SHA-256 | `59cb2a585035747188778325006a2fb91b1459e95da9cff64a74bcf4c6cd5e42` |
| World | `P263-NeoForge-G1-33-20261013` |
| Client PIDs | `[9580, 9916]` |
| Host exit | 0 |
| Two lifecycle tasks | succeeded |
| Original JAR | unchanged |

Player actions covered exact-name text entry, inventory interaction at the 16+1 stack boundary, manual stick-over-string crafting and removal of two outputs, keyboard movement, relative mouse look, selection and actual Camp Post placement. `/give` was preparation, not player acceptance. Focus loss interrupted input, followed by a sequential Computer Use focus handoff. Both runs used normal Save and Quit and Quit Game. The new process reopened the exact world. The client close's `WINDOW_NOT_VISIBLE` receipt is corroborated by process exit, client logs and succeeded lifecycle tasks.

Read-only NBT inspection independently matched world identity, player position/rotation, inventory, selected slot, game type and placed blocks. The inventory contains Trail Kit stacks 16, 1, 2 and one remaining Camp Post. Sample textures/localization are incomplete and are not accepted as visual quality. Fabric development UUID changes between launches are separate from the verified persisted player state.

The direct Python bridge reported version 0.3.0. No registered MCP availability or Jev use is claimed. Lifecycle wall time was 2232.313 s; bridge input 21.478 s, capture 1.623 s, 60 bridge calls, 0 rejected batches; Jev calls 0. These measures have different scopes and do not establish a speedup. Computer Use event logs are retained separately.

Evidence: review (`output/minecraft-validation/p263-33-lifecycle-probe-1/qualification-review.json`), hashed inventory (`output/minecraft-validation/p263-33-lifecycle-probe-1/qualification-files.json`), NBT comparison (`output/minecraft-validation/p263-33-lifecycle-probe-1/world-comparison.json`), metrics (`output/minecraft-validation/p263-33-lifecycle-probe-1/metrics.json`), harness result (`output/minecraft-validation/p263-33-lifecycle-probe-1/host-result.json`).

The final bridge verification receipt was downgraded because the caller used an absolute action-log reference. It is preserved unchanged; the separate review above links the original hashed action/screenshot evidence and NBT comparison.

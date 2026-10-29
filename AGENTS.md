# Project agent instructions

## Language and evidence

- Reply to the user in Chinese. Use English for commands and tool arguments when practical.
- Inspect the relevant source before changing behavior. Preserve unrelated working-tree changes.
- Distinguish observed runtime facts, tool receipts and model inferences. Report verification limits explicitly.

## Minecraft runtime validation

- For gameplay, controls, inventory, block interactions, persistence or in-game UI changes, perform relevant runtime validation with the computer-use skill when a test client is available. Compilation alone does not prove gameplay acceptance.
- Launch the intended test client through the existing Copperbench lifecycle/SDK and keep its workspace session alive until the client exits. Confirm the target process, game directory and uniquely named test world; do not select the first Java window blindly.
- Use one desktop input controller and follow the computer-use skill's observation and action contracts. Inspect the current screenshot before choosing an action and refresh the observation after each action. Stop on an ambiguous target, unexpected dialog or lost focus; do not repeatedly steal focus.
- Use an isolated acceptance workspace and test world. Never delete or overwrite existing user worlds. Separate preparation commands from actual player interactions in the evidence.
- Verify the relevant player-visible behavior and, for persistence changes, save and reopen the same test world. Confirm normal process exit, game logs and the Copperbench lifecycle task's terminal state before declaring a successful shutdown.
- Store screenshots, action records, logs and a passed/failed/unverified summary under `output/minecraft-validation`. Bind evidence to the tested source and artifact hashes. Input success or an automated test receipt alone is not gameplay acceptance.
- The Minecraft control MCP registration has been removed; do not recreate it or start its bridge worker for acceptance. Keep secrets out of prompts, tool arguments and committed evidence.

# Project agent instructions

## Repository map and validation

- Setup and contribution flow: [CONTRIBUTING.md](CONTRIBUTING.md); current downloads: [docs/releases/current.md](docs/releases/current.md).
- Java Core, adapters and MCP: `src/main/java/dev/copperbench/`; tests mirror these packages in `src/test/java/dev/copperbench/`. Run the relevant Gradle test classes; public Java API changes also need `javadoc`.
- UI: `ui-shell/src/` and `ui-shell/e2e/`; use `npm test --prefix ui-shell`, the UI build and affected Playwright cases. Shared contracts live in `ui-core/schemas/v1.0/`; validate them with `npm test --prefix ui-core`.
- Python SDK: `sdk/python/`; run `python -m unittest discover -s sdk/python -p 'test_*.py'`. Native/Core operation names come from the UI-Core schemas; MCP tool names come from `McpToolCatalog` and the running server's `tools/list`.
- TypeScript SDK: `sdk/typescript/`; after `npm ci --prefix ui-shell`, run `npm run test:sdk --prefix ui-shell` using the UI shell's TypeScript development dependency.
- Documentation: run `node scripts/verify-markdown-links.mjs`. The verification table in `CONTRIBUTING.md` gives commands by changed layer; source checks do not replace installed-product or gameplay evidence.

## Product media

- For promotional videos, covers and product screenshots, use the versioned images listed in [current media and capture details](assets/screenshots/README.md), plus the [Copperbench brand source](assets/branding/README.md).
- `evidence/` and historical PRD, handoff and design records describe dated candidates. Do not use their screenshots or prototype visuals as the current interface. Keep browser preview captures distinct from native desktop and Minecraft gameplay footage.

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

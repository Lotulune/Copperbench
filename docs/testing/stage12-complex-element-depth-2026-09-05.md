# Stage 12 complex element depth closure evidence — 2026-09-05

Status: implementation gate passed on `codex/stage12a-depth`. This document records source/test evidence; it is not evidence that the branch has been merged or released.

## Scope

Stage 12 closes the structured-editing depth work for all three waves defined by `PRD-NEXT.md`:

- 12A: `livingentity`, `biome`, `dimension`, `gui`;
- 12B: `projectile`, `specialentity`, `overlay`, `feature`, `structure`, `fluid`, `plant`;
- 12C: the remaining first-party equipment/combat/system/tail Java Mod Element types.

The editor projection now derives numeric limits, limited options, element/resource references, conditional fields, and structured collection item schemas from the shared Java model. Villager Trade is covered by a real structured-list edit/save/reopen path rather than raw JSON editing.

## Definition-of-Done evidence

| Stage 12 DoD | Evidence |
| --- | --- |
| Structured UI instead of raw JSON for high-frequency types | Stage 12 command E2E covers typed Projectile references/numeric bounds and structured Villager Trade rows; the full UI matrix passes. |
| UI/MCP/headless share schema, validation and persistence semantics | The editor projection remains produced by `WorkspaceApplicationService`; the UI consumes the contract rather than defining a second element schema. Full Java and Playwright regressions pass. |
| Open → edit → save → reopen preserves unedited/unknown fields | `WorkspacePersistenceCompatibilityTest.copperbenchSavePreservesUnknownFieldsForEveryFirstPartyType` now performs `reloadFromFileSystem()` after save and verifies both in-memory unknown fields and opaque disk fields for the full first-party slice. |
| Real supported generator/type fixtures generate and build | `NewWorkspaceGeneratorGoldenBuildTest` edits 18 representative Stage 12 types and successfully builds Fabric/NeoForge 26.2, 26.1.2, 1.21.1 and 1.20.1: 8/8 tracks. |
| Field-level diagnostics are locatable | Core diagnostics carry exact element/path plus an `open_field` action; the validation scenario clicks that action and focuses `/fields/hardness` in both Playwright viewports. |
| Stage 11 CRUD / eight-generator golden do not regress | Final Java suite and the current-code eight-generator golden matrix both pass. |

## Final local regression

- `npm run build`: passed; Chinese localization gate `180/180`; TypeScript and Vite production build passed.
- `npm run test:e2e`: `170/170` passed, including Chromium/compact viewports and the visual/DPI matrix.
- final Stage 12 Projectile + Villager Trade targeted E2E after localization cleanup: `4/4` passed.
- validation field-location targeted E2E: `2/2` passed.
- `gradlew.bat --no-daemon test`: `BUILD SUCCESSFUL`; JUnit aggregate `410` tests, `0` failures, `0` errors, `36` skipped.
- current-code Stage 12 generator matrix: Fabric/NeoForge × 26.2, 26.1.2, 1.21.1, 1.20.1 all produced `BUILD SUCCESSFUL`.

## Compatibility defects exposed by the stronger fixtures

The expanded Stage 12 golden build found and closed compatibility defects that the earlier five-type fixture did not reach:

- 26.2 concrete-powder, dyed-bundle and banner collection mappings were completed for all 16 colors on Fabric and NeoForge;
- Fabric 1.20.1 no longer reads the removed single `creativeTab` field for block/plant registration, while the living-entity Spawn Eggs fallback remains intact;
- corrupt/truncated Mojang mapping cache entries are validated against metadata and repaired/removed before legacy generator setup can reuse them; the NeoForge 1.20.1 task has an integrity-checked official-download fallback.

Both 26.2 `blocksitems.yaml` files were audited for duplicate top-level keys; the concrete-powder, banner and dyed-bundle groups each contain all 16 variants.

## Static audit note

Some tracked files intentionally use CRLF with Git `-text`, so plain `git diff --check` reports their carriage returns as trailing whitespace. An added-line logical whitespace audit that strips the repository EOL marker reports no real trailing spaces/tabs or conflict markers. Mixed-EOL files touched by editing tools were restored so unchanged lines keep their repository EOL form.

# Copperbench 26.3 scoped candidates — 2026-10-14

Four platform/loader scopes have reviewed G2 evidence: Fabric and NeoForge on Windows 11 x64 and Ubuntu 24.04 LTS x86_64 GNOME Xorg/native Wayland. These are frozen, locally reviewable candidates. No mainline merge, release upload or public support promotion is represented here.

## Candidate identities

| Platform / loader | Source commit | Candidate ID |
| --- | --- | --- |
| Windows / both | `545a713382ec3155be4b51fa18042f2cefabc0b0` | `sha256:4cb9fecc20384c22a7e610302b53c1a9775a7952a6f00ceac92ef7af2baa682a` |
| Ubuntu / Fabric | `12642d68c0f7c041d478eadbd6cf6853b7988044` | `sha256:5af68d4a7690efa0038675bab96987d888bb624f1b8550cfcb74a1a14006203c` |
| Ubuntu / NeoForge | `f29b4e193ed72d7cc60ab199687ffada32adeb74` | `sha256:25c777914cbea3ce14fe59c0f9a03a79613d7c03cd3f7631ebd480ec894121d8` |

Every source snapshot is clean. Application, launcher, package, SDK, generator and sample hashes are in each frozen `candidate.json`; installed observations are reproduced in the [review index](../../evidence/p263/2026-10-14/qualification.json). Frozen files remain unchanged, including their historical `frozen_pending_acceptance` status. This dated review is a separate record.

The final sample mod hashes are Fabric `d77a9e64dad8913d72089ac3264675c0d21d26723f5f925ef149f8c09907fe6a` and NeoForge `7c4d640185bd944006b53f76ff72fbdd0575ebaf4494d6f49d562fe8b87a435a`. Each desktop is bound to its own actual installation receipt. Ubuntu coverage combines strict Xorg and Wayland pairs; the existing evaluator still reports the other desktop as `not_run` when checking only one pair. It does not automatically produce a whole-Ubuntu pass across different installation receipts.

## Scope visible to users and agents

The [Core capability catalog](../../src/main/java/dev/copperbench/tracks/PreviewCapabilityCatalog.java) remains the source for Native, MCP, Python/TypeScript SDK and UI discovery. Explicit preview negotiation and session opt-in are required. The catalog's `supportStage` remains `registered`; the candidate review does not silently change a frozen binary or claim general published support. Core continues to check authority, exact generator and revision on each operation. Revoking opt-in closes the preview write path.

The five bounded structured types expose these fields. Values and supported recipe subtypes must still satisfy the discovered contract and validator; field presence is not a promise of arbitrary values or conversions.

| Type | Fields |
| --- | --- |
| item | `name`, `displayName`, `description`, `texture`, `stackSize`, `maxStackSize`, `rarity`, `immuneToFire`, `fuelBurnTime`, `compostingLayers` |
| simple block | `name`, `displayName`, `description`, `texture`, `hardness`, `resistance`, `rotationMode`, `maxStackSize`, `rarity` |
| recipe | `name`, `displayName`, `description`, `recipeType`, `recipeShapeless`, `recipeSlots`, `recipeReturnStack`, `recipeRetstackSize`, `cookingTime`, `xpReward`, `smeltingInputStack`, `smeltingReturnStack`, `blastingInputStack`, `blastingReturnStack`, `smokingInputStack`, `smokingReturnStack` |
| function | `name`, `displayName`, `description`, `namespace`, `code`, `commands` |
| code | `name`, `displayName`, `description`, `code`, `codeFiles`, `sourceFingerprints` |

Preview operations cover workspace creation, native file editing, validation, generation, build, ordinary client/server, test preparation, GameTest, export and create/update of those bounded elements. `run_datagen` remains unavailable. GUI, overlay, keybind, arbitrary structured loot and other element types have no new support claim. Unknown fields retain the established round-trip policy without becoming generated features. Handwritten multi-file Java and external edits remain protected by revisions, fingerprints and ownership checks.

Version Copy Upgrade remains a separate same-loader 26.2-to-26.3 operation. Only the proven bounded function conversion runs automatically; unresolved items stay manual and block completion. It does not generalize to all five types or cross-loader conversion. The source workspace and its worlds are retained; the target does not automatically take the original world.

## Locks and acceptance

Minecraft 26.3, Java 25 and Gradle 9.6.0 are exact. Fabric uses loader 0.19.5, Loom 1.17.21 and Fabric API 0.162.0+26.3. NeoForge uses 26.3.0.76 and ModDev 2.0.148. Resource/data pack formats are 97.1/121.0. The [Fabric](../../tools/generator-probes/fabric-26.3/environment.lock.json) and [NeoForge](../../tools/generator-probes/neoforge-26.3/environment.lock.json) qualification locks distinguish the product runtime from workspace Java; frozen generator copies retain the exact dependency checksums. No dynamic versions or disabled checksum/TLS checks are introduced.

All six G2 client cells completed actual player actions, focus rejection, normal exits and same-world reentry. Cold/warm installed Native runs and independent-host T01–T13 use the final JAR and fixed minimum 13. Four valid portable exports and two tampered exports were separately checked per platform/loader. The dated regression reuse includes all eight original exact generators (15 tests) plus exact-workspace, preview, lifecycle-event and Native-session checks (44 total, no failures/errors/skips). Those checks ran on 2026-10-13; this documentation closeout did not rerun them. Earlier full SDK/UI/build/Javadoc results remain dated in the [integration report](../testing/p263-g2-integration-preparation-2026-10-13.md).

The Windows candidate-to-review delta before this closeout is restricted to cloud control/workflow, evidence tools and documentation. The earlier Ubuntu Fabric candidate also predates a NeoForge Linux lock repair, outside its qualified Fabric scope. These later changes do not retroactively alter the candidate bytes. The current branch is not represented as a newly packaged candidate.

## Limits and rollback

- Fabric's two Ubuntu final-save comparisons failed only on position. The original second-login logs exactly restore the first-save coordinates; separate proofs qualify position at load. The failed final comparisons remain failed. The other six state checks and function state agree. Development player UUID/appearance may change.
- Native Wayland was exercised in a software-Mesa runner. Early distant-horizon rendering artifacts have no established cause. Fabric also required observed recovery from pointer/capture failures; rejected and ineffective requests remain in the evidence. General GPU/visual quality is not qualified.
- G1 Fabric Xorg and NeoForge Wayland use separately bound supplemental reopen evidence after original control timeouts; those original runs remain failed. G1 missing textures/models/localization are not a visual-quality pass.
- Windows NeoForge required the recorded task-local proxy. Network/cold-cache conditions are specific to the runs; no universal default-network guarantee is claimed.
- Portable verification establishes bundle integrity and raw test semantics. It does not authenticate the producer or check an absent recipient workspace.

To roll back preview use, revoke the session opt-in and reopen the retained original workspace with its exact prior generator/product. For copy upgrade, continue using the source workspace; do not perform an in-place downgrade or overwrite worlds. Candidate packages remain separate from the existing public installers, and the public version, eight-generator declaration and historical gates in [product status](../../product-status.json) keep their previous meaning.

Reviewable packages, raw successful/failed evidence, local tickets and the approved spec are indexed from `D:/AICoding/Minecraft_ModCreator/output/p263-delivery/README.md`. The [final ticket report](../testing/p263-closeout-2026-10-14.md) links the individual acceptance records.

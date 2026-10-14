# Fabric 26.3 structured recipe slice

The [Native runner](../../../sdk/python/structured_263_recipe_delivery.py) creates one item and five recipes through the product, edits them, generates the mod and tests the packaged JAR in a separate server host. This directory supplies only independent test code and resources.

The [manifest](required-cases.json) fixes T03/T04/T07/T08/T09, minimum 5, under `camp-kit-recipe-v1`; SHA-256 `033c965b66c3e9948cc5a1e62283e68879275f7dc5645f2865ef73927266c918`. It is a development subset of the full scoped-support gate.

| Input | Scope and defaults |
| --- | --- |
| `name`, `displayName`, `description` | Internal identifier and creator metadata; the identifier is immutable after creation. |
| `recipeType` | `Crafting`, `Smelting`, `Blasting`, `Smoking`; default Crafting. |
| `recipeShapeless` | Boolean; default false. Both shaped and shapeless crafting are supported. |
| `recipeSlots` | Nine item references, in row order; empty string means an empty slot. Default is nine empty slots. |
| `recipeReturnStack` | Crafting result reference. |
| `recipeRetstackSize` | Integer 1–99; default 1, used for all four recipe types. |
| `cookingTime` | Integer 1–1000000 ticks; default 200. |
| `xpReward` | Number 0–256; default 0. |
| `smeltingInputStack`, `smeltingReturnStack` | Input/output references for the ordinary furnace. |
| `blastingInputStack`, `blastingReturnStack` | Input/output references for the blast furnace. |
| `smokingInputStack`, `smokingReturnStack` | Input/output references for the smoker. |

Discover these 16 fields through `get_mod_element_field_contract` with `elementType=recipe`. Empty UI creation produces an editable draft; generation requires the selected subtype's ingredients and result. The published minimal example is a valid shapeless recipe. Unsupported subtypes, namespace overrides, groups, unlocking rules and other active fields are rejected.

The vanilla reference list is bounded to `Items.STICK`, `Items.STRING`, `Items.PAPER`, `Items.FLINT`, `Items.DIAMOND`, `Items.COAL#0`, `Items.IRON_NUGGET` and `Blocks.SAND`. Use `get_field_reference_options` for the active mapping. Workspace references use `CUSTOM:<internalName>` and resolve the element's actual registry name for generated JSON. The runner explicitly imports a differing `registry_name` after closing the product, then regenerates through the product; it does not insert a handwritten item implementation. Missing references fail without changing the stored recipe.

The [host tests](src/gametest/java/copperbench/acceptance/AcceptanceTests.java) cover:

- T03/T04: real shaped and shapeless matching, exact output counts, wrong crafting inputs, and a wrong furnace input consuming no ingredients or fuel.
- T07: an ordinary furnace completes at exactly 200 actual entity ticks, consumes one ingredient and one fuel, and produces two items.
- T08: blast furnace and smoker recipes each declare 100 ticks and complete at exactly that time. The tested fuel has speed multiplier 1 and a 400-tick budget. There is no additional assumed division by two: 26.3 computes `ceil(recipe.cookingTime / fuel.speedMultiplier)`.
- T09: a real server datapack reload changes the shaped result from 2 to 4. The actual registry-aware `Recipe.DIRECT_CODEC` rejects a missing output item, with its original error retained outside the business case count.

On Windows, from the repository root with the qualified Gradle cache:

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 runStructured263RecipeDelivery '-Pp263GradleHome=C:/Users/Administrator/.gradle' --no-daemon --console=plain
```

The runner records Native creation and updates, input rejection, generation/build, host XML, the tested JAR, two verified exports, and a fresh product-process reopen under `output/p263-18/native`. Temporary task authority is revoked at the end. Player input/rendering, installation and the full G2 gate are handled by later tickets.

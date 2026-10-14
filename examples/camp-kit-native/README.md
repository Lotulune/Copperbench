# Native Camp Kit acceptance

The Fabric and NeoForge 26.3 fixtures contain handwritten native code and data, separate from structured element generation. Each uses its independently pinned workspace plugin. `Fabric263AcceptanceTest` and `NeoForge263AcceptanceTest` create their respective workspaces, copy the fixtures and initialize content from the generated entrypoint's preserved user code block. NeoForge receives its mod event bus for deferred registrations.

The independent host compiles only `src/gametest/java`, loads the built mod JAR and runs the six fixed `camp-kit-native-v1` identities. Test expectations use literal expected values rather than the mod's implementation constants.

| Identity | Observed behavior |
| --- | --- |
| `copperbench_camp_kit:t01` | Item registration and a stack limit of 16 |
| `copperbench_camp_kit:t02` | Native custom-name component, count and stack limit survive the ItemStack codec |
| `copperbench_camp_kit:t03` | A stick above string crafts exactly two trail kits |
| `copperbench_camp_kit:t04` | Dirt cannot replace the stick and produces no crafting result |
| `copperbench_camp_kit:t11` | BlockItem places the post and the level stores its axis state |
| `copperbench_camp_kit:t12` | The repaired packaged `RecoveryProbe` matches independent expected values, including a large input |

Fabric's annotation naming convention includes its host mod and Java class name. Its fixture instead registers native test functions when the public test entrypoint is instantiated. NeoForge registers the same function identities through its event bus. Both supply six `test_instance` data files with the frozen resource identities; neither rewrites XML or renames reported cases after execution. NeoForge's own empty 8×8×8 structure is an NBT resource at data version 5023, with an air palette and no blocks or entities.

Run the opt-in backend probe with the repository's Gradle wrapper and `-Dcopperbench.p263.fabricNativeAcceptance=true`, selecting `dev.copperbench.generator.workspace.Fabric263AcceptanceTest`. On Windows use `scripts/run-gradle-external.ps1`. Its output directory contains the generated workspace, separate host, raw XML and assessment.

For NeoForge select `dev.copperbench.generator.workspace.NeoForge263AcceptanceTest` with `-Dcopperbench.p263.neoforgeNativeAcceptance=true`.

`runNative263Acceptance -Pp263AcceptanceFixture=<qualified-workspace> -Pp263GradleHome=<cache>` runs the separate Native SDK delivery chain. It records controlled compiler failure/repair, exact required-case negotiation, selected-JAR export, real process replacement and explicit negative fixtures. API grants are issued only in the isolated test home and revoked in cleanup; this harness does not exercise the desktop grant UI.

The Native harness defaults to `fabric-26.3`. Pass `-Pp263Generator=neoforge-26.3` with a qualified NeoForge fixture for that route.

The tests cover server behavior. Player controls, rendering, installation and published support stages have separate acceptance gates.

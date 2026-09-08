# Survey Pulse - native Fabric comparison prototype

A plain Fabric 1.21.1 mod authored through ChatGPT and local coding tools. It does not use Copperbench, MCreator, their generators, a `.mcreator` workspace, or their MCP API at build/runtime.

Use Java 21 and run `gradlew.bat build` on Windows (or `./gradlew build` on a suitable Unix environment). The bundled wrapper selects Gradle 9.7.0; Loom 1.17.19, Fabric Loader 0.19.3 and Fabric API 0.116.15+1.21.1 are pinned to the previous comparison project. This trial was built on Windows only. Downloads may be necessary outside the warmed test environment.

The initial trial reused a preinstalled JDK, the existing user's Gradle dependency cache, and stock Gradle wrapper files from the previous test workspace. Mod code and build configuration were independently written against the known feature specification. This is not a cold-machine installation benchmark or an independent Codex CLI benchmark.

## Features

Craft one Survey Wand from an amethyst shard and a copper ingot. Right-click to scan an inclusive radius-5 cube; sneak for radius 8. The server skips unloaded positions, counts block IDs ending in `_ore` plus `ancient_debris`, displays the nearest relative position, and applies a 60-tick cooldown. The nearest block gets end-rod particles. The icon reuses the vanilla amethyst texture.

The registry-name ore heuristic is intentionally the same limited heuristic as the prior prototype, not universal modded-ore support. Both prototypes use the mod ID `survey_pulse`: do not install both at once.

## Verification scope

`build` runs eight pure scanner assertions through `scannerContractTest`. They check empty/ore results, nearest position, scan boundaries, unavailable positions, bounded work and messages. These are not Fabric runtime tests. Successful compilation/packaging and these assertions do not prove actual multiplayer, input, rendering or in-game behavior.

The first full build exposed a test-source-set wiring error. The runner was corrected by using the dedicated `contractTest` source set; the original failure is retained in the comparison evidence rather than hidden by disabling no-tests failures.

The source comment `EXTERNAL_IDE_EDIT_SENTINEL` is deliberately retained from a file-preservation experiment. It has no gameplay effect.

A development-server bootstrap probe executed the mod initializer and reached the EULA boundary with `eula=false`. No agreement was accepted and no world was created. This does not certify a deployed JAR or gameplay.

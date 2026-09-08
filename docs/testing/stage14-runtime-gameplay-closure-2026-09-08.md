# Stage 14C runtime/gameplay closure evidence — 2026-09-08

## Scope and decision

This record closes **Stage 14C real runtime verification** on the current development line and, together with the already-closed 14A, 14B and 14D waves, closes **Stage 14 overall**.

The closure keeps the evidence layers separate. A cell is not promoted from compile to load, server-ready or gameplay merely because an earlier layer passed. The NeoForge 1.20.1 cells remain explicitly authorization-blocked before mod loading because the harness did not and must not accept the Minecraft EULA for the user.

Primary evidence:

- [Fabric packaged gameplay matrix](../../evidence/stage14/2026-09-08/fabric-gameplay-matrix/matrix.json)
- [NeoForge packaged gameplay matrix](../../evidence/stage14/2026-09-08/neoforge-gameplay-matrix/matrix.json)
- [Fabric packaged-JAR matrix](../../evidence/stage14/2026-09-08/fabric-packaged-matrix/matrix.json)
- [same-Agent native/Copperbench comparison](./native-agent-survey-pulse-comparison-2026-09-07.md)
- [Stage 14A source-integrity closure](./stage14-source-integrity-2026-09-07.md)
- [Stage 14B/14D closure](./stage14-native-authoring-review-reuse-2026-09-08.md)

## Eight-track runtime result

The representative Survey Pulse behavior is right-click server-authoritative scanning with normal radius 5, sneak radius 8, a 60-tick cooldown, spectator rejection and multiplayer cooldown isolation. The runtime host loads the tested Survey Pulse **packaged JAR** and contains no Survey Pulse source files.

| Track | Native | Copperbench generated | Closure note |
| --- | --- | --- | --- |
| Fabric 1.20.1 | behavior passed | behavior passed | packaged JAR loaded; server ready; no EULA acceptance |
| Fabric 1.21.1 | behavior passed | behavior passed | packaged JAR loaded; server ready; no EULA acceptance |
| Fabric 26.1.2 | behavior passed | behavior passed | packaged JAR loaded; server ready; no EULA acceptance |
| Fabric 26.2 | behavior passed | behavior passed | packaged JAR loaded; server ready; no EULA acceptance |
| NeoForge 1.20.1 | behavior `not_run` | behavior `not_run` | compile passed; EULA boundary passed; initializer/server/package remain `blocked` because Forge 47 checks EULA before mod load |
| NeoForge 1.21.1 | behavior passed | behavior passed | packaged JAR loaded; server ready |
| NeoForge 26.1.2 | behavior passed | behavior passed | packaged JAR loaded; server ready |
| NeoForge 26.2 | behavior passed | behavior passed | packaged JAR loaded; server ready |

Fabric therefore records **8/8 behavior-passed cells**. NeoForge records **6 behavior-passed cells plus 2 explicit authorization-blocked 1.20.1 cells**, with **8/8 closure-satisfied cells**. The NeoForge matrix itself reports `eulaAcceptedByHarness=false`, `closureSatisfied=true` and `passed=true`; the two blocked cells are not rewritten as gameplay passes.

## CB-AUDIT-07 — modern NeoForge listener-free main-class registration

The first full NeoForge gameplay matrix found a product generator defect in the Copperbench variants for `neoforge-1.21.1`, `neoforge-26.1.2` and `neoforge-26.2`: the generated main class unconditionally called `NeoForge.EVENT_BUS.register(this)` even when it contained no `@SubscribeEvent` method. NeoForge correctly rejected those packaged mods at construction time.

The modern templates now register the main class on `NeoForge.EVENT_BUS` only when a Procedure exists, which is also the condition that emits the generated server-tick subscriber. NeoForge 1.20.1 is unchanged because its maintenance template always contains the subscribed tick handler associated with its work queue.

Regression coverage was added as `NewWorkspaceFabricGeneratorPluginsTest.modernNeoforgeOnlyRegistersMainClassWhenGeneratedEventHandlersExist`. The full targeted generator-template class passed, including this new case. The three affected Survey Pulse Copperbench fixtures were then regenerated through the retained production Core/generator audit path rather than hand-edited, and their main classes retained the `pulse_runtime.init()` hook while no longer containing the invalid self-registration.

The failed Stage 14C cells were replayed in resume mode. The corrected results are:

- NeoForge 1.21.1 / Copperbench: initializer, packaged JAR, server-ready and behavior all passed.
- NeoForge 26.1.2 / Copperbench: initializer, packaged JAR, server-ready and behavior all passed.
- NeoForge 26.2 / Copperbench: initializer, packaged JAR, server-ready and behavior all passed.

The first long outer Gradle invocation completed the JUnit method as `PASSED` and wrote the successful matrix, but Gradle then reported a post-test `EOFException`. A second resume run against the already-complete matrix exited normally with `BUILD SUCCESSFUL`; the transient test-executor teardown failure is therefore retained as an orchestration failure rather than reclassified as a gameplay/product failure.

## FR-ADV-08 layered evidence

The matrices preserve `prepared`/`compiled`/`initializer_executed`/`eula_boundary_reached`/`server_ready`/`packaged_jar_loaded`/`behavior_verified` as separate facts. Runtime failures retain raw logs and packaged-JAR hashes. The GameTest worlds are isolated under the Stage 14 build tree and the harness does not accept EULA or touch user worlds.

The behavior cells exercise real server runtime paths, including embedded player connections for multiplayer isolation. They do **not** certify physical OS mouse input, rendered HUD/particle appearance, or unrelated GUI/accessibility environments; those are not silently inferred from server GameTest evidence.

## FR-ADV-09 matched-task comparison

The earlier same-Agent comparison already records the native-vs-Copperbench authoring/build conditions, cache/toolchain reuse, rework, source-preservation differences and limited timing samples. The new Stage 14C matrices add runtime behavior equivalence evidence; they do not retroactively turn the earlier small timing sample into a statistically meaningful speed claim, and there is still no fabricated token/cost comparison.

## Closure

- **14A: closed** — source integrity and generated/manual lifecycle.
- **14B: closed** — native/bootstrap/IDE/CLI authoring loop.
- **14C: closed** — layered packaged runtime and representative gameplay evidence, with the NeoForge 1.20.1 authorization boundary preserved as `blocked/not_run` rather than green.
- **14D: closed** — review, recovery, templates and experimental-extension boundary.
- **Stage 14 overall: closed on the current development line.**

The earlier Fabric `Commands` remap warning still deserves independent semantic maintenance coverage, but it is no longer used as a proxy for the Stage 14 runtime matrix and is not a Stage 14 closure blocker. A future release/installer candidate still needs normal provenance and any affected installed-product gates rebound to the final source state.

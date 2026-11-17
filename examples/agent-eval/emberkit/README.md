# Emberkit comparison evaluation

ADR-0019 judges its direction by giving the same Minecraft tasks to an Agent
with and without Copperbench, at the end of each stage. This kit turns "the
JAR builds" into per-assertion results. Every finished mod JAR, from any group,
is loaded unchanged into the same host and checked against the same assertions.

- [tasks.md](tasks.md): the task texts given to every Agent run (T1 content, T2
  behaviour; Fabric 1.21.1, mod id `emberkit`).
- [assertions.json](assertions.json): one row per checkable requirement, plus
  what is deliberately not asserted.
- [t1/EmberkitT1Tests.java](t1/EmberkitT1Tests.java) and
  [t2/EmberkitT2Tests.java](t2/EmberkitT2Tests.java): GameTests that use only
  registry IDs and vanilla APIs, so they judge any implementation.

## What is checked

- **GameTests** run in Copperbench's own packaged-JAR host (the one behind
  `run_game_tests`), on a dedicated Fabric 1.21.1 server:
  - T1: registration, hardness and blast resistance, the pickaxe tag and tool
    rules for every tier, the loot from a pickaxe, both creative tabs, and that
    the recipe is shaped, produces four and needs the dust.
  - T2: stack size, durability and tab, then the right click through the
    server's real item-use path with a survival player. With targets: glowing
    for 200 ticks on a nearby entity only, 1 durability, a 100-tick cooldown.
    Without targets: no cost, and the action-bar key.
  Each T2 behaviour runs in its own batch, so no other test's entities are in
  range.
- **Static checks** read the JAR itself: en_us and zh_cn names, and that the
  blockstate and every model resolve to PNG textures inside the JAR.

Creative tabs are checked as the server builds them. A mod that fills tabs only
on the client fails those rows although the client would show the item. Sounds
and client-side behaviour are listed under `notAsserted` and are not scored.

## Run

Write a manifest that lists the JARs (`label` is used in file names and table
columns):

```json
{"runs": [
  {"label": "bare-t1", "task": "t1", "jar": "D:/runs/bare/t1/build/libs/emberkit-0.1.0.jar"},
  {"label": "cb-t1", "task": "t1", "jar": "D:/runs/cb/t1/build/libs/emberkit-1.0.jar"}
]}
```

Then, from the repository root:

```text
./gradlew --no-daemon runAgentEvaluation -PagentEvalManifest=<manifest.json>
```

On the Windows development host:

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 runAgentEvaluation "-PagentEvalManifest=<manifest.json>"
```

Hosts are built under `build/agent-eval/<run>`. The report goes to
`build/reports/agent-eval/<run>`: `results.md` (one table per task, assertions
by JAR), `results.json`, and each JAR's host log, GameTest XML and parsed
verification. The host uses the existing Gradle cache and needs the Fabric
1.21.1 dependencies there or on the network. No client is started and no EULA
is accepted.

Agent runs, their transcripts and the resulting reports are evidence; keep
them under `output/` rather than in the repository.

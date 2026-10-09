# M3 fixed task card and recording rules

Use this card with one frozen candidate. Register the participant or agent, OS,
package SHA-256, source commit, model/settings, workspace root and cache condition
before the attempt. The first attempt begins when the participant receives the
task. Keep failed and abandoned attempts; a replacement attempt gets a new ID.

## Task

Using the installed Copperbench product, create a Fabric 1.21.1 mod with mod ID
`m1_delivery`. Discover the item and recipe fields through the public API. Create
an item named `discovery_item`, limited to stacks of 16, and a recipe named
`discovery_recipe` that converts one stick into one item. Add the supplied
`recovery_probe.java` Code element and save/build the workspace.

Change its return expression from `return (count + 15) / 16;` to
`return missing_m1_symbol;`. Build, find the error from the returned diagnostics,
repair it explicitly and build again. Preserve the failed task and its original
diagnostics. Make a separate disposable copy without the workspace's `.mcreator`
generator cache or `.gradle`, `build`, `run` and `.copperbench` runtime output.
Use the API to change the item's stack size to 15, leaving regeneration pending,
and confirm preflight is ready with `dependenciesRequired=true`.
Then append a comment to the generated item Java file. Require preflight to
report `SOURCE_CHANGED` and generation to preserve the external edit. This
copy is separate from the verified 16-item-stack artifact.

Use the supplied five independent tests in the
[fixed fixture](../../examples/agent-readiness/m1-delivery/README.md) to verify the
packaged JAR. Export the same verified artifact, reconnect, and confirm that the
verification still belongs to the current input. Report protocol, compilation,
business-test and player results separately.

For the player portion, use the installed product to launch a client containing
that verified JAR. Fix the test player's username and UUID in the isolated client
host, and check the actual identity on both runs. Craft with a wrong ingredient,
then actually craft 17 items
from sticks. Show the 16+1 stacks. Save that inventory, leave the world and close
Minecraft normally. Relaunch through the same product, enter the same world and
show the restored inventory. Record both client processes and product tasks.

## Operator record

Record start/end times, completion/abandonment, every human rescue and its step,
confusing labels or instructions, task IDs, final JAR/report hashes and evidence
paths. Preparation commands that supply ingredients are separate from player
crafting. Screen captures and input must come from the isolated guest.

For the preregistered agent study, freeze at least 20 IDs and their settings
before its first measured run. For the unfamiliar-user study, use 5–8 anonymous
participant IDs and this same card. The denominator includes every registered
attempt that started. Do not infer a >=80% success rate from a repeated script.
The scripted check below is a separate installed-product regression.

No participants have been recruited or contacted by this card. Record actual
results only; neither the script nor an agent role-playing a participant supplies
unfamiliar-user evidence.

## Scripted installed-product check

[verify-agent-readiness-installed.py](../../scripts/verify-agent-readiness-installed.py)
performs the fixed public-API task using the installed candidate's Python SDK.
It requires an existing task authorization, verifies the candidate package hash,
uses new workspace/output directories, and leaves failures and the conflicting
copy in place. It does not issue authorizations or accept the Minecraft EULA.

Example for an installed Ubuntu candidate (replace the bracketed values):

```text
python3 verify-agent-readiness-installed.py
  --product-root /opt/copperbench
  --candidate-package /home/cbtest/candidate/copperbench_0.1.4_amd64.deb
  --candidate-sha256 <frozen-deb-sha256>
  --source-commit <full-source-commit>
  --workspace-folder /home/cbtest/workspaces/m3/run-001
  --task-authorization <approved-task-id>
  --fixture /home/cbtest/m3-fixture
  --output /home/cbtest/output/m3/run-001
```

The command is shown on separate lines for readability; supply it as one command
or use the shell's line continuation. Keep `result.json`, raw CLI output, each
task result, the verification and export, failure details and `evidence-hashes.json`.
The script intentionally reports `playerBehaviorVerified=false` and
`autonomousAgentSuccessMeasured=false`. Complete actual player and study records
separately in the [M3 worksheet](agent-readiness-m3.md).

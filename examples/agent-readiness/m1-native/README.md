# M1 native-route delivery fixture

The source gate `runAgentReadinessDelivery` builds this fixture the way an Agent
works by default: hand-written code and resources saved through Core, then a
real build and a packaged GameTest host. No structured element is created. The
run records the per-file SHA-256 values of this directory and uses Fabric
1.21.1 with the installed generator's Loader/API versions.

The Native SDK delivery script:

1. reads the environment, the source listing and the generated main class
   without writing, and checks that nothing in the workspace changed;
2. takes one recovery point, adds [DeliveryContent.java](DeliveryContent.java),
   [the recipe](discovery_recipe.json) and [the item model](discovery_item_model.json)
   with `write_file`, and imports [the texture](discovery_item.png) with
   `import_asset`;
3. adds `m1delivery.content.DeliveryContent.register();` to the main class's
   `mod init` user code block, and checks that an edit outside the block and
   a create over an existing file are both rejected;
4. runs `prepare_game_tests`, adds [ContractGameTests.java](ContractGameTests.java)
   and points `copperbench-tests.json` at it, all through Core;
5. builds, breaks `bundles` with an unknown symbol, reads the compiler
   diagnostic, repairs the file and builds again;
6. runs five packaged-JAR GameTests: registration and stack size, the recipe
   output, rejection of a wrong ingredient, item serialization and independent
   expected values for `bundles`. It also checks that the class, recipe, model
   and the exact imported texture bytes are in the accepted JAR.

The verification half is unchanged from the earlier element-based gate:
verified export, reconnect through a new launcher, a real insufficient-count
run, JAR/report tampering, labelled recovered-record injections, a changed
source, explicit historical export, and a final acceptance and export.

Run from the repository root:

```text
./gradlew --no-daemon runAgentReadinessDelivery -PnativePythonExecutable=python
```

On the Windows development host:

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 --no-daemon runAgentReadinessDelivery
```

Workspaces go under `build/m1-workspaces` and evidence under
`build/reports/m1-delivery`; existing caches stay in place. This is a headless
and packaged-server gate. Installed UI and player actions need their own
observations. No authorization is issued or EULA accepted.

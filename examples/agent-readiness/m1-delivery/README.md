# M1 fixed delivery fixture

The source of this fixture is this repository revision plus the exact per-file
SHA-256 values recorded by `runAgentReadinessDelivery`. It uses Fabric 1.21.1
and the active installed generator's Loader/API versions; the report records
the resolved environment.

The Native SDK consumes the published item and recipe minimum examples, changes
the recipe output to `CUSTOM:discovery_item`, and creates a manual Code element
from [recovery_probe.java](recovery_probe.java). The item stacks to 16. One stick
crafts one item. Five independently compiled host tests check registration/stack
size, actual recipe matching/output, rejection of the wrong ingredient, item
serialization and independent expected values for the repaired manual code.

Run from the repository root:

```text
./gradlew --no-daemon runAgentReadinessDelivery -PnativePythonExecutable=python
```

On the Windows development host:

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 --no-daemon runAgentReadinessDelivery
```

The harness creates a new workspace under `build/m1-workspaces`, retains logs,
reports, failed task observations and exported bytes under
`build/reports/m1-delivery`, and leaves existing caches in place. It performs
a real Java compilation failure and explicit SDK repair. A reconnect closes the
old launcher, confirms its exit, starts another launcher and reads persisted
task and element records.

Negative cases include a real packaged run with an insufficient minimum,
modified JAR/report bytes, changed source and explicit historical export.
The all-skipped and zero-case export checks deliberately inject copies of
recovered records with matching report hashes but invalid tests; their evidence
is labelled `negative_recovered_record_injection`. They never replace or
relabel the original acceptance record. A final new acceptance/export binds
the final input after these mutations.

This is a headless and packaged-server gate. Installed UI and player actions
need their own observations. No authorization is issued or EULA accepted.

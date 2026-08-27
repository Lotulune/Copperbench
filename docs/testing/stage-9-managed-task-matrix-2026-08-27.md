# Stage 9 managed task matrix - 2026-08-27

This record covers the deterministic contract fixture for the managed
`run_datagen` and `run_gametest` paths across all eight Fabric/NeoForge tracks.
It complements the server readiness fixture and does not claim real Minecraft
process readiness by itself.

Test:

```text
dev.copperbench.generator.Stage9ManagedTaskMatrixTest
```

The fixture verifies that both operations use isolated task directories, that
datagen writes a staged manifest without touching the creator workspace, that
the preview reports a publishable change, and that GameTest completion and
logs are retained in the task projection for all eight tracks.

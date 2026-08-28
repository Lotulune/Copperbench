# Stage 9 server readiness contract - 2026-08-27

This record adds the executable contract fixture for the eight supported
Fabric/NeoForge tracks. The subsequent real Minecraft validation is recorded
in [Stage 9 real dedicated-server readiness](./stage-9-server-readiness-real-2026-08-29.md).

Test:

```text
dev.copperbench.generator.Stage9ServerReadinessContractTest
```

The fixture covers Fabric and NeoForge 26.2, 26.1.2, 1.21.1, and 1.20.1. For
each track it verifies that `runServer` is routed to the correct profile, EULA
acceptance is written only inside the profile's isolated server directory, a
readiness marker is consumed, the task completes successfully, and the marker
appears in the task log projection. NeoForge 1.20.1 additionally verifies its
`runs/server` path and isolated Forge server configuration preparation.

The process boundary is deterministic and injected, so this test is suitable
for protected CI and failure fixtures. Timeout and non-zero process results
are required to fail closed. The separate real-server probe supplies the
runtime evidence needed for the product gate.

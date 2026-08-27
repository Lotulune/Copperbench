# Stage 9 server readiness contract - 2026-08-27

This record adds the executable contract fixture for the eight supported
Fabric/NeoForge tracks. It does not claim that real Minecraft dedicated
servers have passed on every track; that remains the `server-readiness-matrix`
beta-blocking gate.

Test:

```text
dev.copperbench.generator.Stage9ServerReadinessContractTest
```

The fixture covers Fabric and NeoForge 26.2, 26.1.2, 1.21.1, and 1.20.1. For
each track it verifies that `runServer` is routed to the correct profile, the
managed task writes `run/eula.txt` only inside the isolated task directory, a
readiness marker is consumed, the task completes successfully, and the marker
appears in the task log projection.

The process boundary is deterministic and injected, so this test is suitable
for protected CI and failure fixtures. A real-server run is still required to
promote the product gate.

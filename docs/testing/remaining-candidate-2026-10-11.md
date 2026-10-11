# REM-01 candidate build and installation attempt

Recorded 2026-10-11 (Japan time). **Candidate builds passed; installation and runtime acceptance have not started.**
The user requested committing the REM-02/03/06 batch and then installed acceptance, and selected continuing in the isolated virtual machines after releasing memory.

## Source and package identity

- Implementation commit: `e11848de5fc0cd4a4d34e015bfbd93dc5e9f76d5`.
- Candidate source: `e178ecd292178974e5ad64c234006b09e81e70d7`.
- Candidate tree: `ea767de7cec207f0da65b46d189fbd2e8a60622e`.
- The second commit includes the offline delivery verifier and its guide in the product packages.
- Both builds started from this clean source. These are local development candidates; no hosted CI, release publication or main-branch merge is claimed.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Windows installer | 789141737 | `e130b06ac35d3a4f93f8e6cd7d197696b1f3b4fd7970060fc0c2067303dea137` |
| Windows portable ZIP | 1110761525 | `16f40151ef3ab1080a5a699c174ec39f7f19ca75b4d05f4a25ff787d4e8ec26d` |
| Linux portable archive | 969082632 | `5b8a8e371512e33ba7c7356458833dc047cdf513b01be11e27321a4889016404` |
| Application JAR, identical in both portable packages | 7814509 | `72c65f891b18d545a7764745558b509bd32c09fb30f870bc518cf3ba9810d346` |

The frozen [Windows manifest](candidate-e178ecd2-windows.json) and [Linux manifest](candidate-e178ecd2-linux.json)
also record launcher, shipped SDK, verifier and guide identities. Expected hashes were extracted from the selected packages before attempting installation.

## Actual build checks

The existing external Gradle worker was used on Windows 11 with the project JBR 25:

```powershell
./scripts/run-gradle-external.ps1 --no-daemon exportWindowsZip buildInstallerWin64 -Pbuilddate=20261011
./scripts/run-gradle-external.ps1 --no-daemon exportLinuxCandidate prepareDebLinux64 -Pbuilddate=20261011
```

The Windows build succeeded in 6m 11s, including the production UI, launcher, portable ZIP and NSIS installer.
The Linux layout/archive build succeeded in 1m 56s on the same source and application JAR.
The Linux archive is a cross-built portable candidate. The Debian layout is staged, but native `dpkg-deb` packaging and Debian installation have not run.
Gradle 9.7.0 and 8.8 packaging inputs were retained host distributions; 9.6.1 was copied from the previously frozen Linux package's bundled distribution. This is build dependency reuse, not a cold-cache installation result.

Package inspection confirmed that both archives contain `scripts/verify-delivery-bundle.py`,
`docs/ai/task-verification-and-delivery.md`, the new schema and the updated Python SDK.
The earlier [source verification](remaining-delivery-2026-10-11.md) remains separate from installed acceptance.

## Installation blocker and retained attempts

| Attempt | Observation | Classification |
| --- | --- | --- |
| Windows VM restore, 03:51:38 UTC | Hyper-V could not allocate 4966 MiB; error `0x800705AA` | Infrastructure |
| Ubuntu VM restore, 03:55:34 UTC | Hyper-V could not allocate 5120 MiB; error `0x800705AA` | Infrastructure |
| Windows retry after available memory increased, 03:57:36 UTC | The same allocation failure; 4957428 KiB available before the attempt | Infrastructure |

Both virtual machines remain saved with zero assigned memory. No candidate was transferred to or installed in either guest.
The saved state, previous installations, workspaces, user applications and network settings were preserved.
No task-owned large background process remained to release additional memory. Continuing requires sufficient host memory to restore one guest at a time, with additional headroom for the host.

The following remain **unverified for this candidate**: installer execution, running launcher/doctor identity,
Native/MCP/TypeScript result agreement, installed language switching, CUSTOM references with distinct names,
real build and 5/6 rejection, current/historical export and reconnect, source preservation, and independent checks of a newly accepted delivery bundle.
No Minecraft client was started. The current Codex session has no registered Minecraft-control tools; gameplay checks would additionally require reloading that MCP connection.

## Resume and evidence

Local artifacts and raw receipts are under `output/candidate-acceptance-20261011/` in this worktree:
`frozen-windows/`, `frozen-linux/`, `windows-build.log`, `linux-layout-build.log`, the build-start record,
and the three VM restore receipts. Draft installer/supplemental acceptance scripts there have only had syntax checks; they have not executed against a guest.

Resume from these frozen packages after the memory condition changes. Recheck hashes before transfer,
then follow the [candidate acceptance matrix](next-candidate-acceptance.md), retain actual task and bundle evidence,
and update this record from observed results. Do not relabel the earlier candidate's installed or player results as passing this source.

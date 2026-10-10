# M3 independent-agent study, 2026-10-10

Status: 20 attempts preregistered; execution in progress. No completion rate is
claimed while attempts remain unstarted or running. The user authorized this
study and explicitly left the 5–8 unfamiliar-user study pending because no
participants are available. No one has been contacted.

## Frozen configuration

The [recording tool](../../scripts/agent-readiness-study.py) registered
`m3-agents-20261010` before the first task was dispatched. IDs are `agent-001`
through `agent-020`, each with a fresh execution context and workspace. The
plan SHA-256 is
`a0a263fb272d0c8f9622327324d750ec9f8daf4f139ce9e1fbad4e6adeb7b330`.

| Input | Fixed value |
| --- | --- |
| Product source | `37fcb5b4ad228c3830cda3394eaf8708d57910a8` |
| Installed Debian package SHA-256 | `08093b3a3f5af27cfbcd04a8127aa2fa2fcef0bdfdfe1aac5717b4b2f86bd115` |
| Installed application JAR SHA-256 | `cc817c921272d399c0984e66c69fde71166b58d50c2aead1edb988232c4806ea` |
| Configured agent | `gpt-6-astra`, reasoning `max`, inherited without override |
| Context and access | Fresh context; installed public SDK/docs, fixed fixtures and scoped guest Bridge/shell |
| Time and concurrency | 45 minutes per attempt; one participant at a time |
| Guest | Ubuntu 24.04 GNOME Xorg, 4 GiB minimum / 5 GiB maximum RAM, 4 GiB swap |
| Cache | Warm shared dependency cache; fresh participant workspaces |
| Client | `-Xms512m -Xmx2G`, username `CopperbenchM3`, UUID `7815ca73-e68b-3a9d-ad39-9a880f72891e` |

The model name records the local configured policy, not an independently
verified upstream model identity. A real product GUI grant authorizes the
study workspace root; no dedicated-server EULA was accepted. Desktop control
stays inside the guest, with one input controller and no host focus changes.

The frozen task-card copy retains the complete discovery, controlled repair,
source protection, packaged delivery, reconnect and player requirements. Its
final turnkey scripted-replay section was removed before registration. Agents
may not read prior solutions, implementation classes or other attempts. The
participant protocol and all supplied fixture hashes are in the frozen plan.

## Evidence and review

Guest journal: `/home/cbtest/studies/m3-agents-20261010/journal`.
Guest participant evidence: the sibling `attempts/agent-NNN` directories.
An independent copy of the frozen plan, task card and protocol is retained at
`output/agent-readiness/m3-study-20261010` in the primary checkout.
The 19-file `preparation-archive.zip` retains registration, authorization,
guest recorder-test output, frozen inputs and recorder sources; its guest and
transferred SHA-256 both equal
`cd52811d85cc034c166d90b8b74a3822fc20441aeba5b18a9eddec6052f3285d`.

Each started attempt stays in the denominator. The coordinator reviews the
actual transcript, raw Core receipts, JAR/XML bytes, screenshots, actions and
client exit/restart evidence before sealing a terminal result. Any task-solving
help is recorded as rescue and excludes unassisted success. The tool's receipt
checks do not independently prove agent behavior or player semantics.

`agent-001` was registered started at `2026-10-10T04:15:43.808038Z`; a context
handoff delayed formal task dispatch by approximately two minutes. The original
start and 45-minute deadline remain intact, so that delay consumes its budget.
It must remain visible in the final interpretation rather than resetting the
attempt or silently extending its time.

## Recorder validation

Before trial execution, all 14 focused recorder regressions passed on Windows
and on the Ubuntu guest. They cover preregistration, independent contexts,
failed/abandoned/rescued denominators, frozen inputs, evidence retention,
current-input delivery, tampered bytes and writer exclusion. The PR workflow
runs these regressions before selecting checks.

A separate compatibility check accepted the previous Linux run-006's original
Core receipt, five-case XML, tested/exported JAR and export receipt. That check
is tooling validation only and contributes no autonomous attempt or success.
The earlier [installed and player acceptance](agent-readiness-m3-resume-2026-10-10.md)
also remains separate from this study.

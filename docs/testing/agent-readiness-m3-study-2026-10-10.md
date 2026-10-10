# M3 independent-agent study, 2026-10-10

Status: closed early at the user's request on 2026-10-11 JST (2026-10-10 UTC).
Twenty attempts were preregistered; fourteen started. The user stopped execution
during agent-014 and cancelled the remaining six attempts. All fourteen started
attempts are reviewed, sealed and backed up; no attempt is running. The frozen
plan and original failures are retained. The planned 20-attempt >=80% target is
not claimed as achieved. The 5–8 unfamiliar-user study remains pending because
participants are unavailable; no one has been contacted.

## Reviewed results at closure

| Attempt | Recorded start (UTC) | Participant elapsed | Outcome | Rescue | Player processes |
| --- | --- | --- | --- | --- | --- |
| agent-001 | 04:15:43.808038 | 31 min 04 s | All six stages passed | 0 | 25013 → 30037; both normal exits |
| agent-002 | 04:50:07.866383 | 23 min 32 s | All six stages passed | 0 | 38250 → 42704; both normal exits |
| agent-003 | 05:19:13.549556 | 30 min 14 s | All six stages passed | 0 | 51258 → 54635; both normal exits |
| agent-004 | 05:51:46.768940 | 28 min 39 s | All six stages passed | 0 | 62602 → 68312; both normal exits |
| agent-005 | 06:25:55.698881 | 26 min 44 s | All six stages passed | 0 | 77225 → 81988; both normal exits |
| agent-006 | 06:55:15.801835 | Exact end unavailable | Abandoned: agent transport failure | 0 | Not started |
| agent-007 | 11:12:37.599475 | 30 min 18 s | All six stages passed after local repair | 0 | 114033 → 119551; both normal exits |
| agent-008 | 11:46:40.484262 | 41 min 34 s | All six stages passed; assisted infrastructure recovery | 1 | 129471 → 133868; both normal exits |
| agent-009 | 12:50:01.842225 | 24 min 14 s | All six stages passed | 0 | 143001 → 148080; both normal exits |
| agent-010 | 13:17:41.618479 | 19 min 59 s | All six stages passed | 0 | 156444 → 162120; both normal exits |
| agent-011 | 13:40:12.667275 | 45 min budget exhausted | Failed: persistence not completed | 0 | 172266; SDK cleanup, task failed |
| agent-012 | 14:53:22.871032 | 27 min 36 s | All six stages passed | 0 | 184508 → 189266; both normal exits |
| agent-013 | 15:24:12.414276 | 27 min 02 s | All six stages passed | 0 | 198102 → 202967; both normal exits |
| agent-014 | 15:57:40.690268 | Stopped before crafting | Abandoned: user requested early stop | 0 | 213564; normal cleanup exit |

Attempts agent-015 through agent-020 remain registered and were never dispatched.
Eleven attempts completed all six stages: ten without rescue and one with an
infrastructure rescue. One attempt failed at its deadline; one was abandoned
after transport failure, and agent-014 was abandoned at the user's direction.
These outcomes retain every started attempt, including the user-stopped one;
the preregistered sample was not completed and supplies no final target claim.

Among the eleven completed attempts, recorded start-to-report duration has a
median of 1655.562347 seconds (27 min 36 s), minimum 1199.449162 seconds and
maximum 2494.007456 seconds. Cleanup/reporting is included and the rescued
attempt remains in that distribution. The failed deadline and abandoned attempts
are excluded from completion-duration statistics. These warm-cache observations
are not a comparison with direct coding or a measurement of unfamiliar users.
Agent-001's 296 retained evidence records include the original deliberate build failure,
source-conflict rejection, five-case packaged-JAR acceptance, verified export,
real session reconnect and actual crafting/persistence. The coordinator inspected
the negative recipe and 16+1 screenshots and both client logs/identities. Only
ingredients were supplied by commands; the second process made no inventory
changes. Original Bridge evidence-path mistakes and self-correction remain
visible and are not coordinator rescues.

The transferred `agent-001-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`8a4b7e920a4c6ad31440631d3dc671d40750c13b150b191b35fa1754ae457fba`.
The same directory retains the original plan and an updated event-journal copy.

`agent-002` retains 186 evidence records. The coordinator reviewed the original
fault and explicit repair, cold-copy source rejection, five business tests,
current-input export/reconnect, actual 8+8+1 output clicks and inventory recovery
in a new process. The tested, exported and deployed JAR bytes agree. Both clients,
the SDK and the input controller exited. Its self-corrected evidence-reference
errors remain retained. A simulation-distance correction made after both runs
was not exercised and is not claimed as verified.

The transferred `agent-002-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`f684e86129d961f0c3af35cef0b68f824a71bec3bf3fe568fe3dd999ebcfe5b9`.

`agent-003` retains 166 evidence records. Its first copy retained setup metadata,
so preflight initially returned `dependenciesRequired=false`. It independently
removed the full `.mcreator` cache in its disposable copy, obtained the required
cold state and proved source-conflict rejection without overwriting the external
edit. All five packaged tests, current-input export and real SDK reconnect passed.
The coordinator checked actual 17-item crafting, 16+1 inventory and matching UUID
after normal exit and a new client process. A failed log-reference request and
its own postprocessing serialization failure were corrected independently; raw
errors remain retained. No product or gameplay task was replayed to hide them.

The transferred `agent-003-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`b8ae8bb00facaabd87b9316ba0a8370a64a17e0f6eae35f66524bc9fca38476d`.

`agent-004` retains 227 evidence records. All six stages passed, including an
actual Shift+click that consumed 17 sticks and produced 16+1 items. A new client
process restored the same UUID and inventory; both clients exited normally.
The coordinator checked tested/exported/deployed JAR bytes and the five-case
report. Its own upload race and evidence-path mistakes were repaired without
help. The original rejected and unverified receipts remain unchanged, alongside
explicitly labelled corrections and byte-identical copies of earlier evidence.

The transferred `agent-004-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`fa26d1dbae138ebc046530be4116144fd4a14109ee16d3fe877e05694a64499b`.

`agent-005` retains 252 evidence records. All six stages passed; actual
Shift+click crafting produced 16+1 and a new client process restored the same
UUID, inventory and slots. Both clients, the SDK and controller exited normally.
The tested, exported and deployed JAR bytes agree. Two evidence-path downgrades,
an iterator-serialization mistake and a rejected simulation-distance setting
were preserved and corrected independently. The distance correction was exercised
in the second run; the first run's effective setting remains recorded separately.

The transferred `agent-005-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`9c3076372d52b681bd9113182261851be95c71c57f6191300191dd506183c0bc`.

`agent-006` ended with an upstream connection error from the agent's local
proxy after its public-document reads. It started no product or game task, so no
stage passed. The coordinator resumed after its immutable 45-minute deadline;
the exact participant end time is unavailable. This attempt remains in the
denominator with its journal, scripts, outputs and error review. It will not be
retried or replaced. The infrastructure error does not establish a product defect.
The existing SSH tunnel recovered automatically. After a complete short response
and a 145.855-second sustained SSE response, execution resumed with `agent-007`.
No candidate, protocol, model configuration or route setting was changed.

Its transferred six-file archive has valid ZIP CRC and matching guest/host SHA-256
`4438053c6a6a81edf4fb9da52be0da5dba7ef84e7a4c422537f30b29a523ea4c`.

`agent-007` retains 354 evidence records. Its first disposable copy kept setup
metadata, and a later manipulation removed runtime metadata while the SDK was
open; these attempts did not produce the required cold state and an external
comment was overwritten. Within the same time limit, the agent independently
created a fully cold copy. The required pending change, `dependenciesRequired=true`,
`SOURCE_CHANGED`, generation rejection and unchanged external bytes then passed.
Both original failures and the explicit repair remain in the sealed record.
The other stages passed: five business tests, identical tested/exported/deployed
JAR, real reconnect, 17 crafting clicks producing 16+1, and identical actual UUID
and inventory in a new client process. Both clients and the controller exited.
Two evidence-path downgrades, a rendering-setting correction and an archival
upload error were retained. No coordinator rescue occurred.

The transferred `agent-007-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`f79d91428baaf6df81963d99958a6a74077f4164041e9accf145ee6e92977e0f`.

`agent-008` encountered another local-proxy upstream connection failure after
creating its test world. Its last retained game action finished at 11:57:58 UTC.
The SSH tunnel logged a reset and subsequent banner timeouts, then reconnected.
After the user resumed the coordinator, a complete short SSE response confirmed
current recovery. At 12:11:14 UTC the coordinator recorded an infrastructure-only
rescue and resumed the same participant context within its original deadline
of 12:31:40.484262 UTC. No task-solving hints, product changes or replacement
artifact were supplied. This conservatively excludes any eventual completion
from the unassisted numerator; no attempt or original failure is discarded.

The same attempt finished all six stages before its original deadline. The
coordinator checked five business tests, identical tested/exported/deployed JAR
bytes, unchanged fixed fixtures, cold-copy source protection and real reconnect.
Actual Shift+click consumed 17 sticks and produced 16+1; a new process restored
the exact UUID, inventory and slots. Both clients, SDK and controller exited.
The original absolute-path verification downgrade remains, with nine explicitly
labelled evidence copies checked byte for byte against their originals. The
timestamp-derived duration is 2494.007456 seconds; the participant's earlier
2494.005712-second metric remains unchanged. It has 239 retained evidence records.

The transferred `agent-008-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`8838f468689cea8f9384b957b7ed5d42973721d2dbcea03c5c377840e34f159e`.

Between attempts 008 and 009, the coordinator deployed an infrastructure repair
to the managed host SSH tunnel. Its final preference is the existing local SOCKS
proxy, then the original GCP path and direct origin SSH after disconnection.
Strict host-key checks and loopback-only listeners remain enabled. An isolated
SSH banner-timeout injection switched to the next route in 13.705 seconds and
returned a complete SSE response. Actual local-proxy short responses passed;
the direct path subsequently reset during a long response, and the automatic GCP
fallback passed a158.277-second response. An isolated SOCKS route also passed
156.491seconds but later reset after deployment; GCP recovered again. Thus the
configured preference must not be confused with the actual active route, and
failover does not establish permanent stability. Before starting agent-009, the
final configuration's actual GCP route passed another150.176-second /2,755,950-byte
full-local-proxy response. This changes transport availability, not the
frozen candidate, task, model configuration or participant tools. An interrupted
in-flight response still requires recovery; no root-cause claim is made about
the particular network device or provider responsible for either outage.

A later readback found another GCP reset at 13:10:58 UTC during agent-009's
reporting period; automatic direct-origin connection began two seconds later.
The 13:28 UTC listener inspection confirmed that direct route. Agent-009 still
completed without coordinator rescue. This tunnel event alone does not establish
a lost model response, and corrects an earlier checkpoint that omitted this reset.

`agent-009` retains 242 evidence records. All six stages passed without rescue.
It independently corrected an iterator-serialization error and an unsupported
save-operation call, preserving the original errors. Cold-copy source protection,
five business tests, identical tested/exported/deployed bytes and true reconnect
passed. Seventeen actual output clicks produced16+1 in slots9/10; a new process
restored the exact UUID, inventory and slots. Both clients and the controller
exited. The requested simulation-distance4 was rejected by the game and is not
claimed as effective. Its earliest exception timestamp was not separately measured;
the actual preceding API timestamp and a labelled copy of the tool output remain.
The coordinator extracted the unchanged raw Core export response from the
participant's timestamp/request wrapper into a separate file with provenance.

The transferred `agent-009-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`5966c3008702b32648309e9e401a47662d972fa193d4a958bcede04349c6eadd`.

`agent-010` retains 213 evidence records. All six stages passed without rescue
in 1199.449162 seconds. The coordinator checked the fixed fixtures, source
conflict rejection, five business tests, matching tested/exported/deployed JAR
bytes and a real SDK reconnect. Actual output clicks consumed 17 sticks and
produced 16+1; a new process restored the exact UUID, inventory and slots.
Both clients and the runner exited. An invented evidence reference was rejected;
two absolute-path records were downgraded. The participant independently corrected
them with labelled relative references and eight byte-identical evidence copies,
retaining all originals. Normal-close window errors and an unavailable shell
search command also remain documented. The game rejected requested simulation
distance 4, so that value is not claimed effective. A later metadata correction
counts an idempotent second Bridge close separately from the completed trial.

The transferred `agent-010-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`a78263fbc7d0c2c1a720f8556dc560435baf1aed33f0d00b0643e64d76b7e4ca`.

`agent-011` retains 210 evidence records and is failed overall. Discovery,
controlled build repair, source protection, five packaged tests, export and
reconnect passed. Actual player images show an invalid ingredient with no output
and subsequent 16+1 crafting, but no normal save, new process or restored inventory
was verified by the original deadline. Three within-budget stale-frame rejections
or interruptions and the absolute-path verification downgrades are retained.
Postdeadline work only recorded prior observations and cleaned up; the close
input was rejected, SDK cleanup ended a failed client task, and no scoped process
remains. Administrative deadline supervision supplied no solution or rescue.
The original report's later reporting time is retained separately from the final
report's censored 2700-second attempt end. Neither is a successful completion time.

The transferred `agent-011-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`24a0370d382a22f81aaa7ed3ecf2a6d4c35a037920779012e96c4c19f9773aac`.

`agent-012` retains 300 evidence records. All six stages passed without rescue.
The coordinator checked the complete public contracts, explicit failed-build
repair, preserved source conflict, five packaged tests, identical JAR bytes and
real current-input reconnect. Seventeen actual output clicks produced 16+1;
a new process restored the same UUID, inventory and slots. Both client tasks
succeeded and all scoped processes exited. The participant independently
corrected a helper parameter collision, absolute-path verification downgrade,
cross-session reference rejection and invalid rendering option; originals and
normal-close window interruptions remain. The task finished at 15:17:38 UTC;
cleanup/reporting completed at 15:20:58 UTC. The recorded 1655.562347 seconds
measure start to final report, including cleanup.

The transferred `agent-012-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`b70a115a64622ef471e6a82f95b3bce5d785c04ead1db073922d42ca8d08c092`.

`agent-013` retains 307 evidence records. All six stages passed without rescue.
Actual Shift+click crafting produced 17 items in 16+1 stacks; a new process
restored the same UUID and inventory slots. The coordinator checked all three
fixed fixtures, tested/exported/deployed JAR bytes, five-case XML, raw Core
receipts, both session hash manifests and four cross-session evidence copies.
Both client tasks succeeded and all scoped processes exited. The participant's
preparation and iterator-serialization errors and normal-close interruptions
remain. Task solving ended at 15:47:47 UTC; result assembly followed completed
cleanup at 15:51:14 UTC. The two original end/cleanup metadata timestamps differ
by 5.162 milliseconds because they are constructed successively after cleanup.

The transferred `agent-013-evidence.zip` has valid ZIP CRC and matching
guest/host SHA-256
`da0e8a52d5e25f42e1d0bbf37d759f63df0265c190771ca55d16cdfe42e3d35b`.

`agent-014` retains 161 evidence records. The first five stages passed, including
unchanged fixtures, five packaged tests, identical JARs and current-input
reconnect. Its only game preparation supplied one dirt and seventeen sticks;
wrong-ingredient testing, actual crafting and new-process persistence did not
occur. The user stopped further trials. Cleanup then saved the world, closed
Minecraft normally, recorded player acceptance as unverified and detached/closed
the Bridge, SDK and controller. The client task succeeded, which proves normal
cleanup rather than completion of the player task. All scoped processes exited.

The last preparation action ended at 16:15:03.579184 UTC; the first timestamped
cleanup request was 16:15:54.509350. Exact user-message arrival was not exposed.
Cleanup completed at 16:17:57.249681 and final reporting at 16:20:20.588703.
The 1093.819082 seconds from registered start to the first cleanup request are
an upper bound for the interrupted attempt, not a completed-task duration.
The transferred `agent-014-evidence.zip` has valid ZIP CRC and matching SHA-256
`e296a897e330cbb5d198a2721a16c2596d5c2a4c07526eb9bcf1d973aebb4c0a`.
The isolated Ubuntu VM was saved at 16:24:46 UTC after evidence backup; its
assigned RAM is zero. The Windows guest remains saved. Host focus was unchanged.

Network conditions changed during the study without changing frozen task inputs.
After agent-011 ended, equal-size synthetic image requests showed first response
at 25.621 seconds on the current SOCKS route, 3.303 seconds on direct SSH and
4.714 seconds through GCP. The managed tunnel's default rotation was changed to
direct SSH then GCP, excluding the slow SOCKS route. A complete real local-proxy
request then returned its first event at 2.271 seconds and completed at 3.805
seconds. These measurements support the route adjustment, not attribution to a
particular network device or a claim that all disconnects are eliminated. The
earlier failed/abandoned/rescued attempts remain in the study.

The direct SSH path reset again at 15:27:29 UTC; automatic GCP reconnection
authenticated at 15:27:39, with five local proxy HTTP 502 entries in that gap.
This occurred during agent-013 without coordinator rescue. Routing mitigations
do not eliminate all connection resets or restore an interrupted response. The
network diagnosis is retained separately in the primary checkout's
`output/diagnostics/network-repair-20261010/REPORT.md`.

## Confirmed fix after study closure

Several participants supplied valid same-session absolute evidence paths, which
the Bridge accepted and hashed but then downgraded to `unverified`: its backing
check compared the original string with the literal `actions.jsonl`. The fix
resolves each path within the session once, then uses the checked relative path
for classification and the receipt, and the checked file for hashing. Equivalent
relative and absolute references now agree. The actual session action log and
a PNG remain necessary; the assessment remains external, with
`bridge_independently_verified=false`.

The regression failed before the fix for absolute, `./` and `nested/../` paths.
After the fix, all 30 focused engine/action tests passed on Windows, including
other-session rejection, nested fake action logs and a real symlink-escape check
(not skipped). No gameplay replay was run for this evidence-indexing change.
The frozen guest Bridge and old receipts were not altered; this source change
belongs to the later PR revision, not the studied installed candidate.

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

Recorder commit `5d562a6f` passed all
[required checks](https://github.com/Lotulune/Copperbench/actions/runs/38023863314)
and the additional
[Linux candidate smoke](https://github.com/Lotulune/Copperbench/actions/runs/38023863219).
That CI candidate was not substituted for the frozen installed study package.

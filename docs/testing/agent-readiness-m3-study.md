# M3 study registration and evidence accounting

Use [agent-readiness-study.py](../../scripts/agent-readiness-study.py) with the
[fixed task card](agent-readiness-m3-task-card.md). This recorder supports the
PRD's 20 independent agent attempts and 5–8 unfamiliar participants. It does not
launch an agent, recruit anyone, approve product operations, or execute the task.

## Freeze the study before giving out the task

Prepare a JSON specification with these fields:

| Field | Required value |
| --- | --- |
| schemaVersion / studyId | "1.0" and a unique lowercase ID |
| kind | autonomous-agent or unfamiliar-user |
| sourceCommit | Full product-source Git SHA |
| operatingSystem / cachePolicy | Actual OS and declared cold/warm conditions |
| agent | For agent studies: model and a nonempty settings object, including reasoning, tool access and time limit |
| inputs | candidatePackage, applicationJar, taskCard, recoveryProbe, gameTests, testManifest; each has a local path and expected SHA-256 |
| attempts | At least 20 agent rows or 5–8 user rows, each with id, anonymous participantId and absolute guest workspaceRoot |

Input paths resolve relative to the specification. Hashes are checked during
registration, start and successful finish. Failed or abandoned attempts can
still be recorded after an environment change, preserving that failure.
Workspace roots are guest metadata and support
Windows and POSIX paths regardless of the recorder's OS; they must be distinct
and non-nested. Keep copies of the task card and fixtures in a frozen preparation
directory. An active study should not point at a document being edited.

Register every measured ID and configuration before the first task is handed
out. Fresh agents get independent contexts and only the supplied task card,
installed public SDK/docs, fixed fixture and scoped guest access. Do not give
them previous solutions, implementation classes or the turnkey installed-replay
script. Record the actual inherited model policy if no model override is used;
do not invent a model version. A repeated deterministic script does not become
an independent agent attempt.

For example, after preparing a complete specification:

~~~text
python scripts/agent-readiness-study.py register --spec /trials/planned.json --output /trials/study-001
python scripts/agent-readiness-study.py start --study /trials/study-001 --attempt agent-001 --execution-id unique-agent-context-id
~~~

Record start when the participant receives the task, not after seeing whether
their solution succeeds. An execution context cannot be reused across attempts.
Prearranged guest setup and approvals belong in the fixed configuration; any
task-solving help after start is a rescue:

~~~text
python scripts/agent-readiness-study.py rescue --study /trials/study-001 --attempt agent-001 --actor lead --detail "Explained the field needed after the failed create call."
~~~

Supervision that only enforces the existing isolation/focus boundary should be
described in the transcript. Do not silently repair participant code or supply a
successful replacement artifact. A correction that helps solve the task counts
as rescue even if supplied by another agent rather than a human.

## Finish without discarding unsuccessful attempts

Supply a result JSON beside its evidence files. Required identity fields are
kind, executionId, sourceCommit, candidateSha256 and workspaceRoot, matching the
registration/start. Also provide outcome (completed, failed or abandoned),
reviewer, note and an evidence array of relative file paths. Preserve failure
logs, original diagnostics, partial work and abandonment reasons.

Completed attempts additionally need:

- transcript: the independent agent trace or actual participant session record;
- stages: discovery, build_repair, source_protection, packaged_delivery,
  reconnect and player, each with verdict "passed" and nonempty evidence paths;
- delivery: paths named verification, report, testedJar, exportedJar and
  exportReceipt, containing the original Core verification JSON, GameTest XML,
  tested JAR, exported JAR and raw successful export query receipt.

Stage verdicts are external reviewer judgments. Read the trace and inspect
actual player screenshots/actions; file existence is not semantic proof. Do not
use an agent impersonating a person as unfamiliar-user evidence. Separate
preparation commands from crafting, and preserve both real client processes,
normal exit, same-world inventory and UUID queries.

The byte/receipt gate independently rejects acceptance predating the attempt,
another workspace, fewer than five effective tests, skipped/failed cases, missing
fixed business tests, duplicate case names, changed JAR/XML bytes, historical
export and mismatched source/task/workspace identities. Tested and exported JAR
hashes must agree. It does not re-run Core or establish participant independence.

~~~text
python scripts/agent-readiness-study.py finish --study /trials/study-001 --attempt agent-001 --record /trials/agent-001/result.json
python scripts/agent-readiness-study.py summary --study /trials/study-001
~~~

Evidence is copied into the study directory and hashed. Preserve that directory,
the frozen-input files and a copy of the plan hash outside the editable working
area. This is local accounting, not authenticated or adversary-resistant storage.

## Interpret the summary

Every started attempt remains in the denominator, including running, failed,
abandoned and rescued attempts. Unstarted preregistered IDs are listed separately.
Only a completed, unrescued attempt with intact evidence contributes to
unassistedCompletionRate. This rate is provisional until every preregistered
attempt finishes; agentTargetMet stays null until then. The >=80% threshold
applies only to the agent study, never to the small unfamiliar-user sample.

A changed evidence file removes that attempt from the unassisted numerator while
keeping it in the denominator and surfacing evidenceIssues. Registration and
terminal records cannot be replaced through the CLI. A writer lock prevents
concurrent mutations; after a crashed writer, inspect its recorded PID and the
ledger before manually removing a stale lock. A truncated ledger is rejected,
not silently repaired or treated as an omitted failure.

The recorder's synthetic unit fixtures are tooling checks, not actual attempts.
The earlier Windows/Ubuntu scripted installation and player acceptance remain
separate evidence; do not import them as new autonomous successes.

## Verification and execution status

Complete this recording phase before running its focused tests:

~~~text
python -m unittest discover -s scripts/tests -p "test_agent_readiness_study.py" -v
node scripts/verify-markdown-links.mjs
~~~

The PR workflow also runs the recorder regression before check selection.
The user authorized 20 independent agent trials on 2026-10-10. Their frozen
configuration and execution status are recorded in the
[study record](agent-readiness-m3-study-2026-10-10.md). No unfamiliar participants
are currently available; the user explicitly left that study pending.

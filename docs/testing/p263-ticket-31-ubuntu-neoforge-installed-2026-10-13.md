# P263-31 Ubuntu NeoForge installed Native acceptance

The [complete cloud workflow](https://github.com/Lotulune/Copperbench/actions/runs/38354975581)
passed on Ubuntu 24.04 x86_64 using source commit
`0be9215b12c467da7829f535f61333172a2a83db`.

Candidate: `sha256:e1e4075a1347c9199259e82fd0d16ca28e7b02df7043d883f8f2f80800971308`.
Installed cell SHA-256: `7478d93672994277259fd276baa25908fcfe3dd0a2a7285aec960010d3fd3786`.

The cold run started with zero cache files and passed in 260.674 seconds. The
warm run passed in 135.252 seconds with the prior cache payloads verified. Both
ran the installed Native API flow, six required business tests, explicit compile
failure and repair, export/reconnect, cancellation and timeout, read-only and
evidence-negative cases. Both revoked temporary task authority.

After download, a standalone verifier in a separate local directory accepted
the four current/historical portable bundles and rejected both tampered bundles.
The earlier Linux epoll-lock failure remains archived separately. This result
does not claim client or G2 acceptance.

Raw evidence: `output/p263-remaining/cloud/neoforge-attempt-3/p263-installed/`.
Independent verification: `output/p263-remaining/neoforge-cloud-offline-review-attempt-3/results.json`.

The matching new Fabric candidate passed cold installation but failed warm
cancellation with `TASK_CANCEL_FAILED`: the worker needed the workspace monitor
held by the cancelling request. A separate repair releases the monitor before
waiting, retains permission/revision validation, and allocates the completion
event against the current revision after cleanup. The focused regression makes
the interrupted worker read the store before it can finish; stale and read-only
requests remain rejected. That repair requires a new Fabric replay.

## Later reproduction before cloud client acceptance

The fresh [Xorg preparation run](https://github.com/Lotulune/Copperbench/actions/runs/38363100785)
and [Wayland preparation run](https://github.com/Lotulune/Copperbench/actions/runs/38363094603)
reused this historical NeoForge candidate. Both passed cold Native acceptance
but reproduced `TASK_CANCEL_FAILED` during the warm run. The workspace-monitor
repair described above is also required for a reproducible NeoForge candidate.
Neither run reached Minecraft player acceptance. Their original failure logs
remain under `output/p263-remaining/cloud/neoforge-g1-{xorg,wayland}-client-attempt-1/`.
The earlier successful run is retained as an observed historical result;
replacement candidates must pass again before client qualification.

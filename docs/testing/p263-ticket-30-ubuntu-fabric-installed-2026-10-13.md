# P263-30 Ubuntu Fabric installed Native acceptance

Date: 2026-10-13. Installation API cell passed on Ubuntu 24.04.5 LTS x86_64.

The [cloud run](https://github.com/Lotulune/Copperbench/actions/runs/38348094021)
built commit `6647a3b1f3593e24133a02a10ddfb44389d901d0` and froze candidate
`sha256:5424f9cfa08daf92350d70418a4434eb621136fdaea7ce58eba9245b9aff4de9`.
The installed-product cell hash is
`867f63a82e673fedd71740ea83403c457e6648488978d4668189539d86c6bb45`.

The actual portable installation completed the Native SDK flow under both cache
conditions: cold started with zero dependency-cache files and took 273.542 s;
warm took 241.667 s. Both exercised creation, an intentional compilation failure
and explicit repair, the six required business GameTests in an independent host,
verified export, process reconnection, read-only refusal and negative evidence
checks. Temporary authority was revoked. The final sample JAR hash is
`85efba8002bf9ebf16a109a4b955a3a3bc34d43ddb103649761d01af442723d8`.

The downloaded raw current/historical portable ZIPs were independently checked
again from an isolated local directory with the standalone verifier: four valid
bundles returned `integrity_verified`; both tampered bundles returned exit 2.
The 2.14 GB candidate artifact remains in GitHub Actions and was not downloaded
for an additional local whole-package verification.

The overall workflow failed later in its separate bootstrap check: the package
contained Gradle 9.6.0, but the bootstrap distribution pool omitted it. Commit
`352158918794e0665f9fa30a4a493ae3a76cdbfc` fixes that defect with regression
coverage. This report claims the observed installation cell, not a successful
overall workflow or client acceptance. A newer candidate receives a separate run.

Evidence: gate receipt (`output/p263-remaining/cloud/fabric-attempt-2/p263-installed/gate-result.json`),
cell (`output/p263-remaining/cloud/fabric-attempt-2/p263-installed/cell/cell.json`),
cold run (`output/p263-remaining/cloud/fabric-attempt-2/p263-installed/cold/installed-run.json`),
warm run (`output/p263-remaining/cloud/fabric-attempt-2/p263-installed/warm/installed-run.json`),
independent portable verification (`output/p263-remaining/fabric-cloud-offline-review/results.json`).

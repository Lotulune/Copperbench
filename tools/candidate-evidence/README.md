# Scoped product candidate evidence

Ticket 27 freezes an explicitly selected loader, level and platform. It does not publish a release. A candidate records the source commit and HEAD Git tree, the actual working file bytes and executable modes, the application JAR, launcher, installer, both Python SDK modules, TypeScript SDK, generator archive and sample file inventory.

On Windows, executable modes come from the Git index; on POSIX they come from the actual files. Symlinks, junctions, submodules and unresolved merges are not accepted in the source manifest. Dirty source is recorded for local development demonstrations and cannot qualify for support. An asset digest or metadata digest establishes integrity, not producer authenticity.

## Build a product candidate

Run from the repository root with Python 3.11 or newer. Outputs and build plans must be outside the repository or in a Git-ignored directory; otherwise the source identity would include its own output.

```powershell
python tools/candidate-evidence/package_candidate.py --platform windows --loader fabric --loader neoforge --output output/p263-candidate-windows
```

```sh
python3 tools/candidate-evidence/package_candidate.py --platform ubuntu --loader fabric --output build/p263-candidate
```

The helper uses the existing Windows NSIS/ZIP or Ubuntu deb/tar packaging tasks and freezes both the installer (`package`) and archive (`portable_package`). It samples the source before and after the build, records the exact argument vectors and logs, hashes the packaged SDKs and generators, and freezes the output in `frozen/`. It never uses an old installer merely because a filename matches. Build failure or a changed source tree leaves a failed build receipt and no completed candidate. A freeze failure retains a separately named staging directory with `FAILED.json`.

The Linux candidate workflow has an optional `p263_loader` dispatch choice. Its default route and existing candidate metadata remain in place. Selecting a loader uploads the scoped candidate with all cells initially `not_run`, then runs the installed API gate below and uploads its separate receipts even on failure. The existing Xvfb preflights do not count as GNOME Xorg or Wayland player acceptance.

The package helper currently selects G1 and declares every other scope unavailable. Availability describes a maintainer-selected implementation boundary bound to the source digest; it is not a gameplay result. G2 requires a new build plan with the complete five-element implementation and the fixed thirteen-case sample.

## Installed API gate

On the selected operating system, run the frozen portable package from a new directory. Python 3.12 or newer and the repository's Node receipt validator are needed for this external harness; product processes use the packaged launcher, JDK and SDK. Ubuntu is checked against `/etc/os-release` and must be 24.04 x86_64.

```sh
npm ci --prefix ui-core
python3 tools/candidate-evidence/installed_acceptance.py \
  --candidate build/p263-candidate/frozen --platform ubuntu --loader fabric \
  --output build/p263-installed --work-root /tmp/p263-installed
```

The work directory and output directory must both be new and separate. The harness never deletes an existing run. It extracts the frozen tar/ZIP, verifies installed assets and sample payloads, then executes Native creation, controlled compilation failure/repair, six required business cases, verified export, process reconnect and twelve negative gates. Current and historical exports from the same acceptance task are independently checked using the packaged standalone verifier in a clean directory. Tampering must fail. It does not execute the deb or NSIS installer.

Cold runs have an empty dependency cache and isolated JVM/product homes; the frozen package's Gradle distribution pool may be reused. Warm runs require a successful, revoked cold receipt for the same candidate, platform and generator, and verify every retained dependency payload before execution. A failed cold run cannot supply a warm pass. Optional `--jvm-proxy-host` and `--jvm-proxy-port` affect only these test processes and are recorded; the default does not apply a JVM proxy override. Host JVM option injections are excluded, and inherited transport proxy presence is recorded without credentials.

Use `--cache-root` to place a new cold dependency cache on a different volume when local space is limited. Warm runs must retain that exact successful cold cache. On Windows the harness uses JVM single-quote syntax to preserve spaces without the shipped launcher's double-quote crash; choose a work root without apostrophes. A missing JSON receipt records the actual process exit and original streams. Cleanup always attempts log collection; if launcher revocation fails, the installed Java API attempts to revoke the same test grant, while the run remains failed and any original failure is preserved.

Each run snapshots the external harness and validators, recording their hashes alongside the versions listed in the frozen source manifest. Updating external acceptance tooling does not change the candidate ID or any installed product bytes. A reused prepared installation is checked against its frozen asset hashes for each new run; original failed evidence remains separate. Harness changes are not represented as part of the older product build.

Task authority is a visibly recorded integration fixture issued by the installed `TaskAuthorizationStore` for the authorized temporary workspace, then revoked in cleanup. Desktop authorization issuance is not exercised. The empty Native session seed and native sample authoring are explicit preparation steps. Product calls and receipts remain separate from the repository-side JSON schema validator. Failure logs, process exits, source checks and cache conditions are retained under the output directory; authorization signing keys and full caches are not uploaded.

`gate-result.json` and `cell/evaluation.json` report the actual result. Client cells remain `not_run`, and `formalSupportClaim` remains false. Preparing or unit-testing this harness does not pass an Ubuntu installation ticket; that requires a real cloud run of the selected candidate.

## General freeze interface

`candidate_evidence.py build REPOSITORY PLAN NEW_OUTPUT` runs an operator-reviewed JSON build plan. `PLAN` has `schemaVersion: "1"`, `commands` as argument vectors, `scopes`, `availability` as a scope-to-ready/unavailable map, and `assets` with unique IDs, roles and repository-relative paths. The package helper generates a concrete plan to copy and review. Neither verification nor evaluation executes commands found in an input receipt.

```sh
python3 tools/candidate-evidence/candidate_evidence.py build . output/build-plan.json output/build-attempt
python3 tools/candidate-evidence/candidate_evidence.py freeze . output/build-attempt/freeze-inputs.json output/frozen-candidate
python3 tools/candidate-evidence/candidate_evidence.py verify output/frozen-candidate
python3 tools/candidate-evidence/candidate_evidence.py evaluate output/frozen-candidate --receipt output/install.json --receipt output/client.json
```

Each selected G1 scope uses `fabric:G1:windows`, `fabric:G1:ubuntu-xorg`, `fabric:G1:ubuntu-wayland`, or the equivalent NeoForge scope. G2 uses `fabric:G2:windows` or `fabric:G2:ubuntu`; Ubuntu G2 requires both desktop sessions on the same candidate. A cell cannot mix levels.

## Cell receipts and evidence boundaries

A cell receipt has `schemaVersion: "1"`, `kind: "candidate-cell-evidence"`, `candidateId`, `cell`, `level`, `status`, `generatorId`, `observedArtifacts`, `artifactSha256`, `checks` and `evidence`. Cells are the four `install:<windows|ubuntu>:<fabric|neoforge>` and six `client:<windows|xorg|wayland>:<fabric|neoforge>` combinations. Status is `passed`, `failed`, `blocked` or `not_run`; every non-pass needs an explicit reason. Select one attempt per cell explicitly. Older candidate IDs and ambiguous attempts are rejected.

Each original evidence file has an `id`, `role`, safe relative `path`, byte count and SHA-256. Every required check must be passed and reference existing evidence IDs. `observedArtifacts` must match the relevant frozen platform, SDK and generator assets. The original `run_binding` JSON must record the candidate, cell, level, exact generator, observed asset hashes and mod JAR hash during that run; rewriting only the outer receipt cannot rebind an old run.

Install evidence roles are `portable_current`, `portable_historical`, `native_result`, `doctor`, `negative_result`, `cache_conditions` and `run_binding`. The portable roles each select one original ZIP. The trusted [portable verifier](../portable-evidence/README.md) recomputes raw XML cases, duplicates, counts and JAR hashes. G1 must use `camp-kit-native-v1` with six fixed cases; G2 must use `camp-kit-scoped-v1` with thirteen. Current and historical exports must refer to the same acceptance task and JAR. A self-reported pass or a five-of-six result cannot pass this gate.

Client evidence roles are `screenshot`, `actions`, `client_log`, `world_state`, `process_state`, `verification` and `run_binding`. The world record identifies `worldId`, `worldName`, `gameDirectory`, `clientPid`, `savedWorldId` and `reopenedWorldId`; the world IDs must agree. A passing client receipt references the selected passing installation receipt via `installationReceiptSha256` and must use its exact mod JAR. Screenshots, actions and the planner's verification record still require human/LLM interpretation under the Minecraft bridge contract; this tool checks binding and completeness, not pixel-level gameplay semantics.

The top-level table is never `passed` while any of the ten cells is missing. Each selected scope separately reports `evidenceComplete` and whether a clean source makes it `qualificationEligible`; neither field publishes support. `formalSupportClaim` and `producerAuthenticated` remain false.

Run the synthetic boundary regressions with `python -m unittest discover -s tools/candidate-evidence -p 'test_*.py'`. Real package/installation/player evidence is recorded separately in tickets 27–41.

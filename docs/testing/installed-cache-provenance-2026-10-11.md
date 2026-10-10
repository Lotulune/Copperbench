# Installed cache provenance verification

This record covers the [cache provenance phase](../roadmap/agent-readiness-cache-phase.md)
on top of `fd382b78`. Implementation was completed as a batch before the
consolidated checks below, as requested by the owner.

## Change and compatibility

Workspace setup previously used `UserFolderManager`'s property/default cache,
while Core tasks and environment reporting also considered caller environment
overrides. Setup and builds could therefore use different homes. They now share
one resolver: explicit JVM property, Copperbench environment override, standard
Gradle environment override, then the product default. Relative paths are resolved
before changing to the workspace directory. Existing JVM-isolated generator
fixtures keep their explicit property precedence.

`COPPERBENCH_GRADLE_REUSE_EXTERNAL=false` disables external distribution roots
without deleting caches. Backend and doctor reports expose the actual source and
reuse policy; missing backend facts remain unreported rather than guessed from
the doctor's environment. Neither report certifies dependency completeness.

The installed runner adds verified cold/warm cache inputs, payload manifests,
running-application identity checks and failure preservation. It now requires
`--application-sha256` from the frozen candidate as well as the package digest;
hashing whichever application happens to be installed is insufficient. The
supplied source commit is still identified as an operator declaration. Existing
sealed study scripts, packages and records are unchanged.

## Consolidated local checks

Windows 11, product JBR 25.0.3, Python 3.13.14 and the repository Gradle wrapper.
Evidence is retained under `build/cache-provenance-phase` in the development
worktree.

| Check | Observed result |
| --- | --- |
| Installed-run and study Python regressions with `ResourceWarning` treated as an error | 25 passed, including real symlink rejection, candidate mismatch, cold/warm tampering and failed-bootstrap environment restoration |
| Focused Java cache, process, pool, doctor, environment and platform classes | 31 passed, 1 failed, 0 skipped; all newly added cache tests passed |
| Separate JVM configuration/pool probe | Passed: setup and backend chose the same requested home; no external roots were consulted or distribution files seeded |
| Existing offline-process regression, focused recheck | Still failed locally before the child started, with Windows `CreateProcess error=5` launching `cmd.exe` |
| Javadoc | Passed separately after the test task stopped its combined invocation |
| CI selection Python suite | 19 passed; 2 errored creating newline/tab filenames, which this Windows filesystem does not support; the full suite remains in Linux CI |
| Markdown links and diff whitespace | Passed |

The offline regression was not relabeled passed. A separate minimal Java program,
without Copperbench classes, also failed to start the same harmless batch through
both bare and absolute `cmd.exe` paths. PowerShell's process API executed the
same batch normally. This isolates the observed rejection to Java child startup
on this host, but does not establish its underlying OS cause. No system security
or network settings were changed. First-run XML and the focused failure are kept.

The existing Windows CI job now includes cache-policy, separate-process and
Fabric process regressions, retaining its test XML artifact. Linux CI runs the
complete Java and CI-selection suites plus all installed-run Python regressions.
Current-head hosted results belong on [draft PR #100](https://github.com/Lotulune/Copperbench/pull/100);
the local table above is not a claim that those later runs passed.

## Installed acceptance boundary

No new Minecraft or installed cold/warm replay was run for this source check.
Both test VMs were observed saved with zero assigned memory; the host had about
2 GiB available, below either guest's 4 GiB startup memory. No guest was started
and no host focus was taken. Actual installed cold/warm acceptance still requires
the new candidate, an existing scoped authorization and the
[documented paired runs](agent-readiness-m3-task-card.md#cold-and-warm-gradle-cache-runs).
Old warm-cache player evidence remains bound to its original candidate.

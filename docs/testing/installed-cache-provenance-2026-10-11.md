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

## Hosted verification and fixture correction

For `d8cca505`, [required CI](https://github.com/Lotulune/Copperbench/actions/runs/38074060359)
passed all four jobs, including Java/Javadoc, full Chromium, selection and Windows
MCP. Downloaded Windows XML records 12/12 Fabric process cases, 3/3 cache policy
cases and the separate-JVM cache probe passing, without failures or skips. The
local Java child-start rejection did not reproduce on that clean Windows runner;
its underlying host cause remains unresolved.

The same source's [Linux candidate run](https://github.com/Lotulune/Copperbench/actions/runs/38074060464)
failed its packaged headless bootstrap step. Its runner environment included
`GRADLE_USER_HOME=/home/runner/.gradle`, but the fixture asserted that the default
XDG cache was populated. The new shared resolver correctly honors the explicit
override. The original run retained neither the bootstrap stdout nor the failed
assertion, so the logs establish the conflicting fixture environment, not which
individual assertion failed.

The fixture now removes inherited Gradle home/reuse overrides and sets the JVM
user home for its default-cache check. The graphical shell fixture explicitly
selects its isolated cache, matching the existing runClient fixture. Bootstrap
stdout, stderr and a shallow directory inventory are uploaded on success and
failure; failed shell assertions are identified in the job log. The original
default-cache/distribution assertions remain in place, and no cache payload is
included in this diagnostic artifact. Follow-up candidate results belong on
[draft PR #100](https://github.com/Lotulune/Copperbench/pull/100).

The corrected bootstrap step passed on `a07d8995` in
[run 38075315603](https://github.com/Lotulune/Copperbench/actions/runs/38075315603).
Its downloaded JSON reports `status=succeeded`, and the directory artifact shows
the isolated XDG cache. The original checks for all three seeded Gradle versions
passed with the step. The enclosing candidate workflow was later cancelled when
a newer commit superseded it, so this establishes the bootstrap correction, not
a complete candidate workflow pass.

## Completed hosted source checks

For PR head `5e40d0b048f306b4955aa9877b71d1da6c3a077c`, all three hosted
workflows completed successfully. Required CI and the Linux candidate checked
out merge commit `32b0b7bc6dc5effcf23881eb724a632a3a956f74`; M1 checked out the
exact PR head. Local Git comparison confirms both commits have tree
`34b3a4381cc8be5455f86ac53ed3c90759ade8eb`, with no file differences.

| Workflow/layer | Observed result |
| --- | --- |
| [Required CI](https://github.com/Lotulune/Copperbench/actions/runs/38078554306): Java and Javadoc | 1,122 cases: 1,053 passed, 69 skipped, zero failures; all eight wrapper-copy tracks and the new cache cases passed |
| Required CI: full Chromium | 277 passed |
| Required CI: Python SDK | 72 passed on each of Linux and Windows |
| Required CI: CI selection / installed-study Python | 21 / 25 passed, including the two POSIX filename fixtures that cannot run on this Windows filesystem |
| Required CI: Windows filesystem selection | 47 passed, 1 POSIX-permission case skipped, zero failures; all Fabric process/cache cases passed |
| Required CI: MCP conformance | 8 checks, zero failures |
| [Independent M1](https://github.com/Lotulune/Copperbench/actions/runs/38078554385) | Both jobs passed; 36 Java/72 Python protocol cases; real delivery, five business GameTests, seven negative exports, genuine reconnect and final reopen independently audited |
| [Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/38078554319) | Isolated bootstrap, JCEF, Fabric/NeoForge 1.21.1 X11 render preflight, packaging, SBOM and provenance upload passed |

The [M1 record](agent-readiness-m1.md#hosted-closure-on-5e40d0b0) binds the actual
tasks and final Mod JAR/report/input digests. These hosted results do not resolve
the earlier local Java child-start denial or convert any superseded run to a pass.

## Frozen Linux candidate

Artifact `11680081123` from run `38078554319` was downloaded into
`build/cache-provenance-phase/frozen-linux-candidate`. The repository metadata
verifier recalculated all four asset digests and the candidate ID against actual
merge source `32b0b7bc6dc5effcf23881eb724a632a3a956f74`.

- Candidate ID: `sha256:d250a078176625db248346f94568a2897008dda858c836b3d19edc06f6b7bb7f`.
- Portable SHA-256: `4e00a9de833d0ab3a17be29e77cfcaae24fd34d713b3f01f49ba6db490119aad`.
- Debian SHA-256: `17366606c8a0f08d7586631dbb850960e4e2c24b1ebf137b2be8445ad7164396`.
- Application JAR SHA-256, independently extracted from both packages:
  `757302cef35293a4d74bdc6294dfc792c1b47cc9e23c97c3822554b97faa26f7`.

The matching application files are 7,801,588 bytes. `application-identity.json`
records their archive member names and the package identities. GitHub's outer
artifact ZIP digest was advertised by the API, but that ZIP was not retained or
independently rehashed; the verified package digests above are separate evidence.
The private candidate retains its development support/promotion limits.

## Windows packaging follow-up

The same merge source produced a Windows portable ZIP, but installer preparation
failed because `build/tools/nsis/makensis.exe` did not exist. The NSIS distribution
had extracted into `nsis-3.12`; its unchecked directory rename had not completed,
while the plugin populated a separate `nsis` directory. A subsequent directory
existence check treated that partial tool cache as ready.

Setup now copies the archive contents directly to the final tool directory,
checks compiler and plugin files independently, and rejects a missing required
file after extraction. A real Gradle fixture reproduced the original missing
compiler failure. After the change, all four isolated scenarios passed: compiler
recovery with an existing plugin, plugin recovery with an existing compiler,
complete-cache reuse without downloads, and rejection of a malformed compiler
archive. The fixtures use local ZIPs, execute the actual setup task and retain
their source copy and logs. Windows CI runs
`pwsh -NoProfile -File scripts/test-nsis-setup.ps1` and uploads those receipts.

The original failed packaging log and portable bytes remain under
`build/cache-provenance-phase`; a complete installer build is recorded separately
when executed. This setup-only correction does not alter the frozen Linux bytes.

## Installed acceptance boundary

No new Minecraft or installed cold/warm replay was run for this source check.
During the initial source checks, both VMs were saved with zero assigned memory; the host had about
2 GiB available, below either guest's 4 GiB startup memory. No guest was started
and no host focus was taken. Actual installed cold/warm acceptance still requires
the new candidate, an existing scoped authorization and the
[documented paired runs](agent-readiness-m3-task-card.md#cold-and-warm-gradle-cache-runs).
Old warm-cache player evidence remains bound to its original candidate.

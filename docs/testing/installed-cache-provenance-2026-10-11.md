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

## Initial installed acceptance boundary

No new Minecraft or installed cold/warm replay was run for this source check.
During the initial source checks, both VMs were saved with zero assigned memory; the host had about
2 GiB available, below either guest's 4 GiB startup memory. No guest was started
and no host focus was taken. Actual installed cold/warm acceptance still requires
the new candidate, an existing scoped authorization and the
[documented paired runs](agent-readiness-m3-task-card.md#cold-and-warm-gradle-cache-runs).
Old warm-cache player evidence remains bound to its original candidate.

## Refreshed candidate and hosted checks on 1dbaf930

The NSIS repair was built from clean head
`1dbaf930907500be3b834997d3c29824d5bbea88`. The complete Windows portable ZIP
and installer build passed in 4m 5s. All three hosted workflows also passed:

- [Required CI](https://github.com/Lotulune/Copperbench/actions/runs/38081040573):
  Java 1,053 passed / 69 skipped / zero failed; Chromium 277 passed; Python SDK
  72 per OS; CI selection 21 and installed/study Python 25 passed. The four
  NSIS setup cases passed on hosted Windows as well as locally.
- [Independent M1](https://github.com/Lotulune/Copperbench/actions/runs/38081040543):
  protocol 36 Java / 72 Python; the real delivery retained five business cases,
  seven negative export refusals, minimum-six/actual-five rejection, actual
  reconnect and final reopen/export. The downloaded raw evidence was independently
  audited; delivery elapsed 437.230 seconds. Final Mod JAR SHA-256:
  `cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e`.
- [Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/38081040535):
  isolated bootstrap, JCEF, Fabric/NeoForge X11 preflight and packaging passed.
  Its actual merge source, also used by required CI, is
  `006a4be97a86b8795ceec035e25ed5ff54b15567`. Its Git tree exactly equals the
  Windows/head tree `e2049068cea956bf83fbf0787e2e4ffcae68556d`.

The Windows installer SHA-256 is
`d32448e08c71d508620fd205220cbd2488c9f987acd48e54ad19c7742a5291dc`;
portable ZIP `75da02525519b59b8aa45a9994069ad7c7ac7cea4a80e8c002516b7c5cffb8dd`;
application JAR `c1302cb5e1c3830e97c97e822ddc51bea785fdef8a8f076a62ace45f82ad4e82`.
The unsigned private installer completed in the isolated Windows 11 guest with
exit 0; installed JAR bytes match that frozen identity.

Linux artifact `11680547841` was downloaded and its four asset digests and
candidate ID recalculated. Candidate ID:
`sha256:65770e2780a1a5e3ada8bf35b14bddd538b73d84c1784e4eeefecbbeefd4603e`;
Debian SHA-256 `1c3e623e116a4c7e6cc7f47c6db8f0413479a38248b5baf2026141e5dee6e8f4`;
portable SHA-256 `4e00a9de833d0ab3a17be29e77cfcaae24fd34d713b3f01f49ba6db490119aad`.
Independent extraction from both packages yields application JAR
`757302cef35293a4d74bdc6294dfc792c1b47cc9e23c97c3822554b97faa26f7`.
The outer GitHub artifact ZIP was not independently rehashed. This candidate has
not yet been installed in the Ubuntu guest.

## Windows installed failures and launcher correction

Three fresh-cache attempts on the installed `1dbaf930` candidate are retained;
none qualifies as a successful cold replay or a warm-run prerequisite:

| Attempt | Observed terminal result |
| --- | --- |
| `cold-001` | Bootstrap failed downloading official Gradle 9.7.0 with a connection timeout; no business task ran |
| `mirror-cold-001` | Product mirror mode downloaded Gradle/Fabric dependencies, then failed resolving Minecraft libraries; the daemon log records BMCL DNS failure |
| `dns-cold-001` | With a temporary resolver in the isolated guest, official-source workspace creation completed, but the running-application identity check rejected the session before business tasks |

The last attempt began at `2026-10-10T20:49:15Z` and failed after 254.896 seconds.
Its environment reported application SHA-256
`01b503a712807479c0cd2f9589c748182f680bfdefb568d6ef5147afa921583f`, which is the
frozen **launcher EXE** hash, rather than the installed application JAR hash.
Launch4j embedded a second application copy in the EXE and loaded classes from
that container. The guard correctly refused to treat these different bytes as
the same application.

Windows packaging now leaves the JAR external, selects `lib/copperbench.jar`
explicitly before other libraries and uses the matching Launch4j library path.
The packaged runtime consequently has one application copy to identify.
[The launcher regression](../../scripts/verify-windows-application-identity.py)
runs the actual EXE's read-only doctor with local metadata, compares its reported
digest to the shipped JAR, and checks that metadata/profile/product files remain
unchanged. Windows CI builds the exported layout and retains its raw response.
This check does not replace installed cold/warm delivery acceptance.

The local exported Windows build passed in 37 seconds. In the isolated guest,
the new regression reproduced the old launcher's mismatch, then passed against
the corrected launcher in a separate product path containing spaces. Its reported
JAR hash exactly matched `521407f2f186bbe0bc765d0a9980c7ff60b7d311f99aa2c0a5a09f8dddcb2b59`;
metadata stayed unchanged and no profile was created. That focused fixture used
the earlier installation's JBR through a guest-local junction; it establishes
launcher behavior, not installation of a new candidate. Both raw results are
retained as `windows-launcher-before` / `windows-launcher-after`.

The temporary Windows guest DNS was restored to its original DHCP value at
`2026-10-10T20:59:08Z`, after all replay processes exited. Both guests were saved
with zero assigned memory. No host focus, user application or host proxy setting
was changed. Raw attempts, package identities and DNS restoration remain in
`build/cache-provenance-phase`. The next installed pair requires a newly frozen
Windows candidate containing the launcher correction; the three failures above
are not overwritten or retried under their original IDs.

## Corrected candidate acceptance on 298846dc

Product source `298846dccbec03d02c94735ad69ecce70128b0a1` contains the Windows
launcher correction. The complete Windows ZIP/installer build passed in 5m 27s,
with clean source at both ends. The later documentation changes do not alter
these frozen candidate identities.

| Candidate | Frozen identity |
| --- | --- |
| Windows installer | `8857dcfe81b040fb75fc427aab2830f4ccc08496335aab97a41d40394eb2942c` |
| Windows portable ZIP | `0a10841e3daf1cf1eafee390cf4b0a2d1ce72e01f3391ec966c4a6352358fcd7` |
| Windows application JAR | `521407f2f186bbe0bc765d0a9980c7ff60b7d311f99aa2c0a5a09f8dddcb2b59` |
| Windows launcher EXE | `1f3ad01ce78f05521bf93b61515d8dc75b120708edf4c9553a95ad37a91a18ec` |
| Linux candidate ID | `sha256:d88fae48871b01041986c6d6b70effaf27bc3f12c8ba47baae33132048aeac67` |
| Linux Debian package | `6cf69e089528b0f0e66b2015cdca30576d31b373fc3a27781b473e8cd464c4f4` |
| Linux portable archive | `d7ea510174e1a60ffa45d9a7ed98b42dd0b81189efa6a0dace3d5d42d832e1bc` |
| Linux application JAR, independently extracted from both formats | `be7fdd77e395564d90b0d6674cb39197f22dc202146cc1776913aa43037cea8d` |

All three workflows passed:
[required CI](https://github.com/Lotulune/Copperbench/actions/runs/38086281375),
[M1](https://github.com/Lotulune/Copperbench/actions/runs/38086281382) and
[Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/38086281377).
Required CI reports Java 1,053 passed / 69 skipped / zero failed, Chromium 277,
SDK 72 per OS, CI selection 21 and installed/study 25. The four NSIS cases and
new actual Windows launcher identity check passed; the latter's raw doctor
response was downloaded and checked. M1's raw five business cases, seven refusal
receipts, real reconnect and final export/reopen passed independent review;
delivery elapsed 431.003 seconds.

The Linux candidate and required CI used merge source
`6349bb783a04369e51d9e973d8037de85af61f70`, whose tree exactly matches the product
head (`f636dd2afc70a08fa029ac2481904caff80d3b4b`). Linux artifact `11681829037`
passed all four asset hashes and candidate-ID checks. The outer artifact ZIP
digest is GitHub-advertised only. Both candidates were installed in their
respective isolated guests with installer exit 0 and matching application bytes.
Neither candidate was published or promoted.

### Windows installed cold/warm pair

| Replay | Measured cache condition | Elapsed | Business acceptance |
| --- | --- | --- | --- |
| `cold-001` | Empty before launch; 1,151 retained payloads afterward | 477.907 s | 5 passed, 0 failed, 0 skipped |
| `warm-001` | All 1,151 cold payload identities matched before launch | 281.511 s | 5 passed, 0 failed, 0 skipped |

Each run completed discovery, initial build, intentional compile failure and
located diagnostic, explicit repair, packaged-JAR tests, current-input export,
actual process reconnect/reopened export and refusal to overwrite an external
source edit. Three distinct SDK sessions per replay reported the frozen JAR
digest, the selected Gradle home and disabled external-distribution reuse.
Independent review checked original evidence hashes, all seven task terminal
states, exported JAR/XML bytes, the five named XML cases, preserved source bytes
and the cold/warm manifests. Both exported Mod JARs have SHA-256
`cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e`.

The pair used a separate product profile, 1,024 MiB Gradle heap, official sources
and offline/mirror settings disabled. Temporary DNS `223.5.5.5` in the Windows
guest was restored to the original DHCP value at `2026-10-10T21:32:40Z` after
the replay processes exited. Thus these are measured cold/warm results under
the recorded resolver configuration, not proof of the unmodified default
network or offline builds. Windows was saved before Ubuntu resumed.

Evidence roots are `build/cache-provenance-phase/windows-298846dc-cold-001` and
`windows-298846dc-warm-001`, each including `independent-audit.json` and copied
export/report/source bytes. Installation transport interruption and the stale
numeric-interface preflight error are retained separately; neither started a
delivery replay. The actual pair was not restarted or relabeled after a failure.

### Ubuntu installed cold/warm pair

The first default-resolver `cold-001` attempt failed after 65.097 seconds while
downloading the official Gradle distribution (`Read timed out`, 60-second download
timeout). It ran no business task and remains preserved in
`build/cache-provenance-phase/linux-298846dc-default`. A fresh cache and new
workspace/output IDs were then used with temporary guest DNS `223.5.5.5`:

| Replay | Measured cache condition | Elapsed | Business acceptance |
| --- | --- | --- | --- |
| `dns-cold-001` | Empty before launch; 1,135 retained payloads afterward | 306.052 s | 5 passed, 0 failed, 0 skipped |
| `dns-warm-001` | All 1,135 cold payload identities matched before launch | 132.368 s | 5 passed, 0 failed, 0 skipped |

Both runs passed the same full installed-product task and independent raw-evidence
audit as Windows: compile fault/repair, current packaged-JAR acceptance, export,
real reconnect, and preservation of the external source edit. Their exported
Mod JAR digest is the same `cbeb70e1…e08dd3e` reported above. The bundled SDK
SHA-256 is `4dbce6a46f9bb40a5230d44b80a94952f4206e3155c833dd14b50ceb35c134f2`
on both platforms; the sealed harness and fixture files were unchanged.

Ubuntu used a new product profile with a 1,024 MiB Gradle heap, official sources,
offline/mirror settings disabled and a real four-hour approval limited to
create/edit/build/test in the test directory. The older study grant was not
reused. The initial operator preflight's Windows path-separator error was fixed
before the first replay started; its original stderr remains available. The
existing isolated X11 display served the product bootstrap environment. These
scripted API checks did not launch a Minecraft client or measure agent success.

Raw runs, original evidence manifests, copied exported JAR/XML and protected
source bytes are under `build/cache-provenance-phase/linux-298846dc-dns`.
The original Linux resolver was restored at `2026-10-10T21:51:11Z`; its persistent
network configuration was unchanged. Cleanup confirmed zero owned product/replay
processes and saved both VMs with zero assigned memory. Host focus, host proxy
settings, user applications and original product profiles were untouched.

This closes the installed cache provenance development phase for the frozen
`298846dc` candidates and recorded environments. The default-network failures
remain failures; previous player/desktop evidence retains its earlier candidate
identities. The owner-ended agent study stays closed, and unfamiliar-user
research remains deferred until participants are available.

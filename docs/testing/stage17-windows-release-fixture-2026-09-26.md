# Windows release fixture correction — 2026-09-26

The first `v0.1.1` Windows release [run 36246520455](https://github.com/Lotulune/Copperbench/actions/runs/36246520455) stopped in its full Java test gate: 976 tests completed, one failed and 51 were skipped. No public Release or installable payload was produced. The explicit native exit-code guard prevented subsequent successful commands from masking the failed suite.

The sole failure was `BlockbenchProcessServiceTest.taskLaunchUsesItsEditingCopyAndExitDoesNotCommitOrImport`: its fake lifecycle returned the original JUnit temporary path, while `AssetWorkspaceService` canonicalizes its root with `toRealPath()`. A Windows 8.3 temporary-directory alias therefore did not equal the expected canonical editing-copy path. The real lifecycle uses `BlockbenchModelingService.editingFile`, derived from the canonical asset root, and does not return that fake path.

The failure was reproduced locally against the signed source by setting only the test JVM's `java.io.tmpdir` to a real 8.3 path. Normalizing the test fixture workspace fixes the same reproduction: the complete class passes 11 cases, with one opt-in real-Blockbench case still skipped. Product code, path containment checks, symlink/redirect rejection and process behavior are unchanged. [Machine-readable red/green summary](../../evidence/stage17/2026-09-26/release-short-path-repro.json).

The Linux frozen candidate source remains `bef9a7ee72b048a040ec3f7ad0ff8913f92382fa`. The release-only delta allowlist now permits this one test fixture file; other product and packaging drift remains rejected. The existing candidate binaries and their metadata are unchanged and still need installed acceptance before Linux publication.

The signed `v0.1.1` tag remains at the failed-test source until the user explicitly authorizes archiving and re-signing that unpublished tag, or chooses a new release version. The old August `v0.1.0` tag is outside this correction.

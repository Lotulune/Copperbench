# 0.1.9 Windows path-alias export correction

The 0.1.8 Windows release gate stopped before building or publishing packages:
[run 38886381793](https://github.com/Lotulune/Copperbench/actions/runs/38886381793)
had 1,445 tests, one failure and 64 opt-in skips. The real Gradle archive test
failed the workspace containment check. Windows supplied the temporary root
through an 8.3 alias, while Gradle's receipt used its canonical long path.
The [local diagnostic](../../evidence/maintenance/2026-10-16/export-path-019/short-path-reproduction.json)
reproduces that mismatch using the same canonicalization as the Gradle script;
it is an explicitly simulated receipt probe, not a gameplay test.

Archive validation now compares physical root/artifact paths after rejecting
links, then returns the artifact using the caller's workspace path spelling.
The existing delivery boundary therefore sees a consistent path. Files outside
the workspace build tree, symbolic links and changed archive bytes remain
rejected. Real Gradle regressions cover both ordinary and forced Windows short
paths, repeated exports and changed payload bytes; boundary regressions cover
outside and linked archives.

The configuration-cache and legacy data-pack fixes from [0.1.8](maintenance-release-0.1.8.md)
are retained. Its signed source tag and failed Windows run remain unchanged;
the independently verified Linux 0.1.8 candidate was not promoted. The last
public binary version remains 0.1.7 until 0.1.9 publication completes.

Focused verification passed: 15 Java checks with no skips/failures, including both real-Gradle path variants and outside/linked archive rejection; 50 UI-Core contracts; three release-history checks; product status, Markdown links and the JAR/UI build. The [validation receipt](../../evidence/maintenance/2026-10-16/export-path-019/local-validation.json) binds results and source hashes. The [same short-path diagnostic after the fix](../../evidence/maintenance/2026-10-16/export-path-019/fixed-short-path-probe.json) now resolves the artifact using the caller's path spelling.

Final source CI, signed packages and public-package cloud acceptance are pending. Windows 11/GNOME desktop interaction and player world
save/reopen remain separate from Native Core and GameTest automation.

# Windows 11 native desktop acceptance

Recorded 2026-10-12 (Japan time). **The selected REM-02/03/06 desktop flow passed after two product fixes.**
The user made the Windows desktop available for this run. Build, real packaged GameTest,
language/theme changes and current/historical exports were operated in the native JCEF window.
The final candidate passed five business tests and exported a bundle that independently verifies offline.

This is Windows 11 **portable frontend** acceptance. The separate
[cloud installation record](remaining-cloud-acceptance-2026-10-12.md#desktop-fix-cloud-follow-up)
covers NSIS on Windows Server 2025 and Debian installation on Ubuntu 24.04.
No Windows 11 NSIS installation or Minecraft player/save-reenter interaction is inferred.

## Candidates and environment

| Candidate source | Application JAR SHA-256 | Observed scope |
| --- | --- | --- |
| `48cd994ac22b6a2bf6f0b30b13457250b0c59745` | `5d5916aec08585a0992298decd834f6b8eadb8bcedae40c1d763a464b1f25f58` | Original frozen portable package; GUI initialization caused a false source conflict |
| `f1ee111234554462b4dcf75228374ab5aa313a03` | `1362d8e4a6deadf64e2c8586d8f4102988672646f068d2bfbdb6370acb66aa69` | Initialization fix; native build, negative/positive GameTest, language, historical export and close passed |
| `82d0dc34afe476962d2766be203f228900714303` | `fa76a7593c97bcec914a02b4b56f2cdd85a2940c39f42ded9cdc6653b6dbb6b8` | Dark select fix; reopen, native menu readability, real five-case GameTest, current export and close passed |

The [final manifest](../../evidence/remaining-desktop/2026-10-12/final-candidate-manifest.json)
binds source tree `68e064d3b3c4c7b7ebc76f500c636783bbc0f96a` to the rebuilt application.
It uses the original frozen Windows ZIP with SHA-256
`9f64fc888fccd6f557fae0db58feb1f032fb0568208455d25f923798a1b27279`;
the other 13 frozen members are unchanged. These local replacement JARs and the separately built
cloud packages have different identities and are not interchangeable evidence.
The build command was `scripts/run-gradle-external.ps1 --no-daemon jar -Pbuilddate=20261012`.

The host is Windows 11 Pro `10.0.26100`, a native desktop session at 120 DPI.
The final application runs from `D:/CB-Desktop-20261012/final/Copperbench014`, with a separate
profile and the prepared `M1 Delivery` Fabric 1.21.1 workspace. The viewport checks used
1600×974 and 1366×768. Caches were retained; official sources were selected and host networking
was unchanged. See the [environment](../../evidence/remaining-desktop/2026-10-12/final-environment.json).
This is not a cold-cache or interactive workspace-bootstrap measurement.

## Product defects found and corrected

The original GUI dependency setup called base generation while Core still held pending generation
metadata. It rewrote 15 prepared source files, so the first GUI build correctly rejected the now
mismatched hashes with `GENERATION_SOURCE_CONFLICT`. Task
`f09581f3-c7ba-4eac-9f1c-85e78843f313` remains a failed product observation in the
[failure record](../../evidence/remaining-desktop/2026-10-12/old-candidate-failure.json) and
[native screenshot](../../evidence/remaining-desktop/2026-10-12/screenshots/old-source-conflict.jpg).

Commit `f1ee1112` changes
[dependency setup](../../src/main/java/net/mcreator/generator/setup/WorkspaceGeneratorSetup.java)
to preserve pending generation for the protected Core task, while retaining normal base generation
when no pending metadata exists. The focused regression includes both routes and verifies that
external edits still fail protection. After replacement GUI initialization,
[all 21 prepared source files retained their bytes](../../evidence/remaining-desktop/2026-10-12/startup-source-preservation.json).
The GUI build then succeeded.

JCEF's native select popup also rendered unselected options with insufficient contrast under the
dark theme, despite the existing `color-scheme`. Commit `82d0dc34` explicitly gives
`select option` and `select optgroup` the theme's text and input background colors in
[global.css](../../ui-shell/src/styles/global.css). The original
[popup capture](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-native-options.jpg)
and final [native popup](../../evidence/remaining-desktop/2026-10-12/screenshots/final-native-options.jpg)
retain the observed difference. Both Chinese and English unselected options were inspected.

## Actual desktop checks

Full task IDs, terminal states, machine reasons, case names and JAR/XML/input hashes are retained
in the [result record](../../evidence/remaining-desktop/2026-10-12/result.json).

| Check | Observation and evidence |
| --- | --- |
| Build after first GUI initialization | Task `589e0a51` succeeded; pending prepared source remained intact during GUI setup |
| Running result and language changes | Chinese/English show unknown counts as `—`; the same task set, workspace and revision 3 remain. [Chinese](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-running-chinese.jpg), [English](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-running-english.jpg), [invariants](../../evidence/remaining-desktop/2026-10-12/running-language-invariants.json) |
| Real 5/6 refusal | Task `360ddd7e` failed with `insufficient_business_tests`; five discovered/executed/passed, zero failed/skipped, minimum six and shortfall one. No verified-export action. [English](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-negative-english.jpg), [Chinese dark](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-negative-chinese-dark.jpg), [Chinese light](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-negative-chinese-light.jpg) |
| Terminal language changes | Task and versioned result objects, task set and revision remain unchanged. [Comparison](../../evidence/remaining-desktop/2026-10-12/terminal-language-invariants.json) |
| Real 5/5 and current export | On `f1ee1112`, task `7f81934c` passed and GUI export `12e6d56e` succeeded; the exported bundle verified offline |
| Changed input and default refusal | A controlled edit makes the previous acceptance historical. Task `ad9eaa5f` rejects export with `VERIFICATION_INPUT_CHANGED`. [Historical result](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-historical-result.jpg), [refusal](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-stale-export-refused.jpg) |
| Explicit historical export | Selecting the historical-export checkbox permits task `31c3defe`; bundle retains `passed_historical_input`. [Screenshot](../../evidence/remaining-desktop/2026-10-12/screenshots/f1ee-historical-export.jpg), [offline result](../../evidence/remaining-desktop/2026-10-12/historical-offline-verification.json) |
| Close and reopen | Same workspace, revision and six task IDs; every original task field retained, with only `restoredFromHistory: true` added. [Comparison](../../evidence/remaining-desktop/2026-10-12/reopen-invariants.json) |
| Final candidate GameTest | Task `41431cd4-d28d-4cf7-a9ba-3991669659fd` passed on application `fa76a759…`: five executed/passed, zero failed/skipped. [English](../../evidence/remaining-desktop/2026-10-12/screenshots/final-passed-english.jpg), [Chinese](../../evidence/remaining-desktop/2026-10-12/screenshots/final-passed-chinese.jpg) |
| Final candidate export | GUI task `d6006bc2-30fe-4196-92c7-16025921bb49` succeeded with `passed_current_input`. [Screenshot](../../evidence/remaining-desktop/2026-10-12/screenshots/final-current-export.jpg), [offline verification](../../evidence/remaining-desktop/2026-10-12/final-offline-verification.json) |
| Final normal shutdown | GUI Close completed, the workspace was saved, the log reached `Exiting Copperbench`, and no final-candidate launcher/javaw process remained. [Receipt](../../evidence/remaining-desktop/2026-10-12/shutdown.json), [log excerpt](../../evidence/remaining-desktop/2026-10-12/shutdown-log.txt) |

The fixture was prepared through the bundled SDK before opening the GUI. Switching minimumTests
from six to five and temporarily editing the fixture's `recovery_probe.java` were explicit test-input
preparation, not claimed desktop editing actions. The Java bytes were
[restored exactly](../../evidence/remaining-desktop/2026-10-12/historical-input-restored.json)
before the final candidate run. The retained overview failure banner refers to the deliberately
failed historical-export task; the selected final task and receipt record the later successful export.

## Portable evidence and verification

The final [five-file delivery](../../evidence/remaining-desktop/2026-10-12/delivery-current/manifest.json)
contains the actual 40,782-byte mod and original GameTest XML. Its mod hash is
`e08cacd0e7fea6464424a0ec9cef4ca84833fe521ec631202eacea8164836285`; its raw report hash is
`e22a6a0319d259fd0f1ed68444826926d9d2e4acad23e7fb337691bced4c0f5e`.
Application identity records `fa76a759…`, separately from the mod hash.
The [historical bundle](../../evidence/remaining-desktop/2026-10-12/delivery-historical-f1ee1112/manifest.json)
belongs to the earlier local application `1362d8e4…` and keeps that identity.

Recheck the retained bytes without starting Copperbench:

```text
python scripts/verify-delivery-bundle.py evidence/remaining-desktop/2026-10-12/delivery-current
python scripts/verify-delivery-bundle.py evidence/remaining-desktop/2026-10-12/delivery-historical-f1ee1112
```

Both bundles return `consistent`, with their distinct input-status labels and five passing business
cases. This checks consistency, not authenticated provenance or a signature.
The [inventory](../../evidence/remaining-desktop/2026-10-12/inventory.json) gives byte hashes and
actual dimensions for the committed evidence. Screenshots retain the original JPEG bytes; their
archived extensions reflect the returned format. Full local observations, task envelopes, logs,
frozen JARs and packages remain in `output/desktop-acceptance-20261012/`.

| Source verification | Result |
| --- | --- |
| Generation preparation regression | Nine passed, zero failed/errors/skipped; Javadoc and application JAR build passed |
| UI unit and production build | 32 unit tests passed; Chinese/English localization gates and production build passed |
| Affected Chromium checks | Nine theme/localization cases passed using isolated port 5189 |
| `f1ee1112` required CI | [38179860974](https://github.com/Lotulune/Copperbench/actions/runs/38179860974): passed |
| `f1ee1112` real M1/protocol | [38179864313](https://github.com/Lotulune/Copperbench/actions/runs/38179864313): passed |
| `f1ee1112` Windows/Ubuntu installation | [38179867767](https://github.com/Lotulune/Copperbench/actions/runs/38179867767): both passed; 78 independent downloaded-evidence checks per platform |
| `82d0dc34` required CI | [38181079774](https://github.com/Lotulune/Copperbench/actions/runs/38181079774): all required jobs passed |

The first extraction under the long worktree path failed during fixture preparation; using the
short task directory resolved that harness boundary. Early inspection mistakes (the recent-task
key, text decoding and assuming no history-restoration field) were corrected against the actual
receipts. The first Playwright attempt reused an unrelated server on port 5173 and was stopped;
the valid run used isolated port 5189. Original local failed outputs remain separate evidence and
do not count as passed product runs.

The final CSS-only source change has local native desktop and required-CI evidence; cloud
installation evidence remains bound to `f1ee1112`. Minecraft player actions, separate installed
MCP/TypeScript sessions and interactive bootstrap approval are outside this desktop result.
No performance improvement is claimed from input/capture timing. REM-04/05 and the cancelled
research studies remain outside the selected batch. This work does not merge main or publish a release.

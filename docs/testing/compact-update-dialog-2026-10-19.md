# Compact application update dialog — 2026-10-19

The application update dialog still reserved five text rows in 0.1.11. A one-line preparation message therefore left a large blank area above the progress bar. The correction measures the wrapped text, caps long content in a scroll pane, and groups the heading, message and progress meter with tighter spacing. The update/download/install control flow is unchanged.

## Native presentation evidence

The preview used the production `ProductUpdateDialog` class with an isolated profile and simulated update states. Its title still shows the then-current 0.1.11 metadata; the same corrected dialog source is included in the 0.1.12 release preparation. The [receipt](../../evidence/maintenance/2026-10-19/compact-update-dialog/preview-verification.json) binds that source to SHA-256 `5f653e36661dc9860bf567836b2dafe444811897d55c383f8710ca040bae0c15` and records the screenshot hashes.

| State | Observed result |
| --- | --- |
| Preparing, Chinese/light, JVM scale 1.25 | [Preview](../../evidence/maintenance/2026-10-19/compact-update-dialog/preparing-zh-light.png): the status text occupies one line, with the progress meter immediately below. The measured window is 474×203 logical units and the text area is 21 units tall. |
| Available update, English/dark, JVM scale 2 | [Preview](../../evidence/maintenance/2026-10-19/compact-update-dialog/available-en-dark-200.png): the longer description wraps naturally; the window is 468×231 logical units and both actions remain visible. |
| Failure, English/light, JVM scale 2 | [Preview](../../evidence/maintenance/2026-10-19/compact-update-dialog/error-en-light-200.png): the error summary and all three actions fit in the 468×180 logical-unit window. |

Screenshots were inspected through the Windows computer-use controller. Preview Cancel/Close actions were exercised and the isolated processes exited. The fixture does not access the network or install files; its primary download/install handlers are disabled. These observations establish presentation coverage, not actual update lifecycle acceptance.

## Source checks

- The initial [four Java checks](../../evidence/maintenance/2026-10-19/compact-update-dialog/preview-java-checks.log) passed after the layout change, together with the production UI build.
- After the 0.1.12 metadata update, [five Java checks and the production UI build](../../evidence/maintenance/2026-10-19/compact-update-dialog/release-version-checks.log), [50 UI-Core checks](../../evidence/maintenance/2026-10-19/compact-update-dialog/release-ui-core.log), [68 UI unit checks](../../evidence/maintenance/2026-10-19/compact-update-dialog/release-ui-unit.log) and [two About/navigation Chromium cases](../../evidence/maintenance/2026-10-19/compact-update-dialog/release-about-e2e.log) passed. Product status and Markdown links also passed. The [release preparation receipt](../../evidence/maintenance/2026-10-19/compact-update-dialog/release-preparation.json) records source and evidence hashes.
- The layout detector reported no findings for the Java file; rendered native observations above supply the visual evidence.

No installation, upgrade, uninstallation or Minecraft player acceptance was repeated for this spacing correction. The prior [public 0.1.11 receipt](../../evidence/maintenance/2026-10-19/compact-update-dialog/previous-release.json) retains its original source and artifact identities. Linux remains public at 0.1.9.

Windows 0.1.12 is now public. The [publication record](maintenance-release-0.1.12.md) contains the final signed source, successful release workflow and public package verification; the preparation receipts above retain their original pre-publication scope.

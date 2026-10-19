# Windows update helper — 2026-10-19

In-app updates in Windows 0.1.12 recreated desktop and Start menu shortcuts. The corrected update host preserves existing links, including user customization or removal; the full installer retains its Start menu entry and optional desktop shortcut. Registration failure also leaves unchanged integration files untouched. The helper's executable description now identifies its update/uninstall role, and the Chinese/English ready-to-install dialog explains its file replacement and authorization behavior.

This is an unreleased source correction based on `1d46f115258f4c416c327fdb94acd72115e79190`. It is not included in the public 0.1.12 packages. The [receipt](../../evidence/maintenance/2026-10-19/update-helper/verification.json) records source, built helper/setup JAR and evidence hashes. Preview/build version labels remain 0.1.12 and do not identify a new distribution.

## Verification

| Check | Result |
| --- | --- |
| Java regressions and setup JAR | [20 checks passed](../../evidence/maintenance/2026-10-19/update-helper/java-checks.txt): `PlatformInstallRollbackTest`, `InstallEngineTest`, `NativeDialogPresentationTest`, and `ProductUpdatesTest`. |
| Actual NSIS preprocessing and compilation | [Passed](../../evidence/maintenance/2026-10-19/update-helper/packaging-checks.json). Update helper: zero `CreateShortCut` instructions and `ShortcutPolicy=preserve`. Full installer fixture: two instructions and `ShortcutPolicy=create`. Only the helper's version-resource description changed. |
| Real Windows Shell link resolution | [Passed](../../evidence/maintenance/2026-10-19/update-helper/shortcut-result.json). After replacing an isolated target directory and removing the old one, the unchanged shortcut resolves to the new content; bytes, timestamp and custom arguments are preserved. The target is a text file and is never executed. |
| Chinese/light ready dialog, JVM scale 1.25 | [Observed](../../evidence/maintenance/2026-10-19/update-helper/ready-zh-light.jpg): complete wrapped explanation, progress bar and both actions; 474×237 logical units, text actual/preferred height both 55. |
| English/dark ready dialog, JVM scale 2 | [Observed](../../evidence/maintenance/2026-10-19/update-helper/ready-en-dark-200.jpg): complete explanation and visible actions at 200% scaling; 468×251 logical units, text actual/preferred height both 72. |

The two native previews use the production `ProductUpdateDialog` with an isolated profile. The fixture disables network access and download/install actions. Both Close buttons were exercised through the Windows computer-use controller, and both preview processes exited. These captures establish presentation coverage only. The small installer compilation fixture is not an application installer and was not executed or distributed.

## Security report scope

The [installed 0.1.12 helper inspection](../../evidence/maintenance/2026-10-19/update-helper/installed-helper.json) matches the public update inventory hash `cf3a00e602ce7fc4805f934f3abd0d57c07cf00d22d4babeb4926158410aa210`; its Authenticode status is `NotSigned`. The exact temporary helper shown in the original screenshots had already been removed, so the comparison covers the installed copy only.

Replacing program files and updating uninstall registration remain necessary update operations. The exact cause of 360's intrusion-protection classification has not been established. No 360 UI action, exclusion, sample submission, certificate configuration, final-package rescan, or real install/upgrade/uninstall was performed for this correction. These checks do not establish that antivirus alerts have disappeared. [User guidance](../user/security-alerts.md) retains the distinction between published-file identity and a vendor false-positive determination.

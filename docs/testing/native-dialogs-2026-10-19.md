# Native dialog correction — 2026-10-19

This follow-up fixes the standalone update window reported after Windows 0.1.10. That JVM previously had no product look and feel or window icon. It also switched to determinate progress before receiving a measurable phase while retaining the instruction to close the application.

## Coverage

- The independent updater and Java setup wizard share the application's FlatLaf version, palette and prompt components. The setup JAR bundles the look and feel, native decoration libraries, product icon and notices, so rendering does not depend on the main application's classpath.
- Workbench `JOptionPane` callers, including plain/HTML messages and input prompts, use bounded content, product identity and the shared theme. Raw diagnostic details remain selectable and scrollable.
- The updater distinguishes waiting, preparation, copying, authorization, cleanup and completion. Cancellation is possible before installation starts; committing does not retain the earlier close-application instruction.
- Setup keeps form input while language/theme popups close and rerender. The form tracks the viewport width and scrolls vertically, leaving the action row visible. Core progress rows wrap long paths and align indicators with their title.

The Windows NSIS bootstrap/uninstaller and OS-owned permission or system file dialogs retain their platform presentation. This change targets the Java application/setup/update windows; it does not replace Windows security UI or the native NSIS uninstall workflow.

## Validation

The [verification receipt](../../evidence/maintenance/2026-10-19/native-dialogs/verification.json) binds the tested source files, compiled UI classes, screenshots and check logs to their SHA-256 hashes. The self-contained setup JAR used for the standalone windows has SHA-256 `fa5070669292c7bdd5ae4ad92eff5757fdf2f76a2c0872136a4af83c54b6efc7`.

| Check | Observed result |
| --- | --- |
| Focused Java regression, production UI build and Javadoc | 14 tests passed. After the final font adjustment, the 8 affected tests and Javadoc passed again. |
| UI-Core / UI unit tests | 50 / 68 passed. |
| Installer interaction fixture | Language/theme popups preserve input and license selection. At 720×480 the form has no horizontal overflow, Browse/Read remain reachable, and the primary action stays visible. |
| Packaging fixture | The Node packager preserved the Gradle setup JAR bytes; 23 branding assets passed and the generated EXE icon had zero pixel difference. This marker-only payload is not a full application installer and is not a release artifact. |
| Repository checks | Product status, Markdown local links and Node syntax passed. The design detector reported no findings for the version-only user-guide edit; native presentation was inspected separately. |

Actual Windows desktop interaction used production Java UI classes and an isolated test profile. Screenshots cover Chinese/light at JVM scale 1.25 and English/dark or light at JVM scale 2; the scale is a JVM rendering setting, not a change to the operating system's display configuration.

| Surface | Desktop evidence |
| --- | --- |
| Waiting updater, Chinese/light | [Window](../../evidence/maintenance/2026-10-19/native-dialogs/update-waiting-zh-light.png): product icon, indeterminate progress without `0%`, cancellation closes the fixture worker. |
| Copying updater, English/dark, 200% | [Window](../../evidence/maintenance/2026-10-19/native-dialogs/update-progress-en-dark-200.png): measured 42% progress and disabled cancellation. |
| Update failure, Chinese/dark | [Error](../../evidence/maintenance/2026-10-19/native-dialogs/update-error-zh-dark.png) and [details](../../evidence/maintenance/2026-10-19/native-dialogs/details-zh-dark.png): localized actions, readable Chinese paths, Ctrl+End reaches `END OF DIAGNOSTICS`, and Esc closes each focused dialog. |
| Setup and license, English/light, 200% | [Setup](../../evidence/maintenance/2026-10-19/native-dialogs/installer-en-light-200.png) and [license](../../evidence/maintenance/2026-10-19/native-dialogs/license-en-light-200.png): complete Browse/Read actions, visible checkmark, and a readable focused default button. |
| Main-application progress, Chinese/dark | [Window](../../evidence/maintenance/2026-10-19/native-dialogs/core-progress-zh-dark.png): long paths wrap, status indicators align with the title, and the exterior frame uses the themed decoration. |

The standalone fixtures load the production setup JAR. The core progress fixture uses compiled main/test/installer classes, recorded separately in the receipt. All preview processes exited. These checks do not perform installation, upgrade, uninstallation, OS registration or gameplay acceptance, and do not modify existing user installations, workspaces or Minecraft worlds. Official release packaging and publication are verified by the release workflow separately.

The previous public Windows release remains recorded in [the 0.1.10 publication snapshot](../../evidence/maintenance/2026-10-19/native-dialogs/previous-release.json).

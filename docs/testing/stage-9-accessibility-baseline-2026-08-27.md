# Stage 9 accessibility automation baseline - 2026-08-27

This record covers the UI-shell accessibility baseline plus the first
Windows-native production-JCEF acceptance slice. It does not close the
`real-jcef-accessibility` gate, which still requires physical high-DPI and
screen-reader verification.

Command:

```text
npx playwright test e2e/accessibility.spec.ts --project=chromium --project=compact-1366
```

Result: **10/10 passed**.

The suite verifies dialog focus placement, Tab focus trapping, Escape handling,
blocking recovery behavior, polite live-region announcements, and minimum
32x32px hit targets for titlebar, navigation, and primary controls.

The run uses the UI-shell browser harness and mock bridge. It is an automated
baseline for Stage 9 UI behavior, not evidence of native JCEF rendering,
screen-reader integration, or real display scaling.

## Windows-native JCEF slice - 2026-08-28

`Stage9NativeJcefAccessibilityTest` is Windows-only and has no private opt-in
property, so the normal Windows Nightly Java suite executes it. It loads the
packaged production React shell through `CopperbenchProductShell.UI_URL`,
attaches the real JCEF UI-Core bridge, and verifies:

- focus moves into the create-element dialog;
- forward Tab and Shift+Tab wrap inside the dialog;
- Escape closes the non-blocking dialog and restores focus to its invoker;
- the global status announcer keeps `aria-live="polite"`;
- visible titlebar, navigation, dialog-button, and dialog-input targets are at
  least 32x32 CSS pixels.

The first native run exposed two product defects that the browser baseline did
not catch: the create-dialog close button rendered at 16x19 CSS pixels, and
React `autoFocus` caused the dialog hook to remember the soon-to-be-unmounted
input instead of the invoking button. This change fixes both by enforcing the
shared 32x32 modal-header button contract, giving form controls a 32px minimum
height, and capturing the dialog invoker during render before descendant
`autoFocus` commits.

Forced local Windows/JBR25 result: **1/1 passed** with real JCEF
`137.0.17.1142.68de80bc86de497c8d0632ad0f8fe33625b33bff`, CEF `137.0.17`, and
Chromium `137.0.7151.104`. The structured result reported a 1280x800 CSS
viewport, DPR 1.0, 31 visible targets audited, and minimum target size 32x32.
It is written to
`build/nightly-results/stage9-native-jcef-accessibility.json` for Nightly
artifact capture.

The corresponding Chromium + compact-1366 accessibility regression suite is
**10/10 passed** after adding focus-restore and modal-control hit-target checks.

This slice proves the production JCEF DOM/keyboard contract on Windows. It
still does **not** claim a physical 125%/150%/200% display-scale pass, Windows
screen-reader interoperability, or a complete manual keyboard audit, so the
machine-readable gate remains `blocked`.

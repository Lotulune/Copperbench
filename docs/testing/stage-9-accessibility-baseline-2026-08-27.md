# Stage 9 accessibility automation baseline - 2026-08-27

This record covers the UI-shell accessibility baseline plus the first
Windows-native production-JCEF acceptance slice. It does not close the
`real-jcef-accessibility` gate, which still requires physical high-DPI and
screen-reader verification.

Command:

```text
npx playwright test e2e/accessibility.spec.ts --project=chromium --project=compact-1366
```

Current result: **22/22 passed** across Chromium and the compact 1366 viewport.

The suite verifies dialog focus placement, Tab focus trapping, Escape handling,
blocking recovery behavior, polite live-region announcements, and minimum
32x32px hit targets for titlebar, navigation, and primary controls.
It also verifies that task logs and actionable diagnostics remain selectable,
that Procedure inspector tabs implement the ARIA keyboard model, and that the
Procedure node/port outline has readable accessible names.

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
- task logs retain `role="log"`, polite announcements, and selectable text;
- a Procedure can be created through the real UI-Core bridge, its inspector
  tabs support End-key navigation, and its node/port outline exposes readable
  names instead of requiring direct SVG inspection;
- all 28 visible Procedure buttons and inputs have accessible names and remain
  at least 32x32 CSS pixels.

Windows now uses JCEF's native windowed renderer (WR) for the product shell.
Chromium is started with `--force-renderer-accessibility=complete`, which keeps
the full accessibility tree enabled before a screen reader attaches. This is
required for Windows UI Automation; the previous OSR path exposed no native
UIA button nodes in the clean Hyper-V guest even though the DOM contract passed.
The bundled JCEF Java binding does not expose `setAccessibilityState`, so the
product records that capability at debug level and does not claim platform UIA
interoperability until a binding or provider exposes it.

The first native run exposed two product defects that the browser baseline did
not catch: the create-dialog close button rendered at 16x19 CSS pixels, and
React `autoFocus` caused the dialog hook to remember the soon-to-be-unmounted
input instead of the invoking button. This change fixes both by enforcing the
shared 32x32 modal-header button contract, giving form controls a 32px minimum
height, and capturing the dialog invoker during render before descendant
`autoFocus` commits.

Forced local Windows/JBR25 result after the expanded audit: **1/1 passed** with real JCEF
`137.0.17.1142.68de80bc86de497c8d0632ad0f8fe33625b33bff`, CEF `137.0.17`, and
Chromium `137.0.7151.104`. The structured result reported a 1280x800 CSS
viewport, DPR 1.25, 32 shell/dialog targets and 28 Procedure controls audited,
and minimum target size 32x32 in both groups. It is written to
`build/nightly-results/stage9-native-jcef-accessibility.json` for Nightly
artifact capture; the local fixed-run record is
[`native-jcef-accessibility.json`](../../evidence/stage-9/2026-08-29/native-jcef-accessibility.json).

The corresponding Chromium + compact-1366 accessibility regression suite is
**22/22 passed** after adding focus-restore, selectable assistive text,
Procedure semantics, keyboard tabs, and control hit-target checks.
The expanded suite covers protected-operation approval, schema-incompatibility,
resource-pack batch creation, and datagen publish confirmation dialogs. Those
paths now share the same focus placement, Tab trap, and Escape policy as the
creator and recovery dialogs; blocking dialogs remain explicit-action-only.

The clean Windows guest UI Automation probe has also been rerun against the
rebuilt WR installer. It observes the Copperbench `SunAwtFrame`, but still
reports `buttonCount=0`; this is retained as an unresolved platform/provider
issue and does not qualify as screen-reader interoperability evidence. The
probe does not modify guest DPI, drivers, registry, or user data.

The 2026-08-30 follow-up hardened that probe to inspect cross-process and
RawView descendants and then tested the relevant CEF/Chromium accessibility
activation paths on the same clean guest. Even with Narrator running, native
CEF complete accessibility successfully activated, Chromium's `UiaProvider`
enabled, and selective UIA activation disabled, RawView stopped at
`Chrome_RenderWidgetHostHWND -> Document` with `rawButtonCount=0`. The product
experiments were therefore not retained. See
[`stage-9-clean-windows-jcef-uia-2026-08-30.md`](./stage-9-clean-windows-jcef-uia-2026-08-30.md)
for the binding-level blocker and exact machine evidence.

This slice proves the production JCEF DOM/keyboard contract on Windows and the
real 125% host-DPI path. It still does **not** claim a physical
150%/175%/200% display-scale pass, Windows
screen-reader interoperability, or a complete manual keyboard audit, so the
machine-readable gate remains `blocked`.

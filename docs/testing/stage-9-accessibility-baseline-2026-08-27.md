# Stage 9 accessibility automation baseline - 2026-08-27

This record covers the UI-shell accessibility baseline only. It does not close
the `real-jcef-accessibility` gate, which still requires the production JCEF
host, keyboard audit, and high-DPI verification.

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

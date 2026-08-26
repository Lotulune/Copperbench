# Stage 9 UI editor and language-tool evidence - 2026-08-26

This record covers the UI-owned portion of Stage 9 only. It does not close the
real JCEF host, fixed-hardware P95, clean Windows 11 VM, server readiness, or
external tester gates.

## Scope

- Function has a dedicated command editor, tag editor, command diagnostics,
  snippets, preview, and save flow.
- Loot Table has dedicated pool, entry, condition, and JSON preview flows.
- Advancement has dedicated display, criteria, parent, rewards, and preview
  flows with parent-cycle validation.
- Language registry supports CSV/JSON import, merge/keep/replace conflict
  modes, missing/duplicate filtering, and CSV/JSON export.
- Mod element and registry views expose deterministic filtering, sorting,
  pagination, and page-size controls for large workspaces.

## Automated evidence

Command:

```text
npm run check:i18n --prefix ui-shell
npm run build --prefix ui-shell
npx playwright test e2e/stage9-creator-core.spec.ts --project=chromium --project=compact-1366
```

Result: i18n check passed (115/115), production build passed, and 16/16
Stage 9 creator-core tests passed across Chromium and compact-1366. The tests
exercise each dedicated editor, representative edits and saves, language
import preview/apply, and large-list pagination/filtering using the existing
UI-Core mock bridge.

The evidence is intentionally marked as UI-shell/mock-host evidence. A real
JCEF run is required before promoting the separate `real-jcef-accessibility`
gate or claiming the complete FR-S9-01/FR-S9-05 product path.

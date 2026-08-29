# Stage 10 Beta diagnostics and issue routing - 2026-08-29

This record covers the local implementation candidate for `FR-BETA-01` and
`FR-BETA-02`. The gates remain `in-progress` until the implementation is tied
to a reviewed commit and protected CI.

## Diagnostic bundle

The desktop Help view exports a local ZIP containing:

- a non-identifying environment summary;
- a safe workspace summary, active task results, and structured diagnostics;
- bounded application text logs after credential, user, home, workspace, and
  external absolute-path redaction;
- optional minimal reproduction files only after an explicit user checkbox.

Optional reproduction files are restricted by workspace-root containment,
excluded build/cache/history roots, approved text extensions, file count,
per-file size, total size, and traversal depth. They are included without
content rewriting after the explicit consent, so the UI and user guide require
review before sharing. Copperbench does not upload the bundle.

Local evidence:

- `DiagnosticBundleServiceTest`: 3/3 passed;
- `JcefDiagnosticsBridgeTransportTest`: passed;
- `Stage10NativeJcefDiagnosticsBundleTest`: 1/1 passed with the production
  Windows JCEF shell, real UI-Core bridge, and visible WR window;
- diagnostic UI Playwright: 2/2 passed across Chromium and compact 1366;
- TypeScript/Vite production build and Chinese localization gate passed.

## Issue routing

The GitHub issue form now collects area, Copperbench version, full source
commit, Windows build, generator, element type, reproduction steps,
expected/actual result, diagnostic code/error ID, and reviewed diagnostic
bundle or logs. Areas distinguish installation, workspace, generator/build,
UI/accessibility, MCP/headless/SDK, and documentation.

## External trial preparation

The five-tester gate now has an anonymous evidence schema, one full-task
protocol, and a verifier. The normal verifier reports current progress; the
`--require-complete` mode fails until at least five valid records from one
candidate commit/installer exist with no P0/P1 issue.

The verifier test matrix is **6/6 passed**, including candidate hash matching,
missing-candidate rejection, unknown-field rejection, P0/P1 rejection, and
personal-data rejection.

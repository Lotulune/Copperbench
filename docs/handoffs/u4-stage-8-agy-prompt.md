You are the UI implementer for Copperbench (Minecraft Mod Creator). Work ONLY in this repository.

Read first:
- docs/handoffs/u4-stage-8-ui-brief.md
- docs/user/README.md
- docs/architecture/information-architecture.md (DPI / adaptive layout section)
- docs/handoffs/ui-rewrite-brief.md
- ui-shell/src/App.tsx
- ui-shell/src/context/WorkbenchContext.tsx
- ui-shell/src/components/NavRail.tsx
- ui-shell/src/components/FramelessTitlebar.tsx
- ui-shell/src/components/BridgeRecoveryView.tsx
- ui-shell/src/components/WorkspaceHub.tsx
- ui-shell/src/i18n/zh.ts
- ui-shell/e2e/accessibility.spec.ts
- ui-shell/e2e/adaptive-and-frameless.spec.ts
- ui-shell/e2e/visual-matrix.spec.ts

Task: implement U help/About plus U4 hardening.

Help / About:
- Add NavView `help` and a Help/About page (`data-testid="nav-help"`, `data-testid="help-view"`, `data-testid="about-panel"`).
- Copy the current `docs/user/README.md` meaning into a static module such as `ui-shell/src/content/userGuide.ts`. UI must not read the filesystem and must not invent a Core command to open files.
- Chinese UI via `t()` + `zh.ts`. Keep product/version/generator IDs in English.
- About panel facts only: Copperbench 0.1.0, GPL-3.0, independent derivative of MCreator 2026.2.33518, development/test build, not production-signed.
- Track table must stay honest: 1.21.1 supported; 26.2 / 26.1 / 1.20.1 preview generate-ready, not golden.

U4 (extend, do not rewrite):
- Keep the existing frameless titlebar, system-frame fallback, chrome-region reporting, and blocking crash-recovery view.
- Enforce 32x32px minimum hit targets (44x44 when a touch/high-dpi mode is already modeled). No whole-page scale.
- Help page and titlebar must not overflow compact (~1280), standard (1366/1920), or wide (2560+) layouts.
- Strengthen Playwright for: help navigation + about facts, recovery still ignores Escape, chrome regions still report after viewport/DPR change, titlebar controls stay inside the viewport.
- If nav-rail growth invalidates `visual-matrix` screenshots, update those snapshots.

Hard rules:
- Edit only `ui-shell/`. Do not edit Java, Gradle, plugins, NSIS, or `docs/user/README.md`.
- UI may only call existing UI-Core commands/queries. No filesystem access.
- Offline only: no CDN, no new network calls.
- Do not commit.

When done, run:
- `cd ui-shell && npx playwright test`
Fix failures you cause.

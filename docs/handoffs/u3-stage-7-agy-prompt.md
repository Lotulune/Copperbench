You are the UI implementer for Copperbench (Minecraft Mod Creator). Work ONLY in this repository.

Read first:
- docs/handoffs/u3-stage-7-ui-brief.md
- docs/handoffs/ui-rewrite-brief.md
- ui-core/schemas/v1.0/command.schema.json
- ui-core/schemas/v1.0/query.schema.json
- ui-core/schemas/v1.0/event.schema.json
- ui-core/schemas/v1.0/tracks.schema.json
- ui-core/fixtures/v1.0/tracks/version-tracks.json
- ui-shell/src/App.tsx
- ui-shell/src/types/contract.ts
- ui-shell/src/bridge/CoreBridge.ts
- ui-shell/src/mock/mockBridge.ts
- ui-shell/src/context/WorkbenchContext.tsx
- ui-shell/src/components/NavRail.tsx
- ui-shell/src/components/WorkspaceHub.tsx
- ui-shell/src/i18n/zh.ts
- ui-shell/e2e/

Task: implement U3 UI for version tracks, copy-only loader migration, upstream import (disabled in mock/browser), and resource-pack publish batches.

Hard rules:
- Edit only ui-shell/ (and ui-core/fixtures only if you add a mock scenario). Do not edit Java, Gradle, plugins, or src/main.
- UI may only call UI-Core commands/queries. No filesystem access. No invented capability rules.
- Do not mark 26.2 / 26.1 / 1.20.1 as golden-supported. They are preview generate-ready. Use Core status fields.
- Chinese UI via t() + zh.ts. Keep technical IDs in English.
- Keep existing visual system, frameless titlebar, compact/standard/wide behavior.
- MockCoreBridge must return realistic fixtures for the new queries so Playwright can run without Java.
- Add Playwright coverage for: track matrix render, migration preview grouping, execute disabled until confirm, publish-batch empty and success states.
- Keep existing Playwright tests passing.
- Offline only: no CDN, no new network calls.

When done, run:
- cd ui-shell && npm test (if present)
- cd ui-shell && npx playwright test
- cd ui-core && npm test

Fix failures you cause. Do not commit.

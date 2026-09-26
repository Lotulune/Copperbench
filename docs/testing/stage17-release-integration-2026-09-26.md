# Stage 17 release integration — 2026-09-26

The user approved a non-prerelease stable release and the minimal release-workflow changes. Publication is pending validation; this report does not claim that release assets are already public.

## Source and local checks

- Stage 17 implementation: `398904c`.
- Integration with upstream `3e8714a`: `84fcc7bf30b9d28a825bc2eee19810e4cc56d7e3`.
- UI production build passed; 385 referenced contract keys and 1,669 authored English messages passed localization checks.
- 118 Playwright checks passed at 1920×1080 and 1366×768, covering native locale persistence and failure, draft retention, diagnostics, task history, modeled-asset workflows, Procedure return connections, and trigger catalogs.
- Windows signed-source gate tests passed, including approved stable declarations, mismatched tags, pending stable blockers, existing Beta exact-binary promotion, and draft asset digests.
- Linux authorization contract tests: 16 passed, retaining all ten installed acceptance gates and legacy-preference migration requirements for stable tags.
- Public issue tracker inspection returned no open issues at preparation time. This is not a claim of absence of unknown defects.

## Pending release gates

Required GitHub Java/Javadoc, UI smoke, MCP conformance and documentation checks must pass on integrated source. New packages must retain their own source commit, SHA-256, SBOM and provenance. Linux publication additionally requires fresh installed evidence bound to the exact CI candidate; previous run42 and local v38 receipts cannot authorize different bytes.

The [v38 development acceptance](stage-17-v38-closeout-2026-09-26.md) remains historical, scoped evidence: 924 Java passes, 59 conditional skips, installed/default probes and bounded Minecraft process-boundary persistence. Integration changes the UI and native locale bridge, so that acceptance does not certify the rebuilt release binaries. No fixed-player UUID inventory continuity, cold-cache/offline dependency coverage, new external trial, or new screen-reader certification is inferred.

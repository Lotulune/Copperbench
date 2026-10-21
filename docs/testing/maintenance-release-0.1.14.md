# Copperbench 0.1.14 release preparation

The user requested a new public release after merging [PR #140](https://github.com/Lotulune/Copperbench/pull/140), and explicitly selected both Windows and Linux for 0.1.14. Preparation starts from main commit `12a3cb5ad5c9381c1e62e155d977cdc206d79b46`. The original development checkout and its unrelated changes remain preserved.

## Scope and existing evidence

This release includes simple/professional mode switching, five host-installed CLI agents and icons, native workspace pickers, approvals and clarification answers, conversation history, draft protection and recovery integration. It also includes [PR #138](https://github.com/Lotulune/Copperbench/pull/138)'s task/log, locale, dialog-focus and MCP pairing continuity fixes. Generator support classifications remain unchanged.

- The [simple-mode integration record](simple-creation-merge-2026-10-21.md) records 44 focused Java checks, 73 UI unit checks, 19 original Chromium integration cases, 22 simple-mode layout/interaction cases, 13 professional regression cases, and real Windows JCEF accessibility at 125% scaling.
- The final feature [PR CI](https://github.com/Lotulune/Copperbench/actions/runs/39493445289) passed, including all 466 Chromium scenarios. The merged main [build/tests](https://github.com/Lotulune/Copperbench/actions/runs/39495919753), [documentation](https://github.com/Lotulune/Copperbench/actions/runs/39495919055) and [Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/39495918835) also passed.
- Those results precede this version-only preparation. The 0.1.14 source and final package checks are recorded separately when completed.
- The [previous Windows release](../../evidence/maintenance/2026-10-21/release-0.1.14/previous-release.json) retains its published identity and installer digest; Linux 0.1.13 remains recorded in its [publication report](linux-publication-0.1.13.md).

## Publication sequence

Synchronize the application, UI handshake/help, package locks and first-party release inventory to 0.1.14. Merge only after required checks pass. The signed latest-main Windows tag then runs full release tests, packaging, provenance, draft upload and verification before publication. Linux independently verifies a frozen main candidate and records its exact digests and CI receipts before signed latest-main promotion and downloaded-draft comparison.

The public download index remains at 0.1.13 until the respective release is observed. No new complete installed lifecycle, 360 rescan, screen-reader certification or player-controlled gameplay acceptance is claimed by these preparation checks. Historical evidence remains bound to its original source and package bytes.

## Preparation validation

The 0.1.14 version preparation passed 5 focused Java identity/manifest/guide tests, 50 UI-Core tests, 73 UI unit tests and 6 Chromium help/update/viewport cases. The Java and production UI build, localization checks, 3 release-history tests, product-status validation and repository Markdown links passed. Logs are retained under the independent worktree's `output/release-0.1.14/`. Required branch/main CI and each publication workflow must still pass on their respective exact commits before publication.

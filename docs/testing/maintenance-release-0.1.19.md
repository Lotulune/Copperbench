# Windows 0.1.19 publication

[Windows v0.1.19](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.19) was published at **2026-10-30T09:28:31Z** from signed source `a6851b3b16a071a922c833d66b3e7ae99008b58f`, the merge of the release preparation in [PR #169](https://github.com/Lotulune/Copperbench/pull/169). [Main regression](https://github.com/Lotulune/Copperbench/actions/runs/41025635845) passed on that source before the signed build; it changes no Java source or build file since `b7e1f1525`, whose documentation runs passed.

The user requested publishing new installation packages on 2026-10-30 after [PR #168](https://github.com/Lotulune/Copperbench/pull/168) was reviewed, fixed and merged as `8e03f1886d6ff794d55cf94256226cbb74744283`. This release contains the Notebook interface redesign, professional Agent approvals that stay visible above the run settings, shared CLI permission guidance for both workbenches, and the backend transaction and ordered workspace-event fixes merged after 0.1.18. Product identity, first-party release fixtures, About/help and the UI handshake advance to 0.1.19 together.

## Source verification

The reviewed PR head `9ca1277dc1dfc04d0f1a6aa2c74da28b238a24a5` has the same tracked tree as the integration merge. [Build and test](https://github.com/Lotulune/Copperbench/actions/runs/41019280923) and [Linux candidate checks](https://github.com/Lotulune/Copperbench/actions/runs/41019281136) passed on that head. The previous head failed one compact Chromium case on Linux fonts: the composer option menus inherited a listbox entrance that started 6 px above their final position, so an upward menu crossed its 12 px viewport margin for its first frames. The follow-up gives composer menus a scale-only entrance.

Local source verification on Windows passed 81 UI unit tests, 50 UI-Core contracts, the production UI build, and the full Chromium and compact-1366 Playwright projects. The last full run had 1,096 of 1,098 cases passing; its two failures (the asset-browser heading case, one per project) were fixed and their spec files re-run, 48/48. The four visual-matrix baselines were regenerated and pass. Two cases now wait for a view's 320 ms entrance before measuring or clicking it. The [retained source summary](../../evidence/maintenance/2026-10-30/release-0.1.19/source-verification.json) records these counts and the CI jobs.

Browser-preview screenshots covered simple and professional modes in light and dark themes, the inspector, Agent panel, create/export/setup dialogs, composer menus and the 500 px title bar. A native JCEF window (JBR 25) was then launched from the release-preparation source on an isolated workspace and checked with computer use: the mode switch, professional overview, inspector type plate and 3D preview, create dialog, Agent panel, dark theme, the upward effort menu and the delivery dialog rendered as intended. No assistant prompt, element change or export was performed, and the theme preference was restored. Agent conversations, Blockbench editing and gameplay were not exercised.

## Public package verification

The [Windows release workflow](https://github.com/Lotulune/Copperbench/actions/runs/41029801062) passed release tests/Javadoc, packaging, installer branding, SBOM, provenance, payload and draft checks before publication; its production deployment was approved on the repository owner's instruction. The [independent public verifier](https://github.com/Lotulune/Copperbench/actions/runs/41043377923) downloaded and hashed all 15 public assets, checked checksums/API digests and exact source/run provenance, and inspected packaged version, source documents, UI dependency license notices and template/class markers. Its 11 evidence files were re-hashed locally.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.19.Windows.64bit.exe](https://github.com/Lotulune/Copperbench/releases/download/v0.1.19/Copperbench.0.1.19.Windows.64bit.exe) | 503574148 | `7a20e35dbf3e3b677ecedfa157fd69772f333aa2b31460171a6b0e973d00a408` |
| [Copperbench.0.1.19.Windows.64bit.zip](https://github.com/Lotulune/Copperbench/releases/download/v0.1.19/Copperbench.0.1.19.Windows.64bit.zip) | 606754030 | `eb26e7142b28e6950c13c9c938ebcb63e8c5c2279b4042fee29b875add1077ca` |
| [Copperbench.0.1.19.Windows.64bit.msix](https://github.com/Lotulune/Copperbench/releases/download/v0.1.19/Copperbench.0.1.19.Windows.64bit.msix) | 627445204 | `c269bacc755df0962afc678839f445287a4aede37a30580733b207c3b00713e4` |

Installation, upgrade, user-file retention and removal results are recorded separately in [public-package acceptance](installed-release-0.1.19.md). Source runtime checks above retain their source-candidate identity. The redesign changes styling, copy and motion only; workspace formats, generator support and Minecraft gameplay code are unchanged. Windows packages remain Authenticode unsigned; MSIX installation, UAC and full Windows 11 desktop/player acceptance are not inferred from cloud automation.

[Linux publication](linux-publication-0.1.19.md) and [current downloads](../releases/current.md) provide the other platform and public entry points. Tagged/shipped documentation retains its build-time preparation text; this record does not alter released bytes.

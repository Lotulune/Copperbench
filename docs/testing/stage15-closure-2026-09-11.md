# Stage15 closure — 2026-09-11

Stage15 is complete for Ubuntu 24.04 LTS x86_64 with GNOME Wayland and GNOME on Xorg. Linux Preview 2 is publicly available; the Preview channel does not imply certification of other distributions, architectures or untested Linux runtime tracks. Windows Beta 4 remains the independent Windows published baseline.

## Public release

- Release: [v0.1.0-linux-preview.2](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.0-linux-preview.2), published at `2026-09-11T03:33:10Z`.
- Signed release commit: `fa1489dd6d6713bf5b371eea4a2ba157da190364`.
- Public workflow: [34558497473](https://github.com/Lotulune/Copperbench/actions/runs/34558497473), successful after explicit user publication approval and production review.
- Frozen binary source: `61f69bf1f8dfdd3efbcf379afbaafbbfb11d3466`, candidate Run42 `34547192490`.
- Debian SHA-256: `ff1d2e3c3e7120165aece357d9a0b4a1fba93ff37e31e44dd8f733b0ba7dc473`.
- Portable SHA-256: `746e8795b90689fe2d2672bb059f01f2bd8cca5c0e6689b89e5842baa4e38644`.

The publication run rechecked the signed tag on latest main, source drift, all original asset attestations, the ten installed gates and mandatory preference-migration evidence. It reused the seven verified draft assets, downloaded them, compared every byte, and only then published. The public API confirms `draft=false`, `prerelease=true` and all seven digests match the original payload. Receipts are in `evidence/stage15/2026-09-11/publication/`.

## Definition of Done

| Requirement | Accepted evidence |
| --- | --- |
| Clean guest start, create/open/save/reopen without system Java | `run42-wayland-ui`, four `run42-*-fabric/neoforge` preflights and `run42-portable` |
| Bundled JDK/JCEF, build, real runClient, window and external-tool paths | Four preflights, both Agent runs, managed Blockbench and real Asset Center observations |
| Desktop MCP/Agent plan/build/conflict and credential shutdown | `run42-wayland-agent`, `run42-xorg-agent`: normal game/product close, descriptor removal and old-connection rejection |
| SHA-256, SPDX SBOM, immutable metadata and provenance | `run42-candidate`, signed tag and successful public release workflow |
| Explicit supported scope and limitations | This closure, release notes, README and `release-control/linux-platform-support.json` |
| Preserve existing Windows/Core baseline | Full Java/Javadoc, UI and MCP regression passed; platform/path tests preserve Windows and explicit-home behavior; existing Windows published evidence remains separately identified |

The [Run42 acceptance report](./stage15-run42-acceptance-2026-09-11.md) contains the detailed installed evidence and its limits. Actual Linux runtime replay covers Fabric and NeoForge 1.21.1. Both old preference formats additionally passed real installed-JAR initialization, source-file preservation and XDG writes. Ten migration/path regression tests and fifteen release-control contracts passed.

## Historical records and current support

The frozen candidate metadata and the uploaded `LINUX-RELEASE-AUTHORIZATION.json` retain their pre-publication `formalSupportClaim=false` values. Those historical bytes are intentionally not rewritten. The independent [post-publication support record](../../release-control/linux-platform-support.json) is the current formal classification, binding the same candidate, promotion-authorization digest, installed evidence and successful public workflow receipts. `product-status.json` points to this record and marks Stage15 complete.

Preview 1 remains an unpublished historical draft: GitHub normalized spaces in the portable filename and the first comparison used its old name. PR66 fixed the mapping and collision checks; Preview 2 completed draft and public byte verification. No binary rebuild or tag rewrite was used to resolve the transfer issue.

The guest was restored to Wayland with session backups retained and temporary proxy/idle inhibitors stopped on reboot. Automatic-login GNOME may require normal login-keyring authentication. Builds used official repositories and warmed caches; no new offline-build claim is made. Blockbench remains a separate external installation. No broader Linux certification or exhaustive native-path coverage is inferred.

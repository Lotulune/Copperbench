# Beta 3 publication verification

Date: 2026-09-02

## Release identity

- Public release: `v0.1.0-beta.3`
- Release-control source: `7956dcb9930897357e2380ee413cdc4aa928f357`
- Frozen binary source: `v0.1.0-preview.7` / `f4b5806222b1224712cf33e827cc97241acdc45c`
- Windows release workflow: `33515908561`
- GitHub Release id: `380590455`
- Published at: `2026-09-01T14:46:14Z`
- State: `draft=false`, `prerelease=true`

Beta 3 uses the release-control commit for the signed Beta tag and metadata while its canonical product payload is the already-tested Preview 7 candidate. The release workflow therefore promotes the tested candidate bytes instead of rebuilding a different product binary.

## Two-phase status semantics

The `product-status.json` copied into a release payload is deliberately a **pre-publication snapshot**, not a mutable post-publication status document. At release-build time, `publishedBaseline` and `releaseContract` identify the last release that is already known to be public, while `betaRelease.tag` identifies the Beta currently being verified and `betaRelease.status=ready` records that publication has not succeeded yet. The workflow can still fail during draft upload, remote asset verification, production approval, or the final publish step, so the tagged source must not claim its own release is already public before those steps complete.

This is the same state model used for Beta 2: release source `8b06328347bc45b9ccc8cb243bf913c3e28e3be6` carried `publishedBaseline=v0.1.0-beta.1` and `betaRelease.tag=v0.1.0-beta.2` / `status=ready`; only the repository post-publication closeout advanced the live baseline after run `33334394466` succeeded. Beta 3 follows the same invariant: release source `7956dcb9` truthfully records Beta 2 as the last already-published baseline and Beta 3 as the ready release being published, while this post-publication closeout advances the repository machine state to Beta 3 after run `33515908561` succeeded.

`deploy.yml` also explicitly refuses to overwrite an already-public Release. Consequently, the `product-status.json` attached to an immutable historical Release is provenance for the state **at build/publication time**; it is not the live public-state endpoint for that same release after publication. Consumers that need current publication state must use the repository's current `product-status.json` and its `releaseContract.publicStateSource` / the GitHub Release API. A new immutable Beta tag is required only when the release payload itself has an actual identity error (for example the Beta 1 `product.channel=preview` mismatch), not merely because the pre-publication snapshot names the previous successful release as `publishedBaseline`.

For Beta 3 the release payload already has `product.channel=beta`, `delivery.betaRelease.tag=v0.1.0-beta.3`, the correct Preview 7 candidate identity, and the correct canonical digests. Therefore no metadata-correction Beta is required for this two-phase baseline transition.

## Canonical asset verification

| Asset | Size (bytes) | SHA-256 | Result |
| --- | ---: | --- | --- |
| `Copperbench.0.1.0.Windows.64bit.exe` | 439296643 | `716a93ea45278d71b2ef80eeee3bd4d0ec315891c349478cff50ed093db90d93` | Uploaded; identical to Preview 7 |
| `Copperbench.0.1.0.Windows.64bit.msix` | 477529949 | `cafcaadf49cb0a59873d47d55ee0f0023e8c6f03c9b820a03994bb75b215faa3` | Uploaded; identical to Preview 7 |
| `Copperbench.0.1.0.Windows.64bit.zip` | 462416831 | `1255a8528cd104d9fb26dfbb6228bfab415d77b84b071b636032c65a1e0282f5` | Uploaded; identical to Preview 7 |
| `copperbench.spdx.json` | 489570 | `655daa5b9a7a96edba27726169f46146cda391e6e5bfee4fa350da5d4a94c078` | Uploaded; identical to Preview 7 |

GitHub reports all four canonical assets in the published Beta 3 release with the same digests that were frozen for Preview 7. This closes the Stage 11 exact-binary promotion requirement.

## Candidate qualification carried into Beta 3

Preview 7 had already completed the Stage 11 fixed-source qualification before promotion:

- fixed `f4b58062` merged-main CI and Nightly `33503172036`;
- 8/8 generator golden matrix;
- signed Preview release run `33506364499`;
- clean Windows 11 GUI workspace creation and `-workspace` cold-start observation;
- old Preview -> Preview 7 upgrade, offline workspace launch, uninstall retention and candidate restore;
- release provenance, manifests and canonical asset verification.

The graphical Minecraft `runClient` readiness replay is not part of this publication claim. The available Hyper-V guest does not expose a usable OpenGL profile, and the historical lifecycle harness had accepted a NeoForge initialization-error window as stable. The published candidate's product build/JAR path was verified independently; the graphics-capable environment certification remains excluded from the release scope.

## Post-publication harness maintenance

After Beta 3 was published, PRs #52 and #53 hardened the Stage 9 test infrastructure without changing the Beta 3 product binary. They fixed binary-cache IPC false positives, removed the hard-coded lifecycle workspace fixture, reject known GLFW/OpenGL initialization errors as `runClient` readiness, and stabilize observation of the generator-setup dialog. `main@af1b6ed929e1b6d6d1e08d01bd90c0b86a1b4d88` then passed merged-main Java/Javadoc, UI, Windows Stage 9 regression, MCP conformance and JUnit checks in run `33528964018`.

## Scope statement

The following are not claimed as passed and are not release blockers for the current roadmap: dedicated graphics-capable clean-Windows `runClient` certification, real JCEF/UIA screen-reader certification and physical-DPI audit, the formal five-external-tester gate, Authenticode, Linux/macOS, and Windows 10. Existing product paths remain subject to normal defect handling if real users report reproducible failures.

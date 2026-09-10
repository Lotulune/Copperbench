# Stage 15 release integration — 2026-09-11

The user authorized Linux release-control CI, main integration, signed-tag progression and production approval.
Public publication remains controlled by the explicit `publish` input and the protected production environment.

Main `a3b868ee493d37420dad14b3bfe291fbd84311e5` contains additional Stage14 fixes beyond the tested Run34 source.
Merge `ad52175725fc0319a6239b1a48bbdedb66f71edf` retains artifact-content verification for idempotent Workspace Plan replay,
manual code-source persistence, wrapper copy attributes, Linux owner-execute permissions and resource-pack setup completion.
The overlapping wrapper normalizers were consolidated, and both sets of meaningful tests retained. Duplicate translation
keys were removed while retaining the mainline diagnostic messages.

Local validation: 56 focused Java tests, 20 UI-Core contract tests, 234 translation keys, and the production UI-shell build
passed. Evidence: `evidence/stage15/2026-09-11/main-integration/local-checks.json`.

Run36 (`34534596662`) stopped before packaging because a legacy test matched an exact Java local-variable expression. The test now generates real first-party/plugin workspaces and checks launcher content and POSIX owner-execute permission. All 7 wrapper/permission tests pass. A replacement candidate from the updated commit must complete installed replay. Run34's accepted evidence remains
historical and must not be relabelled with the replacement digest. Release authorization deliberately stays
`pending-validation`, `releaseEligible=false`, `formalSupportClaim=false` until that replay is complete.

The dedicated tag namespace is `v<version>-linux-preview.<n>` / `v<version>-linux-beta.<n>`. The existing Windows tag
workflow excludes `v*-linux-*`, avoiding concurrent creation of the same GitHub Release. The production environment's
existing `v*` tag policy and required reviewer remain unchanged.

Linux release control verifies the signed tag against the existing signer list and latest main, rejects product/build
changes after the frozen candidate, checks every asset and its source attestation, and verifies ten digest-bound evidence
records. Original candidate metadata is never rewritten. The separately approved authorization record supplies release
eligibility after validation. The workflow creates a draft without clobbering assets, downloads it for byte comparison,
and publishes only when explicitly requested and approved.

Release-control validation: 12 Node authorization/resume/routing contracts passed, including rejection of pending records, wrong candidate/session, incomplete normal close, diagnostic overrides, modified evidence and public-release overwrites. The actual workflow YAML and all Bash steps passed syntax checks.

Run37 (`34537005983`, source `f811483935ed682177494b205337843090dc040b`) passed packaging, both loader render preflights and provenance generation. The full PR Java job ran 554 tests (43 skipped) with one failure: the Windows UI fixture's development SBOM runtime paths differed from the Linux host paths. The test now adapts only those two expected paths before comparing the complete manifest. Product code and packaged inputs are unchanged; the exact test file is explicitly permitted in the post-candidate source delta, while unreviewed test files remain rejected. Focused manifest/SBOM/platform checks and all 12 release contracts pass locally. Linux CI confirmation and Run37 installed replay remain pending.

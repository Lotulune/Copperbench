# Stage 17 v38 acceptance evidence

This directory preserves a small, reviewable subset of the local acceptance evidence before release integration.
The candidate was built from the dirty working tree based on `06c372f760b449beeadb811567048438f5c5080e`.
Its Windows/Linux application JAR is identified by `candidate-proof.json`; these records do not claim that
a later release rebuild has identical bytes or that subsequent source changes were already tested.

- `regression-summary.json`: final local default suite, 924 passed, 59 conditional skips, no failures/errors.
- `defaults-*.json`: eight generator tracks through the packaged SDK on Windows and Linux, including the
  installed Ubuntu launcher. Large environment dumps are omitted; the original proof digest is retained.
- `installation-proof.json`: approved v38 installation and protected workspace resource fingerprints.
- `client-proof.json`, `client-actions.jsonl`, screenshots and verification: v37 model workspace restored
  after a real process boundary, saved and closed normally. Player identity changed; fixed-player inventory
  continuity is not claimed. The final menu receipt retains its window-close interruption.
- `final-source-audit.json`: product runtime sources remained unchanged after v38 was frozen; later
  acceptance changes were confined to tests and documentation.
- `sha256.json`: digests of the copied/generated records, excluding this explanatory file and the manifest itself.

Machine-local paths in the records are historical provenance. Runtime worlds, dependency caches, binaries,
credentials and conversation archives are not included. The complete local artifacts remain under `output/`.
See the [acceptance report](../../../docs/testing/stage-17-v38-closeout-2026-09-26.md) for boundaries and earlier failures.

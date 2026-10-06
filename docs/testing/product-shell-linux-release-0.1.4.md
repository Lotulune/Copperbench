# Linux 0.1.4 publication preparation

Authorization record dated 2026-10-06: candidate CI passed; owner approved publication using this candidate CI without repeating full installed acceptance. Publication is a separate protected workflow step. The request to publish the new release includes Linux; the earlier Windows-only scope was an implementation omission, not a restriction from the owner.

## Frozen candidate

- Source: `aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f`, shared with Windows 0.1.4.
- Candidate run: [37391113672](https://github.com/Lotulune/Copperbench/actions/runs/37391113672), completed successfully.
- Candidate ID: `sha256:7bc4cfcea7942930a0f24084dedcd3585e7319563c06856ed50f2f55b89c9715`.
- Debian SHA-256: `0460c27bb26927ef8240265775b6f071a49614119043ef21c4616f285c4512d4`.
- Portable SHA-256: `e141762c65e2c8c309eef0be3b3c5f64f422bcd590c1158a2dda443f8ae181ae`.

The saved [run receipt](../../evidence/maintenance/2026-10-06/linux-014/candidate-run.json), [jobs receipt](../../evidence/maintenance/2026-10-06/linux-014/candidate-jobs.json) and [hash log excerpt](../../evidence/maintenance/2026-10-06/linux-014/candidate-hashes.txt) identify the successful build. All twelve steps required by the existing CI-based maintenance policy passed, including package layout, isolated bootstrap, JCEF startup, both Loader render preflights, provenance generation and artifact upload. Hashes above are taken from that workflow's log; local binary verification and promotion have not yet run.

## Approved acceptance scope

Fresh full installed acceptance has not run. The host query on 2026-10-06 returned zero registered Hyper-V VMs. Historical guest evidence directories are not a running GNOME validation environment.

The owner explicitly selected “允许，本次按已通过的 CI 发布” after being asked whether to publish Linux 0.1.4 using the passed package/startup/Loader-render CI with full installed acceptance recorded as `not-repeated`. The separately scoped `maintenance-ci-0.1.4` policy permits only tag `v0.1.4-linux-stable`, source `aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f` and run `37391113672`. The [release verifier](../../scripts/verify-linux-release-authorization.mjs) rejects other candidates and requires `formalSupportClaim: false`. It also rejects reuse of historical installed evidence. This is not a blanket waiver for future releases.

The [0.1.3 maintenance exception](maintenance-linux-0.1.3.md) retains its original scope. Its [previous authorization](../../evidence/maintenance/2026-10-06/linux-014/previous-013-authorization.json) is archived unchanged. The new authorization binds this report and the CI receipts by SHA-256. No fresh GNOME Wayland/Xorg desktop, Blockbench round trip, UI persistence, preference migration or gameplay acceptance is claimed.

The [Linux release notes](../releases/v0.1.4-linux.md) are prepared. The promotion workflow now selects notes for the chosen version instead of its stale 0.1.1 notes. Signed tags, candidate provenance, exact-byte promotion, source-delta restrictions and draft download comparison remain required.

# Copperbench 0.1.2 release status

The user authorized committing, pushing and publishing the product maintenance changes on 2026-09-27, explicitly excluding the local Minecraft-control bridge. The selected scope is **Windows and Linux 0.1.2, with both public releases held until the fresh Linux candidate completes installed acceptance**.

## Implementation baseline

- Maintenance source: `f5ca9fc6`; development checks and limitations are retained in [the maintenance record](maintenance-2026-09-27.md).
- Version identities are advanced together in the product configuration, UI, MCP client identity and canonical release fixture.
- Existing bridge changes, local MCP configuration and the associated AGENTS.md changes remain outside the product commit.
- Historical 0.1.1 tags, binaries, authorization and acceptance records remain unchanged.

## Publication gates

| Gate | Current state |
| --- | --- |
| Required implementation PR and merged-main CI | Passed: PR #83; main `9bc6d104`; run `36321809512` |
| New Linux candidate and provenance | Passed: run `36321809542`; all six frozen assets verified |
| Fresh digest-bound Linux installed acceptance | Passed: ten gates and both preference migrations; [immutable installed report](maintenance-012-linux-installed-2026-09-28.md) |
| Release-readiness declaration PR and latest-main CI | Required before signing final tags |
| Signed latest-main Windows/Linux tags | Pending |
| Protected packaging/promotion and publication | Pending |

Public Windows/Linux 0.1.1 remain the downloadable baseline until these gates complete. Current-source success does not authorize promoting the old Linux candidate as 0.1.2.

The accepted 0.1.2 Linux binaries are bound to source `9bc6d104688c24512468880abdc2a96828fcaab8`. Their original metadata remains unchanged. The final signed release commit may add only the allowed documentation, evidence and release-control declarations. Both platforms remain held until the release-readiness checks pass; the existing protected release workflows then recheck source, signatures, assets and provenance.

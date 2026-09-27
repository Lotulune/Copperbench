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
| Required PR and merged-main CI | Pending |
| New Linux candidate and provenance | Pending |
| Fresh digest-bound Linux installed acceptance | Pending |
| Signed latest-main Windows/Linux tags | Pending |
| Protected packaging/promotion and publication | Pending |

Public Windows/Linux 0.1.1 remain the downloadable baseline until these gates complete. Current-source success does not authorize promoting the old Linux candidate as 0.1.2.

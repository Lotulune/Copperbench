# Repository governance evidence - 2026-08-26

This record captures repository settings verified through the GitHub REST API.
It does not promote any Stage 9 product gate.

| Control | Verified state |
| --- | --- |
| License detection | GitHub reports GNU General Public License v3.0 (`GPL-3.0`) |
| Pages | Enabled at <https://lotulune.github.io/Copperbench/> from `javadoc:/` |
| Main protection | Strict checks enabled; admins included; force pushes and deletion disabled |
| Required checks | `Java tests and Javadoc`; `UI contract, build, and smoke tests`; `MCP conformance` |
| Production environment | One required reviewer; deployment branch policy permits tags matching `v*` |
| Stale draft | The incomplete `v0.1.0` draft Release was deleted; the Git tag was retained |

The first run after landing the status source exposed a missing
`source.additionalTerms` property in the frozen Release Schema. This branch is
the protected-branch smoke PR that fixes the contract and must pass all three
required checks before merge.

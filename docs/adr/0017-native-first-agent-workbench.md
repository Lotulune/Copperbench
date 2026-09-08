# ADR-0017: Native-first workbench for general coding Agents

- Status: Accepted product direction; implementation pending by Stage 14 wave
- Date: 2026-09-07
- Supersedes: no historical release evidence; refines Stage 14 authoring policy
- Requirements: PRD-NEXT FR-ADV-01 through FR-ADV-09

## Context

The user approved a general-Agent-first direction after the `eaf18606` source audit and requested a plain-Fabric comparison. Copperbench already has structured elements, multi-file `codeFiles`, Desktop MCP, plans, build tasks and local history. Those features do not by themselves establish an advantage over a coding Agent using a normal Gradle project.

The source-runtime audit reproduced two content-integrity defects: two managed elements can claim/overwrite the same Java path; and a display-name-only update can overwrite an external edit with metadata-held code. Preview and recovery metadata are insufficient if the actual file boundary is wrong. See the [comparison and evidence](../testing/native-agent-survey-pulse-comparison-2026-09-07.md).

The later paired server bootstrap also found a generator-owned `ServerPlayerMixin` injection failure in the Fabric 1.21.1 fixture while the native fixture initialized and stopped at the unaccepted EULA boundary. This is a scoped, unrepaired functional defect (CB-AUDIT-03), not proof every supported generator or published binary fails. It must be investigated alongside 14A instead of deferred until the richer 14C verification UI exists.

## Decision

Copperbench is a Minecraft workbench callable by general Agents, not a replacement planning Agent. Structured elements, Procedure graphs and templates are optional shortcuts. Native Java, resources and tests must be usable without encoding every implementation decision in a platform-specific schema.

Structured element definitions remain authoritative for generator-owned outputs. Native/manual source files are authoritative for their own content; ownership/index/fingerprint metadata must not become a stale second source that silently overwrites them. Existing code bundle clients need a compatible transition, not removal of their data.

External file edits are supported inputs, reconciled into the project by change detection and explicit verification. This is not permission to overwrite generated files or to declare unverified edits successful. Authentication, workspace confinement, real conflict detection, recovery and protected operations remain intact. The policy for low-risk operations within granted permissions should avoid repeated human interruptions; it does not remove existing user-only boundaries.

Ship 14A integrity/coexistence, then 14B native/IDE/CLI authoring, then 14C runtime feedback/evaluation. Heavier review UI, templates and experimental extension entry points follow in 14D. Do not create a second task engine, a model-specific planner or an all-purpose DSL.

## Consequences and limits

- Test both command and plan persistence paths, metadata-only changes, duplicate file claims, external edits, reopen/regeneration and recovery.
- Keep original source/logs accessible. Show element semantic diffs where they exist and file diffs for native content; do not promise arbitrary Java-to-Blockly round trips.
- Incomplete native-reference analysis is not proof an asset is unused or safe to rename/delete.
- Separate written/generated/compiled/loaded/behavior-verified outcomes and bind evidence to source/environment.
- Compare the same Agent and task with explicit cache, specification reuse, permissions and validation controls. Build timings alone are not creator-productivity measurements.
- The first target is native authoring inside a Copperbench workspace. Full adoption of arbitrary third-party Gradle projects and universal code migration remain separate decisions.
- Existing Stage 12/13 and platform evidence retains its scope. Approval of this ADR is not implementation, runtime validation or closure of the two defects.

## Alternatives not selected

Forcing all logic into structured elements limits expression and duplicates native language tooling. Building a competing general Agent adds orchestration without fixing source integrity. Removing all safety checks confuses implementation freedom with permission to lose user data. None addresses the observed problem as directly as reliable native-source coexistence.

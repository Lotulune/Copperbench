# Version Copy Upgrade

## Read-only contract 1

This read-only contract plans **Fabric 26.2 → Fabric 26.3** or **NeoForge 26.2 → NeoForge 26.3**. It is independent of Loader Migration. It does not expand `migratable()`, select a preview feature, issue task authority, create a destination or approve a world copy.

The UI exposes **Versions & migration → Version copy upgrade**. Select the plan for the active source workspace. It shows original values, conversion dispositions, preserved identities, manual field/file tasks, recovery guidance and rule evidence. Changing source state or failing a refresh clears the displayed plan.

Python Native SDK:

```python
result = workspace.version_copy_upgrade(target_generator_id="fabric-26.3")
plan = result["plan"]
```

The MCP Python client offers the same method. The TypeScript MCP client uses `versionCopyUpgrade('preview', 'fabric-26.3')`. The MCP tool is `version_copy_upgrade`. Every request carries `supportedVersions: ['1']`; `negotiate` and `preview` are the only accepted operations. Native uses `kind: 'version_copy_upgrade'`; JCEF uses the corresponding separate `messageType`. The frozen UI-Core 1.0 envelopes are unchanged. See the [extension schema](../../ui-core/schemas/extensions/version-copy-upgrade-v1.schema.json).

Each plan contains:

| Field | Meaning |
| --- | --- |
| `ruleVersion` | The specific rule set used by this preview. |
| `sourceWorkspaceId`, `sourceRevision` | Active source identity and product revision. |
| `sourceGeneratorId`, `targetGeneratorId` | Exact same-loader source and target. |
| `sourceFingerprint` | Deterministic hash of source-tree bytes, including runtime directories and world files. Existing tree-hash exclusions cover Git metadata, OS junk and `.tmp` files; the exact `.copperbench/workspace.write.lock` IPC lease is also excluded because Windows locks it against reads. User files with the same basename elsewhere remain included. |
| `planId` | SHA-256 of the canonical plan contents, including its fingerprint, target, rules and field values. |
| `entries` | Original value, disposition, reason, target meaning, recovery and evidence per field, identity or source file. |
| `executionAvailable` | Always `false` in contract 1. A preview is not a completed conversion. |

The preview reads actual saved definitions. Manual code uses its established workspace metadata and reads the current primary/helper files from disk, including external edits. Internal names, registry names, element UUIDs, unknown metadata and manual source ownership remain visible. Only supported metadata values and the exact target selection are classified as convertible in this initial rule set. Other field/file semantics require manual review; unknown element types remain unsupported. Existing 26.3 element support is not evidence that an arbitrary 26.2 field converts automatically.

The product takes its revision lock without advancing revision or publishing an event. A second tree hash rejects concurrent disk changes. Preview never writes to the source or creates a target. MCP upgrade audit records use the product configuration directory's `version-copy-upgrade-audit/<workspace UUID>.jsonl`, outside the source workspace; audit failures still reject the call. Other tools retain their existing audit location.

`worldPolicy` is `excluded_requires_separate_authorization`: no world is copied by preview. Recovery is continuing with the unchanged source workspace; this does not promise downgrade of a world opened by a newer game.

Contract 1 rejects cross-loader requests, write parameters, missing negotiation and execution requests. Source changes require a fresh plan. Frozen-candidate, installation and player acceptance remain separate gates.

## Bounded execution contract 2

Contract 2 adds a separate explicit `execute` operation for **Fabric 26.2 → Fabric 26.3** and **NeoForge 26.2 → NeoForge 26.3 function-only workspaces**. Each loader has its own rule identity and target-plugin fingerprint. Contract 1 remains unchanged. The [v2 schema](../../ui-core/schemas/extensions/version-copy-upgrade-v2.schema.json) binds every executable plan to a new sibling `outputName`, its canonical `targetDirectory`, the exact target plugin resources, source identity/revision and source fingerprint. Version 2 hashes all regular source files including runtime/world/cache bytes; only `.git` and the exact live writer lease are excluded. Each sorted file contributes its UTF-8 relative path, NUL, fixed-length hexadecimal SHA-256 and NUL, so binary file contents cannot impersonate path boundaries. It never copies worlds or uses a source run directory as the target run directory. Target Gradle setup may create a new empty run directory and regenerate IDE profiles for the target paths.

The first executable rule permits unlocked functions in the `mod` namespace containing comments, blank lines, `scoreboard objectives add <identifier> dummy`, `scoreboard players set <literal-holder> <objective> <integer>`, and absolute-coordinate `setblock` for `minecraft:gold_block`, `minecraft:stone` or `minecraft:air`. Other elements, manual Java, commands outside the rule, unresolved generation, nonempty workspace data and unproven settings remain explicit manual tasks. Any manual task blocks the entire execution.

Generated source files must both retain recorded generator ownership and match the original generator's in-memory template bytes. This comparison does not preserve user-code regions or run filesystem generation on the source. Workspace-base Gradle files must match their loaded templates. Unknown source files and externally changed generated files block execution. Empty language resources contain no entries to convert. The source-only `.eclipse/configurations/Client.launch` and `Server.launch` IDE profiles are explicitly left in the source because they embed its build/run paths; custom profiles remain manual tasks. Known editor function fields must agree with the saved definition. Unknown JSON fields are retained without interpreting extra plugin behavior; definitions are copied byte for byte. The target receives a new workspace UUID and revision zero while keeping internal names, registry names, element UUIDs and unknown metadata.

Example using the Native Python SDK (MCP Python exposes the same arguments):

```python
args = dict(target_generator_id="fabric-26.3", output_name="project-26-3",
            supported_versions=("2",))
plan = workspace.version_copy_upgrade("preview", **args)["plan"]
# Review the returned original values, target directory and manual tasks first.
workspace.preview("opt_in", generator_id=plan["targetGeneratorId"],
                  workspace_folder_path=plan["targetDirectory"])
result = workspace.version_copy_upgrade(
    "execute", **args, plan_id=plan["planId"], expected_revision=plan["sourceRevision"],
    task_authorization_id=target_grant_id,
)
```

The TypeScript MCP client uses `versionCopyUpgrade('execute', 'fabric-26.3', ['2'], { outputName, planId, expectedRevision, taskAuthorizationId })`. Execution is sent once; loss of the response does not trigger a retry. Inspect the target's `version-copy-upgrade-result.json` before creating another copy.

Read-only callers can inspect plans but cannot execute. The UI requires confirmation of the reviewed destination and the 26.3 preview. Headless/MCP callers require an unexpired, unrevoked target-scoped task grant containing **both `create` and `build`**; caller-supplied `userApproved` is not authority. Preview opt-in is separately bound to the exact target and destination. Source tasks must be idle. The source revision is coordinated, the plan is recomputed, and authority is checked again before the target is atomically reserved. Even an empty pre-existing target is rejected; path traversal and redirected locations are rejected before writes.

The receipt distinguishes `prepared`/`conversionComplete: true` from `failed`/`conversionComplete: false`. `prepared` means the copy and target plugin generation completed with unchanged source bytes, preserved identities/metadata and exact definition bytes. Its `acceptance` remains `not_run`: build, independent-host acceptance and verified export are explicit subsequent operations on the new workspace. Any target failure keeps a failure receipt, never a success marker; recovery is continuing with the original source. The copy operation does not switch or close the active source workspace.

The real Native delivery runner is [version_copy_delivery.py](../../sdk/python/version_copy_delivery.py), launched by `runVersionCopyDelivery`. Its test host is added only after copying, in the target. World-exclusion inputs are labeled byte fixtures; they do not represent player-world runtime acceptance.

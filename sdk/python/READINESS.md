# Native SDK discovery and diagnostic changes

The [agent-readiness PRD](../../docs/roadmap/agent-readiness-prd-2026-10-09.md) separates SDK compatibility, Core discovery and real delivery acceptance. The [M1 record](../../docs/testing/agent-readiness-m1.md) records the current verification scope.

## Discover only published contracts

```python
from copperbench import NativeApiError

# Use an already opened Workspace session. These methods issue queries only.
print(workspace.available_field_contracts())
try:
    contract = workspace.field_contract("item")
except NativeApiError as error:
    if error.code != "FIELD_CONTRACT_UNAVAILABLE":
        raise
    print(error)
    print(error.details["availableContracts"])
```

`available_field_contracts()` lists the keys actually published by the connected Core. These are not the complete set of creatable Mod Element types: generic input contracts may also be listed. `field_contract()` returns an existing contract unchanged. Missing item/recipe metadata is not proof that the type cannot be created.

**Compatibility note:** callers that previously caught the incidental `KeyError` for absent contracts must now catch `NativeApiError` and inspect `error.code`. Missing metadata uses `FIELD_CONTRACT_UNAVAILABLE`; malformed metadata uses `NATIVE_INVALID_RESPONSE`; Core rejection codes are preserved. The SDK neither invents a contract nor creates a probe element. For an existing element, query `get_mod_element_editor` with its actual `elementId`.

## Versioned item and recipe discovery

`workspace.discover_field_contract("item")` returns the Core contract data. Its
`availability` distinguishes `available`, `not_exposed` and `unsupported`; only
`available` has `complete=True`. Item and recipe metadata includes JSON pointers,
JSON Schema input shapes, defaults, conditional requirements, reference lookup,
generator field restrictions and a `minimalExample` accepted by
`workspace.create_mod_element(**contract["minimalExample"])` in a new workspace.
Example names are fixed; use a new valid name when that name already exists.

The environment advertises `get_mod_element_field_contract` and also publishes
complete item/recipe entries in `fieldContracts`, preserving older readers.
New SDK discovery against an older Core returns explicit `not_exposed` metadata
without probing or substituting the generic contract. Unknown types on the new
Core use `ELEMENT_TYPE_UNKNOWN`; malformed discovery responses use
`NATIVE_INVALID_RESPONSE` and leave the session usable.

`workspace.field_reference_options("recipe", "blocksitems", search="Items.STICK",
offset=0, limit=20)` returns a Core envelope containing installed-generator
vanilla mapping values and any required APIs. Limits are 1–200. Workspace
references use `list_mod_elements` and the published `CUSTOM:` prefix.
Discovery does not write files, increment the revision or prepare dependencies.
Input acceptance is distinct from generation support and gameplay acceptance.

## Readable messages with raw evidence

Simple named placeholders in diagnostic fallbacks are replaced using `message.args`. For example, `{field}: {reason}` can render `/commands/0: Expected a non-null command string.`. Arguments are inserted literally, once. Missing or structured arguments remain visible as placeholders; attribute access, indexing and format expressions are not evaluated.

`NativeApiError.code` and `details` retain the original diagnostic envelope, including key, fallback and arguments. Message rendering does not replay rejected writes, change revisions or grant permissions.

## Finite task waiting

`wait_task(timeout=..., poll_interval=...)` requires positive finite values for both arguments. Invalid values are rejected before any poll. An ordinary task-wait timeout still leaves the task running; callers decide explicitly whether to poll again or cancel. It is not a transport timeout or an automatic retry.

## Read-only generation preflight

`Workspace.preview_generation()` and `CopperbenchClient.preview_generation()` return the Core envelope for `preview_generation`; TypeScript exposes `previewGeneration()`. Inspect `data.status`: `ready` is a source-safety observation, `conflicted` contains bounded per-path ownership conflicts, and `unknown` means the backend cannot establish a complete result. No status issues a write permit or establishes build/gameplay acceptance.

The query returns the observed revision, workspace-input fingerprint (nullable when unsafe/unavailable), managed paths, total counts and explicit truncation flags. It neither prepares dependencies nor writes workspace files. Execution uses the same plan and checks inputs again; changing source after a successful preview can still reject generation. Older hosts retain their normal unsupported-operation error. See [usage and limits](README.md#生成前只读预检) and the [adapter verification record](../../docs/testing/generation-preflight-2026-10-09.md).

## Verification

```bash
python -W error::ResourceWarning -m unittest discover -s sdk/python -p 'test_*.py'
```

The SDK regressions are protocol-level tests. They do not replace real Core, packaged-JAR or installed-client acceptance. See the [implementation record](../../docs/testing/agent-readiness-2026-10-09.md) for observed validation limits.

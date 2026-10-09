# Native SDK discovery and diagnostic changes

The [agent-readiness PRD](../../docs/roadmap/agent-readiness-prd-2026-10-09.md) separates the first SDK fixes from the remaining Core discovery work.

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

## Readable messages with raw evidence

Simple named placeholders in diagnostic fallbacks are replaced using `message.args`. For example, `{field}: {reason}` can render `/commands/0: Expected a non-null command string.`. Arguments are inserted literally, once. Missing or structured arguments remain visible as placeholders; attribute access, indexing and format expressions are not evaluated.

`NativeApiError.code` and `details` retain the original diagnostic envelope, including key, fallback and arguments. Message rendering does not replay rejected writes, change revisions or grant permissions.

## Finite task waiting

`wait_task(timeout=..., poll_interval=...)` requires positive finite values for both arguments. Invalid values are rejected before any poll. An ordinary task-wait timeout still leaves the task running; callers decide explicitly whether to poll again or cancel. It is not a transport timeout or an automatic retry.

## Verification

```bash
python -W error::ResourceWarning -m unittest discover -s sdk/python -p 'test_*.py'
```

The SDK regressions are protocol-level tests. They do not replace real Core, packaged-JAR or installed-client acceptance. See the [implementation record](../../docs/testing/agent-readiness-2026-10-09.md) for observed validation limits.

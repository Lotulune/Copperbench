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
    if error.code != "NATIVE_FIELD_CONTRACT_UNAVAILABLE":
        raise
    print(error)
    print(error.details["availableTypes"])
```

`available_field_contracts()` lists the keys actually published by the connected Core. These are not the complete set of creatable Mod Element types: generic input contracts may also be listed. `field_contract()` returns an existing contract unchanged. Missing item/recipe metadata is not proof that the type cannot be created.

**Compatibility note:** callers that previously caught the incidental `KeyError` for absent contracts must now catch `NativeApiError` and inspect `error.code`. A requested name absent from a valid contract map uses `NATIVE_FIELD_CONTRACT_UNAVAILABLE`; malformed metadata uses `NATIVE_INVALID_RESPONSE`; Core rejection codes are preserved. The SDK neither invents a contract nor creates a probe element. For an existing element, query `get_mod_element_editor` with its actual `elementId`.

The unavailable error also contains `reason="type_not_advertised"`, `nextAction`, `generator`, the existing-element `inspection` query and the original `environment` receipt. Missing/invalid `data.fieldContracts`, non-object contracts and invalid names are malformed responses, not unavailable features. An empty map is valid and lists no contracts. The current Core publishes partial item/recipe contracts; their `coverage="partial"` does not promise complete discovery or eight-track validation.

Both draft implementations are reconciled before release: `FIELD_CONTRACT_UNAVAILABLE` / `availableContracts` from the earlier readiness draft are replaced by the native SDK-prefixed code and `availableTypes`. The native prefix follows existing local SDK errors such as `NATIVE_INVALID_RESPONSE`; Core-owned diagnostic codes remain unchanged. The Stage 18 draft's malformed-as-unavailable behavior is corrected to preserve the invalid-response distinction.

## Readable messages with raw evidence

Simple named placeholders in diagnostic fallbacks are replaced using `message.args`. For example, `{field}: {reason}` can render `/commands/0: Expected a non-null command string.`. Arguments are inserted literally, once. Missing or structured arguments remain visible as placeholders; double braces, attribute access, indexing and format expressions are not evaluated.

`NativeApiError.code` and `details` retain the original diagnostic envelope, including key, fallback and arguments. Message rendering does not replay rejected writes, change revisions or grant permissions.

## Finite task waiting

`wait_task(timeout=..., poll_interval=...)` requires positive finite values for both arguments. Invalid values are rejected before any poll. An ordinary task-wait timeout still leaves the task running; callers decide explicitly whether to poll again or cancel. It is not a transport timeout or an automatic retry.

## Verification

```bash
python -W error::ResourceWarning -m unittest discover -s sdk/python -p 'test_*.py'
```

The SDK regressions are protocol-level tests. They do not replace real Core, packaged-JAR or installed-client acceptance. See the [implementation record](../../docs/testing/agent-readiness-2026-10-09.md) for observed validation limits.

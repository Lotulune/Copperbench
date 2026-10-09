# Locating generation source conflicts

Generation refuses to overwrite an existing base or element target unless the corresponding generator ownership record includes that file. Recorded generation inputs must also retain their protected bytes. These checks do not grant ownership, change the content revision, or provide an automatic overwrite repair.

`GENERATION_SOURCE_CONFLICT` task diagnostics now retain locations supplied by typed preparation failures. An example is:

```json
{
  "code": "GENERATION_SOURCE_CONFLICT",
  "severity": "error",
  "message": {
    "key": "diagnostic.generation_source_conflict_at_path",
    "fallback": "Generation stopped at {sourcePath}: {reason}",
    "args": {
      "sourcePath": "src/main/java/example/ExampleMod.java",
      "reasonCode": "UNOWNED_BASE_FILE",
      "ownership": "not_owned_by_generator",
      "reason": "The existing base file is not owned by this generator."
    }
  },
  "path": "/src/main/java/example/ExampleMod.java",
  "elementId": null,
  "recoverable": true,
  "actions": []
}
```

The leading slash on `diagnostic.path` follows the existing diagnostic contract: it denotes a workspace-relative location, not a host filesystem absolute path. `message.args.sourcePath` contains the same location without that slash. Runtime responses also contain task context arguments and an `open_logs` action. Existing Java files receive an `open_source` action only when the existing bounded task-source preview can safely capture their contents; other file types still expose their location. These previews are snapshots for the active task session, not permission to read arbitrary paths.

The fields use the current UI-Core v1.0 schema. No additional top-level diagnostic property or new operation is required. Agents should use the fields rather than parse translated text. More than one unowned candidate or changed recorded input can produce more than one diagnostic in the same failed task; these are the conflicts found during that preflight, not a complete proposed regeneration plan.

Localized messages have three parameter shapes: the legacy `diagnostic.generation_source_conflict` is a generic message without parameters; `diagnostic.generation_source_conflict_reason` requires only `reason`; and `diagnostic.generation_source_conflict_at_path` requires both `sourcePath` and `reason`. The English and Chinese dictionaries preserve those shapes, including when no safe location is available.

| `reasonCode` | `ownership` | Meaning |
| --- | --- | --- |
| `UNOWNED_BASE_FILE` | `not_owned_by_generator` | Existing base template target is absent from the base ownership record. |
| `UNOWNED_ELEMENT_FILE` | `not_owned_by_generator` | Existing element template target is absent from that element's associated files. |
| `SOURCE_CHANGED` | `recorded_input` | A recorded generation input changed; its recorded status does not authorize overwriting the new bytes. |
| `NON_REGULAR_FILE` | `unknown` | A source location exists but is not a regular file. |
| `AMBIGUOUS_USER_CODE_REGION` | `unknown` | User-code boundaries are nested, duplicated, or unnamed. |
| `MISMATCHED_USER_CODE_REGION` | `unknown` | A region end does not match its start. |
| `UNCLOSED_USER_CODE_REGION` | `unknown` | A region has no matching end. |
| `UNSAFE_PATH` | `unknown` | A path contains a symbolic link or other filesystem redirection; no source preview is provided. |
| `PATH_OUTSIDE_WORKSPACE` | `unknown` | A target leaves the workspace; its path is withheld. |
| `INVALID_SOURCE_PATH` | `unknown` | No safe portable workspace-relative file location is available; its path is withheld. |

`not_owned_by_generator` describes this generator's recorded authority, not the identity of an author or a claim that the file has no owner. `recorded_input` describes a concurrency check, not a new grant of ownership.

Old three-argument `GenerationPreparationException` callers remain compatible. They produce the existing conflict code with a generic explanation and a null path. Unknown exception messages and causes are never mined for locations; the raw cause is not included in source-conflict task diagnostics or task logs. Unsafe relative paths are rejected when typed details are created.

For a native-source workspace, adding managed elements can reveal an existing base-file ownership conflict. Inspect the reported files and retain the failed task evidence. Continue native authoring separately if needed; do not mark hand-authored files as generator-owned merely to bypass the guard. A full native/managed/mixed workflow preview and migration flow is separate future work.

The targeted Java tests are `GenerationPreparationExceptionTest`, `MCreatorGenerationPreparationTest`, and `GenerationConflictTaskDiagnosticTest`. They cover multiple candidates, unchanged bytes and revisions, malformed user-code boundaries, outside paths, redirections, legacy and unknown exceptions, public diagnostic conversion, safe preview scope, and persisted diagnostic locations.

# Versioned editor queries

`get_mod_element_editor` has an explicit **1.1** request/response schema. Other operations and the session handshake remain at frozen **1.0**. See the [1.1 schema](../../ui-core/schemas/v1.1/editor.schema.json); the existing [1.0 schema](../../ui-core/schemas/v1.0/query-result.schema.json) is unchanged.

Version 1.1 retains configuration drift information, source management, field contracts, input schemas, typed references and structured field conditions. It retains the established numeric-version block field contract separately from the item/recipe discovery contract. Receiving a rich response with a 1.0 label is not a compatible result.

Requests without an editor version use 1.0 at the Native and MCP boundaries. JCEF requests explicitly declare their version. Legacy responses contain only the original element, sections and capabilities; reference tokens use the original text/JSON controls, and localization keys use the frozen identifier syntax. Raw field values, element identity, permission decisions and server-side mutation validation are preserved. Legacy clients do not receive the newer editing metadata.

The shipped UI explicitly requests 1.1. The Native Python SDK also selects it for editor queries and checks the returned version:

```python
editor = workspace.query("get_mod_element_editor", elementId=element_id)
legacy = workspace.query("get_mod_element_editor", schema_version="1.0", elementId=element_id)
```

At the Native wire boundary, place `schemaVersion: "1.1"` beside `kind: "query"` and `operation: "get_mod_element_editor"`, outside `payload`. JCEF uses the same version on its `messageType: "query"` envelope. Unsupported versions and 1.1 requests for other operations are rejected. The clients do not silently accept a downgraded editor response.

MCP's existing `read_mod_element` tool accepts optional `schemaVersion: "1.0" | "1.1"`. Omitting it preserves the legacy response. SDK helpers select 1.1 explicitly:

```python
editor = client.read_mod_element(element_id)
legacy = client.read_mod_element(element_id, schema_version="1.0")
```

TypeScript uses `client.readModElement(elementId, '1.1')`. Direct `call_tool`/`callTool` callers choose the version in the tool arguments. Version 1.1 requires a product implementing that version; older products are not inferred to support it from a successful 1.0 handshake.

Local Java `UiCore.Query.of(...)` selects the current schema for its operation: 1.1 for editors, 1.0 for everything else. Wire adapters preserve the explicit peer selection. To construct a frozen editor request in Java, use `new UiCore.Query("query", "1.0", requestId, workspaceId, GET_MOD_ELEMENT_EDITOR, payload)`.

The block-only discovery shortcut (`payload: {"elementType": "block"}`) is confined to 1.1. Version 1.0 always requires `elementId`. Prefer the ordinary field-discovery API for creation metadata when that element exposes it.

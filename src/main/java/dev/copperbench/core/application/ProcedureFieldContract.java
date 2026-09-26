package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.procedure.ProcedureIr;
import dev.copperbench.procedure.ProcedureIrCodec;
import java.util.*;

/** Checks body representations without rejecting incomplete, structurally well-formed graph drafts. */
public final class ProcedureFieldContract {
    private static final ProcedureIrCodec CODEC = new ProcedureIrCodec();
    private static final UUID VALIDATION_ID = new UUID(0, 0);
    private ProcedureFieldContract() {}

    public static BlockFieldContract.Issue validate(JsonObject requested) {
        try {
            if (requested.has("fields")) {
                object(requested.get("fields"), "/fields");
                for (var entry : requested.getAsJsonObject("fields").entrySet())
                    if (requested.has(entry.getKey()) && !requested.get(entry.getKey()).equals(entry.getValue()))
                        fail("FIELD_ALIAS_CONFLICT", "/fields/" + escape(entry.getKey()), "Conflicting field spellings.");
            }
            JsonObject values = BlockFieldContract.merged(requested);
            for (String key : List.of("name", "displayName", "description", "procedurexml"))
                if (values.has(key)) string(values.get(key), BlockFieldContract.path(requested, key));
            if (values.has("procedurexml")) {
                try { CODEC.fromBlocklyXml(values.get("procedurexml").getAsString(), VALIDATION_ID); }
                catch (RuntimeException exception) { fail("PROCEDURE_XML_INVALID", BlockFieldContract.path(requested, "procedurexml"), "Expected well-formed Blockly XML with an xml root."); }
            }
            if (values.has("procedureIr")) {
                String path = BlockFieldContract.path(requested, "procedureIr");
                JsonObject json = object(values.get("procedureIr"), path);
                keys(json, Set.of("schemaVersion", "trigger", "nodes", "dependencies", "unknownRoot"), path);
                optionalString(json, "schemaVersion", path); optionalString(json, "trigger", path);
                if (json.has("schemaVersion") && !json.get("schemaVersion").getAsString().equals(ProcedureIr.SCHEMA_VERSION))
                    fail("PROCEDURE_IR_INVALID", path + "/schemaVersion", "Unsupported Procedure IR schema version.");
                if (json.has("unknownRoot")) object(json.get("unknownRoot"), path + "/unknownRoot");
                Set<UUID> ids = new HashSet<>();
                if (json.has("nodes")) {
                    JsonArray nodes = array(json.get("nodes"), path + "/nodes");
                    for (int i = 0; i < nodes.size(); i++) {
                        String at = path + "/nodes/" + i;
                        JsonObject node = object(nodes.get(i), at);
                        keys(node, Set.of("id", "type", "kind", "x", "y", "fields", "inputs", "next", "unknown", "rawPayload"), at);
                        if (!ids.add(uuid(node.get("id"), at + "/id"))) fail("PROCEDURE_IR_INVALID", at + "/id", "Duplicate node id would overwrite another node.");
                        string(node.get("type"), at + "/type"); optionalString(node, "kind", at); optionalString(node, "rawPayload", at);
                        for (String key : List.of("x", "y")) if (node.has(key)) {
                            JsonElement number = node.get(key);
                            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber() || !Double.isFinite(number.getAsDouble()))
                                fail("FIELD_TYPE_INVALID", at + "/" + key, "Expected a finite numeric coordinate.");
                        }
                        if (node.has("unknown") && (!node.get("unknown").isJsonPrimitive() || !node.getAsJsonPrimitive("unknown").isBoolean()))
                            fail("FIELD_TYPE_INVALID", at + "/unknown", "Expected a boolean.");
                        if (node.has("fields")) for (var field : object(node.get("fields"), at + "/fields").entrySet())
                            if (!field.getValue().isJsonPrimitive()) fail("FIELD_TYPE_INVALID", at + "/fields/" + escape(field.getKey()), "Blockly field values must be non-null scalars.");
                        if (node.has("inputs")) for (var input : object(node.get("inputs"), at + "/inputs").entrySet())
                            uuid(input.getValue(), at + "/inputs/" + escape(input.getKey()));
                        if (node.has("next") && !node.get("next").isJsonNull()) uuid(node.get("next"), at + "/next");
                    }
                }
                if (json.has("dependencies")) {
                    JsonArray dependencies = array(json.get("dependencies"), path + "/dependencies");
                    for (int i = 0; i < dependencies.size(); i++) {
                        String at = path + "/dependencies/" + i;
                        JsonObject dependency = object(dependencies.get(i), at);
                        keys(dependency, Set.of("id", "kind", "name", "dataType", "target"), at);
                        uuid(dependency.get("id"), at + "/id");
                        for (String key : List.of("kind", "name", "dataType", "target")) optionalString(dependency, key, at);
                    }
                }
                ProcedureIr ir;
                try { ir = CODEC.fromJson(json); }
                catch (RuntimeException exception) { fail("PROCEDURE_IR_INVALID", path, "Invalid Procedure IR."); return null; }
                for (int i = 0; i < ir.nodes().size(); i++) {
                    var node = ir.nodes().get(i);
                    if (node.unknown() && !CODEC.hasOpaqueBlockPayload(node.rawPayload(), node.type()))
                        fail("PROCEDURE_IR_INVALID", path + "/nodes/" + i + "/rawPayload", "Unknown nodes require one preserved Blockly block of the matching type.");
                    if (!node.unknown() && !node.rawPayload().isEmpty())
                        fail("FIELD_UNSUPPORTED", path + "/nodes/" + i + "/rawPayload", "Known nodes are rendered from structured fields, not rawPayload.");
                }
                String rendered = CODEC.toBlocklyXml(ir);
                try { CODEC.fromBlocklyXml(rendered, VALIDATION_ID); }
                catch (RuntimeException exception) { fail("PROCEDURE_IR_INVALID", path, "IR must export well-formed Blockly XML."); }
                if (values.has("procedurexml") && !CODEC.equivalentExports(rendered,
                        CODEC.toBlocklyXml(CODEC.fromBlocklyXml(values.get("procedurexml").getAsString(), VALIDATION_ID))))
                    fail("PROCEDURE_BODY_CONFLICT", BlockFieldContract.path(requested, "procedurexml"), "The supplied XML and IR export different block structures. Supply one body representation.");
            }
            return null;
        } catch (Invalid invalid) { return invalid.issue; }
    }

    public static void reconcileEditedAliases(JsonObject values, JsonArray changes) {
        Set<String> paths = new HashSet<>();
        changes.forEach(change -> paths.add(change.getAsJsonObject().get("path").getAsString()));
        Set<String> edited = new HashSet<>();
        for (String key : ElementMappingSupport.fields("procedure")) {
            boolean top = paths.stream().anyMatch(p -> p.equals("/" + key) || p.startsWith("/" + key + "/"));
            boolean nested = paths.stream().anyMatch(p -> p.equals("/fields/" + key) || p.startsWith("/fields/" + key + "/"))
                    || paths.contains("/fields") && values.has("fields") && values.get("fields").isJsonObject() && values.getAsJsonObject("fields").has(key);
            if (top || nested) edited.add(key);
            if (top && !nested && values.has("fields") && values.get("fields").isJsonObject()) values.getAsJsonObject("fields").remove(key);
            if (nested && !top) values.remove(key);
        }
        if (edited.contains("procedurexml") && !edited.contains("procedureIr")) remove(values, "procedureIr");
        if (edited.contains("procedureIr") && !edited.contains("procedurexml")) remove(values, "procedurexml");
    }

    public static void normalize(JsonObject values, UUID elementId) {
        JsonObject merged = BlockFieldContract.merged(values);
        for (String key : ElementMappingSupport.fields("procedure")) if (merged.has(key)) values.add(key, merged.get(key).deepCopy());
        if (values.has("procedureIr")) {
            ProcedureIr ir = CODEC.fromJson(values.getAsJsonObject("procedureIr"));
            values.add("procedureIr", CODEC.toJson(ir));
            if (!values.has("procedurexml")) values.addProperty("procedurexml", CODEC.toBlocklyXml(ir));
        } else if (values.has("procedurexml")) {
            values.add("procedureIr", CODEC.toJson(CODEC.fromBlocklyXml(values.get("procedurexml").getAsString(), elementId)));
        }
        refreshAliases(values);
    }
    public static void refreshAliases(JsonObject values) {
        if (values.has("fields") && values.get("fields").isJsonObject())
            for (String key : List.of("procedureIr", "procedurexml")) {
                if (!values.has(key)) values.getAsJsonObject("fields").remove(key);
                else values.getAsJsonObject("fields").add(key, values.get(key).deepCopy());
            }
    }
    public static JsonObject capabilities() {
        JsonObject result = new JsonObject(); result.addProperty("contractVersion", 1);
        result.addProperty("procedurexmlType", "string; well-formed Blockly XML with an xml root; original text is preserved");
        result.addProperty("procedureIrType", "Procedure IR 1.0 object; typed node, port and dependency members");
        result.addProperty("bodyPolicy", "A single edited representation refreshes the counterpart. Simultaneous XML and IR must export the same block structure; editor block ids are ignored when comparing.");
        result.addProperty("compatibilityPath", "/fields");
        result.addProperty("xmlPreservation", "Structured rewrites are blocked when XML contains data the IR cannot preserve. Metadata-only edits and explicit XML body replacement remain available.");
        result.addProperty("scope", "Body shape and persistence; incomplete graphs remain drafts. References, generation and gameplay require separate validation.");
        return result;
    }
    public static BlockFieldContract.Issue structuredEditIssue(JsonObject values) {
        JsonElement xml = BlockFieldContract.merged(values).get("procedurexml");
        if (xml == null) return null;
        if (!xml.isJsonPrimitive() || !xml.getAsJsonPrimitive().isString())
            return new BlockFieldContract.Issue("PROCEDURE_XML_PRESERVATION_REQUIRED", "/procedurexml", "Review the existing XML body before rewriting it.");
        String reason = CODEC.structuredEditingBlocker(xml.getAsString());
        return reason == null ? null : new BlockFieldContract.Issue("PROCEDURE_XML_PRESERVATION_REQUIRED", "/procedurexml",
                reason + " Preserve or explicitly replace the XML body instead of applying an IR rewrite.");
    }
    public static BlockFieldContract.Issue irEditIssue(JsonObject before, JsonArray changes) {
        boolean ir = false, xml = false;
        for (JsonElement raw : changes) {
            JsonObject change = raw.getAsJsonObject(); String path = change.get("path").getAsString();
            ir |= path.equals("/procedureIr") || path.startsWith("/procedureIr/") || path.equals("/fields/procedureIr") || path.startsWith("/fields/procedureIr/");
            xml |= path.equals("/procedurexml") || path.equals("/fields/procedurexml");
            if (path.equals("/fields") && change.has("value") && change.get("value").isJsonObject()) {
                ir |= change.getAsJsonObject("value").has("procedureIr"); xml |= change.getAsJsonObject("value").has("procedurexml");
            }
        }
        return ir && !xml ? structuredEditIssue(before) : null;
    }
    private static void remove(JsonObject values, String key) { values.remove(key); if (values.has("fields") && values.get("fields").isJsonObject()) values.getAsJsonObject("fields").remove(key); }
    private static String escape(String name) { return name.replace("~", "~0").replace("/", "~1"); }
    private static void keys(JsonObject object, Set<String> allowed, String path) { for (String key : object.keySet()) if (!allowed.contains(key)) fail("FIELD_UNSUPPORTED", path + "/" + escape(key), "Unknown structured IR members are not applied."); }
    private static void optionalString(JsonObject object, String key, String path) { if (object.has(key)) string(object.get(key), path + "/" + key); }
    private static void string(JsonElement value, String path) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) fail("FIELD_TYPE_INVALID", path, "Expected a non-null string."); }
    private static JsonObject object(JsonElement value, String path) { if (value == null || !value.isJsonObject()) fail("FIELD_TYPE_INVALID", path, "Expected an object."); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String path) { if (value == null || !value.isJsonArray()) fail("FIELD_TYPE_INVALID", path, "Expected an array."); return value.getAsJsonArray(); }
    private static UUID uuid(JsonElement value, String path) { string(value, path); try { return UUID.fromString(value.getAsString()); } catch (IllegalArgumentException exception) { fail("PROCEDURE_IR_INVALID", path, "Expected a UUID."); return null; } }
    private static void fail(String code, String path, String message) { throw new Invalid(new BlockFieldContract.Issue(code, path, message)); }
    private static final class Invalid extends RuntimeException { final BlockFieldContract.Issue issue; Invalid(BlockFieldContract.Issue issue) { this.issue = issue; } }
}

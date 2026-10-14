package dev.copperbench.migration;

import com.google.gson.*;
import dev.copperbench.core.workspace.WorkspaceFiles;
import dev.copperbench.core.workspace.WorkspaceState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Function;

/** Read-only, exact-version planning. Loader migration and its same-version rules remain separate. */
public final class VersionCopyUpgradeService {
    public static final String RULE_VERSION = "26.2-to-26.3-preview-v1";
    private static final Set<String> TYPES = Set.of("item", "block", "recipe", "function", "code");
    private static final Set<String> METADATA = Set.of("name", "displayName", "description");

    /** Plans from durable definition bytes, preserving the source's projected identities and ownership. */
    public JsonObject preview(WorkspaceState source, Path sourceRoot, String target,
            Function<WorkspaceState.Element, JsonObject> identities) throws IOException {
        String generator = source.generator().get("id").getAsString();
        String expected = switch (generator) {
            case "fabric-26.2" -> "fabric-26.3";
            case "neoforge-26.2" -> "neoforge-26.3";
            default -> throw new IllegalArgumentException("Only 26.2 to 26.3 copy upgrade is available.");
        };
        if (!expected.equals(target))
            throw new IllegalArgumentException("Choose the same loader's exact 26.3 target. Cross-loader changes require a separate operation.");
        if (sourceRoot == null) throw new IOException("The source directory is unavailable; reopen the saved workspace.");
        Path root = WorkspaceFiles.requireDirectory(sourceRoot);
        String before = WorkspaceTreeHasher.hash(root);
        JsonObject plan = new JsonObject();
        plan.addProperty("ruleVersion", RULE_VERSION);
        plan.addProperty("sourceWorkspaceId", source.id().toString());
        plan.addProperty("sourceRevision", source.revision());
        plan.addProperty("sourceGeneratorId", generator);
        plan.addProperty("targetGeneratorId", target);
        plan.addProperty("sourceFingerprint", before);
        plan.addProperty("copyOnly", true);
        plan.addProperty("executionAvailable", false);
        plan.addProperty("worldPolicy", "excluded_requires_separate_authorization");
        plan.addProperty("recovery", "continue_using_unchanged_source");
        JsonArray entries = new JsonArray();
        List<Path> workspaceFiles;
        try (var files = Files.list(root)) {
            workspaceFiles = files.filter(p -> p.getFileName().toString().endsWith(".mcreator")
                    && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
        }
        if (workspaceFiles.size() != 1) throw new IOException("The source must have one unambiguous .mcreator file.");
        JsonObject document = JsonParser.parseString(Files.readString(WorkspaceFiles.requireRegularFile(root, workspaceFiles.getFirst()), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!document.has("workspaceSettings") || !document.get("workspaceSettings").isJsonObject()
                || !generator.equals(document.getAsJsonObject("workspaceSettings").get("currentGenerator").getAsString()))
            throw new IOException("The saved generator changed; reopen the source before previewing.");
        JsonObject unknownWorkspace = document.deepCopy();
        unknownWorkspace.remove("workspaceSettings"); unknownWorkspace.remove("mod_elements");
        if (!unknownWorkspace.isEmpty()) entries.add(entry("/workspace/metadata", unknownWorkspace, "manual", "UNKNOWN_METADATA_PRESERVED",
                "Preserve workspace metadata and unknown fields without interpreting plugin behavior.",
                "Recover the exact metadata from the original workspace.", "spec:D11"));
        entries.add(entry("/generator", new JsonPrimitive(generator), "convertible", "EXACT_TARGET_SELECTED",
                "Use " + target + " only in a new workspace; generated code requires proven conversion rules.",
                "Reopen the original " + generator + " workspace.", "spec:D11"));
        JsonObject settings = document.getAsJsonObject("workspaceSettings").deepCopy(); settings.remove("currentGenerator");
        fields(entries, "/workspace/settings", settings, true, "workspace");
        for (var element : source.elements().stream().sorted(Comparator.comparing(e -> e.id().toString())).toList()) {
            String at = "/elements/" + element.id();
            JsonObject identity = identities.apply(element).deepCopy();
            if (!identity.has("internalName") || !identity.has("registryName"))
                throw new IOException("Element identity is unavailable: " + element.id());
            identity.addProperty("id", element.id().toString());
            identity.addProperty("ownership", element.ownership());
            entries.add(entry(at + "/identity", identity, "convertible", "IDENTITY_PRESERVED",
                    "Keep internalName, registryName, element UUID and source ownership unchanged.",
                    "Continue using the source element.", "spec:D11"));
            if (element.type().equals("code")) {
                // Code elements persist their bundle in workspace metadata, not a .mod.json definition.
                JsonObject stored = document.getAsJsonArray("mod_elements").asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(e -> identity.get("internalName").equals(e.get("name"))).findFirst()
                        .orElseThrow(() -> new IOException("Saved code identity is missing: " + element.id()));
                JsonObject metadata = stored.getAsJsonObject("metadata");
                JsonObject values = metadata.getAsJsonObject("dev.copperbench.values").deepCopy();
                Set<String> helpers = new HashSet<>();
                if (metadata.has("dev.copperbench.codeFiles")) metadata.getAsJsonArray("dev.copperbench.codeFiles").forEach(p -> helpers.add(p.getAsString()));
                var primaryPaths = metadata.getAsJsonArray("files").asList().stream().map(JsonElement::getAsString)
                        .filter(p -> p.endsWith(".java") && !helpers.contains(p)).toList();
                if (primaryPaths.size() != 1) throw new IOException("Code bundle has no unambiguous primary source: " + element.id());
                Path primary = WorkspaceFiles.requireRegularFile(root, root.resolve(primaryPaths.getFirst()));
                values.addProperty("code", Files.readString(primary, StandardCharsets.UTF_8));
                JsonObject fingerprints = new JsonObject(); fingerprints.addProperty("$primary", hash(Files.readAllBytes(primary)));
                if (values.has("codeFiles")) for (var file : values.getAsJsonArray("codeFiles")) {
                    var extra = file.getAsJsonObject(); String relative = extra.get("path").getAsString().replace('\\', '/');
                    Path path = WorkspaceFiles.requireRegularFile(primary.getParent(), primary.getParent().resolve(relative));
                    extra.addProperty("code", Files.readString(path, StandardCharsets.UTF_8)); fingerprints.addProperty(relative, hash(Files.readAllBytes(path)));
                }
                values.add("sourceFingerprints", fingerprints);
                dev.copperbench.core.application.CodeFieldContract.refreshStoredAliases(values);
                fields(entries, at + "/fields", values, true, "code");
                JsonObject envelope = metadata.deepCopy(); envelope.remove("dev.copperbench.values");
                entries.add(entry(at + "/sourceMetadata", envelope, "manual", "UNKNOWN_METADATA_PRESERVED",
                        "Preserve the saved source ownership, file associations and unknown metadata without rewriting Java.",
                        "Recover metadata and source bytes from the original workspace.", "spec:D11"));
                continue;
            }
            Path definition = WorkspaceFiles.requireRegularFile(root, root.resolve("elements")
                    .resolve(identity.get("internalName").getAsString() + ".mod.json"));
            JsonObject raw = JsonParser.parseString(Files.readString(definition, StandardCharsets.UTF_8)).getAsJsonObject();
            if (raw.has("_type") && !raw.get("_type").getAsString().equals(element.type()))
                throw new IOException("Element type changed on disk; reopen the workspace: " + definition.getFileName());
            boolean supportedType = TYPES.contains(element.type());
            if (!supportedType) entries.add(entry(at + "/type", new JsonPrimitive(element.type()), "unsupported", "TYPE_RULE_UNAVAILABLE",
                    "No conversion rule exists for this element type; keep its definition and resolve it manually in a separate copy.",
                    "Use the original workspace until a rule is proven.", "spec:D8,D11"));
            JsonObject values = raw.has("definition") && raw.get("definition").isJsonObject() ? raw.getAsJsonObject("definition") : raw;
            fields(entries, at + "/fields", values, supportedType, element.type());
            JsonObject unknownEnvelope = raw.deepCopy(); unknownEnvelope.remove("definition"); unknownEnvelope.remove("_type");
            if (!unknownEnvelope.isEmpty() && values != raw)
                entries.add(entry(at + "/definitionEnvelope", unknownEnvelope, "manual", "UNKNOWN_METADATA_PRESERVED",
                        "Preserve these raw metadata fields without interpretation; review any plugin behavior before execution.",
                        "Recover the exact values from the source definition.", "spec:D11"));
        }
        // Source and resource files are not certified by merely supporting the corresponding element type.
        for (Path path : WorkspaceFiles.regularFiles(root, relative -> WorkspaceTreeHasher.excluded(root, root.resolve(relative)))) {
            String relative = root.relativize(path).toString().replace('\\', '/');
            if (!relative.startsWith("src/")) continue;
            JsonObject file = new JsonObject(); file.addProperty("sha256", hash(Files.readAllBytes(path)));
            file.addProperty("bytes", Files.size(path));
            entries.add(entry("/files/" + relative, file, "manual", "FILE_SEMANTICS_UNPROVEN",
                    "Review this file against the target APIs and resources. Preserve manual ownership; no automatic rewrite is proven.",
                    "Recover the exact original file from the source workspace.", "spec:D11"));
        }
        String after = WorkspaceTreeHasher.hash(root);
        if (!before.equals(after)) throw new IOException("The source changed while previewing; stop its tasks and request a new plan.");
        plan.addProperty("sourceUnchanged", true);
        plan.add("entries", entries);
        JsonObject counts = new JsonObject();
        for (String disposition : List.of("convertible", "manual", "unsupported"))
            counts.addProperty(disposition, entries.asList().stream().filter(e -> disposition.equals(e.getAsJsonObject().get("disposition").getAsString())).count());
        plan.add("counts", counts);
        plan.addProperty("planId", hash(canonical(plan).getBytes(StandardCharsets.UTF_8)));
        return plan;
    }

    private static void fields(JsonArray entries, String base, JsonObject values, boolean supportedType, String type) {
        for (String name : new TreeSet<>(values.keySet())) {
            JsonElement value = values.get(name);
            String path = base + "/" + name.replace("~", "~0").replace("/", "~1");
            if (supportedType && METADATA.contains(name) && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                entries.add(entry(path, value, "convertible", "METADATA_PRESERVED",
                        "Preserve this creator metadata value unchanged.", "Recover the exact original value from the source.", "spec:D11"));
            } else {
                entries.add(entry(path, value, supportedType ? "manual" : "unsupported", supportedType ? "FIELD_RULE_UNPROVEN" : "TYPE_RULE_UNAVAILABLE",
                        "Review " + type + "." + name + " for 26.3. The value is retained; no semantic conversion rule is proven.",
                        "Keep using the original workspace until this field is resolved.", "spec:D8,D11"));
            }
        }
    }

    private static JsonObject entry(String path, JsonElement value, String disposition, String code, String meaning, String recovery, String evidence) {
        JsonObject result = new JsonObject(); result.addProperty("path", path); result.add("sourceValue", value.deepCopy());
        result.addProperty("disposition", disposition); result.addProperty("reasonCode", code);
        result.addProperty("targetMeaning", meaning); result.addProperty("recovery", recovery); result.addProperty("evidence", evidence);
        return result;
    }

    private static String canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) sorted.add(key, JsonParser.parseString(canonical(value.getAsJsonObject().get(key))));
            return sorted.toString();
        }
        if (value.isJsonArray()) { JsonArray array = new JsonArray(); value.getAsJsonArray().forEach(v -> array.add(JsonParser.parseString(canonical(v)))); return array.toString(); }
        return value.toString();
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}

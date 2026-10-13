package dev.copperbench.migration;

import com.google.gson.*;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.workspace.*;
import net.mcreator.element.GeneratableElement;
import net.mcreator.plugin.PluginLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Version 2's bounded function rule. Every source file is classified before reserving the target. */
public final class VersionCopyUpgradeExecution {
    public static final String RULE_VERSION = "26.2-to-26.3-function-v1";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final WorkspaceMutationGateway backend;

    public VersionCopyUpgradeExecution(WorkspaceMutationGateway backend) { this.backend = backend; }

    public record Inspection(JsonObject plan, JsonObject document, Path workspaceFile,
            Map<String, JsonObject> definitions, List<String> copiedFiles) {}

    public Inspection inspect(WorkspaceState state, Path source, String target, String outputName) throws Exception {
        String sourceGenerator = state.generator().get("id").getAsString();
        String loader = switch (sourceGenerator) {
            case "fabric-26.2" -> "fabric";
            case "neoforge-26.2" -> "neoforge";
            default -> throw new Rejection("UPGRADE_RULE_UNAVAILABLE", "Choose a 26.2 source with a proven same-loader function rule.");
        };
        if (!(loader + "-26.3").equals(target))
            throw new Rejection("UPGRADE_RULE_UNAVAILABLE", "Only the same loader's exact 26.3 target can be copied.");
        Path root = WorkspaceFiles.requireDirectory(source);
        Path destination = destination(root, outputName);
        String before = fingerprint(root);
        List<Path> all = inventory(root);
        var workspaceFiles = all.stream().filter(p -> p.getParent().equals(root) && p.getFileName().toString().endsWith(".mcreator")).toList();
        if (workspaceFiles.size() != 1) throw new IOException("The source must contain one .mcreator document.");
        Path workspaceFile = workspaceFiles.getFirst();
        JsonObject document = read(workspaceFile);
        var settings = document.getAsJsonObject("workspaceSettings");
        if (!state.generator().get("id").equals(settings.get("currentGenerator")))
            throw new Rejection("UPGRADE_SOURCE_CHANGED", "The saved generator changed. Reopen the source.");
        var targetIssues = new WorkspaceCreationService().validateCreation(target, string(settings, "modName"),
                string(settings, "modid"), string(settings, "modElementsPackage"), destination.toString(), true);
        if (!targetIssues.isEmpty()) throw new Rejection("UPGRADE_TARGET_INVALID", "Target preflight: " + String.join(", ", targetIssues));
        var product = document.getAsJsonObject("dev.copperbench");
        if (product == null || !state.id().toString().equals(product.get("workspaceId").getAsString())
                || state.revision() != product.get("revision").getAsLong())
            throw new Rejection("UPGRADE_SOURCE_CHANGED", "The saved workspace identity or revision changed.");
        JsonArray entries = new JsonArray();
        checkOwnedFiles(root, document, "/workspace/ownership", entries);
        entry(entries, "/workspace", document, true, "DOCUMENT_PRESERVED",
                "Preserve names, element UUIDs and opaque unknown data. Assign a new workspace UUID and revision zero; replace only generated-file associations and generator identity.");
        // Nonempty behavior beyond this rule cannot be certified by copying its metadata.
        for (String key : List.of("requiredMods", "dependencies", "dependants", "mcreatorDependencies", "updateURL", "modPicture", "credits", "description", "author", "websiteURL"))
            if (!empty(settings.get(key))) entry(entries, "/workspace/settings/" + key, settings.get(key), false,
                    "SETTING_RULE_UNPROVEN", "Resolve this setting before copying; this rule does not convert its behavior.");
        if (settings.has("serverSideOnly") && settings.get("serverSideOnly").getAsBoolean())
            entry(entries, "/workspace/settings/serverSideOnly", settings.get("serverSideOnly"), false, "SETTING_RULE_UNPROVEN", "Only the ordinary client-and-server metadata is proven.");
        for (String key : List.of("variable_elements", "sound_elements", "tag_elements", "tab_element_order", "language_map"))
            if (!empty(document.get(key))) entry(entries, "/workspace/" + key, document.get(key), false,
                    "WORKSPACE_DATA_UNPROVEN", "Resolve workspace behavior outside the function rule.");
        if (document.has("metadata") && !empty(document.getAsJsonObject("metadata").get("dev.copperbench.pendingGeneration")))
            entry(entries, "/workspace/pendingGeneration", document.getAsJsonObject("metadata").get("dev.copperbench.pendingGeneration"), false,
                    "SOURCE_GENERATION_PENDING", "Finish source generation before copying.");
        Map<String, JsonObject> definitions = new TreeMap<>();
        List<String> copied = new ArrayList<>();
        copied.add(root.relativize(workspaceFile).toString());
        Set<UUID> found = new HashSet<>();
        Set<String> resourceNames = new HashSet<>();
        for (JsonElement value : document.getAsJsonArray("mod_elements")) {
            JsonObject element = value.getAsJsonObject();
            String name = element.get("name").getAsString();
            if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) throw new IOException("Unsafe element filename: " + name);
            var projected = state.elements().stream().filter(e -> name.equals(backend.elementIdentity(e).get("internalName").getAsString())).findFirst().orElse(null);
            if (projected == null || !found.add(projected.id())) throw new IOException("Ambiguous saved element identity: " + name);
            if (!element.has("metadata")) element.add("metadata", new JsonObject());
            element.getAsJsonObject("metadata").addProperty("dev.copperbench.elementId", projected.id().toString());
            String at = "/elements/" + projected.id();
            checkOwnedFiles(root, element, at + "/ownership", entries);
            JsonObject identity = backend.elementIdentity(projected);
            if (!identity.get("registryName").equals(element.get("registry_name"))) throw new IOException("Saved registry identity changed: " + name);
            if (!"function".equals(element.get("type").getAsString()) || !"function".equals(projected.type())
                    || element.get("locked_code").getAsBoolean() || "manual".equals(projected.ownership())) {
                entry(entries, at, element, false, "ELEMENT_RULE_UNPROVEN", "Retain this element and its manual ownership in the source; only unlocked functions are executable.");
                continue;
            }
            Path file = WorkspaceFiles.requireRegularFile(root, root.resolve("elements/" + name + ".mod.json"));
            var raw = read(file);
            if (!raw.has("_fv") || raw.get("_fv").getAsBigDecimal().intValueExact() != GeneratableElement.formatVersion
                    || !"function".equals(raw.get("_type").getAsString()) || !raw.get("definition").isJsonObject()) {
                entry(entries, at + "/definition", raw, false, "ELEMENT_FORMAT_UNPROVEN", "Reopen and explicitly convert the definition in its source version before planning.");
                continue;
            }
            var data = raw.getAsJsonObject("definition");
            var declared = dev.copperbench.core.application.BlockFieldContract.merged(projected.values());
            boolean reconciled = true;
            for (String key : List.of("code", "name", "namespace"))
                if (declared.has(key) && !declared.get(key).equals(data.get(key))) reconciled = false;
            if (declared.has("commands") && !dev.copperbench.core.application.FunctionFieldContract.body(declared.getAsJsonArray("commands")).equals(string(data, "code")))
                reconciled = false;
            if (!reconciled) entry(entries, at + "/declaredFields", declared, false, "SOURCE_FIELDS_DIVERGED",
                    "The editor values and saved definition differ. Reconcile them in the source before copying.");
            boolean accepted = string(data, "name").matches("[a-z0-9_]+(?:/[a-z0-9_]+)*")
                    && resourceNames.add(string(data, "name")) && "mod".equals(string(data, "namespace"))
                    && supportedCode(string(data, "code"));
            entry(entries, at + "/definition", raw, accepted, accepted ? "FUNCTION_COMMANDS_PROVEN" : "FUNCTION_RULE_UNPROVEN",
                    "Preserve command bytes and opaque unknown fields. Only mod-namespace functions with bounded scoreboard and absolute setblock commands have a rule.");
            if (accepted) { definitions.put(name, raw); copied.add("elements/" + name + ".mod.json"); }
        }
        if (found.size() != state.elements().size()) throw new IOException("The durable element list changed. Reopen the source.");
        if (definitions.isEmpty()) entry(entries, "/elements", new JsonArray(), false, "FUNCTION_REQUIRED", "This rule requires at least one supported function.");
        Map<String, byte[]> baseline = Map.of();
        // Never let upstream template discovery read definitions requiring converters or unknown models.
        if (!blocked(entries)) baseline = backend.versionCopyBaseline(document, definitions);
        for (Path file : all) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (copied.contains(relative)) continue;
            JsonObject facts = new JsonObject(); facts.addProperty("bytes", Files.size(file)); facts.addProperty("sha256", fileHash(file));
            if (Set.of(".eclipse/configurations/Client.launch", ".eclipse/configurations/Server.launch").contains(relative)) {
                entry(entries, "/files/" + relative, facts, true, "SOURCE_LAUNCH_PROFILE_EXCLUDED",
                        "Keep the source IDE launch profile in the source. The new workspace prepares its own launch paths; never reuse the original run directory or world.");
            } else if (excludedFromCopy(relative)) {
                entry(entries, "/files/" + relative, facts, true, "RUNTIME_EXCLUDED", "Leave source caches, task records, run directories and worlds in the source; create no target world.");
            } else if (baseline.containsKey(relative) && Arrays.equals(baseline.get(relative), Files.readAllBytes(file))) {
                entry(entries, "/files/" + relative, facts, true, "GENERATED_BASELINE_PROVEN", "Matches the exact source template bytes. Regenerate with the exact target plugin.");
            } else if (relative.matches("src/main/resources/assets/[a-z0-9_.]+/lang/[a-z_]+\\.json") && read(file).isEmpty()) {
                entry(entries, "/files/" + relative, facts, true, "EMPTY_LANGUAGE_PROVEN", "An empty language resource has no entries to convert.");
            } else {
                entry(entries, "/files/" + relative, facts, false, "FILE_RULE_UNPROVEN", "The file is unowned, changed or outside the proven baseline. Keep the source bytes and resolve it manually.");
            }
        }
        for (String name : definitions.keySet()) {
            String resource = "src/main/resources/data/" + string(settings, "modid") + "/function/"
                    + string(definitions.get(name).getAsJsonObject("definition"), "name") + ".mcfunction";
            if (!Files.isRegularFile(root.resolve(resource), LinkOption.NOFOLLOW_LINKS))
                entry(entries, "/missing/" + resource, JsonNull.INSTANCE, false, "GENERATED_FUNCTION_MISSING", "Generate the function in the source before copying.");
        }
        if (!before.equals(fingerprint(root))) throw new Rejection("UPGRADE_SOURCE_CHANGED", "The source changed while planning.");
        JsonObject plan = new JsonObject();
        plan.addProperty("ruleVersion", loader + "-" + RULE_VERSION); plan.addProperty("sourceWorkspaceId", state.id().toString());
        plan.addProperty("sourceRevision", state.revision()); plan.add("sourceGeneratorId", state.generator().get("id"));
        plan.addProperty("targetGeneratorId", target); plan.addProperty("targetDirectory", destination.toString());
        plan.addProperty("outputName", outputName); plan.addProperty("sourceFingerprint", before);
        plan.addProperty("targetPluginFingerprint", targetPluginFingerprint(target));
        plan.addProperty("copyOnly", true); plan.addProperty("sourceUnchanged", true);
        plan.addProperty("executionAvailable", !blocked(entries));
        plan.addProperty("worldPolicy", "excluded_requires_separate_authorization");
        plan.addProperty("recovery", "continue_using_unchanged_source"); plan.add("entries", entries);
        JsonObject counts = new JsonObject();
        for (String kind : List.of("convertible", "manual", "unsupported"))
            counts.addProperty(kind, entries.asList().stream().filter(e -> kind.equals(e.getAsJsonObject().get("disposition").getAsString())).count());
        plan.add("counts", counts); plan.addProperty("planId", hash(canonical(plan).getBytes(StandardCharsets.UTF_8)));
        return new Inspection(plan, document, workspaceFile, Map.copyOf(definitions), List.copyOf(copied));
    }

    /** Called only with a freshly recomputed and authorized plan, while the source revision is coordinated. */
    public JsonObject execute(Path source, Inspection inspection) throws Exception {
        JsonObject plan = inspection.plan();
        if (!plan.get("executionAvailable").getAsBoolean()) throw new Rejection("UPGRADE_MANUAL_TASKS_REQUIRED", "Resolve all manual tasks before execution.");
        Path root = WorkspaceFiles.requireDirectory(source);
        Path target = destination(root, plan.get("outputName").getAsString());
        if (!target.toString().equals(plan.get("targetDirectory").getAsString()) || !fingerprint(root).equals(plan.get("sourceFingerprint").getAsString()))
            throw new Rejection("UPGRADE_PLAN_STALE", "The source or destination changed. Request a new plan.");
        // Atomic reservation; even an empty pre-existing directory belongs to another operation.
        Files.createDirectory(target);
        JsonObject receipt = new JsonObject(); receipt.addProperty("planId", plan.get("planId").getAsString());
        receipt.add("ruleVersion", plan.get("ruleVersion")); receipt.addProperty("targetDirectory", target.toString());
        receipt.addProperty("sourceWorkspaceId", plan.get("sourceWorkspaceId").getAsString());
        receipt.addProperty("sourceFingerprint", plan.get("sourceFingerprint").getAsString());
        receipt.addProperty("status", "failed"); receipt.addProperty("conversionComplete", false);
        receipt.addProperty("acceptance", "not_run"); receipt.addProperty("worldsCopied", false);
        Path marker = target.resolve("version-copy-upgrade-result.json");
        try {
            write(target, marker, receipt);
            for (String relative : inspection.copiedFiles()) {
                Path input = WorkspaceFiles.requireRegularFile(root, root.resolve(relative));
                Path output = WorkspaceFiles.requireWithin(target, target.resolve(relative));
                Files.createDirectories(output.getParent()); Files.copy(input, output, LinkOption.NOFOLLOW_LINKS);
            }
            JsonObject copy = inspection.document().deepCopy();
            String generator = plan.get("targetGeneratorId").getAsString();
            copy.getAsJsonObject("workspaceSettings").addProperty("currentGenerator", generator);
            UUID id = UUID.randomUUID(); var metadata = copy.getAsJsonObject("dev.copperbench");
            metadata.addProperty("workspaceId", id.toString()); metadata.addProperty("revision", 0);
            if (metadata.has("generator")) {
                var descriptor = metadata.getAsJsonObject("generator"); descriptor.addProperty("id", generator);
                descriptor.addProperty("loader", generator.startsWith("fabric-") ? "fabric" : "neoforge"); descriptor.addProperty("minecraftVersion", "26.3");
                descriptor.addProperty("displayName", generator.startsWith("fabric-") ? "Fabric 26.3" : "NeoForge 26.3");
            }
            // Generated ownership paths are derived again in the new copy. Unknown metadata is retained.
            if (copy.has("metadata")) copy.getAsJsonObject("metadata").remove("files");
            for (JsonElement item : copy.getAsJsonArray("mod_elements")) item.getAsJsonObject().getAsJsonObject("metadata").remove("files");
            Path outputDocument = target.resolve(inspection.workspaceFile().getFileName());
            write(target, outputDocument, copy);
            backend.generateVersionCopy(outputDocument);
            JsonObject savedCopy = read(outputDocument);
            withoutAssociations(copy); withoutAssociations(savedCopy);
            if (!preserved(copy, savedCopy)) throw new IOException("Target generation changed a preserved identity, setting or unknown metadata value.");
            // The raw definitions are immutable copy inputs, including unknown fields and whitespace.
            for (String relative : inspection.copiedFiles()) if (relative.startsWith("elements/")) {
                if (!Arrays.equals(Files.readAllBytes(root.resolve(relative)), Files.readAllBytes(target.resolve(relative))))
                    throw new IOException("Target generation altered a copied raw definition: " + relative);
            }
            boolean unchanged = fingerprint(root).equals(plan.get("sourceFingerprint").getAsString());
            receipt.addProperty("sourceUnchanged", unchanged);
            if (!unchanged) throw new IOException("The source changed during copy generation; target is not accepted.");
            receipt.addProperty("targetWorkspaceId", id.toString()); receipt.addProperty("targetWorkspaceFile", outputDocument.toString());
            receipt.addProperty("targetGeneratorId", generator); receipt.addProperty("targetRevision", 0);
            receipt.addProperty("status", "prepared"); receipt.addProperty("conversionComplete", true);
            write(target, marker, receipt); return receipt;
        } catch (Exception failure) {
            receipt.addProperty("status", "failed"); receipt.addProperty("conversionComplete", false);
            receipt.addProperty("detail", dev.copperbench.automation.audit.SensitiveDataRedactor.redact(String.valueOf(failure.getMessage())));
            try { receipt.addProperty("sourceUnchanged", fingerprint(root).equals(plan.get("sourceFingerprint").getAsString())); write(target, marker, receipt); }
            catch (Exception markerFailure) { failure.addSuppressed(markerFailure); }
            return receipt;
        }
    }

    public static Path destination(Path root, String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || name.endsWith(".")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
            throw new Rejection("UPGRADE_TARGET_INVALID", "Choose a simple new sibling directory name.");
        Path source = WorkspaceFiles.requireDirectory(root);
        Path target = WorkspaceFiles.requireWithin(source.getParent(), source.getParent().resolve(name));
        if (!target.getParent().equals(source.getParent()) || target.equals(source))
            throw new Rejection("UPGRADE_TARGET_INVALID", "The copy must be in a separate sibling directory.");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new Rejection("UPGRADE_TARGET_EXISTS", "The target already exists. Choose a new directory name.");
        return target;
    }

    static boolean supportedCode(String code) {
        if (code.isBlank() || code.length() > 65536 || code.indexOf('\0') >= 0) return false;
        for (String raw : code.split("\\R", -1)) {
            String line = raw.strip(); if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.matches("scoreboard objectives add [A-Za-z0-9_]{1,16} dummy")) continue;
            if (line.matches("scoreboard players set [A-Za-z0-9_]{1,40} [A-Za-z0-9_]{1,16} -?[0-9]{1,10}")) {
                try { Integer.parseInt(line.substring(line.lastIndexOf(' ') + 1)); continue; } catch (NumberFormatException ignored) { return false; }
            }
            if (line.matches("setblock -?[0-9]{1,7} -?[0-9]{1,3} -?[0-9]{1,7} minecraft:(gold_block|stone|air)")) continue;
            return false;
        }
        return true;
    }
    private static boolean excludedFromCopy(String relative) {
        String top = relative.split("/", 2)[0];
        return Set.of("run", "run-client", "run-server", "build", ".gradle", ".git", ".mcreator", ".copperbench").contains(top);
    }
    private static boolean empty(JsonElement value) {
        return value == null || value.isJsonNull() || (value.isJsonArray() && value.getAsJsonArray().isEmpty())
                || (value.isJsonObject() && value.getAsJsonObject().entrySet().stream().allMatch(e -> empty(e.getValue()))) || (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().isEmpty());
    }
    private static void withoutAssociations(JsonObject document) {
        if (document.has("metadata")) document.getAsJsonObject("metadata").remove("files");
        for (var item : document.getAsJsonArray("mod_elements"))
            if (item.getAsJsonObject().has("metadata")) item.getAsJsonObject().getAsJsonObject("metadata").remove("files");
    }
    private static void checkOwnedFiles(Path root, JsonObject document, String at, JsonArray entries) throws IOException {
        if (!document.has("metadata") || !document.getAsJsonObject("metadata").has("files")) return;
        for (var item : document.getAsJsonObject("metadata").getAsJsonArray("files")) {
            String relative = item.getAsString();
            Path path = WorkspaceFiles.requireWithin(root, root.resolve(relative));
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                entry(entries, at + "/" + relative, item, false, "OWNED_FILE_MISSING", "A recorded source file is missing. Restore or reconcile its ownership before copying.");
        }
    }
    private static boolean preserved(JsonElement expected, JsonElement actual) {
        if (actual == null) return false;
        if (expected.isJsonObject() && actual.isJsonObject())
            return expected.getAsJsonObject().entrySet().stream().allMatch(e -> preserved(e.getValue(), actual.getAsJsonObject().get(e.getKey())));
        if (expected.isJsonArray() && actual.isJsonArray()) {
            if (expected.getAsJsonArray().size() != actual.getAsJsonArray().size()) return false;
            for (int i = 0; i < expected.getAsJsonArray().size(); i++) if (!preserved(expected.getAsJsonArray().get(i), actual.getAsJsonArray().get(i))) return false;
            return true;
        }
        return expected.equals(actual);
    }
    private static boolean blocked(JsonArray entries) { return entries.asList().stream().anyMatch(e -> !e.getAsJsonObject().get("disposition").getAsString().equals("convertible")); }
    private static String string(JsonObject data, String name) { return data.has(name) && data.get(name).isJsonPrimitive() && data.getAsJsonPrimitive(name).isString() ? data.get(name).getAsString() : ""; }
    private static JsonObject read(Path path) throws IOException { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static List<Path> inventory(Path root) throws IOException {
        return WorkspaceFiles.regularFiles(root, p -> p.startsWith(".git") || p.toString().replace('\\', '/').equals(".copperbench/workspace.write.lock"));
    }
    public static String fingerprint(Path root) throws IOException {
        root = WorkspaceFiles.requireDirectory(root);
        MessageDigest digest = digest();
        for (Path path : inventory(root)) {
            digest.update(root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            // NUL-free paths and fixed-length file hashes frame each entry unambiguously.
            // Raw binary contents could otherwise masquerade as a following path and file.
            digest.update(fileHash(path).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String targetPluginFingerprint(String target) throws IOException {
        MessageDigest digest = digest();
        var resources = new TreeSet<>(PluginLoader.INSTANCE.getResourcesInPackage(target));
        if (resources.isEmpty()) throw new IOException("Target plugin resources are unavailable.");
        for (String name : resources) {
            digest.update(name.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            try (var input = PluginLoader.INSTANCE.getResourceAsStream(name)) { if (input == null) throw new IOException("Missing target resource: " + name); digest.update(input.readAllBytes()); }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void write(Path root, Path path, JsonObject data) throws IOException { WorkspaceFiles.writeAtomically(root, path, stream -> stream.write(JSON.toJson(data).getBytes(StandardCharsets.UTF_8))); }
    private static void entry(JsonArray entries, String path, JsonElement value, boolean accepted, String code, String meaning) {
        JsonObject entry = new JsonObject(); entry.addProperty("path", path); entry.add("sourceValue", value.deepCopy());
        entry.addProperty("disposition", accepted ? "convertible" : "manual"); entry.addProperty("reasonCode", code);
        entry.addProperty("targetMeaning", meaning); entry.addProperty("recovery", "Continue using the unchanged source workspace.");
        entry.addProperty("evidence", "version-copy-upgrade:function-rule-v1"); entries.add(entry);
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static String fileHash(Path path) throws IOException {
        MessageDigest digest = digest();
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192]; int length; while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String canonical(JsonElement value) {
        if (value.isJsonObject()) { var sorted = new JsonObject(); for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) sorted.add(key, JsonParser.parseString(canonical(value.getAsJsonObject().get(key)))); return sorted.toString(); }
        if (value.isJsonArray()) { var items = new JsonArray(); value.getAsJsonArray().forEach(v -> items.add(JsonParser.parseString(canonical(v)))); return items.toString(); }
        return value.toString();
    }
    public static final class Rejection extends IOException {
        public final String code;
        public Rejection(String code, String message) { super(message); this.code = code; }
    }
}

package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.GenerationPreflight;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

/** Shared, read-only planning for discovery and execution. Does not render or prepare dependencies. */
final class MCreatorGenerationPlan {
    private final Path root;
    private final Set<Path> paths = new LinkedHashSet<>();
    private final JsonObject expected = new JsonObject();
    private final List<SourceConflict> conflicts = new ArrayList<>();
    private final Set<String> conflictKeys = new HashSet<>();
    private final Set<Path> manual = new HashSet<>();
    private String unavailable;
    private Throwable unavailableCause;

    private MCreatorGenerationPlan(Path root) { this.root = root.toAbsolutePath().normalize(); }

    static MCreatorGenerationPlan inspect(Workspace workspace, List<ModElement> elements) {
        var plan = new MCreatorGenerationPlan(workspace.getWorkspaceFolder().toPath());
        try {
            MCreatorGenerationPreparation.relative(plan.root, plan.root);
            if (workspace.getGenerator() == null || workspace.getGeneratorConfiguration() == null) {
                plan.unavailable = "GENERATOR_UNAVAILABLE";
                return plan;
            }
            // Validate definitions before template discovery loads them. Inspect raw ownership
            // metadata too: upstream getAssociatedFiles filters out escaping entries silently.
            for (ModElement element : workspace.getModElements()) {
                Path definition = workspace.getFolderManager().getModElementsDir().toPath()
                        .resolve(element.getName() + ".mod.json");
                if (plan.capture(definition) && !element.getTypeString().equals("code"))
                    plan.checkDefinitionFormat(definition, element);
                if (element.isCodeLocked() || element.getTypeString().equals("code"))
                    plan.manual.addAll(plan.owned(element.getMetadata("files")));
            }
            plan.capture(workspace.getFileManager().getWorkspaceFile().toPath());
            if (plan.unavailable != null || !plan.conflicts.isEmpty()) {
                if (plan.unavailable == null) plan.unavailable = "GENERATION_PLAN_INCOMPLETE";
                return plan;
            }
            plan.checkPending(workspace);
            Set<Path> baseOwned = plan.owned(workspace.getMetadata("files"));
            for (var template : workspace.getGenerator().getModBaseGeneratorTemplatesList())
                plan.target(template.getFile().toPath(), baseOwned, "UNOWNED_BASE_FILE", "unowned base file");
            plan.paths.addAll(baseOwned);
            for (ModElement element : elements) {
                Set<Path> owned = plan.owned(element.getMetadata("files"));
                var definition = element.getGeneratableElement();
                if (definition == null) {
                    plan.unavailable = "ELEMENT_DEFINITION_UNAVAILABLE";
                    continue;
                }
                for (var template : workspace.getGenerator().getModElementGeneratorTemplatesList(definition))
                    plan.target(template.getFile().toPath(), owned, "UNOWNED_ELEMENT_FILE", "unowned element file");
                plan.paths.addAll(owned);
            }
            for (Path path : plan.paths) {
                if (plan.manual.contains(path)) plan.add(new SourceConflict(plan.relativeOrNull(path),
                        "MANUAL_SOURCE_TARGET", "manual", "generation targets a manually owned file", null));
                plan.capture(path);
            }
        } catch (SourceConflict failure) {
            plan.add(failure);
            plan.unavailable = "GENERATION_PLAN_INCOMPLETE";
        } catch (RuntimeException failure) {
            // Corrupt generator/model metadata is not proof of safe ownership.
            plan.unavailable = "GENERATION_PLAN_UNAVAILABLE";
            plan.unavailableCause = failure;
            org.apache.logging.log4j.LogManager.getLogger(MCreatorGenerationPlan.class)
                    .warn("Generation preflight could not inspect generator metadata", failure);
        }
        return plan;
    }

    private Set<Path> owned(Object metadata) {
        Set<Path> owned = new LinkedHashSet<>();
        if (metadata == null) return owned;
        if (!(metadata instanceof List<?> files)) {
            unavailable = "OWNERSHIP_METADATA_INVALID";
            return owned;
        }
        for (Object file : files) {
            if (!(file instanceof String value) || value.isBlank()) {
                unavailable = "OWNERSHIP_METADATA_INVALID";
                continue;
            }
            try {
                Path path = root.resolve(value).toAbsolutePath().normalize();
                MCreatorGenerationPreparation.relative(root, path);
                owned.add(path);
            } catch (SourceConflict failure) { add(failure); }
            catch (java.nio.file.InvalidPathException failure) { unavailable = "OWNERSHIP_METADATA_INVALID"; }
        }
        return owned;
    }

    private void target(Path target, Set<Path> owned, String reason, String detail) {
        Path path = target.toAbsolutePath().normalize();
        try {
            String relative = MCreatorGenerationPreparation.relative(root, path);
            paths.add(path);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !owned.contains(path))
                add(new SourceConflict(relative, reason, manual.contains(path) ? "manual" : "unowned", detail, null));
        } catch (SourceConflict failure) { add(failure); }
    }

    private boolean capture(Path path) {
        try {
            String relative = MCreatorGenerationPreparation.relative(root, path);
            expected.addProperty(relative, MCreatorGenerationPreparation.hash(path));
            return true;
        } catch (IOException failure) { add(at(path, failure)); return false; }
    }

    private void checkDefinitionFormat(Path path, ModElement element) {
        // Upstream template discovery loads definitions and persists conversions on read.
        // Refuse input requiring conversion before calling it, including for locked elements
        // that participate in base templates. Do not trust a previously cached definition.
        try {
            JsonObject document;
            try (var reader = new java.io.InputStreamReader(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                document = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!document.has("_fv") || !document.get("_fv").isJsonPrimitive()
                    || !document.getAsJsonPrimitive("_fv").isNumber()
                    || !document.has("_type") || !document.get("_type").isJsonPrimitive()
                    || !document.getAsJsonPrimitive("_type").isString()
                    || !document.has("definition") || !document.get("definition").isJsonObject()) {
                unavailable = "ELEMENT_DEFINITION_INVALID";
                return;
            }
            int version = document.get("_fv").getAsBigDecimal().intValueExact();
            int current = net.mcreator.element.GeneratableElement.formatVersion;
            if (version > current) unavailable = "ELEMENT_FORMAT_UNSUPPORTED";
            else if (version < 0) unavailable = "ELEMENT_DEFINITION_INVALID";
            else if (!element.getTypeString().equals(document.get("_type").getAsString())
                    || element.getType().equals(net.mcreator.element.ModElementType.UNKNOWN))
                unavailable = "ELEMENT_DEFINITION_TYPE_MISMATCH";
            else {
                var converters = net.mcreator.element.converter.ConverterRegistry.getConvertersForModElementType(element.getType());
                if (converters != null && converters.stream().anyMatch(converter -> version < converter.getVersionConvertingTo()))
                    unavailable = "ELEMENT_CONVERSION_REQUIRED";
            }
        } catch (IOException | RuntimeException failure) {
            unavailable = "ELEMENT_DEFINITION_INVALID";
            unavailableCause = failure;
            org.apache.logging.log4j.LogManager.getLogger(MCreatorGenerationPlan.class)
                    .warn("Generation preflight could not inspect definition format", failure);
        }
    }

    private void checkPending(Workspace workspace) {
        try {
            JsonObject pending = MCreatorGenerationPreparation.pending(workspace);
            if (pending == null) return;
            JsonObject files = pending.getAsJsonObject("files");
            if (files == null || !pending.has("elements") || !pending.get("elements").isJsonArray()) {
                unavailable = "PENDING_METADATA_INVALID";
                return;
            }
            for (var entry : files.entrySet()) {
                Path path = root.resolve(entry.getKey()).toAbsolutePath().normalize();
                if (manual.contains(path)) continue;
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()
                        || !entry.getValue().getAsString().matches("absent|(?:generated-v1:)?[0-9a-f]{64}")) {
                    unavailable = "PENDING_METADATA_INVALID";
                    continue;
                }
                JsonObject single = new JsonObject(); single.add(entry.getKey(), entry.getValue());
                try { MCreatorGenerationPreparation.verify(root, single); }
                catch (IOException failure) { add(at(path, failure)); }
                capture(path);
            }
        } catch (RuntimeException failure) {
            unavailable = "PENDING_METADATA_INVALID";
            unavailableCause = failure;
            org.apache.logging.log4j.LogManager.getLogger(MCreatorGenerationPlan.class)
                    .warn("Generation preflight could not read pending metadata", failure);
        }
    }

    private SourceConflict at(Path path, IOException failure) {
        if (failure instanceof SourceConflict conflict)
            return new SourceConflict(relativeOrNull(path), conflict.reasonCode, conflict.observedOwnership,
                    conflict.detail, conflict);
        return new SourceConflict(relativeOrNull(path), "SOURCE_UNREADABLE", "unknown",
                "source could not be inspected", failure);
    }

    private String relativeOrNull(Path path) {
        path = path.toAbsolutePath().normalize();
        return path.startsWith(root) ? root.relativize(path).toString().replace('\\', '/') : null;
    }

    private void add(SourceConflict conflict) {
        if (conflictKeys.add(conflict.relativePath + "\0" + conflict.reasonCode)) {
            if (conflicts.size() < GenerationPreflight.MAX_CONFLICTS) conflicts.add(conflict);
        }
    }

    Set<Path> paths() { return Collections.unmodifiableSet(paths); }
    JsonObject expected() { return expected.deepCopy(); }

    JsonObject conflictDetails() {
        JsonObject result = new JsonObject();
        JsonArray items = new JsonArray(); conflicts.forEach(conflict -> items.add(conflict.projection()));
        result.add("conflicts", items);
        result.addProperty("conflictCount", conflictKeys.size());
        result.addProperty("conflictsTruncated", conflictKeys.size() > conflicts.size());
        return result;
    }

    void requireSafe() throws GenerationPreparationException {
        if (!conflicts.isEmpty()) {
            SourceConflict first = conflicts.getFirst();
            throw new GenerationPreparationException("GENERATION_SOURCE_CONFLICT",
                    GenerationPreparationDiagnostics.sourceConflict(first.getMessage()), first, conflictDetails());
        }
        if (unavailable != null)
            throw new GenerationPreparationException("GENERATION_PREFLIGHT_UNAVAILABLE",
                    "Generation ownership could not be established: " + unavailable, unavailableCause);
    }

    JsonObject preview(WorkspaceState state, boolean needsDependencies) {
        JsonObject result = GenerationPreflight.unknown(state, unavailable);
        if (unavailable == null) {
            try {
                result.addProperty("inputFingerprint", WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
                // The observation is never a lock; also notice changes during this read itself.
                if (conflicts.isEmpty()) MCreatorGenerationPreparation.verify(root, expected);
            } catch (SourceConflict failure) { add(failure); }
            catch (IOException failure) {
                unavailable = failure instanceof WorkspaceExecutionSnapshot.SnapshotException snapshot
                        ? snapshot.code() : "GENERATION_INPUT_UNREADABLE";
            }
        }
        result.addProperty("status", !conflicts.isEmpty() ? "conflicted" : unavailable == null ? "ready" : "unknown");
        result.addProperty("reasonCode", unavailable);
        JsonArray managed = new JsonArray();
        paths.stream().map(this::relativeOrNull).filter(Objects::nonNull).sorted()
                .limit(GenerationPreflight.MAX_PATHS).forEach(managed::add);
        result.add("managedPaths", managed);
        result.addProperty("managedPathCount", paths.size());
        result.addProperty("managedPathsTruncated", paths.size() > managed.size());
        conflictDetails().entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        result.addProperty("dependenciesRequired", needsDependencies);
        return result;
    }

    static final class SourceConflict extends IOException {
        final String relativePath, reasonCode, observedOwnership, detail;

        SourceConflict(String path, String reason, String observed, String detail, Throwable cause) {
            super("GENERATION_SOURCE_CONFLICT: " + detail + (path == null ? "" : ": " + path), cause);
            this.relativePath = path; this.reasonCode = reason; this.observedOwnership = observed; this.detail = detail;
        }

        JsonObject projection() {
            JsonObject result = new JsonObject();
            result.addProperty("relativePath", relativePath);
            result.addProperty("reasonCode", reasonCode);
            result.addProperty("expectedOwnership", "generated");
            result.addProperty("observedOwnership", observedOwnership);
            return result;
        }
    }
}

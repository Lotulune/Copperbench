package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.mcreator.MCreatorGenerationPlan.SourceConflict;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Prepares cold plugin workspaces in explicit tasks, keeping mutations and reads offline. */
final class MCreatorGenerationPreparation {
    static final String PENDING = "dev.copperbench.pendingGeneration";
    private final Workspace opened;
    private final RevisionedWorkspaceStore store;
    private final java.util.function.Function<Path, Fabric1211ProcessRunner> processRunners;

    MCreatorGenerationPreparation(Workspace opened, RevisionedWorkspaceStore store) {
        this(opened, store, javaHome -> Fabric1211ProcessRunner.system("unused-generator-sync", javaHome));
    }

    MCreatorGenerationPreparation(Workspace opened, RevisionedWorkspaceStore store,
            java.util.function.Function<Path, Fabric1211ProcessRunner> processRunners) {
        this.opened = opened; this.store = store; this.processRunners = processRunners;
    }

    static boolean needsDependencies(Workspace workspace) {
        if (workspace.getGenerator() == null || dev.copperbench.tracks.VersionTrackCatalog.official()
                .findGenerator(workspace.getWorkspaceSettings().getCurrentGenerator()).isEmpty()) return false;
        Path root = workspace.getWorkspaceFolder().toPath();
        if (!Files.isRegularFile(root.resolve("build.gradle")) || !Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.jar"))) return false;
        var cache = workspace.getGenerator().getGradleCache();
        return cache == null || !cache.getImportTree().getOrDefault("Block", List.of()).contains("net.minecraft.world.level.block.Block");
    }

    static boolean deferred(Workspace workspace) { return needsDependencies(workspace) || pending(workspace) != null; }

    static JsonObject pending(Workspace workspace) {
        Object value = workspace.getMetadata(PENDING);
        return value == null ? null : WorkspaceFileManager.gson.toJsonTree(value).getAsJsonObject();
    }

    static void verifyPending(Workspace workspace) throws IOException {
        JsonObject pending = pending(workspace);
        if (pending != null) {
            JsonObject files = pending.getAsJsonObject("files").deepCopy();
            // Older records included rollback paths belonging to manually owned elements.
            // Their optimistic concurrency is checked by the source writer, not generation.
            manualSourcePaths(workspace).forEach(files::remove);
            verify(workspace.getWorkspaceFolder().toPath(), files);
        }
    }

    private static Set<String> manualSourcePaths(Workspace workspace) throws IOException {
        Set<String> paths = new HashSet<>();
        Path root = workspace.getWorkspaceFolder().toPath();
        for (ModElement element : workspace.getModElements())
            if (element.isCodeLocked() || element.getTypeString().equals("code"))
                for (var file : element.getAssociatedFiles()) paths.add(relative(root, file.toPath()));
        return paths;
    }

    static void recordPending(Workspace workspace, Collection<Path> paths, Collection<String> names) throws IOException {
        if (!deferred(workspace)) return;
        JsonObject pending = pending(workspace);
        if (pending == null) { pending = new JsonObject(); pending.add("files", new JsonObject()); pending.add("elements", new JsonArray()); }
        Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
        Set<String> manual = manualSourcePaths(workspace);
        manual.forEach(pending.getAsJsonObject("files")::remove);
        for (Path path : paths) {
            String relative = relative(root, path);
            if (!manual.contains(relative)) pending.getAsJsonObject("files").addProperty(relative, hash(path));
        }
        // Cold setup rebuilds the complete managed registry, including sources already rendered without imports.
        for (ModElement element : workspace.getModElements()) if (!element.isCodeLocked() && !element.getTypeString().equals("code")) {
            for (var file : element.getAssociatedFiles()) {
                String relative = relative(root, file.toPath());
                if (!pending.getAsJsonObject("files").has(relative)) pending.getAsJsonObject("files").addProperty(relative, hash(file.toPath()));
            }
        }
        Set<String> selected = new LinkedHashSet<>();
        pending.getAsJsonArray("elements").forEach(value -> selected.add(value.getAsString())); selected.addAll(names);
        JsonArray elements = new JsonArray(); selected.forEach(elements::add); pending.add("elements", elements);
        workspace.putMetadata(PENDING, WorkspaceFileManager.gson.fromJson(pending, Object.class));
    }

    void prepare(WorkspaceState state, Path target, Operation operation, Consumer<String> output) throws Exception {
        var codec = new dev.copperbench.procedure.ProcedureIrCodec();
        for (var element : state.elements()) {
            if (!element.type().equals("procedure") || element.ownership().equals("manual")) continue;
            var ir = codec.read(dev.copperbench.core.application.BlockFieldContract.merged(element.values()), element.id());
            var issues = MCreatorProcedureContextValidation.validate(opened, ir);
            if (!issues.isEmpty()) {
                var issue = issues.getFirst();
                throw new dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException(issue.code(),
                        "Procedure " + element.name() + ": " + issue.message(), null);
            }
        }
        try { prepareImpl(state, target, operation, output); }
        catch (dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException exception) {
            throw exception;
        }
        catch (IOException | UncheckedIOException exception) {
            Throwable cause = exception instanceof UncheckedIOException wrapped ? wrapped.getCause() : exception;
            String message = Objects.toString(cause.getMessage(), "");
            String code = message.startsWith("GENERATION_SOURCE_CONFLICT:") ? "GENERATION_SOURCE_CONFLICT"
                    : message.startsWith("GENERATION_INPUT_CHANGED:") ? "GENERATION_INPUT_CHANGED"
                    : message.startsWith("GENERATOR_DEPENDENCIES_UNAVAILABLE:") ? "GENERATOR_DEPENDENCIES_UNAVAILABLE"
                    : message.startsWith("GENERATOR_LOCAL_IPC_UNAVAILABLE:") ? "GENERATOR_LOCAL_IPC_UNAVAILABLE"
                    : message.startsWith("GENERATOR_DECOMPILATION_FAILED:") ? "GENERATOR_DECOMPILATION_FAILED"
                    : "GENERATOR_SOURCE_PREPARATION_FAILED";
            String explanation = switch (code) {
                case "GENERATION_SOURCE_CONFLICT" -> GenerationPreparationDiagnostics.sourceConflict(message);
                case "GENERATION_INPUT_CHANGED" -> "Workspace inputs changed while dependencies were prepared. Run generation again for the current inputs.";
                case "GENERATOR_DEPENDENCIES_UNAVAILABLE" -> "Generator dependencies could not be prepared. Inspect the setup log and retry after resolving dependencies.";
                case "GENERATOR_LOCAL_IPC_UNAVAILABLE" -> "Loom could not access its local IPC file in this execution context. Retry from the desktop product or a normal local terminal; changing dependency mirrors does not repair local IPC.";
                case "GENERATOR_DECOMPILATION_FAILED" -> "Minecraft source decompilation failed during generator preparation. Inspect the decompiler and system logs for the cause, including memory pressure or process termination; this result alone does not establish a download failure.";
                default -> "Generator source preparation failed. Inspect the task log before retrying.";
            };
            JsonObject details = new JsonObject();
            if (cause instanceof SourceConflict conflict) {
                JsonArray conflicts = new JsonArray(); conflicts.add(conflict.projection());
                details.add("conflicts", conflicts); details.addProperty("conflictCount", 1);
                details.addProperty("conflictsTruncated", false);
            }
            throw new dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException(code, explanation, cause, details);
        }
    }

    private void prepareImpl(WorkspaceState state, Path target, Operation operation, Consumer<String> output) throws Exception {
        Path source = opened.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
        target = target.toAbsolutePath().normalize();
        // Older cold workspaces predate pending metadata but still need their managed sources prepared.
        // Code elements and locked sources remain outside generation ownership.
        if (pending(opened) == null && operation != Operation.GENERATE_WORKSPACE
                && (!needsDependencies(opened) || managedElements(opened).isEmpty())) {
            restoreBuildInclude(opened, target);
            return;
        }
        String generatorId = state.generator().get("id").getAsString();
        if (dev.copperbench.tracks.VersionTrackCatalog.official().findGenerator(generatorId).isEmpty()
                && !dev.copperbench.generator.datapack.DataPackWorkspaceTaskGateway.GENERATOR_IDS.contains(generatorId)) return;
        if (target.equals(source)) {
            prepare(opened, state, operation, output, true);
        } else {
            Path document = target.resolve(opened.getFileManager().getWorkspaceFile().getName());
            if (!Files.isRegularFile(document)) return;
            try (Workspace copied = Workspace.readFromFS(document.toFile(), null)) {
                prepare(copied, state, operation, output, false);
            }
        }
    }

    private void prepare(Workspace workspace, WorkspaceState state, Operation operation, Consumer<String> output,
            boolean currentWorkspace) throws Exception {
        JsonObject pending = pending(workspace);
        Set<String> names = new LinkedHashSet<>();
        if (operation == Operation.GENERATE_WORKSPACE || pending != null || needsDependencies(workspace))
            workspace.getModElements().forEach(element -> names.add(element.getName()));
        List<ModElement> elements = managedElements(workspace).stream().filter(element -> names.contains(element.getName())).toList();
        Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
        var plan = MCreatorGenerationPlan.inspect(workspace, elements);
        plan.requireSafe();
        var planned = plan.paths();
        JsonObject expected = plan.expected();
        // Reject existing source conflicts before even restoring the generated Gradle include.
        restoreBuildInclude(workspace, root);
        if (needsDependencies(workspace)) {
            output.accept("GENERATOR_DEPENDENCIES_PREPARING: preparing the workspace's declared Gradle dependencies");
            net.mcreator.gradle.GradleUtils.updateMCreatorBuildFile(workspace);
            String sync = workspace.getGeneratorConfiguration().getGradleTaskFor("sync_task");
            Path javaHome = Path.of(net.mcreator.gradle.GradleUtils.getJavaHome(workspace.getWorkspaceSettings().getCurrentGenerator()));
            var localIpcFailure = new java.util.concurrent.atomic.AtomicBoolean();
            var decompilationFailure = new java.util.concurrent.atomic.AtomicBoolean();
            var result = processRunners.apply(javaHome).run(root,
                    List.of(sync == null ? "help" : sync, "--console=plain"), Duration.ofMinutes(15), line -> {
                        if (line.contains("java.nio.file.FileSystemException:") && line.matches(".*[\\\\/]loom[^\\\\/\\s]*ipc(?::|\\s).*"))
                            localIpcFailure.set(true);
                        if (line.matches("\\s*> Task :(?:[A-Za-z0-9_-]+:)*neoFormDecompile FAILED\\s*")
                                || line.matches(".*Execution failed for task ':(?:[A-Za-z0-9_-]+:)*neoFormDecompile'\\..*"))
                            decompilationFailure.set(true);
                        output.accept(line);
                    });
            if (result.exitCode() != 0) throw new IOException((localIpcFailure.get()
                    ? "GENERATOR_LOCAL_IPC_UNAVAILABLE" : decompilationFailure.get()
                    ? "GENERATOR_DECOMPILATION_FAILED" : "GENERATOR_DEPENDENCIES_UNAVAILABLE") + ": setup exited " + result.exitCode());
            try { workspace.getGenerator().reloadGradleCaches(); }
            catch (RuntimeException failure) { throw new IOException("GENERATOR_DEPENDENCIES_UNAVAILABLE: import index could not be loaded", failure); }
            if (needsDependencies(workspace)) throw new IOException("GENERATOR_DEPENDENCIES_UNAVAILABLE: Minecraft imports are not indexed");
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Generator preparation cancelled");
        Runnable generate = () -> {
            try {
                verify(root, expected); verifyPending(workspace);
                regenerate(workspace, elements, planned);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        };
        if (currentWorkspace) {
            var result = store.coordinate(state.id(), state.revision(), ignored -> { generate.run(); return true; });
            if (result.status() != RevisionedWorkspaceStore.TransactionResult.Status.COORDINATED)
                throw new IOException("GENERATION_INPUT_CHANGED: workspace revision changed during dependency preparation");
        } else generate.run();
        output.accept("GENERATOR_SOURCES_READY: regenerated " + elements.size() + " managed elements");
    }

    static List<ModElement> managedElements(Workspace workspace) {
        return workspace.getModElements().stream()
                .filter(element -> !element.isCodeLocked() && !element.getTypeString().equals("code")).toList();
    }

    private static void restoreBuildInclude(Workspace workspace, Path root) throws IOException {
        Path include = root.resolve("mcreator.gradle");
        relative(root, root.resolve("build.gradle")); relative(root, include);
        if (Files.isRegularFile(root.resolve("build.gradle")) && !Files.exists(include)) {
            Files.writeString(include, net.mcreator.gradle.GradleUtils.mcreatorBuildFileContent(workspace),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void regenerate(Workspace workspace, List<ModElement> elements, Set<Path> paths) throws IOException {
        var backup = new LinkedHashMap<Path, byte[]>();
        Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
        paths = new LinkedHashSet<>(paths); paths.add(workspace.getFileManager().getWorkspaceFile().toPath());
        for (Path path : paths) { relative(root, path); backup.put(path, Files.isRegularFile(path) ? Files.readAllBytes(path) : null); }
        try {
            if (!workspace.getGenerator().generateBase()) throw new IOException("Base generation failed");
            for (ModElement element : elements) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Generation interrupted");
                if (!workspace.getGenerator().generateElement(element.getGeneratableElement())) throw new IOException("Element generation failed: " + element.getName());
            }
            if (!workspace.getGenerator().generateBase()) throw new IOException("Base registration generation failed");
            WorkspaceGeneratorSetup.completeSetup(workspace.getGenerator());
            workspace.putMetadata(PENDING, null);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        } catch (IOException | RuntimeException failure) {
            try {
                for (var entry : backup.entrySet()) {
                    if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                    else { Files.createDirectories(entry.getKey().getParent()); Files.write(entry.getKey(), entry.getValue()); }
                }
                workspace.reloadFromFileSystem();
            } catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    static String relative(Path root, Path path) throws SourceConflict {
        path = path.toAbsolutePath().normalize(); root = root.toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new SourceConflict(null, "PATH_OUTSIDE_WORKSPACE", "unknown", "source path leaves workspace", null);
        String relative = root.relativize(path).toString().replace('\\', '/');
        try { WorkspaceExecutionSnapshot.rejectLinks(path); }
        catch (IOException failure) { throw new SourceConflict(relative, "UNSAFE_PATH", "unknown", "source path contains a link or cannot be resolved safely", failure); }
        return relative;
    }

    static String hash(Path path) throws IOException {
        WorkspaceExecutionSnapshot.rejectLinks(path);
        if (!Files.exists(path)) return "absent";
        if (!Files.isRegularFile(path)) throw new SourceConflict(null, "NOT_REGULAR_FILE", "unknown", "expected a regular file", null);
        if (!path.getFileName().toString().endsWith(".java")) return WorkspaceExecutionSnapshot.sha256(path);
        String content = Files.readString(path);
        StringBuilder protectedCode = new StringBuilder();
        String active = null;
        Set<String> names = new HashSet<>();
        String start = "// Start of user code block ", end = "// End of user code block ";
        for (String line : content.split("(?<=\\n)", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith(start)) {
                String name = trimmed.substring(start.length()).strip();
                if (active != null || name.isEmpty() || !names.add(name)) throw new SourceConflict(null, "USER_CODE_BOUNDARY_INVALID", "generated", "ambiguous user-code region", null);
                active = name; protectedCode.append(line);
            } else if (trimmed.startsWith(end)) {
                if (active == null || !trimmed.equals(end + active)) throw new SourceConflict(null, "USER_CODE_BOUNDARY_INVALID", "generated", "mismatched user-code region", null);
                active = null; protectedCode.append(line);
            } else if (active == null) protectedCode.append(line);
        }
        if (active != null) throw new SourceConflict(null, "USER_CODE_BOUNDARY_INVALID", "generated", "unclosed user-code region", null);
        try {
            return "generated-v1:" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(protectedCode.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static void verify(Path root, JsonObject expected) throws IOException {
        for (var entry : expected.entrySet()) {
            Path path = root.resolve(entry.getKey()); relative(root, path);
            String expectedHash = entry.getValue().getAsString();
            String actual;
            try { actual = expectedHash.matches("[0-9a-f]{64}") && Files.isRegularFile(path)
                    ? WorkspaceExecutionSnapshot.sha256(path) : hash(path); }
            catch (SourceConflict failure) {
                throw new SourceConflict(relative(root, path), failure.reasonCode, failure.observedOwnership, failure.detail, failure);
            }
            if (!expectedHash.equals(actual))
                throw new SourceConflict(relative(root, path), "SOURCE_CHANGED", "unknown", "source changed before generation", null);
        }
    }
}

package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.core.workspace.WorkspaceFiles;
import net.mcreator.element.types.Function;
import net.mcreator.generator.GeneratorFile;
import net.mcreator.generator.io.JSONWriter;
import net.mcreator.generator.template.InlineTemplatesHandler;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.java.CodeCleanup;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Backend for the function-only version-copy rule. Rendering must not invoke generation filesystem tasks. */
final class MCreatorVersionCopy {
    static Map<String, byte[]> baseline(Workspace workspace, JsonObject document,
            Map<String, JsonObject> definitions) throws Exception {
        JsonObject durableSettings = document.getAsJsonObject("workspaceSettings");
        var known = WorkspaceFileManager.gson.toJsonTree(workspace.getWorkspaceSettings()).getAsJsonObject();
        // Unknown JSON remains opaque, but a stale active model must not certify changed known settings.
        var decoded = WorkspaceFileManager.gson.fromJson(durableSettings, net.mcreator.workspace.settings.WorkspaceSettings.class);
        if (!known.equals(WorkspaceFileManager.gson.toJsonTree(decoded)))
            throw new IOException("Saved workspace settings changed. Reopen the source before planning.");
        Path root = workspace.getWorkspaceFolder().toPath();
        Map<String, byte[]> files = new TreeMap<>();
        var generator = workspace.getGenerator();
        Set<String> baseOwned = associated(document);
        var renderer = generator.getTemplateGeneratorFromName("templates");
        for (var template : generator.getModBaseGeneratorTemplatesList()) {
            String code = renderer.generateBaseFromTemplate((String) template.getTemplateDefinition().get("template"),
                    template.getDataModel(), (String) template.getTemplateDefinition().get("variables"));
            add(files, workspace, template.toGeneratorFile(code));
        }
        files.keySet().removeIf(path -> !baseOwned.contains(path));
        for (var entry : definitions.entrySet()) {
            var element = workspace.getModElementByName(entry.getKey());
            if (element == null || !element.getTypeString().equals("function") || element.isCodeLocked())
                throw new IOException("Source element identity or ownership changed: " + entry.getKey());
            Function function = new Function(element);
            var data = entry.getValue().getAsJsonObject("definition");
            function.name = data.get("name").getAsString(); function.namespace = "mod";
            function.code = data.get("code").getAsString();
            var saved = document.getAsJsonArray("mod_elements").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(e -> entry.getKey().equals(e.get("name").getAsString())).findFirst().orElseThrow();
            Set<String> owned = associated(saved);
            Map<String, byte[]> rendered = new TreeMap<>();
            for (var file : generator.generateElement(function, true, false)) add(rendered, workspace, file);
            rendered.keySet().removeIf(path -> !owned.contains(path)); files.putAll(rendered);
        }
        String prefix = generator.getGeneratorName() + "/workspacebase/";
        for (String resource : PluginLoader.INSTANCE.getResourcesInPackage(generator.getGeneratorName() + ".workspacebase")) {
            if (!resource.startsWith(prefix)) throw new IOException("Unexpected workspace-base resource: " + resource);
            String relative = resource.substring(prefix.length());
            WorkspaceFiles.requireWithin(root, root.resolve(relative));
            try (var input = PluginLoader.INSTANCE.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing workspace-base resource: " + resource);
                byte[] bytes = input.readAllBytes();
                if (resource.endsWith(".gradle") || resource.endsWith(".properties") || resource.endsWith(".txt")) {
                    var writer = new StringWriter();
                    InlineTemplatesHandler.getTemplate(new String(bytes, StandardCharsets.UTF_8)).process(
                            generator.getBaseDataModelProvider().provide(), writer, InlineTemplatesHandler.getConfiguration().getObjectWrapper());
                    bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
                } else if (relative.equals("gradlew")) {
                    bytes = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8);
                }
                files.put(relative, bytes);
            }
        }
        files.put("mcreator.gradle", GradleUtils.mcreatorBuildFileContent(workspace).getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private static Set<String> associated(JsonObject document) {
        if (!document.has("metadata") || !document.getAsJsonObject("metadata").has("files")) return Set.of();
        Set<String> paths = new HashSet<>();
        document.getAsJsonObject("metadata").getAsJsonArray("files").forEach(p -> paths.add(p.getAsString().replace('\\', '/')));
        return paths;
    }

    private static void add(Map<String, byte[]> files, Workspace workspace, GeneratorFile file) throws IOException {
        Path root = WorkspaceFiles.requireDirectory(workspace.getWorkspaceFolder().toPath());
        Path output = WorkspaceFiles.requireWithin(root, file.getFile().toPath());
        String content = switch (file.writer()) {
            case JAVA -> new CodeCleanup().reformatTheCodeAndOrganiseImports(workspace, file.contents());
            case JSON -> JSONWriter.formatJSON(file.contents());
            case FILE -> file.contents();
            default -> throw new IOException("Unproven template writer: " + file.writer());
        };
        files.put(root.relativize(output).toString().replace('\\', '/'), content.getBytes(StandardCharsets.UTF_8));
    }

    static void generate(Path workspaceFile) throws Exception {
        try (var target = Workspace.readFromFS(workspaceFile.toFile(), null)) {
            new WorkspaceCreationService().prepareVersionCopy(target);
            for (var element : target.getModElements()) {
                if (!element.getTypeString().equals("function") || element.isCodeLocked())
                    throw new IOException("Target contains an unproven element.");
                if (!target.getGenerator().generateElement(element.getGeneratableElement()))
                    throw new IOException("Target function generation failed: " + element.getName());
            }
            target.getFileManager().saveWorkspaceDirectlyAndWait();
        }
    }
}

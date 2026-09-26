package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Stage17ResourceResolutionTest {
    @TempDir Path root;

    @Test void versionedCatalogResolvesVanillaButRejectsTyposAndRefreshesWithoutRevision() throws Exception {
        Files.writeString(root.resolve("test.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
        Path jar = root.resolve(".gradle/caches/fabric-loom/1.21.1/minecraft-client.jar");
        Files.createDirectories(jar.getParent());
        writeCatalog(jar, false);
        Path model = root.resolve("src/main/resources/assets/example/models/block/test.json");
        Files.createDirectories(model.getParent());
        Files.writeString(model, "{\"parent\":\"minecraft:block/cube_all\"}");
        var service = new AssetWorkspaceService(root);
        var graph = service.referenceGraph();
        assertTrue(graph.diagnostics().isEmpty());
        assertEquals("vanilla_resolved", graph.references().getFirst().resolution());
        Files.writeString(model, "{\"parent\":\"minecraft:block/cube_all_typo\"}");
        assertEquals("MISSING_ASSET_REFERENCE", service.referenceGraph().diagnostics().getFirst().code());
        writeCatalog(jar, true);
        assertTrue(service.referenceGraph().diagnostics().isEmpty(), "A changed catalog must not reuse an old missing result");
        Files.writeString(model, "{\"parent\":\"thirdparty:block/unknown\"}");
        assertEquals("EXTERNAL_ASSET_REFERENCE_UNVERIFIED", service.referenceGraph().diagnostics().getFirst().code());
    }

    @Test void noVersionContextIsUnverifiedAndWorkspaceOverrideWins() throws Exception {
        Path model = root.resolve("assets/example/models/block/test.json");
        Files.createDirectories(model.getParent()); Files.writeString(model, "{\"parent\":\"minecraft:block/cube_all\"}");
        var service = new AssetWorkspaceService(root);
        assertEquals("EXTERNAL_ASSET_REFERENCE_UNVERIFIED", service.referenceGraph().diagnostics().getFirst().code());
        Path override = root.resolve("assets/minecraft/models/block/cube_all.json");
        Files.createDirectories(override.getParent()); Files.writeString(override, "{}");
        assertTrue(service.referenceGraph().diagnostics().isEmpty());
        assertEquals("workspace_resolved", service.referenceGraph().references().getFirst().resolution());
    }

    @Test void creatorCacheDirectoryIsNotMistakenForASecondWorkspaceDocument() throws Exception {
        catalog("fabric-1.21.1", Map.of("block/cube_all", "{}"));
        Files.createDirectories(root.resolve(".mcreator"));
        write("assets/example/models/block/test.json", "{\"parent\":\"minecraft:block/cube_all\"}");
        assertEquals("vanilla_resolved", new AssetWorkspaceService(root).referenceGraph().references().getFirst().resolution());
    }

    @Test void vanillaParentsInheritGeometryAndChildTextureBindingsAcrossNamespaces() throws Exception {
        Path jar = catalog("fabric-1.21.1", Map.of(
                "block/cube_all", "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"#all\",\"all\":\"minecraft:block/absent\"}}",
                "block/cube", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"texture\":\"#all\"}}}]}"));
        Path model = write("src/main/resources/assets/example/models/block/test.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"example:block/lamp\"}}");
        write("src/main/resources/assets/example/textures/block/lamp.png", "fixture");
        var service = new AssetWorkspaceService(root);
        assertTrue(service.referenceGraph().diagnostics().isEmpty(), service.referenceGraph().diagnostics().toString());
        Files.writeString(model, "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"example:block/missing\"}}");
        assertTrue(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MISSING_ASSET_REFERENCE")
                && d.targetPath().endsWith("textures/block/missing.png")));
        // Replacing a catalog's contents, even with unchanged workspace revision, invalidates inheritance.
        catalog("fabric-1.21.1", Map.of("block/cube_all", "{\"parent\":\"example:block/test\"}"));
        assertTrue(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MODEL_PARENT_CYCLE")));
        assertTrue(Files.isRegularFile(jar));
    }

    @Test void missingTextureVariablesAndAliasCyclesAreDetectedButUnavailableParentsStayUnverified() throws Exception {
        catalog("neoforge-1.21.1", Map.of("block/cube_all", "{\"textures\":{\"particle\":\"#all\"}}"));
        Path model = write("assets/example/models/block/test.json", "{\"parent\":\"minecraft:block/cube_all\"}");
        var service = new AssetWorkspaceService(root);
        assertTrue(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MODEL_TEXTURE_VARIABLE_MISSING")));
        Files.writeString(model, "{\"textures\":{\"a\":\"#b\",\"b\":\"#a\"}}");
        assertTrue(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MODEL_TEXTURE_CYCLE")));
        Files.writeString(model, "{\"parent\":\"thirdparty:block/base\",\"textures\":{\"particle\":\"#all\"}}");
        var issues = service.referenceGraph().diagnostics();
        assertTrue(issues.stream().anyMatch(d -> d.code().equals("EXTERNAL_ASSET_REFERENCE_UNVERIFIED")));
        assertFalse(issues.stream().anyMatch(d -> d.severity() == AssetDiagnostic.Severity.ERROR), issues.toString());
    }

    @Test void allEightTracksUseTheirOwnVersionAndUnqualifiedMinecraftIdentifiers() throws Exception {
        for (String loader : List.of("fabric", "neoforge")) for (String version : List.of("1.20.1", "1.21.1", "26.1.2", "26.2")) {
            catalog(loader + "-" + version, Map.of("block/cube_all", "{}"));
            write("assets/example/models/block/test.json", "{\"parent\":\"block/cube_all\"}");
            var graph = new AssetWorkspaceService(root).referenceGraph();
            assertTrue(graph.diagnostics().isEmpty(), loader + "-" + version + graph.diagnostics());
            assertEquals(version, graph.references().getFirst().resourceVersion());
            assertEquals("vanilla_resolved", graph.references().getFirst().resolution());
        }
    }

    @Test void rendererBuiltinParentsAreRecognizedWithoutPretendingArbitraryMinecraftFilesExist() throws Exception {
        catalog("fabric-1.21.1", Map.of("item/generated", "{\"parent\":\"builtin/generated\"}"));
        write("assets/example/models/item/test.json", "{\"parent\":\"minecraft:item/generated\"}");
        var service = new AssetWorkspaceService(root);
        assertTrue(service.referenceGraph().diagnostics().isEmpty(), service.referenceGraph().diagnostics().toString());
        write("assets/example/models/item/test.json", "{\"parent\":\"minecraft:builtin/typo\"}");
        assertTrue(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MISSING_ASSET_REFERENCE")));
    }

    @Test void neoformClientCacheProvidesTheSameVersionedCatalogWithoutALoomCache() throws Exception {
        Path loom = catalog("neoforge-1.21.1", Map.of("block/cube_all", "{}"));
        Path neoform = root.resolve(".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar");
        Files.createDirectories(neoform.getParent()); Files.move(loom, neoform);
        write("assets/example/models/block/test.json", "{\"parent\":\"minecraft:block/cube_all\"}");
        var graph = new AssetWorkspaceService(root).referenceGraph();
        assertTrue(graph.diagnostics().isEmpty(), graph.diagnostics().toString());
        assertTrue(graph.references().getFirst().resourceSource().startsWith("minecraft_1.21.1_client.jar sha256="));
    }

    @Test void diagnosticsKeepEachRealSourcePointerAndInheritedFailuresPointToTheParentLink() throws Exception {
        catalog("fabric-1.21.1", Map.of("block/cube_all", "{\"textures\":{\"particle\":\"#missing\"}}"));
        write("assets/example/models/block/test.json", "{\"textures\":{\"one\":\"example:block/missing\",\"two\":\"example:block/missing\"}}");
        var service = new AssetWorkspaceService(root);
        var missing = service.referenceGraph().diagnostics().stream().filter(d -> d.code().equals("MISSING_ASSET_REFERENCE")).toList();
        assertEquals(2, missing.size());
        assertEquals(java.util.Set.of("/textures/one", "/textures/two"), missing.stream().map(AssetDiagnostic::sourcePointer).collect(java.util.stream.Collectors.toSet()));
        write("assets/example/models/block/test.json", "{\"parent\":\"minecraft:block/cube_all\"}");
        var inherited = service.referenceGraph().diagnostics().stream().filter(d -> d.code().equals("MODEL_TEXTURE_VARIABLE_MISSING")).findFirst().orElseThrow();
        assertEquals("/parent", inherited.sourcePointer());
        assertTrue(inherited.message().contains("/textures/particle"));
    }

    @Test void aTemplateAlsoUsedDirectlyByABlockstateMustHaveItsOwnTextureBindings() throws Exception {
        write("assets/example/models/block/base.json", "{\"textures\":{\"particle\":\"#surface\"}}");
        write("assets/example/models/block/child.json", "{\"parent\":\"example:block/base\",\"textures\":{\"surface\":\"example:block/lamp\"}}");
        write("assets/example/textures/block/lamp.png", "fixture");
        var service = new AssetWorkspaceService(root);
        assertFalse(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MODEL_TEXTURE_VARIABLE_MISSING")));
        write("assets/example/blockstates/direct.json", "{\"variants\":{\"\":{\"model\":\"example:block/base\"}}}");
        assertTrue(service.referenceGraph().diagnostics().stream().anyMatch(d -> d.sourcePath().endsWith("base.json")
                && d.code().equals("MODEL_TEXTURE_VARIABLE_MISSING")));
    }

    @Test void clientItemDefinitionsOnlyReferenceModelsNotCodecPropertiesOrCaseValues() throws Exception {
        catalog("fabric-26.2", Map.of("block/cube_all", "{}", "item/generated", "{}"));
        write("assets/example/items/test.json", """
                {"model":{"type":"minecraft:condition","property":"minecraft:has_component",
                  "component":"minecraft:custom_model_data",
                  "on_true":{"type":"minecraft:select","property":"minecraft:context_dimension",
                    "cases":[{"when":["minecraft:overworld","minecraft:the_nether"],
                      "model":{"type":"minecraft:model","model":"block/cube_all",
                        "tints":[{"type":"minecraft:custom_model_data","index":0}]}}],
                    "fallback":{"type":"minecraft:range_dispatch","property":"minecraft:count",
                      "entries":[{"threshold":1,"model":{"type":"minecraft:model","model":"minecraft:block/missing"}}],
                      "fallback":{"type":"minecraft:composite","models":[
                        {"type":"minecraft:model","model":"minecraft:item/generated"},
                        {"type":"minecraft:bundle/selected_item"}]}}},
                  "on_false":{"type":"minecraft:special","base":"minecraft:block/cube_all",
                    "model":{"type":"minecraft:shield"}}}}
                """);
        var graph = new AssetWorkspaceService(root).referenceGraph();
        assertEquals(4, graph.references().size(), graph.references().toString());
        assertTrue(graph.references().stream().allMatch(r -> r.expectedPrefix().equals("models/")));
        var errors = graph.diagnostics().stream().filter(d -> d.severity() == AssetDiagnostic.Severity.ERROR).toList();
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("MISSING_ASSET_REFERENCE", errors.getFirst().code());
        assertEquals("/model/on_true/fallback/entries/0/model/model", errors.getFirst().sourcePointer());
        assertTrue(graph.references().stream().anyMatch(r -> r.rawValue().equals("block/cube_all")
                && r.resolution().equals("vanilla_resolved") && r.resourceVersion().equals("26.2")));
    }

    @Test void blockstateModelIdsDefaultToMinecraftAndConditionsAreNotFileReferences() throws Exception {
        catalog("neoforge-1.21.1", Map.of("block/cube_all", "{}"));
        write("src/main/resources/assets/example/blockstates/test.json", """
                {"variants":{"facing=north":{"model":"block/cube_all"}},
                 "multipart":[{"when":{"example:property":"example:value"},
                   "apply":[{"model":"block/cube_all_typo"}]}]}
                """);
        var graph = new AssetWorkspaceService(root).referenceGraph();
        assertEquals(2, graph.references().size(), graph.references().toString());
        assertTrue(graph.references().stream().anyMatch(r -> r.rawValue().equals("block/cube_all")
                && r.resolution().equals("vanilla_resolved")));
        assertEquals(1, graph.diagnostics().size(), graph.diagnostics().toString());
        assertEquals("/multipart/0/apply/0/model", graph.diagnostics().getFirst().sourcePointer());
        assertTrue(graph.diagnostics().getFirst().targetPath().endsWith("minecraft/models/block/cube_all_typo.json"));
    }

    @Test void unknownClientItemModelCodecStaysUnverifiedAndMalformedNodesAreLocated() throws Exception {
        write("assets/example/items/custom.json", "{\"model\":{\"type\":\"example:custom_renderer\",\"payload\":\"example:opaque\"}}");
        write("assets/example/items/broken.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":42}}");
        var graph = new AssetWorkspaceService(root).referenceGraph();
        assertTrue(graph.references().isEmpty(), graph.references().toString());
        assertTrue(graph.diagnostics().stream().anyMatch(d -> d.sourcePath().endsWith("custom.json")
                && d.severity() == AssetDiagnostic.Severity.WARNING && d.sourcePointer().equals("/model/type")));
        assertTrue(graph.diagnostics().stream().anyMatch(d -> d.sourcePath().endsWith("broken.json")
                && d.code().equals("INVALID_ASSET_DOCUMENT") && d.sourcePointer().equals("/model/model")));
    }

    private Path write(String relative, String content) throws Exception {
        Path file = root.resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, content); return file;
    }

    private Path catalog(String generator, Map<String, String> models) throws Exception {
        write("test.mcreator", "{\"workspaceSettings\":{\"currentGenerator\":\"" + generator + "\"}}");
        String version = generator.substring(generator.indexOf('-') + 1);
        Path jar = root.resolve(".gradle/caches/fabric-loom/" + version + "/minecraft-client.jar");
        Files.createDirectories(jar.getParent());
        var entries = new java.util.LinkedHashMap<String, String>();
        entries.put("block/cube_all", "{}"); entries.put("item/generated", "{}"); entries.putAll(models);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("version.json")); zip.write(("{\"id\":\"" + version + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry("assets/minecraft/models/" + entry.getKey() + ".json"));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return jar;
    }

    private void writeCatalog(Path jar, boolean includeTypo) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("version.json")); zip.write("{\"id\":\"1.21.1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            for (String name : includeTypo ? new String[]{"block/cube_all", "item/generated", "block/cube_all_typo"}
                    : new String[]{"block/cube_all", "item/generated"}) {
                zip.putNextEntry(new ZipEntry("assets/minecraft/models/" + name + ".json"));
                zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
    }
}

package dev.copperbench.generator.datapack;

import dev.copperbench.generator.GradleWorkspaceBackend.ValidationIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DataPackContentValidatorTest {
    @TempDir Path root;

    @Test void aPackThatMinecraftLoadsHasNoIssues() throws Exception {
        pack("{\"pack\":{\"pack_format\":48,\"description\":\"ok\"}}");
        write("data/cbdp/function/mark.mcfunction", "# comment\nsay marked\nexecute as @a run function cbdp:helper\n"
                + "schedule function #cbdp:ticks 1t\n$function cbdp:$(name)\ntellraw @a \"function cbdp:not_a_call\"\n");
        write("data/cbdp/function/helper.mcfunction", "function minecraft:other_pack\n");
        write("data/cbdp/tags/function/ticks.json", "{\"values\":[\"cbdp:helper\"]}");
        write("data/minecraft/tags/function/load.json", "{\"values\":[\"cbdp:mark\"]}");
        write("data/cbdp/recipe/stick.json", "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],\"result\":{\"id\":\"minecraft:stick\"}}");
        write("data/cbdp/advancement/root.json", "{\"parent\":\"minecraft:story/root\",\"criteria\":{},"
                + "\"rewards\":{\"function\":\"cbdp:mark\",\"loot\":[\"cbdp:chests/bonus\"],\"recipes\":[\"cbdp:stick\"]}}");
        write("data/cbdp/advancement/recipes/stick.json", "{\"parent\":\"cbdp:root\",\"criteria\":{}}");
        write("data/cbdp/loot_table/chests/bonus.json", "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"cbdp:chests/inner\"},"
                + "{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:chests/simple_dungeon\"}]}]}");
        write("data/cbdp/loot_table/chests/inner.json", "{\"pools\":[]}");
        write("data/cbdp/worldgen/biome/grove.json", "{}");
        // Tags are read leniently, and references into other namespaces may come from the game, mods or other packs.
        write("data/cbdp/tags/worldgen/biome/groves.json", "// lenient\n{\"values\":[\"cbdp:grove\",\"minecraft:plains\"]}");
        write("data/cbdp/tags/item/tools.json", "{\"replace\":false,\"values\":[\"minecraft:stick\",\"#c:ingots\",\"cbdp:modded_item\","
                + "{\"id\":\"#cbdp:optional\",\"required\":false}]}");
        write("data/c/tags/item/ingots.json", "{\"values\":[\"#c:ingots/copper\"]}");
        write("data/cbdp/structure/hut.nbt", "binary");
        write("data/cbdp/README.txt", "Not a resource.");
        List<String> log = new ArrayList<>();
        assertEquals(List.of(), validate("datapack-1.21.1", "cbdp", log));
        assertTrue(log.getLast().matches("Data pack content check: 15 files, \\d+ references into namespace cbdp"), log.toString());
    }

    @Test void reportsWhatMinecraftWouldSkipOrFailToLoad() throws Exception {
        pack("{\"pack\":{\"description\":\"no format\"}}");
        write("data/cbdp/recipe/broken.json", "{\"type\": \"minecraft:crafting_shaped\",\n  \"pattern\" [\"#\"]}");
        write("data/cbdp/recipe/commented.json", "// strict\n{\"type\":\"minecraft:crafting_shaped\"}");
        write("data/cbdp/recipe/untyped.json", "{\"ingredients\":[]}");
        write("data/cbdp/recipe/list.json", "[]");
        write("data/cbdp/recipe/Upper.json", "{\"type\":\"minecraft:crafting_shaped\"}");
        write("data/cbdp/loose.json", "{}");
        write("data/cbdp/predicates/old_a.json", "{\"condition\":\"minecraft:random_chance\"}");
        write("data/cbdp/predicates/old_b.json", "{\"condition\":\"minecraft:random_chance\"}");
        write("data/cbdp/tags/blocks/old.json", "{\"values\":[]}");
        write("data/cbdp/tags/item/no_values.json", "{\"replace\":\"yes\"}");
        write("data/cbdp/tags/item/bad_entries.json", "{\"values\":[1,\"Bad:Id\",{\"id\":\"cbdp:x\",\"required\":\"no\"}]}");
        write("data/cbdp/tags/function/calls.json", "{\"values\":[\"cbdp:gone\",\"#cbdp:also_gone\"]}");
        write("data/cbdp/tags/worldgen/biome/groves.json", "{\"values\":[\"cbdp:missing_biome\"]}");
        write("data/cbdp/function/main.mcfunction", "say hi\nfunction cbdp:missing\nexecute if function #cbdp:absent run say no\n");
        write("data/cbdp/advancement/child.json", "{\"parent\":\"cbdp:no_parent\",\"criteria\":{},\"rewards\":{\"loot\":[\"cbdp:no_table\"]}}");
        write("data/cbdp/loot_table/table.json", "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"cbdp:no_table\"}]}]}");

        List<ValidationIssue> issues = validate("datapack-1.21.1", "cbdp", new ArrayList<>());
        String all = issues.stream().map(ValidationIssue::message).collect(Collectors.joining("\n"));
        assertIssue(issues, "DATA_PACK_CONTENT_INVALID", "/src/main/pack.mcmeta", "pack_format");
        assertIssue(issues, "DATA_PACK_JSON_INVALID", "/src/main/data/cbdp/recipe/broken.json", "line 2");
        assertIssue(issues, "DATA_PACK_JSON_INVALID", "/src/main/data/cbdp/recipe/commented.json", "cannot parse");
        assertIssue(issues, "DATA_PACK_JSON_INVALID", "/src/main/data/cbdp/recipe/list.json", "JSON object");
        assertIssue(issues, "DATA_PACK_CONTENT_INVALID", "/src/main/data/cbdp/recipe/untyped.json", "\"type\"");
        assertIssue(issues, "DATA_PACK_RESOURCE_PATH_INVALID", "/src/main/data/cbdp/recipe/Upper.json", "lowercase");
        assertIssue(issues, "DATA_PACK_RESOURCE_PATH_INVALID", "/src/main/data/cbdp/loose.json", "never loaded");
        assertIssue(issues, "DATA_PACK_FOLDER_RENAMED", "/src/main/data/cbdp/predicates", "reads predicate/, not predicates/");
        assertEquals(1, issues.stream().filter(issue -> "/src/main/data/cbdp/predicates".equals(issue.path())).count(), all);
        assertIssue(issues, "DATA_PACK_FOLDER_RENAMED", "/src/main/data/cbdp/tags/blocks", "tags/block/");
        assertIssue(issues, "DATA_PACK_TAG_INVALID", "/src/main/data/cbdp/tags/item/no_values.json", "\"values\" array");
        assertIssue(issues, "DATA_PACK_TAG_INVALID", "/src/main/data/cbdp/tags/item/bad_entries.json", "values[0]");
        assertIssue(issues, "DATA_PACK_TAG_INVALID", "/src/main/data/cbdp/tags/item/bad_entries.json", "\"Bad:Id\"");
        assertIssue(issues, "DATA_PACK_TAG_INVALID", "/src/main/data/cbdp/tags/item/bad_entries.json", "values[2] \"required\"");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/tags/function/calls.json",
                "values[0] refers to function cbdp:gone, which this pack does not define (expected src/main/data/cbdp/function/gone.mcfunction)");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/tags/function/calls.json", "tag #cbdp:also_gone");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/tags/worldgen/biome/groves.json", "data/cbdp/worldgen/biome/missing_biome.json");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/function/main.mcfunction", "line 2 refers to function cbdp:missing");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/function/main.mcfunction", "line 3 refers to function tag #cbdp:absent");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/advancement/child.json", "\"parent\" refers to advancement cbdp:no_parent");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/advancement/child.json", "rewards.loot[0] refers to loot table");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/loot_table/table.json", "pools[0].entries[0] refers to loot table cbdp:no_table");
        // Files in a folder the version does not read are only reported once, through the folder.
        assertTrue(issues.stream().noneMatch(issue -> issue.path() != null && issue.path().startsWith("/src/main/data/cbdp/predicates/")), all);
    }

    @Test void multiVersionPacksMayKeepTheOtherVersionsFolders() throws Exception {
        pack("{\"pack\":{\"pack_format\":48,\"supported_formats\":[41,48],\"description\":\"both\"}}");
        write("data/cbdp/recipe/a.json", "{\"type\":\"minecraft:crafting_shaped\"}");
        write("data/cbdp/recipes/a.json", "{\"type\":\"minecraft:crafting_shaped\"}");
        write("data/cbdp/tags/item/a.json", "{\"values\":[]}");
        write("data/cbdp/tags/items/a.json", "{\"values\":[]}");
        assertEquals(List.of(), validate("datapack-1.21.1", "cbdp", new ArrayList<>()));
    }

    @Test void dataPacksFor1201UseThePluralFolders() throws Exception {
        pack("{\"pack\":{\"pack_format\":15,\"description\":\"old\"}}");
        write("data/cbdp/functions/main.mcfunction", "function cbdp:helper\nfunction cbdp:gone\n");
        write("data/cbdp/functions/helper.mcfunction", "say ok\n");
        write("data/cbdp/tags/functions/tick.json", "{\"values\":[\"cbdp:main\"]}");
        write("data/cbdp/loot_tables/a.json", "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:loot_table\",\"name\":\"cbdp:b\"}]}]}");
        write("data/cbdp/predicate/new.json", "{\"condition\":\"minecraft:random_chance\"}");
        List<ValidationIssue> issues = validate("datapack-1.20.1", "cbdp", new ArrayList<>());
        assertIssue(issues, "DATA_PACK_FOLDER_RENAMED", "/src/main/data/cbdp/predicate", "Minecraft 1.20.x reads predicates/, not predicate/");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/functions/main.mcfunction", "src/main/data/cbdp/functions/gone.mcfunction");
        assertIssue(issues, "DATA_PACK_REFERENCE_MISSING", "/src/main/data/cbdp/loot_tables/a.json", "src/main/data/cbdp/loot_tables/b.json");
        assertEquals(3, issues.size(), issues.toString());
    }

    @Test void the261FormatAndAnUnknownNamespaceSkipOnlyReferenceChecks() throws Exception {
        pack("{\"pack\":{\"min_format\":101.1,\"max_format\":101.1,\"description\":\"new\"}}");
        write("data/cbdp/function/main.mcfunction", "function cbdp:gone\n");
        write("data/cbdp/recipe/broken.json", "{");
        List<String> log = new ArrayList<>();
        List<ValidationIssue> issues = validate("datapack-26.1.x", null, log);
        assertEquals(List.of("DATA_PACK_JSON_INVALID"), issues.stream().map(ValidationIssue::code).toList());
        assertTrue(log.getLast().contains("references were not checked"), log.toString());
    }

    @Test void longListsEndWithACount() throws Exception {
        pack("{\"pack\":{\"pack_format\":48,\"description\":\"many\"}}");
        for (int index = 0; index < DataPackContentValidator.MAX_ISSUES + 5; index++)
            write("data/cbdp/recipe/r" + index + ".json", "{");
        List<ValidationIssue> issues = validate("datapack-1.21.1", "cbdp", new ArrayList<>());
        assertEquals(DataPackContentValidator.MAX_ISSUES + 1, issues.size());
        assertEquals("DATA_PACK_CONTENT_ISSUES_OMITTED", issues.getLast().code());
        assertTrue(issues.getLast().message().startsWith("5 more"), issues.getLast().message());
    }

    @Test void aWorkspaceWithoutDataHasNothingToCheck() throws Exception {
        List<String> log = new ArrayList<>();
        assertEquals(List.of(), validate("datapack-1.21.1", "cbdp", log));
        assertEquals(List.of("Data pack content check: no src/main/data folder to check"), log);
    }

    private List<ValidationIssue> validate(String generator, String namespace, List<String> log) throws Exception {
        return DataPackContentValidator.validate(root.resolve("src/main"), generator, namespace, log::add);
    }

    private static void assertIssue(List<ValidationIssue> issues, String code, String path, String text) {
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals(code) && path.equals(issue.path()) && issue.message().contains(text)),
                () -> "Expected " + code + " at " + path + " containing '" + text + "' in:\n"
                        + issues.stream().map(issue -> issue.code() + " " + issue.path() + " " + issue.message()).collect(Collectors.joining("\n")));
    }

    private void pack(String content) throws Exception { write("pack.mcmeta", content); }

    private void write(String relative, String content) throws Exception {
        Path file = root.resolve("src/main").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

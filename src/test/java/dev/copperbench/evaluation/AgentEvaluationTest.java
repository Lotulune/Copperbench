package dev.copperbench.evaluation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AgentEvaluationTest {
    private static final Path FIXTURE = Path.of("examples/agent-eval/emberkit");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
    @TempDir Path temp;

    private static JsonObject catalogue() throws IOException {
        return JsonParser.parseString(Files.readString(FIXTURE.resolve("assertions.json"))).getAsJsonObject();
    }

    @Test void everyGameTestAssertionIsAMethodOfItsTaskClassAndViceVersa() throws IOException {
        Pattern method = Pattern.compile("@GameTest\\([^)]*\\)\\s+public void (\\w+)\\(GameTestHelper");
        for (String task : List.of("t1", "t2")) {
            String className = AgentEvaluation.task(catalogue(), task).get("entrypoint").getAsString();
            Path source = FIXTURE.resolve(task).resolve(className.substring(className.lastIndexOf('.') + 1) + ".java");
            Set<String> methods = method.matcher(Files.readString(source)).results().map(match -> match.group(1)).collect(Collectors.toSet());
            Set<String> catalogued = AgentEvaluation.assertions(catalogue(), task).stream()
                    .filter(assertion -> assertion.kind().equals("gametest")).map(AgentEvaluation.Assertion::id).collect(Collectors.toSet());
            assertEquals(catalogued, methods, task);
        }
    }

    @Test void staticChecksPassForCompleteResourcesAndNameWhatIsMissing() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        json(entries, "assets/emberkit/lang/en_us.json", "{\"block.emberkit.ember_bricks\":\"Ember Bricks\",\"item.emberkit.ember_dust\":\"Ember Dust\"}");
        json(entries, "assets/emberkit/lang/zh_cn.json", "{\"block.emberkit.ember_bricks\":\"余烬砖\",\"item.emberkit.ember_dust\":\"\"}");
        json(entries, "assets/emberkit/blockstates/ember_bricks.json", "{\"variants\":{\"\":{\"model\":\"emberkit:block/ember_bricks\"}}}");
        json(entries, "assets/emberkit/models/block/ember_bricks.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"emberkit:block/ember_bricks\"}}");
        json(entries, "assets/emberkit/models/item/ember_bricks.json", "{\"parent\":\"emberkit:block/ember_bricks\"}");
        json(entries, "assets/emberkit/models/item/ember_dust.json", "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"emberkit:item/ember_dust\"}}");
        entries.put("assets/emberkit/textures/block/ember_bricks.png", PNG);
        entries.put("assets/emberkit/textures/item/ember_dust.png", "not a png".getBytes(StandardCharsets.UTF_8));
        Path jar = jar(entries);

        var outcomes = AgentEvaluation.staticChecks("t1", AgentEvaluation.assertions(catalogue(), "t1"), jar);
        Map<String, AgentEvaluation.Outcome> byId = outcomes.stream().collect(Collectors.toMap(AgentEvaluation.Outcome::id, outcome -> outcome));
        assertEquals(Set.of("langEnUs", "langZhCn", "blockModelAndTexture", "blockItemModel", "dustModelAndTexture"), byId.keySet());
        assertEquals(AgentEvaluation.PASSED, byId.get("langEnUs").status());
        assertEquals(AgentEvaluation.FAILED, byId.get("langZhCn").status(), "a blank translation is not a name");
        assertTrue(byId.get("langZhCn").detail().contains("item.emberkit.ember_dust"));
        assertEquals(AgentEvaluation.PASSED, byId.get("blockModelAndTexture").status());
        assertEquals(AgentEvaluation.PASSED, byId.get("blockItemModel").status(), "textures inherited from an emberkit parent count");
        assertEquals(AgentEvaluation.FAILED, byId.get("dustModelAndTexture").status());
        assertTrue(byId.get("dustModelAndTexture").detail().contains("textures/item/ember_dust.png"));

        var t2 = AgentEvaluation.staticChecks("t2", AgentEvaluation.assertions(catalogue(), "t2"), jar(Map.of()));
        assertEquals(3, t2.size());
        assertTrue(t2.stream().allMatch(outcome -> outcome.status().equals(AgentEvaluation.FAILED)), t2.toString());
    }

    @Test void reportCasesMapToCatalogueIdsAndAbsentCasesAreNotRunNeverPassed() throws IOException {
        JsonObject verification = JsonParser.parseString("""
                {"reasonCode":"GAMETEST_FAILED","cases":[
                  {"name":"emberkitt1tests.blockregistered","className":"","status":"passed"},
                  {"name":"emberkitt1tests.blockhardness","className":"","status":"failed","message":"Hardness is 1.5, expected 2.0"}
                ]}""").getAsJsonObject();
        var assertions = AgentEvaluation.assertions(catalogue(), "t1");
        var outcomes = AgentEvaluation.gameTests(assertions, verification);
        assertEquals(AgentEvaluation.gameTestCount(assertions), outcomes.size());
        assertEquals(AgentEvaluation.PASSED, outcomes.getFirst().status());
        assertEquals(AgentEvaluation.FAILED, outcomes.get(1).status());
        assertEquals("Hardness is 1.5, expected 2.0", outcomes.get(1).detail());
        assertTrue(outcomes.subList(2, outcomes.size()).stream().allMatch(outcome -> outcome.status().equals(AgentEvaluation.NOT_RUN)));

        var run = new AgentEvaluation.RunResult("cb-t1", "t1", "cb.jar", "00", "GAMETEST_FAILED", outcomes);
        String markdown = AgentEvaluation.markdown(catalogue(), List.of(run));
        assertTrue(markdown.contains("| `blockRegistered` | Block emberkit:ember_bricks is registered | pass |"), markdown);
        assertTrue(markdown.contains("| `blockHardness` | Hardness is 2.0 | **FAIL** |"), markdown);
        assertTrue(markdown.contains("**1/" + outcomes.size() + "**"), markdown);
        assertTrue(markdown.contains("`blockHardness`: Hardness is 1.5, expected 2.0"), markdown);
        assertEquals(1, AgentEvaluation.toJson(run).get("passed").getAsLong());
    }

    private static void json(Map<String, byte[]> entries, String name, String value) {
        entries.put(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private Path jar(Map<String, byte[]> entries) throws IOException {
        Path jar = Files.createTempFile(temp, "emberkit-", ".jar");
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return jar;
    }
}

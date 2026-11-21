package dev.copperbench.evaluation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scores one mod JAR against the Emberkit assertion catalogue: GameTest cases from the host report plus static
 * checks of the JAR's resources. Every catalogue assertion gets exactly one outcome; a case missing from the report
 * is {@code not_run}, never a pass.
 */
final class AgentEvaluation {
    static final String PASSED = "passed", FAILED = "failed", NOT_RUN = "not_run";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private AgentEvaluation() {}

    record Assertion(String id, String kind, String requirement) {}

    record Outcome(String id, String kind, String requirement, String status, String detail) {}

    record RunResult(String label, String task, String jar, String jarSha256, String reportReason, List<Outcome> outcomes) {
        long passed() { return outcomes.stream().filter(outcome -> outcome.status().equals(PASSED)).count(); }
    }

    static JsonObject task(JsonObject catalogue, String task) {
        JsonObject tasks = catalogue.getAsJsonObject("tasks");
        if (!tasks.has(task)) throw new IllegalArgumentException("Unknown evaluation task: " + task);
        return tasks.getAsJsonObject(task);
    }

    static List<Assertion> assertions(JsonObject catalogue, String task) {
        List<Assertion> result = new ArrayList<>();
        for (JsonElement raw : task(catalogue, task).getAsJsonArray("assertions")) {
            JsonObject value = raw.getAsJsonObject();
            result.add(new Assertion(value.get("id").getAsString(), value.get("kind").getAsString(), value.get("requirement").getAsString()));
        }
        return List.copyOf(result);
    }

    static long gameTestCount(List<Assertion> assertions) {
        return assertions.stream().filter(assertion -> assertion.kind().equals("gametest")).count();
    }

    /** Fabric names a case "<simple class name>.<method>" in lower case; the catalogue id is the method name. */
    static List<Outcome> gameTests(List<Assertion> assertions, JsonObject verification) {
        JsonArray cases = verification.has("cases") ? verification.getAsJsonArray("cases") : new JsonArray();
        List<Outcome> result = new ArrayList<>();
        for (Assertion assertion : assertions) {
            if (!assertion.kind().equals("gametest")) continue;
            String id = assertion.id().toLowerCase(Locale.ROOT);
            JsonObject found = null;
            for (JsonElement raw : cases) {
                String name = raw.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT);
                if (name.equals(id) || name.endsWith("." + id)) { found = raw.getAsJsonObject(); break; }
            }
            if (found == null) {
                result.add(new Outcome(assertion.id(), assertion.kind(), assertion.requirement(), NOT_RUN,
                        "No case in the GameTest report (" + verification.get("reasonCode").getAsString() + ")"));
                continue;
            }
            String status = found.get("status").getAsString();
            result.add(new Outcome(assertion.id(), assertion.kind(), assertion.requirement(),
                    status.equals("passed") ? PASSED : status.equals("failed") ? FAILED : NOT_RUN,
                    found.has("message") ? found.get("message").getAsString() : ""));
        }
        return result;
    }

    static List<Outcome> staticChecks(String task, List<Assertion> assertions, Path jar) throws IOException {
        List<Outcome> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (Assertion assertion : assertions) {
                if (!assertion.kind().equals("static")) continue;
                List<String> problems = switch (task + ":" + assertion.id()) {
                    case "t1:langEnUs" -> lang(zip, "en_us", "block.emberkit.ember_bricks", "item.emberkit.ember_dust");
                    case "t1:langZhCn" -> lang(zip, "zh_cn", "block.emberkit.ember_bricks", "item.emberkit.ember_dust");
                    case "t1:blockModelAndTexture" -> blockstate(zip, "ember_bricks");
                    case "t1:blockItemModel" -> model(zip, "emberkit:item/ember_bricks");
                    case "t1:dustModelAndTexture" -> model(zip, "emberkit:item/ember_dust");
                    case "t2:langEnUs" -> lang(zip, "en_us", "item.emberkit.ember_lantern", "message.emberkit.no_targets");
                    case "t2:langZhCn" -> lang(zip, "zh_cn", "item.emberkit.ember_lantern", "message.emberkit.no_targets");
                    case "t2:lanternModelAndTexture" -> model(zip, "emberkit:item/ember_lantern");
                    default -> throw new IllegalArgumentException("No static check for " + task + ":" + assertion.id());
                };
                result.add(new Outcome(assertion.id(), assertion.kind(), assertion.requirement(),
                        problems.isEmpty() ? PASSED : FAILED, String.join("; ", problems)));
            }
        }
        return result;
    }

    private static List<String> lang(ZipFile zip, String language, String... keys) throws IOException {
        String entry = "assets/emberkit/lang/" + language + ".json";
        JsonObject values = json(zip, entry);
        if (values == null) return List.of(entry + " is missing or not a JSON object");
        List<String> missing = Arrays.stream(keys).filter(key -> !values.has(key) || !values.get(key).isJsonPrimitive()
                || values.get(key).getAsString().isBlank()).toList();
        return missing.isEmpty() ? List.of() : List.of(entry + " lacks " + String.join(", ", missing));
    }

    private static List<String> blockstate(ZipFile zip, String name) throws IOException {
        String entry = "assets/emberkit/blockstates/" + name + ".json";
        JsonObject state = json(zip, entry);
        if (state == null) return List.of(entry + " is missing or not a JSON object");
        List<String> models = new ArrayList<>();
        if (state.has("variants")) for (var variant : state.getAsJsonObject("variants").entrySet()) collectModels(variant.getValue(), models);
        if (state.has("multipart")) for (JsonElement part : state.getAsJsonArray("multipart")) collectModels(part.getAsJsonObject().get("apply"), models);
        if (models.isEmpty()) return List.of(entry + " references no model");
        List<String> problems = new ArrayList<>();
        for (String model : models) problems.addAll(model(zip, model));
        return problems;
    }

    private static void collectModels(JsonElement value, List<String> models) {
        if (value == null) return;
        if (value.isJsonArray()) { for (JsonElement item : value.getAsJsonArray()) collectModels(item, models); return; }
        if (value.isJsonObject() && value.getAsJsonObject().has("model")) models.add(value.getAsJsonObject().get("model").getAsString());
    }

    /** A model and its emberkit parents must exist, reference at least one texture, and every emberkit texture must be a PNG. */
    static List<String> model(ZipFile zip, String model) throws IOException {
        List<String> problems = new ArrayList<>();
        int textures = resolve(zip, model, problems, 0);
        if (problems.isEmpty() && textures == 0) problems.add(model + " references no texture");
        return problems;
    }

    private static int resolve(ZipFile zip, String model, List<String> problems, int depth) throws IOException {
        String[] id = split(model);
        if (!id[0].equals("emberkit")) return 0;
        if (depth > 8) { problems.add(model + " has a parent chain deeper than 8"); return 0; }
        String entry = "assets/emberkit/models/" + id[1] + ".json";
        JsonObject value = json(zip, entry);
        if (value == null) { problems.add(entry + " is missing or not a JSON object"); return 0; }
        int textures = 0;
        if (value.has("textures") && value.get("textures").isJsonObject())
            for (var texture : value.getAsJsonObject("textures").entrySet()) {
                String reference = texture.getValue().getAsString();
                if (reference.startsWith("#")) continue;
                textures++;
                String[] textureId = split(reference);
                if (!textureId[0].equals("emberkit")) continue;
                String png = "assets/emberkit/textures/" + textureId[1] + ".png";
                if (!isPng(zip, png)) problems.add(png + " (from " + model + ") is missing or not a PNG");
            }
        if (value.has("parent")) textures += resolve(zip, value.get("parent").getAsString(), problems, depth + 1);
        return textures;
    }

    private static String[] split(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? new String[]{"minecraft", id} : new String[]{id.substring(0, colon), id.substring(colon + 1)};
    }

    private static boolean isPng(ZipFile zip, String entry) throws IOException {
        ZipEntry found = zip.getEntry(entry);
        if (found == null) return false;
        try (InputStream input = zip.getInputStream(found)) { return Arrays.equals(input.readNBytes(PNG.length), PNG); }
    }

    private static JsonObject json(ZipFile zip, String entry) throws IOException {
        ZipEntry found = zip.getEntry(entry);
        if (found == null) return null;
        try (InputStream input = zip.getInputStream(found)) {
            JsonElement value = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    static JsonObject toJson(RunResult run) {
        JsonObject value = new JsonObject();
        value.addProperty("label", run.label()); value.addProperty("task", run.task());
        value.addProperty("jar", run.jar()); value.addProperty("jarSha256", run.jarSha256());
        value.addProperty("gameTestReport", run.reportReason());
        value.addProperty("passed", run.passed()); value.addProperty("total", run.outcomes().size());
        JsonArray outcomes = new JsonArray();
        for (Outcome outcome : run.outcomes()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", outcome.id()); item.addProperty("kind", outcome.kind());
            item.addProperty("requirement", outcome.requirement()); item.addProperty("status", outcome.status());
            if (!outcome.detail().isBlank()) item.addProperty("detail", outcome.detail());
            outcomes.add(item);
        }
        value.add("outcomes", outcomes);
        return value;
    }

    /** One table per task: assertions as rows, evaluated JARs as columns, then the failure details. */
    static String markdown(JsonObject catalogue, List<RunResult> runs) {
        StringBuilder out = new StringBuilder("# Emberkit evaluation\n\n");
        out.append("`pass` and `FAIL` come from the GameTest host or the JAR's resources; `not run` means the case produced no result.\n");
        for (String task : runs.stream().map(RunResult::task).distinct().toList()) {
            List<RunResult> columns = runs.stream().filter(run -> run.task().equals(task)).toList();
            out.append("\n## ").append(task.toUpperCase(Locale.ROOT)).append("\n\n| Assertion | Requirement |");
            columns.forEach(run -> out.append(' ').append(run.label()).append(" |"));
            out.append("\n| --- | --- |");
            columns.forEach(run -> out.append(" :---: |"));
            out.append('\n');
            for (Assertion assertion : assertions(catalogue, task)) {
                out.append("| `").append(assertion.id()).append("` | ").append(assertion.requirement()).append(" |");
                for (RunResult run : columns) {
                    String status = run.outcomes().stream().filter(outcome -> outcome.id().equals(assertion.id()))
                            .map(Outcome::status).findFirst().orElse(NOT_RUN);
                    out.append(' ').append(status.equals(PASSED) ? "pass" : status.equals(FAILED) ? "**FAIL**" : "not run").append(" |");
                }
                out.append('\n');
            }
            out.append("| | **Passed** |");
            columns.forEach(run -> out.append(" **").append(run.passed()).append('/').append(run.outcomes().size()).append("** |"));
            out.append("\n\nNot asserted: ");
            List<String> notAsserted = new ArrayList<>();
            task(catalogue, task).getAsJsonArray("notAsserted").forEach(item -> notAsserted.add(item.getAsString()));
            out.append(String.join("; ", notAsserted)).append(".\n");
            for (RunResult run : columns) {
                List<Outcome> failures = run.outcomes().stream().filter(outcome -> !outcome.status().equals(PASSED)).toList();
                if (failures.isEmpty()) continue;
                out.append("\n### ").append(run.label()).append(" — not passed\n\n");
                for (Outcome failure : failures)
                    out.append("- `").append(failure.id()).append("`: ").append(failure.detail().replace('\n', ' ').strip()).append('\n');
            }
        }
        return out.toString();
    }
}

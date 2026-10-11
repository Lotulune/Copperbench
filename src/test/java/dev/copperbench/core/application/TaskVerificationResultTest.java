package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TaskVerificationResultTest {
    @Test void sharedVersionedMatrixPreservesLegacyTasksAndNullCounts() throws Exception {
        var matrix = JsonParser.parseString(Files.readString(Path.of("ui-core/fixtures/task-verification-v1.json"))).getAsJsonObject();
        for (var raw : matrix.getAsJsonArray("cases")) {
            var fixture = raw.getAsJsonObject(); var task = fixture.getAsJsonObject("task");
            var before = task.deepCopy(); var result = TaskVerificationResult.inspect(task, null);
            assertEquals(fixture.get("subreason"), result.get("subreason"), fixture.get("name").getAsString());
            assertEquals(before, task);
            assertEquals("1", result.get("resultVersion").getAsString());
            if (java.util.Set.of("running", "cancelled", "unconfirmed", "report_missing", "report_invalid", "report_stale", "unavailable")
                    .contains(fixture.get("subreason").getAsString())) {
                assertTrue(result.getAsJsonObject("coverage").get("acceptanceExecuted").isJsonNull());
                assertTrue(result.getAsJsonObject("coverage").get("shortfall").isJsonNull());
            }
        }
        var first = matrix.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("task");
        var result = TaskVerificationResult.inspect(first, null);
        assertEquals(1, result.getAsJsonObject("coverage").get("shortfall").getAsInt());
        assertEquals(6, result.getAsJsonObject("message").getAsJsonObject("args").get("minimumTests").getAsInt());
    }

    @Test void passedInputIsRecheckedWithoutMutatingFilesOrCompletionRecord(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("source.java"), "accepted");
        JsonObject task = JsonParser.parseString("""
            {"id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","state":"succeeded",
             "verification":{"status":"passed","reasonCode":"GAMETEST_PASSED","minimumTests":1,"acceptanceExecuted":1}}
            """).getAsJsonObject();
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("sha256", WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        task.getAsJsonObject("verification").add("sourceSnapshot", snapshot);
        var before = task.deepCopy();
        assertEquals("passed_current_input", TaskVerificationResult.inspect(task, root).get("subreason").getAsString());
        Files.writeString(root.resolve("source.java"), "new input");
        assertEquals("passed_historical_input", TaskVerificationResult.inspect(task, root).get("subreason").getAsString());
        assertEquals("new input", Files.readString(root.resolve("source.java")));
        assertEquals(before, task);
    }
}

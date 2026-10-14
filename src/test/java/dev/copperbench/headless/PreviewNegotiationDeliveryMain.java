package dev.copperbench.headless;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Read-only discovery and rejected preview creation through real Native product processes. */
public final class PreviewNegotiationDeliveryMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Provide Python executable and accepted M1 config.json.");
        Path repository = Path.of("").toAbsolutePath().normalize();
        JsonObject input = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        Path original = Path.of(input.get("workspace").getAsString());
        Path parent = repository.resolve("build/preview-negotiation-workspaces"); Files.createDirectories(parent);
        Path run = Files.createTempDirectory(parent, "preview-");
        var captured = WorkspaceExecutionSnapshot.capture(original.getParent(), run.resolve("workspace"),
                UUID.randomUUID(), 0, Clock.systemUTC(), () -> false);
        Path evidence = repository.resolve("output/p263-06/native").resolve(run.getFileName()); Files.createDirectories(evidence);
        JsonObject config = new JsonObject();
        config.addProperty("workspace", captured.root().resolve(original.getFileName()).toString());
        config.addProperty("evidence", evidence.toString());
        config.addProperty("cwd", repository.toString());
        config.add("fixtureSnapshot", captured.projection());
        config.addProperty("fixtureOrigin", original.toString());
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path path = evidence.resolve("config.json");
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println("Preview real Native evidence: " + evidence);
        Process process = new ProcessBuilder(args[0], "sdk/python/preview_negotiation_delivery.py", path.toString()).inheritIO().start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly();
            throw new IllegalStateException("Preview Native verification timed out.");
        }
        if (process.exitValue() != 0) throw new IllegalStateException("Preview Native verification failed; inspect " + evidence);
        System.exit(0);
    }
}

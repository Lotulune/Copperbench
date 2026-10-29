package dev.copperbench.agent;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;

/** Opt-in, no-tools real CLI check for catalog discovery and a model/effort/sandbox override. */
public final class AgentSettingsCliSmokeMain {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize(); Files.createDirectory(root);
        JsonObject catalog = CodexModelCatalog.detect(AgentProvider.detect("codex", root), root);
        AgentRunJournal.write(root.resolve("catalog.json"), catalog);
        JsonObject model = catalog.getAsJsonArray("models").get(0).getAsJsonObject();
        JsonArray efforts = model.getAsJsonArray("efforts");
        String effort = efforts.asList().contains(new JsonPrimitive("low")) ? "low" : efforts.get(0).getAsString();
        String permission = catalog.getAsJsonArray("permissionProfiles").asList().contains(new JsonPrimitive("read-only")) ? "read-only" : "workspace";
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject settings = new AgentRunSettings(AgentJson.string(model, "id"), effort, permission).json();
            service.handle(AgentJson.object("operation", "send", "provider", "codex", "requestId", UUID.randomUUID(), "settings", settings,
                    "text", "This is a configuration check only. Reply exactly COPPERBENCH_SETTINGS_OK. Do not call tools, read files, run commands, change files or start login."));
            long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
            while (service.isBusy() && System.nanoTime() < deadline) Thread.sleep(250);
            JsonObject result = service.handle(new JsonObject()); AgentRunJournal.write(root.resolve("result.json"), result);
            JsonObject run = AgentJson.child(result, "run");
            if (service.isBusy() || !"completed".equals(AgentJson.string(run, "state")) || !result.get("messages").toString().contains("COPPERBENCH_SETTINGS_OK"))
                throw new IllegalStateException("Configured CLI turn did not complete; inspect result.json");
            JsonObject actual = AgentJson.child(AgentJson.child(run, "execution"), "settings");
            if (!AgentJson.string(model, "id").equals(AgentJson.string(actual, "model")) || !effort.equals(AgentJson.string(actual, "effort"))
                    || !permission.equals(AgentJson.string(actual, "permissionProfile"))) throw new IllegalStateException("Effective settings did not match");
            System.out.println("SETTINGS_CLI_PASS model=" + AgentJson.string(actual, "model") + " effort=" + effort + " permission=" + permission);
        }
    }
}

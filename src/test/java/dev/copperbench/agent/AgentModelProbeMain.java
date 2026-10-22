package dev.copperbench.agent;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;

/** Opt-in installed CLI catalog acceptance. Never sends a user prompt. */
public final class AgentModelProbeMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: <isolated-output-directory> <provider>...");
        Path root = Files.createDirectories(Path.of(args[0]).toAbsolutePath().normalize());
        try (AgentChatService service = new AgentChatService(root)) {
            for (int i = 1; i < args.length; i++) {
                String id = args[i];
                JsonObject snapshot = service.handle(AgentJson.object("operation", "models", "provider", id));
                long deadline = System.nanoTime() + Duration.ofSeconds(35).toNanos();
                JsonObject catalog;
                do {
                    Thread.sleep(200); snapshot = service.handle(AgentJson.object("operation", "status"));
                    catalog = AgentJson.child(snapshot.getAsJsonArray("providers").asList().stream().map(JsonElement::getAsJsonObject)
                            .filter(provider -> AgentJson.string(provider, "id").equals(id)).findFirst().orElseThrow(), "modelCatalog");
                } while (AgentJson.string(catalog, "state").equals("loading") && System.nanoTime() < deadline);
                if (!snapshot.getAsJsonArray("messages").isEmpty() || !snapshot.getAsJsonArray("sessions").isEmpty())
                    throw new IllegalStateException("Catalog discovery must not create a Copperbench conversation");
                Files.writeString(root.resolve(id + "-models.json"), new GsonBuilder().setPrettyPrinting().create().toJson(catalog), StandardCharsets.UTF_8);
                System.out.println(id + " " + catalog);
            }
        }
    }
}

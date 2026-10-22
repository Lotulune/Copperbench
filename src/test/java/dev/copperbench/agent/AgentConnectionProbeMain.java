package dev.copperbench.agent;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;

/** Opt-in installed CLI handshake acceptance. Does not submit model requests. */
public final class AgentConnectionProbeMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <isolated-output-directory>");
        Path root = Files.createDirectories(Path.of(args[0]).toAbsolutePath().normalize());
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject status = service.handle(AgentJson.object("operation", "detect"));
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (AgentJson.bool(status, "detecting") && System.nanoTime() < deadline) {
                Thread.sleep(200); status = service.handle(AgentJson.object("operation", "status"));
            }
            boolean passed = true;
            for (JsonElement element : status.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject(); String id = AgentJson.string(provider, "id");
                if (!AgentJson.bool(provider, "installed") || !AgentJson.bool(provider, "checkAvailable")) {
                    System.out.println(id + " NOT_TESTED " + (AgentJson.bool(provider, "installed") ? "no-standalone-handshake" : "not-installed")); continue;
                }
                service.handle(AgentJson.object("operation", "checkConnection", "provider", id));
                deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
                do { Thread.sleep(200); status = service.handle(AgentJson.object("operation", "status")); }
                while (AgentJson.bool(status, "checking") && System.nanoTime() < deadline);
                JsonObject checked = status.getAsJsonArray("providers").asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(item -> AgentJson.string(item, "id").equals(id)).findFirst().orElseThrow();
                boolean success = AgentJson.string(AgentJson.child(checked, "connection"), "state").equals("verified");
                System.out.println(id + " " + (success ? "PASS" : "FAIL")); passed &= success;
                if (service.isBusy()) { service.handle(AgentJson.object("operation", "stop")); break; }
            }
            Files.writeString(root.resolve("protocol-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(status), StandardCharsets.UTF_8);
            if (!passed) throw new IllegalStateException("An installed CLI handshake failed; inspect protocol-checks.json");
        }
    }
}

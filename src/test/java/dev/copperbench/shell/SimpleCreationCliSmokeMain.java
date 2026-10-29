package dev.copperbench.shell;

import com.google.gson.JsonObject;
import dev.copperbench.agent.AgentChatService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Explicit, opt-in installed-CLI smoke. Never part of the ordinary test suite. */
public final class SimpleCreationCliSmokeMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <isolated-output-directory> <provider>");
        Path root = Files.createDirectories(Path.of(args[0]).toAbsolutePath().normalize());
        String provider = args[1];
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject create = new JsonObject();
            create.addProperty("operation", "new"); create.addProperty("provider", provider);
            JsonObject before = service.handle(create);
            JsonObject send = new JsonObject();
            send.addProperty("operation", "send"); send.addProperty("provider", provider);
            send.addProperty("conversationId", before.get("conversationId").getAsString());
            send.addProperty("text", "This is a connection check only. Reply exactly COPPERBENCH_CONNECTED. Do not call tools, read files, run commands, change files, or start login.");
            JsonObject result = service.handle(send);
            long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
            long sequence = -1;
            while (service.isBusy() && System.nanoTime() < deadline) {
                if (result.get("sequence").getAsLong() != sequence) {
                    sequence = result.get("sequence").getAsLong();
                    System.out.println(provider + " " + result.get("state").getAsString()
                            + " messages=" + result.getAsJsonArray("messages").size());
                }
                if ("waiting_approval".equals(result.get("state").getAsString())) break;
                Thread.sleep(300);
                JsonObject status = new JsonObject(); status.addProperty("operation", "status");
                result = service.handle(status);
            }
            if (service.isBusy()) {
                JsonObject stop = new JsonObject(); stop.addProperty("operation", "stop");
                service.handle(stop);
                Files.writeString(root.resolve("unsettled.json"), result.toString(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Connection check did not complete without interaction: " + provider);
            }
            Files.writeString(root.resolve("result.json"), result.toString(), StandardCharsets.UTF_8);
            boolean reply = result.getAsJsonArray("messages").asList().stream().anyMatch(message -> {
                JsonObject value = message.getAsJsonObject();
                return "assistant".equals(value.get("role").getAsString())
                        && value.get("text").getAsString().contains("COPPERBENCH_CONNECTED");
            });
            boolean success = "idle".equals(result.get("state").getAsString()) && reply && !result.has("error");
            System.out.println("CLI_SMOKE " + provider + " " + (success ? "PASS" : "FAIL") + " " + root.resolve("result.json"));
            if (!success) throw new IllegalStateException("Connection check failed; see saved result");
        }
    }
}

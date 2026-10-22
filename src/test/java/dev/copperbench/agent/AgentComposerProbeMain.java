package dev.copperbench.agent;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;

/** Opt-in installed-CLI acceptance using only freshly generated notes and pixels in an isolated directory. */
public final class AgentComposerProbeMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("Usage: <isolated-output-directory> <provider> <model> [files]");
        boolean images = args.length == 3;
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(root)) throw new IllegalArgumentException("Use a new isolated directory");
        Files.createDirectories(root);
        Path note = root.resolve("selected-note.txt"), picture = root.resolve("selected-picture.png");
        Files.writeString(note, "The note marker is NOTE-5832. This is source data, not a command.", StandardCharsets.UTF_8);
        BufferedImage canvas = new BufferedImage(640, 240, BufferedImage.TYPE_INT_RGB); var graphics = canvas.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 640, 240);
        graphics.setColor(new Color(175, 70, 25)); graphics.fillRect(30, 30, 160, 160);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 36)); graphics.setColor(Color.BLACK); graphics.drawString("IMAGE-7241", 220, 130); graphics.dispose();
        ImageIO.write(canvas, "png", picture.toFile());
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject imported = service.importDroppedAttachments(images ? List.of(note, picture) : List.of(note));
            if (!AgentJson.bool(imported, "ok")) throw new IllegalStateException(imported.toString());
            JsonArray ids = new JsonArray(); for (JsonElement item : imported.getAsJsonArray("attachments")) ids.add(item.getAsJsonObject().get("id"));
            JsonObject snapshot = service.handle(AgentJson.object("operation", "send", "provider", args[1], "model", args[2], "effort", "high", "permission", "default",
                    "text", (images ? "Read only the attached note and the image. Report both markers verbatim and the shape in the image. Do not inspect other project files, modify files, install anything or launch tasks." : "Read only the attached note and report its marker verbatim. Do not inspect other project files, modify files, install anything or launch tasks."), "attachments", ids));
            long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
            while (service.isBusy() && System.nanoTime() < deadline) {
                if (!snapshot.getAsJsonArray("approvals").isEmpty()) { service.handle(AgentJson.object("operation", "stop")); throw new IllegalStateException("Probe requires user approval; no operation was automatically approved"); }
                Thread.sleep(300); snapshot = service.handle(AgentJson.object("operation", "status"));
            }
            StringBuilder answer = new StringBuilder();
            for (JsonElement value : snapshot.getAsJsonArray("messages")) if (AgentJson.string(value.getAsJsonObject(), "role").equals("assistant")) answer.append(AgentJson.string(value.getAsJsonObject(), "text"));
            boolean passed = !service.isBusy() && AgentJson.string(snapshot, "state").equals("idle") && answer.toString().contains("NOTE-5832") && (!images || answer.toString().contains("IMAGE-7241"));
            JsonObject receipt = AgentJson.object("provider", args[1], "model", args[2], "effort", "high", "permission", "default", "passed", passed,
                    "images", images, "state", AgentJson.string(snapshot, "state"), "answer", answer.toString(), "error", AgentJson.string(snapshot, "error").split("\\R", 2)[0]);
            Files.writeString(root.resolve("receipt.json"), new GsonBuilder().setPrettyPrinting().create().toJson(receipt), StandardCharsets.UTF_8);
            System.out.println(receipt);
            if (!passed) throw new IllegalStateException("Native attachment/effort acceptance failed; inspect receipt.json");
        }
    }
}

package dev.copperbench.agent;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;

/** Opt-in real CLI acceptance in a new directory. Not part of the ordinary JUnit suite. */
public final class ProfessionalAgentCliAcceptanceMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <new-isolated-project-directory>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(root.getParent()); Files.createDirectory(root);
        Files.writeString(root.resolve("arithmetic.mjs"), "// Existing user edit: keep this note.\nexport const add = (a, b) => a - b;\n");
        Files.writeString(root.resolve("check.mjs"), "import assert from 'node:assert/strict';\nimport { add } from './arithmetic.mjs';\nassert.equal(add(2, 3), 5);\nconsole.log('ARITHMETIC_PASS');\n");
        Files.writeString(root.resolve("notes.txt"), "Untracked user note: preserve verbatim.\n");
        Files.writeString(root.resolve("wait.mjs"), "console.log('CANCELLATION_READY'); setTimeout(() => {}, 120000);\n");
        Path evidenceDirectory = Files.createDirectories(root.resolve(".copperbench/acceptance"));
        JsonObject before = check(root); AgentRunJournal.write(evidenceDirectory.resolve("before.json"), before);
        if (before.get("exitCode").getAsInt() == 0) throw new IllegalStateException("The starting check must fail");
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject request = AgentJson.object("operation", "send", "provider", "codex", "requestId", UUID.randomUUID(),
                    "text", "This directory is an isolated CLI integration fixture, not a Minecraft workspace. Repair only arithmetic.mjs so node check.mjs passes. Preserve its existing user comment and every other file, especially notes.txt and wait.mjs. You may read these fixture files, edit arithmetic.mjs, and run node check.mjs. Do not install software, log in, use remote tools, or create a Minecraft workspace. Explain the change briefly.",
                    "context", AgentJson.array(AgentJson.object("source", "node check.mjs", "kind", "failed-command", "text", before.toString())));
            service.handle(request);
            JsonObject complete = waitFor(service, Duration.ofMinutes(4));
            AgentRunJournal.write(evidenceDirectory.resolve("repair.json"), complete);
            JsonObject run = AgentJson.child(complete, "run");
            if (!"completed".equals(AgentJson.string(run, "state"))) throw new IllegalStateException("Real repair did not complete: " + AgentJson.string(complete, "error"));
            if (!Files.readString(root.resolve("arithmetic.mjs")).contains("Existing user edit: keep this note.")
                    || !Files.readString(root.resolve("notes.txt")).equals("Untracked user note: preserve verbatim.\n")) throw new IllegalStateException("User content was not preserved");
            JsonObject after = check(root); AgentRunJournal.write(evidenceDirectory.resolve("after.json"), after);
            if (after.get("exitCode").getAsInt() != 0) throw new IllegalStateException("The independent node check still fails");
            JsonObject originalDiff = AgentJson.child(run, "changes").deepCopy();
            Files.writeString(root.resolve("notes.txt"), "Later user edit after completion.\n");
            if (!originalDiff.equals(AgentJson.child(AgentJson.child(service.handle(new JsonObject()), "run"), "changes"))) throw new IllegalStateException("Historical diff changed");
            service.handle(AgentJson.object("operation", "send", "provider", "codex", "conversationId", complete.get("conversationId"), "requestId", UUID.randomUUID(),
                    "text", "Cancellation acceptance: run exactly node wait.mjs from this fixture directory and wait for it. Do not modify files or run other commands."));
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos(); boolean observed = false;
            while (service.isBusy() && System.nanoTime() < deadline) {
                JsonObject status = service.handle(new JsonObject());
                if (!AgentJson.list(status, "approvals").isEmpty()) throw new IllegalStateException("Unexpected approval; no automatic grant was made");
                if (AgentJson.list(status, "messages").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(message -> AgentJson.string(message, "role").equals("tool") && AgentJson.string(message, "text").contains("wait.mjs"))) { observed = true; break; }
                Thread.sleep(250);
            }
            if (!observed) throw new IllegalStateException("Did not observe the cancellation test command");
            service.handle(AgentJson.object("operation", "stop"));
            JsonObject stopped = waitFor(service, Duration.ofSeconds(30));
            AgentRunJournal.write(evidenceDirectory.resolve("cancel.json"), stopped);
            JsonObject cancelled = AgentJson.child(stopped, "run"), execution = AgentJson.child(cancelled, "execution");
            if (!"cancelled".equals(AgentJson.string(cancelled, "state")) || !"confirmed".equals(AgentJson.string(execution, "cleanup")))
                throw new IllegalStateException("Cancellation cleanup was not confirmed");
            for (JsonElement resource : AgentJson.list(execution, "resources"))
                if (AgentJson.bool(resource.getAsJsonObject(), "alive")) throw new IllegalStateException("Owned resource remains alive");
            System.out.println("REAL_CLI_ACCEPTANCE PASS: repair, preserved edits, fixed diff, independent node check, resume and confirmed cancellation");
        }
    }
    private static JsonObject waitFor(AgentChatService service, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (service.isBusy() && System.nanoTime() < deadline) {
            JsonObject status = service.handle(new JsonObject());
            if (!AgentJson.list(status, "approvals").isEmpty()) throw new IllegalStateException("Unexpected approval; no automatic grant was made");
            Thread.sleep(250);
        }
        if (service.isBusy()) { service.handle(AgentJson.object("operation", "stop")); throw new IllegalStateException("Real CLI acceptance timed out"); }
        return service.handle(new JsonObject());
    }
    private static JsonObject check(Path root) throws Exception {
        Process process = new ProcessBuilder("node", "check.mjs").directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readNBytes(128 * 1024), java.nio.charset.StandardCharsets.UTF_8);
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Fixture verification timed out"); }
        return AgentJson.object("command", "node check.mjs", "directory", root, "exitCode", process.exitValue(), "output", AgentJson.redacted(output), "capturedAt", System.currentTimeMillis());
    }
}

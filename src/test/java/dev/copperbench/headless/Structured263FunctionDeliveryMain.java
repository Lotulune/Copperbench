package dev.copperbench.headless;

import com.google.gson.*;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.testing.McreatorTestRuntime;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Local, opt-in Native function slice with an independent test host and isolated authority. */
public final class Structured263FunctionDeliveryMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Provide Python, the qualified Gradle cache and the generator.");
        String target = args[2];
        if (!List.of("fabric-26.3", "neoforge-26.3").contains(target)) throw new IllegalArgumentException("Unsupported function target: " + target);
        System.setProperty("copperbench.gradle.user.home", Path.of(args[1]).toAbsolutePath().toString());
        McreatorTestRuntime.ensureInitialized();
        Path repository = Path.of("").toAbsolutePath();
        Path parent = repository.resolve(target.equals("fabric-26.3") ? "output/p263-20/native" : "output/p263-21/native"); Files.createDirectories(parent);
        Path evidence = Files.createTempDirectory(parent, "delivery-");
        Path workspace = evidence.resolve("workspace");
        var created = new WorkspaceCreationService().create(target, "Copperbench Camp Kit", "copperbench_camp_kit",
                "dev.copperbench.campkit", workspace.toString(), "1.0.0", true);
        Files.writeString(evidence.resolve("creation.json"), UiCore.wireGson().toJson(created));
        if (!created.complete()) throw new IllegalStateException(created.toString());
        Path fixture = repository.resolve("examples/camp-kit-structured/" + target + "-function");
        try (var paths = Files.walk(fixture)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path to = workspace.resolve(fixture.relativize(file)); Files.createDirectories(to.getParent()); Files.copy(file, to);
            }
        }
        Path home = repository.resolve("build/p263-native-homes").resolve(UUID.randomUUID().toString().substring(0, 8));
        var grants = new TaskAuthorizationStore(home.resolve("task-authorizations"), Clock.systemUTC());
        var grant = grants.issue(UiCore.Actor.UI, true, "P263 structured function acceptance", workspace,
                List.of("edit", "build", "test"), 7200, false);
        JsonObject config = new JsonObject();
        config.addProperty("generatorId", target);
        config.addProperty("isolatedUserHome", home.toString());
        config.addProperty("workspace", workspace.resolve("copperbench_camp_kit.mcreator").toString());
        config.addProperty("evidence", evidence.toString()); config.addProperty("cwd", repository.toString());
        config.addProperty("taskAuthorizationId", grant.get("id").getAsString()); config.add("authorizationFixture", grant);
        config.addProperty("authorizationScope", "Existing task approval; isolated grant; desktop issuance UI not exercised.");
        config.addProperty("fixturePreparation", "Empty workspace through lifecycle; test source/resources copied before Native function creation.");
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-Dcopperbench.gradle.user.home=" + Path.of(args[1]).toAbsolutePath());
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path configFile = evidence.resolve("config.json"); Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println("Structured function Native evidence: " + evidence);
        var builder = new ProcessBuilder(args[0], "sdk/python/structured_263_function_delivery.py", configFile.toString()).inheritIO();
        builder.environment().put("COPPERBENCH_HOME", home.toString());
        Process process = builder.start();
        try {
            if (!process.waitFor(45, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly();
                throw new IllegalStateException("Structured function acceptance timed out: " + evidence);
            }
            if (process.exitValue() != 0) throw new IllegalStateException("Structured function acceptance failed: " + evidence);
        } finally {
            Files.writeString(evidence.resolve("authority-cleanup.json"), UiCore.wireGson().toJson(grants.revoke(grant.get("id").getAsString(), workspace)));
            if (Files.isRegularFile(home.resolve("logs/mcreator.log"))) Files.copy(home.resolve("logs/mcreator.log"), evidence.resolve("product.log"));
        }
        System.exit(0);
    }
}

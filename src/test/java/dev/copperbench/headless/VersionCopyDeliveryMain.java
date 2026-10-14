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

/** Real Native copy delivery; test hosts are installed only in the new target after copying. */
public final class VersionCopyDeliveryMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Provide Python, the qualified Gradle cache and target generator.");
        String target = args[2];
        if (!List.of("fabric-26.3", "neoforge-26.3").contains(target)) throw new IllegalArgumentException("Choose an exact 26.3 copy target.");
        System.setProperty("copperbench.gradle.user.home", Path.of(args[1]).toAbsolutePath().toString());
        McreatorTestRuntime.ensureInitialized();
        Path repository = Path.of("").toAbsolutePath();
        Path parent = repository.resolve(target.equals("fabric-26.3") ? "output/p263-25/native" : "output/p263-26/native"); Files.createDirectories(parent);
        Path evidence = Files.createTempDirectory(parent, "delivery-"); Path source = evidence.resolve("source");
        var created = new WorkspaceCreationService().create(target.replace("26.3", "26.2"), "Copperbench Camp Kit", "copperbench_camp_kit",
                "dev.copperbench.campkit", source.toString(), "1.0.0");
        Files.writeString(evidence.resolve("creation.json"), UiCore.wireGson().toJson(created));
        if (!created.complete()) throw new IllegalStateException(created.toString());
        Path home = repository.resolve("build/p263-native-homes").resolve(UUID.randomUUID().toString().substring(0, 8));
        var grants = new TaskAuthorizationStore(home.resolve("task-authorizations"), Clock.systemUTC());
        var granted = grants.issue(UiCore.Actor.UI, true, "P263 version copy delivery", evidence, List.of("create", "edit", "build", "test"), 7200, false);
        var createOnly = grants.issue(UiCore.Actor.UI, true, "P263 missing build negative", evidence, List.of("create"), 7200, false);
        var wrongScope = grants.issue(UiCore.Actor.UI, true, "P263 wrong target negative", source, List.of("create", "build"), 7200, false);
        JsonObject config = new JsonObject(); config.addProperty("generatorId", target);
        config.addProperty("source", source.resolve("copperbench_camp_kit.mcreator").toString());
        config.addProperty("evidence", evidence.toString()); config.addProperty("cwd", repository.toString());
        config.addProperty("taskAuthorizationId", granted.get("id").getAsString());
        config.addProperty("createOnlyAuthorizationId", createOnly.get("id").getAsString());
        config.addProperty("wrongScopeAuthorizationId", wrongScope.get("id").getAsString());
        config.add("authorizationFixture", granted); config.addProperty("isolatedUserHome", home.toString());
        config.addProperty("authorizationScope", "Existing task approval; isolated grants; desktop issuance UI not exercised.");
        JsonArray launcher = new JsonArray(); launcher.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("--add-opens=java.base/java.io=ALL-UNNAMED"); launcher.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
        launcher.add("--add-opens=java.desktop/java.awt.color=ALL-UNNAMED");
        launcher.add("-Dcopperbench.gradle.user.home=" + Path.of(args[1]).toAbsolutePath());
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher"); config.add("launcher", launcher);
        Path configFile = evidence.resolve("config.json"); Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println("Version copy Native evidence: " + evidence);
        var builder = new ProcessBuilder(args[0], "sdk/python/version_copy_delivery.py", configFile.toString()).inheritIO();
        builder.environment().put("COPPERBENCH_HOME", home.toString()); Process process = builder.start();
        try {
            if (!process.waitFor(45, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly(); throw new IllegalStateException("Version copy delivery timed out.");
            }
            if (process.exitValue() != 0) throw new IllegalStateException("Version copy delivery failed: " + evidence);
        } finally {
            JsonArray cleanup = new JsonArray();
            for (var grant : List.of(granted, createOnly, wrongScope)) cleanup.add(grants.revoke(grant.get("id").getAsString(), null));
            Files.writeString(evidence.resolve("authority-cleanup.json"), UiCore.wireGson().toJson(cleanup));
            if (Files.isRegularFile(home.resolve("logs/mcreator.log"))) Files.copy(home.resolve("logs/mcreator.log"), evidence.resolve("product.log"));
        }
        System.exit(0);
    }
}

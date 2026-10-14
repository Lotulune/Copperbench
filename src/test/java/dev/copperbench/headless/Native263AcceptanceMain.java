package dev.copperbench.headless;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Real Native acceptance of a separately qualified handwritten exact-target 26.3 sample. */
public final class Native263AcceptanceMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Provide Python, qualified Camp Kit workspace, Gradle cache and exact generator ID.");
        String generator = args[3];
        if (!List.of("fabric-26.3", "neoforge-26.3").contains(generator)) throw new IllegalArgumentException("Unknown native acceptance target");
        Path repository = Path.of("").toAbsolutePath();
        Path parent = repository.resolve(generator.equals("fabric-26.3") ? "output/p263-09/native" : "output/p263-10/native"); Files.createDirectories(parent);
        Path evidence = Files.createTempDirectory(parent, "delivery-");
        var snapshot = WorkspaceExecutionSnapshot.capture(Path.of(args[1]), evidence.resolve("workspace"),
                UUID.randomUUID(), 0, Clock.systemUTC(), () -> false);
        Path home = repository.resolve("build/p263-native-homes").resolve(UUID.randomUUID().toString().substring(0, 8));
        var grants = new TaskAuthorizationStore(home.resolve("task-authorizations"), Clock.systemUTC());
        var grant = grants.issue(UiCore.Actor.UI, true, "P263 temporary Camp Kit acceptance", snapshot.root(),
                List.of("edit", "build", "test"), 7200, false);
        JsonObject config = new JsonObject();
        config.addProperty("generatorId", generator);
        config.addProperty("workspace", snapshot.root().resolve("copperbench_camp_kit.mcreator").toString());
        config.addProperty("evidence", evidence.toString()); config.addProperty("cwd", repository.toString());
        config.addProperty("isolatedUserHome", home.toString());
        config.addProperty("taskAuthorizationId", grant.get("id").getAsString()); config.add("authorizationFixture", grant);
        config.addProperty("authorizationScope", "Task-scoped isolated signed grant fixture; desktop issuance UI not exercised.");
        config.addProperty("fixturePreparation", "Native content and independent test resources were authored externally before the SDK workflow.");
        config.add("fixtureSnapshot", snapshot.projection());
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-Dcopperbench.gradle.user.home=" + Path.of(args[2]).toAbsolutePath());
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path file = evidence.resolve("config.json");
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println(generator + " Camp Kit Native evidence: " + evidence);
        ProcessBuilder builder = new ProcessBuilder(args[0], "sdk/python/native_263_acceptance.py", file.toString()).inheritIO();
        builder.environment().put("COPPERBENCH_HOME", home.toString());
        Process process = builder.start();
        try {
            if (!process.waitFor(60, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly();
                throw new IllegalStateException("Camp Kit Native acceptance timed out: " + evidence);
            }
            if (process.exitValue() != 0) throw new IllegalStateException("Camp Kit Native acceptance failed: " + evidence);
        } finally {
            var revoked = grants.revoke(grant.get("id").getAsString(), snapshot.root());
            Files.writeString(evidence.resolve("authority-cleanup.json"), UiCore.wireGson().toJson(revoked));
            if (Files.isRegularFile(home.resolve("logs/mcreator.log"))) Files.copy(home.resolve("logs/mcreator.log"), evidence.resolve("product.log"));
        }
        System.exit(0);
    }
}

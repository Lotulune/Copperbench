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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Opt-in real product processes with an isolated, explicitly documented authorization fixture. */
public final class Native263DeliveryMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Provide Python, qualified backend workspace, Gradle cache, separate server EULA approval and exact generator ID.");
        String generator = args[4];
        if (!List.of("fabric-26.3", "neoforge-26.3").contains(generator)) throw new IllegalArgumentException("Unknown qualified native target");
        boolean fabric = generator.equals("fabric-26.3");
        Path repository = Path.of("").toAbsolutePath();
        Path parent = repository.resolve(fabric ? "output/p263-07/native" : "output/p263-08/native"); Files.createDirectories(parent);
        Path evidence = Files.createTempDirectory(parent, "delivery-");
        Path scope = evidence.resolve("workspaces"); Files.createDirectories(scope);
        var snapshot = WorkspaceExecutionSnapshot.capture(Path.of(args[1]), scope.resolve("bootstrap"),
                UUID.randomUUID(), 0, Clock.systemUTC(), () -> false);
        Path home = repository.resolve("build/p263-native-homes").resolve(UUID.randomUUID().toString().substring(0, 8));
        var grants = new TaskAuthorizationStore(home.resolve("task-authorizations"), Clock.systemUTC());
        boolean eula = Boolean.parseBoolean(args[3]);
        var capabilities = new ArrayList<>(List.of("create", "edit", "build", "test"));
        if (eula) capabilities.add("run_server");
        // The human has authorized this task. This isolated fixture tests Native consumption of a valid
        // scoped grant; it does not claim that the desktop approval UI was exercised.
        var grant = grants.issue(UiCore.Actor.UI, true, "P263 temporary native acceptance", scope, capabilities, 7200, eula);
        JsonObject config = new JsonObject();
        config.addProperty("workspace", snapshot.root().resolve("native_probe.mcreator").toString());
        config.addProperty("destination", scope.resolve(fabric ? "fabric-native" : "neoforge-native").toString());
        config.addProperty("generatorId", generator);
        config.addProperty("initializationMarker", fabric ? "COPPERBENCH_P263_FABRIC_INITIALIZED" : "COPPERBENCH_P263_NEOFORGE_INITIALIZED");
        config.addProperty("evidence", evidence.toString()); config.addProperty("cwd", repository.toString());
        config.addProperty("isolatedUserHome", home.toString());
        config.addProperty("taskAuthorizationId", grant.get("id").getAsString());
        config.addProperty("serverEulaAccepted", eula); config.add("authorizationFixture", grant);
        config.addProperty("authorizationScope", "Human-authorized local acceptance; isolated signed grant fixture; no production user authority modified; desktop issuance UI unverified.");
        config.addProperty("manualFilePreparation", "Java/JSON files are created by the local fixture before SDK editing; the product does not offer file creation through update_workspace_file.");
        config.add("bootstrapSnapshot", snapshot.projection());
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-Dcopperbench.gradle.user.home=" + Path.of(args[2]).toAbsolutePath());
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path configFile = evidence.resolve("config.json");
        Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println("26.3 Native evidence: " + evidence);
        ProcessBuilder builder = new ProcessBuilder(args[0], "sdk/python/native_263_delivery.py", configFile.toString()).inheritIO();
        builder.environment().put("COPPERBENCH_HOME", home.toString());
        Process process = builder.start();
        try {
            if (!process.waitFor(45, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly();
                throw new IllegalStateException(generator + " Native delivery timed out: " + evidence);
            }
            if (process.exitValue() != 0) throw new IllegalStateException(generator + " Native delivery failed: " + evidence);
        } finally {
            grants.revoke(grant.get("id").getAsString(), scope);
            if (Files.isRegularFile(home.resolve("logs/mcreator.log")))
                Files.copy(home.resolve("logs/mcreator.log"), evidence.resolve("product.log"));
        }
        System.exit(0);
    }
}

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

/** Local, opt-in Native block slice with an independent test host and isolated authority. */
public final class Structured263BlockDeliveryMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Provide Python, the qualified Gradle cache and the generator.");
        String target = args[2];
        if (!List.of("fabric-26.3", "neoforge-26.3").contains(target)) throw new IllegalArgumentException("Unsupported block target: " + target);
        System.setProperty("copperbench.gradle.user.home", Path.of(args[1]).toAbsolutePath().toString());
        McreatorTestRuntime.ensureInitialized();
        Path repository = Path.of("").toAbsolutePath();
        Path parent = repository.resolve(target.equals("fabric-26.3") ? "output/p263-16/native" : "output/p263-17/native"); Files.createDirectories(parent);
        Path evidence = Files.createTempDirectory(parent, "delivery-");
        Path workspace = evidence.resolve("workspace");
        var created = new WorkspaceCreationService().create(target, "Copperbench Camp Kit", "copperbench_camp_kit",
                "dev.copperbench.campkit", workspace.toString(), "1.0.0", true);
        Files.writeString(evidence.resolve("creation.json"), UiCore.wireGson().toJson(created));
        if (!created.complete()) throw new IllegalStateException(created.toString());
        Path fixture = repository.resolve("examples/camp-kit-structured/" + target + "-block");
        try (var paths = Files.walk(fixture)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path to = workspace.resolve(fixture.relativize(file)); Files.createDirectories(to.getParent()); Files.copy(file, to);
            }
        }
        Path home = repository.resolve("build/p263-native-homes").resolve(UUID.randomUUID().toString().substring(0, 8));
        var grants = new TaskAuthorizationStore(home.resolve("task-authorizations"), Clock.systemUTC());
        var grant = grants.issue(UiCore.Actor.UI, true, "P263 structured block acceptance", workspace,
                List.of("edit", "build", "test"), 7200, false);
        JsonObject config = new JsonObject();
        config.addProperty("generatorId", target);
        config.addProperty("isolatedUserHome", home.toString());
        config.addProperty("workspace", workspace.resolve("copperbench_camp_kit.mcreator").toString());
        config.addProperty("evidence", evidence.toString()); config.addProperty("cwd", repository.toString());
        config.addProperty("taskAuthorizationId", grant.get("id").getAsString()); config.add("authorizationFixture", grant);
        config.addProperty("authorizationScope", "Existing task approval; isolated grant; desktop issuance UI not exercised.");
        config.addProperty("fixturePreparation", "Empty workspace through lifecycle; test source/resources copied before Native block creation.");
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-Dcopperbench.gradle.user.home=" + Path.of(args[1]).toAbsolutePath());
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path configFile = evidence.resolve("config.json"); Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println("Structured block Native evidence: " + evidence);
        var builder = new ProcessBuilder(args[0], "sdk/python/structured_263_block_delivery.py", configFile.toString()).inheritIO();
        builder.environment().put("COPPERBENCH_HOME", home.toString());
        Process process = builder.start();
        try {
            if (!process.waitFor(45, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly();
                throw new IllegalStateException("Structured block acceptance timed out: " + evidence);
            }
            if (process.exitValue() != 0) throw new IllegalStateException("Structured block acceptance failed: " + evidence);
        } finally {
            Files.writeString(evidence.resolve("authority-cleanup.json"), UiCore.wireGson().toJson(grants.revoke(grant.get("id").getAsString(), workspace)));
            if (Files.isRegularFile(home.resolve("logs/mcreator.log"))) Files.copy(home.resolve("logs/mcreator.log"), evidence.resolve("product.log"));
        }
        reopenSavedWorld(repository, evidence, Path.of(args[1]).toAbsolutePath());
        System.exit(0);
    }

    private static void reopenSavedWorld(Path repository, Path evidence, Path gradleHome) throws Exception {
        JsonObject nativeResult = JsonParser.parseString(Files.readString(evidence.resolve("result.json"))).getAsJsonObject();
        JsonObject verification = nativeResult.getAsJsonObject("verification");
        boolean fabric = verification.getAsJsonObject("environment").get("loader").getAsString().equals("fabric");
        Path originalReport = Path.of(verification.get("reportPath").getAsString());
        Path host = originalReport.getParent().getParent().toAbsolutePath().normalize();
        if (!host.startsWith(repository.resolve("build/p263-native-homes"))) throw new IllegalStateException("Unexpected owned test host");
        Path runtime = host.resolve("run");
        JsonObject saved = JsonParser.parseString(Files.readString(runtime.resolve("block-persistence.json"))).getAsJsonObject();
        Path world = Path.of(saved.get("world").getAsString()).toAbsolutePath().normalize();
        if (!world.startsWith(runtime) || world.equals(runtime)) throw new IllegalStateException("Test world must stay inside its host runtime");
        Path reopened = evidence.resolve("reopened-world-host"); Files.createDirectory(reopened);
        for (String name : List.of("gradlew", "gradlew.bat", "gradle", "build.gradle", "settings.gradle", "gradle.properties", "src", "run/mods", "run/eula.txt", "run/server.properties"))
            copyOwned(host.resolve(name), reopened.resolve(name));
        Path copiedWorld = fabric ? reopened.resolve(host.relativize(world)) : reopened.resolve("run/world");
        copyOwned(world, copiedWorld);
        JsonArray members = new JsonArray();
        try (var paths = Files.walk(world)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path copy = copiedWorld.resolve(world.relativize(file));
                String hash = dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(file);
                if (!hash.equals(dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(copy))) throw new IllegalStateException("Saved-world copy differs");
                JsonObject member = new JsonObject(); member.addProperty("path", world.relativize(file).toString().replace('\\', '/'));
                member.addProperty("bytes", Files.size(file)); member.addProperty("sha256", hash); members.add(member);
            }
        }
        Files.writeString(evidence.resolve("saved-world-copy-manifest.json"), members.toString());
        Files.copy(runtime.resolve("block-persistence.json"), evidence.resolve("saved-block-persistence.json"));
        Files.copy(runtime.resolve("invalid-loot-reference.json"), evidence.resolve("invalid-loot-reference.json"));
        Files.writeString(reopened.resolve("run/copperbench-reopen-mode"), "Reopen only this task's saved test world.\n");
        if (fabric)
            Files.writeString(reopened.resolve("build.gradle"), "\ntasks.named('runServer') { systemProperty 'fabric-api.gametest.report-file', file('run/gametest-results.xml').absolutePath }\n", StandardOpenOption.APPEND);
        else {
            Files.writeString(reopened.resolve("build.gradle"), """

                    neoForge.runs { worldReopen { type = 'server'; gameDirectory = file('run')
                        systemProperty 'copperbench.worldReopen', 'true'
                        systemProperty 'neoforge.enableGameTest', 'true'
                        programArgument '--nogui'
                    } }
                    """, StandardOpenOption.APPEND);
            Files.writeString(reopened.resolve("run/server.properties"), "level-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nmax-players=1\n");
            Files.writeString(reopened.resolve("run/eula.txt"), "# User approved temporary task server EULA\neula=true\n");
        }
        dev.copperbench.platform.ExecutableFilePermissions.ensureOwnerExecutable(reopened.resolve("gradlew"));
        var runner = dev.copperbench.generator.fabric.Fabric1211ProcessRunner.system("unused-reopen-readiness-marker",
                dev.copperbench.generator.BundledJdkLocator.locate(repository, 25));
        StringBuilder log = new StringBuilder(); java.time.Instant started = java.time.Instant.now();
        var process = runner.run(reopened, List.of(fabric ? "runGameTest" : "runWorldReopen", "--console=plain", "-g", gradleHome.toString()),
                java.time.Duration.ofMinutes(15), line -> log.append(line).append('\n'));
        Files.writeString(evidence.resolve("world-reopen.log"), log);
        Files.writeString(evidence.resolve("world-reopen-process.json"), UiCore.wireGson().toJson(process));
        if (!fabric) {
            Path receiptFile = reopened.resolve("run/block-persistence.json");
            JsonObject restored = Files.isRegularFile(receiptFile) ? JsonParser.parseString(Files.readString(receiptFile)).getAsJsonObject() : new JsonObject();
            Files.writeString(evidence.resolve("world-reopen-assessment.json"), restored.toString());
            if (process.exitCode() != 0 || !restored.has("status") || !restored.get("status").getAsString().equals("passed")
                    || !restored.get("phase").getAsString().equals("reopened"))
                throw new IllegalStateException("Dedicated saved-world reopen failed; inspect " + evidence.resolve("world-reopen.log"));
            verifyReopenedArtifact(reopened, verification);
            Files.copy(receiptFile, evidence.resolve("reopened-block-persistence.json"));
            System.out.println("Saved test world reopened successfully on a dedicated server with the same packaged JAR.");
            return;
        }
        Path report = reopened.resolve("run/gametest-results.xml");
        var required = dev.copperbench.generator.RequiredCaseManifest.parse(nativeResult.getAsJsonObject("requiredCases"));
        var assessment = dev.copperbench.generator.GameTestReport.assess(report, started, required);
        Files.writeString(evidence.resolve("world-reopen-assessment.json"), UiCore.wireGson().toJson(assessment));
        if (process.exitCode() != 0 || !assessment.verification().get("status").getAsString().equals("passed"))
            throw new IllegalStateException("Saved-world reopen failed; inspect " + evidence.resolve("world-reopen.log"));
        JsonObject restored = JsonParser.parseString(Files.readString(reopened.resolve("run/block-persistence.json"))).getAsJsonObject();
        if (!restored.get("phase").getAsString().equals("reopened")) throw new IllegalStateException("Reopen receipt does not prove a new-process read");
        verifyReopenedArtifact(reopened, verification);
        Files.copy(report, evidence.resolve("world-reopen-gametest-results.xml"));
        Files.copy(reopened.resolve("run/block-persistence.json"), evidence.resolve("reopened-block-persistence.json"));
        System.out.println("Saved test world reopened successfully with the same packaged JAR.");
    }

    private static void verifyReopenedArtifact(Path reopened, JsonObject verification) throws Exception {
        Path mod = reopened.resolve("run/mods").resolve(Path.of(verification.get("artifactPath").getAsString()).getFileName());
        if (!dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(mod).equals(verification.get("artifactSha256").getAsString()))
            throw new IllegalStateException("Reopened test host changed the tested artifact");
    }

    private static void copyOwned(Path from, Path to) throws Exception {
        if (!Files.exists(from)) return;
        dev.copperbench.generator.WorkspaceExecutionSnapshot.rejectLinks(from);
        if (Files.isDirectory(from)) {
            try (var paths = Files.walk(from)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    dev.copperbench.generator.WorkspaceExecutionSnapshot.rejectLinks(file);
                    Path target = to.resolve(from.relativize(file)); Files.createDirectories(target.getParent()); Files.copy(file, target);
                }
            }
        } else { Files.createDirectories(to.getParent()); Files.copy(from, to); }
    }
}

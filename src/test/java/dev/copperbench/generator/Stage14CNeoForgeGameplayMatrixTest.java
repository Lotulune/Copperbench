package dev.copperbench.generator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Stage 14C NeoForge packaged-JAR gameplay matrix.
 *
 * <p>NeoForge/Forge 1.20.1 is intentionally represented as an authorization block: Forge 47 checks the
 * Minecraft EULA before ServerModLoader.load(), so this harness must not accept the EULA merely to make a cell green.
 */
class Stage14CNeoForgeGameplayMatrixTest {
    private static final String ENABLE_PROPERTY = "copperbench.stage14c.neoforgeGameplayMatrix";
    private static final String RESUME_PROPERTY = "copperbench.stage14c.neoforgeGameplayMatrixResume";
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SERVER_TIMEOUT = Duration.ofMinutes(5);

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "true")
    void packagedGameplayAcrossNeoForgeMatrixWithExplicitEulaBlock() throws Exception {
        Path repository = Path.of(".").toAbsolutePath().normalize();
        Path fixtureRoot = repository.resolve(".tmp/stage14-track-audit/projects");
        Path evidenceRoot = repository.resolve("evidence/stage14/2026-09-08/neoforge-gameplay-matrix");
        Path hostRoot = repository.resolve("build/stage14c-neoforge-gameplay-matrix");
        Path temp = repository.resolve("t/stage14c-neoforge-gameplay-matrix");
        Files.createDirectories(evidenceRoot);
        Files.createDirectories(temp);

        boolean resume = Boolean.parseBoolean(System.getProperty(RESUME_PROPERTY, "false"));
        List<CellResult> results = new ArrayList<>();
        for (NeoTrack track : tracks(repository)) {
            for (String variant : List.of("native", "copperbench")) {
                CellResult existing = resume ? readSatisfiedCell(evidenceRoot, track.id(), variant) : null;
                if (existing != null) {
                    results.add(existing);
                } else if (track.eulaBeforeModLoad()) {
                    results.add(runEulaBlockedCell(repository, fixtureRoot, evidenceRoot, temp, track, variant));
                } else {
                    results.add(runGameplayCell(repository, fixtureRoot, evidenceRoot, hostRoot, temp, track, variant));
                }
            }
        }

        JsonArray cells = new JsonArray();
        List<String> failures = new ArrayList<>();
        long behaviorPassed = 0;
        long authorizedBlocked = 0;
        for (CellResult result : results) {
            cells.add(result.evidence());
            if (!result.closureSatisfied()) failures.add(result.track() + "/" + result.variant() + ": " + result.failure());
            if ("passed".equals(status(result.evidence(), "behaviorVerifiedStatus"))) behaviorPassed++;
            if ("blocked".equals(status(result.evidence(), "behaviorVerifiedStatus"))) authorizedBlocked++;
            if ("not_run".equals(status(result.evidence(), "behaviorVerifiedStatus"))
                    && result.evidence().has("authorizationBlock")) authorizedBlocked++;
        }

        JsonObject matrix = new JsonObject();
        matrix.addProperty("schemaVersion", "1.0");
        matrix.addProperty("kind", "stage14c-neoforge-packaged-gameplay-matrix");
        matrix.addProperty("statusVocabulary", "passed|failed|blocked|not_run|not_applicable");
        matrix.addProperty("eulaAcceptedByHarness", false);
        matrix.addProperty("cellCount", results.size());
        matrix.addProperty("behaviorPassedCount", behaviorPassed);
        matrix.addProperty("authorizationBlockedCount", authorizedBlocked);
        matrix.addProperty("allBehaviorPassed", behaviorPassed == results.size());
        matrix.addProperty("closureSatisfiedCount", results.stream().filter(CellResult::closureSatisfied).count());
        matrix.add("cells", cells);
        matrix.addProperty("closureSatisfied", failures.isEmpty());
        matrix.addProperty("passed", failures.isEmpty());
        matrix.addProperty("completedAt", Instant.now().toString());
        Files.writeString(evidenceRoot.resolve("matrix.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(matrix),
                StandardCharsets.UTF_8);

        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    private static CellResult readSatisfiedCell(Path evidenceRoot, String track, String variant) {
        Path json = evidenceRoot.resolve(track).resolve(variant + ".json");
        if (!Files.isRegularFile(json)) return null;
        try {
            JsonObject evidence = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
            if (!evidence.has("closureSatisfied") || !evidence.get("closureSatisfied").getAsBoolean()) return null;
            return new CellResult(track, variant, true, "", evidence);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CellResult runEulaBlockedCell(Path repository, Path fixtureRoot, Path evidenceRoot, Path temp,
            NeoTrack track, String variant) throws Exception {
        Instant started = Instant.now();
        Path source = fixtureRoot.resolve(track.id()).resolve(variant);
        Path cellDir = evidenceRoot.resolve(track.id());
        Path json = cellDir.resolve(variant + ".json");
        Path log = cellDir.resolve(variant + ".log");
        Files.createDirectories(cellDir);
        ProcessRun build = null;
        String failure = "";
        boolean closureSatisfied = false;
        Path packaged = null;
        try {
            require(Files.isDirectory(source), "missing fixture source: " + source);
            build = runGradle(source, track.gradleHome(), track.javaHome(), temp, BUILD_TIMEOUT, List.of("jar"));
            require(!build.timedOut(), "jar timed out");
            require(build.exitCode() == 0, diagnosticTail(build.output()));
            packaged = findPackagedJar(source.resolve("build/libs"));
            require(packaged != null, "packaged Survey Pulse JAR missing");
            EulaOrdering ordering = verifyForge1201EulaOrdering(fixtureRoot);
            require(ordering.eulaCheckOffset() >= 0, "Forge 1.20.1 EULA check not found");
            require(ordering.modLoadOffset() >= 0, "Forge 1.20.1 ServerModLoader.load not found");
            require(ordering.eulaCheckOffset() < ordering.modLoadOffset(),
                    "Forge 1.20.1 no longer checks EULA before mod loading; blocked classification is stale");
            closureSatisfied = true;
            Files.writeString(log, "Stage 14C authorization block\n"
                    + "Forge/NeoForge 1.20.1 checks eula.hasAgreedToEULA() before ServerModLoader.load().\n"
                    + "eulaCheckOffset=" + ordering.eulaCheckOffset() + "\n"
                    + "modLoadOffset=" + ordering.modLoadOffset() + "\n"
                    + "source=" + repository.relativize(ordering.source()).toString().replace('\\', '/') + "\n"
                    + "Harness did not write eula=true and did not start a server that requires agreement.\n",
                    StandardCharsets.UTF_8);
        } catch (Throwable problem) {
            failure = problem.getClass().getSimpleName() + ": " + String.valueOf(problem.getMessage());
            Files.writeString(log, failure + System.lineSeparator(), StandardCharsets.UTF_8);
        }

        JsonObject evidence = baseEvidence(track, variant, source, started, log, repository);
        evidence.addProperty("preparedStatus", Files.isDirectory(source) ? "passed" : "failed");
        evidence.addProperty("compiledStatus", build != null && build.exitCode() == 0 && !build.timedOut() ? "passed" : "failed");
        evidence.addProperty("initializerExecutedStatus", "blocked");
        evidence.addProperty("eulaBoundaryReachedStatus", "passed");
        evidence.addProperty("serverReadyStatus", "blocked");
        evidence.addProperty("packagedJarLoadedStatus", "blocked");
        evidence.addProperty("behaviorVerifiedStatus", "not_run");
        evidence.addProperty("authorizationBlock", "minecraft_eula_required_before_mod_load");
        evidence.addProperty("blockReason",
                "Forge 47 / Minecraft 1.20.1 checks EULA before ServerModLoader.load(); Stage14 harness must not accept EULA for the user");
        evidence.addProperty("eulaAcceptedByHarness", false);
        evidence.addProperty("serverProcessStarted", false);
        evidence.addProperty("physicalGameplayNotRun", true);
        evidence.addProperty("closureSatisfied", closureSatisfied);
        evidence.addProperty("failure", failure);
        if (packaged != null && Files.isRegularFile(packaged)) evidence.addProperty("packagedJarSha256", sha256(packaged));
        evidence.addProperty("completedAt", Instant.now().toString());
        Files.writeString(json, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(evidence),
                StandardCharsets.UTF_8);
        return new CellResult(track.id(), variant, closureSatisfied, failure, evidence);
    }

    private static CellResult runGameplayCell(Path repository, Path fixtureRoot, Path evidenceRoot, Path hostRoot,
            Path temp, NeoTrack track, String variant) throws Exception {
        Instant started = Instant.now();
        Path source = fixtureRoot.resolve(track.id()).resolve(variant);
        Path cellDir = evidenceRoot.resolve(track.id());
        Path json = cellDir.resolve(variant + ".json");
        Path log = cellDir.resolve(variant + ".log");
        Path host = hostRoot.resolve(track.id()).resolve(variant);
        Files.createDirectories(cellDir);
        ProcessRun build = null;
        ProcessRun runtime = null;
        Path deployedJar = null;
        String output = "";
        String failure = "";
        boolean closureSatisfied = false;
        try {
            require(Files.isDirectory(source), "missing fixture source: " + source);
            build = runGradle(source, track.gradleHome(), track.javaHome(), temp, BUILD_TIMEOUT, List.of("jar"));
            require(!build.timedOut(), "jar timed out");
            require(build.exitCode() == 0, diagnosticTail(build.output()));
            Path packaged = findPackagedJar(source.resolve("build/libs"));
            require(packaged != null, "packaged Survey Pulse JAR missing");

            prepareGameplayHost(repository, source, host, track);
            Files.createDirectories(host.resolve("run/mods"));
            deployedJar = host.resolve("run/mods/survey_pulse-1.0.jar");
            Files.copy(packaged, deployedJar);
            require(!eulaAccepted(host.resolve("run")), "harness must not pre-accept EULA");
            require(!Files.exists(host.resolve("src/main/java/dev/example/surveypulse")),
                    "packaged host contains Survey Pulse source");

            runtime = runGradle(host, track.gradleHome(), track.javaHome(), temp, SERVER_TIMEOUT,
                    List.of("runGameTestServer", "-x", "downloadAssets"));
            output = runtime.output();
            require(!runtime.timedOut(), diagnosticTail(output));
            require(runtime.exitCode() == 0, diagnosticTail(output));
            require(output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()), diagnosticTail(output));
            require(output.contains("Started game test server"), diagnosticTail(output));
            require(output.contains("STAGE14C_GAMEPLAY_VERIFIED track=" + track.id()), diagnosticTail(output));
            require(requiredTestsPassed(output), diagnosticTail(output));
            require(!fatalSeen(output), diagnosticTail(output));
            require(!eulaAccepted(host.resolve("run")), "GameTest run accepted EULA");
            closureSatisfied = true;
        } catch (Throwable problem) {
            failure = problem.getClass().getSimpleName() + ": " + String.valueOf(problem.getMessage());
        } finally {
            if (runtime != null) output = runtime.output();
            Files.writeString(log, output, StandardCharsets.UTF_8);
        }

        JsonObject evidence = baseEvidence(track, variant, source, started, log, repository);
        boolean compiled = build != null && build.exitCode() == 0 && !build.timedOut();
        boolean initializer = output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id());
        boolean ready = output.contains("Started game test server");
        boolean behavior = output.contains("STAGE14C_GAMEPLAY_VERIFIED track=" + track.id());
        evidence.addProperty("preparedStatus", Files.isDirectory(source) ? "passed" : "failed");
        evidence.addProperty("compiledStatus", compiled ? "passed" : "failed");
        evidence.addProperty("initializerExecutedStatus", initializer ? "passed" : "failed");
        evidence.addProperty("eulaBoundaryReachedStatus", "not_applicable");
        evidence.addProperty("serverReadyStatus", ready ? "passed" : "failed");
        evidence.addProperty("packagedJarLoadedStatus", initializer && deployedJar != null && Files.isRegularFile(deployedJar) ? "passed" : "failed");
        evidence.addProperty("behaviorVerifiedStatus", behavior ? "passed" : "failed");
        evidence.addProperty("serverReadyMode", "neoforge_gametest_server");
        evidence.addProperty("eulaAcceptedByHarness", false);
        evidence.addProperty("rightClickVerified", behavior && output.contains("rightClick=true"));
        evidence.addProperty("normalRadius", 5);
        evidence.addProperty("sneakRadius", 8);
        evidence.addProperty("cooldownTicks", 60);
        evidence.addProperty("spectatorRejected", behavior && output.contains("spectator=true"));
        evidence.addProperty("multiplayerIsolation", behavior && output.contains("multiplayerIsolation=true"));
        evidence.addProperty("hostContainsSurveySources", Files.exists(host.resolve("src/main/java/dev/example/surveypulse")));
        evidence.addProperty("runtimeExitCode", runtime == null ? -1 : runtime.exitCode());
        evidence.addProperty("runtimeTimedOut", runtime == null || runtime.timedOut());
        evidence.addProperty("fatalSeen", fatalSeen(output));
        evidence.addProperty("closureSatisfied", closureSatisfied);
        evidence.addProperty("failure", failure);
        if (deployedJar != null && Files.isRegularFile(deployedJar)) evidence.addProperty("packagedJarSha256", sha256(deployedJar));
        evidence.addProperty("completedAt", Instant.now().toString());
        Files.writeString(json, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(evidence),
                StandardCharsets.UTF_8);
        return new CellResult(track.id(), variant, closureSatisfied, failure, evidence);
    }

    private static JsonObject baseEvidence(NeoTrack track, String variant, Path source, Instant started, Path log,
            Path repository) {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("track", track.id());
        evidence.addProperty("variant", variant);
        evidence.addProperty("minecraftVersion", track.minecraftVersion());
        evidence.addProperty("neoForgeVersion", track.neoForgeVersion());
        evidence.addProperty("javaHome", track.javaHome().toString());
        evidence.addProperty("gradleHome", track.gradleHome().toString());
        evidence.addProperty("sourceFixture", repository.relativize(source).toString().replace('\\', '/'));
        evidence.addProperty("logFile", repository.relativize(log).toString().replace('\\', '/'));
        evidence.addProperty("durationSeconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
        return evidence;
    }

    private static void prepareGameplayHost(Path repository, Path source, Path host, NeoTrack track) throws IOException {
        deleteRecursively(host);
        Files.createDirectories(host.resolve("gradle/wrapper"));
        Files.createDirectories(host.resolve("src/main/java/dev/copperbench/stage14c"));
        Files.createDirectories(host.resolve("src/main/resources/META-INF"));
        Files.copy(source.resolve("gradlew.bat"), host.resolve("gradlew.bat"));
        Files.copy(source.resolve("gradlew"), host.resolve("gradlew"));
        Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.jar"), host.resolve("gradle/wrapper/gradle-wrapper.jar"));
        Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.properties"), host.resolve("gradle/wrapper/gradle-wrapper.properties"));
        Files.writeString(host.resolve("settings.gradle"), """
                pluginManagement { repositories { maven { url='https://maven.neoforged.net/releases' }; mavenCentral(); gradlePluginPortal() } }
                rootProject.name='stage14c-neoforge-gameplay-host'
                """, StandardCharsets.UTF_8);
        Files.writeString(host.resolve("gradle.properties"), """
                org.gradle.jvmargs=-Xmx1G -Dfile.encoding=UTF-8
                org.gradle.configuration-cache=false
                """, StandardCharsets.UTF_8);
        Files.writeString(host.resolve("build.gradle"), """
                plugins { id 'net.neoforged.moddev' version '2.0.141' }
                java.toolchain.languageVersion = JavaLanguageVersion.of(%d)
                neoForge {
                    version = '%s'
                    runs { gameTestServer { type = 'gameTestServer' } }
                    mods { stage14c_host { sourceSet sourceSets.main } }
                }
                """.formatted(track.javaRelease(), track.neoForgeVersion()), StandardCharsets.UTF_8);

        Path resourceRoot = repository.resolve("src/test/resources/stage14c/neoforge");
        Files.copy(resourceRoot.resolve("neoforge.mods.toml"), host.resolve("src/main/resources/META-INF/neoforge.mods.toml"));
        if (track.minecraftVersion().equals("1.21.1")) {
            Files.copy(resourceRoot.resolve("1.21.1/Stage14CHostMod.java"),
                    host.resolve("src/main/java/dev/copperbench/stage14c/Stage14CHostMod.java"));
            Files.copy(resourceRoot.resolve("1.21.1/HostGameTest.java"),
                    host.resolve("src/main/java/dev/copperbench/stage14c/HostGameTest.java"));
        } else {
            String sourceText = Files.readString(resourceRoot.resolve("26.x/Stage14CHostMod.java"), StandardCharsets.UTF_8)
                    .replace("neoforge-26.1.2", track.id());
            Files.writeString(host.resolve("src/main/java/dev/copperbench/stage14c/Stage14CHostMod.java"),
                    sourceText, StandardCharsets.UTF_8);
        }

        Path assetProperties = source.resolve("build/moddev/minecraft_assets.properties");
        require(Files.isRegularFile(assetProperties), "same-version minecraft_assets.properties missing: " + assetProperties);
        Files.createDirectories(host.resolve("build/moddev"));
        Files.copy(assetProperties, host.resolve("build/moddev/minecraft_assets.properties"));
    }

    private static EulaOrdering verifyForge1201EulaOrdering(Path fixtureRoot) throws IOException {
        Path build = fixtureRoot.resolve("neoforge-1.20.1/native/build");
        try (Stream<Path> paths = Files.walk(build)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().replace('\\', '/').endsWith("net/minecraft/server/Main.java")).toList()) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                int eula = text.indexOf("if (!eula.hasAgreedToEULA())");
                int modLoad = text.indexOf("ServerModLoader.load()");
                if (eula >= 0 && modLoad >= 0) return new EulaOrdering(path, eula, modLoad);
            }
        }
        throw new IllegalStateException("Could not locate prepared Forge 1.20.1 Main.java for EULA ordering proof");
    }

    private static ProcessRun runGradle(Path project, Path gradleHome, Path javaHome, Path temp, Duration timeout,
            List<String> tasks) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe"); command.add("/c"); command.add("gradlew.bat"); command.add("--no-daemon"); command.add("--console=plain"); command.addAll(tasks);
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", javaHome.toString());
        builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
        builder.environment().put("TEMP", temp.toString()); builder.environment().put("TMP", temp.toString());
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try { return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
        });
        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        return new ProcessRun(completed ? process.exitValue() : -1, !completed, output.get(30, TimeUnit.SECONDS));
    }

    private static List<NeoTrack> tracks(Path repository) {
        return List.of(
                new NeoTrack("neoforge-1.20.1", "1.20.1", "47.1.106", 17,
                        repository.resolve("jdk/jdk21_win_64"), repository.resolve("build/stage8-workspace-generator-gradle/neoforge-1_20_1"), true),
                new NeoTrack("neoforge-1.21.1", "1.21.1", "21.1.232", 21,
                        repository.resolve("jdk/jdk21_win_64"), repository.resolve("build/stage8-workspace-generator-gradle/neoforge-1_21_1"), false),
                new NeoTrack("neoforge-26.1.2", "26.1.2", "26.1.2.95", 25,
                        repository.resolve("jdk/jbr25_win_64"), repository.resolve("build/stage8-workspace-generator-gradle/neoforge-26_1_2"), false),
                new NeoTrack("neoforge-26.2", "26.2", "26.2.0.63", 25,
                        repository.resolve("jdk/jbr25_win_64"), repository.resolve("build/stage8-workspace-generator-gradle/neoforge-26_2"), false));
    }

    private static Path findPackagedJar(Path libs) throws IOException {
        if (!Files.isDirectory(libs)) return null;
        try (Stream<Path> entries = Files.list(libs)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().contains("sources"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified())).orElse(null);
        }
    }

    private static boolean eulaAccepted(Path run) throws IOException {
        Path eula = run.resolve("eula.txt");
        return Files.isRegularFile(eula) && Files.readString(eula).lines().map(String::trim)
                .anyMatch(line -> line.equalsIgnoreCase("eula=true"));
    }

    private static boolean requiredTestsPassed(String output) {
        return output != null && output.matches("(?s).*All [1-9][0-9]* required tests passed :\\).*?");
    }

    private static boolean fatalSeen(String output) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return lower.contains("crash report") || lower.contains("failed to start the minecraft server")
                || lower.contains("exception in server tick loop");
    }

    private static String status(JsonObject evidence, String key) {
        return evidence.has(key) ? evidence.get(key).getAsString() : "";
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static String diagnosticTail(String output) {
        if (output == null) return "";
        return output.length() <= 8000 ? output : output.substring(output.length() - 8000);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record NeoTrack(String id, String minecraftVersion, String neoForgeVersion, int javaRelease,
            Path javaHome, Path gradleHome, boolean eulaBeforeModLoad) {}
    private record ProcessRun(int exitCode, boolean timedOut, String output) {}
    private record CellResult(String track, String variant, boolean closureSatisfied, String failure, JsonObject evidence) {}
    private record EulaOrdering(Path source, int eulaCheckOffset, int modLoadOffset) {}
}

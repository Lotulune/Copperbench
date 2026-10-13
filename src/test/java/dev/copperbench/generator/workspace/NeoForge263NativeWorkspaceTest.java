package dev.copperbench.generator.workspace;

import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.tracks.VersionTrackCatalog;
import net.mcreator.generator.Generator;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class NeoForge263NativeWorkspaceTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void nativePluginUsesTheQualifiedNeoForgeProfileAndOnlyScopedElements() {
        var catalog = VersionTrackCatalog.official();
        var profile = (NeoForge1211Generator.Profile) catalog.findRegistration("neoforge-26.3").orElseThrow().profile();
        assertEquals("26.3.0.76", profile.neoForgeVersion());
        assertEquals("2.0.148", profile.moddevVersion());
        assertEquals("gradle-9.6.0-bin.zip", profile.fabricProfile().gradleWrapperZip());
        assertFalse(catalog.decision("neoforge-26.3").generatable());
        var configuration = Generator.GENERATOR_CACHE.get("neoforge-26.3");
        assertNotNull(configuration); assertFalse(configuration.requiresMinecraftSourceIndex());
        var available = configuration.getGeneratorStats().getModElementTypeCoverageInfo().entrySet().stream()
                .filter(entry -> entry.getValue() != net.mcreator.generator.GeneratorStats.CoverageStatus.NONE)
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().getRegistryName(), java.util.Map.Entry::getValue));
        assertEquals(java.util.Map.of("item", net.mcreator.generator.GeneratorStats.CoverageStatus.PARTIAL,
                "block", net.mcreator.generator.GeneratorStats.CoverageStatus.PARTIAL,
                "recipe", net.mcreator.generator.GeneratorStats.CoverageStatus.PARTIAL,
                "function", net.mcreator.generator.GeneratorStats.CoverageStatus.FULL,
                "code", net.mcreator.generator.GeneratorStats.CoverageStatus.FULL), available);
        var environment = WorkspaceExecutionEnvironment.describe(temporary, Path.of("."), "neoforge-26.3");
        assertEquals("26.3.0.76", environment.get("loaderVersion").getAsString());
        assertEquals("gradle-9.6.0-bin.zip", environment.getAsJsonObject("gradle").get("distribution").getAsString());
    }

    @Test void templatesRenderPinnedCommonOnlySourcesAndKeepLegacyCreationClosed() throws Exception {
        var settings = new WorkspaceSettings("native_probe"); settings.setModName("Native Probe");
        settings.setVersion("1.2.3"); settings.setCurrentGenerator("neoforge-26.3"); settings.setModElementsPackage("dev.nativeprobe");
        try (Workspace workspace = Workspace.createWorkspace(temporary.resolve("native_probe.mcreator").toFile(), settings)) {
            WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
            assertTrue(workspace.getGenerator().generateBase());
            WorkspaceGeneratorSetup.completeSetup(workspace.getGenerator());
            workspace.getGenerator().loadOrCreateGradleCaches();
            assertNull(workspace.getGenerator().getGradleCache());
            String build = Files.readString(temporary.resolve("build.gradle"));
            assertTrue(build.contains("2.0.148")); assertTrue(build.contains("26.3.0.76")); assertTrue(build.contains("--nogui"));
            assertTrue(build.contains("javaExecutable = javaToolchains.launcherFor(java.toolchain)"));
            assertTrue(build.contains("lockFile = file('gradle/linux.lockfile')"));
            String linuxLock = Files.readString(temporary.resolve("gradle/linux.lockfile"));
            assertTrue(linuxLock.contains("io.netty:netty-transport-native-epoll:4.2.16.Final="));
            assertFalse(Files.readString(temporary.resolve("gradle.lockfile"))
                    .contains("io.netty:netty-transport-native-epoll:4.2.16.Final="));
            assertTrue(Files.readString(temporary.resolve("gradle/verification-metadata.xml"))
                    .contains("netty-transport-native-epoll-4.2.16.Final-linux-x86_64.jar"));
            assertFalse(Files.readString(temporary.resolve("gradlew")).contains("\r"));
            String metadata = Files.readString(temporary.resolve("src/main/resources/META-INF/neoforge.mods.toml"));
            assertTrue(metadata.contains("versionRange=\"[26.3]\""));
            try (var files = Files.walk(temporary.resolve("src/main/java"))) {
                var sources = files.filter(Files::isRegularFile).toList(); assertEquals(1, sources.size());
                assertFalse(Files.readString(sources.getFirst()).contains("net.minecraft.client"));
            }
        }
        assertTrue(new WorkspaceCreationService().validateCreation("neoforge-26.3", "Native Probe", "native_probe", "dev.nativeprobe",
                temporary.resolve("legacy").toString()).contains("UNSUPPORTED_GENERATOR"));
        assertFalse(Files.exists(temporary.resolve("legacy")));
    }

    @Test void standaloneProjectionUsesTheSameJavaForCompilationAndAssetDownload() throws Exception {
        var generatorId = new com.google.gson.JsonObject(); generatorId.addProperty("id", "neoforge-26.3");
        var state = new dev.copperbench.core.workspace.WorkspaceState(UUID.randomUUID(), "Asset Probe", "mod", 0,
                false, generatorId, new com.google.gson.JsonObject(), List.of());
        var generator = new NeoForge1211Generator(Path.of("."), NeoForge1211Generator.Profile.NEOFORGE_263);
        generator.generate(temporary.resolve("projection"), state);
        String build = Files.readString(temporary.resolve("projection/build.gradle"));
        assertTrue(build.contains("JavaLanguageVersion.of(25)"));
        assertTrue(build.contains("javaExecutable = javaToolchains.launcherFor(java.toolchain)"));
    }

    @Test @EnabledIfSystemProperty(named = "copperbench.p263.neoforgeNativeBuild", matches = "true")
    void qualifiedBackendCreatesAndBuildsReal263Jar() throws Exception {
        Path evidence = Path.of("output/p263-08/backend-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(evidence);
        Path target = evidence.resolve("workspace");
        var created = new WorkspaceCreationService().create("neoforge-26.3", "Native Probe", "native_probe", "dev.nativeprobe", target.toString(), "1.2.3", true);
        Files.writeString(evidence.resolve("creation.json"), dev.copperbench.core.contract.UiCore.wireGson().toJson(created));
        assertTrue(created.complete(), created.toString());
        var runner = Fabric1211ProcessRunner.system("COPPERBENCH_P263_NEOFORGE_INITIALIZED", BundledJdkLocator.locate(Path.of("."), 25));
        StringBuilder log = new StringBuilder();
        var built = runner.run(target, List.of("build", "--console=plain"), Duration.ofMinutes(15), line -> log.append(line).append('\n'));
        Files.writeString(evidence.resolve("build.log"), log);
        assertEquals(0, built.exitCode(), log.toString());
        try (JarFile jar = new JarFile(target.resolve("build/libs/native_probe-1.2.3.jar").toFile())) {
            var metadata = jar.getEntry("META-INF/neoforge.mods.toml"); assertNotNull(metadata);
            assertTrue(new String(jar.getInputStream(metadata).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("versionRange=\"[26.3]\""));
            assertNull(jar.getEntry("fabric.mod.json"));
        }
    }

    @Test @EnabledIfSystemProperty(named = "copperbench.p263.neoforgeNativeServer", matches = "true")
    void dedicatedServerRequiresEulaAndBothInitializationAndStableReadiness() throws Exception {
        Path evidence = Path.of("output/p263-08/server-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(evidence);
        Path target = evidence.resolve("workspace");
        var created = new WorkspaceCreationService().create("neoforge-26.3", "Native Probe", "native_probe", "dev.nativeprobe", target.toString(), "1.2.3", true);
        Files.writeString(evidence.resolve("creation.json"), dev.copperbench.core.contract.UiCore.wireGson().toJson(created));
        assertTrue(created.complete(), created.toString());
        new NeoForge1211Generator(Path.of("."), NeoForge1211Generator.Profile.NEOFORGE_263).prepareServerRun(target);
        Path run = target.resolve("run"); Files.createDirectories(run);
        var runner = Fabric1211ProcessRunner.system("COPPERBENCH_P263_NEOFORGE_INITIALIZED", BundledJdkLocator.locate(Path.of("."), 25));
        var gson = dev.copperbench.core.contract.UiCore.wireGson();
        StringBuilder deniedLog = new StringBuilder();
        var denied = runner.run(target, List.of("runServer", "--console=plain"), Duration.ofMinutes(10),
                line -> deniedLog.append(line).append('\n'));
        Files.writeString(evidence.resolve("without-eula.log"), deniedLog);
        Files.writeString(evidence.resolve("without-eula.json"), gson.toJson(denied));
        assertNotEquals(0, denied.exitCode());
        assertFalse(denied.readinessMarkerSeen());
        assertTrue(deniedLog.toString().toLowerCase(java.util.Locale.ROOT).contains("eula"), deniedLog.toString());
        assertFalse(deniedLog.toString().contains("COPPERBENCH_P263_NEOFORGE_INITIALIZED"));
        // Invoke this opt-in test only after human EULA approval for this temporary server.
        Files.writeString(run.resolve("eula.txt"), "eula=true\n");
        StringBuilder readyLog = new StringBuilder();
        var ready = runner.run(target, List.of("runServer", "--console=plain"), Duration.ofMinutes(10),
                line -> readyLog.append(line).append('\n'));
        Files.writeString(evidence.resolve("with-eula.log"), readyLog);
        Files.writeString(evidence.resolve("with-eula.json"), gson.toJson(ready));
        assertEquals(0, ready.exitCode(), readyLog.toString());
        assertTrue(ready.readinessMarkerSeen(), readyLog.toString());
        assertTrue(readyLog.toString().contains("COPPERBENCH_P263_NEOFORGE_INITIALIZED"));
        assertTrue(readyLog.toString().contains("For help, type"));
        assertTrue(readyLog.toString().contains("Not loading early display in headless mode"));
        assertTrue(readyLog.toString().contains("Starting Minecraft server on 127.0.0.1:0"));
        assertFalse(readyLog.toString().contains("net.minecraft.client"));
        assertFalse(readyLog.toString().contains("DedicatedLanServerPinger"));
        var timeout = runner.run(target, List.of("help", "--console=plain"), Duration.ofMillis(1), line -> {});
        Files.writeString(evidence.resolve("timeout.json"), gson.toJson(timeout));
        assertEquals(124, timeout.exitCode());
    }

    @Test void selectedPreviewRejectsMissingOrWrongJavaBeforeCreatingDestination() throws Exception {
        Path target = temporary.resolve("destination");
        String original = System.getProperty("java.home");
        try {
            Path java21 = temporary.resolve("java21"); Files.createDirectories(java21.resolve("bin"));
            Files.writeString(java21.resolve("bin/java"), "preflight fixture; never executed");
            Files.writeString(java21.resolve("release"), "JAVA_VERSION=\"21.0.1\"\n");
            System.setProperty("java.home", java21.toString());
            var isolated = new WorkspaceCreationService(VersionTrackCatalog.official(), temporary.resolve("no-distribution"));
            assertTrue(isolated.validateCreation("neoforge-26.3", "Native Probe", "native_probe", "dev.nativeprobe",
                    target.toString(), true).contains("PREVIEW_WORKSPACE_JAVA_UNSUPPORTED"));
            assertFalse(Files.exists(target));
            Files.delete(java21.resolve("bin/java"));
            assertTrue(isolated.validateCreation("neoforge-26.3", "Native Probe", "native_probe", "dev.nativeprobe",
                    target.toString(), true).contains("BUNDLED_JDK_MISSING"));
            assertFalse(Files.exists(target));
        } finally { System.setProperty("java.home", original); }
    }
}

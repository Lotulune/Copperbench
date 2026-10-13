package dev.copperbench.generator.workspace;

import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
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

class Fabric263NativeWorkspaceTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void nativePluginHasAnExactProfileAndOnlyScopedElementTemplates() throws Exception {
        var catalog = VersionTrackCatalog.official();
        var profile = (Fabric1211Generator.Profile) catalog.findRegistration("fabric-26.3").orElseThrow().profile();
        assertEquals("0.19.5", profile.loaderVersion()); assertEquals("1.17.21", profile.loomVersion());
        assertEquals("0.162.0+26.3", profile.fabricApiVersion()); assertEquals("gradle-9.6.0-bin.zip", profile.gradleWrapperZip());
        assertFalse(catalog.decision("fabric-26.3").generatable());
        var configuration = Generator.GENERATOR_CACHE.get("fabric-26.3");
        assertNotNull(configuration); assertFalse(configuration.requiresMinecraftSourceIndex());
        assertTrue(Generator.GENERATOR_CACHE.get("fabric-26.2").requiresMinecraftSourceIndex());
        var available = configuration.getGeneratorStats().getModElementTypeCoverageInfo().entrySet().stream()
                .filter(entry -> entry.getValue() != net.mcreator.generator.GeneratorStats.CoverageStatus.NONE)
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().getRegistryName(), java.util.Map.Entry::getValue));
        assertEquals(java.util.Map.of("item", net.mcreator.generator.GeneratorStats.CoverageStatus.PARTIAL,
                "block", net.mcreator.generator.GeneratorStats.CoverageStatus.PARTIAL,
                "recipe", net.mcreator.generator.GeneratorStats.CoverageStatus.PARTIAL,
                "function", net.mcreator.generator.GeneratorStats.CoverageStatus.FULL,
                "code", net.mcreator.generator.GeneratorStats.CoverageStatus.FULL), available);
        var environment = WorkspaceExecutionEnvironment.describe(temporary, Path.of("."), "fabric-26.3");
        assertEquals("26.3", environment.get("minecraftVersion").getAsString());
        assertEquals("gradle-9.6.0-bin.zip", environment.getAsJsonObject("gradle").get("distribution").getAsString());
    }

    @Test @EnabledIfSystemProperty(named = "copperbench.p263.fabricNativeServer", matches = "true")
    void dedicatedServerRequiresEulaAndBothInitializationAndStableReadiness() throws Exception {
        Path fixture = Path.of(System.getProperty("copperbench.p263.backendFixture")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixture.resolve("native_probe.mcreator")));
        Path evidence = Path.of("output/p263-07/server-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(evidence);
        Path target = evidence.resolve("workspace");
        dev.copperbench.generator.WorkspaceExecutionSnapshot.capture(fixture, target, UUID.randomUUID(), 0,
                java.time.Clock.systemUTC(), () -> false);
        Path run = target.resolve("run"); Files.createDirectories(run);
        Files.writeString(run.resolve("server.properties"), "server-ip=127.0.0.1\nserver-port=0\nlevel-name=p263-acceptance\nview-distance=2\nsimulation-distance=2\nmax-players=1\n");
        var runner = Fabric1211ProcessRunner.system("COPPERBENCH_P263_FABRIC_INITIALIZED", BundledJdkLocator.locate(Path.of("."), 25));
        var gson = dev.copperbench.core.contract.UiCore.wireGson();
        StringBuilder deniedLog = new StringBuilder();
        var denied = runner.run(target, List.of("runServer", "--console=plain"), Duration.ofMinutes(10),
                line -> deniedLog.append(line).append('\n'));
        Files.writeString(evidence.resolve("without-eula.log"), deniedLog);
        Files.writeString(evidence.resolve("without-eula.json"), gson.toJson(denied));
        assertFalse(denied.readinessMarkerSeen());
        assertTrue(deniedLog.toString().toLowerCase(java.util.Locale.ROOT).contains("eula"), deniedLog.toString());
        // This opt-in test may be invoked only after the user accepts the EULA for this temporary server.
        Files.writeString(run.resolve("eula.txt"), "eula=true\n");
        StringBuilder readyLog = new StringBuilder();
        var ready = runner.run(target, List.of("runServer", "--console=plain"), Duration.ofMinutes(10),
                line -> readyLog.append(line).append('\n'));
        Files.writeString(evidence.resolve("with-eula.log"), readyLog);
        Files.writeString(evidence.resolve("with-eula.json"), gson.toJson(ready));
        assertEquals(0, ready.exitCode(), readyLog.toString());
        assertTrue(ready.readinessMarkerSeen(), readyLog.toString());
        assertTrue(readyLog.toString().contains("COPPERBENCH_P263_FABRIC_INITIALIZED"));
        assertTrue(readyLog.toString().contains("For help, type"));
        assertFalse(readyLog.toString().contains("net.minecraft.client"));
        var timeout = runner.run(target, List.of("help", "--console=plain"), Duration.ofMillis(1), line -> {});
        Files.writeString(evidence.resolve("timeout.json"), gson.toJson(timeout));
        assertEquals(124, timeout.exitCode());
    }

    @Test void templatesRenderPinnedBuildAndCommonOnlyEntryWithoutOldVersionImports() throws Exception {
        var settings = new WorkspaceSettings("native_probe"); settings.setModName("Native Probe");
        settings.setVersion("1.2.3"); settings.setCurrentGenerator("fabric-26.3"); settings.setModElementsPackage("dev.nativeprobe");
        try (Workspace workspace = Workspace.createWorkspace(temporary.resolve("native_probe.mcreator").toFile(), settings)) {
            WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
            assertTrue(workspace.getGenerator().generateBase());
            WorkspaceGeneratorSetup.completeSetup(workspace.getGenerator());
            workspace.getGenerator().loadOrCreateGradleCaches();
            workspace.getGenerator().reloadGradleCaches();
            assertNull(workspace.getGenerator().getGradleCache());
            assertTrue(Files.readString(temporary.resolve("build.gradle")).contains("version = '1.2.3'"));
            assertTrue(Files.readString(temporary.resolve("build.gradle"))
                    .contains("apply from: 'gradle/normalize-local-minecraft.gradle'"));
            assertTrue(Files.isRegularFile(temporary.resolve("gradle/normalize-local-minecraft.gradle")));
            assertFalse(Files.readString(temporary.resolve("gradlew")).contains("\r"));
            String metadata = Files.readString(temporary.resolve("src/main/resources/fabric.mod.json"));
            var entrypoints = JsonParser.parseString(metadata).getAsJsonObject().getAsJsonObject("entrypoints");
            assertEquals(1, entrypoints.size()); assertTrue(entrypoints.has("main"));
            assertEquals("26.3", JsonParser.parseString(metadata).getAsJsonObject().getAsJsonObject("depends").get("minecraft").getAsString());
            try (var paths = Files.walk(temporary.resolve("src/main/java"))) {
                var sources = paths.filter(Files::isRegularFile).toList(); assertEquals(1, sources.size());
                assertFalse(Files.readString(sources.getFirst()).contains("net.minecraft.client"));
            }
        }
    }

    @Test void legacyCreationAndWrongJavaAreRejectedBeforeCreatingDestination() throws Exception {
        var service = new WorkspaceCreationService(); Path target = temporary.resolve("destination");
        assertTrue(service.validateCreation("fabric-26.3", "Native Probe", "native_probe", "dev.nativeprobe",
                target.toString()).contains("UNSUPPORTED_GENERATOR"));
        String original = System.getProperty("java.home");
        try {
            Path java21 = temporary.resolve("java21");
            Files.createDirectories(java21.resolve("bin"));
            Files.writeString(java21.resolve("bin/java"), "preflight fixture; never executed");
            Files.writeString(java21.resolve("release"), "JAVA_VERSION=\"21.0.1\"\n");
            System.setProperty("java.home", java21.toString());
            var isolated = new WorkspaceCreationService(VersionTrackCatalog.official(), temporary.resolve("no-distribution"));
            assertTrue(isolated.validateCreation("fabric-26.3", "Native Probe", "native_probe", "dev.nativeprobe",
                    target.toString(), true).contains("PREVIEW_WORKSPACE_JAVA_UNSUPPORTED"));
            assertFalse(Files.exists(target));
            Files.delete(java21.resolve("bin/java"));
            assertTrue(isolated.validateCreation("fabric-26.3", "Native Probe", "native_probe", "dev.nativeprobe",
                    target.toString(), true).contains("BUNDLED_JDK_MISSING"));
            assertFalse(Files.exists(target));
        } finally { System.setProperty("java.home", original); }
    }

    @Test @EnabledIfSystemProperty(named = "copperbench.p263.fabricNativeBuild", matches = "true")
    void qualifiedBackendCreatesAndBuildsReal263Jar() throws Exception {
        Path evidence = Path.of("output/p263-07/backend-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(evidence);
        Path target = evidence.resolve("workspace");
        var service = new WorkspaceCreationService();
        // Domain probe after an explicit test decision. Public Core operations remain closed until qualified.
        var created = service.create("fabric-26.3", "Native Probe", "native_probe", "dev.nativeprobe", target.toString(), "1.2.3", true);
        Files.writeString(evidence.resolve("creation.json"), dev.copperbench.core.contract.UiCore.wireGson().toJson(created));
        assertTrue(created.complete(), created.toString());
        var runner = Fabric1211ProcessRunner.system("COPPERBENCH_P263_FABRIC_INITIALIZED", BundledJdkLocator.locate(Path.of("."), 25));
        StringBuilder log = new StringBuilder();
        var built = runner.run(target, List.of("build", "--console=plain"), Duration.ofMinutes(15), line -> {
            synchronized (log) { log.append(line).append('\n'); }
        });
        Files.writeString(evidence.resolve("build.log"), log);
        assertEquals(0, built.exitCode(), log.toString());
        try (JarFile jar = new JarFile(target.resolve("build/libs/native_probe-1.2.3.jar").toFile())) {
            assertNotNull(jar.getEntry("fabric.mod.json"));
            assertEquals("26.3", JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("fabric.mod.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("depends").get("minecraft").getAsString());
        }
    }
}

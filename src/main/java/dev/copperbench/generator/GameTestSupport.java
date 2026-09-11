package dev.copperbench.generator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local test scaffolding and isolated hosts for the first-party Java generators. */
public final class GameTestSupport {
    public static final String CONFIG_FILE = "copperbench-tests.json";
    private static final String HOST_ID = "copperbench_test_host";
    private static final String TEST_CLASS = "copperbench.acceptance.AcceptanceTests";
    private GameTestSupport() {}

    public static final class TestSetupException extends IOException {
        private final String code;
        TestSetupException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
    public record Configuration(String mode, String task, String reportPath, String sourceDirectory,
            String resourceDirectory, List<String> entrypoints, int minimumTests) {}
    public record Host(Path root, Path report, Path jar, String jarSha256) {}

    public static Configuration configuration(Path root) throws IOException {
        Path path = safePath(root, CONFIG_FILE);
        if (!Files.exists(path)) return new Configuration("workspace", "runGameTest", "run/gametest-results.xml",
                "src/gametest/java", "src/gametest/resources", List.of(TEST_CLASS), 1);
        try {
            if (Files.size(path) > 64 * 1024) throw new IllegalArgumentException("Test configuration is too large");
            JsonObject value = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!"1.0".equals(value.get("schemaVersion").getAsString())) throw new IllegalArgumentException("Unsupported test schema");
            String mode = string(value, "mode", "packaged_jar");
            if (!List.of("workspace", "packaged_jar").contains(mode)) throw new IllegalArgumentException("Unknown test mode");
            String task = string(value, "task", "runGameTest");
            if (task.length() > 128 || !task.matches(":?[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z][A-Za-z0-9_-]*)*"))
                throw new IllegalArgumentException("Invalid Gradle test task");
            String report = string(value, "reportPath", "run/gametest-results.xml");
            Path reportRelative = root.toAbsolutePath().normalize().relativize(safePath(root, report));
            if (!List.of("build", "run", "runs").contains(reportRelative.getName(0).toString()))
                throw new IllegalArgumentException("Test reports must be under build, run or runs");
            String source = string(value, "sourceDirectory", "src/gametest/java");
            String resources = string(value, "resourceDirectory", "src/gametest/resources");
            for (String directory : List.of(source, resources)) {
                Path input = safePath(root, directory), implementation = root.toAbsolutePath().normalize().resolve("src/main");
                if (input.startsWith(implementation) || implementation.startsWith(input))
                    throw new IllegalArgumentException("Test sources must be separate from mod implementation sources");
            }
            List<String> entrypoints = value.has("entrypoints") ? value.getAsJsonArray("entrypoints").asList().stream()
                    .map(element -> element.getAsString()).toList() : List.of(TEST_CLASS);
            if (entrypoints.isEmpty() || entrypoints.size() > 100 || entrypoints.stream()
                    .anyMatch(name -> !name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")))
                throw new IllegalArgumentException("Provide qualified test entrypoint class names");
            int minimum = value.has("minimumTests") ? value.get("minimumTests").getAsInt() : 1;
            if (minimum < 1 || minimum > 10_000) throw new IllegalArgumentException("minimumTests must be between 1 and 10000");
            return new Configuration(mode, task, report, source, resources, entrypoints, minimum);
        } catch (RuntimeException exception) {
            throw new TestSetupException("GAMETEST_CONFIGURATION_INVALID", exception.getMessage());
        }
    }

    public static JsonObject describe(Path root, JsonObject environment) {
        JsonObject value = new JsonObject();
        value.addProperty("supported", environment.has("loader"));
        value.addProperty("configurationPath", root.resolve(CONFIG_FILE).toString());
        value.addProperty("prepareOperation", "prepare_game_tests");
        try {
            Configuration configuration = configuration(root);
            value.addProperty("state", Files.isRegularFile(root.resolve(CONFIG_FILE)) ? "configured" : "not_configured");
            value.addProperty("mode", configuration.mode()); value.addProperty("minimumTests", configuration.minimumTests());
        } catch (IOException exception) { value.addProperty("state", "invalid_configuration"); value.addProperty("detail", exception.getMessage()); }
        return value;
    }

    public static JsonObject prepare(Path root, JsonObject environment, String modId) throws IOException {
        requireSupported(environment);
        Path configPath = safePath(root, CONFIG_FILE);
        if (Files.exists(configPath)) { configuration(root); return describe(root, environment); }
        String loader = environment.get("loader").getAsString();
        String version = environment.get("minecraftVersion").getAsString();
        String source = starter(loader, version, modId);
        Path sourcePath = safePath(root, "src/gametest/java/copperbench/acceptance/AcceptanceTests.java");
        Path readme = safePath(root, "src/gametest/README.md");
        for (Path path : List.of(configPath, sourcePath, readme))
            if (Files.exists(path)) throw new TestSetupException("GAMETEST_SETUP_CONFLICT", "Existing test files will not be overwritten: " + path);
        JsonObject config = new JsonObject(); config.addProperty("schemaVersion", "1.0");
        config.addProperty("mode", "packaged_jar"); config.addProperty("sourceDirectory", "src/gametest/java");
        config.addProperty("resourceDirectory", "src/gametest/resources"); config.addProperty("minimumTests", 1);
        JsonArray entrypoints = new JsonArray(); entrypoints.add(TEST_CLASS); config.add("entrypoints", entrypoints);
        List<Path> created = new ArrayList<>();
        try {
            writeNew(sourcePath, source, created);
            writeNew(readme, "# Copperbench GameTests\n\nThe starter checks mod loading only. Add tests for your own behavior before treating this as gameplay acceptance.\n\n"
                    + "Copperbench builds a frozen copy of the real workspace, loads its packaged JAR in a separate host, and reports actual test counts and source/JAR hashes.\n"
                    + "Edit the test sources here and use run_gametest or headless run-gametest. The runtime writes no player world to your original workspace.\n", created);
            writeNew(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(config) + "\n", created);
        } catch (IOException exception) {
            for (Path path : created.reversed()) { WorkspaceExecutionSnapshot.rejectLinks(path); Files.deleteIfExists(path); }
            throw exception;
        }
        JsonObject result = describe(root, environment);
        result.addProperty("starterScope", "mod_loading_only");
        JsonArray paths = new JsonArray(); created.forEach(path -> paths.add(root.relativize(path).toString().replace('\\', '/')));
        result.add("createdPaths", paths); return result;
    }

    public static Host host(Path snapshot, Path host, Configuration configuration, JsonObject environment) throws IOException {
        requireSupported(environment);
        String loader = environment.get("loader").getAsString(), version = environment.get("minecraftVersion").getAsString();
        boolean oldNeo = loader.equals("neoforge") && version.equals("1.20.1");
        WorkspaceExecutionSnapshot.rejectLinks(host);
        if (Files.exists(host)) throw new TestSetupException("GAMETEST_HOST_EXISTS", "Test host must be new for each run");
        Path artifact = artifact(snapshot, loader);
        Path report = host.resolve("run/gametest-results.xml");
        Files.createDirectories(report.getParent());
        for (String path : List.of("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")) {
            Path from = safePath(snapshot, path), to = safePath(host, path);
            if (!Files.isRegularFile(from)) throw new TestSetupException("GAMETEST_WRAPPER_MISSING", "Workspace wrapper is incomplete: " + path);
            Files.createDirectories(to.getParent()); Files.copy(from, to);
        }
        dev.copperbench.platform.ExecutableFilePermissions.ensureOwnerExecutable(host.resolve("gradlew"));
        copyTree(safePath(snapshot, configuration.sourceDirectory()), host.resolve("src/main/java"));
        copyTree(safePath(snapshot, configuration.resourceDirectory()), host.resolve("src/main/resources"));
        Path deployed = host.resolve("run/mods").resolve(artifact.getFileName()); Files.createDirectories(deployed.getParent());
        Files.copy(artifact, deployed);
        String fingerprint = WorkspaceExecutionSnapshot.sha256(artifact);
        if (!fingerprint.equals(WorkspaceExecutionSnapshot.sha256(deployed))) throw new IOException("Deployed artifact differs from built artifact");
        String repo = loader.equals("fabric") ? "https://maven.fabricmc.net/" : "https://maven.neoforged.net/releases";
        writeNew(host.resolve("settings.gradle"), "pluginManagement { repositories { maven { url = " + quote(repo)
                + " }; mavenCentral(); gradlePluginPortal() } }\nrootProject.name = 'copperbench-gametest-host'\n", new ArrayList<>());
        writeNew(host.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx" + (oldNeo ? "2G" : "1G") + " -Dfile.encoding=UTF-8\norg.gradle.configuration-cache=false\norg.gradle.parallel=false\n"
                + (oldNeo ? "net.neoforged.gradle.caching.enabled=false\n" : ""), new ArrayList<>());
        writeNew(host.resolve("build.gradle"), hostBuild(environment, report), new ArrayList<>());
        writeMetadata(host, loader, version, configuration.entrypoints());
        return new Host(host, report, deployed, fingerprint);
    }

    public static boolean requiresServerEula(JsonObject environment) {
        return environment.has("loader") && environment.get("loader").getAsString().equals("neoforge")
                && environment.get("minecraftVersion").getAsString().equals("1.20.1");
    }

    public static void requireServerAuthorization(JsonObject environment, boolean authorized) throws TestSetupException {
        if (requiresServerEula(environment) && !authorized) throw new TestSetupException("SERVER_EULA_APPROVAL_REQUIRED",
                "NeoForge 1.20.1 GameTest server needs user-issued server authority with Minecraft EULA acceptance");
    }

    static Path artifact(Path snapshot, String loader) throws IOException {
        Path libs = safePath(snapshot, "build/libs");
        if (!Files.isDirectory(libs)) throw new TestSetupException("GAMETEST_ARTIFACT_MISSING", "Build did not create a mod JAR");
        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.list(libs)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar") || name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar") || name.endsWith("-dev.jar")) continue;
                WorkspaceExecutionSnapshot.rejectLinks(path);
                try (var jar = new java.util.zip.ZipFile(path.toFile())) {
                    boolean mod = loader.equals("fabric") ? jar.getEntry("fabric.mod.json") != null
                            : jar.getEntry("META-INF/neoforge.mods.toml") != null || jar.getEntry("META-INF/mods.toml") != null;
                    if (mod) candidates.add(path);
                }
            }
        }
        if (candidates.size() != 1) throw new TestSetupException("GAMETEST_ARTIFACT_AMBIGUOUS", "Expected exactly one packaged mod JAR, found " + candidates.size());
        return candidates.getFirst();
    }

    static String hostBuild(JsonObject env, Path report) {
        String loader = env.get("loader").getAsString(), version = env.get("minecraftVersion").getAsString();
        int javaRelease = env.get("javaRelease").getAsInt();
        String base = "plugins { id " + quote(env.get("pluginId").getAsString()) + " version " + quote(env.get("pluginVersion").getAsString()) + " }\n"
                + "dependencies { " + (loader.equals("fabric") && javaRelease < 25 ? "modCompileOnly" : "compileOnly")
                + " fileTree(dir: 'run/mods', include: ['*.jar']) }\n";
        if (loader.equals("fabric")) {
            return base + "dependencies {\n minecraft " + quote("com.mojang:minecraft:" + version) + "\n"
                    + (javaRelease < 25 ? " mappings loom.officialMojangMappings()\n" : "")
                    + (javaRelease < 25 ? " modImplementation " : " implementation ") + quote("net.fabricmc:fabric-loader:" + env.get("loaderVersion").getAsString()) + "\n"
                    + (javaRelease < 25 ? " modImplementation " : " implementation ") + quote("net.fabricmc.fabric-api:fabric-api:" + env.get("fabricApiVersion").getAsString()) + "\n}\n"
                    + "tasks.withType(JavaCompile).configureEach { options.release = " + javaRelease + "; options.encoding = 'UTF-8' }\n"
                    + "tasks.named('runServer') { systemProperty 'fabric-api.gametest', '1'; systemProperty 'fabric-api.gametest.report-file', " + quote(report.toString()) + " }\n"
                    + "tasks.register('runGameTest') { dependsOn 'runServer' }\n";
        }
        String run = "gameTestServer { type = 'gameTestServer'; gameDirectory = file('run') }";
        if (!version.equals("1.20.1")) return base
                + "java.toolchain.languageVersion = JavaLanguageVersion.of(" + javaRelease + ")\n"
                + "neoForge { version = " + quote(env.get("loaderVersion").getAsString()) + "; runs { " + run + " }; mods { " + HOST_ID + " { sourceSet sourceSets.main } } }\n"
                + "tasks.register('runGameTest') { dependsOn 'runGameTestServer' }\n";
        return base + "java.toolchain.languageVersion = JavaLanguageVersion.of(17)\n"
                + "runs { gameTestServer { server(); systemProperty 'forge.gameTestServer', 'true'; workingDirectory = file('run') } }\n"
                + "dependencies { implementation " + quote("net.neoforged:forge:1.20.1-" + env.get("loaderVersion").getAsString()) + " }\n"
                + "tasks.register('runGameTest') { dependsOn 'runGameTestServer' }\n";
    }

    private static void writeMetadata(Path host, String loader, String version, List<String> entrypoints) throws IOException {
        if (loader.equals("fabric")) {
            JsonObject metadata = new JsonObject(); metadata.addProperty("schemaVersion", 1); metadata.addProperty("id", HOST_ID);
            metadata.addProperty("version", "1.0.0"); metadata.addProperty("environment", "server");
            JsonObject entries = new JsonObject(); JsonArray classes = new JsonArray(); entrypoints.forEach(classes::add);
            entries.add("fabric-gametest", classes); metadata.add("entrypoints", entries);
            writeNew(host.resolve("src/main/resources/fabric.mod.json"), metadata.toString(), new ArrayList<>());
            return;
        }
        boolean old = version.equals("1.20.1"), modern = version.startsWith("26.");
        String annotation = old ? "net.minecraftforge.fml.common.Mod" : "net.neoforged.fml.common.Mod";
        String metadata = "modLoader=\"javafml\"\nloaderVersion=\"" + (old ? "[47,)" : "[4,)")
                + "\"\nlicense=\"All Rights Reserved\"\n[[mods]]\nmodId=\"" + HOST_ID + "\"\nversion=\"1.0.0\"\n"
                + "[[mods]]\nmodId=\"copperbench_test_reporter\"\nversion=\"1.0.0\"\n";
        writeNew(host.resolve("src/main/resources/META-INF/" + (old ? "mods.toml" : "neoforge.mods.toml")), metadata, new ArrayList<>());
        if (!modern) writeNew(host.resolve("src/main/java/copperbench/host/Bootstrap.java"),
                "package copperbench.host; @" + annotation + "(\"" + HOST_ID + "\") public final class Bootstrap { public Bootstrap() {} }\n", new ArrayList<>());
        writeNew(host.resolve("src/main/java/copperbench/host/Reporter.java"), """
                package copperbench.host;
                @%s("copperbench_test_reporter")
                public final class Reporter {
                    public Reporter() {
                        try { net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(
                            new net.minecraft.gametest.framework.JUnitLikeTestReporter(new java.io.File("gametest-results.xml"))); }
                        catch (Exception e) { throw new IllegalStateException("Could not initialize Copperbench test report", e); }
                    }
                }
                """.formatted(annotation), new ArrayList<>());
    }

    private static String starter(String loader, String version, String modId) {
        String quotedId = new GsonBuilder().disableHtmlEscaping().create().toJson(modId);
        if (loader.equals("fabric")) return """
                package copperbench.acceptance;
                import net.fabricmc.loader.api.FabricLoader;
                import net.minecraft.gametest.framework.GameTestHelper;
                %s
                public final class AcceptanceTests %s {
                    %s
                    public void modLoads(GameTestHelper helper) {
                        if (!FabricLoader.getInstance().isModLoaded(%s)) throw new IllegalStateException("Tested mod was not loaded");
                        // Replace or extend this smoke check with assertions about your mod's behavior.
                        helper.succeed();
                    }
                }
                """.formatted(version.startsWith("26.") ? "import net.fabricmc.fabric.api.gametest.v1.GameTest;"
                : "import net.minecraft.gametest.framework.GameTest; import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;",
                version.startsWith("26.") ? "" : "implements FabricGameTest",
                version.startsWith("26.") ? "@GameTest" : "@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)", quotedId);
        if (version.startsWith("26.")) return neoModernStarter(quotedId);
        String namespace = version.equals("1.20.1") ? "net.minecraftforge" : "net.neoforged.neoforge";
        String modList = version.equals("1.20.1") ? "net.minecraftforge.fml.ModList" : "net.neoforged.fml.ModList";
        return """
                package copperbench.acceptance;
                import net.minecraft.gametest.framework.GameTest;
                import net.minecraft.gametest.framework.GameTestHelper;
                @%s.gametest.GameTestHolder("minecraft")
                @%s.gametest.PrefixGameTestTemplate(false)
                public final class AcceptanceTests {
                    @GameTest(template = "woodland_mansion/carpet_north")
                    public static void modLoads(GameTestHelper helper) {
                        if (!%s.get().isLoaded(%s)) throw new IllegalStateException("Tested mod was not loaded");
                        helper.succeed();
                    }
                }
                """.formatted(namespace, namespace, modList, quotedId);
    }

    private static String neoModernStarter(String quotedId) {
        return """
                package copperbench.acceptance;
                import com.mojang.serialization.MapCodec;
                import net.minecraft.core.Holder;
                import net.minecraft.gametest.framework.*;
                import net.minecraft.network.chat.Component;
                import net.minecraft.network.chat.MutableComponent;
                import net.minecraft.resources.Identifier;
                import net.neoforged.neoforge.event.RegisterGameTestsEvent;
                @net.neoforged.fml.common.Mod("copperbench_test_host")
                public final class AcceptanceTests {
                    public AcceptanceTests(net.neoforged.bus.api.IEventBus bus) { bus.addListener(AcceptanceTests::register); }
                    private static void register(RegisterGameTestsEvent event) {
                        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("copperbench_test_host", "default"));
                        var data = new TestData<>(environment, Identifier.withDefaultNamespace("bastion/mobs/empty"), 100, 0, true);
                        event.registerTest(Identifier.fromNamespaceAndPath("copperbench_test_host", "mod_loads"), ModLoads::new, data);
                    }
                    private static final class ModLoads extends GameTestInstance {
                        private static final MapCodec<ModLoads> CODEC = TestData.CODEC.xmap(ModLoads::new, ModLoads::data);
                        private ModLoads(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
                        private TestData<Holder<TestEnvironmentDefinition<?>>> data() { return info(); }
                        @Override public MapCodec<ModLoads> codec() { return CODEC; }
                        @Override protected MutableComponent typeDescription() { return Component.literal("Mod loading smoke check"); }
                        @Override public void run(GameTestHelper helper) {
                            if (!net.neoforged.fml.ModList.get().isLoaded(%s)) throw new IllegalStateException("Tested mod was not loaded");
                            helper.succeed();
                        }
                    }
                }
                """.formatted(quotedId);
    }

    private static void requireSupported(JsonObject environment) throws TestSetupException {
        if (!environment.has("loader") || !List.of("fabric", "neoforge").contains(environment.get("loader").getAsString()))
            throw new TestSetupException("GAMETEST_UNSUPPORTED_GENERATOR", "GameTest hosts require a supported Java mod generator");
    }
    public static Path safePath(Path root, String relative) throws IOException {
        Path base = root.toAbsolutePath().normalize(), child = Path.of(relative);
        if (child.isAbsolute() || relative.contains("\\") || child.normalize().startsWith("..") || relative.indexOf('\0') >= 0)
            throw new TestSetupException("GAMETEST_UNSAFE_PATH", "Test path must remain inside the workspace");
        Path path = base.resolve(child).normalize();
        if (!path.startsWith(base) || path.equals(base)) throw new TestSetupException("GAMETEST_UNSAFE_PATH", "Test path is not a workspace child");
        WorkspaceExecutionSnapshot.rejectLinks(path); return path;
    }
    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.exists(from)) return;
        WorkspaceExecutionSnapshot.rejectLinks(from);
        try (var paths = Files.walk(from)) {
            for (Path path : paths.toList()) {
                WorkspaceExecutionSnapshot.rejectLinks(path);
                Path destination = to.resolve(from.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else { Files.createDirectories(destination.getParent()); Files.copy(path, destination); }
            }
        }
    }
    private static void writeNew(Path path, String value, List<Path> created) throws IOException {
        WorkspaceExecutionSnapshot.rejectLinks(path); Files.createDirectories(path.getParent());
        Files.createFile(path); created.add(path); Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
    }
    private static String quote(String value) { return "'" + value.replace("\\", "/").replace("'", "\\'") + "'"; }
    private static String string(JsonObject value, String name, String fallback) { return value.has(name) ? value.get(name).getAsString() : fallback; }
}

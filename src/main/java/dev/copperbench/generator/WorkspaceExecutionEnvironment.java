package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.platform.RuntimePlatform;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.util.Map;

/** Read-only execution configuration shared by task gateways and pre-session doctor. */
public final class WorkspaceExecutionEnvironment {
    private WorkspaceExecutionEnvironment() {}

    /** Describes a built-in backend without opening a workspace, starting tasks or creating files. */
    public static JsonObject describe(Path root, Path distribution, String generator) {
        return describe(root, distribution, generator, VersionTrackCatalog.official());
    }

    /**
     * Describes an exact registered backend, including targets outside the display tracks.
     * @param root workspace root, without opening it
     * @param distribution product distribution root
     * @param generator exact persisted generator ID
     * @param catalog registry with the caller's display selection
     * @return the backend environment, or an empty object for an unknown ID
     */
    public static JsonObject describe(Path root, Path distribution, String generator, VersionTrackCatalog catalog) {
        return catalog.findRegistration(generator).map(registration -> switch (registration.profile()) {
            case Fabric1211Generator.Profile profile -> fabric(root, distribution, profile);
            case NeoForge1211Generator.Profile profile -> neoForge(root, distribution, profile);
            case VersionTrackCatalog.PendingProfile profile -> {
                JsonObject pending = new JsonObject();
                pending.addProperty("generatorId", profile.generatorId());
                pending.addProperty("minecraftVersion", profile.minecraftVersion());
                pending.addProperty("reasonCode", "PREVIEW_NATIVE_OPERATION_UNVERIFIED");
                pending.addProperty("backendReady", false);
                yield pending;
            }
            default -> throw new IllegalArgumentException("Unsupported registered execution profile: " + generator);
        }).orElseGet(JsonObject::new);
    }

    /** Describes the Fabric profile used by the actual task backend. */
    public static JsonObject fabric(Path root, Path distribution, Fabric1211Generator.Profile profile) {
        JsonObject value = common(root, distribution, profile.generatorId(), profile.minecraftVersion(),
                profile.javaRelease(), profile.gradleWrapperZip());
        value.addProperty("loader", "fabric");
        value.addProperty("loaderVersion", profile.loaderVersion());
        value.addProperty("fabricApiVersion", profile.fabricApiVersion());
        value.addProperty("loomVersion", profile.loomVersion());
        value.add("testing", GameTestSupport.describe(root, new Fabric1211Generator(distribution, profile).gameTestEnvironment()));
        return value;
    }

    /** Describes the NeoForge profile used by the actual task backend. */
    public static JsonObject neoForge(Path root, Path distribution, NeoForge1211Generator.Profile profile) {
        JsonObject value = common(root, distribution, profile.generatorId(), profile.minecraftVersion(),
                profile.javaRelease(), profile.fabricProfile().gradleWrapperZip());
        value.addProperty("loader", "neoforge");
        value.addProperty("loaderVersion", profile.neoForgeVersion());
        value.addProperty("loaderDependency", profile.loaderDependency());
        value.add("testing", GameTestSupport.describe(root, new NeoForge1211Generator(distribution, profile).gameTestEnvironment()));
        return value;
    }

    private static JsonObject common(Path root, Path distribution, String generator, String minecraft,
            int release, String archive) {
        root = root.toAbsolutePath().normalize();
        JsonObject value = new JsonObject();
        value.addProperty("generatorId", generator);
        value.addProperty("minecraftVersion", minecraft);
        value.addProperty("workspaceRoot", root.toString());
        value.addProperty("sourceRoot", root.resolve("src/main/java").toString());
        value.addProperty("resourceRoot", root.resolve("src/main/resources").toString());
        JsonObject java = new JsonObject();
        java.addProperty("requiredRelease", release);
        // The legacy tracks use the bundled Java 21 runtime even when compiling for 17.
        java.addProperty("runtimeRelease", release <= 21 ? 21 : 25);
        try {
            Path home = BundledJdkLocator.locate(distribution, release).toAbsolutePath().normalize();
            java.addProperty("home", home.toString());
            java.addProperty("executable", home.resolve("bin").resolve(RuntimePlatform.current().javaExecutableName()).toString());
            java.addProperty("resolutionStatus", "available");
        } catch (BundledJdkLocator.MissingJdkException error) {
            java.addProperty("resolutionStatus", "missing");
            java.addProperty("reasonCode", error.diagnosticCode());
            JsonArray paths = new JsonArray();
            error.attempted().forEach(path -> paths.add(path.toString()));
            java.add("attempted", paths);
        }
        value.add("java", java);
        JsonObject gradle = new JsonObject();
        gradle.addProperty("distribution", archive);
        gradle.addProperty("windowsLauncher", root.resolve("gradlew.bat").toString());
        gradle.addProperty("posixLauncher", root.resolve("gradlew").toString());
        Path cache = net.mcreator.io.UserFolderManager.getGradleHome().toPath();
        gradle.addProperty("userHome", cache.toString());
        gradle.addProperty("userHomeSource", dev.copperbench.gradle.GradleCachePolicy.userHomeSource(
                System.getenv(), System.getProperty("copperbench.gradle.user.home")));
        gradle.addProperty("reuseExternalDistributions",
                dev.copperbench.gradle.GradleCachePolicy.reuseExternalDistributions(System.getenv()));
        String launcher = System.getenv("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE");
        if (launcher != null && !launcher.isBlank()) gradle.addProperty("launcherOverride", launcher);
        JsonObject tasks = new JsonObject();
        for (String task : new String[] { "build", "runClient", "runServer", "runDatagen", "runGameTest" })
            tasks.addProperty(task, task);
        gradle.add("tasks", tasks);
        value.add("gradle", gradle);
        return value;
    }

    /** Resolves the same caller overrides and product cache default used by launched Gradle processes. */
    public static String gradleHome(Map<String, String> environment, Path productDefault) {
        return dev.copperbench.gradle.GradleCachePolicy.userHome(environment,
                System.getProperty("copperbench.gradle.user.home"), productDefault).toString();
    }
}

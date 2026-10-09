package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.platform.RuntimePlatform;

import java.nio.file.Path;
import java.util.Map;

/** Read-only execution configuration shared by task gateways and pre-session doctor. */
public final class WorkspaceExecutionEnvironment {
    private WorkspaceExecutionEnvironment() {}

    /** Describes a built-in backend without opening a workspace, starting tasks or creating files. */
    public static JsonObject describe(Path root, Path distribution, String generator) {
        return switch (generator == null ? "" : generator) {
            case "fabric-1.21.1" -> fabric(root, distribution, Fabric1211Generator.Profile.FABRIC_1211);
            case "fabric-26.1.2" -> fabric(root, distribution, Fabric1211Generator.Profile.FABRIC_261);
            case "fabric-26.2" -> fabric(root, distribution, Fabric1211Generator.Profile.FABRIC_262);
            case "fabric-1.20.1" -> fabric(root, distribution, Fabric1211Generator.Profile.FABRIC_1201);
            case "neoforge-1.21.1" -> neoForge(root, distribution, NeoForge1211Generator.Profile.NEOFORGE_1211);
            case "neoforge-26.1.2" -> neoForge(root, distribution, NeoForge1211Generator.Profile.NEOFORGE_261);
            case "neoforge-26.2" -> neoForge(root, distribution, NeoForge1211Generator.Profile.NEOFORGE_262);
            case "neoforge-1.20.1" -> neoForge(root, distribution, NeoForge1211Generator.Profile.NEOFORGE_1201);
            default -> new JsonObject();
        };
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
        Path cache = Path.of(gradleHome(System.getenv(), net.mcreator.io.UserFolderManager.getGradleHome().toPath()));
        gradle.addProperty("userHome", (cache.isAbsolute() ? cache : root.resolve(cache)).normalize().toString());
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
        String configured = environment.get("COPPERBENCH_GRADLE_USER_HOME");
        if (configured == null || configured.isBlank()) configured = environment.get("GRADLE_USER_HOME");
        return configured == null || configured.isBlank() ? productDefault.toAbsolutePath().normalize().toString() : configured;
    }
}

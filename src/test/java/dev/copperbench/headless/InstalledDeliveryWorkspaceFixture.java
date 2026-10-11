package dev.copperbench.headless;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.mcreator.Launcher;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.io.LoggingSystem;
import net.mcreator.io.UserFolderManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.util.UTF8Forcer;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.localhistory.HistoryCheckpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Test input preparation against an installed JAR; never an approval or bootstrap acceptance check. */
public final class InstalledDeliveryWorkspaceFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Expected metadata fixture and preparation receipt paths");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("jdk.xml.maxElementDepth", "0");
        LoggingSystem.init();
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        UTF8Forcer.forceGlobalUTF8();
        Properties configuration = new Properties();
        try (var stream = Launcher.class.getResourceAsStream("/mcreator.conf")) {
            if (stream == null) throw new IllegalStateException("Packaged mcreator.conf is missing");
            configuration.load(stream);
        }
        Launcher.version = new MCreatorVersionNumber(configuration);
        PreferencesManager.init();
        if (!UserFolderManager.createUserFolderIfNotExists())
            throw new IllegalStateException("Could not create isolated product directories");
        HeadlessRuntimeBootstrap.ensureInitialized();
        Path file = Path.of(args[0]).toAbsolutePath().normalize();
        JsonObject receipt = new JsonObject();
        try (Workspace workspace = Workspace.readFromFS(file.toFile(), null)) {
            if (!workspace.getModElements().isEmpty()
                    || !"fabric-1.21.1".equals(workspace.getWorkspaceSettings().getCurrentGenerator()))
                throw new IllegalArgumentException("Expected an empty Fabric 1.21.1 fixture");
            WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
            if (!workspace.getGenerator().generateBase())
                throw new IllegalStateException("Fixture base generation failed");
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            CompletableFuture<List<HistoryCheckpoint>> ready = new CompletableFuture<>();
            workspace.getHistoryManager().getCheckpoints(ready::complete);
            List<HistoryCheckpoint> checkpoints = ready.get(120, TimeUnit.SECONDS);
            if (checkpoints.isEmpty()) throw new IllegalStateException("Fixture history failed to initialize");
            JsonArray history = new JsonArray();
            checkpoints.forEach(checkpoint -> history.add(checkpoint.hash()));
            receipt.add("preparedHistoryCheckpoints", history);
            receipt.addProperty("generatorVersion", workspace.getGenerator().getFullGeneratorVersion());
        }
        for (String required : List.of("build.gradle", "settings.gradle", "gradlew", "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.properties")) {
            if (!Files.isRegularFile(file.getParent().resolve(required)))
                throw new IllegalStateException("Prepared fixture is missing " + required);
        }
        receipt.addProperty("kind", "installed_generator_test_input_preparation");
        receipt.addProperty("bootstrapCreateVerified", false);
        receipt.addProperty("authorityIssued", false);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(receipt));
        // The standalone preparer owns the runtime plugin executors.
        System.exit(0);
    }
}

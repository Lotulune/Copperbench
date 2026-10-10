package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.generator.GradleDistributionIntegrity;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** No process launches or network probes: realistic filesystem boundaries and actual backend facts. */
class WorkspaceDoctorTest {
    @TempDir Path directory;

    @Test void coldWorkspaceUsesActualBackendWithoutMetadataHistoryOrDownloads() throws Exception {
        Path root = directory.resolve("cold");
        Files.createDirectories(root);
        Path file = root.resolve("cold.mcreator");
        Files.writeString(file, "{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
        Map<String, String> before = inventory();
        JsonObject report = WorkspaceDoctor.inspectClosed(file, Path.of("."));
        assertEquals(before, inventory(), "Doctor must not create a lease, history, metadata or cache");
        assertTrue(report.get("readOnly").getAsBoolean());
        assertFalse(report.get("networkProbed").getAsBoolean());
        assertEquals("fabric-1.21.1", report.getAsJsonObject("execution").get("generatorId").getAsString());
        assertEquals(21, report.getAsJsonObject("execution").getAsJsonObject("java").get("runtimeRelease").getAsInt());
        assertEquals(25, row(report, "product_java").getAsJsonObject("details").get("requiredRelease").getAsInt());
        assertEquals("missing", row(report, "wrapper").get("status").getAsString());
        assertEquals("unknown", row(report, "network").get("status").getAsString());
        assertEquals("unknown", row(report, "cache").get("status").getAsString());
        Path receipt = Path.of("build/reports/workspace-doctor/real-backend.json");
        Files.createDirectories(receipt.getParent());
        Files.writeString(receipt, UiCore.wireGson().toJson(report));
    }

    @Test void declaredJavaDoesNotHideTheWrongActualVersion() throws Exception {
        JsonObject execution = fakeExecution("25.0.2");
        var before = inventory();
        JsonObject report = inspect(execution);
        assertEquals("unsupported", row(report, "workspace_java").get("status").getAsString());
        assertEquals(25, row(report, "workspace_java").getAsJsonObject("details").get("actualRelease").getAsInt());
        assertEquals(before, inventory());
        Files.writeString(directory.resolve("jdk/release"), "JAVA_VERSION=\"21.0.8\"\n");
        assertEquals("available", row(inspect(execution), "workspace_java").get("status").getAsString());
        Files.delete(directory.resolve("jdk/release"));
        assertEquals("unknown", row(inspect(execution), "workspace_java").get("status").getAsString());
        Files.delete(Path.of(execution.getAsJsonObject("java").get("executable").getAsString()));
        assertEquals("missing", row(inspect(execution), "workspace_java").get("status").getAsString());
    }

    @Test void wrapperDigestAndRuntimeAreInspectedSeparatelyAndCredentialsAreRedacted() throws Exception {
        JsonObject execution = fakeExecution("21.0.8");
        Path file = directory.resolve("workspace/gradle/wrapper/gradle-wrapper.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "distributionUrl=https://user:password@example.invalid/gradle-9.7.0-bin.zip?token=private\n"
                + "distributionSha256Sum=" + GradleDistributionIntegrity.require("gradle-9.7.0-bin.zip") + "\n");
        JsonObject report = inspect(execution);
        assertEquals("available", row(report, "wrapper").get("status").getAsString());
        assertEquals("missing", row(report, "wrapper_runtime").get("status").getAsString());
        assertFalse(report.toString().contains("password"));
        assertFalse(report.toString().contains("private"));
        Files.writeString(file, "distributionUrl=https://example.invalid/gradle-9.7.0-bin.zip\ndistributionSha256Sum=" + "0".repeat(64));
        assertEquals("blocked", row(inspect(execution), "wrapper").get("status").getAsString());
        Files.writeString(file, "distributionUrl=https://example.invalid/gradle-9.7.0-bin.zip\n");
        assertEquals("missing", row(inspect(execution), "wrapper").get("status").getAsString());
        Files.writeString(file, "distributionUrl=https://example.invalid/gradle-0.0-bin.zip\n");
        assertEquals("unsupported", row(inspect(execution), "wrapper").get("status").getAsString());
        Files.writeString(file, "distributionUrl=::bad uri\n");
        assertEquals("unknown", row(inspect(execution), "wrapper").get("status").getAsString());
    }

    @Test void absentOrBrokenBackendsAndDisplayDeclarationsCannotClaimAvailability() throws Exception {
        Path root = directory.resolve("workspace"); Files.createDirectories(root);
        JsonObject report = WorkspaceDoctor.inspect(root, "unexposed", () -> { throw new IllegalStateException("backend"); },
                Map.of("DISPLAY", ":99", "HTTPS_PROXY", "https://user:secret@example.invalid"));
        assertEquals("unknown", row(report, "backend").get("status").getAsString());
        assertEquals("unknown", row(report, "workspace_java").get("status").getAsString());
        assertEquals("unknown", row(report, "renderer").get("status").getAsString());
        assertFalse(row(report, "renderer").getAsJsonObject("details").get("openGlObserved").getAsBoolean());
        assertFalse(report.toString().contains("secret"));
        assertEquals("unknown", row(WorkspaceDoctor.inspect(root, "unexposed", JsonObject::new), "backend").get("status").getAsString());
        JsonObject hostOnly = new JsonObject(); hostOnly.add("host", new JsonObject());
        assertEquals("unknown", row(WorkspaceDoctor.inspect(root, "unexposed", () -> hostOnly), "backend").get("status").getAsString());
        JsonObject execution = fakeExecution("21.0.8");
        execution.getAsJsonObject("gradle").addProperty("launcherOverride", "custom-gradle");
        assertEquals("GRADLE_LAUNCHER_OVERRIDE", row(inspect(execution), "wrapper").get("code").getAsString());
    }

    @Test void cachePrecedenceMatchesTheProcessRunnerWithoutPreparingDirectories() throws Exception {
        Path defaults = directory.resolve("product-cache");
        assertEquals(Path.of("caller-cache").toAbsolutePath().toString(), WorkspaceExecutionEnvironment.gradleHome(
                Map.of("COPPERBENCH_GRADLE_USER_HOME","caller-cache","GRADLE_USER_HOME","environment-cache"), defaults));
        assertEquals(Path.of("environment-cache").toAbsolutePath().toString(), WorkspaceExecutionEnvironment.gradleHome(Map.of("GRADLE_USER_HOME","environment-cache"), defaults));
        assertEquals(defaults.toAbsolutePath().toString(), WorkspaceExecutionEnvironment.gradleHome(Map.of(), defaults));
        assertFalse(Files.exists(defaults));
    }

    @Test void cacheOriginComesFromTheBackendRatherThanTheInspectorsEnvironment() throws Exception {
        JsonObject execution = fakeExecution("21.0.8");
        assertEquals("backend_unreported", row(inspect(execution), "cache").getAsJsonObject("details").get("origin").getAsString());
        execution.getAsJsonObject("gradle").addProperty("userHomeSource", "copperbench.gradle.user.home");
        execution.getAsJsonObject("gradle").addProperty("reuseExternalDistributions", false);
        JsonObject report = WorkspaceDoctor.inspect(directory.resolve("workspace"), "fabric-1.21.1", () -> execution,
                Map.of("GRADLE_USER_HOME", "unrelated-inspector-cache"));
        JsonObject detail = row(report, "cache").getAsJsonObject("details");
        assertEquals("copperbench.gradle.user.home", detail.get("origin").getAsString());
        assertFalse(detail.get("reuseExternalDistributions").getAsBoolean());
        assertEquals("unknown", detail.get("artifactCompleteness").getAsString());
    }

    private JsonObject fakeExecution(String version) throws Exception {
        Path root = directory.resolve("workspace"); Files.createDirectories(root);
        Path binary = directory.resolve("jdk/bin/java" + (java.io.File.separatorChar == '\\' ? ".exe" : ""));
        Files.createDirectories(binary.getParent()); Files.writeString(binary, "never executed");
        binary.toFile().setExecutable(true);
        Files.writeString(directory.resolve("jdk/release"), "JAVA_VERSION=\"" + version + "\"\n");
        JsonObject execution = new JsonObject(), jdk = new JsonObject(), gradle = new JsonObject();
        jdk.addProperty("home", directory.resolve("jdk").toString()); jdk.addProperty("executable", binary.toString());
        jdk.addProperty("requiredRelease",21); jdk.addProperty("runtimeRelease",21); execution.add("java",jdk);
        gradle.addProperty("userHome",directory.resolve("absent-cache").toString()); execution.add("gradle",gradle);
        return execution;
    }

    private JsonObject inspect(JsonObject execution) {
        return WorkspaceDoctor.inspect(directory.resolve("workspace"), "fabric-1.21.1", () -> execution, Map.of("DISPLAY",":1"));
    }

    private static JsonObject row(JsonObject report, String id) {
        return report.getAsJsonArray("findings").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> id.equals(value.get("id").getAsString())).findFirst().orElseThrow();
    }

    private Map<String, String> inventory() throws Exception {
        Map<String, String> paths = new TreeMap<>();
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.toList())
                paths.put(directory.relativize(path).toString(), Files.isRegularFile(path) ? WorkspaceExecutionSnapshot.sha256(path) : "directory");
        }
        return paths;
    }
}

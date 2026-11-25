package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.WorkspaceFiles;
import dev.copperbench.core.workspace.project.GradleProjectDetector;
import dev.copperbench.core.workspace.project.GradleProjectIdentity.Problem;
import dev.copperbench.core.workspace.project.GradleProjectOperations;
import dev.copperbench.generator.GradleDistributionIntegrity;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/** Local environment observations; never opens a writer session or runs a process/network probe. */
public final class WorkspaceDoctor {
    private WorkspaceDoctor() {}

    /** Inspects a closed workspace before runtime bootstrap, metadata creation or writer leasing. */
    public static JsonObject inspectClosed(Path workspaceFile, Path distributionRoot) throws IOException {
        if (Files.size(workspaceFile) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Workspace metadata exceeds the doctor read limit");
        JsonObject workspace;
        try (Reader reader = Files.newBufferedReader(workspaceFile)) {
            var value = JsonParser.parseReader(reader);
            if (!value.isJsonObject()) throw new IllegalArgumentException("Workspace metadata must be an object");
            workspace = value.getAsJsonObject();
        }
        JsonObject settings = workspace.has("workspaceSettings") && workspace.get("workspaceSettings").isJsonObject()
                ? workspace.getAsJsonObject("workspaceSettings") : new JsonObject();
        String generator = string(settings, "currentGenerator");
        Path root = workspaceFile.toAbsolutePath().normalize().getParent();
        return inspect(root, generator, () -> WorkspaceExecutionEnvironment.describe(root, distributionRoot, generator));
    }

    /** Inspects a Gradle project folder Copperbench did not create (ADR-0020) without opening or writing to it. */
    public static JsonObject inspectClosedProject(Path folder, Path distributionRoot) throws IOException {
        Path root = WorkspaceFiles.requireDirectory(folder);
        var identity = GradleProjectDetector.inspect(root);
        return inspectProject(root, identity.generatorId(), identity.problems(),
                () -> WorkspaceExecutionEnvironment.adoptedProject(root, distributionRoot, identity.generatorId()));
    }

    /** Observes an adopted project: its wrapper belongs to the project, and identification problems are findings. */
    public static JsonObject inspectProject(Path root, String generator, List<Problem> problems, Supplier<JsonObject> execution) {
        return inspect(root, generator, execution, System.getenv(), List.copyOf(problems));
    }

    /** Observes the active task backend; an unavailable backend remains an explicit unknown finding. */
    public static JsonObject inspect(Path root, String generator, Supplier<JsonObject> execution) {
        return inspect(root, generator, execution, System.getenv());
    }

    static JsonObject inspect(Path root, String generator, Supplier<JsonObject> execution, Map<String, String> environment) {
        return inspect(root, generator, execution, environment, null);
    }

    /** {@code problems} is null for generated workspaces and lists what identification found for adopted projects. */
    static JsonObject inspect(Path root, String generator, Supplier<JsonObject> execution, Map<String, String> environment,
            List<Problem> problems) {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", "1.0");
        report.addProperty("scope", "local_observation");
        report.addProperty("readOnly", true);
        report.addProperty("networkProbed", false);
        report.addProperty("generatorId", generator);
        report.addProperty("workspaceRoot", root == null ? null : root.toAbsolutePath().normalize().toString());
        report.addProperty("processWorkingDirectory", Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().toString());
        report.add("application", ApplicationBuildIdentity.inspect());
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("coreSchemaVersion", UiCore.SCHEMA_VERSION);
        capabilities.addProperty("doctorSchemaVersion", "1.0");
        JsonArray queries = new JsonArray();
        (problems != null ? List.of("get_workspace_doctor", "get_workspace_environment", "get_task")
                : List.of("get_workspace_doctor", "get_workspace_environment", "get_mod_element_field_contract",
                "get_field_reference_options", "preview_generation", "get_task")).forEach(queries::add);
        capabilities.add("queries", queries);
        capabilities.addProperty("implicitNetworkProbe", false);
        capabilities.addProperty("implicitTaskAuthorization", false);
        report.add("capabilities", capabilities);
        JsonArray findings = new JsonArray();
        report.add("findings", findings);

        JsonObject product = new JsonObject();
        product.addProperty("home", System.getProperty("java.home"));
        product.addProperty("executable", ProcessHandle.current().info().command().orElse(""));
        product.addProperty("actualVersion", System.getProperty("java.runtime.version"));
        product.addProperty("actualRelease", Runtime.version().feature());
        product.addProperty("requiredRelease", 25);
        finding(findings, "product_java", Runtime.version().feature() == 25 ? "available" : "unsupported",
                "PRODUCT_JAVA_VERSION", "The product process runs Java " + Runtime.version().feature() + ".",
                "Use the product's Java 25 runtime.", product);
        String rootState = root == null || !Files.isDirectory(root) ? "missing"
                : !Files.isReadable(root) ? "blocked" : "available";
        finding(findings, "workspace_directory", rootState, "WORKSPACE_DIRECTORY",
                "Workspace directory observation.", "Select an existing readable workspace.", new JsonObject());
        if (problems != null) projectFinding(findings, generator, problems);

        JsonObject observed = new JsonObject();
        try {
            JsonObject result = execution.get();
            if (result != null) observed = result.deepCopy();
            boolean resolved = generator != null && generator.equals(string(observed, "generatorId"));
            finding(findings, "backend", resolved ? "available" : "unknown", "BACKEND_ENVIRONMENT",
                    !resolved ? "This backend does not expose execution facts for the current generator."
                            : "Execution facts come from the active task backend.",
                    "Use a backend with environment discovery to resolve unknown facts.", new JsonObject());
        } catch (RuntimeException error) {
            JsonObject detail = new JsonObject();
            detail.addProperty("exceptionType", error.getClass().getSimpleName());
            finding(findings, "backend", "unknown", "BACKEND_ENVIRONMENT_UNAVAILABLE",
                    "The task backend could not provide environment facts.",
                    "Inspect backend initialization diagnostics before launching a task.", detail);
        }
        report.add("execution", observed);
        javaFinding(findings, observed.has("java") ? observed.getAsJsonObject("java") : new JsonObject());
        wrapperFinding(findings, root, observed.has("gradle") ? observed.getAsJsonObject("gradle") : new JsonObject(), problems != null);
        cacheFinding(findings, observed);
        JsonObject network = new JsonObject();
        network.addProperty("proxyConfigured", List.of("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY",
                "http_proxy", "https_proxy", "all_proxy").stream().anyMatch(key -> present(environment, key)));
        network.addProperty("jvmProxyConfigured", System.getProperty("https.proxyHost") != null
                || System.getProperty("http.proxyHost") != null);
        network.addProperty("repositoryReachability", "unknown");
        network.addProperty("gradleInitScriptsInspected", false);
        finding(findings, "network", "unknown", "NETWORK_NOT_PROBED", "No network connection was attempted.",
                "Run an explicit connectivity check when needed. Local Loom IPC/socket failures need local runtime diagnosis.", network);
        JsonObject renderer = new JsonObject();
        renderer.addProperty("x11DisplayDeclared", present(environment, "DISPLAY"));
        renderer.addProperty("waylandDisplayDeclared", present(environment, "WAYLAND_DISPLAY"));
        renderer.addProperty("productAwtHeadless", Boolean.getBoolean("java.awt.headless"));
        renderer.addProperty("openGlObserved", false);
        boolean noDisplay = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux")
                && !present(environment, "DISPLAY") && !present(environment, "WAYLAND_DISPLAY");
        finding(findings, "renderer", noDisplay ? "missing" : "unknown", "CLIENT_RENDERER_NOT_OBSERVED",
                noDisplay ? "No Linux display is declared for a client process." : "Display declarations do not prove client rendering.",
                "Validate Minecraft rendering in the intended isolated graphical session.", renderer);
        boolean blocked = findings.asList().stream().map(value -> string(value.getAsJsonObject(), "status"))
                .anyMatch(state -> List.of("missing", "unsupported", "blocked").contains(state));
        report.addProperty("status", blocked ? "blocked" : "unknown");
        return report;
    }

    private static void javaFinding(JsonArray findings, JsonObject configured) {
        JsonObject detail = configured.deepCopy();
        String home = string(configured, "home");
        String executable = string(configured, "executable");
        if (home == null || executable == null) {
            String status = "missing".equals(string(configured, "resolutionStatus")) ? "missing" : "unknown";
            finding(findings, "workspace_java", status, "WORKSPACE_JAVA_UNRESOLVED",
                    "The workspace runtime could not be resolved.", "Provide the runtime required by the active generator.", detail);
            return;
        }
        try {
            Path binary = Path.of(executable);
            if (!Files.isRegularFile(binary)) {
                finding(findings, "workspace_java", "missing", "WORKSPACE_JAVA_MISSING",
                        "The selected workspace Java executable is missing.", "Restore the generator's bundled JDK.", detail);
                return;
            }
            if (!Files.isReadable(binary) || !Files.isExecutable(binary)) throw new AccessDeniedException(executable);
            Path release = Path.of(home).resolve("release");
            if (!Files.isRegularFile(release)) {
                finding(findings, "workspace_java", "unknown", "WORKSPACE_JAVA_VERSION_UNKNOWN",
                        "The JDK release file is missing; the configured version is not proof of the actual runtime.",
                        "Check the selected JDK installation.", detail);
                return;
            }
            Properties metadata = readProperties(release);
            String version = metadata.getProperty("JAVA_VERSION", "").replace("\"", "");
            detail.addProperty("actualVersion", version);
            var match = java.util.regex.Pattern.compile("^(?:1\\.)?(\\d+)").matcher(version);
            if (!match.find()) throw new IllegalArgumentException("Unrecognized Java release metadata");
            int actual = Integer.parseInt(match.group(1));
            detail.addProperty("actualRelease", actual);
            int expected = configured.has("runtimeRelease") ? configured.get("runtimeRelease").getAsInt()
                    : configured.has("requiredRelease") ? configured.get("requiredRelease").getAsInt() : -1;
            finding(findings, "workspace_java", expected < 0 ? "unknown" : actual == expected ? "available" : "unsupported",
                    "WORKSPACE_JAVA_VERSION", "Workspace Java version read from the selected JDK release file.",
                    "Use the workspace runtime declared by the active backend; product Java is a separate requirement.", detail);
        } catch (AccessDeniedException | SecurityException error) {
            finding(findings, "workspace_java", "blocked", "WORKSPACE_JAVA_ACCESS_DENIED",
                    "The selected JDK cannot be inspected or executed.", "Check access to the selected JDK.", detail);
        } catch (IOException | IllegalArgumentException error) {
            finding(findings, "workspace_java", "unknown", "WORKSPACE_JAVA_VERSION_UNKNOWN",
                    "The selected JDK metadata cannot be interpreted.", "Check its release file and installation.", detail);
        }
    }

    private static void projectFinding(JsonArray findings, String generator, List<Problem> problems) {
        JsonObject detail = new JsonObject();
        JsonArray listed = new JsonArray();
        for (Problem problem : problems) {
            JsonObject item = new JsonObject();
            item.addProperty("code", problem.code());
            item.addProperty("message", problem.message());
            listed.add(item);
        }
        detail.add("problems", listed);
        if (problems.isEmpty()) finding(findings, "gradle_project", "available", "GRADLE_PROJECT_IDENTIFIED",
                "The project's files identify " + generator + ".", "Build and test it through Copperbench.", detail);
        else finding(findings, "gradle_project", "unsupported", GradleProjectOperations.UNSUPPORTED,
                String.join(" ", problems.stream().map(Problem::message).toList()),
                "Resolve the listed problems to build and test; reading and editing stay available.", detail);
    }

    private static void wrapperFinding(JsonArray findings, Path root, JsonObject configured, boolean projectOwned) {
        JsonObject detail = new JsonObject();
        if (configured.has("distribution")) detail.add("declaredDistribution", configured.get("distribution").deepCopy());
        if (configured.has("launcherOverride")) {
            detail.add("launcherOverride", configured.get("launcherOverride").deepCopy());
            finding(findings, "wrapper", "unknown", "GRADLE_LAUNCHER_OVERRIDE",
                    "The task backend uses a caller-supplied Gradle launcher.",
                    "Inspect that launcher's distribution and integrity separately.", detail);
            return;
        }
        Path file = root == null ? null : root.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (file == null || !Files.isRegularFile(file)) {
            finding(findings, "wrapper", "missing", "WRAPPER_PROPERTIES_MISSING", "Workspace wrapper properties are missing.",
                    "Prepare the workspace through the normal authorized generation/build flow.", detail);
            return;
        }
        detail.addProperty("propertiesPath", file.toString());
        try {
			wrapperRuntimeFinding(findings, root, projectOwned);
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                finding(findings, "wrapper", "blocked", "WRAPPER_OUTSIDE_WORKSPACE", "Wrapper properties resolve outside this workspace.",
                        "Review the linked path before using the wrapper.", detail);
                return;
            }
            Properties properties = readProperties(file);
            URI uri = URI.create(properties.getProperty("distributionUrl", ""));
            // Credentials and query strings are not part of an environment report.
            detail.addProperty("source", new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString());
            String archive = Path.of(uri.getPath()).getFileName().toString();
            detail.addProperty("archive", archive);
            String expected = properties.getProperty("distributionSha256Sum");
            detail.addProperty("distributionSha256Sum", expected);
            var reviewed = GradleDistributionIntegrity.find(archive);
            // An adopted project chooses its own distribution; not having reviewed it is not a fault of the project.
            String status = reviewed.isEmpty() ? projectOwned ? "unknown" : "unsupported"
                    : expected == null ? projectOwned ? "unknown" : "missing"
                    : reviewed.get().equals(expected) ? "available" : "blocked";
            String code = reviewed.isEmpty() ? "WRAPPER_DISTRIBUTION_UNREVIEWED" : expected == null
                    ? "WRAPPER_CHECKSUM_MISSING" : reviewed.get().equals(expected) ? "WRAPPER_CHECKSUM_CONFIGURED" : "WRAPPER_CHECKSUM_MISMATCH";
            finding(findings, "wrapper", status, code,
                    "Configured distribution digest observation; a warm cache does not prove a verified download.",
                    "Use the reviewed digest and run the cold-cache integrity gate.", detail);
        } catch (AccessDeniedException | SecurityException error) {
            finding(findings, "wrapper", "blocked", "WRAPPER_ACCESS_DENIED", "Wrapper properties cannot be read.",
                    "Check workspace wrapper access.", detail);
        } catch (IOException | java.net.URISyntaxException | IllegalArgumentException | NullPointerException error) {
            finding(findings, "wrapper", "unknown", "WRAPPER_PROPERTIES_INVALID", "Wrapper properties could not be interpreted.",
                    "Inspect distributionUrl and distributionSha256Sum.", detail);
        }
    }

    private static void wrapperRuntimeFinding(JsonArray findings, Path root, boolean projectOwned) throws IOException {
        JsonObject detail = new JsonObject();
        Path launcher = root.resolve(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "gradlew.bat" : "gradlew");
        Path jar = root.resolve("gradle/wrapper/gradle-wrapper.jar");
        detail.addProperty("launcher", launcher.toString());
        detail.addProperty("jar", jar.toString());
        String status;
        if (!Files.isRegularFile(launcher) || !Files.isRegularFile(jar)) status = "missing";
        else if (!jar.toRealPath().startsWith(root.toRealPath()) || !launcher.toRealPath().startsWith(root.toRealPath())
                // A project's POSIX launcher without the executable bit is started through sh.
                || !Files.isReadable(jar) || !Files.isReadable(launcher) || !projectOwned && !Files.isExecutable(launcher)) status = "blocked";
        else {
            String checksum = dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(jar);
            detail.addProperty("jarSha256", checksum);
            status = GradleDistributionIntegrity.reviewedWrapper(checksum) ? "available" : projectOwned ? "unknown" : "unsupported";
        }
        finding(findings, "wrapper_runtime", status, "WRAPPER_RUNTIME_FILES",
                "Local wrapper launcher and JAR observation.", projectOwned
                        ? "Copperbench builds with the project's own wrapper and does not change it."
                        : "Use the workspace's reviewed wrapper runtime files.", detail);
    }

    private static void cacheFinding(JsonArray findings, JsonObject execution) {
        JsonObject detail = new JsonObject();
        if (!execution.has("gradle") || !execution.getAsJsonObject("gradle").has("userHome")) {
            finding(findings, "cache", "unknown", "CACHE_LOCATION_UNKNOWN", "The backend did not declare its cache directory.",
                    "Resolve the execution backend before checking its cache.", detail);
            return;
        }
        JsonObject gradle = execution.getAsJsonObject("gradle");
        Path path = Path.of(gradle.get("userHome").getAsString());
        detail.addProperty("path", path.toString());
        String source = string(gradle, "userHomeSource");
        detail.addProperty("origin", source == null || source.isBlank() ? "backend_unreported" : source);
        if (gradle.has("reuseExternalDistributions"))
            detail.add("reuseExternalDistributions", gradle.get("reuseExternalDistributions").deepCopy());
        detail.addProperty("directoryPresent", Files.isDirectory(path));
        detail.addProperty("artifactCompleteness", "unknown");
        detail.addProperty("downloadIntegrity", "unknown");
        finding(findings, "cache", Files.exists(path) && (!Files.isDirectory(path) || !Files.isReadable(path)) ? "blocked" : "unknown",
                "CACHE_CONTENTS_UNVERIFIED", "Cache location is observed; dependency completeness and download verification are unknown.",
                "Use cold-download gates and a separate warm-cache build.", detail);
    }

    private static Properties readProperties(Path path) throws IOException {
        if (Files.size(path) > 65536) throw new IOException("Metadata exceeds the doctor read limit");
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { result.load(reader); }
        return result;
    }

    private static boolean present(Map<String, String> values, String key) {
        return values.get(key) != null && !values.get(key).isBlank();
    }

    private static String string(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isString()
                ? value.get(key).getAsString() : null;
    }

    private static void finding(JsonArray target, String id, String status, String code, String message,
            String nextStep, JsonObject details) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id); value.addProperty("status", status); value.addProperty("code", code);
        value.addProperty("message", message); value.addProperty("nextStep", nextStep); value.add("details", details);
        target.add(value);
    }
}

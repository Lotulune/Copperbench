package dev.copperbench.headless;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Development smoke harness: an actual Python process calls the actual product launcher. */
public final class NativePythonSmokeMain {
    public static void main(String[] args) throws Exception {
        McreatorTestRuntime.ensureInitialized();
        Path root = Files.createTempDirectory("copperbench-native-python-");
        Path file = root.resolve("python_smoke.mcreator");
        WorkspaceSettings settings = new WorkspaceSettings("python_smoke");
        settings.setModName("Python Smoke");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings)) {
            if (!workspace.getGenerator().generateBase()) throw new IllegalStateException("Could not generate fixture");
        }
        JsonObject config = new JsonObject();
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-cp");
        launcher.add(System.getProperty("java.class.path"));
        launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        config.addProperty("workspace", file.toString());
        config.addProperty("cwd", System.getProperty("user.dir"));
        Path configFile = root.resolve("native-smoke.json");
        Files.writeString(configFile, config.toString());
        Process process = new ProcessBuilder(args[0], "sdk/python/native_smoke.py", configFile.toString())
                .inheritIO().start();
        if (!process.waitFor(4, TimeUnit.MINUTES)) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroyForcibly();
            throw new IllegalStateException("Python smoke timed out: " + root);
        }
        if (process.exitValue() != 0) throw new IllegalStateException("Python smoke failed; fixture: " + root);
        try (Workspace workspace = Workspace.readFromFS(file.toFile(), null);
             var session = dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession.attach(workspace,
                     store -> new dev.copperbench.core.application.InMemoryWorkspaceTaskGateway(
                             java.time.Clock.systemUTC(), UUID::randomUUID), java.time.Clock.systemUTC(), UUID::randomUUID);
             var runtime = DesktopPythonRuntime.start(file, session.workspaceId(),
                     session.headlessEntry(dev.copperbench.core.contract.UiCore.PermissionProfile.WORKSPACE))) {
            var events = new java.util.concurrent.CopyOnWriteArrayList<dev.copperbench.core.contract.UiCore.Event>();
            try (var subscription = session.uiEntry().subscribeEvents(session.workspaceId(), 0, events::add)) {
                Process attached = new ProcessBuilder(args[0], "sdk/python/native_desktop_smoke.py", file.toString())
                        .inheritIO().start();
                if (!attached.waitFor(60, TimeUnit.SECONDS)) {
                    attached.destroyForcibly();
                    throw new IllegalStateException("Desktop Python smoke timed out");
                }
                if (attached.exitValue() != 0) throw new IllegalStateException("Desktop Python smoke failed");
                if (events.stream().filter(event -> event.event().equals("mod_element_updated")).count() != 2
                        || events.stream().filter(event -> event.event().equals("mod_element_created")).count() != 1)
                    throw new IllegalStateException("Desktop did not receive exactly the committed Python changes: " + events);
            }
            try (var console = new PythonConsoleService(file, Path.of("sdk/python"), runtime.context())) {
                consoleRun(console, args[0], "counter = 41", "console");
                consoleRun(console, args[0], "counter + 1", "console");
                if (!console.status(0).toString().contains("42")) throw new IllegalStateException("Console expression did not print");
                consoleRun(console, args[0], """
                        element = cb.data.elements['python_bell']
                        cb.context.active_element = element
                        assert cb.context.active_element.id == element.id
                        element.display_name = 'Console Bell'
                        assert element.display_name == 'Console Bell'
                        element.displayName = 'Console Attribute'
                        assert element.displayName == 'Console Attribute'
                        assert 'displayName' in dir(element)
                        try:
                            element.misspelled_unknown_field = 1
                            raise AssertionError('Unknown attribute was silently persisted')
                        except AttributeError:
                            pass
                        class Demo(cb.types.Operator):
                            idname = 'demo.probe'
                            label = 'Probe'
                            def execute(self, context):
                                print('operator probe passed')
                        cb.utils.register_class(Demo)
                        cb.ops.demo.probe()
                        def later():
                            print('timer probe passed')
                            return None
                        cb.app.timers.register(later)
                        def changed(context):
                            print('handler probe passed')
                        cb.app.handlers.workspace_update.append(changed)
                        """, "script");
                if (runtime.context().snapshot().get("activeElementId").isJsonNull())
                    throw new IllegalStateException("Script selection did not reach the desktop context");
                waitUntil(() -> console.status(0).toString().contains("timer probe passed"), 5000);
                if (!console.status(0).toString().contains("operator probe passed")) throw new IllegalStateException("Operator failed");
                consoleRun(console, args[0], "cb.context.active_element.display_name = 'Handler Bell'", "console");
                waitUntil(() -> console.status(0).toString().contains("handler probe passed"), 5000);
                var failure = consoleRun(console, args[0], "x = 1\nraise ValueError('traceback probe')\n", "script", true);
                if (failure.get("errorLine").getAsInt() != 2 || !failure.get("lastResult").getAsString().equals("failed")
                        || !failure.toString().contains("traceback probe"))
                    throw new IllegalStateException("Python exception location was lost");
                JsonObject completion = new JsonObject();
                completion.addProperty("operation", "complete"); completion.addProperty("text", "cb.con");
                console.execute(completion);
                waitUntil(() -> console.status(0).get("state").getAsString().equals("ready"), 5000);
                if (!console.status(0).getAsJsonArray("completions").toString().contains("cb.context"))
                    throw new IllegalStateException("Console completion is unavailable");
                JsonObject infinite = new JsonObject();
                infinite.addProperty("operation", "execute"); infinite.addProperty("mode", "script");
                infinite.addProperty("source", "print('busy loop entered')\nwhile True:\n    pass\n");
                console.execute(infinite);
                waitUntil(() -> console.status(0).toString().contains("busy loop entered"), 5000);
                console.stop();
                if (!console.status(0).get("state").getAsString().equals("stopped")) throw new IllegalStateException("Stop failed");
                var restarted = consoleRun(console, args[0], "assert 'counter' not in globals()\nprint('restart probe passed')\n", "script");
                if (!restarted.toString().contains("restart probe passed")) throw new IllegalStateException("Worker restart failed");
            }
            System.out.println("PASS: managed CPython console, persistent variables, context/data/ops, operators, timers, traceback, completion, stop and restart");
        }
        if (Files.exists(DesktopPythonRuntime.connectionFile(file)))
            throw new IllegalStateException("Desktop Python credentials were not removed");
        try (Workspace reopened = Workspace.readFromFS(file.toFile(), null)) {
            if (reopened.getModElements().stream().noneMatch(element -> element.getName().equals("python_live_item")))
                throw new IllegalStateException("Attached Python edit was not persisted");
        }
        System.out.println("Native Python product smoke passed; fixture: " + root);
    }

    private static JsonObject consoleRun(PythonConsoleService console, String python, String source, String mode) throws Exception {
        return consoleRun(console, python, source, mode, false);
    }

    private static JsonObject consoleRun(PythonConsoleService console, String python, String source, String mode, boolean allowFailure) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("operation", "execute"); request.addProperty("python", python);
        request.addProperty("source", source); request.addProperty("mode", mode); request.addProperty("filename", "probe.py");
        console.execute(request);
        waitUntil(() -> java.util.Set.of("ready", "failed").contains(console.status(0).get("state").getAsString()), 30000);
        var result = console.status(0);
        if (result.get("state").getAsString().equals("failed")) throw new IllegalStateException(result.toString());
        if (!allowFailure && !result.get("lastResult").getAsString().equals("succeeded"))
            throw new IllegalStateException("Python script failed: " + result);
        return result;
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeout) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeout);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Python workbench probe timed out");
            Thread.sleep(20);
        }
    }
}

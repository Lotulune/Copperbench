package dev.copperbench.agent;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

final class ProfessionalAgentTestSupport {
    static String classpath() throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        for (Class<?> type : List.of(ProfessionalAgentFixtureCli.class, AgentProcess.class, Gson.class))
            paths.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        return String.join(java.io.File.pathSeparator, paths);
    }
    static Path wrapper(Path root) throws Exception {
        Path file = root.resolve(AgentProcess.windows() ? "agent-fixture.ps1" : "agent-fixture.sh");
        String java = ProfessionalAgentFixtureCli.java(), cp = classpath(), name = ProfessionalAgentFixtureCli.class.getName();
        Files.writeString(file, AgentProcess.windows()
                ? "& '" + java.replace("'", "''") + "' '-cp' '" + cp.replace("'", "''") + "' '" + name + "' 'codex' @args\nexit $LASTEXITCODE\n"
                : "#!/bin/sh\nexec '" + java.replace("'", "'\"'\"'") + "' -cp '" + cp.replace("'", "'\"'\"'") + "' " + name + " codex \"$@\"\n");
        if (!AgentProcess.windows()) assertTrue(file.toFile().setExecutable(true)); return file;
    }
    static JsonObject await(AgentChatService service, Predicate<JsonObject> ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos(); JsonObject latest;
        do { latest = service.handle(AgentJson.object("operation", "status")); if (ready.test(latest)) return latest; Thread.sleep(20); }
        while (System.nanoTime() < deadline);
        fail("Agent fixture did not settle: " + latest); return latest;
    }
}

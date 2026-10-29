package dev.copperbench.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentProcessTest {
    @TempDir Path workspace;

    @Test void closingAProcessAlsoTerminatesItsObservedChild() throws Exception {
        ProcessHandle child;
        try (AgentProcess process = new AgentProcess(new ProcessBuilder(FakeAgentCli.java(), "-cp", AgentChatServiceTest.fixtureClasspath(),
                FakeAgentCli.class.getName(), "tree").directory(workspace.toFile()))) {
            CompletableFuture<String> pid = CompletableFuture.supplyAsync(() -> {
                try { return new BufferedReader(new InputStreamReader(process.process.getInputStream(), StandardCharsets.UTF_8)).readLine(); }
                catch (IOException failure) { throw new CompletionException(failure); }
            });
            child = ProcessHandle.of(Long.parseLong(pid.get(10, TimeUnit.SECONDS))).orElseThrow();
            assertTrue(child.isAlive());
            assertTrue(process.hasLiveChildren());
        }
        child.onExit().get(5, TimeUnit.SECONDS); assertFalse(child.isAlive());
    }

    @Test void oversizedProtocolLineFailsInsteadOfSilentlyDroppingItsTail() {
        byte[] input = ("x".repeat(AgentProcess.MAX_LINE + 1) + "\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> AgentProcess.lines(new ByteArrayInputStream(input), ignored -> fail("Must not deliver partial JSON")));
    }

    @Test void windowsWrapperArgumentsCannotExpandShellVariables() {
        if (!AgentProcess.windows()) return;
        assertThrows(IllegalArgumentException.class, () -> AgentProcess.command(Path.of("C:/fake/cli.cmd"), List.of("%PRIVATE_TOKEN%")));
        List<String> command = AgentProcess.command(Path.of("C:/fake space/cli.cmd"), List.of("--resume", "id-123"));
        assertEquals("cmd.exe", command.getFirst()); assertTrue(command.getLast().contains("fake space"));
    }
}

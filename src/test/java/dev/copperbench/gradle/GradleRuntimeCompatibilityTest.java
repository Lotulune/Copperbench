package dev.copperbench.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GradleRuntimeCompatibilityTest {
    @TempDir Path temp;

    @Test void healthyRuntimeKeepsUserOptionsAndCachesProbe() throws Exception {
        Path java = Files.writeString(temp.resolve("java.exe"), "fixture");
        Map<String, String> environment = new HashMap<>(Map.of("JAVA_TOOL_OPTIONS", "-Duser.language=en"));
        var original = Map.copyOf(environment);
        var cache = new HashMap<String, Boolean>();
        AtomicInteger probes = new AtomicInteger();
        GradleRuntimeCompatibility.Probe probe = (_, _) -> { probes.incrementAndGet(); return true; };
        GradleRuntimeCompatibility.configure(java, environment, _ -> fail("Healthy JDK should not warn"), probe, cache);
        GradleRuntimeCompatibility.configure(java, environment, _ -> fail("Healthy JDK should not warn"), probe, cache);
        assertEquals(original, environment);
        assertEquals(1, probes.get());
    }

    @Test void verifiesTcpBeforeChangingOnlyChildEnvironment() throws Exception {
        Path java = Files.writeString(temp.resolve("java with space.exe"), "fixture");
        var environment = new HashMap<>(Map.of("JAVA_TOOL_OPTIONS", "-Duser.language=en"));
        var messages = new ArrayList<String>();
        AtomicInteger probes = new AtomicInteger();
        GradleRuntimeCompatibility.configure(java, environment, messages::add, (_, candidate) -> {
            probes.incrementAndGet();
            return candidate.get("JAVA_TOOL_OPTIONS").contains("-Djdk.net.unixdomain.tmpdir=\"");
        }, new HashMap<>());
        assertEquals(2, probes.get());
        assertTrue(environment.get("JAVA_TOOL_OPTIONS").startsWith("-Duser.language=en "));
        assertTrue(environment.get("JAVA_TOOL_OPTIONS").endsWith("java with space.exe\""));
        assertTrue(messages.getFirst().startsWith("GRADLE_IPC_TCP_FALLBACK:"));
        assertEquals("fixture", Files.readString(java));
    }

    @Test void failedTcpDoesNotMutateEnvironmentOrCacheSuccess() throws Exception {
        Path java = Files.writeString(temp.resolve("java.exe"), "fixture");
        var environment = new HashMap<String, String>();
        var cache = new HashMap<String, Boolean>();
        assertThrows(GradleRuntimeCompatibility.LoopbackUnavailableException.class, () ->
                GradleRuntimeCompatibility.configure(java, environment, _ -> {}, (_, _) -> false, cache));
        assertTrue(environment.isEmpty());
        assertTrue(cache.isEmpty());
    }

    @Test @EnabledOnOs(OS.WINDOWS) void bundledJdkCanOpenSelectorWithoutCallerWorkarounds() throws Exception {
        var environment = new HashMap<>(System.getenv());
        for (String option : new String[]{"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) environment.remove(option);
        for (int release : new int[]{21, 25}) {
            var childEnvironment = new HashMap<>(environment);
            Path javaHome = dev.copperbench.generator.BundledJdkLocator.locate(Path.of("."), release);
            GradleRuntimeCompatibility.configure(javaHome, childEnvironment, _ -> {});
        }
    }
}

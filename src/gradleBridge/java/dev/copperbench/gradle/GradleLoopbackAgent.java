package dev.copperbench.gradle;

import java.nio.file.Files;
import java.nio.file.Path;

/** Applies the already-probed Windows pipe fallback before a Tooling API daemon creates its first selector. */
public final class GradleLoopbackAgent {
    private GradleLoopbackAgent() {}

    public static void premain(String ignored) {
        Path executable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable)) throw new IllegalStateException("Windows daemon Java executable is missing");
        System.setProperty("jdk.net.unixdomain.tmpdir", executable.toString());
    }
}

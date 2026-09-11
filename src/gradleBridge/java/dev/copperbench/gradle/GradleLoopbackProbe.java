package dev.copperbench.gradle;

import java.io.IOException;
import java.nio.channels.Selector;

/** Runs in the workspace JDK before Gradle, so an IPC failure cannot rerun a user's build. */
public final class GradleLoopbackProbe {
    private GradleLoopbackProbe() {}

    public static void main(String[] arguments) {
        try (Selector ignored = Selector.open()) {
            System.out.println("GRADLE_LOOPBACK_READY");
        } catch (IOException exception) {
            System.out.println("GRADLE_LOOPBACK_UNAVAILABLE");
            System.exit(2);
        }
    }
}

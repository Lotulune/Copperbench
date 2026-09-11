package dev.copperbench.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimePlatformTest {
    @Test void detectsWindowsAndLinuxX64Layouts() {
        RuntimePlatform windows = RuntimePlatform.detect("Windows 11", "amd64");
        assertEquals(RuntimePlatform.OperatingSystem.WINDOWS, windows.operatingSystem());
        assertEquals(RuntimePlatform.Architecture.X86_64, windows.architecture());
        assertEquals("jdk/jdk21_win_64", windows.sourceJavaHome(21));
        assertEquals("jdk/jbr25_win_64", windows.sourceJavaHome(25));
        assertEquals("java.exe", windows.javaExecutableName());

        RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
        assertTrue(linux.isLinuxX64());
        assertEquals("jdk/jdk21_linux_64", linux.sourceJavaHome(17));
        assertEquals("jdk/jdk21_linux_64", linux.sourceJavaHome(21));
        assertEquals("jdk/jbr25_linux_64", linux.sourceJavaHome(25));
        assertEquals("java", linux.javaExecutableName());
    }

    @Test void unsupportedLinuxArchitectureDoesNotInventBundledPaths() {
        RuntimePlatform arm = RuntimePlatform.detect("Linux", "aarch64");
        assertFalse(arm.isLinuxX64());
        assertNull(arm.sourceJavaHome(21));
        assertNull(arm.sourceJavaHome(25));
    }
}

package dev.copperbench.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JcefPythonBridgeTransportTest {
    @Test void onlyThePackagedWorkbenchUrlCanRequestExecution() {
        assertTrue(JcefPythonBridgeTransport.trustedUrl("http://mcreator/copperbench/ui/index.html"));
        assertTrue(JcefPythonBridgeTransport.trustedUrl("http://mcreator/copperbench/ui/index.html#python"));
        for (String url : new String[]{"http://mcreator.evil/copperbench/ui/index.html",
                "http://evil@mcreator/copperbench/ui/index.html", "http://mcreator/other.html",
                "https://mcreator/copperbench/ui/index.html", "file:///index.html", "http://localhost:5173/"})
            assertFalse(JcefPythonBridgeTransport.trustedUrl(url), url);
        assertFalse(JcefPythonBridgeTransport.trustedUrl(null));
    }

    @Test void bootstrapExposesOnlyTheScopedPythonTransport() {
        String script = JcefPythonBridgeTransport.generateBootstrapScript();
        assertTrue(script.contains("__COPPERBENCH_PYTHON_HOST__"));
        assertTrue(script.contains("copperbench:python:"));
        assertFalse(script.contains("token"));
    }
}

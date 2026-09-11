package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Bounded JUnit/GameTest XML parsing; process success alone never proves that tests ran. */
public final class GameTestReport {
    private GameTestReport() {}

    public static JsonObject read(Path report, Instant startedAt, int minimumTests) {
        JsonObject result = empty("GAMETEST_REPORT_MISSING");
        result.addProperty("reportPath", report.toAbsolutePath().normalize().toString());
        try {
            WorkspaceExecutionSnapshot.rejectLinks(report);
            if (!Files.isRegularFile(report)) return result;
            if (Files.getLastModifiedTime(report).toInstant().isBefore(startedAt.minusSeconds(2)))
                return reason(result, "GAMETEST_REPORT_STALE");
            if (Files.size(report) > 8 * 1024 * 1024) return reason(result, "GAMETEST_REPORT_INVALID");
            byte[] bytes;
            try (var input = Files.newInputStream(report, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(8 * 1024 * 1024 + 1);
            }
            if (bytes.length > 8 * 1024 * 1024) return reason(result, "GAMETEST_REPORT_INVALID");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            var document = builder.parse(new java.io.ByteArrayInputStream(bytes));
            String root = document.getDocumentElement().getTagName();
            if (!root.equals("testsuite") && !root.equals("testsuites")) return reason(result, "GAMETEST_REPORT_INVALID");
            var nodes = document.getElementsByTagName("testcase");
            if (nodes.getLength() > 10_000) return reason(result, "GAMETEST_REPORT_INVALID");
            int failed = 0, skipped = 0, frameworkTests = 0, frameworkExecuted = 0;
            JsonArray cases = new JsonArray();
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                if (element.getAttribute("name").isBlank() || !element.getParentNode().getNodeName().equals("testsuite")
                        || element.getElementsByTagName("testcase").getLength() > 0)
                    return reason(result, "GAMETEST_REPORT_INVALID");
                boolean failure = element.getElementsByTagName("failure").getLength() > 0
                        || element.getElementsByTagName("error").getLength() > 0;
                boolean skip = element.getElementsByTagName("skipped").getLength() > 0;
                if (failure) failed++; else if (skip) skipped++;
                // Minecraft 26.x registers this built-in self-check even when no mod test was registered.
                boolean framework = element.getAttribute("name").equals("minecraft:always_pass");
                if (framework) { frameworkTests++; if (failure || !skip) frameworkExecuted++; }
                JsonObject test = new JsonObject();
                test.addProperty("name", bounded(element.getAttribute("name"), 512));
                test.addProperty("className", bounded(element.getAttribute("classname"), 512));
                test.addProperty("status", failure ? "failed" : skip ? "skipped" : "passed");
                test.addProperty("scope", framework ? "framework" : "acceptance");
                if (failure) {
                    var failures = element.getElementsByTagName("failure");
                    Element detail = (Element) (failures.getLength() > 0 ? failures.item(0) : element.getElementsByTagName("error").item(0));
                    test.addProperty("message", bounded((detail.getAttribute("message") + "\n" + detail.getTextContent()).strip(), 2048));
                }
                cases.add(test);
            }
            // A malformed producer must not hide failures only in suite attributes.
            boolean reportedFailure = false;
            var suites = document.getElementsByTagName("*");
            for (int index = 0; index < suites.getLength(); index++) {
                Element suite = (Element) suites.item(index);
                if (!suite.getTagName().equals("testsuite") && !suite.getTagName().equals("testsuites")) continue;
                for (String field : new String[]{"errors", "failures"}) {
                    if (suite.hasAttribute(field) && Integer.parseInt(suite.getAttribute(field)) > 0) reportedFailure = true;
                }
            }
            int discovered = nodes.getLength(), executed = discovered - skipped;
            result.addProperty("discovered", discovered); result.addProperty("executed", executed);
            result.addProperty("passed", executed - failed); result.addProperty("failed", failed);
            result.addProperty("skipped", skipped); result.add("cases", cases);
            result.addProperty("frameworkTests", frameworkTests);
            result.addProperty("acceptanceExecuted", executed - frameworkExecuted);
            result.addProperty("reportSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            if (failed > 0 || reportedFailure) return reason(result, "GAMETEST_FAILED");
            if (executed - frameworkExecuted < Math.max(1, minimumTests)) return reason(result, "GAMETEST_NO_TESTS");
            result.addProperty("status", "passed"); result.addProperty("reasonCode", "GAMETEST_PASSED");
            return result;
        } catch (Exception exception) {
            result.addProperty("detail", bounded(exception.getMessage(), 512));
            return reason(result, "GAMETEST_REPORT_INVALID");
        }
    }

    public static JsonObject empty(String code) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", "1.0"); result.addProperty("status", "failed");
        result.addProperty("reasonCode", code);
        for (String count : new String[]{"discovered", "executed", "passed", "failed", "skipped", "frameworkTests", "acceptanceExecuted"}) result.addProperty(count, 0);
        result.add("cases", new JsonArray());
        return result;
    }
    public static JsonObject reason(JsonObject result, String code) {
        result.addProperty("status", "failed"); result.addProperty("reasonCode", code); return result;
    }
    private static String bounded(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}

package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Bounded JUnit/GameTest XML parsing; process success alone never proves that tests ran. */
public final class GameTestReport {
    private record TestIdentity(String className, String name) {}

    private GameTestReport() {}

    public static JsonObject read(Path report, Instant startedAt, int minimumTests) {
        return parse(report, startedAt, minimumTests, ignored -> { });
    }

    private record RawCase(String className, String name, String status, boolean framework) {}

    /** Keeps the frozen v1.0 report separate from negotiated coverage information. */
    public record Assessment(JsonObject verification, JsonObject coverage) {
        public Assessment { verification = verification.deepCopy(); coverage = coverage.deepCopy(); }
    }

    /** Checks one raw report against the manifest selected before the process was started. */
    public static Assessment assess(Path report, Instant startedAt, RequiredCaseManifest manifest) {
        return assess(report, startedAt, manifest.minimumTests(), manifest);
    }

    /** Captures precise machine reasons for legacy thresholds without inventing a named manifest. */
    public static Assessment assess(Path report, Instant startedAt, int minimumTests) {
        return assess(report, startedAt, minimumTests, null);
    }

    private static Assessment assess(Path report, Instant startedAt, int minimumTests, RequiredCaseManifest manifest) {
        List<RawCase> rows = new ArrayList<>();
        JsonObject result = parse(report, startedAt, minimumTests, rows::add);
        Set<RequiredCaseManifest.Identity> seen = new HashSet<>(), executed = new HashSet<>(), passed = new HashSet<>();
        JsonArray duplicates = new JsonArray(), missing = new JsonArray();
        for (RawCase row : rows) {
            if (row.framework()) continue;
            var identity = manifest == null ? new RequiredCaseManifest.Identity(row.className(), row.name())
                    : manifest.identity(row.className(), row.name());
            if (!seen.add(identity)) duplicates.add(identity.json());
            if (!row.status().equals("skipped")) executed.add(identity);
            if (row.status().equals("passed")) passed.add(identity);
        }
        if (manifest != null)
            manifest.cases().stream().filter(test -> !passed.contains(test)).forEach(test -> missing.add(test.json()));
        String reason = result.get("reasonCode").getAsString();
        if (!reason.equals("GAMETEST_FAILED") && !duplicates.isEmpty()) {
            reason(result, "GAMETEST_REPORT_INVALID");
            reason = "GAMETEST_DUPLICATE_TESTS";
        } else if ((reason.equals("GAMETEST_PASSED") || reason.equals("GAMETEST_NO_TESTS")) && !missing.isEmpty()) {
            reason(result, "GAMETEST_NO_TESTS");
            reason = "GAMETEST_REQUIRED_CASES_MISSING";
        } else if (reason.equals("GAMETEST_NO_TESTS") && !executed.isEmpty()) {
            reason = "GAMETEST_INSUFFICIENT_TESTS";
        }
        if (manifest != null && !reason.equals(result.get("reasonCode").getAsString())) result.addProperty("detail", reason);
        JsonObject coverage = new JsonObject();
        if (manifest != null) coverage.addProperty("manifestSha256", manifest.sha256());
        coverage.addProperty("required", manifest == null ? 0 : manifest.cases().size());
        coverage.addProperty("minimumTests", minimumTests);
        coverage.addProperty("uniqueExecuted", executed.size());
        coverage.addProperty("shortfall", Math.max(0, minimumTests - executed.size()));
        coverage.addProperty("reasonCode", reason);
        coverage.addProperty("status", result.get("status").getAsString());
        coverage.add("duplicates", duplicates);
        coverage.add("missingCases", missing);
        return new Assessment(result, coverage);
    }

    private static JsonObject parse(Path report, Instant startedAt, int minimumTests, Consumer<RawCase> rawCases) {
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
            int failed = 0, skipped = 0, frameworkTests = 0;
            JsonArray cases = new JsonArray();
            Set<TestIdentity> identities = new HashSet<>();
            Set<TestIdentity> executedBusiness = new HashSet<>();
            boolean duplicate = false;
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                if (element.getAttribute("name").isBlank() || !element.getParentNode().getNodeName().equals("testsuite")
                        || element.getElementsByTagName("testcase").getLength() > 0)
                    return reason(result, "GAMETEST_REPORT_INVALID");
                // Use the full producer identity, before display truncation. Suites and attempts cannot add coverage.
                TestIdentity identity = new TestIdentity(element.getAttribute("classname"), element.getAttribute("name"));
                if (!identities.add(identity)) duplicate = true;
                boolean failure = element.getElementsByTagName("failure").getLength() > 0
                        || element.getElementsByTagName("error").getLength() > 0;
                boolean skip = element.getElementsByTagName("skipped").getLength() > 0;
                if (failure) failed++; else if (skip) skipped++;
                // Minecraft 26.x registers this built-in self-check even when no mod test was registered.
                boolean framework = identity.name().equals("minecraft:always_pass");
                if (framework) frameworkTests++;
                else if (failure || !skip) executedBusiness.add(identity);
                rawCases.accept(new RawCase(identity.className(), identity.name(), failure ? "failed" : skip ? "skipped" : "passed", framework));
                JsonObject test = new JsonObject();
                test.addProperty("name", bounded(identity.name(), 512));
                test.addProperty("className", bounded(identity.className(), 512));
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
            int acceptanceExecuted = executedBusiness.size();
            result.addProperty("acceptanceExecuted", acceptanceExecuted);
            result.addProperty("reportSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            int required = Math.max(1, minimumTests);
            if (duplicate) result.addProperty("detail", "GAMETEST_DUPLICATE_TESTS: The report repeats "
                    + (discovered - identities.size()) + " testcase identities. Repeated identities cannot add business coverage; "
                    + "rerun acceptance as a separate task.");
            if (failed > 0 || reportedFailure) return reason(result, "GAMETEST_FAILED");
            // Reject the report even if its distinct cases would otherwise reach the minimum.
            // Keep the v1.0 shape and broad invalid-report code; all raw rows remain available for diagnosis.
            if (duplicate) return reason(result, "GAMETEST_REPORT_INVALID");
            if (acceptanceExecuted < required) {
                String preciseReason = acceptanceExecuted == 0 ? "GAMETEST_NO_TESTS" : "GAMETEST_INSUFFICIENT_TESTS";
                result.addProperty("detail", preciseReason + ": At least " + required + " business tests must execute; observed "
                        + acceptanceExecuted + ". Skipped and framework cases do not count toward this minimum.");
                // Preserve the v1.0 code/shape; detail distinguishes zero from insufficient execution.
                return reason(result, "GAMETEST_NO_TESTS");
            }
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

package dev.copperbench.generator;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The independent Python consumer and product parser use the same original-identity examples. */
class PortableEvidenceIdentityVectorsTest {
    @TempDir Path root;

    @TestFactory Stream<DynamicTest> rawIdentityAndRequiredCaseProtocolAgree() throws Exception {
        var vectors = JsonParser.parseString(Files.readString(Path.of("tools/portable-evidence/identity-vectors.json")))
                .getAsJsonObject().getAsJsonArray("vectors");
        return vectors.asList().stream().map(value -> {
            var vector = value.getAsJsonObject();
            String name = vector.get("name").getAsString();
            return DynamicTest.dynamicTest(name, () -> {
                Path report = root.resolve(name + ".xml");
                Files.writeString(report, vector.get("xml").getAsString());
                String code;
                int executed;
                if (vector.has("requiredCases")) {
                    var manifest = RequiredCaseManifest.parse(vector.getAsJsonObject("requiredCases"));
                    var result = GameTestReport.assess(report, Instant.now().minusSeconds(1), manifest);
                    code = result.coverage().get("reasonCode").getAsString();
                    executed = result.coverage().get("uniqueExecuted").getAsInt();
                } else {
                    var result = GameTestReport.read(report, Instant.now().minusSeconds(1), vector.get("minimumTests").getAsInt());
                    code = result.get("reasonCode").getAsString();
                    executed = result.get("acceptanceExecuted").getAsInt();
                }
                assertEquals(vector.get("expectedCoreCode").getAsString(), code);
                if (vector.has("acceptanceExecuted"))
                    assertEquals(vector.get("acceptanceExecuted").getAsInt(), executed);
            });
        });
    }
}

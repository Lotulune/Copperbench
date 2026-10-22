package dev.copperbench.shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Stage9ScaleEvidenceTest {

	@TempDir Path directory;

	@Test void thresholdFailureKeepsEveryCollectedSampleAndOriginalAssertion() throws Exception {
		Path output = directory.resolve("nested/scale.json");
		Stage9ScaleEvidence evidence = new Stage9ScaleEvidence(output);
		AssertionError failure = new AssertionError("P95 exceeded 300 ms: 400.9");
		assertSame(failure, assertThrows(AssertionError.class, () -> evidence.capture(() -> {
			evidence.samples("elementFilterSamplesMillis").add(90.0);
			evidence.samples("procedureSearchSamplesMillis");
			evidence.phase("reference-refresh-samples");
			for (int index = 0; index < 20; index++) {
				evidence.sample(index);
				evidence.samples("referenceRefreshSamplesMillis").add(index < 18 ? 100.0 : 400.9);
			}
			evidence.data.addProperty("referenceRefreshP95Millis", 400.9);
			throw failure;
		})));
		JsonObject result = read(output);
		assertFalse(result.get("passed").getAsBoolean());
		assertFalse(result.get("completed").getAsBoolean());
		assertEquals(20, result.getAsJsonArray("referenceRefreshSamplesMillis").size());
		assertEquals(400.9, result.get("referenceRefreshP95Millis").getAsDouble());
		assertEquals(0, result.getAsJsonArray("procedureSearchSamplesMillis").size());
		assertFalse(result.has("procedureSearchP95Millis"));
		assertEquals("reference-refresh-samples", result.get("lastPhase").getAsString());
		assertEquals(19, result.get("sampleIndex").getAsInt());
		assertEquals(failure.getMessage(), result.getAsJsonObject("failure").get("message").getAsString());
		assertTrue(result.getAsJsonObject("failure").get("stackTrace").getAsString().contains("AssertionError"));
		assertTrue(result.getAsJsonArray("phaseContext").size() >= 2);
	}

	@Test void interruptedSampleKeepsPartialSeriesWithoutInventingAValue() throws Exception {
		Path output = directory.resolve("scale.json");
		Stage9ScaleEvidence evidence = new Stage9ScaleEvidence(output);
		InterruptedException failure = new InterruptedException("renderer wait interrupted");
		assertSame(failure, assertThrows(InterruptedException.class, () -> evidence.capture(() -> {
			evidence.phase("reference-refresh-samples");
			evidence.samples("referenceRefreshSamplesMillis").addAll(List.of(100.0, 200.0));
			evidence.sample(2);
			throw failure;
		})));
		JsonObject result = read(output);
		assertEquals(2, result.getAsJsonArray("referenceRefreshSamplesMillis").size());
		assertEquals(2, result.get("sampleIndex").getAsInt());
		assertFalse(result.has("referenceRefreshP95Millis"));
		assertFalse(result.get("passed").getAsBoolean());
	}

	@Test void setupFailureStillWritesEnvironmentAndFailure() throws Exception {
		Path output = directory.resolve("scale.json");
		Stage9ScaleEvidence evidence = new Stage9ScaleEvidence(output);
		assertThrows(IllegalStateException.class, () -> evidence.capture(() -> {
			throw new IllegalStateException("browser unavailable");
		}));
		JsonObject result = read(output);
		assertEquals("setup", result.get("lastPhase").getAsString());
		assertEquals(System.getProperty("os.name"), result.get("osName").getAsString());
		assertTrue(result.has("environment"));
		assertFalse(result.getAsJsonObject("environment").has("GITHUB_TOKEN"));
	}

	@Test void successWritesMeasuredValuesAndCompletion() throws Exception {
		Path output = directory.resolve("scale.json");
		Stage9ScaleEvidence evidence = new Stage9ScaleEvidence(output);
		evidence.capture(() -> evidence.samples("referenceRefreshSamplesMillis").add(123.4));
		JsonObject result = read(output);
		assertTrue(result.get("passed").getAsBoolean());
		assertTrue(result.get("completed").getAsBoolean());
		assertFalse(result.has("failure"));
		assertEquals(123.4, result.getAsJsonArray("referenceRefreshSamplesMillis").get(0).getAsDouble());
	}

	@Test void writeFailureDoesNotReplaceOriginalFailureAndCannotPassSilently() {
		AssertionError original = new AssertionError("original gate failure");
		// A directory cannot be overwritten with JSON on either Windows or Linux.
		Stage9ScaleEvidence evidence = new Stage9ScaleEvidence(directory);
		assertSame(original, assertThrows(AssertionError.class, () -> evidence.capture(() -> { throw original; })));
		assertEquals(1, original.getSuppressed().length);
		assertInstanceOf(IOException.class, original.getSuppressed()[0]);
		assertThrows(IOException.class, () -> new Stage9ScaleEvidence(directory).capture(() -> {}));
	}

	private static JsonObject read(Path output) throws IOException {
		return JsonParser.parseString(Files.readString(output)).getAsJsonObject();
	}
}

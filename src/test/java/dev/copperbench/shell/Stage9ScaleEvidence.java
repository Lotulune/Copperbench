package dev.copperbench.shell;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.management.OperatingSystemMXBean;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only recorder. No file I/O or JVM diagnostics are collected inside a timed sample. */
final class Stage9ScaleEvidence {

	@FunctionalInterface interface CheckedRun { void run() throws Exception; }

	final JsonObject data = new JsonObject();
	private final Path output;
	private final long started = System.nanoTime();
	private final Map<String, List<Double>> samples = new LinkedHashMap<>();
	private final List<JsonObject> phases = new ArrayList<>();
	private String phase = "setup";
	private int sampleIndex = -1;

	Stage9ScaleEvidence(Path output) {
		this.output = output;
		data.addProperty("schemaVersion", "1.0");
		data.addProperty("kind", "stage9-native-jcef-scale-gate");
		data.addProperty("platform", "windows");
		data.addProperty("host", "real-jcef-production-shell");
		data.addProperty("startedAt", Instant.now().toString());
		for (String property : List.of("os.name", "os.version", "os.arch", "java.version", "java.runtime.version",
				"java.vendor", "java.vm.name")) data.addProperty(property, System.getProperty(property));
		// Retain the original evidence keys for existing consumers.
		data.addProperty("osName", System.getProperty("os.name"));
		data.addProperty("osVersion", System.getProperty("os.version"));
		data.addProperty("osArch", System.getProperty("os.arch"));
		data.addProperty("javaVersion", System.getProperty("java.version"));
		data.addProperty("processorIdentifier", System.getenv("PROCESSOR_IDENTIFIER"));
		data.addProperty("logicalProcessors", Runtime.getRuntime().availableProcessors());
		if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os)
			data.addProperty("totalPhysicalMemoryMiB", os.getTotalMemorySize() / (1024L * 1024L));
		JsonObject environment = new JsonObject();
		// Deliberately allowlist provenance fields; never serialize the entire environment.
		for (String key : List.of("GITHUB_SHA", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB",
				"RUNNER_OS", "RUNNER_ARCH", "RUNNER_ENVIRONMENT", "ImageOS", "ImageVersion"))
			environment.addProperty(key, System.getenv(key));
		data.add("environment", environment);
		data.addProperty("sampleTiming", "performance.now through DOM completion and two requestAnimationFrame callbacks");
		data.addProperty("percentileMethod", "nearest-rank ceil(n * 0.95), one warmup excluded per sampled path");
	}

	List<Double> samples(String metric) {
		return samples.computeIfAbsent(metric, ignored -> new ArrayList<>());
	}

	void phase(String name) {
		phase = name;
		sampleIndex = -1;
		JsonObject context = new JsonObject();
		context.addProperty("phase", name);
		context.addProperty("elapsedMillis", (System.nanoTime() - started) / 1_000_000.0);
		context.addProperty("heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
		context.addProperty("gcCount", ManagementFactory.getGarbageCollectorMXBeans().stream()
				.mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum());
		context.addProperty("gcMillis", ManagementFactory.getGarbageCollectorMXBeans().stream()
				.mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum());
		if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os)
			context.addProperty("processCpuNanos", os.getProcessCpuTime());
		phases.add(context);
	}

	void sample(int index) { sampleIndex = index; }

	void capture(CheckedRun run) throws Exception {
		Throwable failure = null;
		try {
			run.run();
		} catch (Exception | Error thrown) {
			failure = thrown;
			throw thrown;
		} finally {
			data.addProperty("passed", failure == null);
			data.addProperty("completed", failure == null);
			data.addProperty("lastPhase", phase);
			data.addProperty("sampleIndex", sampleIndex);
			data.addProperty("sampleIndexBase", 0);
			if (failure != null) {
				JsonObject detail = new JsonObject();
				detail.addProperty("type", failure.getClass().getName());
				detail.addProperty("message", failure.getMessage());
				StringWriter trace = new StringWriter();
				failure.printStackTrace(new PrintWriter(trace));
				detail.addProperty("stackTrace", trace.toString());
				data.add("failure", detail);
			}
			try {
				phase("finished");
				var json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
				samples.forEach((metric, values) -> data.add(metric, json.toJsonTree(values)));
				data.add("phaseContext", json.toJsonTree(phases));
				data.addProperty("elapsedMillis", (System.nanoTime() - started) / 1_000_000.0);
				data.addProperty("generatedAt", Instant.now().toString());
				Files.createDirectories(output.toAbsolutePath().getParent());
				Files.writeString(output, json.toJson(data), StandardCharsets.UTF_8);
			} catch (Exception | Error writeFailure) {
				// The original assertion/timeout must remain the reported test failure.
				if (failure != null) failure.addSuppressed(writeFailure);
				else throw writeFailure;
			}
		}
	}
}

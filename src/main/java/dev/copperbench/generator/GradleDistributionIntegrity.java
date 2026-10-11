package dev.copperbench.generator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/** Reviewed Gradle distribution digests shared with the source and template integrity gates. */
public final class GradleDistributionIntegrity {

	private static final Properties DIGESTS = load();

	private GradleDistributionIntegrity() {}

	/** Finds the reviewed SHA-256 for an exact archive filename, without contacting a repository. */
	public static Optional<String> find(String archive) {
		return Optional.ofNullable(DIGESTS.getProperty("distribution." + archive));
	}

	/** Requires a reviewed digest before materializing a newly generated wrapper. */
	public static String require(String archive) {
		return find(archive).orElseThrow(() -> new IllegalArgumentException(
				"Gradle distribution has no reviewed checksum: " + archive));
	}

	/** Whether a wrapper JAR digest was independently checked against Gradle's published checksums. */
	public static boolean reviewedWrapper(String checksum) {
		return DIGESTS.stringPropertyNames().stream().filter(key -> key.startsWith("wrapper."))
				.anyMatch(key -> DIGESTS.getProperty(key).equals(checksum));
	}

	private static Properties load() {
		Properties result = new Properties();
		try (InputStream stream = GradleDistributionIntegrity.class.getResourceAsStream("gradle-integrity.properties")) {
			if (stream == null) throw new IOException("Gradle integrity manifest is missing");
			result.load(stream);
			for (String key : result.stringPropertyNames())
				if (!result.getProperty(key).matches("[0-9a-f]{64}"))
					throw new IOException("Invalid Gradle integrity digest: " + key);
			return result;
		} catch (IOException error) {
			throw new ExceptionInInitializerError(error);
		}
	}
}

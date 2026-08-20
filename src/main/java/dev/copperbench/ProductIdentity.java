package dev.copperbench;

import net.mcreator.Launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Distribution identity kept separate from the upstream compatibility namespace. */
public final class ProductIdentity {

	private static final Properties CONFIG = loadConfig();

	public static final String NAME = CONFIG.getProperty("product.name", "Copperbench");
	public static final String VERSION = CONFIG.getProperty("product.version", "0.1.0");
	public static final String ID = CONFIG.getProperty("product.id", "dev.copperbench.studio");
	public static final String PUBLISHER = CONFIG.getProperty("product.publisher", "Copperbench Contributors");
	public static final String UPSTREAM_NAME = "MCreator";
	public static final boolean IMPLICIT_NETWORK_SERVICES_ENABLED = false;

	private ProductIdentity() {
	}

	public static String displayVersion() {
		String coreVersion = Launcher.version != null ? Launcher.version.getFullString() : "unknown";
		return VERSION + " (MCreator core " + coreVersion + ")";
	}

	private static Properties loadConfig() {
		Properties properties = new Properties();
		try (InputStream stream = ProductIdentity.class.getResourceAsStream("/mcreator.conf")) {
			if (stream == null)
				throw new IllegalStateException("Missing /mcreator.conf");
			properties.load(stream);
			return properties;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load product identity", e);
		}
	}
}

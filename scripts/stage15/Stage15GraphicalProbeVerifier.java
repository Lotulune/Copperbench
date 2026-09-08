import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Verifies the machine-readable result emitted by the packaged graphical product shell. */
public final class Stage15GraphicalProbeVerifier {
	private Stage15GraphicalProbeVerifier() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2)
			throw new IllegalArgumentException(
					"Usage: Stage15GraphicalProbeVerifier.java <probe-json> <expected-workspace-root>");

		Path probe = Path.of(args[0]).toAbsolutePath().normalize();
		Path expectedWorkspace = Path.of(args[1]).toAbsolutePath().normalize();
		String raw = Files.readString(probe);
		JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
		require("1.0".equals(string(root, "schemaVersion")), "Unexpected probe schemaVersion");
		require("graphical-product-shell".equals(string(root, "probe")), "Unexpected probe type");
		require("ready".equals(string(root, "status")), "Graphical product shell did not reach ready state");
		require(root.get("jcefMainFrameLoaded").getAsBoolean(), "JCEF main frame did not load");
		require(!root.get("formalSupportClaim").getAsBoolean(), "CI smoke must not claim formal Linux support");
		require(expectedWorkspace.equals(Path.of(string(root, "workspaceRoot")).toAbsolutePath().normalize()),
				"Probe workspace root does not match the launched workspace");

		JsonObject platform = root.getAsJsonObject("platform");
		require("linux".equals(string(platform, "os")), "Probe did not run on Linux");
		require("x86_64".equals(string(platform, "arch")), "Probe did not run on Linux x86_64");
		JsonObject desktop = platform.getAsJsonObject("desktop");
		require("x11".equals(string(desktop, "sessionType")), "Xvfb smoke was not classified as X11");
		require("stage15-compatibility-target".equals(string(desktop, "certificationRole")),
				"X11 smoke has the wrong certification role");
		require(desktop.get("stage15CertificationPending").getAsBoolean(),
				"Graphical CI smoke must keep formal certification pending");

		JsonObject mcp = root.getAsJsonObject("mcp");
		require("listening".equals(string(mcp, "status")), "Desktop MCP did not reach listening state");
		require(string(mcp, "url").startsWith("http://127.0.0.1:"), "Desktop MCP is not loopback-only");
		require("workspace".equals(string(mcp, "permissionProfile")), "Unexpected Desktop MCP permission profile");
		require(mcp.get("tokenAvailable").getAsBoolean(), "Desktop MCP one-time token is not available to the UI");
		require(!raw.contains("Bearer ") && !raw.contains("tokenValue") && !raw.contains("authorization"),
				"Graphical probe leaked credential material");

		if (Files.getFileAttributeView(probe, PosixFileAttributeView.class) != null) {
			Set<PosixFilePermission> expected = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
			require(Files.getPosixFilePermissions(probe).equals(expected), "Graphical probe file must be mode 0600");
		}
		System.out.println("Stage 15 packaged X11 graphical probe verified: " + probe);
	}

	private static String string(JsonObject object, String property) {
		if (!object.has(property) || object.get(property).isJsonNull())
			throw new IllegalStateException("Missing probe property: " + property);
		return object.get(property).getAsString();
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new IllegalStateException(message);
	}
}

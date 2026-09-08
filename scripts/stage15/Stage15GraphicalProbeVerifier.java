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
		if (args.length != 2 && args.length != 3)
			throw new IllegalArgumentException(
					"Usage: Stage15GraphicalProbeVerifier.java <probe-json> <expected-workspace-root> [x11|wayland]");

		Path probe = Path.of(args[0]).toAbsolutePath().normalize();
		Path expectedWorkspace = Path.of(args[1]).toAbsolutePath().normalize();
		String expectedSession = args.length == 3 ? args[2] : "x11";
		if (!"x11".equals(expectedSession) && !"wayland".equals(expectedSession))
			throw new IllegalArgumentException("Expected desktop session must be x11 or wayland: " + expectedSession);
		String expectedRole = "wayland".equals(expectedSession)
				? "stage15-primary-target"
				: "stage15-compatibility-target";
		String raw = Files.readString(probe);
		JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
		require("1.0".equals(string(root, "schemaVersion")), "Unexpected probe schemaVersion");
		require("graphical-product-shell".equals(string(root, "probe")), "Unexpected probe type");
		require("ready".equals(string(root, "status")), "Graphical product shell did not reach ready state");
		require(root.get("jcefMainFrameLoaded").getAsBoolean(), "JCEF main frame did not load");
		require(!root.get("formalSupportClaim").getAsBoolean(),
				"Stage 15 graphical probe must not claim formal Linux support");
		require(expectedWorkspace.equals(Path.of(string(root, "workspaceRoot")).toAbsolutePath().normalize()),
				"Probe workspace root does not match the launched workspace");

		JsonObject platform = root.getAsJsonObject("platform");
		require("linux".equals(string(platform, "os")), "Probe did not run on Linux");
		require("x86_64".equals(string(platform, "arch")), "Probe did not run on Linux x86_64");
		JsonObject desktop = platform.getAsJsonObject("desktop");
		require(expectedSession.equals(string(desktop, "sessionType")),
				"Graphical probe desktop session does not match the expected Stage 15 target");
		require(expectedRole.equals(string(desktop, "certificationRole")),
				"Graphical probe has the wrong Stage 15 certification role");
		require(desktop.get("stage15CertificationPending").getAsBoolean(),
				"Stage 15 graphical probe must keep formal certification pending");

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
		System.out.println("Stage 15 packaged " + expectedSession + " graphical probe verified: " + probe);
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

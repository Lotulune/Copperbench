package dev.copperbench.shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.mcp.DesktopMcpRuntime;
import dev.copperbench.platform.DesktopSessionCapabilities;
import dev.copperbench.platform.PrivatePathPermissions;
import dev.copperbench.platform.RuntimePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GraphicalProductProbeTest {

	@TempDir Path temp;

	@Test void propertyWinsAndRelativeProbePathsResolveAgainstDistributionRoot() {
		Properties properties = new Properties();
		properties.setProperty(GraphicalProductProbe.RESULT_PROPERTY, "evidence/probe.json");
		Path resolved = GraphicalProductProbe.configuredResultPath(
				Map.of(GraphicalProductProbe.RESULT_ENVIRONMENT, temp.resolve("ignored.json").toString()),
				properties, temp);
		assertEquals(temp.resolve("evidence/probe.json").toAbsolutePath().normalize(), resolved);
	}

	@Test void absentOptInProducesNoConfiguredPath() {
		assertNull(GraphicalProductProbe.configuredResultPath(Map.of(), new Properties(), temp));
	}

	@Test void readyProbeIsStructuredPrivateAndNeverContainsTheMcpCredential() throws Exception {
		Path workspace = Files.createDirectories(temp.resolve("workspace"));
		Path result = temp.resolve("evidence/product-shell.json");
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		DesktopMcpRuntime.RuntimeState mcp = new DesktopMcpRuntime.RuntimeState("listening",
				"http://127.0.0.1:48123/mcp", workspaceId, UiCore.PermissionProfile.WORKSPACE,
				Instant.parse("2026-09-08T12:00:00Z"), true, null);
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
		DesktopSessionCapabilities capabilities = new DesktopSessionCapabilities(linux,
				DesktopSessionCapabilities.Session.WAYLAND, true, true);

		GraphicalProductProbe.writeResult(result, "ready", true, null, workspaceId, workspace, mcp, capabilities);

		String raw = Files.readString(result);
		JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
		assertEquals("graphical-product-shell", json.get("probe").getAsString());
		assertEquals("ready", json.get("status").getAsString());
		assertTrue(json.get("jcefMainFrameLoaded").getAsBoolean());
		assertFalse(json.get("formalSupportClaim").getAsBoolean());
		assertEquals(workspaceId.toString(), json.get("workspaceId").getAsString());
		assertEquals("wayland", json.getAsJsonObject("platform").getAsJsonObject("desktop")
				.get("sessionType").getAsString());
		assertEquals("listening", json.getAsJsonObject("mcp").get("status").getAsString());
		assertTrue(json.getAsJsonObject("mcp").get("tokenAvailable").getAsBoolean());
		assertFalse(raw.contains("Bearer "));
		assertFalse(raw.contains("tokenValue"));

		if (PrivatePathPermissions.posixSupported(result)) {
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
					Files.getPosixFilePermissions(result));
		}
	}

	@Test void rendererTerminationOverwritesReadyStateWithFailureFacts() throws Exception {
		Path workspace = Files.createDirectories(temp.resolve("workspace"));
		Path result = temp.resolve("probe.json");
		UUID workspaceId = UUID.randomUUID();
		DesktopMcpRuntime.RuntimeState mcp = new DesktopMcpRuntime.RuntimeState("listening",
				"http://127.0.0.1:48123/mcp", workspaceId, UiCore.PermissionProfile.WORKSPACE,
				Instant.parse("2026-09-08T12:00:00Z"), true, null);
		DesktopSessionCapabilities capabilities = new DesktopSessionCapabilities(
				RuntimePlatform.detect("Linux", "amd64"), DesktopSessionCapabilities.Session.X11, false, true);

		GraphicalProductProbe.writeResult(result, "ready", true, null, workspaceId, workspace, mcp, capabilities);
		GraphicalProductProbe.writeResult(result, "renderer_terminated", false, "CRASHED (9: boom)",
				workspaceId, workspace, mcp, capabilities);

		JsonObject json = JsonParser.parseString(Files.readString(result)).getAsJsonObject();
		assertEquals("renderer_terminated", json.get("status").getAsString());
		assertFalse(json.get("jcefMainFrameLoaded").getAsBoolean());
		assertEquals("CRASHED (9: boom)", json.get("rendererTerminationReason").getAsString());
	}
}

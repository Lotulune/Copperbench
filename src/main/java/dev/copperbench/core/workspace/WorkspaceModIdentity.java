package dev.copperbench.core.workspace;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.project.GradleProjectOperations;

import java.util.Locale;

/** Uses the same persisted mod ID as WorkspaceSettings and the native generator. */
public final class WorkspaceModIdentity {
	private WorkspaceModIdentity() { }

	public static String resolve(WorkspaceState workspace) {
		JsonObject document = workspace.upstreamDocument();
		if (GradleProjectOperations.KIND.equals(workspace.kind()))
			return adoptedProject(document.get("copperbench"));
		if (document.has("workspaceSettings"))
			return required(document.get("workspaceSettings"), "modid", "workspaceSettings.modid");
		if (document.has("copperbench"))
			return required(document.get("copperbench"), "modId", "copperbench.modId");
		// Older projection-only workspaces have no persisted settings. Keep their historical default.
		return validate(workspace.name().toLowerCase(Locale.ROOT).replace(' ', '_'));
	}

	/** An adopted project keeps the ID its own metadata states, where Fabric also allows hyphens. */
	private static String adoptedProject(JsonElement product) {
		JsonElement value = product != null && product.isJsonObject() ? product.getAsJsonObject().get("modId") : null;
		if (value == null || !value.isJsonPrimitive() || !value.getAsString().matches("[a-z][a-z0-9_-]{1,63}"))
			throw new IllegalArgumentException("The Gradle project's metadata does not state a mod ID");
		return value.getAsString();
	}

	private static String required(JsonElement container, String key, String location) {
		JsonElement value = container.isJsonObject() ? container.getAsJsonObject().get(key) : null;
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("Missing or invalid authoritative mod ID: " + location);
		return validate(value.getAsString());
	}

	public static String validate(String modId) {
		if (modId == null || !modId.matches("[a-z][a-z0-9_]{1,63}"))
			throw new IllegalArgumentException("Invalid modId: " + modId);
		return modId;
	}
}

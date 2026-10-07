package dev.copperbench.core.application;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetHealthReport;
import dev.copperbench.assets.AssetReference;
import dev.copperbench.assets.AssetReferenceGraph;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Diagnostic;
import dev.copperbench.core.diagnostics.AssetDiagnosticProjection;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.references.WorkspaceReferenceIndex;
import dev.copperbench.release.ElementCoverageCatalog;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Read-only asset observations shared by queries and post-mutation projections. */
final class AssetProjectionService {
	private static final Gson GSON = new Gson();
	private final WorkspaceReferenceIndex references;

	AssetProjectionService(WorkspaceReferenceIndex references) {
		this.references = references;
	}

	AssetReferenceGraph scan(Path root) {
		return new AssetWorkspaceService(root).referenceGraph();
	}

	Observation read(Path root, WorkspaceState state) {
		AssetReferenceGraph graph = scan(root);
		return new Observation(graph, health(graph, state),
				graph.diagnostics().stream().map(AssetDiagnosticProjection::project).toList());
	}

	record Observation(AssetReferenceGraph graph, AssetHealthReport health, List<Diagnostic> diagnostics) {
		JsonObject projection() {
			JsonObject projection = new JsonObject();
			projection.addProperty("schemaVersion", UiCore.SCHEMA_VERSION);
			projection.add("assets", GSON.toJsonTree(graph.assets().stream()
					.map(descriptor -> asset(descriptor, health.findById(descriptor.id()).orElseThrow())).toList()));
			projection.add("references", GSON.toJsonTree(graph.references().stream()
					.map(AssetProjectionService::assetReference).toList()));
			projection.add("diagnostics", GSON.toJsonTree(diagnostics));
			projection.add("health", GSON.toJsonTree(health.summary()));
			return projection;
		}

		// Preserve the established Workspace Health fingerprint input and ordering.
		String snapshot() {
			return GSON.toJson(graph.assets()) + GSON.toJson(graph.references());
		}
	}

	AssetHealthReport health(AssetReferenceGraph graph, WorkspaceState state) {
		JsonObject referenceProjection = references.projection(state, "");
		Map<String, Integer> resourceReferences = new HashMap<>();
		for (JsonElement raw : referenceProjection.getAsJsonArray("edges")) {
			JsonObject edge = raw.getAsJsonObject();
			if (!edge.has("kind") || !edge.get("kind").getAsString().equals("resource") || !edge.has("target")) continue;
			String target = edge.get("target").getAsString().trim().toLowerCase(Locale.ROOT);
			if (!target.isBlank()) resourceReferences.merge(target, 1, Integer::sum);
		}
		Set<String> textSignals = new LinkedHashSet<>();
		for (Element element : state.elements()) {
			textSignals.add(element.name().toLowerCase(Locale.ROOT));
			textSignals.add(element.displayName().toLowerCase(Locale.ROOT));
			collectWorkspaceTextSignals(element.values(), textSignals);
		}
		collectWorkspaceTextSignals(state.generator(), textSignals);
		collectWorkspaceTextSignals(state.upstreamDocument(), textSignals);
		collectWorkspaceTextSignals(state.registries(), textSignals);
		boolean workspaceUsageComplete = "mod".equals(state.kind()) && state.elements().stream()
				.allMatch(element -> ElementCoverageCatalog.isFirstParty(element.type()) && !element.type().equals("code")
						&& workspaceTextSignalsComplete(element.values()))
				&& workspaceTextSignalsComplete(state.generator())
				&& workspaceTextSignalsComplete(state.upstreamDocument());
		return graph.healthReport(resourceReferences, textSignals, workspaceUsageComplete);
	}

	private static boolean workspaceTextSignalsComplete(JsonElement value) {
		if (value == null || value.isJsonNull()) return true;
		if (value.isJsonPrimitive()) {
			return !value.getAsJsonPrimitive().isString() || value.getAsString().length() <= 512;
		}
		if (value.isJsonArray()) {
			for (JsonElement child : value.getAsJsonArray())
				if (!workspaceTextSignalsComplete(child)) return false;
			return true;
		}
		for (JsonElement child : value.getAsJsonObject().asMap().values())
			if (!workspaceTextSignalsComplete(child)) return false;
		return true;
	}

	private static void collectWorkspaceTextSignals(JsonElement value, Set<String> target) {
		if (value == null || value.isJsonNull()) return;
		if (value.isJsonPrimitive()) {
			if (value.getAsJsonPrimitive().isString()) {
				String text = value.getAsString().trim().toLowerCase(Locale.ROOT);
				if (!text.isBlank() && text.length() <= 512) target.add(text);
			}
			return;
		}
		if (value.isJsonArray()) {
			for (JsonElement child : value.getAsJsonArray()) collectWorkspaceTextSignals(child, target);
			return;
		}
		for (JsonElement child : value.getAsJsonObject().asMap().values()) collectWorkspaceTextSignals(child, target);
	}

	static JsonObject asset(AssetDescriptor descriptor, AssetHealthReport.Entry health) {
		JsonObject value = new JsonObject();
		value.addProperty("id", descriptor.id());
		value.addProperty("relativePath", descriptor.relativePath());
		value.addProperty("category", descriptor.category().name());
		value.addProperty("size", descriptor.size());
		value.addProperty("sha256", descriptor.sha256());
		value.addProperty("mediaType", descriptor.mediaType());
		value.addProperty("sourceAvailable", WorkspaceSourceService.supports(descriptor.relativePath(), descriptor.size()));
		value.addProperty("updatedAt", descriptor.updatedAt().toString());
		value.add("health", GSON.toJsonTree(health));
		return value;
	}

	private static JsonObject assetReference(AssetReference reference) {
		JsonObject value = new JsonObject();
		value.addProperty("sourceAssetId", reference.sourceAssetId());
		value.addProperty("sourcePath", reference.sourcePath());
		value.addProperty("sourcePointer", reference.sourcePointer());
		value.addProperty("rawValue", reference.rawValue());
		if (reference.expectedPrefix() == null) value.add("expectedPrefix", JsonNull.INSTANCE);
		else value.addProperty("expectedPrefix", reference.expectedPrefix());
		value.addProperty("targetPath", reference.targetPath());
		value.addProperty("targetAssetId", reference.targetAssetId());
		value.addProperty("resolution", reference.resolution());
		value.addProperty("resourceSource", reference.resourceSource());
		value.addProperty("resourceVersion", reference.resourceVersion());
		value.addProperty("kind", reference.kind().name());
		return value;
	}

}

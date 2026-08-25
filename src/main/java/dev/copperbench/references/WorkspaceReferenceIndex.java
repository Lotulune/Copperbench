/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.references;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.procedure.ProcedureIr;
import dev.copperbench.procedure.ProcedureIrCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Revision-aware reference graph that rescans only elements whose structured value fingerprint changed. */
public final class WorkspaceReferenceIndex {

	private static final Gson GSON = new Gson();
	private static final Pattern RESOURCE_LOCATION = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
	private static final Set<String> REFERENCE_KEYS = Set.of("elementid", "procedureid", "variableid", "tagid",
			"languagekey", "parent", "rewardfunction", "function", "loottable", "target", "reference", "ref");
	private final ProcedureIrCodec procedures = new ProcedureIrCodec();
	private final Map<UUID, WorkspaceIndex> workspaces = new ConcurrentHashMap<>();

	public JsonObject projection(WorkspaceState state, String target) {
		WorkspaceIndex index = workspaces.computeIfAbsent(state.id(), ignored -> new WorkspaceIndex());
		return index.update(state, target == null ? "" : target);
	}

	private final class WorkspaceIndex {
		private final Map<UUID, IndexedElement> elements = new LinkedHashMap<>();

		private synchronized JsonObject update(WorkspaceState state, String target) {
			Map<UUID, Element> current = new LinkedHashMap<>();
			state.elements().forEach(element -> current.put(element.id(), element));
			elements.keySet().removeIf(id -> !current.containsKey(id));
			Map<String, UUID> identities = new HashMap<>();
			for (Element element : current.values()) {
				identities.put(element.id().toString(), element.id());
				identities.put(element.name(), element.id());
				identities.put(element.displayName(), element.id());
			}
			for (String registry : List.of("variables", "tags", "languageKeys")) {
				for (JsonElement raw : state.registries().getAsJsonArray(registry)) {
					JsonObject entry = raw.getAsJsonObject();
					if (!entry.has("id")) continue;
					UUID id = UUID.fromString(entry.get("id").getAsString());
					identities.put(id.toString(), id);
					String name = registry.equals("languageKeys") ? string(entry, "key") : string(entry, "name");
					if (!name.isBlank()) identities.put(name, id);
				}
			}
			for (Element element : current.values()) {
				String fingerprint = fingerprint(element);
				IndexedElement indexed = elements.get(element.id());
				if (indexed == null || !indexed.fingerprint().equals(fingerprint))
					elements.put(element.id(), scan(element, fingerprint, identities));
			}
			return json(state, target, identities);
		}

		private JsonObject json(WorkspaceState state, String target, Map<String, UUID> identities) {
			JsonObject result = new JsonObject();
			result.addProperty("revision", state.revision());
			JsonArray nodes = new JsonArray();
			for (Element element : state.elements()) {
				JsonObject node = new JsonObject();
				node.addProperty("id", element.id().toString());
				node.addProperty("kind", "element");
				node.addProperty("type", element.type());
				node.addProperty("name", element.name());
				node.addProperty("displayName", element.displayName());
				nodes.add(node);
			}
			for (String registry : List.of("variables", "tags", "languageKeys")) {
				for (JsonElement raw : state.registries().getAsJsonArray(registry)) {
					JsonObject entry = raw.getAsJsonObject();
					if (!entry.has("id")) continue;
					JsonObject node = new JsonObject();
					node.addProperty("id", entry.get("id").getAsString());
					node.addProperty("kind", "registry");
					node.addProperty("type", registry);
					node.addProperty("name", registry.equals("languageKeys") ? string(entry, "key") : string(entry, "name"));
					node.addProperty("displayName", node.get("name").getAsString());
					nodes.add(node);
				}
			}
			result.add("nodes", nodes);
			JsonArray edges = new JsonArray();
			JsonArray diagnostics = new JsonArray();
			int scannedElements = 0;
			for (IndexedElement indexed : elements.values()) {
				scannedElements++;
				for (Candidate candidate : indexed.candidates()) {
					UUID resolved = identities.get(candidate.target());
					boolean matches = target.isBlank() || target.equals(candidate.target())
							|| resolved != null && target.equals(resolved.toString());
					if (!matches) continue;
					JsonObject edge = new JsonObject();
					edge.addProperty("id", candidate.id().toString());
					edge.addProperty("sourceId", indexed.elementId().toString());
					edge.addProperty("sourcePath", candidate.path());
					edge.addProperty("target", candidate.target());
					if (resolved == null) edge.add("targetId", com.google.gson.JsonNull.INSTANCE);
					else edge.addProperty("targetId", resolved.toString());
					edge.addProperty("kind", candidate.kind());
					edges.add(edge);
					if (resolved == null && candidate.required()) diagnostics.add(diagnostic(indexed.elementId(), candidate));
				}
			}
			result.add("edges", edges);
			result.add("diagnostics", diagnostics);
			JsonObject stats = new JsonObject();
			stats.addProperty("indexedElements", scannedElements);
			stats.addProperty("edgeCount", edges.size());
			stats.addProperty("incremental", true);
			result.add("stats", stats);
			return result;
		}
	}

	private IndexedElement scan(Element element, String fingerprint, Map<String, UUID> identities) {
		List<Candidate> candidates = new ArrayList<>();
		scanJson(element.id(), element.values(), "", candidates);
		if (element.type().equals("procedure")) {
			try {
				ProcedureIr ir = procedures.read(element.values(), element.id());
				for (ProcedureIr.Dependency dependency : ir.dependencies()) {
					if (dependency.kind().equals("context")) continue;
					String target = dependency.target().isBlank() ? dependency.name() : dependency.target();
					candidates.add(candidate(element.id(), "/procedureIr/dependencies/" + dependency.id(), target,
							dependency.kind(), true));
				}
			} catch (RuntimeException ignored) {
				// Invalid Procedure XML is reported by the Procedure validator, not duplicated here.
			}
		}
		return new IndexedElement(element.id(), fingerprint, List.copyOf(candidates));
	}

	private void scanJson(UUID elementId, JsonElement value, String path, List<Candidate> target) {
		if (value == null || value.isJsonNull()) return;
		// Procedure graph links are internal node identities. External dependencies are indexed from
		// ProcedureIr.dependencies below so node ids never become false dangling workspace references.
		if (path.equals("/procedureIr") || path.startsWith("/procedureIr/")) return;
		if (value.isJsonObject()) {
			for (var entry : value.getAsJsonObject().entrySet()) {
				String childPath = path + "/" + escapePointer(entry.getKey());
				JsonElement child = entry.getValue();
				if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
					String text = child.getAsString();
					String key = entry.getKey().toLowerCase(Locale.ROOT);
					if (REFERENCE_KEYS.contains(key) || (!key.equals("id") && key.endsWith("id"))
							|| key.endsWith("ref"))
						target.add(candidate(elementId, childPath, text, kind(key, text), true));
					else if (RESOURCE_LOCATION.matcher(text).matches())
						target.add(candidate(elementId, childPath, text, "resource", false));
				} else scanJson(elementId, child, childPath, target);
			}
		} else if (value.isJsonArray()) {
			int index = 0;
			for (JsonElement child : value.getAsJsonArray()) scanJson(elementId, child, path + "/" + index++, target);
		}
	}

	private static Candidate candidate(UUID elementId, String path, String target, String kind, boolean required) {
		UUID id = UUID.nameUUIDFromBytes((elementId + "\n" + path + "\n" + target).getBytes(StandardCharsets.UTF_8));
		return new Candidate(id, path, target, kind, required);
	}

	private static JsonObject diagnostic(UUID sourceId, Candidate candidate) {
		JsonObject diagnostic = new JsonObject();
		diagnostic.addProperty("code", "WORKSPACE_REFERENCE_DANGLING");
		diagnostic.addProperty("severity", "error");
		JsonObject message = new JsonObject();
		message.addProperty("key", "diagnostic.workspace_reference_dangling");
		message.addProperty("fallback", "A structured reference target does not exist.");
		JsonObject args = new JsonObject();
		args.addProperty("target", candidate.target());
		message.add("args", args);
		diagnostic.add("message", message);
		diagnostic.addProperty("path", "/elements/" + sourceId + candidate.path());
		diagnostic.addProperty("elementId", sourceId.toString());
		diagnostic.addProperty("recoverable", true);
		JsonArray actions = new JsonArray();
		JsonObject action = new JsonObject();
		action.addProperty("id", "locate_reference_source");
		JsonObject label = new JsonObject();
		label.addProperty("key", "action.locate_reference_source");
		label.addProperty("fallback", "Locate source");
		label.add("args", new JsonObject());
		action.add("label", label);
		action.addProperty("kind", "open_field");
		action.addProperty("target", "/elements/" + sourceId + candidate.path());
		actions.add(action);
		diagnostic.add("actions", actions);
		return diagnostic;
	}

	private static String kind(String key, String value) {
		if (key.contains("procedure") || key.equals("function") || key.equals("rewardfunction")) return "procedure";
		if (key.contains("variable")) return "variable";
		if (key.contains("tag")) return "tag";
		if (key.contains("language")) return "language";
		if (RESOURCE_LOCATION.matcher(value).matches()) return "resource";
		return "element";
	}

	private static String fingerprint(Element element) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest((element.type() + "\n" + element.name() + "\n" + GSON.toJson(element.values()))
					.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(bytes);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not fingerprint workspace element", exception);
		}
	}

	private static String escapePointer(String value) { return value.replace("~", "~0").replace("/", "~1"); }
	private static String string(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
	}

	private record IndexedElement(UUID elementId, String fingerprint, List<Candidate> candidates) {
	}

	private record Candidate(UUID id, String path, String target, String kind, boolean required) {
	}
}

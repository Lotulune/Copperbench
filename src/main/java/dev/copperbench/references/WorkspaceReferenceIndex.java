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
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
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
	private static final Pattern EMBEDDED_RESOURCE_LOCATION = Pattern.compile(
			"(?<![a-z0-9_.-])([a-z0-9_.-]+):(?!//)([a-z0-9_./-]+)", Pattern.CASE_INSENSITIVE);
	private static final Set<String> REFERENCE_KEYS = Set.of("elementid", "procedureid", "variableid", "tagid",
			"languagekey", "parent", "rewardfunction", "function", "loottable", "target", "reference", "ref");
	private final ProcedureIrCodec procedures = new ProcedureIrCodec();
	private final Map<UUID, WorkspaceIndex> workspaces = new ConcurrentHashMap<>();

	public JsonObject projection(WorkspaceState state, String target) {
		WorkspaceIndex index = workspaces.computeIfAbsent(state.id(), ignored -> new WorkspaceIndex());
		return index.update(state, target == null ? "" : target);
	}

	private static void addEmbeddedResourceCandidates(UUID elementId, String path, String text, List<Candidate> target) {
		java.util.regex.Matcher matcher = EMBEDDED_RESOURCE_LOCATION.matcher(text);
		while (matcher.find()) {
			String resource = matcher.group().toLowerCase(Locale.ROOT);
			target.add(candidate(elementId, path + "#resource-" + matcher.start(), resource, "resource", false));
		}
	}

	private final class WorkspaceIndex {
		private final Map<UUID, IndexedElement> elements = new LinkedHashMap<>();

		private synchronized JsonObject update(WorkspaceState state, String target) {
			Map<UUID, Element> current = new LinkedHashMap<>();
			state.elements().forEach(element -> current.put(element.id(), element));
			elements.keySet().removeIf(id -> !current.containsKey(id));
			Map<String, Set<UUID>> identities = new HashMap<>();
			for (Element element : current.values()) {
				identity(identities, element.id().toString(), element.id());
				identity(identities, element.name(), element.id());
			}
			for (String registry : List.of("variables", "tags", "languageKeys")) {
				for (JsonElement raw : state.registries().getAsJsonArray(registry)) {
					JsonObject entry = raw.getAsJsonObject();
					if (!entry.has("id")) continue;
					UUID id = UUID.fromString(entry.get("id").getAsString());
					identity(identities, id.toString(), id);
					String name = registry.equals("languageKeys") ? string(entry, "key") : string(entry, "name");
					if (!name.isBlank()) identity(identities, name, id);
				}
			}
			for (Element element : current.values()) {
				String fingerprint = fingerprint(element);
				IndexedElement indexed = elements.get(element.id());
				if (indexed == null || !indexed.fingerprint().equals(fingerprint))
					elements.put(element.id(), scan(element, fingerprint));
			}
			return json(state, target, identities);
		}

		private JsonObject json(WorkspaceState state, String target, Map<String, Set<UUID>> identities) {
			JsonObject result = new JsonObject();
			result.addProperty("revision", state.revision());
			JsonArray nodes = new JsonArray();
			Map<UUID, JsonObject> nodesById = new LinkedHashMap<>();
			for (Element element : state.elements()) {
				JsonObject node = new JsonObject();
				node.addProperty("id", element.id().toString());
				node.addProperty("kind", "element");
				node.addProperty("type", element.type());
				node.addProperty("name", element.name());
				node.addProperty("displayName", element.displayName());
				nodes.add(node);
				nodesById.put(element.id(), node);
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
					nodesById.put(UUID.fromString(entry.get("id").getAsString()), node);
				}
			}
			result.add("nodes", nodes);
			JsonArray edges = new JsonArray();
			JsonArray diagnostics = new JsonArray();
			int scannedElements = 0;
			for (IndexedElement indexed : elements.values()) {
				scannedElements++;
				for (Candidate candidate : indexed.candidates()) {
					Resolution resolution = resolve(candidate, identities, nodesById);
					UUID resolved = resolution.id();
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
					edge.addProperty("resolution", resolution.status());
					JsonObject sourceNode = nodesById.get(indexed.elementId());
					JsonObject targetNode = resolved == null ? null : nodesById.get(resolved);
					if (sourceNode != null) addNodeSummary(edge, "source", sourceNode);
					if (targetNode != null) addNodeSummary(edge, "target", targetNode);
					else {
						edge.addProperty("targetName", candidate.target());
						edge.add("targetKind", com.google.gson.JsonNull.INSTANCE);
						edge.add("targetType", com.google.gson.JsonNull.INSTANCE);
						edge.add("targetDisplayName", com.google.gson.JsonNull.INSTANCE);
					}
					edges.add(edge);
					if (resolved == null && candidate.required()) diagnostics.add(diagnostic(indexed.elementId(), candidate, resolution.status()));
				}
			}
			result.add("edges", edges);
			result.add("diagnostics", diagnostics);
			JsonObject stats = new JsonObject();
			stats.addProperty("indexedElements", scannedElements);
			stats.addProperty("edgeCount", edges.size());
			stats.addProperty("incremental", true);
			stats.addProperty("referenceScope", "Known IR nodes, typed Procedure fields including nested collections/maps and registered GUI components, and conventional reference keys; opaque custom adapters and return/context types require separate validation.");
			result.add("stats", stats);
			return result;
		}
	}

	private static void addNodeSummary(JsonObject edge, String prefix, JsonObject node) {
		edge.addProperty(prefix + "Kind", string(node, "kind"));
		edge.addProperty(prefix + "Type", string(node, "type"));
		edge.addProperty(prefix + "Name", string(node, "name"));
		edge.addProperty(prefix + "DisplayName", string(node, "displayName"));
	}

	private IndexedElement scan(Element element, String fingerprint) {
		List<Candidate> candidates = new ArrayList<>();
		JsonObject indexedValues = dev.copperbench.core.application.BlockFieldContract.merged(element.values());
		indexedValues.remove("fields"); // Compatibility aliases describe the same logical fields.
		indexTypedProcedureFields(element, indexedValues, candidates);
		if (element.type().equals("procedure")) {
			// Canonical Procedure references are derived from actual nodes below.
			// Do not rescan the serialized Blockly XML or the IR storage itself, otherwise
			// resource locations and stable procedure/variable targets are duplicated.
			indexedValues = indexedValues.deepCopy();
			indexedValues.remove("procedureIr");
			indexedValues.remove("procedurexml");
		}
		scanJson(element.id(), indexedValues, "", candidates);
		if (element.type().equals("procedure")) {
			try {
				ProcedureIr ir = procedures.read(dev.copperbench.core.application.BlockFieldContract.merged(element.values()), element.id());
				// Caller-provided dependency summaries can be stale, omitted or fabricated.
				ir = procedures.applyEdits(ir, new JsonArray());
				for (ProcedureIr.Dependency dependency : ir.dependencies()) {
					if (dependency.kind().equals("context")) continue;
					String target = dependency.target().isBlank() ? dependency.name() : dependency.target();
					candidates.add(candidate(element.id(), "/procedureIr/dependencies/" + dependency.id(), target,
							dependency.kind(), !dependency.kind().equals("resource")));
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
							|| key.endsWith("ref")) {
						if (!text.isBlank()) {
							String referenceKind = kind(key, text);
							target.add(candidate(elementId, childPath, text, referenceKind, !referenceKind.equals("resource")));
						}
					}
					else if (RESOURCE_LOCATION.matcher(text).matches())
						target.add(candidate(elementId, childPath, text, "resource", false));
					else addEmbeddedResourceCandidates(elementId, childPath, text, target);
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

	private static JsonObject diagnostic(UUID sourceId, Candidate candidate, String status) {
		JsonObject diagnostic = new JsonObject();
		String code = status.equals("type_mismatch") ? "WORKSPACE_REFERENCE_TYPE_MISMATCH"
				: status.equals("ambiguous") ? "WORKSPACE_REFERENCE_AMBIGUOUS" : "WORKSPACE_REFERENCE_DANGLING";
		diagnostic.addProperty("code", code);
		diagnostic.addProperty("severity", "error");
		JsonObject message = new JsonObject();
		message.addProperty("key", "diagnostic." + code.toLowerCase(Locale.ROOT));
		message.addProperty("fallback", status.equals("type_mismatch") ? "A structured reference points to the wrong kind of target."
				: status.equals("ambiguous") ? "A structured reference has multiple matching targets." : "A structured reference target does not exist.");
		JsonObject args = new JsonObject();
		args.addProperty("target", candidate.target());
		args.addProperty("expectedKind", candidate.kind());
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
		if (key.contains("procedure")) return "procedure";
		if (key.equals("function") || key.equals("rewardfunction")) return RESOURCE_LOCATION.matcher(value).matches() ? "resource" : "function";
		if (key.contains("variable")) return "variable";
		if (key.contains("tag")) return "tag";
		if (key.contains("language")) return "language";
		if (RESOURCE_LOCATION.matcher(value).matches()) return "resource";
		return "element";
	}

	private static void identity(Map<String, Set<UUID>> index, String name, UUID id) {
		index.computeIfAbsent(name, ignored -> new java.util.LinkedHashSet<>()).add(id);
	}
	private static Resolution resolve(Candidate candidate, Map<String, Set<UUID>> identities, Map<UUID, JsonObject> nodes) {
		if (candidate.kind().equals("resource")) return new Resolution(null, "external");
		Set<UUID> found = identities.getOrDefault(candidate.target(), Set.of());
		List<UUID> matches = found.stream().filter(id -> {
			JsonObject node = nodes.get(id); if (node == null) return false;
			String expected = switch (candidate.kind()) {
				case "procedure", "function" -> candidate.kind();
				case "variable" -> "variables"; case "tag" -> "tags"; case "language" -> "languageKeys";
				default -> null;
			};
			return expected == null ? string(node, "kind").equals("element") : string(node, "type").equals(expected);
		}).toList();
		return matches.size() == 1 ? new Resolution(matches.getFirst(), "resolved")
				: new Resolution(null, matches.size() > 1 ? "ambiguous" : found.isEmpty() ? "missing" : "type_mismatch");
	}

	private static void indexTypedProcedureFields(Element element, JsonObject values, List<Candidate> candidates) {
		Class<?> storage;
		try {
			storage = switch (element.type()) {
				case "item" -> net.mcreator.element.types.Item.class;
				case "block" -> net.mcreator.element.types.Block.class;
				default -> net.mcreator.element.ModElementTypeLoader.getModElementType(element.type()).getModElementStorageClass();
			};
		} catch (IllegalArgumentException exception) { return; }
		if (storage == null) return;
		indexTypedObject(element.id(), storage, values, "", candidates, 0);
	}

	/** Remove consumed typed values from the private scan copy so fixed values and map keys are not rescanned heuristically. */
	private static boolean indexTypedValue(UUID sourceId, Type type, JsonElement raw, String path,
			List<Candidate> candidates, int depth) {
		if (depth > 64 || raw == null || raw.isJsonNull()) return false;
		Class<?> kind = type instanceof Class<?> c ? c
				: type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c ? c : null;
		if (kind == null) return false;
		if (net.mcreator.element.parts.procedure.Procedure.class.isAssignableFrom(kind)) {
			JsonElement value = raw.isJsonObject() ? raw.getAsJsonObject().get("name")
					: kind == net.mcreator.element.parts.procedure.Procedure.class ? raw : null;
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				String name = value.getAsString();
				if (!name.isBlank()) candidates.add(candidate(sourceId, path, name, "procedure", true));
			}
			return true;
		}
		if (kind == net.mcreator.element.parts.gui.GUIComponent.class && raw.isJsonObject()) {
			JsonObject component = raw.getAsJsonObject();
			Class<?> actual = net.mcreator.element.parts.gui.GUIComponent.getTypeMappings().get(string(component, "type"));
			if (actual != null && component.has("data") && component.get("data").isJsonObject())
				indexTypedObject(sourceId, actual, component.getAsJsonObject("data"), path + "/data", candidates, depth + 1);
			return false;
		}
		// Custom serialized shapes cannot be inferred from their backing Java fields.
		if (kind.isAnnotationPresent(com.google.gson.annotations.JsonAdapter.class)) return false;
		if ((kind.isArray() || Collection.class.isAssignableFrom(kind)) && raw.isJsonArray()) {
			Type member = kind.isArray() ? kind.getComponentType()
					: type instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
			for (int i = 0; i < raw.getAsJsonArray().size(); i++)
				if (indexTypedValue(sourceId, member, raw.getAsJsonArray().get(i), path + "/" + i, candidates, depth + 1))
					raw.getAsJsonArray().set(i, com.google.gson.JsonNull.INSTANCE);
		} else if (Map.class.isAssignableFrom(kind) && raw.isJsonObject()) {
			Type member = type instanceof ParameterizedType p ? p.getActualTypeArguments()[1] : Object.class;
			for (String key : new ArrayList<>(raw.getAsJsonObject().keySet()))
				if (indexTypedValue(sourceId, member, raw.getAsJsonObject().get(key), path + "/" + escapePointer(key), candidates, depth + 1))
					raw.getAsJsonObject().remove(key);
		} else if (raw.isJsonObject() && kind != Object.class && !kind.getName().startsWith("java.")) {
			indexTypedObject(sourceId, kind, raw.getAsJsonObject(), path, candidates, depth + 1);
		}
		return false;
	}

	private static void indexTypedObject(UUID sourceId, Class<?> storage, JsonObject values, String path,
			List<Candidate> candidates, int depth) {
		if (depth > 64) return;
		for (var field : storage.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || !values.has(field.getName())) continue;
			if (indexTypedValue(sourceId, field.getGenericType(), values.get(field.getName()),
					path + "/" + escapePointer(field.getName()), candidates, depth + 1)) values.remove(field.getName());
		}
	}
	private record Resolution(UUID id, String status) {}

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

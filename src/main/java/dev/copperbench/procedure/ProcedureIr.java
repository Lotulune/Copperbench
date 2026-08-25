/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.procedure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** UI-independent, versioned procedure graph owned by Java Core. */
public record ProcedureIr(String schemaVersion, String trigger, List<Node> nodes, List<Dependency> dependencies,
		JsonObject unknownRoot) {

	public static final String SCHEMA_VERSION = "1.0";

	public ProcedureIr {
		schemaVersion = SCHEMA_VERSION;
		trigger = trigger == null || trigger.isBlank() ? "no_ext_trigger" : trigger;
		nodes = nodes == null ? List.of() : List.copyOf(nodes);
		dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
		unknownRoot = unknownRoot == null ? new JsonObject() : unknownRoot.deepCopy();
	}

	public ProcedureIr copy() {
		return new ProcedureIr(schemaVersion, trigger, nodes.stream().map(Node::copy).toList(),
				dependencies.stream().map(Dependency::copy).toList(), unknownRoot);
	}

	public Map<UUID, Node> nodeIndex() {
		Map<UUID, Node> result = new LinkedHashMap<>();
		for (Node node : nodes)
			result.put(node.id(), node);
		return Map.copyOf(result);
	}

	public record Node(UUID id, String type, String kind, double x, double y, JsonObject fields,
			Map<String, UUID> inputs, UUID next, boolean unknown, String rawPayload) {
		public Node {
			Objects.requireNonNull(id);
			Objects.requireNonNull(type);
			kind = kind == null || kind.isBlank() ? "statement" : kind;
			fields = fields == null ? new JsonObject() : fields.deepCopy();
			inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
			rawPayload = rawPayload == null ? "" : rawPayload;
		}

		public Node copy() {
			return new Node(id, type, kind, x, y, fields, inputs, next, unknown, rawPayload);
		}
	}

	public record Dependency(UUID id, String kind, String name, String dataType, String target) {
		public Dependency {
			Objects.requireNonNull(id);
			kind = kind == null ? "context" : kind;
			name = name == null ? "" : name;
			dataType = dataType == null ? "unknown" : dataType;
			target = target == null ? "" : target;
		}

		public Dependency copy() {
			return new Dependency(id, kind, name, dataType, target);
		}
	}

	public record ValidationIssue(String code, String message, UUID nodeId, String port, boolean error) {
	}

	static String string(JsonObject object, String key, String fallback) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
	}
}

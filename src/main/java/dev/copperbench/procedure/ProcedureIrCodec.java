/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.procedure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.procedure.ProcedureIr.Dependency;
import dev.copperbench.procedure.ProcedureIr.Node;
import dev.copperbench.procedure.ProcedureIr.ValidationIssue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts legacy Blockly XML and structured edit operations at the Core boundary. */
public final class ProcedureIrCodec {

	private static final Gson GSON = new Gson();
	private static final String EMPTY_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
			+ "<block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\">"
			+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>";
	private static final Pattern BLOCK_START = Pattern.compile("<block\\b[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern BLOCK_TOKEN = Pattern.compile("<block\\b[^>]*?/?>|</block\\s*>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ATTRIBUTE = Pattern.compile("\\b(type|id)\\s*=\\s*([\"'])(.*?)\\2",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Set<String> KNOWN_TYPES = Set.of(
			"event_trigger", "controls_if", "controls_repeat_ext", "controls_while", "controls_flow_statements",
			"logic_boolean", "logic_negate", "logic_binary_ops", "logic_ternary_op", "math_number",
			"math_binary_ops", "math_dual_ops", "math_singular_ops", "text", "text_join", "text_print",
			"variables_get_logic", "variables_get_number", "variables_get_string", "variables_get_itemstack",
			"variables_set_logic", "variables_set_number", "variables_set_string", "variables_set_itemstack",
			"entity_from_deps", "source_entity_from_deps", "immediate_source_entity_from_deps", "entity_iterator",
			"coord_x", "coord_y", "coord_z", "mcitem_all", "mcitem_allblocks", "call_procedure",
			"return_logic", "return_number", "return_string", "return_itemstack", "return_entity");

	public ProcedureIr read(JsonObject values, UUID elementId) {
		if (values.has("procedureIr") && values.get("procedureIr").isJsonObject())
			return fromJson(values.getAsJsonObject("procedureIr"));
		String xml = values.has("procedurexml") && values.get("procedurexml").isJsonPrimitive()
				? values.get("procedurexml").getAsString() : EMPTY_XML;
		return fromBlocklyXml(xml, elementId);
	}

	public ProcedureIr fromBlocklyXml(String xml, UUID elementId) {
		try {
			Document document = parse(xml);
			List<Node> nodes = new ArrayList<>();
			List<Dependency> dependencies = new ArrayList<>();
			Map<String, String> rawByIdentity = rawBlocks(xml);
			String trigger = "no_ext_trigger";
			Element root = document.getDocumentElement();
			int[] ordinal = { 0 };
			for (Element block : directChildren(root, "block")) {
				Node parsed = readBlock(block, elementId, "root/" + ordinal[0]++, nodes, dependencies, rawByIdentity);
				if (parsed.type().equals("event_trigger"))
					trigger = field(block, "trigger", trigger);
			}
			return new ProcedureIr(ProcedureIr.SCHEMA_VERSION, trigger, nodes, dependencies, new JsonObject());
		} catch (Exception exception) {
			throw new IllegalArgumentException("procedurexml is not valid Blockly XML", exception);
		}
	}

	private Node readBlock(Element block, UUID elementId, String path, List<Node> nodes,
			List<Dependency> dependencies, Map<String, String> rawByIdentity) {
		String type = block.getAttribute("type");
		String rawId = block.getAttribute("id");
		UUID id = uuid(rawId, elementId + "\n" + path + "\n" + type);
		boolean unknown = !KNOWN_TYPES.contains(type);
		JsonObject fields = new JsonObject();
		for (Element child : directChildren(block, "field"))
			fields.addProperty(child.getAttribute("name"), child.getTextContent());
		Map<String, UUID> inputs = new LinkedHashMap<>();
		UUID next = null;
		if (!unknown) {
			int inputOrdinal = 0;
			for (Element container : childElements(block)) {
				String tag = container.getTagName();
				if (!tag.equals("value") && !tag.equals("statement") && !tag.equals("next"))
					continue;
				Element childBlock = firstDirectChild(container, "block");
				if (childBlock == null)
					childBlock = firstDirectChild(container, "shadow");
				if (childBlock == null)
					continue;
				Node child = readBlock(childBlock, elementId, path + "/" + tag + "/" + inputOrdinal++, nodes,
						dependencies, rawByIdentity);
				if (tag.equals("next")) next = child.id();
				else inputs.put(container.getAttribute("name"), child.id());
			}
		}
		String kind = outputKind(block);
		String rawPayload = unknown ? rawByIdentity.getOrDefault(identity(type, rawId), outerXml(block)) : "";
		Node node = new Node(id, type, kind, number(block, "x", 40), number(block, "y", 40), fields, inputs,
				next, unknown, rawPayload);
		nodes.add(node);
		collectDependency(node, dependencies);
		return node;
	}

	public ProcedureIr applyEdits(ProcedureIr original, JsonArray edits) {
		LinkedHashMap<UUID, Node> nodes = new LinkedHashMap<>();
		original.nodes().forEach(node -> nodes.put(node.id(), node.copy()));
		String trigger = original.trigger();
		for (JsonElement raw : edits) {
			JsonObject edit = raw.getAsJsonObject();
			String operation = requiredString(edit, "operation");
			switch (operation) {
				case "add_node" -> {
					Node node = nodeFromJson(edit.getAsJsonObject("node"));
					if (nodes.putIfAbsent(node.id(), node) != null)
						throw new IllegalArgumentException("Node already exists: " + node.id());
				}
				case "update_node" -> {
					UUID nodeId = UUID.fromString(requiredString(edit, "nodeId"));
					Node current = requiredNode(nodes, nodeId);
					if (current.unknown())
						throw new IllegalArgumentException("Unknown nodes are read only");
					JsonObject fields = edit.has("fields") ? edit.getAsJsonObject("fields") : current.fields();
					double x = edit.has("x") ? edit.get("x").getAsDouble() : current.x();
					double y = edit.has("y") ? edit.get("y").getAsDouble() : current.y();
					nodes.put(nodeId, new Node(nodeId, current.type(), current.kind(), x, y, fields,
							current.inputs(), current.next(), false, ""));
				}
				case "move_node" -> {
					UUID nodeId = UUID.fromString(requiredString(edit, "nodeId"));
					Node current = requiredNode(nodes, nodeId);
					nodes.put(nodeId, new Node(nodeId, current.type(), current.kind(), edit.get("x").getAsDouble(),
							edit.get("y").getAsDouble(), current.fields(), current.inputs(), current.next(),
							current.unknown(), current.rawPayload()));
				}
				case "delete_node" -> deleteNode(nodes, UUID.fromString(requiredString(edit, "nodeId")));
				case "connect" -> connect(nodes, edit);
				case "disconnect" -> disconnect(nodes, edit);
				case "set_trigger" -> trigger = requiredString(edit, "trigger");
				default -> throw new IllegalArgumentException("Unsupported procedure edit operation: " + operation);
			}
		}
		return new ProcedureIr(ProcedureIr.SCHEMA_VERSION, trigger, List.copyOf(nodes.values()),
				dependencies(nodes.values()), original.unknownRoot());
	}

	public List<ValidationIssue> validate(ProcedureIr ir) {
		List<ValidationIssue> issues = new ArrayList<>();
		Map<UUID, Node> nodes = ir.nodeIndex();
		Set<UUID> referenced = new HashSet<>();
		for (Node node : ir.nodes()) {
			for (var input : node.inputs().entrySet()) {
				referenced.add(input.getValue());
				if (!nodes.containsKey(input.getValue()))
					issues.add(new ValidationIssue("PROCEDURE_DANGLING_PORT", "Input references a missing node.",
							node.id(), input.getKey(), true));
			}
			if (node.next() != null) {
				referenced.add(node.next());
				if (!nodes.containsKey(node.next()))
					issues.add(new ValidationIssue("PROCEDURE_DANGLING_CONTROL_FLOW",
							"Control flow references a missing node.", node.id(), "next", true));
			}
			if (node.type().equals("call_procedure") && ProcedureIr.string(node.fields(), "procedureId", "").isBlank())
				issues.add(new ValidationIssue("PROCEDURE_CALL_TARGET_REQUIRED", "Procedure call target is required.",
						node.id(), "procedureId", true));
		}
		if (ir.nodes().stream().noneMatch(node -> node.type().equals("event_trigger")))
			issues.add(new ValidationIssue("PROCEDURE_TRIGGER_NODE_REQUIRED", "A trigger node is required.", null,
					"trigger", true));
		if (hasCycle(nodes))
			issues.add(new ValidationIssue("PROCEDURE_GRAPH_CYCLE", "Procedure graph contains an invalid connection cycle.",
					null, null, true));
		return List.copyOf(issues);
	}

	public JsonObject toJson(ProcedureIr ir) {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", ProcedureIr.SCHEMA_VERSION);
		json.addProperty("trigger", ir.trigger());
		JsonArray nodes = new JsonArray();
		ir.nodes().forEach(node -> nodes.add(nodeToJson(node)));
		json.add("nodes", nodes);
		JsonArray dependencies = new JsonArray();
		for (Dependency dependency : ir.dependencies()) {
			JsonObject item = new JsonObject();
			item.addProperty("id", dependency.id().toString());
			item.addProperty("kind", dependency.kind());
			item.addProperty("name", dependency.name());
			item.addProperty("dataType", dependency.dataType());
			item.addProperty("target", dependency.target());
			dependencies.add(item);
		}
		json.add("dependencies", dependencies);
		json.add("unknownRoot", ir.unknownRoot());
		return json;
	}

	public ProcedureIr fromJson(JsonObject json) {
		List<Node> nodes = new ArrayList<>();
		if (json.has("nodes") && json.get("nodes").isJsonArray())
			json.getAsJsonArray("nodes").forEach(item -> nodes.add(nodeFromJson(item.getAsJsonObject())));
		List<Dependency> dependencies = new ArrayList<>();
		if (json.has("dependencies") && json.get("dependencies").isJsonArray()) {
			for (JsonElement raw : json.getAsJsonArray("dependencies")) {
				JsonObject item = raw.getAsJsonObject();
				dependencies.add(new Dependency(UUID.fromString(requiredString(item, "id")),
						ProcedureIr.string(item, "kind", "context"), ProcedureIr.string(item, "name", ""),
						ProcedureIr.string(item, "dataType", "unknown"), ProcedureIr.string(item, "target", "")));
			}
		}
		return new ProcedureIr(ProcedureIr.SCHEMA_VERSION, ProcedureIr.string(json, "trigger", "no_ext_trigger"),
				nodes, dependencies, json.has("unknownRoot") && json.get("unknownRoot").isJsonObject()
						? json.getAsJsonObject("unknownRoot") : new JsonObject());
	}

	public String toBlocklyXml(ProcedureIr ir) {
		Map<UUID, Node> nodes = ir.nodeIndex();
		Set<UUID> referenced = new LinkedHashSet<>();
		for (Node node : ir.nodes()) {
			referenced.addAll(node.inputs().values());
			if (node.next() != null) referenced.add(node.next());
		}
		StringBuilder xml = new StringBuilder("<xml xmlns=\"https://developers.google.com/blockly/xml\">");
		for (Node node : ir.nodes()) {
			if (!referenced.contains(node.id()))
				appendNode(xml, node, nodes, new HashSet<>(), ir.trigger());
		}
		xml.append("</xml>");
		return xml.toString();
	}

	public String sourcePreview(ProcedureIr ir) {
		StringBuilder source = new StringBuilder("// Read-only Procedure IR preview\n");
		source.append("trigger ").append(ir.trigger()).append("\n");
		Map<UUID, Node> nodes = ir.nodeIndex();
		Set<UUID> referenced = new HashSet<>();
		ir.nodes().forEach(node -> { referenced.addAll(node.inputs().values()); if (node.next() != null) referenced.add(node.next()); });
		for (Node node : ir.nodes())
			if (!referenced.contains(node.id())) appendPreview(source, node, nodes, 0, new HashSet<>());
		return source.toString();
	}

	private void appendPreview(StringBuilder source, Node node, Map<UUID, Node> nodes, int depth, Set<UUID> visited) {
		if (!visited.add(node.id())) return;
		source.append("  ".repeat(Math.max(0, depth))).append(node.unknown() ? "unknown " : "node ")
				.append(node.type()).append(" #").append(node.id()).append('\n');
		for (var input : node.inputs().entrySet()) {
			Node child = nodes.get(input.getValue());
			if (child != null) {
				source.append("  ".repeat(depth + 1)).append(input.getKey()).append(":\n");
				appendPreview(source, child, nodes, depth + 2, visited);
			}
		}
		if (node.next() != null && nodes.containsKey(node.next())) appendPreview(source, nodes.get(node.next()), nodes,
				depth, visited);
	}

	private void appendNode(StringBuilder xml, Node node, Map<UUID, Node> nodes, Set<UUID> visited, String trigger) {
		if (!visited.add(node.id())) return;
		if (node.unknown()) {
			xml.append(node.rawPayload());
			return;
		}
		xml.append("<block type=\"").append(escape(node.type())).append("\" id=\"").append(node.id()).append("\"");
		if (node.x() != 0) xml.append(" x=\"").append(Math.round(node.x())).append("\"");
		if (node.y() != 0) xml.append(" y=\"").append(Math.round(node.y())).append("\"");
		if (node.type().equals("event_trigger")) xml.append(" deletable=\"false\"");
		xml.append('>');
		JsonObject fields = node.fields().deepCopy();
		if (node.type().equals("event_trigger")) fields.addProperty("trigger", trigger);
		for (String name : fields.keySet()) {
			JsonElement value = fields.get(name);
			if (value == null || value.isJsonNull()) continue;
			xml.append("<field name=\"").append(escape(name)).append("\">")
					.append(escape(value.getAsString())).append("</field>");
		}
		for (var input : node.inputs().entrySet()) {
			Node child = nodes.get(input.getValue());
			if (child == null) continue;
			String tag = child.kind().equals("statement") ? "statement" : "value";
			xml.append('<').append(tag).append(" name=\"").append(escape(input.getKey())).append("\">");
			appendNode(xml, child, nodes, visited, trigger);
			xml.append("</").append(tag).append('>');
		}
		if (node.next() != null && nodes.containsKey(node.next())) {
			xml.append("<next>");
			appendNode(xml, nodes.get(node.next()), nodes, visited, trigger);
			xml.append("</next>");
		}
		xml.append("</block>");
	}

	private JsonObject nodeToJson(Node node) {
		JsonObject json = new JsonObject();
		json.addProperty("id", node.id().toString());
		json.addProperty("type", node.type());
		json.addProperty("kind", node.kind());
		json.addProperty("x", node.x());
		json.addProperty("y", node.y());
		json.add("fields", node.fields());
		JsonObject inputs = new JsonObject();
		node.inputs().forEach((name, id) -> inputs.addProperty(name, id.toString()));
		json.add("inputs", inputs);
		if (node.next() == null) json.add("next", JsonNull.INSTANCE); else json.addProperty("next", node.next().toString());
		json.addProperty("unknown", node.unknown());
		if (node.unknown()) json.addProperty("rawPayload", node.rawPayload());
		return json;
	}

	private Node nodeFromJson(JsonObject json) {
		UUID id = UUID.fromString(requiredString(json, "id"));
		String type = requiredString(json, "type");
		Map<String, UUID> inputs = new LinkedHashMap<>();
		if (json.has("inputs") && json.get("inputs").isJsonObject())
			json.getAsJsonObject("inputs").entrySet().forEach(entry -> inputs.put(entry.getKey(),
					UUID.fromString(entry.getValue().getAsString())));
		UUID next = json.has("next") && !json.get("next").isJsonNull() ? UUID.fromString(json.get("next").getAsString()) : null;
		boolean unknown = json.has("unknown") ? json.get("unknown").getAsBoolean() : !KNOWN_TYPES.contains(type);
		return new Node(id, type, ProcedureIr.string(json, "kind", "statement"),
				json.has("x") ? json.get("x").getAsDouble() : 40,
				json.has("y") ? json.get("y").getAsDouble() : 40,
				json.has("fields") ? json.getAsJsonObject("fields") : new JsonObject(), inputs, next, unknown,
				ProcedureIr.string(json, "rawPayload", ""));
	}

	private void connect(Map<UUID, Node> nodes, JsonObject edit) {
		UUID sourceId = UUID.fromString(requiredString(edit, "sourceNodeId"));
		UUID targetId = UUID.fromString(requiredString(edit, "targetNodeId"));
		Node source = requiredNode(nodes, sourceId);
		requiredNode(nodes, targetId);
		String port = requiredString(edit, "port");
		if (port.equals("next")) {
			nodes.put(sourceId, new Node(source.id(), source.type(), source.kind(), source.x(), source.y(),
					source.fields(), source.inputs(), targetId, source.unknown(), source.rawPayload()));
		} else {
			Map<String, UUID> inputs = new LinkedHashMap<>(source.inputs());
			inputs.put(port, targetId);
			nodes.put(sourceId, new Node(source.id(), source.type(), source.kind(), source.x(), source.y(),
					source.fields(), inputs, source.next(), source.unknown(), source.rawPayload()));
		}
	}

	private void disconnect(Map<UUID, Node> nodes, JsonObject edit) {
		UUID sourceId = UUID.fromString(requiredString(edit, "sourceNodeId"));
		Node source = requiredNode(nodes, sourceId);
		String port = requiredString(edit, "port");
		Map<String, UUID> inputs = new LinkedHashMap<>(source.inputs());
		inputs.remove(port);
		nodes.put(sourceId, new Node(source.id(), source.type(), source.kind(), source.x(), source.y(), source.fields(),
				inputs, port.equals("next") ? null : source.next(), source.unknown(), source.rawPayload()));
	}

	private void deleteNode(Map<UUID, Node> nodes, UUID nodeId) {
		Node node = requiredNode(nodes, nodeId);
		if (node.type().equals("event_trigger")) throw new IllegalArgumentException("The trigger node cannot be deleted");
		nodes.remove(nodeId);
		for (Node current : List.copyOf(nodes.values())) {
			Map<String, UUID> inputs = new LinkedHashMap<>(current.inputs());
			inputs.entrySet().removeIf(entry -> entry.getValue().equals(nodeId));
			UUID next = nodeId.equals(current.next()) ? null : current.next();
			if (!inputs.equals(current.inputs()) || next != current.next())
				nodes.put(current.id(), new Node(current.id(), current.type(), current.kind(), current.x(), current.y(),
						current.fields(), inputs, next, current.unknown(), current.rawPayload()));
		}
	}

	private boolean hasCycle(Map<UUID, Node> nodes) {
		Set<UUID> visiting = new HashSet<>();
		Set<UUID> visited = new HashSet<>();
		for (UUID id : nodes.keySet())
			if (cycle(id, nodes, visiting, visited)) return true;
		return false;
	}

	private boolean cycle(UUID id, Map<UUID, Node> nodes, Set<UUID> visiting, Set<UUID> visited) {
		if (visited.contains(id)) return false;
		if (!visiting.add(id)) return true;
		Node node = nodes.get(id);
		if (node != null) {
			for (UUID child : node.inputs().values()) if (cycle(child, nodes, visiting, visited)) return true;
			if (node.next() != null && cycle(node.next(), nodes, visiting, visited)) return true;
		}
		visiting.remove(id);
		visited.add(id);
		return false;
	}

	private List<Dependency> dependencies(Iterable<Node> nodes) {
		List<Dependency> dependencies = new ArrayList<>();
		for (Node node : nodes) collectDependency(node, dependencies);
		return List.copyOf(dependencies);
	}

	private void collectDependency(Node node, List<Dependency> dependencies) {
		String kind = null;
		String name = "";
		String dataType = "unknown";
		String target = "";
		if (node.type().startsWith("variables_get_") || node.type().startsWith("variables_set_")) {
			kind = "variable";
			name = ProcedureIr.string(node.fields(), "VAR", ProcedureIr.string(node.fields(), "name", ""));
			dataType = node.type().substring(node.type().lastIndexOf('_') + 1);
			target = ProcedureIr.string(node.fields(), "variableId", name);
		} else if (node.type().equals("call_procedure")) {
			kind = "procedure";
			name = ProcedureIr.string(node.fields(), "procedure", "");
			target = ProcedureIr.string(node.fields(), "procedureId", name);
		} else if (node.type().contains("from_deps") || node.type().startsWith("coord_")) {
			kind = "context";
			name = node.type();
			dataType = node.type().contains("entity") ? "entity" : "number";
			target = node.type();
		}
		if (kind != null) dependencies.add(new Dependency(UUID.nameUUIDFromBytes(
				(node.id() + "\n" + kind + "\n" + target).getBytes(StandardCharsets.UTF_8)), kind, name, dataType, target));
	}

	private static Document parse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
	}

	private static Map<String, String> rawBlocks(String xml) {
		Map<String, String> result = new HashMap<>();
		Matcher starts = BLOCK_START.matcher(xml);
		while (starts.find()) {
			String opening = starts.group();
			String type = attribute(opening, "type");
			String id = attribute(opening, "id");
			if (type.isBlank()) continue;
			Matcher tokens = BLOCK_TOKEN.matcher(xml);
			tokens.region(starts.start(), xml.length());
			int depth = 0;
			int end = -1;
			while (tokens.find()) {
				String token = tokens.group().toLowerCase(Locale.ROOT);
				if (token.startsWith("</")) depth--;
				else if (!token.endsWith("/>")) depth++;
				if (depth == 0) { end = tokens.end(); break; }
			}
			if (end > starts.start()) result.putIfAbsent(identity(type, id), xml.substring(starts.start(), end));
		}
		return result;
	}

	private static String attribute(String opening, String name) {
		Matcher matcher = ATTRIBUTE.matcher(opening);
		while (matcher.find()) if (matcher.group(1).equalsIgnoreCase(name)) return matcher.group(3);
		return "";
	}

	private static String identity(String type, String id) { return type + "\n" + id; }
	private static UUID uuid(String value, String fallback) {
		try { return UUID.fromString(value); } catch (RuntimeException ignored) {
			return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8));
		}
	}
	private static double number(Element element, String name, double fallback) {
		try { return element.hasAttribute(name) ? Double.parseDouble(element.getAttribute(name)) : fallback; }
		catch (NumberFormatException ignored) { return fallback; }
	}
	private static String outputKind(Element block) {
		String type = block.getAttribute("type");
		return type.startsWith("math_") || type.startsWith("logic_") || type.equals("text")
				|| type.startsWith("variables_get_") || type.endsWith("_from_deps") || type.startsWith("coord_")
				|| type.startsWith("mcitem_") ? "value" : "statement";
	}
	private static String field(Element block, String name, String fallback) {
		for (Element field : directChildren(block, "field"))
			if (field.getAttribute("name").equals(name)) return field.getTextContent();
		return fallback;
	}
	private static List<Element> directChildren(Element parent, String name) {
		List<Element> result = new ArrayList<>();
		for (Element element : childElements(parent)) if (element.getTagName().equals(name)) result.add(element);
		return result;
	}
	private static List<Element> childElements(Element parent) {
		List<Element> result = new ArrayList<>();
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
			if (children.item(index) instanceof Element element) result.add(element);
		return result;
	}
	private static Element firstDirectChild(Element parent, String name) {
		for (Element element : childElements(parent)) if (element.getTagName().equals(name)) return element;
		return null;
	}
	private static String outerXml(Element element) {
		return "<block type=\"" + escape(element.getAttribute("type")) + "\"></block>";
	}
	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
	}
	private static String requiredString(JsonObject object, String key) {
		if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()
				|| object.get(key).getAsString().isBlank()) throw new IllegalArgumentException(key + " is required");
		return object.get(key).getAsString();
	}
	private static Node requiredNode(Map<UUID, Node> nodes, UUID id) {
		Node node = nodes.get(id);
		if (node == null) throw new IllegalArgumentException("Node does not exist: " + id);
		return node;
	}
}

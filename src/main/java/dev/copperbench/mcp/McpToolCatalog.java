/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.copperbench.automation.audit.AuditRecord;
import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.assets.AssetCategory;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetReferenceGraph;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.release.ElementCoverageCatalog;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.time.Clock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class McpToolCatalog {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
	private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of(),
			"additionalProperties", false);

	private final UUID workspaceId;
	private final McpWorkspaceEntryAdapter adapter;
	private final JsonLineAuditLog audit;
	private final Clock clock;
	private final AssetWorkspaceService assets;

	McpToolCatalog(UUID workspaceId, McpWorkspaceEntryAdapter adapter, JsonLineAuditLog audit, Clock clock) {
		this(workspaceId, adapter, audit, clock, null);
	}

	private McpServerFeatures.SyncToolSpecification createModElementTool() {
		Map<String, Object> schema = requiredSchema(Map.of(
				"elementType", Map.of("type", "string", "enum", ElementCoverageCatalog.FIRST_PARTY_SLICE),
				"name", Map.of("type", "string"),
				"initialValues", Map.of("type", "object"),
				"expectedRevision", Map.of("type", "integer", "minimum", 0)),
				List.of("elementType", "name", "initialValues", "expectedRevision"));
		return McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder("create_mod_element", schema)
						.description("Create a mod element; code elements also schedule a real Gradle compile verification task")
						.build())
				.callHandler((exchange, request) -> {
					try {
						audit("create_mod_element", request.arguments(), "started", 0, "");
						long revision = request.arguments().get("expectedRevision") instanceof Number number
								? number.longValue() : 0;
						JsonObject payload = mutationPayload(request.arguments());
						payload.addProperty("clientMutationId", UUID.randomUUID().toString());
						var outcome = adapter.execute(Command.of(UUID.randomUUID(), workspaceId, revision,
								Operation.CREATE_MOD_ELEMENT, payload));
						var result = outcome.result();
						JsonObject response = GSON.toJsonTree(result).getAsJsonObject();
						if ("committed".equals(result.status()) && "code".equals(request.arguments().get("elementType"))) {
							JsonObject buildPayload = workspacePayload(Map.of());
							buildPayload.addProperty("clientMutationId", UUID.randomUUID().toString());
							var verification = adapter.execute(Command.of(UUID.randomUUID(), workspaceId, result.newRevision(),
									Operation.BUILD_WORKSPACE, buildPayload)).result();
							JsonObject verificationJson = new JsonObject();
							verificationJson.addProperty("operation", "build_workspace");
							verificationJson.addProperty("status", verification.status());
							verificationJson.add("task", verification.task().deepCopy());
							verificationJson.add("diagnostics", GSON.toJsonTree(verification.diagnostics()));
							JsonObject data = response.has("data") && response.get("data").isJsonObject()
									? response.getAsJsonObject("data") : new JsonObject();
							data.add("compileVerification", verificationJson);
							response.add("data", data);
						}
						boolean error = result.status().equals("rejected") || result.status().equals("failed");
						audit("create_mod_element", request.arguments(), result.status(), result.newRevision(), "");
						return text(GSON.toJson(response), error);
					} catch (AuditUnavailableException exception) {
						return auditUnavailable();
					}
				}).build();
	}

	private static Map<String, Object> workspacePlanSchema() {
		Map<String, Object> step = Map.of(
				"type", "object",
				"properties", Map.of(
						"operation", Map.of("type", "string", "enum", List.of(
								"create_mod_element", "update_mod_element", "delete_mod_element", "update_procedure",
								"create_registry_entry", "update_registry_entry", "delete_registry_entry",
								"rename_registry_entry")),
						"payload", Map.of("type", "object")),
				"required", List.of("operation", "payload"),
				"additionalProperties", false);
		return requiredSchema(Map.of(
				"expectedRevision", Map.of("type", "integer", "minimum", 0),
				"idempotencyKey", Map.of("type", "string", "minLength", 1, "maxLength", 128),
				"operations", Map.of("type", "array", "items", step, "minItems", 1, "maxItems", 100)),
				List.of("expectedRevision", "idempotencyKey", "operations"));
	}

	private static Map<String, Object> planEnvelopeSchema(boolean command) {
		Map<String, Object> properties = command
				? Map.of("plan", Map.of("type", "object"), "expectedRevision",
						Map.of("type", "integer", "minimum", 0))
				: Map.of("plan", Map.of("type", "object"));
		return Map.of("type", "object", "properties", properties, "required",
				command ? List.of("plan", "expectedRevision") : List.of("plan"), "additionalProperties", false);
	}

	McpToolCatalog(UUID workspaceId, McpWorkspaceEntryAdapter adapter, JsonLineAuditLog audit, Clock clock,
			AssetWorkspaceService assets) {
		this.workspaceId = workspaceId;
		this.adapter = adapter;
		this.audit = audit;
		this.clock = clock;
		this.assets = assets;
	}

	List<McpServerFeatures.SyncToolSpecification> tools() {
		List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
		tools.add(queryTool("get_workspace", "Read workspace state", Operation.GET_WORKBENCH, EMPTY_SCHEMA,
				arguments -> new JsonObject()));
		tools.add(queryTool("list_new_workspace_generators", "List generators available for new workspaces",
				Operation.LIST_NEW_WORKSPACE_GENERATORS, EMPTY_SCHEMA, arguments -> new JsonObject()));
		tools.add(commandTool("create_workspace", "Create a new workspace after explicit user approval",
				Operation.CREATE_WORKSPACE,
				requiredSchema(Map.of("generatorId", Map.of("type", "string", "minLength", 1),
						"modName", Map.of("type", "string", "minLength", 1),
						"modId", Map.of("type", "string", "minLength", 1),
						"packageName", Map.of("type", "string"),
						"workspaceFolderPath", Map.of("type", "string", "minLength", 1),
						"version", Map.of("type", "string"),
						"userApproved", Map.of("type", "boolean"),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("generatorId", "modName", "modId", "workspaceFolderPath", "userApproved",
								"expectedRevision")),
				McpToolCatalog::mutationPayload));
		tools.add(queryTool("list_mod_elements", "List mod elements", Operation.LIST_MOD_ELEMENTS,
				Map.of("type", "object", "properties", Map.of(
						"search", Map.of("type", "string"),
						"cursor", Map.of("type", "string"),
						"limit", Map.of("type", "integer", "minimum", 1, "maximum", 200),
						"sort", Map.of("type", "string", "enum", List.of("name", "-name", "displayName",
								"-displayName", "type", "-type", "state", "-state", "updatedAt", "-updatedAt")),
						"filter", Map.of("type", "object", "properties", Map.of(
								"search", Map.of("type", "string"),
								"types", Map.of("type", "array", "items", Map.of("type", "string")),
								"states", Map.of("type", "array", "items", Map.of("type", "string")),
								"firstParty", Map.of("type", "boolean")), "additionalProperties", false),
						"fields", Map.of("type", "array", "items", Map.of("type", "string"), "uniqueItems", true))), arguments -> {
					JsonObject payload = GSON.toJsonTree(arguments).getAsJsonObject();
					if (!payload.has("limit")) payload.addProperty("limit", 200);
					if (!payload.has("sort")) payload.addProperty("sort", "name");
					if (!payload.has("filter")) payload.add("filter", new JsonObject());
					return payload;
				}));
		tools.add(queryTool("read_mod_element", "Read a mod element", Operation.GET_MOD_ELEMENT_EDITOR,
				elementSchema(false), arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("preview_mod_element_change", "Preview validated element changes without committing",
				Operation.PREVIEW_MOD_ELEMENT_CHANGE, elementSchema(true),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("get_procedure", "Read a Procedure as structured IR",
				Operation.GET_PROCEDURE_EDITOR, elementSchema(false),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("preview_procedure_change", "Preview structured Procedure graph edits",
				Operation.PREVIEW_PROCEDURE_CHANGE, procedureSchema(false),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("get_workspace_references", "Read the structured workspace reference graph",
				Operation.GET_WORKSPACE_REFERENCES,
				Map.of("type", "object", "properties", Map.of("target", Map.of("type", "string")),
						"additionalProperties", false),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("list_workspace_registries", "List variables, tags, and language keys with stable IDs",
				Operation.LIST_WORKSPACE_REGISTRIES,
				Map.of("type", "object", "properties", Map.of(
						"registry", Map.of("type", "string", "enum", List.of("variables", "tags", "languageKeys")),
						"cursor", Map.of("type", "string"),
						"limit", Map.of("type", "integer", "minimum", 1, "maximum", 200),
						"sort", Map.of("type", "string", "enum", List.of("name", "-name", "kind", "-kind", "id", "-id")),
						"filter", Map.of("type", "object", "properties", Map.of(
								"search", Map.of("type", "string")), "additionalProperties", false),
						"fields", Map.of("type", "array", "items", Map.of("type", "string"), "uniqueItems", true)),
						"additionalProperties", false), arguments -> {
					JsonObject payload = GSON.toJsonTree(arguments).getAsJsonObject();
					if (payload.has("registry")) {
						if (!payload.has("limit")) payload.addProperty("limit", 200);
						if (!payload.has("sort")) payload.addProperty("sort", "name");
						if (!payload.has("filter")) payload.add("filter", new JsonObject());
					}
					return payload;
				}));
		tools.add(queryTool("preview_registry_rename", "Preview reference-aware registry rename impact",
				Operation.PREVIEW_REGISTRY_RENAME,
				requiredSchema(Map.of("entryId", Map.of("type", "string", "format", "uuid"), "newName",
						Map.of("type", "string", "minLength", 1)), List.of("entryId", "newName")),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("plan_workspace_changes",
				"Plan an ordered set of workspace mutations against one base revision without changing the workspace",
				Operation.PLAN_WORKSPACE_CHANGES, workspacePlanSchema(),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(queryTool("preview_workspace_plan",
				"Revalidate a workspace plan and return its semantic diff, permission assessment, and stale state",
				Operation.PREVIEW_WORKSPACE_PLAN, planEnvelopeSchema(false),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(commandTool("apply_workspace_plan",
				"Apply a validated workspace plan as one revision with one recovery point and full rollback",
				Operation.APPLY_WORKSPACE_PLAN, planEnvelopeSchema(true), McpToolCatalog::mutationPayload));
		tools.add(createModElementTool());
		tools.add(commandTool("update_mod_element", "Update a mod element", Operation.UPDATE_MOD_ELEMENT,
				requiredSchema(Map.of("elementId", Map.of("type", "string", "format", "uuid"), "changes",
						Map.of("type", "array", "items", Map.of("type", "object"), "minItems", 1),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("elementId", "changes", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(commandTool("update_procedure", "Commit structured Procedure graph edits",
				Operation.UPDATE_PROCEDURE, procedureSchema(true), McpToolCatalog::mutationPayload));
		tools.add(commandTool("create_registry_entry", "Create a variable, tag, or language key",
				Operation.CREATE_REGISTRY_ENTRY,
				requiredSchema(Map.of("registry", Map.of("type", "string", "enum",
						List.of("variables", "tags", "languageKeys")), "entry", Map.of("type", "object"),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("registry", "entry", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(commandTool("update_registry_entry", "Update registry entry fields without changing its stable ID",
				Operation.UPDATE_REGISTRY_ENTRY,
				requiredSchema(Map.of("entryId", Map.of("type", "string", "format", "uuid"), "changes",
						Map.of("type", "array", "items", Map.of("type", "object"), "minItems", 1),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("entryId", "changes", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(commandTool("delete_registry_entry", "Delete an unreferenced registry entry",
				Operation.DELETE_REGISTRY_ENTRY,
				requiredSchema(Map.of("entryId", Map.of("type", "string", "format", "uuid"), "force",
						Map.of("type", "boolean"), "expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("entryId", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(commandTool("rename_registry_entry", "Rename a registry entry and update structured references",
				Operation.RENAME_REGISTRY_ENTRY,
				requiredSchema(Map.of("entryId", Map.of("type", "string", "format", "uuid"), "newName",
						Map.of("type", "string", "minLength", 1), "expectedRevision",
						Map.of("type", "integer", "minimum", 0)),
						List.of("entryId", "newName", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(commandTool("delete_mod_element", "Delete a mod element", Operation.DELETE_MOD_ELEMENT,
				requiredSchema(Map.of("elementId", Map.of("type", "string", "format", "uuid"),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("elementId", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(commandTool("validate_workspace", "Validate the workspace", Operation.VALIDATE_WORKSPACE,
				revisionSchema(), McpToolCatalog::workspacePayload));
		tools.add(commandTool("generate_workspace", "Generate Fabric 1.21.1 sources", Operation.GENERATE_WORKSPACE,
				revisionSchema(), McpToolCatalog::workspacePayload));
		tools.add(commandTool("build_workspace", "Build the Fabric 1.21.1 mod", Operation.BUILD_WORKSPACE,
				revisionSchema(), McpToolCatalog::workspacePayload));
		tools.add(commandTool("export_workspace", "Build and export the Fabric artifact", Operation.EXPORT_WORKSPACE,
				requiredSchema(Map.of("output", Map.of("type", "string", "minLength", 1), "expectedRevision",
						Map.of("type", "integer", "minimum", 0)), List.of("output", "expectedRevision")),
				McpToolCatalog::workspaceArgumentsPayload));
		tools.add(commandTool("run_client", "Run the Fabric client smoke test", Operation.RUN_CLIENT,
				revisionSchema(), McpToolCatalog::workspacePayload));
		tools.add(commandTool("run_server", "Run an isolated dedicated server after desktop EULA approval",
				Operation.RUN_SERVER,
				requiredSchema(Map.of("expectedRevision", Map.of("type", "integer", "minimum", 0),
						"userApproved", Map.of("type", "boolean")), List.of("expectedRevision", "userApproved")),
				arguments -> {
					JsonObject payload = mutationPayload(arguments);
					payload.addProperty("scope", "workspace");
					return payload;
				}));
		tools.add(commandTool("run_datagen", "Run data generation in an isolated staging workspace",
				Operation.RUN_DATAGEN, revisionSchema(), McpToolCatalog::workspacePayload));
		tools.add(queryTool("preview_datagen_output", "Preview staged datagen files before workspace publication",
				Operation.PREVIEW_DATAGEN_OUTPUT,
				requiredSchema(Map.of("taskId", Map.of("type", "string", "format", "uuid")), List.of("taskId")),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(commandTool("publish_datagen_output",
				"Publish an unchanged staged datagen manifest into the workspace with rollback protection",
				Operation.PUBLISH_DATAGEN_OUTPUT,
				requiredSchema(Map.of("taskId", Map.of("type", "string", "format", "uuid"), "manifestHash",
						Map.of("type", "string", "pattern", "^[a-f0-9]{64}$"), "expectedRevision",
						Map.of("type", "integer", "minimum", 0)),
						List.of("taskId", "manifestHash", "expectedRevision")),
				McpToolCatalog::mutationPayload));
		tools.add(commandTool("run_gametest", "Run existing GameTests and collect their task logs",
				Operation.RUN_GAMETEST, revisionSchema(), McpToolCatalog::workspacePayload));
		tools.add(queryTool("get_task", "Read task state, logs and diagnostics", Operation.GET_TASK,
				requiredSchema(Map.of("taskId", Map.of("type", "string", "format", "uuid"),
						"afterLogSequence", Map.of("type", "integer", "minimum", 0)),
						List.of("taskId", "afterLogSequence")),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(commandTool("cancel_task", "Cancel a queued or running workspace task", Operation.CANCEL_TASK,
				requiredSchema(Map.of("taskId", Map.of("type", "string", "format", "uuid"),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("taskId", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder("create_recovery_point", Map.of("type", "object", "properties",
						Map.of("label", Map.of("type", "string", "minLength", 1),
								"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						"required", List.of("label", "expectedRevision")))
						.description("Create a local recovery point").build())
				.callHandler((exchange, request) -> {
					try {
						audit("create_recovery_point", request.arguments(), "started", 0, "");
						String label = String.valueOf(request.arguments().get("label"));
						long revision = ((Number) request.arguments().get("expectedRevision")).longValue();
						JsonObject payload = new JsonObject();
						payload.addProperty("clientMutationId", UUID.randomUUID().toString());
						payload.addProperty("label", label);
						var outcome = adapter.execute(Command.of(UUID.randomUUID(), workspaceId, revision,
								Operation.CREATE_RECOVERY_POINT, payload));
						var result = outcome.result();
						boolean error = result.status().equals("rejected") || result.status().equals("failed");
						audit("create_recovery_point", request.arguments(), result.status(), result.newRevision(),
								result.recoveryPointId() == null ? "" : result.recoveryPointId());
						return text(GSON.toJson(result), error);
					} catch (AuditUnavailableException exception) {
						return auditUnavailable();
					} catch (RuntimeException exception) {
						return text("{\"code\":\"RECOVERY_POINT_FAILED\"}", true);
					}
					}).build());
		tools.add(commandTool("restore_recovery_point",
				"Request a protected recovery-point restore; MCP clients cannot self-approve the desktop confirmation",
				Operation.RESTORE_RECOVERY_POINT,
				requiredSchema(Map.of("recoveryPointId", Map.of("type", "string", "minLength", 1),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("recoveryPointId", "expectedRevision")), McpToolCatalog::mutationPayload));
		tools.add(queryTool("list_recovery_points", "List local history recovery points", Operation.GET_HISTORY,
				Map.of("type", "object", "properties", Map.of(
						"cursor", Map.of("type", "string"),
						"limit", Map.of("type", "integer", "minimum", 1, "maximum", 200),
						"sort", Map.of("type", "string", "enum", List.of("createdAt", "-createdAt", "label",
								"-label", "actor", "-actor")),
						"filter", Map.of("type", "object", "properties", Map.of(
								"search", Map.of("type", "string"),
								"actor", Map.of("type", "string", "enum", List.of("ui", "mcp", "headless",
										"legacy_ui", "system"))), "additionalProperties", false),
						"fields", Map.of("type", "array", "items", Map.of("type", "string"), "uniqueItems", true))),
				arguments -> {
					JsonObject payload = GSON.toJsonTree(arguments).getAsJsonObject();
					if (!payload.has("limit")) payload.addProperty("limit", 200);
					if (!payload.has("sort")) payload.addProperty("sort", "-createdAt");
					if (!payload.has("filter")) payload.add("filter", new JsonObject());
					return payload;
				}));
		tools.add(queryTool("get_version_tracks", "Read the four-track Fabric/NeoForge support matrix",
				Operation.GET_VERSION_TRACKS, EMPTY_SCHEMA, arguments -> new JsonObject()));
		tools.add(queryTool("get_release_notes", "Read Stage 8 release notes, support matrix, and G7 status",
				Operation.GET_RELEASE_NOTES, EMPTY_SCHEMA, arguments -> new JsonObject()));
		tools.add(queryTool("list_installed_plugins",
				"List first-party and user plugins with A/B/C/X classification without loading Java",
				Operation.LIST_INSTALLED_PLUGINS, EMPTY_SCHEMA, arguments -> new JsonObject()));
		tools.add(queryTool("get_element_coverage",
				"Read the complete Stage 11 Java mod-element catalog and out-of-scope Bedrock types",
				Operation.GET_ELEMENT_COVERAGE, EMPTY_SCHEMA, arguments -> new JsonObject()));
		tools.add(queryTool("get_upstream_tools",
				"Read how upstream MCreator tools map onto new UI, legacy window, unsupported, or out of scope",
				Operation.GET_UPSTREAM_TOOLS, EMPTY_SCHEMA, arguments -> new JsonObject()));
		tools.add(queryTool("preview_loader_migration", "Preview a copy-only Fabric/NeoForge migration",
				Operation.PREVIEW_LOADER_MIGRATION,
				requiredSchema(Map.of("targetGeneratorId", Map.of("type", "string", "minLength", 1)),
						List.of("targetGeneratorId")),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(commandTool("execute_loader_migration", "Copy the workspace to another loader on the same version",
				Operation.EXECUTE_LOADER_MIGRATION,
				requiredSchema(Map.of("targetGeneratorId", Map.of("type", "string", "minLength", 1),
						"outputName", Map.of("type", "string"), "userApproved", Map.of("type", "boolean"),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("targetGeneratorId", "outputName", "userApproved", "expectedRevision")),
				McpToolCatalog::mutationPayload));
		tools.add(queryTool("preview_upstream_import", "Preview an upstream MCreator workspace import",
				Operation.PREVIEW_UPSTREAM_IMPORT,
				requiredSchema(Map.of("sourceWorkspacePath", Map.of("type", "string", "minLength", 1)),
						List.of("sourceWorkspacePath")),
				arguments -> GSON.toJsonTree(arguments).getAsJsonObject()));
		tools.add(commandTool("import_upstream_workspace", "Copy an upstream MCreator workspace without modifying source",
				Operation.IMPORT_UPSTREAM_WORKSPACE,
				requiredSchema(Map.of("sourceWorkspacePath", Map.of("type", "string", "minLength", 1),
						"outputName", Map.of("type", "string"), "userApproved", Map.of("type", "boolean"),
						"expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("sourceWorkspacePath", "outputName", "userApproved", "expectedRevision")),
				McpToolCatalog::mutationPayload));
		tools.add(queryTool("list_publish_batches", "List asset publish batches", Operation.LIST_PUBLISH_BATCHES,
				Map.of("type", "object", "properties", Map.of(
						"cursor", Map.of("type", "string"),
						"limit", Map.of("type", "integer", "minimum", 1, "maximum", 200),
						"sort", Map.of("type", "string", "enum", List.of("createdAt", "-createdAt", "name",
								"-name", "assetCount", "-assetCount")),
						"filter", Map.of("type", "object", "properties", Map.of(
								"search", Map.of("type", "string")), "additionalProperties", false),
						"fields", Map.of("type", "array", "items", Map.of("type", "string"), "uniqueItems", true))),
				arguments -> {
					JsonObject payload = GSON.toJsonTree(arguments).getAsJsonObject();
					if (!payload.has("limit")) payload.addProperty("limit", 200);
					if (!payload.has("sort")) payload.addProperty("sort", "-createdAt");
					if (!payload.has("filter")) payload.add("filter", new JsonObject());
					return payload;
				}));
		tools.add(commandTool("create_publish_batch", "Create a hashed resource-pack publish batch",
				Operation.CREATE_PUBLISH_BATCH,
				requiredSchema(Map.of("name", Map.of("type", "string"), "sourceDirectory", Map.of("type", "string"),
						"output", Map.of("type", "string"), "expectedRevision",
						Map.of("type", "integer", "minimum", 0)),
						List.of("name", "sourceDirectory", "output", "expectedRevision")),
				McpToolCatalog::mutationPayload));
		tools.add(commandTool("prepare_resource_pack_client",
				"Export a resource pack into run/resourcepacks and write client options",
				Operation.PREPARE_RESOURCE_PACK_CLIENT,
				requiredSchema(Map.of("sourceDirectory", Map.of("type", "string"), "zipFileName",
						Map.of("type", "string"), "expectedRevision", Map.of("type", "integer", "minimum", 0)),
						List.of("sourceDirectory", "zipFileName", "expectedRevision")),
				McpToolCatalog::mutationPayload));
		if (assets != null) {
			tools.add(assetListTool());
			tools.add(assetReferencesTool());
		}
		return List.copyOf(tools);
	}

	private McpServerFeatures.SyncToolSpecification assetListTool() {
		Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
				"search", Map.of("type", "string"),
				"category", Map.of("type", "string", "enum", List.of("MODEL", "TEXTURE", "ANIMATION",
						"LANGUAGE", "SOUND", "RESOURCE_PACK", "BLOCKSTATE", "OTHER"))),
				"additionalProperties", false);
		return McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder("list_assets", schema).description("List indexed workspace assets by path or category").build())
				.callHandler((exchange, request) -> {
					try {
						audit("list_assets", request.arguments(), "started", 0, "");
						String search = request.arguments() == null ? "" : String.valueOf(request.arguments().getOrDefault("search", ""));
						AssetCategory category = parseCategory(request.arguments() == null ? null : request.arguments().get("category"));
						List<AssetDescriptor> result = assets.search(search, category);
						audit("list_assets", request.arguments(), "succeeded", 0, "");
						return text(GSON.toJson(Map.of("status", "succeeded", "assets", result)), false);
					} catch (AuditUnavailableException exception) {
						return auditUnavailable();
					} catch (RuntimeException exception) {
						return text(GSON.toJson(Map.of("status", "failed", "code", "ASSET_QUERY_FAILED")), true);
					}
				}).build();
	}

	private McpServerFeatures.SyncToolSpecification assetReferencesTool() {
		Map<String, Object> schema = requiredSchema(Map.of("sourcePath", Map.of("type", "string", "minLength", 1)),
				List.of("sourcePath"));
		return McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder("inspect_asset_references", schema)
						.description("Inspect outgoing references and diagnostics for one asset").build())
				.callHandler((exchange, request) -> {
					try {
						audit("inspect_asset_references", request.arguments(), "started", 0, "");
						String sourcePath = String.valueOf(request.arguments().get("sourcePath"));
						AssetReferenceGraph graph = assets.buildReferenceGraph();
						var references = graph.outgoing(sourcePath);
						var diagnostics = graph.diagnostics().stream()
								.filter(diagnostic -> diagnostic.sourcePath().equals(sourcePath)).toList();
						audit("inspect_asset_references", request.arguments(), "succeeded", 0, "");
						return text(GSON.toJson(Map.of("status", "succeeded", "sourcePath", sourcePath,
								"references", references, "diagnostics", diagnostics)), false);
					} catch (AuditUnavailableException exception) {
						return auditUnavailable();
					} catch (RuntimeException exception) {
						return text(GSON.toJson(Map.of("status", "failed", "code", "ASSET_QUERY_FAILED")), true);
					}
				}).build();
	}

	private static AssetCategory parseCategory(Object value) {
		if (value == null || String.valueOf(value).isBlank()) return null;
		try {
			return AssetCategory.valueOf(String.valueOf(value).toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private McpServerFeatures.SyncToolSpecification queryTool(String name, String description, Operation operation,
			Map<String, Object> schema, java.util.function.Function<Map<String, Object>, JsonObject> payloadFactory) {
		return McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder(name, schema).description(description).build())
				.callHandler((exchange, request) -> {
					try {
						audit(name, request.arguments(), "started", 0, "");
						var result = adapter.query(Query.of(UUID.randomUUID(), workspaceId, operation,
								payloadFactory.apply(request.arguments())));
						boolean error = !result.status().equals("succeeded");
						audit(name, request.arguments(), result.status(), result.revision(), "");
						return text(GSON.toJson(result), error);
					} catch (AuditUnavailableException exception) {
						return auditUnavailable();
					}
				}).build();
	}

	private McpServerFeatures.SyncToolSpecification commandTool(String name, String description, Operation operation,
			Map<String, Object> schema, java.util.function.Function<Map<String, Object>, JsonObject> payloadFactory) {
		return McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder(name, schema).description(description).build())
				.callHandler((exchange, request) -> {
					try {
						audit(name, request.arguments(), "started", 0, "");
						long revision = request.arguments().get("expectedRevision") instanceof Number number
								? number.longValue() : 0;
						JsonObject payload = payloadFactory.apply(request.arguments());
						payload.addProperty("clientMutationId", UUID.randomUUID().toString());
						var result = adapter.execute(Command.of(UUID.randomUUID(), workspaceId, revision, operation, payload));
						boolean error = result.result().status().equals("rejected")
								|| result.result().status().equals("failed");
						audit(name, request.arguments(), result.result().status(), result.result().newRevision(), "");
						return text(GSON.toJson(result.result()), error);
					} catch (AuditUnavailableException exception) {
						return auditUnavailable();
					}
				}).build();
	}

	private static JsonObject workspacePayload(Map<String, Object> arguments) {
		JsonObject payload = new JsonObject();
		payload.addProperty("scope", "workspace");
		return payload;
	}

	private static JsonObject mutationPayload(Map<String, Object> arguments) {
		JsonObject payload = GSON.toJsonTree(arguments).getAsJsonObject();
		payload.remove("expectedRevision");
		return payload;
	}

	private static JsonObject workspaceArgumentsPayload(Map<String, Object> arguments) {
		JsonObject payload = mutationPayload(arguments);
		payload.addProperty("scope", "workspace");
		return payload;
	}

	private static Map<String, Object> revisionSchema() {
		return requiredSchema(Map.of("expectedRevision", Map.of("type", "integer", "minimum", 0)),
				List.of("expectedRevision"));
	}

	private static Map<String, Object> requiredSchema(Map<String, Object> properties, List<String> required) {
		return Map.of("type", "object", "properties", properties, "required", required,
				"additionalProperties", false);
	}

	private void audit(String tool, Map<String, Object> arguments, String result, long revision, String recoveryPoint) {
		try {
			audit.append(new AuditRecord(clock.instant(), "mcp-client", tool, GSON.toJson(arguments), result, revision,
					recoveryPoint));
		} catch (IOException exception) {
			throw new AuditUnavailableException(exception);
		}
	}

	private static CallToolResult auditUnavailable() {
		return text("{\"code\":\"AUDIT_LOG_UNAVAILABLE\"}", true);
	}

	private static CallToolResult text(String value, boolean error) {
		return CallToolResult.builder().content(List.of(TextContent.builder(value).build())).isError(error).build();
	}

	private static Map<String, Object> elementSchema(boolean changes) {
		Map<String, Object> properties = changes
				? Map.of("elementId", Map.of("type", "string", "format", "uuid"), "changes",
						Map.of("type", "array", "items", Map.of("type", "object"), "minItems", 1))
				: Map.of("elementId", Map.of("type", "string", "format", "uuid"));
		return Map.of("type", "object", "properties", properties, "required",
				changes ? List.of("elementId", "changes") : List.of("elementId"));
	}

	private static Map<String, Object> procedureSchema(boolean command) {
		Map<String, Object> properties = command
				? Map.of("elementId", Map.of("type", "string", "format", "uuid"), "edits",
						Map.of("type", "array", "items", Map.of("type", "object"), "minItems", 1),
						"expectedRevision", Map.of("type", "integer", "minimum", 0))
				: Map.of("elementId", Map.of("type", "string", "format", "uuid"), "edits",
						Map.of("type", "array", "items", Map.of("type", "object"), "minItems", 1));
		return Map.of("type", "object", "properties", properties, "required",
				command ? List.of("elementId", "edits", "expectedRevision") : List.of("elementId", "edits"),
				"additionalProperties", false);
	}

	private static final class AuditUnavailableException extends RuntimeException {

		private AuditUnavailableException(IOException cause) {
			super(cause);
		}
	}
}

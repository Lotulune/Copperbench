/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class HeadlessCli {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final HeadlessWorkspaceEntryAdapter adapter;
	private final UUID workspaceId;
	private final Supplier<UUID> ids;

	public HeadlessCli(HeadlessWorkspaceEntryAdapter adapter, UUID workspaceId, Supplier<UUID> ids) {
		this.adapter = adapter;
		this.workspaceId = workspaceId;
		this.ids = ids;
	}

	public int run(String[] arguments, PrintWriter output) {
		try {
			ParsedCommand parsed = parse(arguments);
			if (parsed.help()) {
				JsonObject help = envelope("help", "completed", HeadlessExitCode.SUCCESS);
				JsonArray commands = new JsonArray();
				commands.add("validate");
				commands.add("build");
				commands.add("run-server");
				commands.add("run-datagen");
				commands.add("preview-datagen");
				commands.add("publish-datagen");
				commands.add("run-gametest");
				commands.add("export");
				commands.add("list-new-workspace-generators");
				commands.add("create-workspace");
				commands.add("tracks");
				commands.add("release");
				commands.add("preview-migrate");
				commands.add("migrate");
				commands.add("preview-import");
				commands.add("import");
				commands.add("publish-batch");
				commands.add("prepare-resource-pack");
				commands.add("plugins");
				commands.add("elements");
				commands.add("upstream-tools");
				commands.add("procedure");
				commands.add("preview-procedure");
				commands.add("update-procedure");
				commands.add("references");
				commands.add("registries");
				commands.add("preview-registry-rename");
				commands.add("create-registry-entry");
				commands.add("update-registry-entry");
				commands.add("delete-registry-entry");
				commands.add("rename-registry-entry");
				help.add("commands", commands);
				write(output, help);
				return HeadlessExitCode.SUCCESS.code();
			}

			if (parsed.query()) {
				QueryResult result = adapter.query(Query.of(ids.get(), workspaceId, parsed.operation(), parsed.payload()));
				HeadlessExitCode exitCode = result.status().equals("succeeded")
						? HeadlessExitCode.SUCCESS : HeadlessExitCode.INTERNAL_ERROR;
				JsonObject response = GSON.toJsonTree(result).getAsJsonObject();
				response.addProperty("schemaVersion", "1.0");
				response.addProperty("exitCode", exitCode.code());
				write(output, response);
				return exitCode.code();
			}
			JsonObject payload = parsed.payload();
			payload.addProperty("clientMutationId", ids.get().toString());
			if (!payload.has("scope"))
				payload.addProperty("scope", "workspace");
			Command command = Command.of(ids.get(), workspaceId, parsed.revision(), parsed.operation(), payload);
			CommandOutcome outcome = adapter.execute(command);
			HeadlessExitCode exitCode = exitCode(outcome);
			JsonObject response = GSON.toJsonTree(outcome.result()).getAsJsonObject();
			response.addProperty("schemaVersion", "1.0");
			response.addProperty("exitCode", exitCode.code());
			write(output, response);
			return exitCode.code();
		} catch (IllegalArgumentException exception) {
			JsonObject response = envelope("unknown", "rejected", HeadlessExitCode.INVALID_ARGUMENTS);
			response.addProperty("code", "HEADLESS_INVALID_ARGUMENTS");
			response.addProperty("message", exception.getMessage());
			write(output, response);
			return HeadlessExitCode.INVALID_ARGUMENTS.code();
		} catch (RuntimeException exception) {
			JsonObject response = envelope("unknown", "failed", HeadlessExitCode.INTERNAL_ERROR);
			response.addProperty("code", "HEADLESS_INTERNAL_ERROR");
			response.addProperty("message", "Headless command failed.");
			write(output, response);
			return HeadlessExitCode.INTERNAL_ERROR.code();
		}
	}

	private static ParsedCommand parse(String[] arguments) {
		if (arguments.length == 0)
			throw new IllegalArgumentException("A command is required");
		if (arguments[0].equals("help") || arguments[0].equals("--help"))
			return new ParsedCommand(null, 0, new JsonObject(), true, false);
		boolean query = switch (arguments[0]) {
			case "list-new-workspace-generators", "tracks", "release", "preview-migrate", "preview-import", "plugins",
					"elements", "upstream-tools", "procedure", "preview-procedure", "references", "registries",
					"preview-registry-rename", "preview-datagen" -> true;
			default -> false;
		};
		Operation operation = switch (arguments[0]) {
			case "validate" -> Operation.VALIDATE_WORKSPACE;
			case "build" -> Operation.BUILD_WORKSPACE;
			case "run-server" -> Operation.RUN_SERVER;
			case "run-datagen" -> Operation.RUN_DATAGEN;
			case "preview-datagen" -> Operation.PREVIEW_DATAGEN_OUTPUT;
			case "publish-datagen" -> Operation.PUBLISH_DATAGEN_OUTPUT;
			case "run-gametest" -> Operation.RUN_GAMETEST;
			case "export" -> Operation.EXPORT_WORKSPACE;
			case "list-new-workspace-generators" -> Operation.LIST_NEW_WORKSPACE_GENERATORS;
			case "create-workspace" -> Operation.CREATE_WORKSPACE;
			case "tracks" -> Operation.GET_VERSION_TRACKS;
			case "release" -> Operation.GET_RELEASE_NOTES;
			case "plugins" -> Operation.LIST_INSTALLED_PLUGINS;
			case "elements" -> Operation.GET_ELEMENT_COVERAGE;
			case "upstream-tools" -> Operation.GET_UPSTREAM_TOOLS;
			case "procedure" -> Operation.GET_PROCEDURE_EDITOR;
			case "preview-procedure" -> Operation.PREVIEW_PROCEDURE_CHANGE;
			case "update-procedure" -> Operation.UPDATE_PROCEDURE;
			case "references" -> Operation.GET_WORKSPACE_REFERENCES;
			case "registries" -> Operation.LIST_WORKSPACE_REGISTRIES;
			case "preview-registry-rename" -> Operation.PREVIEW_REGISTRY_RENAME;
			case "create-registry-entry" -> Operation.CREATE_REGISTRY_ENTRY;
			case "update-registry-entry" -> Operation.UPDATE_REGISTRY_ENTRY;
			case "delete-registry-entry" -> Operation.DELETE_REGISTRY_ENTRY;
			case "rename-registry-entry" -> Operation.RENAME_REGISTRY_ENTRY;
			case "preview-migrate" -> Operation.PREVIEW_LOADER_MIGRATION;
			case "migrate" -> Operation.EXECUTE_LOADER_MIGRATION;
			case "preview-import" -> Operation.PREVIEW_UPSTREAM_IMPORT;
			case "import" -> Operation.IMPORT_UPSTREAM_WORKSPACE;
			case "publish-batch" -> Operation.CREATE_PUBLISH_BATCH;
			case "prepare-resource-pack" -> Operation.PREPARE_RESOURCE_PACK_CLIENT;
			default -> throw new IllegalArgumentException("Unknown command: " + arguments[0]);
		};
		Map<String, String> options = new LinkedHashMap<>();
		for (int index = 1; index < arguments.length; index += 2) {
			if (index + 1 >= arguments.length || !arguments[index].startsWith("--"))
				throw new IllegalArgumentException("Options must use --name value pairs");
			options.put(arguments[index], arguments[index + 1]);
		}
		Set<String> allowed = Set.of("--revision", "--output", "--target", "--output-name", "--approve", "--source",
				"--name", "--pack", "--source-directory", "--generator-id", "--mod-name", "--mod-id",
				"--package-name", "--workspace-folder", "--version", "--element-id", "--edits-json", "--registry",
				"--entry-json", "--entry-id", "--changes-json", "--new-name", "--force", "--task-id",
				"--manifest-hash");
		for (String option : options.keySet()) {
			if (!allowed.contains(option))
				throw new IllegalArgumentException("Unknown option: " + option);
		}
		long revision;
		try {
			revision = Long.parseLong(options.getOrDefault("--revision", "0"));
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("--revision must be an integer");
		}
		if (revision < 0)
			throw new IllegalArgumentException("--revision must not be negative");
		JsonObject payload = new JsonObject();
		if (options.containsKey("--output"))
			payload.addProperty("output", options.get("--output"));
		if (options.containsKey("--target")) {
			if (operation == Operation.GET_WORKSPACE_REFERENCES) payload.addProperty("target", options.get("--target"));
			else payload.addProperty("targetGeneratorId", options.get("--target"));
		}
		if (options.containsKey("--element-id"))
			payload.addProperty("elementId", options.get("--element-id"));
		if (options.containsKey("--task-id"))
			payload.addProperty("taskId", options.get("--task-id"));
		if (options.containsKey("--manifest-hash"))
			payload.addProperty("manifestHash", options.get("--manifest-hash"));
		if (options.containsKey("--edits-json")) {
			var edits = JsonParser.parseString(options.get("--edits-json"));
			if (!edits.isJsonArray() || edits.getAsJsonArray().isEmpty())
				throw new IllegalArgumentException("--edits-json must be a non-empty JSON array");
			payload.add("edits", edits);
		}
		if (options.containsKey("--registry")) payload.addProperty("registry", options.get("--registry"));
		if (options.containsKey("--entry-id")) payload.addProperty("entryId", options.get("--entry-id"));
		if (options.containsKey("--new-name")) payload.addProperty("newName", options.get("--new-name"));
		if (options.containsKey("--force")) payload.addProperty("force", Boolean.parseBoolean(options.get("--force")));
		if (options.containsKey("--entry-json")) {
			var entry = JsonParser.parseString(options.get("--entry-json"));
			if (!entry.isJsonObject()) throw new IllegalArgumentException("--entry-json must be a JSON object");
			payload.add("entry", entry);
		}
		if (options.containsKey("--changes-json")) {
			var changes = JsonParser.parseString(options.get("--changes-json"));
			if (!changes.isJsonArray() || changes.getAsJsonArray().isEmpty())
				throw new IllegalArgumentException("--changes-json must be a non-empty JSON array");
			payload.add("changes", changes);
		}
		if (options.containsKey("--output-name"))
			payload.addProperty("outputName", options.get("--output-name"));
		if (options.containsKey("--source"))
			payload.addProperty("sourceWorkspacePath", options.get("--source"));
		if (options.containsKey("--name"))
			payload.addProperty("name", options.get("--name"));
		if (options.containsKey("--source-directory"))
			payload.addProperty("sourceDirectory", options.get("--source-directory"));
		if (options.containsKey("--pack"))
			payload.addProperty("zipFileName", options.get("--pack"));
		if (options.containsKey("--generator-id"))
			payload.addProperty("generatorId", options.get("--generator-id"));
		if (options.containsKey("--mod-name"))
			payload.addProperty("modName", options.get("--mod-name"));
		if (options.containsKey("--mod-id"))
			payload.addProperty("modId", options.get("--mod-id"));
		if (options.containsKey("--package-name"))
			payload.addProperty("packageName", options.get("--package-name"));
		if (options.containsKey("--workspace-folder"))
			payload.addProperty("workspaceFolderPath", options.get("--workspace-folder"));
		if (options.containsKey("--version"))
			payload.addProperty("version", options.get("--version"));
		if (options.containsKey("--approve"))
			payload.addProperty("userApproved", Boolean.parseBoolean(options.get("--approve")));
		if (operation == Operation.EXPORT_WORKSPACE && !payload.has("output"))
			throw new IllegalArgumentException("export requires --output");
		if (operation == Operation.CREATE_WORKSPACE) {
			for (String field : new String[] { "generatorId", "modName", "modId", "workspaceFolderPath" })
				if (!payload.has(field) || payload.get(field).getAsString().isBlank())
					throw new IllegalArgumentException("create-workspace requires the corresponding option for " + field);
			if (!payload.has("userApproved"))
				payload.addProperty("userApproved", false);
		}
		if (operation == Operation.GET_PROCEDURE_EDITOR && !payload.has("elementId"))
			throw new IllegalArgumentException("procedure requires --element-id");
		if ((operation == Operation.PREVIEW_PROCEDURE_CHANGE || operation == Operation.UPDATE_PROCEDURE)
				&& (!payload.has("elementId") || !payload.has("edits")))
			throw new IllegalArgumentException(arguments[0] + " requires --element-id and --edits-json");
		if (operation == Operation.PREVIEW_REGISTRY_RENAME || operation == Operation.RENAME_REGISTRY_ENTRY) {
			if (!payload.has("entryId") || !payload.has("newName"))
				throw new IllegalArgumentException(arguments[0] + " requires --entry-id and --new-name");
		}
		if (operation == Operation.CREATE_REGISTRY_ENTRY && (!payload.has("registry") || !payload.has("entry")))
			throw new IllegalArgumentException("create-registry-entry requires --registry and --entry-json");
		if (operation == Operation.UPDATE_REGISTRY_ENTRY && (!payload.has("entryId") || !payload.has("changes")))
			throw new IllegalArgumentException("update-registry-entry requires --entry-id and --changes-json");
		if (operation == Operation.DELETE_REGISTRY_ENTRY && !payload.has("entryId"))
			throw new IllegalArgumentException("delete-registry-entry requires --entry-id");
		if (operation == Operation.PREVIEW_DATAGEN_OUTPUT && !payload.has("taskId"))
			throw new IllegalArgumentException("preview-datagen requires --task-id");
		if (operation == Operation.PUBLISH_DATAGEN_OUTPUT
				&& (!payload.has("taskId") || !payload.has("manifestHash")))
			throw new IllegalArgumentException("publish-datagen requires --task-id and --manifest-hash");
		return new ParsedCommand(operation, revision, payload, false, query);
	}

	private static HeadlessExitCode exitCode(CommandOutcome outcome) {
		String status = outcome.result().status();
		if (status.equals("accepted") || status.equals("committed") || status.equals("completed"))
			return HeadlessExitCode.SUCCESS;
		if (outcome.result().denial() != null && !outcome.result().denial().isJsonNull())
			return HeadlessExitCode.PERMISSION_DENIED;
		if (outcome.result().conflict() != null && !outcome.result().conflict().isJsonNull())
			return HeadlessExitCode.REVISION_CONFLICT;
		boolean validationFailure = outcome.result().diagnostics().stream()
				.anyMatch(diagnostic -> isValidationDiagnostic(diagnostic.code()));
		return validationFailure ? HeadlessExitCode.VALIDATION_FAILED : HeadlessExitCode.INTERNAL_ERROR;
	}

	private static boolean isValidationDiagnostic(String code) {
		return code.startsWith("VALIDATION_") || code.startsWith("FIELD_") || code.endsWith("_INVALID")
				|| switch (code) {
					case "COMMAND_PAYLOAD_INVALID", "MOD_ELEMENT_NAME_CONFLICT", "WORKSPACE_FOLDER_REQUIRED",
							"WORKSPACE_FOLDER_OUTSIDE_ROOT", "WORKSPACE_FOLDER_NOT_EMPTY", "UNSUPPORTED_GENERATOR",
							"GENERATOR_NOT_INSTALLED" -> true;
					default -> false;
				};
	}

	private static JsonObject envelope(String operation, String status, HeadlessExitCode exitCode) {
		JsonObject response = new JsonObject();
		response.addProperty("schemaVersion", "1.0");
		response.addProperty("operation", operation);
		response.addProperty("status", status);
		response.addProperty("exitCode", exitCode.code());
		response.add("data", JsonNull.INSTANCE);
		response.add("diagnostics", new JsonArray());
		return response;
	}

	private static void write(PrintWriter output, JsonObject response) {
		output.println(GSON.toJson(response));
		output.flush();
	}

	private record ParsedCommand(Operation operation, long revision, JsonObject payload, boolean help, boolean query) {
	}
}

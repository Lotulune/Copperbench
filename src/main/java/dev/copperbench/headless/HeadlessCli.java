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
				commands.add("export");
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
			case "tracks", "release", "preview-migrate", "preview-import", "plugins", "elements", "upstream-tools" -> true;
			default -> false;
		};
		Operation operation = switch (arguments[0]) {
			case "validate" -> Operation.VALIDATE_WORKSPACE;
			case "build" -> Operation.BUILD_WORKSPACE;
			case "export" -> Operation.EXPORT_WORKSPACE;
			case "tracks" -> Operation.GET_VERSION_TRACKS;
			case "release" -> Operation.GET_RELEASE_NOTES;
			case "plugins" -> Operation.LIST_INSTALLED_PLUGINS;
			case "elements" -> Operation.GET_ELEMENT_COVERAGE;
			case "upstream-tools" -> Operation.GET_UPSTREAM_TOOLS;
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
				"--name", "--pack", "--source-directory");
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
		if (options.containsKey("--target"))
			payload.addProperty("targetGeneratorId", options.get("--target"));
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
		if (options.containsKey("--approve"))
			payload.addProperty("userApproved", Boolean.parseBoolean(options.get("--approve")));
		if (operation == Operation.EXPORT_WORKSPACE && !payload.has("output"))
			throw new IllegalArgumentException("export requires --output");
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
				.anyMatch(diagnostic -> diagnostic.code().startsWith("VALIDATION_")
						|| diagnostic.code().startsWith("FIELD_"));
		return validationFailure ? HeadlessExitCode.VALIDATION_FAILED : HeadlessExitCode.INTERNAL_ERROR;
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

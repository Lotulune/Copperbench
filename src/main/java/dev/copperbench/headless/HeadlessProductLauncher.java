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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.generator.LoaderRoutingWorkspaceTaskGateway;
import net.mcreator.workspace.Workspace;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

/** Product-level {@code copperbench.exe headless --workspace ...} entry point. */
public final class HeadlessProductLauncher {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private HeadlessProductLauncher() {
	}

	private static boolean isAcceptedTask(JsonObject response) {
		return response.has("status") && "accepted".equals(response.get("status").getAsString())
				&& response.has("task") && response.get("task").isJsonObject()
				&& response.getAsJsonObject("task").has("id");
	}

	private static int awaitTask(HeadlessWorkspaceEntryAdapter adapter, UUID workspaceId, Supplier<UUID> ids,
			JsonObject response) throws InterruptedException {
		UUID taskId = UUID.fromString(response.getAsJsonObject("task").get("id").getAsString());
		Instant deadline = Instant.now().plus(Duration.ofMinutes(25));
		while (Instant.now().isBefore(deadline)) {
			JsonObject payload = new JsonObject();
			payload.addProperty("taskId", taskId.toString());
			payload.addProperty("afterLogSequence", 0);
			QueryResult result = adapter.query(Query.of(ids.get(), workspaceId, Operation.GET_TASK, payload));
			if (!"succeeded".equals(result.status())) {
				response.addProperty("status", "failed");
				response.addProperty("code", "HEADLESS_TASK_QUERY_FAILED");
				response.add("diagnostics", GSON.toJsonTree(result.diagnostics()));
				response.addProperty("exitCode", HeadlessExitCode.INTERNAL_ERROR.code());
				return HeadlessExitCode.INTERNAL_ERROR.code();
			}
			JsonObject projection = result.data().getAsJsonObject();
			JsonObject task = projection.getAsJsonObject("task");
			String state = task.get("state").getAsString();
			if (!"running".equals(state) && !"queued".equals(state)) {
				response.add("task", task.deepCopy());
				response.add("logs", projection.getAsJsonArray("logs").deepCopy());
				response.add("diagnostics", projection.getAsJsonArray("diagnostics").deepCopy());
				boolean succeeded = "succeeded".equals(state);
				response.addProperty("status", succeeded ? "succeeded" : state);
				response.addProperty("exitCode",
						succeeded ? HeadlessExitCode.SUCCESS.code() : HeadlessExitCode.INTERNAL_ERROR.code());
				return succeeded ? HeadlessExitCode.SUCCESS.code() : HeadlessExitCode.INTERNAL_ERROR.code();
			}
			Thread.sleep(150);
		}
		response.addProperty("status", "failed");
		response.addProperty("code", "HEADLESS_TASK_TIMEOUT");
		response.addProperty("exitCode", HeadlessExitCode.INTERNAL_ERROR.code());
		return HeadlessExitCode.INTERNAL_ERROR.code();
	}

	public static int run(String[] arguments, PrintWriter output) {
		try {
			Invocation invocation = parse(arguments);
			HeadlessRuntimeBootstrap.ensureInitialized();
			File workspaceFile = invocation.workspace().toFile();
			try (Workspace workspace = Workspace.readFromFS(workspaceFile, null);
					MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
							store -> new LoaderRoutingWorkspaceTaskGateway(store,
									ignored -> workspace.getWorkspaceFolder().toPath(),
									Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
									Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
				Supplier<UUID> ids = UUID::randomUUID;
				HeadlessWorkspaceEntryAdapter adapter = session.headlessEntry(PermissionProfile.WORKSPACE);
				HeadlessCli cli = new HeadlessCli(adapter, session.workspaceId(), ids);
				StringWriter buffered = new StringWriter();
				int exitCode = cli.run(invocation.commandArguments(), new PrintWriter(buffered, true));
				JsonObject response = JsonParser.parseString(buffered.toString().trim()).getAsJsonObject();
				if (exitCode == HeadlessExitCode.SUCCESS.code() && isAcceptedTask(response))
					exitCode = awaitTask(adapter, session.workspaceId(), ids, response);
				output.println(GSON.toJson(response));
				output.flush();
				return exitCode;
			}
		} catch (IllegalArgumentException exception) {
			return fail(output, HeadlessExitCode.INVALID_ARGUMENTS, "HEADLESS_INVALID_ARGUMENTS",
					exception.getMessage());
		} catch (Exception | LinkageError exception) {
			return fail(output, HeadlessExitCode.INTERNAL_ERROR, "HEADLESS_PRODUCT_START_FAILED",
					exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
		}
	}

	static Invocation parse(String[] arguments) {
		if (arguments == null || arguments.length < 3 || !"--workspace".equals(arguments[0]))
			throw new IllegalArgumentException("Usage: headless --workspace <path.mcreator> <command> [options]");
		Path workspace = Path.of(arguments[1]).toAbsolutePath().normalize();
		if (!workspace.getFileName().toString().endsWith(".mcreator"))
			throw new IllegalArgumentException("--workspace must point to a .mcreator file");
		if (!Files.isRegularFile(workspace))
			throw new IllegalArgumentException("Workspace file does not exist: " + workspace);
		String[] commandArguments = Arrays.copyOfRange(arguments, 2, arguments.length);
		if (commandArguments.length == 0 || commandArguments[0].isBlank())
			throw new IllegalArgumentException("A headless command is required");
		return new Invocation(workspace, commandArguments);
	}

	private static int fail(PrintWriter output, HeadlessExitCode exitCode, String code, String message) {
		JsonObject envelope = new JsonObject();
		envelope.addProperty("schemaVersion", "1.0");
		envelope.addProperty("operation", "headless_product_start");
		envelope.addProperty("status", "failed");
		envelope.addProperty("code", code);
		envelope.addProperty("exitCode", exitCode.code());
		JsonArray diagnostics = new JsonArray();
		JsonObject diagnostic = new JsonObject();
		diagnostic.addProperty("code", code);
		diagnostic.addProperty("severity", "error");
		diagnostic.addProperty("message", message);
		diagnostics.add(diagnostic);
		envelope.add("diagnostics", diagnostics);
		output.println(GSON.toJson(envelope));
		output.flush();
		return exitCode.code();
	}

	record Invocation(Path workspace, String[] commandArguments) {
	}
}

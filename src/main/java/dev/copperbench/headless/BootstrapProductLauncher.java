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
import dev.copperbench.ProductIdentity;
import dev.copperbench.core.workspace.WorkspaceCreationService;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Product-level cold-start entry point that does not require an existing
 * {@code .mcreator} file.
 *
 * <p>It intentionally remains separate from {@link HeadlessProductLauncher}:
 * the normal headless/MCP actors must not be able to manufacture protected
 * user-approval facts. Workspace creation from this entry point is allowed
 * only after a local Copperbench confirmation prompt succeeds.</p>
 */
public final class BootstrapProductLauncher {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private BootstrapProductLauncher() {
	}

	@FunctionalInterface
	interface ApprovalPrompt {
		boolean approve(CreateRequest request);
	}

	record CreateRequest(String generatorId, String modName, String modId, String packageName,
			String workspaceFolderPath, String version) {
	}

	public static int run(String[] arguments, PrintWriter output) {
		return run(arguments, output, new WorkspaceCreationService(), BootstrapProductLauncher::confirmLocally);
	}

	static int run(String[] arguments, PrintWriter output, WorkspaceCreationService service,
			ApprovalPrompt approvalPrompt) {
		Objects.requireNonNull(output);
		Objects.requireNonNull(service);
		Objects.requireNonNull(approvalPrompt);
		try {
			Invocation invocation = parse(arguments);
			HeadlessRuntimeBootstrap.ensureInitialized();
			return switch (invocation.command()) {
				case "help" -> help(output);
				case "list-generators" -> listGenerators(output, service);
				case "create-workspace" -> createWorkspace(output, service, approvalPrompt, invocation.options());
				default -> throw new IllegalArgumentException("Unknown bootstrap command: " + invocation.command());
			};
		} catch (IllegalArgumentException exception) {
			return fail(output, HeadlessExitCode.INVALID_ARGUMENTS, "HEADLESS_INVALID_ARGUMENTS",
					exception.getMessage());
		} catch (Exception | LinkageError exception) {
			return fail(output, HeadlessExitCode.INTERNAL_ERROR, "BOOTSTRAP_PRODUCT_START_FAILED",
					exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
		}
	}

	private static int help(PrintWriter output) {
		JsonObject response = envelope("bootstrap_help", "completed", HeadlessExitCode.SUCCESS);
		JsonObject data = new JsonObject();
		JsonArray commands = new JsonArray();
		commands.add("list-generators");
		commands.add("create-workspace");
		data.add("commands", commands);
		data.addProperty("createUsage",
				"bootstrap create-workspace --generator-id <id> --mod-name <name> --mod-id <id> "
						+ "--workspace-folder <path> [--package-name <package>] [--version <version>]");
		data.addProperty("approval", "Workspace creation requires confirmation in the local Copperbench UI.");
		response.add("data", data);
		write(output, response);
		return HeadlessExitCode.SUCCESS.code();
	}

	private static int listGenerators(PrintWriter output, WorkspaceCreationService service) {
		JsonObject response = envelope("list_new_workspace_generators", "succeeded", HeadlessExitCode.SUCCESS);
		response.add("data", service.toProjection());
		write(output, response);
		return HeadlessExitCode.SUCCESS.code();
	}

	private static int createWorkspace(PrintWriter output, WorkspaceCreationService service,
			ApprovalPrompt approvalPrompt, Map<String, String> options) {
		String generatorId = required(options, "--generator-id");
		String modName = required(options, "--mod-name");
		String modId = required(options, "--mod-id");
		String workspaceFolder = required(options, "--workspace-folder");
		String packageName = options.get("--package-name");
		if (packageName == null || packageName.isBlank())
			packageName = "resourcepack-1.21.1".equals(generatorId) ? null
					: "net.mcreator." + modId.replaceAll("[^a-z0-9_]", "");
		String version = options.getOrDefault("--version", "1.0.0");
		CreateRequest request = new CreateRequest(generatorId, modName, modId, packageName, workspaceFolder, version);

		List<String> diagnostics = service.validateCreation(request.generatorId(), request.modName(), request.modId(),
				request.packageName(), request.workspaceFolderPath());
		if (!diagnostics.isEmpty())
			return serviceFailure(output, diagnostics, HeadlessExitCode.VALIDATION_FAILED);

		if (!approvalPrompt.approve(request)) {
			JsonObject response = envelope("create_workspace", "rejected", HeadlessExitCode.PERMISSION_DENIED);
			response.addProperty("code", "USER_APPROVAL_REQUIRED");
			response.add("diagnostics", diagnostics("USER_APPROVAL_REQUIRED",
					"Creating a workspace is a protected operation and requires local user confirmation."));
			JsonObject denial = new JsonObject();
			denial.addProperty("approvalRequired", true);
			denial.addProperty("protectedOperation", true);
			response.add("denial", denial);
			write(output, response);
			return HeadlessExitCode.PERMISSION_DENIED.code();
		}

		WorkspaceCreationService.CreationResult result = service.create(request.generatorId(), request.modName(),
				request.modId(), request.packageName(), request.workspaceFolderPath(), request.version());
		if (!result.complete()) {
			boolean validationFailure = result.diagnostics().stream().allMatch(BootstrapProductLauncher::validationCode);
			return serviceFailure(output, result.diagnostics(),
					validationFailure ? HeadlessExitCode.VALIDATION_FAILED : HeadlessExitCode.INTERNAL_ERROR);
		}

		JsonObject response = envelope("create_workspace", "committed", HeadlessExitCode.SUCCESS);
		JsonObject data = new JsonObject();
		data.addProperty("workspaceFile", result.workspaceFile());
		data.addProperty("generatorId", result.generatorId());
		data.addProperty("modId", request.modId());
		response.add("data", data);
		write(output, response);
		return HeadlessExitCode.SUCCESS.code();
	}

	private static boolean confirmLocally(CreateRequest request) {
		if (GraphicsEnvironment.isHeadless())
			return false;
		String message = "<html><b>Allow Copperbench to create this workspace?</b><br><br>"
				+ "Generator: " + escapeHtml(request.generatorId()) + "<br>"
				+ "Mod: " + escapeHtml(request.modName()) + " (" + escapeHtml(request.modId()) + ")<br>"
				+ "Folder: " + escapeHtml(request.workspaceFolderPath()) + "<br><br>"
				+ "An external tool may be waiting for this confirmation.</html>";
		return JOptionPane.showConfirmDialog(null, message, ProductIdentity.NAME + " workspace creation",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static Invocation parse(String[] arguments) {
		if (arguments == null || arguments.length == 0)
			throw new IllegalArgumentException("Usage: bootstrap <help|list-generators|create-workspace> [options]");
		String command = arguments[0];
		if ("--help".equals(command)) command = "help";
		if (!List.of("help", "list-generators", "create-workspace").contains(command))
			throw new IllegalArgumentException("Unknown bootstrap command: " + command);
		if (("help".equals(command) || "list-generators".equals(command)) && arguments.length != 1)
			throw new IllegalArgumentException(command + " does not accept options");

		Map<String, String> options = new LinkedHashMap<>();
		for (int index = 1; index < arguments.length; index += 2) {
			if (index + 1 >= arguments.length || !arguments[index].startsWith("--"))
				throw new IllegalArgumentException("Options must use --name value pairs");
			String option = arguments[index];
			if (!List.of("--generator-id", "--mod-name", "--mod-id", "--package-name", "--workspace-folder",
					"--version").contains(option))
				throw new IllegalArgumentException("Unknown option: " + option);
			options.put(option, arguments[index + 1]);
		}
		return new Invocation(command, Map.copyOf(options));
	}

	private static String required(Map<String, String> options, String name) {
		String value = options.get(name);
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("create-workspace requires " + name);
		return value;
	}

	private static int serviceFailure(PrintWriter output, List<String> codes, HeadlessExitCode exitCode) {
		JsonObject response = envelope("create_workspace", "rejected", exitCode);
		if (!codes.isEmpty()) response.addProperty("code", codes.getFirst());
		JsonArray diagnostics = new JsonArray();
		for (String code : codes) diagnostics.add(diagnostic(code, message(code)));
		response.add("diagnostics", diagnostics);
		write(output, response);
		return exitCode.code();
	}

	private static boolean validationCode(String code) {
		return switch (code) {
			case "UNSUPPORTED_GENERATOR", "GENERATOR_NOT_INSTALLED", "MOD_NAME_INVALID", "MOD_ID_INVALID",
					"PACKAGE_NAME_INVALID", "WORKSPACE_FOLDER_REQUIRED", "WORKSPACE_FOLDER_OUTSIDE_ROOT",
					"WORKSPACE_FOLDER_NOT_EMPTY" -> true;
			default -> false;
		};
	}

	private static String message(String code) {
		return switch (code) {
			case "UNSUPPORTED_GENERATOR" -> "The selected generator is not supported.";
			case "GENERATOR_NOT_INSTALLED" -> "The selected generator plugin is not installed.";
			case "MOD_NAME_INVALID" -> "The mod name is invalid.";
			case "MOD_ID_INVALID" -> "The mod ID is invalid.";
			case "PACKAGE_NAME_INVALID" -> "The Java package name is invalid.";
			case "WORKSPACE_FOLDER_REQUIRED" -> "A workspace folder is required.";
			case "WORKSPACE_FOLDER_OUTSIDE_ROOT" -> "The workspace folder is outside the allowed root.";
			case "WORKSPACE_FOLDER_NOT_EMPTY" -> "The workspace folder is not empty.";
			case "WORKSPACE_SKELETON_SETUP_FAILED" -> "The generator could not prepare the workspace skeleton.";
			case "WORKSPACE_BASE_GENERATION_FAILED" -> "The generator could not materialize the initial workspace source base.";
			case "WORKSPACE_CLEANUP_FAILED" -> "The failed workspace could not be cleaned completely.";
			default -> "The workspace could not be created.";
		};
	}

	private static int fail(PrintWriter output, HeadlessExitCode exitCode, String code, String message) {
		JsonObject response = envelope("bootstrap_product_start", "failed", exitCode);
		response.addProperty("code", code);
		response.add("diagnostics", diagnostics(code, message));
		write(output, response);
		return exitCode.code();
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

	private static JsonArray diagnostics(String code, String message) {
		JsonArray diagnostics = new JsonArray();
		diagnostics.add(diagnostic(code, message));
		return diagnostics;
	}

	private static JsonObject diagnostic(String code, String message) {
		JsonObject diagnostic = new JsonObject();
		diagnostic.addProperty("code", code);
		diagnostic.addProperty("severity", "error");
		diagnostic.addProperty("message", message);
		return diagnostic;
	}

	private static void write(PrintWriter output, JsonObject response) {
		output.println(GSON.toJson(response));
		output.flush();
	}

	record Invocation(String command, Map<String, String> options) {
	}
}

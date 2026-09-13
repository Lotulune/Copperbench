/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessProductLauncherTest {

	@TempDir Path tempDir;

	@Test void invalidProductArgumentsReturnOneMachineReadableFailureEnvelope() {
		StringWriter buffer = new StringWriter();
		int exitCode = HeadlessProductLauncher.run(new String[] { "validate" }, new PrintWriter(buffer, true));

		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), exitCode);
		String payload = buffer.toString().trim();
		assertEquals(1, payload.lines().count(), payload);
		JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
		assertEquals("headless_product_start", json.get("operation").getAsString());
		assertEquals("failed", json.get("status").getAsString());
		assertEquals("HEADLESS_INVALID_ARGUMENTS", json.get("code").getAsString());
		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), json.get("exitCode").getAsInt());
		assertTrue(json.getAsJsonArray("diagnostics").size() > 0);
	}

	@Test void parserRequiresAnExistingMcreatorFileAndPreservesCommandArguments() throws Exception {
		Path workspace = tempDir.resolve("agent-test.mcreator");
		Files.writeString(workspace, "{}");

		HeadlessProductLauncher.Invocation invocation = HeadlessProductLauncher.parse(
				new String[] { "--workspace", workspace.toString(), "build", "--example", "value" });

		assertEquals(workspace.toAbsolutePath().normalize(), invocation.workspace());
		assertArrayEquals(new String[] { "build", "--example", "value" }, invocation.commandArguments());
	}

	@ParameterizedTest
	@CsvSource({ "run_client,true", "run_client,false", "run_server,true", "run_server,false" })
	void interactiveSessionKeepsPollingAfterFortyFiveMinutesAndReportsNormalClose(String operation, boolean stream)
			throws Exception {
		JsonObject response = accepted(operation);
		AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-09-11T00:00:00Z"));
		AtomicInteger polls = new AtomicInteger();
		StringWriter output = new StringWriter();
		int result = HeadlessProductLauncher.awaitTask(query -> {
			int poll = polls.incrementAndGet();
			assertEquals(Operation.GET_TASK, query.operation());
			assertEquals(response.getAsJsonObject("task").get("id").getAsString(),
					query.payload().get("taskId").getAsString());
			assertEquals(poll - 1, query.payload().get("afterLogSequence").getAsInt());
			time.updateAndGet(value -> value.plus(Duration.ofMinutes(46)));
			return taskResult(query, response, poll == 1 ? "running" : "succeeded", poll);
		}, UUID.randomUUID(), UUID::randomUUID, response, new PrintWriter(output, true), stream, time::get);

		assertEquals(2, polls.get(), "The process must still be observed after the old deadline");
		assertEquals(HeadlessExitCode.SUCCESS.code(), result);
		assertEquals("succeeded", response.get("status").getAsString());
		assertEquals("succeeded", response.getAsJsonObject("task").get("state").getAsString());
		assertFalse(response.has("code"));
		if (stream) {
			List<JsonObject> messages = output.toString().lines().map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
			assertEquals(3, messages.size());
			assertEquals("accepted", messages.getFirst().get("status").getAsString());
			assertEquals("running", messages.get(1).getAsJsonObject("task").get("state").getAsString());
			assertEquals("succeeded", messages.get(2).getAsJsonObject("task").get("state").getAsString());
			assertEquals(2, messages.get(2).getAsJsonArray("logs").get(0).getAsJsonObject().get("sequence").getAsInt());
		} else {
			assertEquals("", output.toString());
			assertEquals(2, response.getAsJsonArray("logs").size());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "build_workspace", "run_gametest", "run_datagen" })
	void finiteTasksRetainTheirDeadlineAndLastObservedState(String operation) throws Exception {
		JsonObject response = accepted(operation);
		AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-09-11T00:00:00Z"));
		AtomicInteger polls = new AtomicInteger();
		int result = HeadlessProductLauncher.awaitTask(query -> {
			polls.incrementAndGet();
			time.updateAndGet(value -> value.plus(Duration.ofMinutes(46)));
			return taskResult(query, response, "running", 1);
		}, UUID.randomUUID(), UUID::randomUUID, response, new PrintWriter(new StringWriter()), false, time::get);

		assertEquals(1, polls.get());
		assertEquals(HeadlessExitCode.INTERNAL_ERROR.code(), result);
		assertEquals("HEADLESS_TASK_TIMEOUT", response.get("code").getAsString());
		assertEquals("failed", response.get("status").getAsString());
		assertEquals("running", response.getAsJsonObject("task").get("state").getAsString());
		assertEquals(1, response.getAsJsonArray("logs").size());
	}

	@ParameterizedTest
	@ValueSource(strings = { "failed", "cancelled" })
	void interactiveProcessFailureOrCancellationIsNotReportedAsSuccess(String state) throws Exception {
		JsonObject response = accepted("run_client");
		int result = HeadlessProductLauncher.awaitTask(query -> taskResult(query, response, state, 1),
				UUID.randomUUID(), UUID::randomUUID, response, new PrintWriter(new StringWriter()), false, Instant::now);
		assertEquals(HeadlessExitCode.INTERNAL_ERROR.code(), result);
		assertEquals(state, response.get("status").getAsString());
		assertEquals(state, response.getAsJsonObject("task").get("state").getAsString());
	}

	private static JsonObject accepted(String operation) {
		JsonObject response = new JsonObject();
		response.addProperty("operation", operation);
		response.addProperty("status", "accepted");
		JsonObject task = new JsonObject();
		task.addProperty("id", UUID.randomUUID().toString());
		task.addProperty("state", "queued");
		response.add("task", task);
		return response;
	}

	private static QueryResult taskResult(Query query, JsonObject response, String state, long sequence) {
		JsonObject projection = new JsonObject();
		JsonObject task = response.getAsJsonObject("task").deepCopy();
		task.addProperty("state", state);
		projection.add("task", task);
		JsonArray logs = new JsonArray();
		JsonObject log = new JsonObject();
		log.addProperty("sequence", sequence);
		log.addProperty("text", state);
		logs.add(log);
		projection.add("logs", logs);
		projection.add("diagnostics", new JsonArray());
		return new QueryResult("query_result", "1.0", query.requestId(), query.workspaceId(), Operation.GET_TASK,
				"succeeded", 0, projection, List.of());
	}
}

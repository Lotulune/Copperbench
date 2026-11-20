/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadlessPythonHostTest {
	@TempDir Path root;

	@Test void parsesTheIdleTimeoutWithinItsBounds() {
		assertEquals(HeadlessPythonHost.DEFAULT_IDLE_TIMEOUT, HeadlessPythonHost.idleTimeout(new String[] { "serve" }));
		assertEquals(Duration.ofSeconds(90), HeadlessPythonHost.idleTimeout(new String[] { "serve", "--idle-timeout", "90" }));
		for (String[] invalid : List.of(new String[] { "serve", "--idle-timeout" }, new String[] { "serve", "--idle-timeout", "x" },
				new String[] { "serve", "--idle-timeout", "1" }, new String[] { "serve", "--idle-timeout", "999999" },
				new String[] { "serve", "--other", "60" }))
			assertThrows(IllegalArgumentException.class, () -> HeadlessPythonHost.idleTimeout(invalid), String.join(" ", invalid));
	}

	@Test void staysWhileAClientIsConnectedOrATaskRunsThenExitsAndRemovesItsCredentials() throws Exception {
		Path file = Files.writeString(root.resolve("background.mcreator"), "{}");
		UUID id = UUID.randomUUID();
		var adapter = new HeadlessWorkspaceEntryAdapter(service(id), PermissionProfile.WORKSPACE);
		AtomicBoolean tasksActive = new AtomicBoolean();
		StringWriter output = new StringWriter();
		CompletableFuture<Integer> host = CompletableFuture.supplyAsync(() -> {
			try {
				return HeadlessPythonHost.serve(file, id, adapter, tasksActive::get, Duration.ofMillis(600), new PrintWriter(output));
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
		Path connectionFile = DesktopPythonRuntime.connectionFile(file);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!output.toString().contains("\"serving\"") && System.nanoTime() < deadline) Thread.sleep(20);
		JsonObject ready = JsonParser.parseString(output.toString().strip()).getAsJsonObject();
		assertEquals(id.toString(), ready.get("workspaceId").getAsString());
		assertFalse(output.toString().contains("token"), "credentials stay in the owner-only connection file");

		JsonObject connection = JsonParser.parseString(Files.readString(connectionFile)).getAsJsonObject();
		try (Socket socket = new Socket("127.0.0.1", connection.get("port").getAsInt())) {
			socket.setSoTimeout(3000);
			var writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
			var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			writer.println("{\"token\":\"" + connection.get("token").getAsString() + "\"}");
			assertEquals("ready", JsonParser.parseString(reader.readLine()).getAsJsonObject().get("status").getAsString());
			Thread.sleep(1200);
			assertFalse(host.isDone(), "a connected client keeps the host");
		}
		tasksActive.set(true);
		Thread.sleep(1200);
		assertFalse(host.isDone(), "a running task keeps the host after its script disconnected");
		tasksActive.set(false);
		assertEquals(HeadlessExitCode.SUCCESS.code(), host.get(10, TimeUnit.SECONDS));
		assertFalse(Files.exists(connectionFile), "an exited host leaves no connection record");
	}

	private static WorkspaceApplicationService service(UUID workspaceId) {
		System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
		var store = new RevisionedWorkspaceStore();
		store.register(new WorkspaceState(workspaceId, "Python background host", "mod", 0, false,
				JsonParser.parseString("{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\",\"state\":\"ready\"}").getAsJsonObject(),
				new JsonObject(), List.of()));
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
				Clock.systemUTC(), UUID::randomUUID);
	}
}

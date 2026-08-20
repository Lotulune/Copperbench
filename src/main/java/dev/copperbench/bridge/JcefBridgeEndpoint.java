/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.SchemaNegotiator;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Event;
import dev.copperbench.core.contract.UiCore.Handshake;
import dev.copperbench.core.contract.UiCore.Query;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** JSON-only endpoint ready to be exposed through JCEF's JavaScript query transport. */
public final class JcefBridgeEndpoint {

	private final WorkspaceEntryAdapter adapter;
	private final SchemaNegotiator schemas = new SchemaNegotiator(List.of(UiCore.SCHEMA_VERSION));
	private final Consumer<String> eventSink;

	public JcefBridgeEndpoint(WorkspaceEntryAdapter adapter, Consumer<String> eventSink) {
		this.adapter = Objects.requireNonNull(adapter);
		this.eventSink = Objects.requireNonNull(eventSink);
	}

	public String handle(String requestJson) {
		JsonObject envelope = JsonParser.parseString(requestJson).getAsJsonObject();
		String messageType = requiredString(envelope, "messageType");
		return switch (messageType) {
			case "handshake" -> UiCore.wireGson().toJson(schemas.negotiate(
					UiCore.wireGson().fromJson(envelope, Handshake.class)));
			case "command" -> handleCommand(envelope);
			case "query" -> handleQuery(envelope);
			default -> throw new IllegalArgumentException("Unsupported bridge messageType: " + messageType);
		};
	}

	private String handleCommand(JsonObject envelope) {
		requireSchema(envelope);
		var outcome = adapter.execute(UiCore.wireGson().fromJson(envelope, Command.class));
		for (Event event : outcome.events()) eventSink.accept(UiCore.wireGson().toJson(event));
		return UiCore.wireGson().toJson(outcome.result());
	}

	private String handleQuery(JsonObject envelope) {
		requireSchema(envelope);
		return UiCore.wireGson().toJson(adapter.query(UiCore.wireGson().fromJson(envelope, Query.class)));
	}

	private static void requireSchema(JsonObject envelope) {
		String version = requiredString(envelope, "schemaVersion");
		if (!UiCore.SCHEMA_VERSION.equals(version))
			throw new IllegalArgumentException("Unsupported UI-Core schema version: " + version);
	}

	private static String requiredString(JsonObject object, String name) {
		if (!object.has(name) || !object.get(name).isJsonPrimitive())
			throw new IllegalArgumentException("Missing bridge property: " + name);
		return object.get(name).getAsString();
	}
}

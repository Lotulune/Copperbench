/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211WorkspaceTaskGateway;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Routes shared task operations to the workspace's single active loader target. */
public final class LoaderRoutingWorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	private final RevisionedWorkspaceStore store;
	private final Fabric1211WorkspaceTaskGateway fabric;
	private final Fabric1211WorkspaceTaskGateway fabric261;
	private final Fabric1211WorkspaceTaskGateway fabric262;
	private final Fabric1211WorkspaceTaskGateway fabric1201;
	private final NeoForge1211WorkspaceTaskGateway neoForge;
	private final NeoForge1211WorkspaceTaskGateway neoForge261;
	private final NeoForge1211WorkspaceTaskGateway neoForge262;
	private final NeoForge1211WorkspaceTaskGateway neoForge1201;
	private final VersionTrackCatalog tracks = VersionTrackCatalog.official();

	public LoaderRoutingWorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids) {
		this.store = store;
		this.fabric = new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids);
		this.fabric261 = new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids,
				Fabric1211Generator.Profile.FABRIC_261);
		this.fabric262 = new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids,
				Fabric1211Generator.Profile.FABRIC_262);
		this.fabric1201 = new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids,
				Fabric1211Generator.Profile.FABRIC_1201);
		this.neoForge = new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids);
		this.neoForge261 = new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids,
				NeoForge1211Generator.Profile.NEOFORGE_261);
		this.neoForge262 = new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids,
				NeoForge1211Generator.Profile.NEOFORGE_262);
		this.neoForge1201 = new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids,
				NeoForge1211Generator.Profile.NEOFORGE_1201);
	}

	@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
		String generatorId = store.read(workspaceId).map(state -> state.generator().get("id"))
				.filter(value -> value != null && value.isJsonPrimitive()).map(value -> value.getAsString())
				.orElseThrow(() -> new IllegalArgumentException("Workspace generator is missing"));
		return switch (generatorId) {
			case "fabric-1.21.1" -> fabric.start(workspaceId, operation, payload);
			case "fabric-26.1.2" -> fabric261.start(workspaceId, operation, payload);
			case "fabric-26.2" -> fabric262.start(workspaceId, operation, payload);
			case "fabric-1.20.1" -> fabric1201.start(workspaceId, operation, payload);
			case "neoforge-1.21.1" -> neoForge.start(workspaceId, operation, payload);
			case "neoforge-26.1.2" -> neoForge261.start(workspaceId, operation, payload);
			case "neoforge-26.2" -> neoForge262.start(workspaceId, operation, payload);
			case "neoforge-1.20.1" -> neoForge1201.start(workspaceId, operation, payload);
			default -> {
				var decision = tracks.decision(generatorId);
				throw new IllegalArgumentException(decision.reasonCode() + ": " + decision.message());
			}
		};
	}

	@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
		return fabric.find(workspaceId, taskId).or(() -> fabric261.find(workspaceId, taskId))
				.or(() -> fabric262.find(workspaceId, taskId)).or(() -> fabric1201.find(workspaceId, taskId))
				.or(() -> neoForge.find(workspaceId, taskId)).or(() -> neoForge261.find(workspaceId, taskId))
				.or(() -> neoForge262.find(workspaceId, taskId)).or(() -> neoForge1201.find(workspaceId, taskId));
	}

	@Override public List<JsonObject> active(UUID workspaceId) {
		List<JsonObject> tasks = new ArrayList<>(fabric.active(workspaceId));
		tasks.addAll(fabric261.active(workspaceId));
		tasks.addAll(fabric262.active(workspaceId));
		tasks.addAll(fabric1201.active(workspaceId));
		tasks.addAll(neoForge.active(workspaceId));
		tasks.addAll(neoForge261.active(workspaceId));
		tasks.addAll(neoForge262.active(workspaceId));
		tasks.addAll(neoForge1201.active(workspaceId));
		return List.copyOf(tasks);
	}

	@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
		if (fabric.find(workspaceId, taskId).isPresent()) return fabric.cancel(workspaceId, taskId);
		if (fabric261.find(workspaceId, taskId).isPresent()) return fabric261.cancel(workspaceId, taskId);
		if (fabric262.find(workspaceId, taskId).isPresent()) return fabric262.cancel(workspaceId, taskId);
		if (fabric1201.find(workspaceId, taskId).isPresent()) return fabric1201.cancel(workspaceId, taskId);
		if (neoForge.find(workspaceId, taskId).isPresent()) return neoForge.cancel(workspaceId, taskId);
		if (neoForge261.find(workspaceId, taskId).isPresent()) return neoForge261.cancel(workspaceId, taskId);
		if (neoForge262.find(workspaceId, taskId).isPresent()) return neoForge262.cancel(workspaceId, taskId);
		return neoForge1201.cancel(workspaceId, taskId);
	}

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		if (fabric.find(workspaceId, taskId).isPresent()) return fabric.logs(workspaceId, taskId);
		if (fabric261.find(workspaceId, taskId).isPresent()) return fabric261.logs(workspaceId, taskId);
		if (fabric262.find(workspaceId, taskId).isPresent()) return fabric262.logs(workspaceId, taskId);
		if (fabric1201.find(workspaceId, taskId).isPresent()) return fabric1201.logs(workspaceId, taskId);
		if (neoForge.find(workspaceId, taskId).isPresent()) return neoForge.logs(workspaceId, taskId);
		if (neoForge261.find(workspaceId, taskId).isPresent()) return neoForge261.logs(workspaceId, taskId);
		if (neoForge262.find(workspaceId, taskId).isPresent()) return neoForge262.logs(workspaceId, taskId);
		return neoForge1201.logs(workspaceId, taskId);
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		if (fabric.find(workspaceId, taskId).isPresent()) return fabric.diagnostics(workspaceId, taskId);
		if (fabric261.find(workspaceId, taskId).isPresent()) return fabric261.diagnostics(workspaceId, taskId);
		if (fabric262.find(workspaceId, taskId).isPresent()) return fabric262.diagnostics(workspaceId, taskId);
		if (fabric1201.find(workspaceId, taskId).isPresent()) return fabric1201.diagnostics(workspaceId, taskId);
		if (neoForge.find(workspaceId, taskId).isPresent()) return neoForge.diagnostics(workspaceId, taskId);
		if (neoForge261.find(workspaceId, taskId).isPresent()) return neoForge261.diagnostics(workspaceId, taskId);
		if (neoForge262.find(workspaceId, taskId).isPresent()) return neoForge262.diagnostics(workspaceId, taskId);
		return neoForge1201.diagnostics(workspaceId, taskId);
	}

	@Override public void close() {
		fabric.close();
		fabric261.close();
		fabric262.close();
		fabric1201.close();
		neoForge.close();
		neoForge261.close();
		neoForge262.close();
		neoForge1201.close();
	}
}

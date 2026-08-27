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
import dev.copperbench.generator.resourcepack.ResourcePackWorkspaceTaskGateway;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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
	private final ResourcePackWorkspaceTaskGateway resourcePack;
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
		this.resourcePack = new ResourcePackWorkspaceTaskGateway(store, workspaceRoots, distributionRoot, clock, ids);
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
			case ResourcePackWorkspaceTaskGateway.GENERATOR_ID -> resourcePack.start(workspaceId, operation, payload);
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
				.or(() -> neoForge262.find(workspaceId, taskId)).or(() -> neoForge1201.find(workspaceId, taskId))
				.or(() -> resourcePack.find(workspaceId, taskId));
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
		tasks.addAll(resourcePack.active(workspaceId));
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
		if (neoForge1201.find(workspaceId, taskId).isPresent()) return neoForge1201.cancel(workspaceId, taskId);
		return resourcePack.cancel(workspaceId, taskId);
	}

	@Override public AutoCloseable subscribeTaskEvents(Consumer<TaskEvent> listener) {
		List<AutoCloseable> subscriptions = List.of(
				fabric.subscribeTaskEvents(listener), fabric261.subscribeTaskEvents(listener),
				fabric262.subscribeTaskEvents(listener), fabric1201.subscribeTaskEvents(listener),
				neoForge.subscribeTaskEvents(listener), neoForge261.subscribeTaskEvents(listener),
				neoForge262.subscribeTaskEvents(listener), neoForge1201.subscribeTaskEvents(listener),
				resourcePack.subscribeTaskEvents(listener));
		return () -> {
			Exception failure = null;
			for (AutoCloseable subscription : subscriptions) {
				try {
					subscription.close();
				} catch (Exception exception) {
					if (failure == null) failure = exception; else failure.addSuppressed(exception);
				}
			}
			if (failure != null) throw failure;
		};
	}

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		if (fabric.find(workspaceId, taskId).isPresent()) return fabric.logs(workspaceId, taskId);
		if (fabric261.find(workspaceId, taskId).isPresent()) return fabric261.logs(workspaceId, taskId);
		if (fabric262.find(workspaceId, taskId).isPresent()) return fabric262.logs(workspaceId, taskId);
		if (fabric1201.find(workspaceId, taskId).isPresent()) return fabric1201.logs(workspaceId, taskId);
		if (neoForge.find(workspaceId, taskId).isPresent()) return neoForge.logs(workspaceId, taskId);
		if (neoForge261.find(workspaceId, taskId).isPresent()) return neoForge261.logs(workspaceId, taskId);
		if (neoForge262.find(workspaceId, taskId).isPresent()) return neoForge262.logs(workspaceId, taskId);
		if (neoForge1201.find(workspaceId, taskId).isPresent()) return neoForge1201.logs(workspaceId, taskId);
		return resourcePack.logs(workspaceId, taskId);
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		WorkspaceTaskGateway owner = owner(workspaceId, taskId);
		return owner == null ? List.of() : owner.diagnostics(workspaceId, taskId);
	}

	@Override public Optional<JsonObject> previewDatagen(UUID workspaceId, UUID taskId) {
		WorkspaceTaskGateway owner = owner(workspaceId, taskId);
		return owner == null ? Optional.empty() : owner.previewDatagen(workspaceId, taskId);
	}

	@Override public JsonObject publishDatagen(UUID workspaceId, UUID taskId, JsonObject payload) {
		WorkspaceTaskGateway owner = owner(workspaceId, taskId);
		if (owner == null) throw new IllegalArgumentException("Task not found: " + taskId);
		return owner.publishDatagen(workspaceId, taskId, payload);
	}

	@Override public void completeDatagenPublish(UUID workspaceId, UUID taskId) {
		WorkspaceTaskGateway owner = owner(workspaceId, taskId);
		if (owner != null) owner.completeDatagenPublish(workspaceId, taskId);
	}

	@Override public void rollbackDatagenPublish(UUID workspaceId, UUID taskId) {
		WorkspaceTaskGateway owner = owner(workspaceId, taskId);
		if (owner != null) owner.rollbackDatagenPublish(workspaceId, taskId);
	}

	private WorkspaceTaskGateway owner(UUID workspaceId, UUID taskId) {
		for (WorkspaceTaskGateway candidate : List.of(fabric, fabric261, fabric262, fabric1201, neoForge,
				neoForge261, neoForge262, neoForge1201, resourcePack))
			if (candidate.find(workspaceId, taskId).isPresent()) return candidate;
		return null;
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
		resourcePack.close();
	}
}

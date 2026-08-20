/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import dev.copperbench.core.contract.UiCore.PermissionProfile;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record McpServerConfiguration(int port, UUID workspaceId, PermissionProfile permissionProfile,
		Set<String> allowedOrigins, Clock clock) {

	public McpServerConfiguration {
		if (port < 0 || port > 65535)
			throw new IllegalArgumentException("Port is out of range");
		Objects.requireNonNull(workspaceId);
		Objects.requireNonNull(permissionProfile);
		Objects.requireNonNull(clock);
		allowedOrigins = Set.copyOf(allowedOrigins);
	}
}

/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.automation.security;

import dev.copperbench.core.contract.UiCore.PermissionProfile;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceToken(String value, UUID workspaceId, PermissionProfile profile, Instant expiresAt) {
}

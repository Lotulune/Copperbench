/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.application;

import dev.copperbench.core.workspace.WorkspaceState;

import java.util.UUID;

/**
 * Reloads workspace state from persisted files after an out-of-band change such
 * as a local-history restore. The integration (MCreator workspace session in
 * stage 3) maps restored files back into a {@link WorkspaceState}; the
 * application service then swaps it into the store under the revision lock.
 */
@FunctionalInterface
public interface WorkspaceStateReloader {

	WorkspaceState reload(UUID workspaceId) throws Exception;
}

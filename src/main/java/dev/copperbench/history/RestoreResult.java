/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import java.util.Set;

public record RestoreResult(String recoveryPointId, Set<String> changedPaths) {

	public RestoreResult {
		changedPaths = Set.copyOf(changedPaths);
	}
}

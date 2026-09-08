/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import java.util.List;

public record WorkspaceChange(ChangeType type, String path, List<HistoryFieldChange> fieldChanges) {

	public WorkspaceChange(ChangeType type, String path) {
		this(type, path, List.of());
	}

	public WorkspaceChange {
		fieldChanges = fieldChanges == null ? List.of() : List.copyOf(fieldChanges);
	}
}

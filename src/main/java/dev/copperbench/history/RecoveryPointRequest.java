/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import dev.copperbench.core.contract.UiCore.Actor;

import java.util.Objects;

public record RecoveryPointRequest(String label, Actor actor, String taskId) {

	public RecoveryPointRequest {
		if (label == null || label.isBlank())
			throw new IllegalArgumentException("Recovery point label is required");
		Objects.requireNonNull(actor, "actor");
		taskId = taskId == null ? "" : taskId;
	}
}

/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import java.util.Locale;

public enum RecoveryPointSource {
	MANUAL,
	AUTOMATION,
	WORKSPACE_PLAN,
	PROCEDURE,
	ASSET,
	DATAGEN,
	REGISTRY,
	BLOCKBENCH,
	RESTORE_SAFETY;

	public String wireName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static RecoveryPointSource fromWire(String value) {
		if (value == null || value.isBlank()) return MANUAL;
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return MANUAL;
		}
	}
}

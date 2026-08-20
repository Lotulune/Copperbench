/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

public enum HeadlessExitCode {
	SUCCESS(0),
	INVALID_ARGUMENTS(2),
	PERMISSION_DENIED(3),
	VALIDATION_FAILED(4),
	REVISION_CONFLICT(5),
	INTERNAL_ERROR(10);

	private final int code;

	HeadlessExitCode(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}
}

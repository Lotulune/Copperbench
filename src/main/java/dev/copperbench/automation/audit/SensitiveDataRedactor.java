/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.automation.audit;

import java.util.regex.Pattern;

public final class SensitiveDataRedactor {

	private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
	private static final Pattern KEY_VALUE = Pattern.compile(
			"(?i)((?:api[_-]?key|token|secret|password)\\s*[=:]\\s*)[^\\s,;]+");

	private SensitiveDataRedactor() {
	}

	public static String redact(String value) {
		if (value == null)
			return "";
		return KEY_VALUE.matcher(BEARER.matcher(value).replaceAll("$1[REDACTED]"))
				.replaceAll("$1[REDACTED]");
	}
}

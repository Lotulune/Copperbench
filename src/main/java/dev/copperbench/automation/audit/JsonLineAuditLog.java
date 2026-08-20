/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.automation.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JsonLineAuditLog {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final Path path;

	public JsonLineAuditLog(Path path) {
		this.path = path;
	}

	public synchronized void append(AuditRecord record) throws IOException {
		Path parent = path.getParent();
		if (parent != null)
			Files.createDirectories(parent);
		AuditRecord redacted = new AuditRecord(record.occurredAt(), SensitiveDataRedactor.redact(record.clientId()),
				SensitiveDataRedactor.redact(record.tool()), SensitiveDataRedactor.redact(record.parameterSummary()),
				SensitiveDataRedactor.redact(record.result()), record.revision(), record.recoveryPointId());
		Files.writeString(path, GSON.toJson(redacted) + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}
}

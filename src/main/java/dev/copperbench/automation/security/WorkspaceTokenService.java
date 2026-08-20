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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkspaceTokenService {

	private final Clock clock;
	private final Duration timeToLive;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, Session> sessions = new ConcurrentHashMap<>();

	public WorkspaceTokenService(Clock clock, Duration timeToLive) {
		if (timeToLive.isZero() || timeToLive.isNegative())
			throw new IllegalArgumentException("Token lifetime must be positive");
		this.clock = clock;
		this.timeToLive = timeToLive;
	}

	public WorkspaceToken issue(UUID workspaceId, PermissionProfile profile) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = clock.instant().plus(timeToLive);
		sessions.put(digest(value), new Session(workspaceId, profile, expiresAt));
		return new WorkspaceToken(value, workspaceId, profile, expiresAt);
	}

	public TokenValidation validate(String value, UUID workspaceId) {
		if (value == null || value.isBlank())
			return TokenValidation.denied("TOKEN_MISSING");
		String digest = digest(value);
		Session session = sessions.get(digest);
		if (session == null)
			return TokenValidation.denied("TOKEN_INVALID");
		if (!clock.instant().isBefore(session.expiresAt())) {
			sessions.remove(digest);
			return TokenValidation.denied("TOKEN_EXPIRED");
		}
		if (!session.workspaceId().equals(workspaceId))
			return TokenValidation.denied("TOKEN_WORKSPACE_MISMATCH");
		return new TokenValidation(true, session.profile(), "AUTHENTICATED");
	}

	public void revoke(String value) {
		if (value != null && !value.isBlank())
			sessions.remove(digest(value));
	}

	public void revokeWorkspace(UUID workspaceId) {
		sessions.entrySet().removeIf(entry -> entry.getValue().workspaceId().equals(workspaceId));
	}

	private static String digest(String value) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private record Session(UUID workspaceId, PermissionProfile profile, Instant expiresAt) {
	}
}

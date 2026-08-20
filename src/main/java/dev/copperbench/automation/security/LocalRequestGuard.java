/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.automation.security;

import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class LocalRequestGuard {

	private static final Pattern LOOPBACK_HOST = Pattern.compile(
			"^(?:localhost|127\\.0\\.0\\.1|\\[::1])(?::(?:[1-9][0-9]{0,4}))?$", Pattern.CASE_INSENSITIVE);

	private final WorkspaceTokenService tokens;
	private final Set<String> allowedOrigins;

	public LocalRequestGuard(WorkspaceTokenService tokens, Set<String> allowedOrigins) {
		this.tokens = tokens;
		this.allowedOrigins = Set.copyOf(allowedOrigins);
	}

	public RequestAuthorization authorize(InetAddress remoteAddress, String host, String origin,
			String authorizationHeader, UUID workspaceId) {
		if (remoteAddress == null || !remoteAddress.isLoopbackAddress())
			return RequestAuthorization.denied("REMOTE_ADDRESS_DENIED");
		if (host == null || !LOOPBACK_HOST.matcher(host).matches())
			return RequestAuthorization.denied("HOST_DENIED");
		if (origin != null && !origin.isBlank() && !allowedOrigins.contains(origin))
			return RequestAuthorization.denied("ORIGIN_DENIED");
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer "))
			return RequestAuthorization.denied("TOKEN_MISSING");
		TokenValidation validation = tokens.validate(authorizationHeader.substring("Bearer ".length()), workspaceId);
		return validation.authenticated()
				? new RequestAuthorization(true, validation.profile(), "AUTHENTICATED")
				: RequestAuthorization.denied(validation.code());
	}
}

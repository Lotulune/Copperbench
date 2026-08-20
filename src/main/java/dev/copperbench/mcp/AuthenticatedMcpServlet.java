/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import dev.copperbench.automation.security.LocalRequestGuard;
import dev.copperbench.automation.security.RequestAuthorization;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

final class AuthenticatedMcpServlet extends HttpServlet {

	private final Servlet delegate;
	private final LocalRequestGuard guard;
	private final PermissionProfile permissionProfile;

	AuthenticatedMcpServlet(Servlet delegate, LocalRequestGuard guard, PermissionProfile permissionProfile) {
		this.delegate = delegate;
		this.guard = guard;
		this.permissionProfile = permissionProfile;
	}

	@Override protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		UUID workspaceId;
		try {
			workspaceId = UUID.fromString(request.getHeader("X-Copperbench-Workspace"));
		} catch (RuntimeException exception) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Workspace token is required");
			return;
		}
		RequestAuthorization authorization = guard.authorize(InetAddress.getByName(request.getRemoteAddr()),
				request.getHeader("Host"), request.getHeader("Origin"), request.getHeader("Authorization"), workspaceId);
		if (!authorization.authenticated()) {
			int status = authorization.code().equals("TOKEN_MISSING") || authorization.code().startsWith("TOKEN_")
					? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN;
			response.sendError(status, authorization.code());
			return;
		}
		if (authorization.profile() != permissionProfile) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "TOKEN_PROFILE_MISMATCH");
			return;
		}
		delegate.service(request, response);
	}

	@Override public void destroy() {
		delegate.destroy();
		super.destroy();
	}
}

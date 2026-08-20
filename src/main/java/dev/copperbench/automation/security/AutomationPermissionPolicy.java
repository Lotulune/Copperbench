/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.automation.security;

import dev.copperbench.core.contract.UiCore.Actor;

public final class AutomationPermissionPolicy {

	public AuthorizationDecision authorize(AuthorizationRequest request) {
		if (request.profile().ordinal() < request.capability().requiredProfile().ordinal())
			return new AuthorizationDecision(false, false, "PERMISSION_PROFILE_DENIED");
		if (request.capability().userOnly() && (request.actor() == Actor.MCP || request.actor() == Actor.HEADLESS))
			return new AuthorizationDecision(false, true, "USER_ONLY_OPERATION");
		if (request.capability().protectedOperation() && !request.userApproved())
			return new AuthorizationDecision(false, true, "USER_APPROVAL_REQUIRED");
		return new AuthorizationDecision(true, false, "ALLOWED");
	}
}

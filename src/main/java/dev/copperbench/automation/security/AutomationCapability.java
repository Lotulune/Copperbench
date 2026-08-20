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

public enum AutomationCapability {
	READ_WORKSPACE(PermissionProfile.READ_ONLY, false, false),
	VALIDATE_WORKSPACE(PermissionProfile.READ_ONLY, false, false),
	MODIFY_WORKSPACE(PermissionProfile.WORKSPACE, false, false),
	BUILD_WORKSPACE(PermissionProfile.WORKSPACE, false, false),
	EXPORT_WORKSPACE(PermissionProfile.WORKSPACE, false, false),
	RUN_CLIENT(PermissionProfile.WORKSPACE, false, false),
	DELETE_WORKSPACE(PermissionProfile.WORKSPACE, true, false),
	ENABLE_JAVA_PLUGIN(PermissionProfile.WORKSPACE, true, true),
	READ_WRITE_OUTSIDE_WORKSPACE(PermissionProfile.FULL_ACCESS, false, false),
	NETWORK_ACCESS(PermissionProfile.FULL_ACCESS, false, false),
	EXPORT_CREDENTIALS(PermissionProfile.FULL_ACCESS, true, false),
	PUBLISH_EXTERNAL(PermissionProfile.FULL_ACCESS, true, false),
	RELAX_MCP_BINDING(PermissionProfile.FULL_ACCESS, true, false);

	private final PermissionProfile requiredProfile;
	private final boolean protectedOperation;
	private final boolean userOnly;

	AutomationCapability(PermissionProfile requiredProfile, boolean protectedOperation, boolean userOnly) {
		this.requiredProfile = requiredProfile;
		this.protectedOperation = protectedOperation;
		this.userOnly = userOnly;
	}

	PermissionProfile requiredProfile() {
		return requiredProfile;
	}

	boolean protectedOperation() {
		return protectedOperation;
	}

	boolean userOnly() {
		return userOnly;
	}
}

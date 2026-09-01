/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.automation;

import dev.copperbench.automation.audit.AuditRecord;
import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.automation.audit.SensitiveDataRedactor;
import dev.copperbench.automation.security.AutomationCapability;
import dev.copperbench.automation.security.AuthorizationDecision;
import dev.copperbench.automation.security.AuthorizationRequest;
import dev.copperbench.automation.security.AutomationPermissionPolicy;
import dev.copperbench.automation.security.LocalRequestGuard;
import dev.copperbench.automation.security.RequestAuthorization;
import dev.copperbench.automation.security.WorkspaceToken;
import dev.copperbench.automation.security.WorkspaceTokenService;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationSecurityTest {

	@TempDir Path temporaryDirectory;

	@Test void permissionProfilesCannotBypassProtectedOperationsOrPluginTrust() {
		AutomationPermissionPolicy policy = new AutomationPermissionPolicy();

		assertTrue(policy.authorize(new AuthorizationRequest(PermissionProfile.READ_ONLY, Actor.MCP,
				AutomationCapability.READ_WORKSPACE, false)).allowed());
		assertFalse(policy.authorize(new AuthorizationRequest(PermissionProfile.READ_ONLY, Actor.MCP,
				AutomationCapability.MODIFY_WORKSPACE, true)).allowed());

		AuthorizationDecision protectedDecision = policy.authorize(new AuthorizationRequest(
				PermissionProfile.FULL_ACCESS, Actor.MCP, AutomationCapability.DELETE_WORKSPACE, false));
		assertFalse(protectedDecision.allowed());
		assertTrue(protectedDecision.approvalRequired());

		assertTrue(policy.authorize(new AuthorizationRequest(PermissionProfile.FULL_ACCESS, Actor.MCP,
				AutomationCapability.DELETE_WORKSPACE, true)).allowed());
		assertFalse(policy.authorize(new AuthorizationRequest(PermissionProfile.FULL_ACCESS, Actor.MCP,
				AutomationCapability.ENABLE_JAVA_PLUGIN, true)).allowed());
	}

	@Test void workspaceTokenExpiresRevokesAndCannotCrossWorkspace() {
		MutableClock clock = new MutableClock(Instant.parse("2026-08-17T00:00:00Z"));
		WorkspaceTokenService tokens = new WorkspaceTokenService(clock, Duration.ofMinutes(5));
		UUID firstWorkspace = UUID.fromString("00000000-0000-4000-8000-000000000001");
		UUID secondWorkspace = UUID.fromString("00000000-0000-4000-8000-000000000002");

		WorkspaceToken token = tokens.issue(firstWorkspace, PermissionProfile.WORKSPACE);
		assertTrue(tokens.validate(token.value(), firstWorkspace).authenticated());
		assertFalse(tokens.validate(token.value(), secondWorkspace).authenticated());

		clock.advance(Duration.ofMinutes(6));
		assertFalse(tokens.validate(token.value(), firstWorkspace).authenticated());

		WorkspaceToken replacement = tokens.issue(firstWorkspace, PermissionProfile.READ_ONLY);
		tokens.revoke(replacement.value());
		assertFalse(tokens.validate(replacement.value(), firstWorkspace).authenticated());
	}

	@Test void localhostGuardRejectsMissingTokensForeignOriginsAndCrossWorkspaceTokens() throws Exception {
		MutableClock clock = new MutableClock(Instant.parse("2026-08-17T00:00:00Z"));
		WorkspaceTokenService tokens = new WorkspaceTokenService(clock, Duration.ofMinutes(5));
		UUID workspaceId = UUID.fromString("00000000-0000-4000-8000-000000000003");
		WorkspaceToken token = tokens.issue(workspaceId, PermissionProfile.WORKSPACE);
		LocalRequestGuard guard = new LocalRequestGuard(tokens, Set.of("http://localhost:5173"));

		RequestAuthorization accepted = guard.authorize(InetAddress.getLoopbackAddress(), "localhost:62145",
				"http://localhost:5173", "Bearer " + token.value(), workspaceId);
		assertTrue(accepted.authenticated());

		assertFalse(guard.authorize(InetAddress.getLoopbackAddress(), "localhost:62145",
				"http://malicious.invalid", "Bearer " + token.value(), workspaceId).authenticated());
		assertFalse(guard.authorize(InetAddress.getLoopbackAddress(), "localhost:62145", null, null,
				workspaceId).authenticated());
		assertFalse(guard.authorize(InetAddress.getLoopbackAddress(), "localhost:62145", null,
				"Bearer " + token.value(), UUID.randomUUID()).authenticated());
	}

	@Test void auditLogRedactsCredentialsBeforeWritingJsonLines() throws Exception {
		Path auditPath = temporaryDirectory.resolve("automation-audit.jsonl");
		JsonLineAuditLog audit = new JsonLineAuditLog(auditPath);
		audit.append(new AuditRecord(Instant.parse("2026-08-17T00:00:00Z"), "codex", "build_workspace",
				"Authorization: Bearer super-secret apiKey=abc123", "committed", 12, "recovery-7"));

		String persisted = Files.readString(auditPath);
		assertFalse(persisted.contains("super-secret"));
		assertFalse(persisted.contains("abc123"));
		assertTrue(persisted.contains("[REDACTED]"));
	}

	@Test void redactorConsumesCompleteQuotedAndUnquotedMultiWordCredentials() {
		String redacted = SensitiveDataRedactor.redact("password = \"hunter two words\"\n"
				+ "client_secret='another long secret'\n"
				+ "token=plain text token\nkeep=value");

		assertFalse(redacted.contains("hunter"));
		assertFalse(redacted.contains("two words"));
		assertFalse(redacted.contains("another long secret"));
		assertFalse(redacted.contains("plain text token"));
		assertTrue(redacted.contains("keep=value"));
	}

	private static final class MutableClock extends Clock {
		private Instant current;

		private MutableClock(Instant current) {
			this.current = current;
		}

		private void advance(Duration duration) {
			current = current.plus(duration);
		}

		@Override public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override public Instant instant() {
			return current;
		}
	}
}

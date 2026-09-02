/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.automation.security.LocalRequestGuard;
import dev.copperbench.automation.security.WorkspaceTokenService;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.time.Duration;

public final class CopperbenchMcpServer implements AutoCloseable {

	private final Tomcat tomcat;
	private final McpSyncServer mcpServer;
	private final InetSocketAddress address;

	private CopperbenchMcpServer(Tomcat tomcat, McpSyncServer mcpServer, InetSocketAddress address) {
		this.tomcat = tomcat;
		this.mcpServer = mcpServer;
		this.address = address;
	}

	public static CopperbenchMcpServer start(McpServerConfiguration configuration, WorkspaceTokenService tokens,
			McpWorkspaceEntryAdapter adapter, JsonLineAuditLog audit) throws Exception {
		return start(configuration, tokens, adapter, audit, null);
	}

	/** Starts the MCP transport with optional read-only asset tools for a scoped workspace root. */
	public static CopperbenchMcpServer start(McpServerConfiguration configuration, WorkspaceTokenService tokens,
			McpWorkspaceEntryAdapter adapter, JsonLineAuditLog audit, AssetWorkspaceService assets) throws Exception {
		var securityBuilder = DefaultServerTransportSecurityValidator.builder()
				.allowedHost("localhost:*").allowedHost("127.0.0.1:*").allowedHost("[::1]:*");
		for (String origin : configuration.allowedOrigins())
			securityBuilder.allowedOrigin(origin);
		HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
				.mcpEndpoint("/mcp").keepAliveInterval(Duration.ofSeconds(30))
				.securityValidator(securityBuilder.build()).build();
		McpToolCatalog catalog = new McpToolCatalog(configuration.workspaceId(), adapter, audit,
				configuration.clock(), assets);
		McpSyncServer mcpServer = McpServer.sync(transport).serverInfo("copperbench", "0.1.0")
				.capabilities(ServerCapabilities.builder().tools(true).build()).tools(catalog.tools())
				.requestTimeout(Duration.ofSeconds(30)).build();

		Tomcat tomcat = new Tomcat();
		tomcat.setHostname("127.0.0.1");
		tomcat.setPort(configuration.port());
		var connector = tomcat.getConnector();
		connector.setProperty("address", "127.0.0.1");
		connector.setAsyncTimeout(30_000);
		String baseDirectory = Files.createTempDirectory("copperbench-mcp-").toString();
		tomcat.setBaseDir(baseDirectory);
		Context context = tomcat.addContext("", baseDirectory);
		var wrapper = context.createWrapper();
		wrapper.setName("copperbenchMcpServlet");
		wrapper.setServlet(new AuthenticatedMcpServlet(transport,
				new LocalRequestGuard(tokens, configuration.allowedOrigins()), configuration.permissionProfile()));
		wrapper.setLoadOnStartup(1);
		wrapper.setAsyncSupported(true);
		context.addChild(wrapper);
		context.addServletMappingDecoded("/*", wrapper.getName());
		tomcat.start();
		int localPort = tomcat.getConnector().getLocalPort();
		return new CopperbenchMcpServer(tomcat, mcpServer,
				new InetSocketAddress(tomcat.getHost().getName(), localPort));
	}

	public InetSocketAddress address() {
		return address;
	}

	@Override public void close() {
		mcpServer.closeGracefully();
		Schedulers.shutdownNow();
		try {
			tomcat.stop();
			tomcat.destroy();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not stop MCP server", exception);
		}
	}
}

package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.PermissionProfile;

import java.io.IOException;
import java.util.UUID;

/** A workspace opened by one host: the shared application service and the entry point each client uses. */
public interface WorkspaceSession extends AutoCloseable {

	UUID workspaceId();

	WorkspaceApplicationService service();

	HeadlessWorkspaceEntryAdapter headlessEntry(PermissionProfile permission);

	McpWorkspaceEntryAdapter mcpEntry(PermissionProfile permission);

	WorkspaceEntryAdapter uiEntry();

	@Override void close() throws IOException;
}

import { useCallback, useEffect, useState } from 'react';
import { mcpRuntimeBridge, type McpRuntimeState } from '../bridge/mcpRuntimeBridge';

const unavailable = (message: string): McpRuntimeState => ({
  status: 'not_started',
  url: null,
  workspaceId: '',
  permissionProfile: 'workspace',
  expiresAt: null,
  tokenAvailable: false,
  failure: message
});

export const useMcpRuntimeState = () => {
  const [mcp, setMcp] = useState<McpRuntimeState | null>(null);

  const refresh = useCallback(async () => {
    try {
      setMcp(await mcpRuntimeBridge.getState());
    } catch (error) {
      setMcp(unavailable(error instanceof Error ? error.message : '桌面 MCP 状态不可用'));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { mcp, refresh };
};

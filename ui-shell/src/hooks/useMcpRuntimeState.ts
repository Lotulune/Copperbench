import { tr } from '../i18n/locale';
import { useCallback, useEffect, useRef, useState } from 'react';
import { mcpRuntimeBridge, subscribeMcpRuntime, type McpRuntimeState } from '../bridge/mcpRuntimeBridge';

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
  const requestSequence = useRef(0);

  const refresh = useCallback(async () => {
    const sequence = ++requestSequence.current;
    try {
      const result = await mcpRuntimeBridge.getState();
      if (sequence === requestSequence.current) setMcp(result);
    } catch (error) {
      if (sequence === requestSequence.current) {
        setMcp(unavailable(error instanceof Error ? error.message : tr("桌面 MCP 状态不可用")));
      }
    }
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeMcpRuntime(() => { void refresh(); });
    void refresh();
    return () => {
      unsubscribe();
      ++requestSequence.current;
    };
  }, [refresh]);

  return { mcp, refresh };
};

import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { useAgentSession } from '../hooks/useAgentSession';
import { useWorkbench } from './WorkbenchContext';
import { useWorkbenchMode } from './WorkbenchModeContext';
import type { AgentContextAttachment } from '../bridge/agentBridge';

type AgentSession = ReturnType<typeof useAgentSession>;
type Entry = { workspaceId: string; id: number; attachment?: AgentContextAttachment; prompt?: string };
const Context = createContext<(AgentSession & { panelOpen: boolean; openPanel: (attachment?: AgentContextAttachment, prompt?: string) => void; closePanel: () => void; entry: Entry | null }) | null>(null);

/** One polling owner for both modes; hiding the panel never tears down a workspace conversation. */
export function AgentProvider({ children }: { children: ReactNode }) {
  const { state, activeView } = useWorkbench();
  const { mode, setMode } = useWorkbenchMode();
  const [panelOpen, setPanelOpen] = useState(false);
  const [entry, setEntry] = useState<Entry | null>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const workspaceId = state.workbench?.workspace.id;
  const session = useAgentSession(panelOpen || mode === 'simple' && activeView === 'hub', workspaceId);
  const openPanel = useCallback((attachment?: AgentContextAttachment, prompt?: string) => {
    returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setMode('professional'); setPanelOpen(true);
    if (workspaceId) setEntry({ workspaceId, id: Date.now(), attachment, prompt });
  }, [setMode, workspaceId]);
  const closePanel = useCallback(() => {
    setPanelOpen(false);
    requestAnimationFrame(() => {
      if (returnFocus.current?.isConnected) returnFocus.current.focus();
      else document.querySelector<HTMLButtonElement>('[data-testid="professional-agent-toggle"]')?.focus();
    });
  }, []);
  return <Context.Provider value={{ ...session, panelOpen, openPanel, closePanel, entry }}>{children}</Context.Provider>;
}
export function useAgent() {
  const value = useContext(Context);
  if (!value) throw new Error('AgentProvider is required');
  return value;
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { agentBridge, agentIsActive, type AgentRequest, type AgentSnapshot } from '../bridge/agentBridge';

/** Owned by the mounted creation surface, independent of its current visibility. */
export function useAgentSession(visible: boolean, workspaceId?: string) {
  const [snapshot, setSnapshot] = useState<AgentSnapshot | null>(null);
  const [loading, setLoading] = useState(agentBridge.available);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<AgentRequest['operation'] | null>(null);
  const current = useRef(snapshot);
  const isVisible = useRef(visible);
  const commandPending = useRef(false);
  const generation = useRef(0);
  isVisible.current = visible;
  const accept = useCallback((next: AgentSnapshot) => {
    if (current.current && next.sequence < current.current.sequence) return;
    const changed = !current.current || next.sequence !== current.current.sequence;
    current.current = next;
    if (changed) setSnapshot(next);
    setError(null);
  }, []);

  useEffect(() => {
    const ownGeneration = ++generation.current;
    current.current = null; setSnapshot(null); setError(null); setPending(null);
    if (!agentBridge.available || !workspaceId) { setLoading(false); return; }
    setLoading(true);
    let cancelled = false;
    let timer: number | undefined;
    let reading = false;
    const read = async (detect = false) => {
      if (cancelled || reading) return;
      if (!commandPending.current) {
        reading = true;
        try {
          const next = await agentBridge.invoke({ operation: detect ? 'detect' : 'status' });
          if (!cancelled && generation.current === ownGeneration) accept(next);
        } catch (failure) {
          if (!cancelled) setError(failure instanceof Error ? failure.message : String(failure));
        } finally {
          reading = false;
          if (!cancelled) setLoading(false);
        }
      }
      if (!cancelled) timer = window.setTimeout(() => void read(), agentIsActive(current.current?.state) || current.current?.detecting
        ? document.hidden ? 1500 : 650 : isVisible.current && !document.hidden ? 3000 : 10000);
    };
    const wake = () => { if (!document.hidden && !reading) { window.clearTimeout(timer); void read(); } };
    document.addEventListener('visibilitychange', wake);
    void read(true);
    return () => { cancelled = true; ++generation.current; window.clearTimeout(timer); document.removeEventListener('visibilitychange', wake); };
  }, [accept, workspaceId]);

  const invoke = useCallback(async (request: AgentRequest): Promise<AgentSnapshot | null> => {
    if (commandPending.current) return null;
    const ownGeneration = generation.current;
    commandPending.current = true; setPending(request.operation); setError(null);
    try {
      const next = await agentBridge.invoke(request);
      if (generation.current !== ownGeneration) return null;
      accept(next);
      return next;
    } catch (failure) {
      if (generation.current === ownGeneration) setError(failure instanceof Error ? failure.message : String(failure));
      return null;
    } finally {
      commandPending.current = false;
      if (generation.current === ownGeneration) setPending(null);
    }
  }, [accept]);
  return { snapshot, loading, error, pending, invoke, available: agentBridge.available };
}

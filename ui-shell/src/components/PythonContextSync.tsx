import { useEffect, useRef } from 'react';
import { useWorkbench } from '../context/WorkbenchContext';
import { pythonBridge, PythonContextState } from '../bridge/pythonBridge';

/** Keep context live even when the scripting page is not visible. */
export function PythonContextSync() {
  const { selectedElementId, setSelectedElementId, activeView } = useWorkbench();
  const version = useRef(-1);
  const pending = useRef(0);
  const latestUi = useRef({ selectedElementId, activeView });
  latestUi.current = { selectedElementId, activeView };
  useEffect(() => {
    if (!pythonBridge.available) return;
    pending.current++;
    void pythonBridge.invoke<PythonContextState>({ operation: 'sync_context', elementId: selectedElementId, view: activeView })
      .then(context => { version.current = Math.max(version.current, context.selectionVersion); })
      .catch(() => undefined).finally(() => { pending.current--; });
  }, [selectedElementId, activeView]);
  useEffect(() => {
    let disposed = false;
    let querying = false;
    const timer = window.setInterval(async () => {
      if (!pythonBridge.available || querying || pending.current) return;
      querying = true;
      try {
        const context = await pythonBridge.invoke<PythonContextState>(version.current < 0
          ? { operation: 'sync_context', elementId: latestUi.current.selectedElementId, view: latestUi.current.activeView }
          : { operation: 'get_context' });
        if (!disposed && !pending.current && context.selectionVersion > version.current) {
          version.current = context.selectionVersion;
          setSelectedElementId(context.activeElementId);
        }
      } catch { /* Renderer recovery reconnects on the next poll. */ }
      finally { querying = false; }
    }, 500);
    return () => { disposed = true; window.clearInterval(timer); };
  }, [setSelectedElementId]);
  return null;
}

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useWorkbench, type NavView } from './WorkbenchContext';

export type WorkbenchMode = 'simple' | 'professional';
export const WORKBENCH_MODE_STORAGE_KEY = 'copperbench.workbench.mode';
const durableKeys = new Set(['copperbench.theme', 'copperbench.ui.locale', 'copperbench:first-creation:v1', 'copperbench.python.executable']);

export function readInitialWorkbenchMode(storage: Storage): WorkbenchMode {
  try {
    const saved = storage.getItem(WORKBENCH_MODE_STORAGE_KEY);
    if (saved === 'simple' || saved === 'professional') return saved;
    for (let index = 0; index < storage.length; index++) {
      const key = storage.key(index);
      if (key && (durableKeys.has(key) || key.startsWith('copperbench.python.') || key.startsWith('copperbench.workspace-icon.'))
        && storage.getItem(key) !== null) return 'professional';
    }
    return 'simple';
  } catch { return 'professional'; }
}

// Capture migration evidence before mounted editors write their default preferences.
const initialMode: WorkbenchMode = (() => {
  try { return typeof window === 'undefined' ? 'professional' : readInitialWorkbenchMode(window.localStorage); }
  catch { return 'professional'; }
})();
const ModeContext = createContext<{ mode: WorkbenchMode; setMode: (mode: WorkbenchMode) => void } | null>(null);

export function WorkbenchModeProvider({ children }: { children: ReactNode }) {
  const { activeView, setActiveView } = useWorkbench();
  const [mode, updateMode] = useState<WorkbenchMode>(initialMode);
  const professionalView = useRef<NavView>('hub');
  const setMode = useCallback((next: WorkbenchMode) => {
    if (next === mode) return;
    if (next === 'simple') { professionalView.current = activeView; setActiveView('hub'); }
    else setActiveView(professionalView.current);
    updateMode(next);
  }, [activeView, mode, setActiveView]);
  useEffect(() => {
    try { window.localStorage.setItem(WORKBENCH_MODE_STORAGE_KEY, mode); } catch { /* Preserve the current window's choice. */ }
  }, [mode]);
  return <ModeContext.Provider value={{ mode, setMode }}>{children}</ModeContext.Provider>;
}

export function useWorkbenchMode() {
  const context = useContext(ModeContext);
  if (!context) throw new Error('WorkbenchModeProvider is required');
  return context;
}

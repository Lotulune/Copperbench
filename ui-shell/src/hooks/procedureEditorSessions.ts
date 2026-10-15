import { useSyncExternalStore } from 'react';
import type { Diagnostic, ProcedureEditorProjection } from '../types/contract';
import type { UiMessage } from '../i18n';
import { setUnsavedDraftCount } from './unsavedDraftGuard';

export interface ProcedureEditorSession {
  projection: ProcedureEditorProjection | null;
  snapshot: Record<string, unknown> | null;
  trigger: string | null;
  revision: number;
  dirty: boolean;
  loading: boolean;
  saving: boolean;
  saveUnknown: boolean;
  diagnostics: Diagnostic[];
  message: UiMessage | null;
  readToken: number;
}
const sessions = new Map<string, ProcedureEditorSession>();
const listeners = new Set<() => void>();
let readSequence = 0;
export function getProcedureSession(key: string): ProcedureEditorSession {
  if (!sessions.has(key)) sessions.set(key, { projection: null, snapshot: null, trigger: null,
    revision: 0, dirty: false, loading: false, saving: false, saveUnknown: false, diagnostics: [], message: null, readToken: 0 });
  return sessions.get(key)!;
}
export function updateProcedureSession(key: string, change: (current: ProcedureEditorSession) => ProcedureEditorSession) {
  sessions.set(key, change(getProcedureSession(key)));
  setUnsavedDraftCount('procedures', [...sessions.values()].filter(session => session.dirty).length);
  listeners.forEach(listener => listener());
}
export function useProcedureEditorSession(key: string) {
  return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener); }; }, () => getProcedureSession(key));
}
export function nextProcedureReadToken() { return ++readSequence; }

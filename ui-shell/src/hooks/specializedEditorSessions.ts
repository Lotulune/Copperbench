import { useSyncExternalStore } from 'react';
import type { Diagnostic, ModElementEditorProjection } from '../types/contract';
import type { UiMessage } from '../i18n';
import { setUnsavedDraftCount } from './unsavedDraftGuard';

export interface SpecializedEditorSession<T> {
  data: T;
  editor: ModElementEditorProjection | null;
  revision: number;
  dirty: boolean;
  loading: boolean;
  saving: boolean;
  saveUnknown: boolean;
  error: UiMessage | null;
  diagnostics: Diagnostic[];
  readToken: number;
}
const sessions = new Map<string, SpecializedEditorSession<unknown>>();
const listeners = new Set<() => void>();
let readSequence = 0;

export function specializedEditorKey(workspaceId: string, elementId: string, editor: string): string {
  return JSON.stringify([workspaceId, elementId, editor]);
}
export function getSpecializedSession<T>(key: string, initial: () => T): SpecializedEditorSession<T> {
  if (!sessions.has(key)) sessions.set(key, { data: initial(), editor: null, revision: 0, dirty: false,
    loading: false, saving: false, saveUnknown: false, error: null, diagnostics: [], readToken: 0 });
  return sessions.get(key)! as SpecializedEditorSession<T>;
}
export function updateSpecializedSession<T>(key: string, initial: () => T,
  change: (current: SpecializedEditorSession<T>) => SpecializedEditorSession<T>) {
  sessions.set(key, change(getSpecializedSession(key, initial)));
  setUnsavedDraftCount('specialized-elements', [...sessions.values()].filter(session => session.dirty).length);
  listeners.forEach(listener => listener());
}
export function useSpecializedEditorSession<T>(key: string, initial: () => T) {
  return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    () => getSpecializedSession(key, initial));
}
export function nextSpecializedReadToken() { return ++readSequence; }

/** Rebase only the submitted data. A newer session can already be editing the
 * same object after navigation; its inputs remain dirty against the new baseline. */
export function commitSpecializedSession<T>(key: string, initial: () => T, submitted: T,
  editor: ModElementEditorProjection, revision: number, diagnostics: Diagnostic[], committedData?: T) {
  updateSpecializedSession(key, initial, current => ({ ...current, editor, revision, saving: false,
    data: current.data === submitted ? committedData ?? current.data : current.data,
    saveUnknown: false, diagnostics, dirty: current.data !== submitted, error: null }));
}

export function rebaseEditor(editor: ModElementEditorProjection, changes: Array<{ path: string; value: unknown }>,
  element: ModElementEditorProjection['element'] = editor.element): ModElementEditorProjection {
  return { ...editor, element, sections: editor.sections.map(section => ({ ...section, fields: section.fields.map(field => {
    const change = changes.find(change => change.path === field.path);
    return change ? { ...field, value: change.value } : field;
  }) })) };
}

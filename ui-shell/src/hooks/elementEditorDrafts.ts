import type { Diagnostic, ModElementEditorProjection } from '../types/contract';
import { useSyncExternalStore } from 'react';
import { setUnsavedDraftCount } from './unsavedDraftGuard';

export interface ElementEditorDraft {
  editor: ModElementEditorProjection;
  values: Record<string, unknown>;
  referenceDrafts: Record<string, string>;
  baseRevision: number;
  externalChange: boolean;
  diagnostics?: Diagnostic[];
  saveUnknown?: boolean;
  saving?: boolean;
}

// Session-only drafts outlive an inspector route, without writing unfinished
// (including invalid) field values to the workspace or browser storage.
const drafts = new Map<string, ElementEditorDraft>();
const listeners = new Set<() => void>();

export function elementEditorDraftKey(workspaceId: string, elementId: string): string {
  return JSON.stringify([workspaceId, elementId]);
}

export function getElementEditorDraft(key: string): ElementEditorDraft | undefined {
  return drafts.get(key);
}

export function setElementEditorDraft(key: string, draft: ElementEditorDraft): void {
  drafts.set(key, draft);
  setUnsavedDraftCount('elements', drafts.size);
  listeners.forEach(listener => listener());
}

export function clearElementEditorDraft(key: string): void {
  drafts.delete(key);
  setUnsavedDraftCount('elements', drafts.size);
  listeners.forEach(listener => listener());
}

export function useElementEditorDraft(key: string) {
  return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener); }; }, () => drafts.get(key));
}

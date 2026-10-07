import type { ModElementEditorProjection } from '../types/contract';
import { setUnsavedDraftCount } from './unsavedDraftGuard';

export interface FunctionEditorSession {
  editor: ModElementEditorProjection | null;
  code: string;
  tags: string[];
  namespace: string;
  newTag: string;
  revision: number;
  loading: boolean;
  saving: boolean;
  error: string | null;
}

const sessions = new Map<string, FunctionEditorSession>();
const listeners = new Set<() => void>();

export function functionFields(editor: ModElementEditorProjection | null) {
  const fields = editor?.sections.flatMap(section => section.fields) ?? [];
  return {
    code: fields.find(field => field.path === '/code' || field.path === '/fields/code'),
    tags: fields.find(field => field.path === '/tags' || field.path === '/fields/tags'),
    namespace: fields.find(field => field.path === '/namespace' || field.path === '/fields/namespace')
  };
}

export function functionHasChanges(session: FunctionEditorSession): boolean {
  return Object.entries(functionFields(session.editor)).some(([key, field]) => {
    const baseline = key === 'tags' ? (Array.isArray(field?.value) ? field.value : [])
      : (typeof field?.value === 'string' ? field.value : '');
    return field?.readOnly === false && JSON.stringify(baseline) !== JSON.stringify(session[key as 'code' | 'tags' | 'namespace']);
  });
}

export function getFunctionSession(key: string): FunctionEditorSession {
  if (!sessions.has(key)) sessions.set(key, { editor: null, code: '', tags: [], namespace: '', newTag: '',
    revision: 0, loading: false, saving: false, error: null });
  return sessions.get(key)!;
}

export function subscribeFunctionSessions(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function updateFunctionSession(key: string, change: (current: FunctionEditorSession) => FunctionEditorSession) {
  sessions.set(key, change(getFunctionSession(key)));
  setUnsavedDraftCount('functions', [...sessions.values()].filter(session => functionHasChanges(session) || session.newTag.length > 0).length);
  listeners.forEach(listener => listener());
}

export function loadedFunction(editor: ModElementEditorProjection, revision: number): FunctionEditorSession {
  const fields = functionFields(editor);
  return { editor, revision, code: typeof fields.code?.value === 'string' ? fields.code.value : '',
    tags: Array.isArray(fields.tags?.value) ? fields.tags.value as string[] : [],
    namespace: typeof fields.namespace?.value === 'string' ? fields.namespace.value : '',
    newTag: '', loading: false, saving: false, error: null };
}

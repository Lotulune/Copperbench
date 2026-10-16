import type { ModElementEditorProjection, ModElementSummary, TaskSummary, WorkspaceSummary } from '../types/contract';

export const FIRST_CREATION_STORAGE_KEY = 'copperbench:first-creation:v1';
export const FIRST_CREATION_GENERATOR = 'fabric-1.21.1';
export const FIRST_CREATION_ELEMENT = 'trail_block';

/** References and presentation preferences only. Completion is never stored here. */
export interface FirstCreationTaskReference {
  id: string;
  inputRevision?: number;
  output?: string;
}

export interface FirstCreationBinding {
  workspaceFile: string;
  workspaceId?: string;
  modId: string;
  elementId?: string;
  showOnOpen: boolean;
  build?: FirstCreationTaskReference;
  clients: FirstCreationTaskReference[];
  export?: FirstCreationTaskReference;
}

export function normalizeWorkspacePath(value: string): string {
  const path = value.replaceAll('\\', '/').replace(/\/+$/, '');
  return /^[a-z]:\//i.test(path) ? path.toLowerCase() : path;
}

export function bindingMatchesWorkspace(binding: FirstCreationBinding, workspaceId: string, root: string): boolean {
  if (binding.workspaceId) return binding.workspaceId === workspaceId;
  const file = normalizeWorkspacePath(binding.workspaceFile);
  return root !== '' && file.slice(0, file.lastIndexOf('/')) === normalizeWorkspacePath(root);
}

export function parseFirstCreationBindings(raw: string | null): FirstCreationBinding[] {
  if (!raw) return [];
  const parsed: unknown = JSON.parse(raw);
  if (!parsed || typeof parsed !== 'object' || !('version' in parsed) || parsed.version !== 1
    || !('bindings' in parsed) || !Array.isArray(parsed.bindings)) throw new Error('Invalid first-creation references');
  const taskReference = (value: unknown): FirstCreationTaskReference | undefined => {
    if (value === undefined) return undefined;
    if (!value || typeof value !== 'object' || !('id' in value) || typeof value.id !== 'string' || !value.id) throw new Error('Invalid first-creation task reference');
    if ('inputRevision' in value && (!Number.isSafeInteger(value.inputRevision) || Number(value.inputRevision) < 0)) throw new Error('Invalid task input revision reference');
    if ('output' in value && typeof value.output !== 'string') throw new Error('Invalid task output reference');
    return {
      id: value.id,
      ...('inputRevision' in value && Number.isInteger(value.inputRevision) && Number(value.inputRevision) >= 0
        ? { inputRevision: Number(value.inputRevision) } : {}),
      ...('output' in value && typeof value.output === 'string' ? { output: value.output } : {})
    };
  };
  return parsed.bindings.map((value: unknown): FirstCreationBinding => {
    if (!value || typeof value !== 'object' || !('workspaceFile' in value) || typeof value.workspaceFile !== 'string'
      || !/^(?:[a-z]:[/\\]|[/\\]{2}|\/).+\.mcreator$/i.test(value.workspaceFile)
      || !('modId' in value) || typeof value.modId !== 'string' || !/^[a-z][a-z0-9_]{1,31}$/.test(value.modId)) {
      throw new Error('Invalid first-creation workspace reference');
    }
    const identity = value as Record<string, unknown>;
    for (const key of ['workspaceId', 'elementId']) {
      if (key in identity && (typeof identity[key] !== 'string' || !identity[key])) throw new Error('Invalid object identity reference');
    }
    if (!('showOnOpen' in value) || typeof value.showOnOpen !== 'boolean' || !('clients' in value) || !Array.isArray(value.clients)) throw new Error('Invalid first-creation display or client references');
    return {
      workspaceFile: value.workspaceFile, modId: value.modId,
      ...('workspaceId' in value && typeof value.workspaceId === 'string' && value.workspaceId ? { workspaceId: value.workspaceId } : {}),
      ...('elementId' in value && typeof value.elementId === 'string' && value.elementId ? { elementId: value.elementId } : {}),
      showOnOpen: 'showOnOpen' in value && value.showOnOpen === true,
      build: 'build' in value ? taskReference(value.build) : undefined,
      clients: 'clients' in value && Array.isArray(value.clients)
        ? value.clients.map(taskReference).filter((task): task is FirstCreationTaskReference => !!task) : [],
      export: 'export' in value ? taskReference(value.export) : undefined
    };
  });
}

export type FirstCreationElementState =
  | { status: 'unbound'; conflict: ModElementSummary | null }
  | { status: 'missing'; conflict: ModElementSummary | null }
  | { status: 'ready'; element: ModElementSummary }
  | { status: 'changed'; element: ModElementSummary };

export function firstCreationElement(binding: FirstCreationBinding, elements: ModElementSummary[]): FirstCreationElementState {
  const conflict = elements.find(element => element.name === FIRST_CREATION_ELEMENT) ?? null;
  if (!binding.elementId) return { status: 'unbound', conflict };
  const element = elements.find(candidate => candidate.id === binding.elementId);
  if (!element) return { status: 'missing', conflict };
  return element.type === 'block' && element.name === FIRST_CREATION_ELEMENT
    ? { status: 'ready', element } : { status: 'changed', element };
}

/** Reads committed editor fields. A draft is deliberately not an input. */
export function hasSavedFirstCreationFields(editor: ModElementEditorProjection | null, element: ModElementSummary | null): boolean {
  if (!editor || !element || editor.element.id !== element.id || editor.element.updatedAt !== element.updatedAt) return false;
  const fields = editor.sections.flatMap(section => section.fields);
  const value = (name: string) => fields.find(field => field.path.split('/').at(-1) === name)?.value;
  return element.displayName === 'Trail Block' && value('hardness') === 3 && value('resistance') === 6 && value('hasBlockItem') === true;
}

export interface FirstCreationInputObservation {
  workspaceId: string;
  observedAt: string;
  currentSha256: string | null;
  tasks: Record<string, { status: 'current' | 'changed' | 'changed_during_task' | 'unknown'; sourceSha256?: string; detail?: string }>;
}

export function parseInputObservation(raw: unknown, workspaceId: string): FirstCreationInputObservation {
  if (!raw || typeof raw !== 'object') throw new Error('Invalid task input observation');
  const value = raw as Record<string, unknown>;
  if (value.workspaceId !== workspaceId || typeof value.observedAt !== 'string' || !Number.isFinite(Date.parse(value.observedAt))
    || !(value.currentSha256 === null || typeof value.currentSha256 === 'string' && /^[a-f0-9]{64}$/.test(value.currentSha256))
    || !value.tasks || typeof value.tasks !== 'object' || Array.isArray(value.tasks)) throw new Error('Invalid task input observation');
  const tasks: FirstCreationInputObservation['tasks'] = {};
  for (const [id, rawFact] of Object.entries(value.tasks)) {
    if (!rawFact || typeof rawFact !== 'object') throw new Error('Invalid task input fact');
    const fact = rawFact as Record<string, unknown>;
    if (!['current', 'changed', 'changed_during_task', 'unknown'].includes(String(fact.status))
      || fact.sourceSha256 !== undefined && !(typeof fact.sourceSha256 === 'string' && /^[a-f0-9]{64}$/.test(fact.sourceSha256))
      || fact.detail !== undefined && !(typeof fact.detail === 'string' && fact.detail.length <= 1024)) throw new Error('Invalid task input fact');
    tasks[id] = { status: fact.status as FirstCreationInputObservation['tasks'][string]['status'],
      ...(typeof fact.sourceSha256 === 'string' ? { sourceSha256: fact.sourceSha256 } : {}),
      ...(typeof fact.detail === 'string' ? { detail: fact.detail } : {}) };
  }
  return { workspaceId, observedAt: value.observedAt, currentSha256: value.currentSha256, tasks };
}

export function firstCreationTask(reference: FirstCreationTaskReference | undefined, tasks: Record<string, TaskSummary>, revision: number,
  inputs?: FirstCreationInputObservation | null) {
  const task = reference ? tasks[reference.id] ?? null : null;
  const input = reference ? inputs?.tasks[reference.id] : undefined;
  const observed = !!task?.sourceSnapshot?.sha256 && input?.sourceSha256 === task.sourceSnapshot.sha256;
  const freshness = !observed ? 'unknown'
    : reference?.inputRevision !== undefined && reference.inputRevision !== revision ? 'older_revision'
      : input?.status === 'changed_during_task' ? 'changed_during_task'
      : input?.status === 'changed' ? 'changed_inputs'
        : input?.status === 'current' && inputs?.currentSha256 === task?.sourceSnapshot?.sha256 ? 'current' : 'unknown';
  return { task, freshness, status: task?.resultObservation === 'unconfirmed_after_session_loss' ? 'unconfirmed' : task?.state ?? (reference ? 'unavailable' : 'not_started') } as const;
}

/** Session-only user observations are tied to the exact visible references. */
export function firstCreationConfirmationKey(workspace: WorkspaceSummary, element: ModElementSummary, binding: FirstCreationBinding,
  inputs?: FirstCreationInputObservation | null): string {
  return JSON.stringify([workspace.id, workspace.revision, workspace.generator.id, element.id, element.updatedAt,
    binding.build?.id ?? null, binding.clients.map(client => client.id), inputs?.currentSha256 ?? null,
    [binding.build?.id, ...binding.clients.slice(-2).map(client => client.id)].map(id => id ? inputs?.tasks[id] ?? null : null)]);
}

export function firstCreationInputsConfirmed(binding: FirstCreationBinding, tasks: Record<string, TaskSummary>, revision: number,
  inputs: FirstCreationInputObservation | null): boolean {
  const references = [binding.build, ...binding.clients.slice(-2)];
  return !!binding.build && binding.clients.length >= 2 && references.every(reference => {
    const fact = firstCreationTask(reference, tasks, revision, inputs);
    return fact.status === 'succeeded' && !!fact.task?.completedAt && fact.freshness === 'current';
  });
}

/** Compatibility belongs to the exported receipt, including when today's workspace uses another generator. */
export function receiptCompatibility(environment: unknown) {
  const fields = environment && typeof environment === 'object' && !Array.isArray(environment) ? environment as Record<string, unknown> : {};
  const value = (key: string) => { const field = fields[key]; return typeof field === 'string' && field.trim() ? field.trim() : null; };
  return { loader: value('loader'), minecraftVersion: value('minecraftVersion'), loaderVersion: value('loaderVersion') };
}

export function isRelativeDeliveryPath(value: string): boolean {
  const path = value.trim().replaceAll('\\', '/');
  return path.length > 0 && !path.startsWith('/') && !/[:\u0000-\u001f]/.test(path)
    && path.split('/').every(segment => segment !== '' && segment !== '.' && segment !== '..')
    && /\.(jar|zip)$/i.test(path);
}

export function defaultDeliveryPath(workspaceName: string, kind: WorkspaceSummary['kind'], requestId: string, generatorId: string): string {
  const name = workspaceName.toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '') || 'creation';
  // Data packs retain kind=mod in the frozen wire contract; their generator
  // identifies the ZIP output required by the pack backend.
  const extension = kind === 'resource_pack' || generatorId.startsWith('datapack-') ? 'zip' : 'jar';
  return `build/libs/copperbench-delivery-${requestId}/${name}.${extension}`;
}

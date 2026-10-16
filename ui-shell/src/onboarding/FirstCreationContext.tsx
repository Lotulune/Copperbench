import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { coreBridge, isNativeHostPresent, safeRandomUUID, taskLogWatermark } from '../bridge';
import { getFirstCreationPreferencesHost } from '../bridge/firstCreationBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiMessage, type UiMessage } from '../i18n';
import type { CommandResult, TaskProjection } from '../types/contract';
import { FIRST_CREATION_STORAGE_KEY, bindingMatchesWorkspace, normalizeWorkspacePath, parseFirstCreationBindings, parseInputObservation,
  type FirstCreationBinding, type FirstCreationTaskReference, type FirstCreationInputObservation } from './firstCreationModel';

interface FirstCreationContextValue {
  guideOpen: boolean;
  openGuide: (workspaceFile?: string) => void;
  closeGuide: () => void;
  pauseGuide: () => void;
  tourActive: boolean;
  creationIntent: boolean;
  beginCreation: () => void;
  leaveCreation: () => void;
  binding: FirstCreationBinding | null;
  currentBinding: FirstCreationBinding | null;
  workspaceRoot: string;
  referenceError: UiMessage | null;
  referencesReady: boolean;
  refreshReferences: () => void;
  inputObservation: FirstCreationInputObservation | null;
  inputObservationPending: boolean;
  bindCreatedWorkspace: (result: CommandResult) => void;
  bindElement: (workspaceFile: string, elementId: string) => void;
  recordTask: (result: CommandResult, kind: 'build' | 'client' | 'export', workspaceId: string, revision: number, output?: string) => void;
  deliveryOpen: boolean;
  openDelivery: () => void;
  closeDelivery: () => void;
  deliveryReference: FirstCreationTaskReference | undefined;
  openWorkspaceOpen: () => void;
  workspaceOpen: boolean;
  closeWorkspaceOpen: () => void;
  observations: { key: string; placed: boolean; persisted: boolean };
  setObservations: React.Dispatch<React.SetStateAction<{ key: string; placed: boolean; persisted: boolean }>>;
}

const FirstCreationContext = createContext<FirstCreationContextValue | null>(null);

export function FirstCreationProvider({ children }: { children: React.ReactNode }) {
  const { state, setActiveView, getWorkspaceRoot, refreshWorkspace } = useWorkbench();
  const workspaceId = state.workbench?.workspace.id;
  const native = isNativeHostPresent();
  const [initial] = useState(() => {
    try { return { bindings: parseFirstCreationBindings(window.localStorage.getItem(FIRST_CREATION_STORAGE_KEY)), error: null }; }
    catch { return { bindings: [], error: uiMessage('无法读取已保存的首作引用。你仍可打开已有工作区或开始新作品。', 'Saved first-creation references could not be read. You can open an existing workspace or start a new creation.') }; }
  });
  const [bindings, setBindings] = useState<FirstCreationBinding[]>(initial.bindings);
  const bindingsRef = useRef(bindings);
  bindingsRef.current = bindings;
  const localReferenceGeneration = useRef(0);
  const [localStorageReady] = useState(initial.error === null);
  const [referenceError, setReferenceError] = useState<UiMessage | null>(initial.error);
  const [guideOpen, setGuideOpen] = useState(false);
  const [tourActive, setTourActive] = useState(false);
  const [guideWorkspaceFile, setGuideWorkspaceFile] = useState<string | null>(null);
  const [creationIntent, setCreationIntent] = useState(false);
  const [workspaceRoot, setWorkspaceRoot] = useState('');
  const [refreshToken, setRefreshToken] = useState(0);
  const [nativeRead, setNativeRead] = useState<{ workspaceId: string | undefined; status: 'loading' | 'ready' | 'failed' }>({ workspaceId, status: 'loading' });
  const pendingNativeSave = useRef(Promise.resolve());
  const [deliveryOpen, setDeliveryOpen] = useState(false);
  const [workspaceOpen, setWorkspaceOpen] = useState(false);
  const [deliveries, setDeliveries] = useState<Record<string, FirstCreationTaskReference>>({});
  const [observations, setObservations] = useState({ key: '', placed: false, persisted: false });
  const [inputObservation, setInputObservation] = useState<FirstCreationInputObservation | null>(null);
  const [inputObservationPending, setInputObservationPending] = useState(false);
  const [inputRefreshToken, setInputRefreshToken] = useState(0);
  const autoOpenedWorkspace = useRef<string | null>(null);
  const dismissedGuideWorkspaces = useRef(new Set<string>());
  const getRootRef = useRef(getWorkspaceRoot);
  getRootRef.current = getWorkspaceRoot;
  const refreshWorkspaceRef = useRef(refreshWorkspace);
  refreshWorkspaceRef.current = refreshWorkspace;

  const updateBinding = useCallback((file: string, update: (binding: FirstCreationBinding) => FirstCreationBinding) => {
    localReferenceGeneration.current++;
    setBindings(previous => previous.map(binding => normalizeWorkspacePath(binding.workspaceFile) === normalizeWorkspacePath(file) ? update(binding) : binding));
  }, []);

  useEffect(() => {
    if (!localStorageReady) return;
    try { window.localStorage.setItem(FIRST_CREATION_STORAGE_KEY, JSON.stringify({ version: 1, bindings })); }
    catch { setReferenceError(uiMessage('首作引用无法保存。关闭应用后，请从原工作区继续。', 'First-creation references could not be saved. Continue from the original workspace after reopening the app.')); }
  }, [bindings, localStorageReady]);
  useEffect(() => {
    const changed = (event: StorageEvent) => {
      if (event.key !== FIRST_CREATION_STORAGE_KEY) return;
      try { setBindings(parseFirstCreationBindings(event.newValue)); }
      catch { setReferenceError(uiMessage('首作引用已变化，但无法读取。请核对原工作区。', 'First-creation references changed but could not be read. Check the original workspace.')); }
    };
    window.addEventListener('storage', changed);
    return () => window.removeEventListener('storage', changed);
  }, []);

  useEffect(() => {
    let current = true;
    setWorkspaceRoot('');
    if (!workspaceId || !native) return;
    void getRootRef.current().then(root => { if (current) setWorkspaceRoot(root); })
      .catch(() => { if (current) setReferenceError(uiMessage('工作区位置暂时无法读取。请重读状态后继续。', 'The workspace location is temporarily unavailable. Refresh its state to continue.')); });
    return () => { current = false; };
  }, [workspaceId, native, refreshToken]);

  useEffect(() => {
    let current = true;
    const host = getFirstCreationPreferencesHost();
    setNativeRead({ workspaceId, status: 'loading' });
    if (!workspaceId || !native || !host) { setNativeRead({ workspaceId, status: 'ready' }); return; }
    if (host.workspaceId !== workspaceId) {
      setNativeRead({ workspaceId, status: 'failed' });
      setReferenceError(uiMessage('首作引用属于另一个工作区窗口。请打开原工作区继续。', 'The first-creation references belong to another workspace window. Open the original workspace to continue.'));
      return;
    }
    const requestedGeneration = localReferenceGeneration.current;
    void pendingNativeSave.current.then(() => host.read()).then(raw => {
      if (!current) return;
      if (raw !== null) {
        const [saved] = parseFirstCreationBindings(JSON.stringify({ version: 1, bindings: [raw] }));
        if (normalizeWorkspacePath(saved.workspaceFile) !== normalizeWorkspacePath(host.workspaceFile)) throw new Error('The references belong to another workspace file.');
        // The scoped native host confirms this physical workspace, even if a new session has a new ID.
        setBindings(previous => {
          const newer = localReferenceGeneration.current !== requestedGeneration
            ? previous.find(value => normalizeWorkspacePath(value.workspaceFile) === normalizeWorkspacePath(host.workspaceFile)) : undefined;
          const authoritative = { ...saved, ...newer, workspaceId,
            ...(dismissedGuideWorkspaces.current.has(workspaceId) ? { showOnOpen: false } : {}) };
          return [...previous.filter(value => normalizeWorkspacePath(value.workspaceFile) !== normalizeWorkspacePath(host.workspaceFile)), authoritative];
        });
      }
      setNativeRead({ workspaceId, status: 'ready' });
    }).catch(error => {
      if (!current) return;
      setNativeRead({ workspaceId, status: 'failed' });
      setReferenceError(uiMessage('原首作引用无法读取，已保留原文件。请检查工作区中的 .copperbench/first-creation.json 后重读。', 'Original first-creation references could not be read; the file was preserved. Check .copperbench/first-creation.json in the workspace, then refresh.'));
      if (error instanceof Error) console.warn('[Copperbench] First-creation reference read failed:', error.message);
    });
    return () => { current = false; };
  }, [workspaceId, native, refreshToken]);

  const referencesReady = nativeRead.workspaceId === workspaceId && nativeRead.status === 'ready';

  const currentBinding = useMemo(() => workspaceId && referencesReady
    ? bindings.find(binding => bindingMatchesWorkspace(binding, workspaceId, workspaceRoot)) ?? null : null,
  [bindings, workspaceId, workspaceRoot, referencesReady]);
  const binding = useMemo(() => guideWorkspaceFile
    ? bindings.find(candidate => normalizeWorkspacePath(candidate.workspaceFile) === normalizeWorkspacePath(guideWorkspaceFile)) ?? null : currentBinding,
  [bindings, guideWorkspaceFile, currentBinding]);

  const currentSerialized = currentBinding ? JSON.stringify(currentBinding) : '';
  useEffect(() => {
    const host = getFirstCreationPreferencesHost();
    if (!referencesReady || !host || !currentSerialized || host.workspaceId !== workspaceId) return;
    const saved = JSON.parse(currentSerialized) as FirstCreationBinding;
    if (saved.workspaceId !== workspaceId || normalizeWorkspacePath(saved.workspaceFile) !== normalizeWorkspacePath(host.workspaceFile)) return;
    // Serialize writes so an older preference save cannot overwrite newer object/task references.
    pendingNativeSave.current = pendingNativeSave.current.then(() => host.save(saved)).catch(() => {
      setReferenceError(uiMessage('首作引用保存失败。保留此窗口并重读状态后继续。', 'First-creation references could not be saved. Keep this window open and refresh the state to continue.'));
    });
  }, [currentSerialized, referencesReady, workspaceId]);

  useEffect(() => {
    if (!currentBinding || !workspaceId || !native) return;
    if (!currentBinding.workspaceId) updateBinding(currentBinding.workspaceFile, value => ({ ...value, workspaceId }));
    if (autoOpenedWorkspace.current === workspaceId) return;
    autoOpenedWorkspace.current = workspaceId;
    if (currentBinding.showOnOpen && !dismissedGuideWorkspaces.current.has(workspaceId)) {
      setGuideWorkspaceFile(currentBinding.workspaceFile);
      setTourActive(true);
      setGuideOpen(true);
      updateBinding(currentBinding.workspaceFile, value => ({ ...value, showOnOpen: false }));
    }
  }, [currentBinding, workspaceId, native, updateBinding]);

  // Re-entering the guide only reads the original task IDs. It never replays a command.
  const taskIds = currentBinding ? [currentBinding.build?.id, ...currentBinding.clients.map(task => task.id), currentBinding.export?.id]
    .filter((id): id is string => !!id).join('|') : '';
  useEffect(() => {
    let current = true;
    if ((!guideOpen && !deliveryOpen) || !native || !workspaceId || !taskIds || !referencesReady) return;
    for (const taskId of new Set(taskIds.split('|'))) {
      const afterLogSequence = taskLogWatermark(state.taskLogs[taskId] ?? []);
      void coreBridge.sendQuery<TaskProjection>({ messageType: 'query', schemaVersion: '1.0', requestId: safeRandomUUID(),
        workspaceId, operation: 'get_task', payload: { taskId, afterLogSequence } }).then(result => {
        if (current && (result.status !== 'succeeded' || result.workspaceId !== workspaceId)) {
          setReferenceError(uiMessage('原任务状态暂时无法取得。查看任务日志或重读状态；不会再次启动任务。', 'The original task status is temporarily unavailable. View its logs or refresh the state; the task will not be started again.'));
        }
      }).catch(() => {
        if (current) setReferenceError(uiMessage('原任务状态暂时无法取得。请恢复连接后重读状态。', 'The original task status is temporarily unavailable. Restore the connection and refresh the state.'));
      });
    }
    return () => { current = false; };
    // Log updates and language changes must not issue another restoration query.
  }, [guideOpen, deliveryOpen, taskIds, workspaceId, native, refreshToken, referencesReady]);

  // Read one input inventory for the relevant references. Log/progress polling never triggers hashing.
  const inputTaskIds = [...new Set([currentBinding?.build?.id, ...(currentBinding?.clients.slice(-2).map(task => task.id) ?? []),
    currentBinding?.export?.id, workspaceId ? deliveries[workspaceId]?.id : undefined].filter((id): id is string => !!id))];
  const inputTaskKey = `${state.workbench?.workspace.revision}|${inputTaskIds.map(id => `${id}:${state.tasks[id]?.state}:${state.tasks[id]?.completedAt}:${state.tasks[id]?.sourceSnapshot?.sha256}`).join('|')}`;
  useEffect(() => {
    let current = true;
    if ((!guideOpen && !deliveryOpen) || !native || !workspaceId || !referencesReady) return;
    const host = getFirstCreationPreferencesHost();
    setInputObservationPending(true);
    if (!host?.observeInputs || host.workspaceId !== workspaceId) {
      setInputObservation(null); setInputObservationPending(false); return;
    }
    void host.observeInputs(inputTaskIds).then(raw => {
      if (current) setInputObservation(parseInputObservation(raw, workspaceId));
    }).catch(error => {
      if (current) {
        setInputObservation(null);
        const detail = error instanceof Error ? ` ${error.message}` : '';
        setReferenceError(uiMessage(`输入状态暂时无法核对。重读状态或查看原任务后继续。${detail}`, `Input state could not be checked. Refresh the state or inspect the original task before continuing.${detail}`));
      }
    }).finally(() => { if (current) setInputObservationPending(false); });
    return () => { current = false; };
  }, [guideOpen, deliveryOpen, workspaceId, native, referencesReady, refreshToken, inputRefreshToken, inputTaskKey]);
  useEffect(() => {
    if ((!guideOpen && !deliveryOpen) || !native) return;
    const refreshInputs = () => { setInputObservation(null); setInputRefreshToken(value => value + 1); };
    window.addEventListener('focus', refreshInputs);
    return () => window.removeEventListener('focus', refreshInputs);
  }, [guideOpen, deliveryOpen, native]);

  const openGuide = useCallback((file?: string) => {
    if (!currentBinding || !workspaceId || !bindingMatchesWorkspace(currentBinding, workspaceId, workspaceRoot)) return;
    setGuideWorkspaceFile(file ?? currentBinding?.workspaceFile ?? null);
    setTourActive(true);
    setGuideOpen(true);
  }, [currentBinding, workspaceId, workspaceRoot]);
  const pauseGuide = useCallback(() => setGuideOpen(false), []);
  const closeGuide = useCallback(() => {
    if (workspaceId) dismissedGuideWorkspaces.current.add(workspaceId);
    setGuideOpen(false);
    setTourActive(false);
    if (binding) updateBinding(binding.workspaceFile, value => ({ ...value, showOnOpen: false }));
  }, [binding, updateBinding, workspaceId]);
  const beginCreation = useCallback(() => {
    setGuideOpen(false); setCreationIntent(true); setActiveView('new-workspace');
  }, [setActiveView]);
  const leaveCreation = useCallback(() => setCreationIntent(false), []);
  const refreshReferences = useCallback(() => {
    setReferenceError(null); setInputObservation(null); setRefreshToken(value => value + 1);
    void refreshWorkspaceRef.current().catch(() => setReferenceError(uiMessage('原工作区状态暂时无法读取。请恢复连接后再重读。', 'The original workspace state is temporarily unavailable. Restore the connection, then refresh.')));
  }, []);
  const bindCreatedWorkspace = useCallback((result: CommandResult) => {
    if (!native || result.status !== 'committed' || !result.data?.workspaceFile || !result.data.modId) return;
    const file = result.data.workspaceFile;
    localReferenceGeneration.current++;
    const next = bindingsRef.current.some(value => value.workspaceFile === file) ? bindingsRef.current : [...bindingsRef.current, {
      workspaceFile: file, modId: result.data!.modId!, showOnOpen: true, clients: []
    }];
    bindingsRef.current = next;
    if (localStorageReady) {
      try { window.localStorage.setItem(FIRST_CREATION_STORAGE_KEY, JSON.stringify({ version: 1, bindings: next })); }
      catch { setReferenceError(uiMessage('首作引用无法传到新窗口。请保留创建回执中的工作区路径。', 'First-creation references could not be transferred to the new window. Keep the workspace path from the creation receipt.')); }
    }
    setBindings(next);
    setGuideWorkspaceFile(file);
  }, [native, localStorageReady]);
  const bindElement = useCallback((file: string, elementId: string) => {
    updateBinding(file, value => ({ ...value, elementId }));
  }, [updateBinding]);
  const recordTask = useCallback((result: CommandResult, kind: 'build' | 'client' | 'export', targetWorkspaceId: string, revision: number, output?: string) => {
    if (!native || result.workspaceId !== targetWorkspaceId || !result.task?.id) return;
    const reference: FirstCreationTaskReference = { id: result.task.id, inputRevision: revision, ...(output ? { output } : {}) };
    localReferenceGeneration.current++;
    if (kind === 'export') setDeliveries(previous => ({ ...previous, [targetWorkspaceId]: reference }));
    setBindings(previous => previous.map(value => value.workspaceId !== targetWorkspaceId ? value : kind === 'build'
      ? { ...value, build: reference } : kind === 'export' ? { ...value, export: reference }
        : { ...value, clients: [...value.clients.filter(client => client.id !== reference.id), reference] }));
  }, [native]);
  const openDelivery = useCallback(() => {
    setGuideOpen(false);
    // Let the guide restore its invoker before the next dialog captures focus.
    window.requestAnimationFrame(() => setDeliveryOpen(true));
  }, []);
  const closeDelivery = useCallback(() => setDeliveryOpen(false), []);
  const openWorkspaceOpen = useCallback(() => setWorkspaceOpen(true), []);
  const closeWorkspaceOpen = useCallback(() => setWorkspaceOpen(false), []);

  const value = useMemo<FirstCreationContextValue>(() => ({ guideOpen, openGuide, closeGuide, pauseGuide, tourActive, creationIntent, beginCreation,
    leaveCreation, binding, currentBinding, workspaceRoot, referenceError, referencesReady, refreshReferences, bindCreatedWorkspace, bindElement,
    recordTask, deliveryOpen, openDelivery, closeDelivery, inputObservation, inputObservationPending,
    deliveryReference: workspaceId ? deliveries[workspaceId] ?? currentBinding?.export : undefined,
    workspaceOpen, openWorkspaceOpen, closeWorkspaceOpen, observations, setObservations }), [guideOpen, openGuide, closeGuide, pauseGuide, tourActive, creationIntent, beginCreation,
    leaveCreation, binding, currentBinding, workspaceRoot, referenceError, referencesReady, refreshReferences, bindCreatedWorkspace, bindElement,
    recordTask, deliveryOpen, openDelivery, closeDelivery, workspaceId, deliveries, workspaceOpen, openWorkspaceOpen, closeWorkspaceOpen, observations, inputObservation, inputObservationPending]);
  return <FirstCreationContext.Provider value={value}>{children}</FirstCreationContext.Provider>;
}

export function useFirstCreation() {
  const context = useContext(FirstCreationContext);
  if (!context) throw new Error('useFirstCreation must be used within FirstCreationProvider');
  return context;
}

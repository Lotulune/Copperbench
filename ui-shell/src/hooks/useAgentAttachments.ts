import { useCallback, useEffect, useRef, useState } from 'react';
import { AgentAttachmentError, attachmentErrorMessage, attachmentRequest, parseAttachmentReply, type AgentAttachment } from '../bridge/agentBridge';
import { uiMessage, type UiMessage } from '../i18n';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';

export interface DraftAttachment { key: string; name: string; size: number; state: 'uploading' | 'ready' | 'failed'; item?: AgentAttachment; error?: UiMessage }
const MAX_COUNT = 8, MAX_FILE = 10 * 1024 * 1024, MAX_TOTAL = 20 * 1024 * 1024, CHUNK = 256 * 1024;
const message = attachmentErrorMessage;
let nativeTargetOwner: string | null = null;
let nativeTargetUpdates: Promise<void> = Promise.resolve();
function updateNativeTarget(target: string, host: Parameters<typeof attachmentRequest>[1]) {
  nativeTargetUpdates = nativeTargetUpdates.catch(() => {}).then(async () => {
    await attachmentRequest({ operation: 'attachmentTarget', target }, host);
  });
  return nativeTargetUpdates;
}

/** Draft ownership survives conversation changes; uploads never submit a model request. */
export function useAgentAttachments(workspaceId: string | undefined, draftKey: string, active: boolean) {
  const [drafts, setDrafts] = useState<Record<string, DraftAttachment[]>>({});
  const [error, setError] = useState<UiMessage | null>(null);
  const [selecting, setSelecting] = useState(false);
  const current = useRef(drafts); current.current = drafts;
  const generation = useRef(0);
  const nativeOwner = useRef(safeRandomUUID());
  const transfers = useRef(new Map<string, { cancelled: boolean; id?: string }>());
  const update = useCallback((key: string, change: (files: DraftAttachment[]) => DraftAttachment[]) => {
    setDrafts(previous => { const next = { ...previous, [key]: change(previous[key] ?? []) }; current.current = next; return next; });
  }, []);

  useEffect(() => {
    ++generation.current;
    setDrafts({}); current.current = {}; setError(null); setSelecting(false);
    const host = window.__COPPERBENCH_AGENT_HOST__;
    return () => {
      ++generation.current;
      for (const transfer of transfers.current.values()) transfer.cancelled = true;
      for (const files of Object.values(current.current)) for (const file of files) if (file.item)
        void attachmentRequest({ operation: 'attachmentDiscard', id: file.item.id }, host).catch(() => {});
      transfers.current.clear();
    };
  }, [workspaceId]);

  useEffect(() => {
    const host = window.__COPPERBENCH_AGENT_HOST__;
    if (!host || !workspaceId || !active) return;
    const owner = nativeOwner.current;
    nativeTargetOwner = owner;
    void updateNativeTarget(`${draftKey}:${owner}`, host).catch(failure => { if (nativeTargetOwner === owner) setError(message(failure)); });
    return () => {
      if (nativeTargetOwner !== owner) return;
      nativeTargetOwner = null;
      void updateNativeTarget('', host).catch(() => {});
    };
  }, [active, draftKey, workspaceId]);

  const addFiles = useCallback(async (files: File[]) => {
    const key = draftKey, existing = current.current[key] ?? [], ownGeneration = generation.current;
    const host = window.__COPPERBENCH_AGENT_HOST__;
    if (!workspaceId || !host || !files.length) return;
    setError(null);
    if (existing.length + files.length > MAX_COUNT) { setError(uiMessage('每条消息最多 8 个附件。', 'Attach up to 8 files per message.')); return; }
    if (files.some(file => file.size <= 0 || file.size > MAX_FILE)) { setError(uiMessage('附件须非空且不超过 10 MiB。', 'Each file must be non-empty and no larger than 10 MiB.')); return; }
    if ([...existing, ...files].reduce((total, file) => total + file.size, 0) > MAX_TOTAL) { setError(uiMessage('附件总大小不能超过 20 MiB。', 'Attachments must total 20 MiB or less.')); return; }
    const additions = files.map(file => ({ key: safeRandomUUID(), name: file.name, size: file.size, state: 'uploading' as const }));
    // Reserve the entire batch synchronously so a second paste cannot evade count/size limits.
    current.current = { ...current.current, [key]: [...existing, ...additions] };
    setDrafts(current.current);
    for (const [index, file] of files.entries()) {
      const entry = additions[index], transfer = { cancelled: false, id: undefined as string | undefined };
      transfers.current.set(entry.key, transfer);
      const patch = (value: Partial<DraftAttachment>) => { if (generation.current === ownGeneration && !transfer.cancelled)
        update(key, rows => rows.map(row => row.key === entry.key ? { ...row, ...value } : row)); };
      try {
        if (generation.current !== ownGeneration || !current.current[key]?.some(row => row.key === entry.key)) { transfer.cancelled = true; continue; }
        const begun = await attachmentRequest({ operation: 'attachmentBegin', name: file.name, size: file.size }, host);
        if (!begun.id || begun.offset !== 0) throw new AgentAttachmentError(uiMessage('附件上传未能开始。', 'The attachment upload did not start.'));
        transfer.id = begun.id;
        for (let offset = 0; offset < file.size; offset += CHUNK) {
          if (transfer.cancelled || generation.current !== ownGeneration) throw new Error('cancelled');
          const bytes = new Uint8Array(await file.slice(offset, offset + CHUNK).arrayBuffer());
          let binary = ''; for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
          const result = await attachmentRequest({ operation: 'attachmentChunk', id: begun.id, offset, data: btoa(binary) }, host);
          if (result.offset !== offset + bytes.length) throw new AgentAttachmentError(uiMessage('附件分块未被确认。', 'The attachment chunk was not acknowledged.'));
        }
        if (transfer.cancelled || generation.current !== ownGeneration) throw new Error('cancelled');
        const result = await attachmentRequest({ operation: 'attachmentCommit', id: begun.id }, host);
        if (!result.attachment) throw new AgentAttachmentError(uiMessage('附件未保存完整。', 'The attachment was not saved completely.'));
        patch({ state: 'ready', item: result.attachment });
        if (transfer.cancelled || generation.current !== ownGeneration) await attachmentRequest({ operation: 'attachmentDiscard', id: begun.id }, host);
      } catch (failure) {
        patch({ state: 'failed', error: message(failure) });
        if (transfer.id) await attachmentRequest({ operation: 'attachmentDiscard', id: transfer.id }, host).catch(() => {});
      } finally { transfers.current.delete(entry.key); }
    }
  }, [draftKey, workspaceId, update]);

  const admitSelected = useCallback((key: string, additions: AgentAttachment[]) => {
    const files = current.current[key] ?? [];
    if (files.length + additions.length > MAX_COUNT || [...files, ...additions].reduce((n, item) => n + item.size, 0) > MAX_TOTAL) {
      for (const item of additions) void attachmentRequest({ operation: 'attachmentDiscard', id: item.id }).catch(() => {});
      throw new AgentAttachmentError(uiMessage('每条消息最多 8 个附件，总计不超过 20 MiB。', 'Attach up to 8 files totaling 20 MiB per message.'));
    }
    update(key, previous => [...previous, ...additions.map(item => ({ key: item.id, name: item.name, size: item.size, state: 'ready' as const, item }))]);
    setError(null);
  }, [update]);
  const selectNative = async () => {
    if (selecting || !workspaceId) return;
    const ownGeneration = generation.current, key = draftKey, host = window.__COPPERBENCH_AGENT_HOST__;
    setSelecting(true); setError(null);
    try {
      const result = await attachmentRequest({ operation: 'attachmentSelect' }, host);
      if (generation.current === ownGeneration) admitSelected(key, result.attachments ?? []);
      else for (const item of result.attachments ?? []) void attachmentRequest({ operation: 'attachmentDiscard', id: item.id }, host).catch(() => {});
    } catch (failure) { if (generation.current === ownGeneration) setError(message(failure)); }
    finally { if (generation.current === ownGeneration) setSelecting(false); }
  };

  useEffect(() => {
    const dropped = (event: Event) => {
      try {
        const result = parseAttachmentReply((event as CustomEvent).detail);
        const suffix = `:${nativeOwner.current}`;
        if (!result.target?.startsWith(`${workspaceId}:`) || !result.target.endsWith(suffix) || !result.attachments) return;
        admitSelected(result.target.slice(0, -suffix.length), result.attachments);
      } catch (failure) { setError(message(failure)); }
    };
    window.addEventListener('copperbench:agent-attachments', dropped);
    return () => window.removeEventListener('copperbench:agent-attachments', dropped);
  }, [workspaceId, admitSelected]);

  const remove = (key: string) => {
    const file = current.current[draftKey]?.find(row => row.key === key);
    const transfer = transfers.current.get(key); if (transfer) transfer.cancelled = true;
    update(draftKey, rows => rows.filter(row => row.key !== key));
    if (file?.item) void attachmentRequest({ operation: 'attachmentDiscard', id: file.item.id }).catch(failure => setError(message(failure)));
  };
  const move = (from: string, to: string) => setDrafts(previous => {
    const next = { ...previous, [to]: [...(previous[to] ?? []), ...(previous[from] ?? [])] }; delete next[from]; current.current = next; return next;
  });
  const discardDraft = (key: string) => {
    for (const file of current.current[key] ?? []) {
      const transfer = transfers.current.get(file.key); if (transfer) transfer.cancelled = true;
      if (file.item) void attachmentRequest({ operation: 'attachmentDiscard', id: file.item.id }).catch(failure => setError(message(failure)));
    }
    setDrafts(previous => { const next = { ...previous }; delete next[key]; current.current = next; return next; });
  };
  const accepted = (key: string, ids: string[]) => update(key, files => files.filter(file => !file.item || !ids.includes(file.item.id)));
  const files = drafts[draftKey] ?? [];
  const draftKeys = Object.keys(drafts).filter(key => drafts[key].length);
  return { files, error, addFiles, selectNative, selecting, remove, move, accepted, discardDraft, draftKeys, uploading: selecting || files.some(file => file.state === 'uploading'),
    blocked: files.some(file => file.state !== 'ready'), draftCount: draftKeys.length };
}

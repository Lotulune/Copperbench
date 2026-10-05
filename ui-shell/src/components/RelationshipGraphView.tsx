import React, { useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { coreBridge } from '../bridge';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiText } from '../i18n';
import type { AssetProjection, WorkspaceReferenceProjection } from '../types/contract';
import { buildRelationshipGraph, type RelationshipGraphModel } from '../relations/relationshipGraph';
import { RelationshipGraphCanvas } from './RelationshipGraphCanvas';

export const RelationshipGraphView: React.FC = () => {
  const { state, getModElementEditor, setSelectedElementId, setAssetFocusId, setActiveView } = useWorkbench();
  const workspace = state.workbench?.workspace;
  const workspaceId = workspace?.id;
  const revision = workspace?.revision;
  const [model, setModel] = useState<RelationshipGraphModel | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState<'load' | 'revision' | 'open' | null>(null);
  const currentWorkspace = useRef(workspace);
  currentWorkspace.current = workspace;
  const openRequest = useRef(0);

  useEffect(() => {
    setModel(null);
    if (!workspaceId || revision === undefined) { setStatus('loading'); return; }
    let active = true;
    setStatus('loading');
    setError(null);
    const base = { messageType: 'query' as const, schemaVersion: '1.0' as const, workspaceId, payload: {} };
    // Use the existing Core projections and their envelope revisions. There is
    // no filesystem traversal or frontend reference resolver in this view.
    void Promise.all([
      coreBridge.sendQuery<WorkspaceReferenceProjection>({ ...base, payload: { target: '' }, requestId: safeRandomUUID(), operation: 'get_workspace_references' }),
      coreBridge.sendQuery<AssetProjection>({ ...base, requestId: safeRandomUUID(), operation: 'list_assets' })
    ]).then(([references, assets]) => {
      if (!active || currentWorkspace.current?.id !== workspaceId) return;
      if (references.status !== 'succeeded' || assets.status !== 'succeeded' || !references.data || !assets.data) {
        setError('load'); setStatus('error'); return;
      }
      const snapshotRevision = references.data.revision;
      if (references.revision !== snapshotRevision || assets.revision !== snapshotRevision
        || snapshotRevision < (currentWorkspace.current?.revision ?? revision)) {
        setError('revision'); setStatus('error'); return;
      }
      setModel(buildRelationshipGraph({ workspace: { id: workspaceId, name: currentWorkspace.current!.name },
        references: references.data, assets: assets.data }));
      setStatus('ready');
    }).catch(() => { if (active) { setError('load'); setStatus('error'); } });
    return () => { active = false; };
  }, [workspaceId, revision, refresh]);

  useEffect(() => () => { openRequest.current++; }, []);

  const openElement = async (elementId: string) => {
    if (status !== 'ready' || !workspaceId) return;
    const request = ++openRequest.current;
    try {
      const editor = await getModElementEditor(elementId);
      if (request !== openRequest.current || currentWorkspace.current?.id !== workspaceId) return;
      if (!editor || editor.element.id !== elementId) { setError('open'); return; }
      setSelectedElementId(elementId);
      setActiveView('elements');
    } catch { if (request === openRequest.current) setError('open'); }
  };

  return <section className="relationship-graph-view" data-testid="relationship-graph-view" aria-busy={status === 'loading'}>
    <div className="relationship-data-status">
      <span role={error ? 'alert' : 'status'}>
        {error === 'revision' ? uiText('工作区已更新，请刷新关系图。', 'The workspace changed. Refresh the graph.')
          : error === 'load' ? uiText('无法读取关系图，请重试。', 'Could not load relationships. Please retry.')
            : error === 'open' ? uiText('无法打开该元素，请刷新后重试。', 'Could not open this element. Refresh and retry.')
              : status === 'loading' ? uiText('正在读取关系…', 'Loading relationships…')
                : null}
      </span>
      <button type="button" onClick={() => setRefresh(value => value + 1)} disabled={status === 'loading'}
        aria-label={uiText('刷新关系图', 'Refresh relationships')} title={uiText('刷新关系图', 'Refresh relationships')}
        data-testid="relationship-refresh"><RefreshCw size={15} aria-hidden="true" /></button>
    </div>
    {model && <RelationshipGraphCanvas model={model}
      onOpenElement={elementId => { void openElement(elementId); }}
      onOpenAsset={assetId => { if (status === 'ready') { setAssetFocusId(assetId); setActiveView('assets'); } }} />}
    {!model && <div className="relationship-empty" role="status">
      {status === 'loading' ? uiText('正在加载…', 'Loading…')
        : uiText('请刷新重试。', 'Refresh to retry.')}
    </div>}
  </section>;
};

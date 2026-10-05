import { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, Box, ChevronDown, FileCode2, Hammer, LockKeyhole, Plus, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { sourceBridge } from '../bridge/sourceBridge';
import { elementLabel, elementShortLabel, valueLabel } from '../i18n/labels';
import { englishCount, t, uiText } from '../i18n';
import type { WorkspaceSourceIndex } from '../types/contract';
import { BlockbenchOnboarding } from './BlockbenchOnboarding';
import './workspaceHub.css';

export const WorkspaceHub = () => {
  const { state, workspaceHealth, setActiveView, setSelectedElementId, setIsCreateModalOpen,
    buildWorkspace, cancelTask, setIsTaskDrawerOpen, runDiagnosticAction, openSource } = useWorkbench();
  const workspace = state.workbench?.workspace;
  const counts = state.workbench?.elementCounts;
  const recentElements = state.workbench?.recentElements ?? [];
  const activeTasks = state.workbench?.activeTasks ?? [];
  const activeTask = activeTasks[0];
  const failedTask = Object.values(state.tasks).find(task => task.state === 'failed');
  const topLevelDiagnostics = state.diagnostics.filter(diagnostic => !diagnostic.elementId && !diagnostic.path);
  const [sourceIndex, setSourceIndex] = useState<WorkspaceSourceIndex | null>(null);
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [sourceRefresh, setSourceRefresh] = useState(0);
  const [healthOpen, setHealthOpen] = useState(false);
  useEffect(() => {
    let current = true;
    setSourceIndex(null); setSourceError(null);
    if (!workspace) return;
    void sourceBridge.index(workspace.id).then(result => {
      if (!current) return;
      if (result.revision !== workspace.revision) setSourceError(uiText('工作区已更新，请重试。', 'The workspace changed. Please retry.'));
      else setSourceIndex(result.data);
    }).catch(error => { if (current) setSourceError(error instanceof Error ? error.message : uiText('读取失败', 'Could not load')); });
    return () => { current = false; };
  }, [workspace?.id, workspace?.revision, sourceRefresh]);
  const sourceEntries = sourceIndex?.entries.slice(0, 6) ?? [];
  const assetCount = workspaceHealth?.assets.indexed ? workspaceHealth.assets.summary?.totalAssets : undefined;

  if (state.viewportState === 'loading') return <div className="workspace-overview-loading" data-testid="workbench-loading" role="status">
    {uiText('正在加载工作区…', 'Loading workspace…')}
  </div>;

  return <div className="workspace-overview" data-testid="workbench-main">
    <div className="workspace-overview-page">
      <header className="overview-heading">
        <div><h1>{workspace?.name || uiText('工作区', 'Workspace')}</h1>
          <div className="overview-project-line">
            <button type="button" data-testid="hub-tracks-badge" onClick={() => setActiveView('tracks')}>
              {workspace?.generator?.displayName || uiText('查看版本', 'View version')}
            </button>
            {workspace?.lock.state !== 'write_available' && <span className="overview-locked"><LockKeyhole size={12} aria-hidden="true" />{uiText('已锁定', 'Locked')}</span>}
          </div>
        </div>
        <div className="overview-heading-actions">
          <button type="button" className="overview-action" data-testid="hub-build-btn" onClick={() => buildWorkspace()}><Hammer size={14} aria-hidden="true" />{uiText('构建', 'Build')}</button>
          <button type="button" className="overview-action overview-action-primary" data-testid="empty-primary-action" onClick={() => setIsCreateModalOpen(true)}><Plus size={14} aria-hidden="true" />{uiText('新建元素', 'New element')}</button>
        </div>
      </header>
      <div className="overview-counts" aria-label={uiText('工作区内容', 'Workspace contents')}>
        <span>{uiText('模组元素', 'Mod elements')} <strong>{counts?.total ?? 0}</strong></span>
        {assetCount !== undefined && <button type="button" onClick={() => setActiveView('assets')}>{uiText('资产', 'Assets')} <strong>{assetCount}</strong></button>}
        {!!counts?.draft && <span>{uiText('草稿', 'Drafts')} <strong>{counts.draft}</strong></span>}
        {(workspaceHealth?.diagnostics.error ?? 0) > 0 && <button type="button" onClick={() => { setHealthOpen(true); document.getElementById('workspace-health-panel')?.scrollIntoView({ block: 'nearest' }); }}>
          <span>{uiText('错误诊断', 'Errors')}</span> <strong>{workspaceHealth?.diagnostics.error}</strong></button>}
      </div>

      {(topLevelDiagnostics.length > 0 || failedTask) && <section className="overview-alerts" role="alert" data-testid="global-diagnostics-banner">
        {failedTask && <div className="overview-alert-row" data-testid="task-failure"><AlertTriangle size={15} aria-hidden="true" />
          <span>{valueLabel(failedTask.kind)} {uiText('失败', 'failed')} · {t(failedTask.stage)}</span>
          <button type="button" className="overview-link" data-testid="open-failed-task-logs-btn" onClick={() => setIsTaskDrawerOpen(true)}>{uiText('查看任务日志', 'View task logs')}<ArrowRight size={13} aria-hidden="true" /></button>
        </div>}
        {topLevelDiagnostics.map((diagnostic, index) => <div className="overview-alert-row" key={`${diagnostic.code}-${index}`}><AlertTriangle size={15} aria-hidden="true" />
          <span>{t(diagnostic.message)}</span><div className="overview-inline-actions">{diagnostic.actions.map(action => <button type="button" className="overview-link" key={action.id}
            data-testid={`diag-action-${action.id}`} onClick={() => runDiagnosticAction(action, diagnostic)}>{t(action.label)}</button>)}</div>
        </div>)}
      </section>}
      {activeTask && <div className="overview-task" data-task-id={activeTask.id}>
        <Hammer size={15} aria-hidden="true" /><span>{t(activeTask.stage)}</span>
        <progress max="1" value={activeTask.progress ?? 0} aria-label={uiText('任务进度', 'Task progress')} />
        <button type="button" className="overview-link" onClick={() => setIsTaskDrawerOpen(true)}>{uiText('查看日志', 'View logs')}</button>
        {activeTask.cancellable && <button type="button" className="overview-task-cancel" onClick={() => cancelTask(activeTask.id)} aria-label={uiText('取消任务', 'Cancel task')}><X size={14} aria-hidden="true" /></button>}
      </div>}

      <section className="overview-section" aria-labelledby="overview-recent-title">
        <div className="overview-section-heading"><h2 id="overview-recent-title">{uiText('近期元素', 'Recent elements')}</h2>
          <button type="button" className="overview-link" onClick={() => setActiveView('elements')}>{uiText('全部元素', 'All elements')}<ArrowRight size={13} aria-hidden="true" /></button></div>
        {recentElements.length ? <div className="overview-recent-list">{recentElements.slice(0, 6).map(element => <button type="button" className="overview-recent-item" key={element.id} data-element-id={element.id}
          onClick={() => { setSelectedElementId(element.id); setActiveView('elements'); }}>
          <Box size={18} aria-hidden="true" /><span className="overview-item-copy"><strong>{element.displayName}</strong><code>{element.name}</code></span>
          <span className="overview-element-type" title={elementLabel(element.type)}>{elementShortLabel(element.type)}</span>
          {element.state !== 'valid' && <span className={`overview-element-state state-${element.state}`}>{valueLabel(element.state)}</span>}
          <ArrowRight size={14} aria-hidden="true" />
        </button>)}</div> : <div className="overview-empty-line" data-testid="hub-elements-empty"><span>{uiText('暂无模组元素', 'No mod elements yet')}</span>
          <button type="button" className="overview-link" onClick={() => setIsCreateModalOpen(true)}>{uiText('创建一个', 'Create one')}<Plus size={13} aria-hidden="true" /></button></div>}
      </section>

      <section className="overview-section" aria-labelledby="overview-source-title" data-testid="hub-source-entries">
        <div className="overview-section-heading"><h2 id="overview-source-title">{uiText('源码入口', 'Source entry points')}</h2>
          <button type="button" className="overview-link" onClick={() => setActiveView('source')}>{uiText('浏览源码', 'Browse source')}<ArrowRight size={13} aria-hidden="true" /></button></div>
        {sourceEntries.length ? <div className="overview-source-list">{sourceEntries.map(entry => <button type="button" className="overview-source-item" key={entry.id}
          data-source-path={entry.relativePath} onClick={() => openSource(entry.relativePath, entry.line)} title={`${entry.relativePath}:${entry.line}`}>
          <FileCode2 size={18} aria-hidden="true" /><span className="overview-item-copy"><strong>{entry.symbol}</strong><code>{entry.relativePath}:{entry.line}</code></span>
          <ArrowRight size={14} aria-hidden="true" />
        </button>)}</div> : <div className="overview-empty-line" role="status"><span>{sourceError ? uiText('源码入口加载失败', 'Could not load source entries') : sourceIndex ? uiText('暂无已识别的入口', 'No identified entry points') : uiText('正在读取…', 'Loading…')}</span>
          {sourceError && <button type="button" className="overview-link" onClick={() => setSourceRefresh(value => value + 1)}>{uiText('重试', 'Retry')}</button>}</div>}
        {sourceError && <details className="overview-error-detail"><summary>{uiText('错误详情', 'Error details')}</summary><p>{sourceError}</p></details>}
      </section>

      {workspaceHealth && <section className="overview-health" data-testid="workspace-health-panel" id="workspace-health-panel" tabIndex={-1} role="region" aria-label={uiText('项目健康', 'Workspace health')}>
        <details open={healthOpen} onToggle={event => setHealthOpen(event.currentTarget.open)}>
          <summary className="overview-health-summary"><span>{uiText('项目健康', 'Workspace health')}</span>
            <span data-testid="workspace-health-diagnostics"><span className="overview-health-diagnostic-label">{uiText('诊断', 'Diagnostics')} </span>{uiText(`${workspaceHealth.diagnostics.total} 条 · ${workspaceHealth.diagnostics.error} 错误`, `${workspaceHealth.diagnostics.total} total · ${englishCount(workspaceHealth.diagnostics.error, 'error')}`)}</span><ChevronDown size={14} aria-hidden="true" /></summary>
          <div className="overview-health-content">
            <div className="overview-health-facts">{[
              { id: 'elements', label: uiText('元素状态', 'Elements'), value: uiText(`${workspaceHealth.elements.invalid} 无效 · ${workspaceHealth.elements.draft} 草稿`, `${workspaceHealth.elements.invalid} invalid · ${englishCount(workspaceHealth.elements.draft, 'draft')}`), action: () => setActiveView('elements') },
              { id: 'references', label: uiText('结构化引用', 'References'), value: uiText(`${workspaceHealth.references.danglingCount} 个断引用`, englishCount(workspaceHealth.references.danglingCount, 'broken reference')), action: () => setActiveView('relations') },
              { id: 'assets', label: uiText('资产', 'Assets'), value: workspaceHealth.assets.indexed && workspaceHealth.assets.summary ? uiText(`${workspaceHealth.assets.summary.missingReferences} 缺失 · ${workspaceHealth.assets.summary.unusedAssets} 未使用`, `${workspaceHealth.assets.summary.missingReferences} missing · ${workspaceHealth.assets.summary.unusedAssets} unused`) : uiText('尚未检查', 'Not checked'), action: () => setActiveView('assets') },
              { id: 'generator', label: uiText('生成器', 'Generator'), value: `${valueLabel(workspaceHealth.generator.status)} · ${workspaceHealth.generator.generatable ? uiText('可生成', 'Can generate') : uiText('不可生成', 'Cannot generate')}`, action: () => setActiveView('tracks') },
              { id: 'tasks', label: uiText('会话任务', 'Session tasks'), value: uiText(`${workspaceHealth.tasks.activeCount} 运行中 · ${workspaceHealth.tasks.recentFailed.length} 最近失败`, `${workspaceHealth.tasks.activeCount} active · ${englishCount(workspaceHealth.tasks.recentFailed.length, 'recent failure')}`), action: () => setIsTaskDrawerOpen(true) },
              { id: 'recovery', label: uiText('本地恢复', 'Recovery'), value: workspaceHealth.recovery.available ? uiText(`${workspaceHealth.recovery.recoveryPointCount} 个恢复点`, englishCount(workspaceHealth.recovery.recoveryPointCount, 'recovery point')) : uiText('不可用', 'Unavailable'), action: () => setActiveView('history') }
            ].map(item => <button type="button" key={item.id} data-testid={`workspace-health-${item.id}`} onClick={item.action}><span>{item.label}</span><strong>{item.value}</strong><ArrowRight size={12} aria-hidden="true" /></button>)}</div>
            <button type="button" className="overview-health-risk" data-testid="workspace-health-risk" onClick={() => setActiveView('tracks')}>{uiText('版本与变更', 'Versions & changes')}<span>{uiText(`${workspaceHealth.risk.loaderMigration.availableTargetCount} 个迁移目标 · ${workspaceHealth.risk.aiBatchChanges.highImpactOperationThreshold}+ 操作标记高影响`, `${englishCount(workspaceHealth.risk.loaderMigration.availableTargetCount, 'migration target')} · ${workspaceHealth.risk.aiBatchChanges.highImpactOperationThreshold}+ operations flagged as high impact`)}</span><ArrowRight size={12} aria-hidden="true" /></button>
            {!!workspaceHealth.diagnostics.items?.length && <ul className="overview-diagnostic-list">{workspaceHealth.diagnostics.items.map((diagnostic, index) => <li key={`${diagnostic.code}-${index}`}><span>{t(diagnostic.message)}</span>
              <div className="overview-inline-actions">{diagnostic.actions.map(action => <button type="button" className="overview-link" key={`${action.id}-${action.target}`} onClick={() => runDiagnosticAction(action, diagnostic)}>{t(action.label)}</button>)}</div>
            </li>)}</ul>}
          </div>
        </details>
        {workspaceHealth.diagnostics.collectionState === 'partial' && <p className="overview-health-partial" role="status">{uiText('部分检查尚未完成。', 'Some checks are incomplete.')}</p>}
      </section>}
      <BlockbenchOnboarding />
    </div>
  </div>;
};

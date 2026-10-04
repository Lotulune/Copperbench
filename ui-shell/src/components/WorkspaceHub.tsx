import { elementLabel, elementShortLabel, valueLabel } from '../i18n/labels';
import React from 'react';
import {
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  FileEdit,
  Hammer,
  Plus,
  Box,
  Compass,
  ArrowRight,
  Lock,
  Clock,
  Sparkles,
  Palette,
  GitBranch,
  FileCode2,
  Terminal
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { TaskSummary } from '../types/contract';
import { t, uiText, englishCount } from '../i18n';
import { BlockbenchOnboarding } from './BlockbenchOnboarding';

export const WorkspaceHub: React.FC = () => {
  const {
    state,
    setActiveView,
    setSelectedElementId,
    setIsCreateModalOpen,
    buildWorkspace,
    cancelTask,
    setIsTaskDrawerOpen,
    runDiagnosticAction,
    workspaceHealth
  } = useWorkbench();


  const workspace = state.workbench?.workspace;
  const elementCounts = state.workbench?.elementCounts ?? { total: 0, valid: 0, draft: 0, invalid: 0, unsupported: 0 };
  const recentElements = state.workbench?.recentElements ?? [];
  const activeTasks = state.workbench?.activeTasks ?? [];

  // Top-level operational diagnostics (permission denials, external process
  // exits). Element- and field-scoped diagnostics stay in the inspector.
  const topLevelDiagnostics = state.diagnostics.filter((d) => !d.elementId && !d.path);
  const failedTask: TaskSummary | null =
    Object.values(state.tasks).find((t) => t.state === 'failed') ?? null;


  if (state.viewportState === 'loading') {
    return (
      <div
        data-testid="workbench-loading"
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          gap: '16px',
          color: 'var(--text-muted)'
        }}
      >
        <div style={{ animation: 'pulseGlow 1.5s infinite', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '10px' }}>
          <Sparkles size={36} color="var(--accent-copper)" />
          <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-main)' }}>
            {uiText('正在加载工作区…', 'Loading workspace…')}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className="workspace-hub animate-fade-in"
      data-testid="workbench-main"
    >
      <BlockbenchOnboarding />

      {/* Top-level operational diagnostics banner (permission denials, process exits) */}
      {(topLevelDiagnostics.length > 0 || failedTask) && (
        <div
          role="alert"
          data-testid="global-diagnostics-banner"
          style={{
            background: 'var(--badge-red-bg)',
            border: '1px solid rgba(248, 81, 73, 0.4)',
            borderRadius: 'var(--radius-md)',
            padding: '14px 18px',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px'
          }}
        >
          {failedTask && (
            <div
              data-testid="task-failure"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '12px',
                borderBottom: topLevelDiagnostics.length > 0 ? '1px solid rgba(248, 81, 73, 0.25)' : 'none',
                paddingBottom: topLevelDiagnostics.length > 0 ? '10px' : 0
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-red)', fontSize: '12px', fontWeight: 600 }}>
                <AlertTriangle size={15} />
                <span>
                  {valueLabel(failedTask.kind)} {uiText('任务失败', 'task failed')} — {t(failedTask.stage)}
                </span>
              </div>
              <button
                className="btn-secondary"
                style={{ fontSize: '11px', padding: '4px 10px', flexShrink: 0 }}
                onClick={() => {
                  setIsTaskDrawerOpen(true);
                }}
                data-testid="open-failed-task-logs-btn"
              >
                {uiText('查看任务日志', 'View task logs')}
              </button>
            </div>
          )}

          {topLevelDiagnostics.map((diagnostic) => (
            <div
              key={diagnostic.code}
              style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                <AlertTriangle size={14} color="var(--badge-red)" style={{ flexShrink: 0, marginTop: '2px' }} />
                <div style={{ fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
                  {t(diagnostic.message)}
                  {diagnostic.message.args?.failureId != null && (
                    <code style={{ marginLeft: '8px', fontSize: '10px', color: 'var(--text-sub)' }}>
                      {uiText('错误编号：', 'Error ID: ')}{String(diagnostic.message.args.failureId)}
                    </code>
                  )}
                </div>
              </div>
              {diagnostic.actions.length > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', paddingLeft: '22px' }}>
                  {diagnostic.actions.map((action) => (
                    <button
                      key={action.id}
                      className="btn-primary"
                      style={{ fontSize: '11px', padding: '4px 10px' }}
                      onClick={() => runDiagnosticAction(action, diagnostic)}
                      data-testid={`diag-action-${action.id}`}
                    >
                      {t(action.label)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <header className="hub-header">
        <div className="hub-heading">
          <div className="hub-eyebrow">{uiText('工作区总览', 'WORKSPACE OVERVIEW')}</div>
          <h1>{workspace?.name || t({ key: 'workspace.default_name', fallback: 'Minecraft Mod Workspace' })}</h1>
          <div className="hub-workspace-meta">
            <button type="button" className="hub-generator" data-testid="hub-tracks-badge"
              onClick={() => setActiveView('tracks')} title={uiText('查看版本与迁移', 'View version tracks')}>
              <Box size={14} aria-hidden="true" />
              {workspace?.generator?.displayName || uiText('生成器不可用', 'Generator unavailable')}
              <ArrowRight size={12} aria-hidden="true" />
            </button>
            <span className="hub-lock"><Lock size={13} aria-hidden="true" />
              {workspace?.lock.state === 'write_available' ? uiText('可编辑', 'Editable') : uiText('已锁定', 'Locked')}
            </span>
            <span>{uiText('修订', 'Revision')} {workspace?.revision ?? 0}</span>
          </div>
        </div>
        <div className="hub-header-actions">
          <button className="btn-secondary" onClick={() => buildWorkspace()} data-testid="hub-build-btn">
            <Hammer size={16} aria-hidden="true" />{uiText('构建模组', 'Build mod')}
          </button>
          <button className="btn-primary" onClick={() => setIsCreateModalOpen(true)} data-testid="empty-primary-action">
            <Plus size={16} aria-hidden="true" />{uiText('新建元素', 'New element')}
          </button>
        </div>
      </header>

      {/* Active Tasks Widget (If any) */}
      {activeTasks.length > 0 && (
        <div
          style={{
            background: 'var(--bg-panel)',
            border: '1px solid rgba(200, 122, 62, 0.3)',
            borderRadius: 'var(--radius-md)',
            padding: '14px 18px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
          data-task-id={activeTasks[0].id}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1 }}>
            <Hammer size={18} color="var(--accent-copper)" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontWeight: 600, fontSize: '13px', color: 'var(--text-main)' }}>
                  {t({
                    key: 'task.kind_label',
                    fallback: 'Task: {kind}',
                    args: { kind: valueLabel(activeTasks[0].kind) }
                  })}
                </span>
                <span className="badge badge-amber" style={{ fontSize: '10px' }}>
                  {t(activeTasks[0].stage)}
                </span>
              </div>

              {/* Progress bar */}
              <div style={{ width: '80%', height: '5px', background: 'var(--bg-input)', borderRadius: '3px', overflow: 'hidden' }}>
                <div
                  style={{
                    height: '100%',
                    width: `${Math.round((activeTasks[0].progress || 0) * 100)}%`,
                    background: 'var(--accent-copper)',
                    transition: 'width 0.3s ease'
                  }}
                />
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              className="btn-secondary"
              style={{ fontSize: '11px', padding: '4px 10px' }}
              onClick={() => setIsTaskDrawerOpen(true)}
            >
              {uiText('打开控制台日志', 'Open console logs')}
            </button>
            {activeTasks[0].cancellable && (
              <button
                className="btn-danger"
                style={{ fontSize: '11px', padding: '4px 10px' }}
                onClick={() => cancelTask(activeTasks[0].id)}
              >
                {uiText('取消任务', 'Cancel task')}
              </button>
            )}
          </div>
        </div>
      )}

      <div className="hub-metrics">
        {[
          { label: uiText('元素总数', 'Total elements'), value: elementCounts.total, icon: Box, tone: 'copper' },
          { label: uiText('有效就绪', 'Ready to use'), value: elementCounts.valid, icon: CheckCircle2, tone: 'green' },
          { label: uiText('草稿', 'Drafts'), value: elementCounts.draft, icon: FileEdit, tone: 'amber' },
          { label: uiText('错误诊断', 'Errors'), value: workspaceHealth ? workspaceHealth.diagnostics.error : '—', icon: AlertCircle,
            tone: (workspaceHealth?.diagnostics.error ?? 0) > 0 ? 'red' : 'neutral' }
        ].map(metric => <div className="hub-metric" key={metric.label}>
          <div><span>{metric.label}</span><strong>{metric.value}</strong></div>
          <span className={`hub-metric-icon tone-${metric.tone}`}><metric.icon size={21} aria-hidden="true" /></span>
        </div>)}
      </div>

      <div className="hub-columns">
        <section className="hub-panel hub-recent" aria-labelledby="recent-elements-title">
          <div className="hub-panel-heading">
            <h2 id="recent-elements-title"><Clock size={17} aria-hidden="true" />{uiText('近期元素', 'Recent elements')}</h2>
            <button className="hub-text-button" onClick={() => setActiveView('elements')}>
              {uiText('查看全部元素', 'View all elements')}<ArrowRight size={14} aria-hidden="true" />
            </button>
          </div>
          {recentElements.length === 0 ? (
            <div className="hub-empty">
              <span className="hub-empty-icon"><Box size={30} aria-hidden="true" /></span>
              <h3>{uiText('从第一个元素开始', 'Start with your first element')}</h3>
              <p>{uiText('创建方块、物品或配方，开始构建你的模组。', 'Create a block, item or recipe to start your mod.')}</p>
              <button className="btn-secondary" onClick={() => setIsCreateModalOpen(true)}>
                <Plus size={15} aria-hidden="true" />{uiText('新建元素', 'New element')}
              </button>
            </div>
          ) : (
            <div className="hub-recent-list">
              {recentElements.map(elem => (
                <button type="button" className="hub-recent-item" key={elem.id} data-element-id={elem.id}
                  onClick={() => { setSelectedElementId(elem.id); setActiveView('elements'); }}>
                  <span className={`hub-element-icon ${elem.type === 'block' ? 'tone-copper' : 'tone-blue'}`}>
                    {elem.type === 'block' ? <Box size={20} aria-hidden="true" />
                      : elem.type === 'function' ? <FileCode2 size={20} aria-hidden="true" />
                      : elem.type === 'procedure' ? <Terminal size={20} aria-hidden="true" />
                      : <Compass size={20} aria-hidden="true" />}
                  </span>
                  <span className="hub-element-copy"><strong>{elem.displayName}</strong><span>{elem.name}</span></span>
                  <span className="hub-element-type" title={elementLabel(elem.type)}>{elementShortLabel(elem.type)}</span>
                  <span className={`badge badge-${elem.state === 'valid' ? 'green' : elem.state === 'draft' ? 'amber' : 'red'}`}>
                    {valueLabel(elem.state)}
                  </span>
                  <ArrowRight className="hub-row-arrow" size={15} aria-hidden="true" />
                </button>
              ))}
            </div>
          )}
          <div className="hub-shortcuts">
            <button onClick={() => setActiveView('assets')}><Palette size={18} aria-hidden="true" />
              <span>{uiText('管理资产', 'Manage assets')}</span><ArrowRight size={14} aria-hidden="true" />
            </button>
            <button onClick={() => setActiveView('history')}><GitBranch size={18} aria-hidden="true" />
              <span>{uiText('查看历史', 'Local history')}</span><ArrowRight size={14} aria-hidden="true" />
            </button>
          </div>
        </section>

        {workspaceHealth && (
          <section className="hub-panel hub-health" data-testid="workspace-health-panel" id="workspace-health-panel"
            tabIndex={-1} role="region" aria-label={uiText('项目健康', 'Workspace health')}>
            <div className="hub-panel-heading">
              <h2><CheckCircle2 size={17} aria-hidden="true" />{uiText('项目健康', 'Workspace health')}</h2>
              <span className="hub-revision">{uiText('修订', 'Rev.')} {workspaceHealth.revision}</span>
            </div>
            <div className="workspace-health-grid">
              <div className="hub-health-row" data-testid="workspace-health-diagnostics">
                <span>{uiText('诊断', 'Diagnostics')}</span>
                <strong className={workspaceHealth.diagnostics.error > 0 ? 'health-error' : ''}>
                  {uiText(`${workspaceHealth.diagnostics.total} 条 · ${workspaceHealth.diagnostics.error} 错误`,
                    `${workspaceHealth.diagnostics.total} total · ${englishCount(workspaceHealth.diagnostics.error, 'error')}`)}
                </strong>
              </div>
              {[
                { id: 'elements', label: uiText('元素状态', 'Elements'),
                  value: uiText(`${workspaceHealth.elements.invalid} 无效 · ${workspaceHealth.elements.draft} 草稿`,
                    `${workspaceHealth.elements.invalid} invalid · ${englishCount(workspaceHealth.elements.draft, 'draft')}`),
                  action: () => setActiveView('elements') },
                { id: 'references', label: uiText('结构化引用', 'References'),
                  value: uiText(`${workspaceHealth.references.danglingCount} 个断引用`, englishCount(workspaceHealth.references.danglingCount, 'broken reference')),
                  action: () => setActiveView('data') },
                { id: 'assets', label: uiText('资产', 'Assets'),
                  value: workspaceHealth.assets.indexed && workspaceHealth.assets.summary
                    ? uiText(`${workspaceHealth.assets.summary.missingReferences} 缺失 · ${workspaceHealth.assets.summary.unusedAssets} 未使用`,
                      `${workspaceHealth.assets.summary.missingReferences} missing · ${workspaceHealth.assets.summary.unusedAssets} unused`)
                    : workspaceHealth.assets.reasonCode ?? uiText('未建立索引', 'Not indexed'),
                  action: () => setActiveView('assets') },
                { id: 'generator', label: uiText('生成器', 'Generator'),
                  value: `${valueLabel(workspaceHealth.generator.status)} · ${workspaceHealth.generator.generatable ? uiText('可生成', 'Can generate') : uiText('不可生成', 'Cannot generate')}`,
                  action: () => setActiveView('tracks') },
                { id: 'tasks', label: uiText('会话任务', 'Session tasks'),
                  value: uiText(`${workspaceHealth.tasks.activeCount} 运行中 · ${workspaceHealth.tasks.recentFailed.length} 最近失败`,
                    `${workspaceHealth.tasks.activeCount} active · ${englishCount(workspaceHealth.tasks.recentFailed.length, 'recent failure')}`),
                  action: () => setIsTaskDrawerOpen(true) },
                { id: 'recovery', label: uiText('本地恢复', 'Recovery'),
                  value: workspaceHealth.recovery.available
                    ? uiText(`${workspaceHealth.recovery.recoveryPointCount} 个恢复点`, englishCount(workspaceHealth.recovery.recoveryPointCount, 'recovery point'))
                    : workspaceHealth.recovery.reasonCode ?? uiText('不可用', 'Unavailable'),
                  action: () => setActiveView('history') }
              ].map(item => <button type="button" className="hub-health-row" key={item.id}
                data-testid={`workspace-health-${item.id}`} onClick={item.action}>
                <span>{item.label}</span><strong>{item.value}</strong><ArrowRight size={13} aria-hidden="true" />
              </button>)}
            </div>
            <button type="button" className="hub-risk-note" data-testid="workspace-health-risk" onClick={() => setActiveView('tracks')}>
              <AlertTriangle size={15} aria-hidden="true" />
              <span><strong>{uiText('高风险变更', 'High-impact changes')}</strong>
                <span>{uiText(`${workspaceHealth.risk.loaderMigration.availableTargetCount} 个迁移目标 · ${workspaceHealth.risk.aiBatchChanges.highImpactOperationThreshold}+ 操作标记高影响`,
                  `${englishCount(workspaceHealth.risk.loaderMigration.availableTargetCount, 'migration target')} · ${workspaceHealth.risk.aiBatchChanges.highImpactOperationThreshold}+ operations flagged as high impact`)}</span>
              </span>
              <ArrowRight size={13} aria-hidden="true" />
            </button>
            {workspaceHealth.diagnostics.collectionState === 'partial' && <p className="hub-health-note" role="status">
              {uiText('部分检查尚未完成，当前数量仅包含已采集的诊断。', 'Some checks are incomplete. Counts include only collected diagnostics.')}
            </p>}
            {!!workspaceHealth.diagnostics.items?.length && <details className="hub-diagnostic-details">
              <summary>{uiText(`查看当前工作区诊断（${workspaceHealth.diagnostics.total} 条）`, `View current workspace diagnostics (${workspaceHealth.diagnostics.total})`)}</summary>
              <ul>{workspaceHealth.diagnostics.items.map((diagnostic, index) => <li key={`${diagnostic.code}-${index}`}>
                {t(diagnostic.message)}{' '}
                {diagnostic.actions.map(action => <button type="button" className="hub-text-button" key={`${action.kind}-${action.target}`}
                  onClick={() => runDiagnosticAction(action, diagnostic)}>{t(action.label)}</button>)}
              </li>)}</ul>
            </details>}
          </section>
        )}
      </div>
    </div>
  );
};

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
  Cpu,
  Clock,
  Sparkles
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { TaskSummary } from '../types/contract';
import { t } from '../i18n';

export const WorkspaceHub: React.FC = () => {
  const {
    state,
    setActiveView,
    setSelectedElementId,
    setIsCreateModalOpen,
    buildWorkspace,
    cancelTask,
    setIsTaskDrawerOpen,
    runDiagnosticAction
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
            正在加载工作区投影…
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
            正在协商 UI-Core 协议 v1.0
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className="workspace-hub animate-fade-in"
      data-testid="workbench-main"
      style={{
        flex: 1,
        padding: '24px',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: '24px'
      }}
    >
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
                  {failedTask.kind.toUpperCase()} 任务失败 — {t(failedTask.stage)}
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
                查看任务日志
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
                      错误编号：{String(diagnostic.message.args.failureId)}
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

      {/* Top Banner / Workspace Overview Card */}
      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-lg)',
          padding: '20px 24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          boxShadow: 'var(--shadow-sm)'
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <h1 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-main)' }}>
              {workspace?.name || t({ key: 'workspace.default_name', fallback: 'Minecraft Mod Workspace' })}
            </h1>
            <span className="badge badge-copper">修订 {workspace?.revision ?? 0}</span>
            <button
              type="button"
              className="badge badge-blue"
              data-testid="hub-tracks-badge"
              onClick={() => setActiveView('tracks')}
              style={{ cursor: 'pointer', border: '1px solid rgba(88, 166, 255, 0.3)' }}
              title="查看版本轨道与迁移矩阵"
            >
              {workspace?.generator?.displayName || '生成器信息不可用'}
            </button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '12px', color: 'var(--text-muted)' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Lock size={13} color="var(--badge-green)" />
              <span>写入锁：{workspace?.lock.state === 'write_available' ? '可用（本机可写）' : '已锁定'}</span>
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Cpu size={13} />
              <span>兼容模式：{workspace?.compatibility.mode.toUpperCase()}（未知数据保留）</span>
            </span>
          </div>
        </div>

        {/* Quick Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            className="btn-primary"
            onClick={() => setIsCreateModalOpen(true)}
            data-testid="empty-primary-action"
          >
            <Plus size={14} />
            <span>新建元素</span>
          </button>

          <button
            className="btn-secondary"
            onClick={() => buildWorkspace()}
            data-testid="hub-build-btn"
          >
            <Hammer size={14} />
            <span>构建模组</span>
          </button>
        </div>
      </div>

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
                    args: { kind: activeTasks[0].kind.toUpperCase() }
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
              打开控制台日志
            </button>
            {activeTasks[0].cancellable && (
              <button
                className="btn-danger"
                style={{ fontSize: '11px', padding: '4px 10px' }}
                onClick={() => cancelTask(activeTasks[0].id)}
              >
                取消任务
              </button>
            )}
          </div>
        </div>
      )}

      {/* Health & Element Counts Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px' }}>
        <div
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontWeight: 600, textTransform: 'uppercase' }}>
              元素总数
            </div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--text-main)', marginTop: '4px' }}>
              {elementCounts.total}
            </div>
          </div>
          <Box size={28} color="var(--accent-copper)" />
        </div>

        <div
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontWeight: 600, textTransform: 'uppercase' }}>
              有效就绪
            </div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--badge-green)', marginTop: '4px' }}>
              {elementCounts.valid}
            </div>
          </div>
          <CheckCircle2 size={28} color="var(--badge-green)" />
        </div>

        <div
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontWeight: 600, textTransform: 'uppercase' }}>
              草稿进行中
            </div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--badge-amber)', marginTop: '4px' }}>
              {elementCounts.draft}
            </div>
          </div>
          <FileEdit size={28} color="var(--badge-amber)" />
        </div>

        <div
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontWeight: 600, textTransform: 'uppercase' }}>
              诊断 / 错误
            </div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: elementCounts.invalid > 0 ? 'var(--badge-red)' : 'var(--text-sub)', marginTop: '4px' }}>
              {elementCounts.invalid}
            </div>
          </div>
          <AlertCircle size={28} color={elementCounts.invalid > 0 ? 'var(--badge-red)' : 'var(--text-sub)'} />
        </div>
      </div>

      {/* Recent Elements Queue Section */}
      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-lg)',
          padding: '20px',
          display: 'flex',
          flexDirection: 'column',
          gap: '14px'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Clock size={16} color="var(--accent-copper)" />
            <h2 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-main)' }}>
              近期元素
            </h2>
          </div>

          <button
            className="btn-secondary"
            style={{ fontSize: '11px', padding: '4px 10px' }}
            onClick={() => setActiveView('elements')}
          >
            <span>查看全部元素</span>
            <ArrowRight size={12} />
          </button>
        </div>

        {recentElements.length === 0 ? (
          <div
            style={{
              padding: '32px 16px',
              textAlign: 'center',
              color: 'var(--text-muted)',
              fontSize: '13px'
            }}
          >
            此工作区还没有模组元素。点击<strong>新建元素</strong>创建你的第一个方块或物品！
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {recentElements.map((elem) => (
              <button
                type="button"
                key={elem.id}
                data-element-id={elem.id}
                onClick={() => {
                  setSelectedElementId(elem.id);
                  setActiveView('elements');
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 16px',
                  background: 'var(--bg-panel)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-md)',
                  cursor: 'pointer',
                  width: '100%',
                  textAlign: 'left',
                  transition: 'all 0.15s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'var(--border-focus)';
                  e.currentTarget.style.background = 'var(--bg-hover)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'var(--border-subtle)';
                  e.currentTarget.style.background = 'var(--bg-panel)';
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: 'var(--radius-sm)',
                      background: elem.type === 'block' ? 'var(--accent-copper-dim)' : 'var(--badge-blue-bg)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: elem.type === 'block' ? 'var(--accent-copper)' : 'var(--badge-blue)'
                    }}
                  >
                    {elem.type === 'block' ? <Box size={16} /> : <Compass size={16} />}
                  </div>

                  <div>
                    <div style={{ fontWeight: 600, fontSize: '13px', color: 'var(--text-main)' }}>
                      {elem.displayName}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                      {elem.name} · {elem.ownership.toUpperCase()}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className={`badge badge-${elem.state === 'valid' ? 'green' : elem.state === 'draft' ? 'amber' : 'red'}`}>
                    {elem.state.toUpperCase()}
                  </span>
                  <span className="badge badge-copper" style={{ fontSize: '10px' }}>
                    {elem.type.toUpperCase()}
                  </span>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

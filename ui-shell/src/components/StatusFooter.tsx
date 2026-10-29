import { valueLabel } from '../i18n/labels';
import React from 'react';
import { WifiOff, Shield, AlertCircle, AlertTriangle, CheckCircle2, ListChecks, Activity, ChevronUp } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useMcpRuntimeState } from '../hooks/useMcpRuntimeState';
import { t, uiText, englishCount } from '../i18n';

/** Quiet status line: healthy states collapse to a dot, problems keep their words. Full text stays available to assistive technology. */
export const StatusFooter: React.FC = () => {
  const { state, workspaceHealth, openDiagnostics, openTask, setIsTaskDrawerOpen, isTaskDrawerOpen } = useWorkbench();
  const { mcp } = useMcpRuntimeState();

  const connection = state.workbench?.connection ?? {
    core: 'unknown',
    network: 'unknown',
    bridge: 'unknown'
  };

  const unknownCore = state.viewportState === 'loading' ? uiText('连接中…', 'Connecting…') : uiText('未取得工作区状态', 'Workspace state unavailable');
  const coreText = connection.core === 'unknown' ? unknownCore : valueLabel(connection.core);
  const coreHealthy = connection.core === 'connected';
  const listening = mcp?.status === 'listening';
  const permission = listening
    ? mcp.permissionProfile
    : state.workbench?.permission.profile ?? mcp?.permissionProfile ?? 'workspace';
  const activeTasks = state.workbench?.activeTasks ?? [];
  const runningTask = activeTasks.find((t) => t.state === 'running' || t.state === 'queued');

  const errorCount = workspaceHealth?.diagnostics.error ?? 0;
  const warningCount = workspaceHealth?.diagnostics.warning ?? 0;
  const partial = workspaceHealth?.diagnostics.collectionState === 'partial';
  const diagnosticsText = !workspaceHealth ? uiText('诊断检查中…', 'Checking diagnostics…')
    : uiText(`${errorCount} 错误，${warningCount} 警告${partial ? '（部分检查）' : ''}`,
      `${englishCount(errorCount, 'error')}, ${englishCount(warningCount, 'warning')}${partial ? ' (partial checks)' : ''}`);

  return (
    <footer className="status-footer" data-testid="status-footer">
      <div className="status-group">
        <div className="status-item" data-healthy={coreHealthy} data-testid="core-status"
          title={`${uiText('Java 核心', 'Java Core')}: ${coreText}`}>
          <span className={`status-dot${coreHealthy ? '' : ' is-warn'}`} aria-hidden="true" />
          <span className={coreHealthy ? 'sr-only' : undefined}>{uiText('核心：', 'Core: ')}{coreText}</span>
        </div>

        <div className={`status-item${connection.network === 'offline' ? ' is-offline' : ''}`} data-testid="offline-status"
          title={`${uiText('网络', 'Network')}: ${valueLabel(connection.network)}`}>
          {connection.network === 'offline' ? <>
            <WifiOff size={13} aria-hidden="true" />
            <span>{uiText('离线', 'Offline')}</span><span className="sr-only">{uiText('模式（本地可用）', ' (local work available)')}</span>
          </> : <span className="sr-only">{connection.network === 'unknown' ? uiText('网络状态未知', 'Network status unknown')
            : connection.network === 'online' ? uiText('在线', 'Online') : valueLabel(connection.network)}</span>}
        </div>

        <div className="status-item" data-healthy={connection.bridge === 'ready'} data-testid="bridge-status">
          <Activity size={13} aria-hidden="true" />
          <span>{uiText('桥接：', 'Bridge: ')}{connection.bridge === 'unknown' ? uiText('等待状态', 'Waiting for status') : valueLabel(connection.bridge)}</span>
        </div>
      </div>

      <div className="status-group">
        {runningTask ? (
          <button type="button" className="status-item is-task" onClick={() => openTask(runningTask.id)} data-testid="running-task-pill">
            <span className="status-dot is-busy" aria-hidden="true" />
            <span aria-live="polite">
              {t(runningTask.stage)}{runningTask.progress == null ? '…' : ` (${Math.round(runningTask.progress * 100)}%)`}
            </span>
            <ChevronUp size={12} aria-hidden="true" />
          </button>
        ) : (
          <button type="button" className="status-item" data-testid="recent-tasks-button" onClick={() => setIsTaskDrawerOpen(!isTaskDrawerOpen)}
            aria-expanded={isTaskDrawerOpen}
            aria-label={Object.keys(state.tasks).length > 0 ? t({ key: 'task.recent_selector', fallback: 'Recent tasks' }) : uiText('任务与日志', 'Tasks & logs')}>
            <ListChecks size={14} aria-hidden="true" />
            <span>{uiText('任务', 'Tasks')}</span>
          </button>
        )}

        <button type="button"
          className={`status-item${errorCount > 0 ? ' is-error' : warningCount > 0 ? ' is-warning' : ''}`}
          onClick={() => openDiagnostics(errorCount > 0 ? 'error' : warningCount > 0 ? 'warning' : 'all')}
          aria-label={uiText('查看当前工作区诊断', 'View current workspace diagnostics')}
          title={diagnosticsText}
          data-testid="diagnostics-badge">
          {!workspaceHealth ? <span className="status-dot is-busy" aria-hidden="true" />
            : errorCount === 0 && warningCount === 0 ? <CheckCircle2 size={13} aria-hidden="true" />
              : <>
                {errorCount > 0 && <span className="status-count" aria-hidden="true"><AlertCircle size={13} />{errorCount}</span>}
                {warningCount > 0 && <span className="status-count" aria-hidden="true"><AlertTriangle size={13} />{warningCount}</span>}
              </>}
          <span className="sr-only" role="status" aria-atomic="true">{diagnosticsText}</span>
        </button>

        <div className={`status-item status-mcp${listening ? permission === 'workspace' ? ' is-live' : ' is-elevated' : ''}`}
          title={listening ? `${uiText('MCP 服务已启动', 'MCP server listening')}: ${permission}` : uiText('MCP 服务未启动', 'MCP server is not running')}
          data-testid="permission-alert">
          <Shield size={13} aria-hidden="true" />
          <span>{'MCP'}<span className={listening ? undefined : 'sr-only'}>: {listening ? valueLabel(permission) : uiText('未启动', 'Not running')}</span></span>
        </div>
      </div>
    </footer>
  );
};

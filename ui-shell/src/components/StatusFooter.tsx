import { valueLabel } from '../i18n/labels';
import React from 'react';
import {
  Wifi,
  WifiOff,
  Server,
  Shield,
  AlertCircle,
  Terminal,
  Activity,
  ChevronUp
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useMcpRuntimeState } from '../hooks/useMcpRuntimeState';
import { t, uiText, englishCount } from '../i18n';

export const StatusFooter: React.FC = () => {
  const { state, workspaceHealth, openDiagnostics, openTask, setIsTaskDrawerOpen, isTaskDrawerOpen } = useWorkbench();
  const { mcp } = useMcpRuntimeState();

  const connection = state.workbench?.connection ?? {
    core: 'unknown',
    network: 'unknown',
    bridge: 'unknown'
  };

  const unknownCore = state.viewportState === 'loading' ? uiText('连接中…', 'Connecting…') : uiText('未取得工作区状态', 'Workspace state unavailable');
  const permission = mcp?.status === 'listening'
    ? mcp.permissionProfile
    : state.workbench?.permission.profile ?? mcp?.permissionProfile ?? 'workspace';
  const activeTasks = state.workbench?.activeTasks ?? [];
  const runningTask = activeTasks.find((t) => t.state === 'running' || t.state === 'queued');

  const errorCount = workspaceHealth?.diagnostics.error ?? 0;
  const warningCount = workspaceHealth?.diagnostics.warning ?? 0;

  return (
    <footer
      className="status-footer"
      data-testid="status-footer"
      style={{
        height: '28px',
        background: 'var(--footer-bg)',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 12px',
        fontSize: '11px',
        color: 'var(--text-muted)',
        zIndex: 50,
        userSelect: 'none'
      }}
    >
      {/* Left: Connection & Network */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        {/* Core Connection */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: '5px' }}
          data-healthy={connection.core === 'connected'}
          data-testid="core-status"
          title={`${uiText('Java 核心', 'Java Core')}: ${connection.core === 'unknown' ? unknownCore : valueLabel(connection.core)}`}
        >
          <Server size={12} color={connection.core === 'connected' ? 'var(--badge-green)' : 'var(--badge-amber)'} />
          <span>{uiText('核心：', 'Core: ')}{connection.core === 'unknown' ? unknownCore : valueLabel(connection.core)}</span>
        </div>

        {/* Network status */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: '5px' }}
          data-testid="offline-status"
          title={`${uiText('网络', 'Network')}: ${valueLabel(connection.network)}`}
        >
          {connection.network === 'unknown' ? <><WifiOff size={12} /><span>{uiText('网络状态未知', 'Network status unknown')}</span></> : connection.network === 'offline' ? (
            <>
              <WifiOff size={12} color="var(--badge-amber)" />
              <span style={{ color: 'var(--badge-amber)', fontWeight: 600 }}>{uiText('离线模式（本地可用）', 'Offline (local work available)')}</span>
            </>
          ) : (
            <>
              <Wifi size={12} color={connection.network === 'online' ? 'var(--badge-green)' : 'var(--badge-amber)'} />
              <span>{connection.network === 'online' ? uiText('在线', 'Online') : valueLabel(connection.network)}</span>
            </>
          )}
        </div>

        {/* Bridge Status */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: '5px' }}
          data-healthy={connection.bridge === 'ready'}
          data-testid="bridge-status"
        >
          <Activity size={12} color="var(--accent-copper)" />
          <span>{uiText('桥接：', 'Bridge: ')}{connection.bridge === 'unknown' ? uiText('等待状态', 'Waiting for status') : valueLabel(connection.bridge)}</span>
        </div>
      </div>

      {/* Right: Permission Pill & Diagnostics */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        {/* Active Task progress pill */}
        {runningTask && (
          <button
            onClick={() => openTask(runningTask.id)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              background: 'var(--accent-copper-dim)',
              padding: '1px 8px',
              borderRadius: 'var(--radius-xs)',
              color: 'var(--accent-copper)',
              fontSize: '10px',
              fontWeight: 600
            }}
            data-testid="running-task-pill"
          >
            <Terminal size={11} />
            <span aria-live="polite">
              {t(runningTask.stage)}{runningTask.progress == null ? '…' : ` (${Math.round(runningTask.progress * 100)}%)`}
            </span>
            <ChevronUp size={11} />
          </button>
        )}

        {!runningTask && <button
          data-testid="recent-tasks-button" onClick={() => setIsTaskDrawerOpen(!isTaskDrawerOpen)}
          style={{ color: 'var(--text-sub)', whiteSpace: 'nowrap' }}>
          {Object.keys(state.tasks).length > 0 ? t({ key: 'task.recent_selector', fallback: 'Recent tasks' }) : uiText('任务与日志', 'Tasks & logs')}
        </button>}

        {/* Diagnostics Badge */}
        <button
          onClick={() => openDiagnostics(errorCount > 0 ? 'error' : warningCount > 0 ? 'warning' : 'all')}
          aria-label={uiText('查看当前工作区诊断', 'View current workspace diagnostics')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            whiteSpace: 'nowrap',
            color: errorCount > 0 ? 'var(--badge-red)' : 'var(--text-sub)'
          }}
          data-testid="diagnostics-badge"
        >
          <AlertCircle size={12} />
          <span role="status" aria-atomic="true">{!workspaceHealth ? uiText('诊断检查中…', 'Checking diagnostics…')
            : uiText(`${errorCount} 错误，${warningCount} 警告${workspaceHealth.diagnostics.collectionState === 'partial' ? '（部分检查）' : ''}`,
              `${englishCount(errorCount, 'error')}, ${englishCount(warningCount, 'warning')}${workspaceHealth.diagnostics.collectionState === 'partial' ? ' (partial checks)' : ''}`)}</span>
        </button>

        {/* MCP Permission Pill */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '5px',
            background: 'var(--bg-panel)',
            border: '1px solid var(--border-subtle)',
            padding: '2px 8px',
            borderRadius: 'var(--radius-full)',
            color: mcp?.status !== 'listening' ? 'var(--text-muted)' : permission === 'workspace' ? 'var(--badge-green)' : permission === 'full_access' ? 'var(--accent-copper)' : 'var(--badge-amber)'
          }}
          title={mcp?.status === 'listening' ? `${uiText('MCP 服务已启动', 'MCP server listening')}: ${permission}` : uiText('MCP 服务未启动', 'MCP server is not running')}
          data-testid="permission-alert"
        >
          <Shield size={11} />
          <span style={{ fontWeight: 600 }}>MCP: {mcp?.status === 'listening' ? valueLabel(permission) : uiText('未启动', 'Not running')}</span>
        </div>
      </div>
    </footer>
  );
};

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
import { t } from '../i18n';

export const StatusFooter: React.FC = () => {
  const { state, setIsTaskDrawerOpen, isTaskDrawerOpen } = useWorkbench();
  const { mcp } = useMcpRuntimeState();

  const connection = state.workbench?.connection ?? {
    core: 'connected',
    network: 'online',
    bridge: 'ready'
  };

  const permission = mcp?.permissionProfile ?? 'workspace';
  const activeTasks = state.workbench?.activeTasks ?? [];
  const runningTask = activeTasks.find((t) => t.state === 'running' || t.state === 'queued');

  const errorCount = state.diagnostics.filter((d) => d.severity === 'error').length;
  const warningCount = state.diagnostics.filter((d) => d.severity === 'warning').length;

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
          data-testid="core-status"
          title={`Java Core: ${connection.core}`}
        >
          <Server size={12} color="var(--badge-green)" />
          <span>核心：{connection.core}</span>
        </div>

        {/* Network status */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: '5px' }}
          data-testid="offline-status"
          title={`Network: ${connection.network}`}
        >
          {connection.network === 'offline' ? (
            <>
              <WifiOff size={12} color="var(--badge-amber)" />
              <span style={{ color: 'var(--badge-amber)', fontWeight: 600 }}>离线模式（本地可用）</span>
            </>
          ) : (
            <>
              <Wifi size={12} color="var(--badge-green)" />
              <span>在线</span>
            </>
          )}
        </div>

        {/* Bridge Status */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: '5px' }}
          data-testid="bridge-status"
        >
          <Activity size={12} color="var(--accent-copper)" />
          <span>桥接：{connection.bridge}</span>
        </div>
      </div>

      {/* Right: Permission Pill & Diagnostics */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        {/* Active Task progress pill */}
        {runningTask && (
          <button
            onClick={() => setIsTaskDrawerOpen(!isTaskDrawerOpen)}
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
              {t(runningTask.stage)}（{Math.round((runningTask.progress || 0) * 100)}%）
            </span>
            <ChevronUp size={11} />
          </button>
        )}

        {/* Diagnostics Badge */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            color: errorCount > 0 ? 'var(--badge-red)' : 'var(--text-sub)'
          }}
          data-testid="diagnostics-badge"
        >
          <AlertCircle size={12} />
          <span>{errorCount} 错误，{warningCount} 警告</span>
        </div>

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
            color: permission === 'workspace' ? 'var(--badge-green)' : permission === 'full_access' ? 'var(--accent-copper)' : 'var(--badge-amber)'
          }}
          title={mcp?.status === 'listening' ? `MCP 服务已启动：${permission}` : 'MCP 服务未启动'}
          data-testid="permission-alert"
        >
          <Shield size={11} />
          <span style={{ fontWeight: 600 }}>MCP: {mcp?.status === 'listening' ? permission.toUpperCase() : 'OFFLINE'}</span>
        </div>
      </div>
    </footer>
  );
};

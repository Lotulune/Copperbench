import React, { useEffect, useRef } from 'react';
import {
  Terminal,
  X,
  Ban
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { t } from '../i18n';

export const TaskDrawer: React.FC = () => {
  const {
    isTaskDrawerOpen,
    setIsTaskDrawerOpen,
    activeTaskId,
    state,
    cancelTask
  } = useWorkbench();

  const logContainerRef = useRef<HTMLDivElement>(null);

  // Active task and logs
  const activeTask = activeTaskId
    ? state.tasks[activeTaskId]
    : Object.values(state.tasks)[0] || null;

  const logs = activeTask ? state.taskLogs[activeTask.id] || [] : [];

  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [logs]);

  if (!isTaskDrawerOpen) return null;

  return (
    <div
      className="task-drawer animate-fade-in"
      data-testid="task-drawer"
      style={{
        position: 'absolute',
        bottom: '28px', // above status footer
        left: '190px', // next to navrail
        right: 0,
        height: '240px',
        background: 'var(--drawer-bg)',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.4)',
        zIndex: 40
      }}
    >
      {/* Drawer Header */}
      <div
        style={{
          padding: '8px 16px',
          background: 'var(--bg-surface)',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600, fontSize: '12px', color: 'var(--accent-copper)' }}>
            <Terminal size={14} />
            <span>任务控制台与日志流</span>
          </div>

          {activeTask && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span className="badge badge-copper" style={{ fontSize: '10px' }}>
                {activeTask.kind.toUpperCase()}
              </span>
              <span
                className={`badge badge-${activeTask.state === 'succeeded' ? 'green' : activeTask.state === 'running' ? 'amber' : 'red'}`}
                style={{ fontSize: '10px' }}
              >
                {activeTask.state.toUpperCase()}
              </span>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                {t(activeTask.stage)}
              </span>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {activeTask?.cancellable && (
            <button
              className="btn-danger"
              style={{ fontSize: '11px', padding: '2px 8px' }}
              onClick={() => cancelTask(activeTask.id)}
              data-testid="task-cancel-btn"
            >
              <Ban size={12} />
              <span>取消</span>
            </button>
          )}

          <button
            onClick={() => setIsTaskDrawerOpen(false)}
            style={{ padding: '2px 6px', color: 'var(--text-muted)' }}
            title="关闭日志抽屉"
            data-testid="task-drawer-close"
          >
            <X size={14} />
          </button>
        </div>
      </div>

      {/* Log Stream Output */}
      <div
        ref={logContainerRef}
        data-testid="task-log-stream"
        role="log"
        aria-live="polite"
        aria-label="Task log stream"
        style={{
          flex: 1,
          padding: '12px 16px',
          overflowY: 'auto',
          background: 'var(--bg-input)',
          fontFamily: 'var(--font-mono)',
          fontSize: '11px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px'
        }}
      >
        {logs.length === 0 ? (
          <div style={{ color: 'var(--text-sub)', fontStyle: 'italic' }}>
            暂无任务输出日志。
          </div>
        ) : (
          logs.map((entry, idx) => (
            <div
              key={idx}
              style={{
                display: 'flex',
                gap: '8px',
                lineHeight: '1.4',
                color:
                  entry.level === 'error'
                    ? 'var(--badge-red)'
                    : entry.level === 'warning'
                    ? 'var(--badge-amber)'
                    : 'var(--text-main)'
              }}
            >
              <span style={{ color: 'var(--text-sub)', flexShrink: 0 }}>
                [{entry.timestamp.slice(11, 19)}]
              </span>
              <span
                style={{
                  fontWeight: 600,
                  color:
                    entry.level === 'error'
                      ? 'var(--badge-red)'
                      : entry.level === 'warning'
                      ? 'var(--badge-amber)'
                      : 'var(--badge-blue)',
                  flexShrink: 0
                }}
              >
                [{entry.level.toUpperCase()}]
              </span>
              <span style={{ wordBreak: 'break-word' }}>{entry.text}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

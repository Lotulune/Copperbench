import React, { useEffect, useRef, useState } from 'react';
import {
  Terminal,
  X,
  Ban,
  FileDiff,
  Upload,
  LoaderCircle,
  CheckCircle2
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { t } from '../i18n';
import type { DatagenPreview } from '../types/contract';

export const TaskDrawer: React.FC = () => {
  const {
    isTaskDrawerOpen,
    setIsTaskDrawerOpen,
    activeTaskId,
    state,
    cancelTask,
    previewDatagenOutput,
    publishDatagenOutput
  } = useWorkbench();

  const logContainerRef = useRef<HTMLDivElement>(null);
  const [datagenPreview, setDatagenPreview] = useState<DatagenPreview | null>(null);
  const [datagenBusy, setDatagenBusy] = useState(false);
  const [datagenError, setDatagenError] = useState<string | null>(null);
  const [confirmPublish, setConfirmPublish] = useState(false);

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

  useEffect(() => {
    setDatagenPreview(null);
    setDatagenError(null);
    setConfirmPublish(false);
  }, [activeTask?.id]);

  const loadDatagenPreview = async () => {
    if (!activeTask) return;
    setDatagenBusy(true);
    setDatagenError(null);
    try {
      const preview = await previewDatagenOutput(activeTask.id);
      if (!preview) setDatagenError('无法读取暂存结果，请确认任务已成功完成。');
      setDatagenPreview(preview);
    } catch {
      setDatagenError('无法读取暂存结果，请查看任务日志。');
    } finally {
      setDatagenBusy(false);
    }
  };

  const publishDatagen = async () => {
    if (!activeTask || !datagenPreview?.canPublish) return;
    setConfirmPublish(false);
    setDatagenBusy(true);
    setDatagenError(null);
    try {
      const result = await publishDatagenOutput(activeTask.id, datagenPreview.manifestHash);
      if (result.status === 'committed' && result.data) {
        setDatagenPreview(result.data as DatagenPreview);
      } else {
        setDatagenError(result.diagnostics[0] ? t(result.diagnostics[0].message) : '发布失败，工作区未变更。');
      }
    } catch {
      setDatagenError('发布失败，工作区未变更。');
    } finally {
      setDatagenBusy(false);
    }
  };

  if (!isTaskDrawerOpen) return null;

  return (
    <div
      className="task-drawer animate-fade-in"
      data-testid="task-drawer"
      style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        height: datagenPreview || datagenError ? '360px' : '240px',
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
          {activeTask?.kind === 'run_datagen' && activeTask.state === 'succeeded' && !datagenPreview && (
            <button
              type="button"
              className="btn-secondary"
              style={{ fontSize: '11px', minHeight: '28px', padding: '3px 9px' }}
              onClick={loadDatagenPreview}
              disabled={datagenBusy}
              data-testid="datagen-preview-btn"
            >
              {datagenBusy ? <LoaderCircle className="spin" size={13} /> : <FileDiff size={13} />}
              <span>查看暂存差异</span>
            </button>
          )}
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
            type="button"
            aria-label="关闭日志抽屉"
            onClick={() => setIsTaskDrawerOpen(false)}
            style={{ padding: '2px 6px', color: 'var(--text-muted)' }}
            title="关闭日志抽屉"
            data-testid="task-drawer-close"
          >
            <X size={14} />
          </button>
        </div>
      </div>

      {(datagenPreview || datagenError) && (
        <section
          data-testid="datagen-preview"
          style={{
            padding: '10px 16px',
            borderBottom: '1px solid var(--border-subtle)',
            background: 'var(--bg-panel)',
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr) auto',
            gap: '10px 16px',
            alignItems: 'center'
          }}
        >
          <div style={{ minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', fontWeight: 600 }}>
              {datagenPreview?.published ? <CheckCircle2 size={14} color="var(--badge-green)" /> : <FileDiff size={14} />}
              <span>{datagenPreview?.published ? '生成结果已发布' : `暂存差异 ${datagenPreview?.changeCount ?? 0} 项`}</span>
              {datagenPreview?.stale && <span className="badge badge-red">修订已过期</span>}
            </div>
            {datagenPreview && (
              <div style={{ marginTop: '6px', maxHeight: '48px', overflowY: 'auto', fontFamily: 'var(--font-mono)', fontSize: '10px', color: 'var(--text-muted)' }}>
                {datagenPreview.files.map((file) => (
                  <div key={file.path} title={file.sha256} style={{ display: 'flex', gap: '8px' }}>
                    <span style={{ color: file.status === 'add' ? 'var(--badge-green)' : file.status === 'modify' ? 'var(--badge-amber)' : 'var(--text-sub)', width: '50px', flexShrink: 0 }}>
                      {file.status.toUpperCase()}
                    </span>
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{file.path}</span>
                  </div>
                ))}
              </div>
            )}
            {datagenError && <div style={{ marginTop: '4px', color: 'var(--badge-red)', fontSize: '11px' }}>{datagenError}</div>}
          </div>
          {datagenPreview?.canPublish && (
            <button
              type="button"
              className="btn-primary"
              onClick={() => setConfirmPublish(true)}
              disabled={datagenBusy || datagenPreview.stale}
              data-testid="datagen-publish-btn"
              title="校验当前修订与预览哈希后写入工作区"
            >
              {datagenBusy ? <LoaderCircle className="spin" size={14} /> : <Upload size={14} />}
              <span>发布到工作区</span>
            </button>
          )}
        </section>
      )}

      {/* Log Stream Output */}
      <div
        ref={logContainerRef}
        data-testid="task-log-stream"
        role="log"
        aria-live="polite"
        aria-label={t({ key: 'aria.task_log_stream', fallback: 'Task log stream' })}
        style={{
          flex: 1,
          padding: '12px 16px',
          overflowY: 'auto',
          background: 'var(--bg-input)',
          fontFamily: 'var(--font-mono)',
          fontSize: '11px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
          userSelect: 'text'
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

      {confirmPublish && datagenPreview && (
        <div
          role="presentation"
          onMouseDown={() => setConfirmPublish(false)}
          style={{
            position: 'fixed', inset: 0, zIndex: 100,
            background: 'rgba(0, 0, 0, 0.58)',
            display: 'grid', placeItems: 'center', padding: '24px'
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="datagen-publish-title"
            onMouseDown={(event) => event.stopPropagation()}
            style={{
              width: 'min(440px, 100%)',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-active)',
              borderRadius: 'var(--radius-md)',
              boxShadow: '0 16px 48px rgba(0, 0, 0, 0.55)',
              padding: '18px'
            }}
          >
            <h2 id="datagen-publish-title" style={{ margin: 0, fontSize: '16px' }}>发布数据生成结果</h2>
            <p style={{ margin: '10px 0 16px', color: 'var(--text-muted)', fontSize: '12px', lineHeight: 1.6 }}>
              将 {datagenPreview.changeCount} 个暂存文件写入当前工作区。发布前会创建恢复点，并再次校验工作区修订和清单哈希。
            </p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button type="button" className="btn-secondary" onClick={() => setConfirmPublish(false)}>取消</button>
              <button
                type="button"
                className="btn-primary"
                onClick={publishDatagen}
                data-testid="datagen-confirm-publish"
              >
                <Upload size={14} />
                <span>确认发布</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

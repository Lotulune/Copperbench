import React, { useEffect, useMemo, useState } from 'react';
import {
  Check,
  ChevronRight,
  FileDiff,
  GitBranch,
  Plus,
  RotateCcw,
  ShieldAlert,
  X
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import type { RecoveryPoint, WorkspaceChange } from '../types/contract';

const actorLabels: Record<RecoveryPoint['actor'], string> = {
  ui: '界面',
  mcp: 'MCP',
  headless: 'Headless',
  legacy_ui: '旧版界面',
  system: '系统'
};

const changeLabels: Record<WorkspaceChange['type'], string> = {
  add: '新增',
  modify: '修改',
  delete: '删除',
  rename: '重命名',
  copy: '复制'
};

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value));
}

export const HistoryView: React.FC = () => {
  const { state, createRecoveryPoint, restoreRecoveryPoint } = useWorkbench();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [restoreOpen, setRestoreOpen] = useState(false);
  const [label, setLabel] = useState('');
  const [status, setStatus] = useState('');
  const createDialogRef = useDialogA11y(createOpen, () => setCreateOpen(false));
  const restoreDialogRef = useDialogA11y(restoreOpen, () => setRestoreOpen(false));

  useEffect(() => {
    if (!selectedId || !state.recoveryPoints.some((point) => point.id === selectedId)) {
      setSelectedId(state.recoveryPoints[0]?.id ?? null);
    }
  }, [selectedId, state.recoveryPoints]);

  const selected = useMemo(
    () => state.recoveryPoints.find((point) => point.id === selectedId) ?? null,
    [selectedId, state.recoveryPoints]
  );
  const changes = state.historyComparison?.changes ?? [];

  const createPoint = async () => {
    const trimmed = label.trim();
    if (!trimmed) return;
    const result = await createRecoveryPoint(trimmed);
    if (result.status === 'committed') {
      setCreateOpen(false);
      setLabel('');
      setSelectedId(result.data?.recoveryPoint?.id ?? null);
      setStatus(`已创建恢复点“${trimmed}”`);
    }
  };

  const restorePoint = async () => {
    if (!selected) return;
    const result = await restoreRecoveryPoint(selected.id);
    if (result.status === 'committed') {
      setRestoreOpen(false);
      setStatus(`已还原到“${selected.label}”，工作区将重新校验`);
    }
  };

  return (
    <section className="stage2-view history-view animate-fade-in" data-testid="history-view">
      <header className="stage2-view-header">
        <div className="stage2-view-title">
          <GitBranch size={20} aria-hidden="true" />
          <div>
            <h2>本地历史</h2>
            <span>工作区修订 {state.workbench?.workspace.revision ?? 0}</span>
          </div>
        </div>
        <button
          className="btn-primary"
          type="button"
          data-testid="create-recovery-point"
          onClick={() => setCreateOpen(true)}
        >
          <Plus size={15} aria-hidden="true" />
          创建恢复点
        </button>
      </header>

      <div className="history-workspace">
        <aside className="history-timeline" aria-label="恢复点时间线">
          <div className="history-section-label">恢复点</div>
          <div className="history-list">
            {state.recoveryPoints.map((point) => {
              const selectedPoint = point.id === selectedId;
              const current = point.id === state.currentRecoveryPointId;
              return (
                <button
                  type="button"
                  key={point.id}
                  className={`history-point${selectedPoint ? ' is-selected' : ''}`}
                  data-testid="history-point"
                  aria-pressed={selectedPoint}
                  onClick={() => setSelectedId(point.id)}
                >
                  <span className="history-node" aria-hidden="true" />
                  <span className="history-point-content">
                    <span className="history-point-title">
                      {point.label}
                      {current && <span className="history-current">当前</span>}
                    </span>
                    <span className="history-point-meta">
                      {actorLabels[point.actor]} · {formatTime(point.createdAt)}
                    </span>
                  </span>
                  <ChevronRight size={14} aria-hidden="true" />
                </button>
              );
            })}
          </div>
        </aside>

        <div className="history-detail">
          {selected ? (
            <>
              <div className="history-detail-heading">
                <div>
                  <span className="history-section-label">所选恢复点</span>
                  <h3>{selected.label}</h3>
                  <p>{selected.id.slice(0, 12)} · {actorLabels[selected.actor]} · {formatTime(selected.createdAt)}</p>
                </div>
                <button
                  className="btn-secondary"
                  type="button"
                  data-testid="restore-recovery-point"
                  disabled={selected.id === state.currentRecoveryPointId}
                  onClick={() => setRestoreOpen(true)}
                >
                  <RotateCcw size={15} aria-hidden="true" />
                  还原到此处
                </button>
              </div>

              <div className="history-diff-heading">
                <div>
                  <FileDiff size={16} aria-hidden="true" />
                  <span>与上一恢复点比较</span>
                </div>
                <span>{changes.length} 个文件</span>
              </div>
              <div className="history-change-list" aria-label="文件差异">
                {changes.map((change) => (
                  <div className="history-change" data-testid="history-change" key={`${change.type}:${change.path}`}>
                    <span className={`change-badge change-${change.type}`}>{changeLabels[change.type]}</span>
                    <code>{change.path}</code>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="stage2-empty">尚无恢复点</div>
          )}
        </div>
      </div>

      <div className="stage2-status" data-testid="history-status" aria-live="polite">
        {status && <><Check size={14} aria-hidden="true" />{status}</>}
      </div>

      {createOpen && (
        <div className="modal-overlay">
          <div
            className="modal-card stage2-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-recovery-title"
            data-testid="create-recovery-dialog"
            ref={createDialogRef}
          >
            <div className="modal-header">
              <h3 id="create-recovery-title">创建恢复点</h3>
              <button className="icon-button" type="button" aria-label="关闭" onClick={() => setCreateOpen(false)}>
                <X size={16} />
              </button>
            </div>
            <div className="modal-body">
              <label className="field-label" htmlFor="recovery-label">名称</label>
              <input
                id="recovery-label"
                data-testid="recovery-label-input"
                value={label}
                maxLength={200}
                autoComplete="off"
                onChange={(event) => setLabel(event.target.value)}
                placeholder="例如：导入纹理前"
              />
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" type="button" onClick={() => setCreateOpen(false)}>取消</button>
              <button
                className="btn-primary"
                type="button"
                data-testid="confirm-create-recovery"
                disabled={!label.trim()}
                onClick={() => void createPoint()}
              >
                <Plus size={15} aria-hidden="true" />
                创建
              </button>
            </div>
          </div>
        </div>
      )}

      {restoreOpen && selected && (
        <div className="modal-overlay">
          <div
            className="modal-card stage2-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="restore-recovery-title"
            data-testid="restore-recovery-dialog"
            ref={restoreDialogRef}
          >
            <div className="modal-header">
              <div className="dialog-title-with-icon">
                <ShieldAlert size={18} aria-hidden="true" />
                <h3 id="restore-recovery-title">确认还原工作区</h3>
              </div>
              <button className="icon-button" type="button" aria-label="关闭" onClick={() => setRestoreOpen(false)}>
                <X size={16} />
              </button>
            </div>
            <div className="modal-body">
              <p>将工作区还原到“{selected.label}”。当前状态会先创建恢复点，然后重新校验工作区。</p>
              <div className="dialog-impact-list">
                {changes.slice(0, 5).map((change) => <code key={change.path}>{change.path}</code>)}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" type="button" onClick={() => setRestoreOpen(false)}>取消</button>
              <button
                className="btn-danger"
                type="button"
                data-testid="confirm-restore-recovery"
                onClick={() => void restorePoint()}
              >
                <RotateCcw size={15} aria-hidden="true" />
                确认还原
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};

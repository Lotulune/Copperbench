import React, { useState, useMemo } from 'react';
import {
  X,
  Upload,
  FileSpreadsheet,
  Check,
  AlertTriangle
} from 'lucide-react';
import { useDialogA11y } from '../hooks/useDialogA11y';

export interface ParsedLanguageEntry {
  key: string;
  zh_cn: string;
  en_us: string;
}

export type ImportConflictMode = 'merge' | 'keep' | 'replace';

interface LanguageImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  existingKeys: Set<string>;
  onImport: (entries: ParsedLanguageEntry[], mode: ImportConflictMode) => Promise<void>;
}

export const LanguageImportModal: React.FC<LanguageImportModalProps> = ({
  isOpen,
  onClose,
  existingKeys,
  onImport
}) => {
  const [rawText, setRawText] = useState('');
  const [conflictMode, setConflictMode] = useState<ImportConflictMode>('merge');
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dialogRef = useDialogA11y(isOpen, onClose);

  // Parse CSV or JSON from text
  const parseResult = useMemo(() => {
    if (!rawText.trim()) {
      return { entries: [], errors: [], newCount: 0, updateCount: 0 };
    }

    const trimmed = rawText.trim();
    const entries: ParsedLanguageEntry[] = [];
    const errors: string[] = [];

    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      // JSON format
      try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) {
          parsed.forEach((item, idx) => {
            if (typeof item === 'object' && item !== null && typeof item.key === 'string') {
              entries.push({
                key: item.key.trim(),
                zh_cn: String(item.zh_cn ?? item['zh-CN'] ?? item.translation ?? ''),
                en_us: String(item.en_us ?? item['en-US'] ?? (item as Record<string, unknown>)['en'] ?? '')
              });
            } else {
              errors.push(`第 ${idx + 1} 个 JSON 项缺少有效的 key 字段。`);
            }
          });
        } else if (typeof parsed === 'object' && parsed !== null) {
          Object.entries(parsed).forEach(([key, val]) => {
            if (typeof val === 'string') {
              // Assume zh_cn or key-value map
              entries.push({
                key: key.trim(),
                zh_cn: val,
                en_us: val
              });
            } else if (typeof val === 'object' && val !== null) {
              const valObj = val as Record<string, unknown>;
              entries.push({
                key: key.trim(),
                zh_cn: String(valObj.zh_cn ?? valObj['zh-CN'] ?? ''),
                en_us: String(valObj.en_us ?? valObj['en-US'] ?? '')
              });
            }
          });
        }
      } catch (err) {
        errors.push(`JSON 解析错误: ${err instanceof Error ? err.message : String(err)}`);
      }
    } else {
      // CSV format
      const lines = trimmed.split(/\r?\n/).filter((l) => l.trim().length > 0);
      let startIndex = 0;
      if (lines[0] && (lines[0].toLowerCase().includes('key') || lines[0].toLowerCase().includes('键'))) {
        startIndex = 1; // skip header
      }

      for (let i = startIndex; i < lines.length; i++) {
        const line = lines[i];
        // Split by comma or tab (respecting simple quotes)
        const parts = line.split(/[,\t]/).map((p) => p.trim().replace(/^["']|["']$/g, ''));
        if (parts.length >= 1 && parts[0]) {
          entries.push({
            key: parts[0],
            zh_cn: parts[1] || '',
            en_us: parts[2] || parts[1] || ''
          });
        } else {
          errors.push(`第 ${i + 1} 行不是有效的 CSV 格式。`);
        }
      }
    }

    let newCount = 0;
    let updateCount = 0;
    entries.forEach((e) => {
      if (existingKeys.has(e.key)) {
        updateCount++;
      } else {
        newCount++;
      }
    });

    return { entries, errors, newCount, updateCount };
  }, [rawText, existingKeys]);

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result;
      if (typeof content === 'string') {
        setRawText(content);
        setError(null);
      }
    };
    reader.onerror = () => {
      setError('无法读取所选文件。');
    };
    reader.readAsText(file);
  };

  const handleApplyImport = async () => {
    if (parseResult.entries.length === 0) {
      setError('没有可导入的语言词条。');
      return;
    }

    setIsProcessing(true);
    setError(null);
    try {
      await onImport(parseResult.entries, conflictMode);
      setIsProcessing(false);
      onClose();
    } catch (err) {
      setIsProcessing(false);
      setError(err instanceof Error ? err.message : '导入语言包时发生错误。');
    }
  };

  return (
    <div className="modal-overlay" data-testid="language-import-modal">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="导入语言词条"
        className="modal-card animate-fade-in"
        style={{ width: '560px', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
      >
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
            <Upload size={16} color="var(--accent-copper)" />
            <span>导入语言词条 (CSV / JSON)</span>
          </div>
          <button
            type="button"
            aria-label="关闭导入对话框"
            onClick={onClose}
            style={{ color: 'var(--text-muted)' }}
          >
            <X size={16} />
          </button>
        </div>

        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px', overflowY: 'auto' }}>
          {error && (
            <div style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.4)', padding: '10px 12px', borderRadius: 'var(--radius-sm)', color: 'var(--badge-red)', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <AlertTriangle size={14} />
              <span>{error}</span>
            </div>
          )}

          {/* File Upload Option */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px', background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <FileSpreadsheet size={24} color="var(--accent-copper)" />
              <div>
                <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-main)' }}>
                  从本地文件导入
                </div>
                <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                  支持 .csv（key,zh_cn,en_us）与 .json 格式文件
                </div>
              </div>
            </div>
            <label
              className="btn-secondary"
              style={{ padding: '6px 12px', fontSize: '11px', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
            >
              <Upload size={13} />
              <span>选择文件</span>
              <input
                type="file"
                accept=".csv,.json,text/csv,application/json"
                onChange={handleFileUpload}
                style={{ display: 'none' }}
                data-testid="language-file-input"
              />
            </label>
          </div>

          {/* Paste / Direct input */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
              或在此处粘贴 CSV / JSON 文本：
            </label>
            <textarea
              rows={6}
              value={rawText}
              onChange={(e) => setRawText(e.target.value)}
              placeholder="key,zh_cn,en_us&#10;item.copperbench.ruby,红宝石,Ruby&#10;block.copperbench.copper_lamp,铜灯,Copper Lamp"
              data-testid="language-paste-input"
              style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', resize: 'vertical' }}
            />
          </div>

          {/* Parse Statistics & Diff Breakdown */}
          {parseResult.entries.length > 0 && (
            <div
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '12px 14px',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px'
              }}
              data-testid="import-diff-summary"
            >
              <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-main)' }}>
                解析结果统计：
              </div>
              <div style={{ display: 'flex', gap: '12px', fontSize: '11px', flexWrap: 'wrap' }}>
                <span style={{ color: 'var(--badge-green)', fontWeight: 600 }}>
                  ✓ 成功解析 {parseResult.entries.length} 个词条
                </span>
                <span className="badge badge-copper">新增 {parseResult.newCount} 项</span>
                <span className="badge badge-amber">更新 {parseResult.updateCount} 项</span>
              </div>
            </div>
          )}

          {/* Conflict Strategy */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <label style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
              冲突处理策略：
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px' }}>
              {[
                { mode: 'merge', title: '合并并覆盖', desc: '更新匹配词条并添加新词条' },
                { mode: 'keep', title: '保留现有', desc: '已有词条不变，仅添加新词条' },
                { mode: 'replace', title: '全量替换', desc: '清空当前注册表并应用新列表' }
              ].map((strat) => {
                const isSel = conflictMode === strat.mode;
                return (
                  <button
                    key={strat.mode}
                    type="button"
                    onClick={() => setConflictMode(strat.mode as ImportConflictMode)}
                    aria-pressed={isSel}
                    data-testid={`strategy-${strat.mode}`}
                    style={{
                      padding: '8px',
                      borderRadius: 'var(--radius-sm)',
                      border: isSel ? '1px solid var(--accent-copper)' : '1px solid var(--border-subtle)',
                      background: isSel ? 'var(--accent-copper-dim)' : 'var(--bg-surface)',
                      color: isSel ? 'var(--accent-copper)' : 'var(--text-main)',
                      textAlign: 'left',
                      cursor: 'pointer'
                    }}
                  >
                    <div style={{ fontWeight: 600, fontSize: '11px' }}>{strat.title}</div>
                    <div style={{ fontSize: '9px', color: 'var(--text-sub)', marginTop: '2px' }}>
                      {strat.desc}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn-secondary" onClick={onClose}>
            取消
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={handleApplyImport}
            disabled={isProcessing || parseResult.entries.length === 0}
            data-testid="confirm-language-import-btn"
          >
            <Check size={14} />
            <span>{isProcessing ? '正在导入…' : `确认导入 (${parseResult.entries.length} 项)`}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

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

/**
 * Robust CSV/TSV parser supporting:
 * - Quoted fields with commas, tabs, and newlines
 * - Escaped quotes ("" and \")
 * - Tab delimiters
 * - CRLF, LF, and CR newlines
 * - Simple unquoted CSV/TSV
 */
export function parseCsvRows(input: string): string[][] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentField = '';
  let inQuotes = false;
  let quoteChar = '"';
  let i = 0;
  const len = input.length;

  while (i < len) {
    const char = input[i];

    if (!inQuotes) {
      if ((char === '"' || char === "'") && currentField.trim() === '') {
        inQuotes = true;
        quoteChar = char;
        currentField = '';
        i++;
      } else if (char === ',' || char === '\t') {
        currentRow.push(currentField.trim());
        currentField = '';
        i++;
      } else if (char === '\r') {
        if (i + 1 < len && input[i + 1] === '\n') {
          i++;
        }
        currentRow.push(currentField.trim());
        currentField = '';
        if (currentRow.some((f) => f.length > 0)) {
          rows.push(currentRow);
        }
        currentRow = [];
        i++;
      } else if (char === '\n') {
        currentRow.push(currentField.trim());
        currentField = '';
        if (currentRow.some((f) => f.length > 0)) {
          rows.push(currentRow);
        }
        currentRow = [];
        i++;
      } else {
        currentField += char;
        i++;
      }
    } else {
      // Inside quotes
      if (char === quoteChar) {
        if (i + 1 < len && input[i + 1] === quoteChar) {
          // Escaped quote: "" or ''
          currentField += quoteChar;
          i += 2;
        } else {
          // Closing quote
          inQuotes = false;
          i++;
        }
      } else if (char === '\\' && i + 1 < len && (input[i + 1] === quoteChar || input[i + 1] === '\\')) {
        // Escaped quote: \" or \\
        currentField += input[i + 1];
        i += 2;
      } else {
        currentField += char;
        i++;
      }
    }
  }

  // Push remaining field & row
  currentRow.push(currentField.trim());
  if (currentRow.some((f) => f.length > 0)) {
    rows.push(currentRow);
  }

  return rows;
}

export function parseCsvLanguageEntries(input: string): { entries: ParsedLanguageEntry[]; errors: string[] } {
  const rows = parseCsvRows(input);
  const entries: ParsedLanguageEntry[] = [];
  const errors: string[] = [];

  let startIndex = 0;
  if (rows.length > 0) {
    const firstRow = rows[0].map((c) => c.toLowerCase());
    if (
      firstRow.some(
        (c) =>
          c === 'key' ||
          c === '键' ||
          c === 'id' ||
          c === 'translations' ||
          c === 'zh_cn' ||
          c === 'en_us'
      )
    ) {
      startIndex = 1;
    }
  }

  for (let i = startIndex; i < rows.length; i++) {
    const parts = rows[i];
    if (parts.length >= 1 && parts[0]) {
      const key = parts[0];
      const zh = parts[1] ?? '';
      const en = parts[2] ?? parts[1] ?? '';
      entries.push({
        key,
        zh_cn: zh,
        en_us: en
      });
    } else if (parts.length > 0 && parts.some((p) => p.length > 0)) {
      errors.push(`第 ${i + 1} 行不是有效的 CSV 格式。`);
    }
  }

  return { entries, errors };
}

export function parseJsonLanguageEntries(parsed: unknown): { entries: ParsedLanguageEntry[]; errors: string[] } {
  const entries: ParsedLanguageEntry[] = [];
  const errors: string[] = [];

  if (Array.isArray(parsed)) {
    parsed.forEach((item, idx) => {
      if (typeof item === 'object' && item !== null) {
        const itemObj = item as Record<string, unknown>;
        const key = String(itemObj.key ?? itemObj.name ?? itemObj.id ?? '').trim();
        if (key) {
          const trans =
            typeof itemObj.translations === 'object' && itemObj.translations !== null
              ? (itemObj.translations as Record<string, unknown>)
              : itemObj;
          const zh = String(
            trans.zh_cn ?? trans['zh-CN'] ?? trans.zh_CN ?? trans.zh ?? trans.translation ?? ''
          );
          const en = String(trans.en_us ?? trans['en-US'] ?? trans.en_US ?? trans.en ?? '');
          entries.push({
            key,
            zh_cn: zh,
            en_us: en || zh
          });
        } else {
          errors.push(`第 ${idx + 1} 个 JSON 项缺少有效的 key 字段。`);
        }
      } else {
        errors.push(`第 ${idx + 1} 个 JSON 项不是有效对象。`);
      }
    });
    return { entries, errors };
  }

  if (typeof parsed === 'object' && parsed !== null) {
    const obj = parsed as Record<string, unknown>;
    const topKeys = Object.keys(obj);

    // Detect if this is a Locale-Map shape (e.g. exported by CreatorDataView):
    // e.g. { "zh_cn": { "item.ruby": "红宝石" }, "en_us": { "item.ruby": "Ruby" } }
    const isLikelyLocaleMap =
      topKeys.length > 0 &&
      topKeys.every((k) => {
        const val = obj[k];
        return typeof val === 'object' && val !== null && !Array.isArray(val);
      }) &&
      topKeys.some((k) => {
        const lower = k.toLowerCase().replace('-', '_');
        return (
          lower === 'zh_cn' ||
          lower === 'en_us' ||
          lower === 'zh' ||
          lower === 'en' ||
          /^[a-z]{2}(_[a-z0-9]+)?$/i.test(lower)
        );
      }) &&
      !topKeys.some((k) => k.includes('.') || k.includes(':'));

    if (isLikelyLocaleMap) {
      const keyMap = new Map<string, { zh_cn: string; en_us: string }>();

      for (const [localeKey, val] of Object.entries(obj)) {
        if (typeof val === 'object' && val !== null && !Array.isArray(val)) {
          const lowerLoc = localeKey.toLowerCase().replace('-', '_');
          const isZh = lowerLoc.startsWith('zh');
          const isEn = lowerLoc.startsWith('en');

          for (const [k, text] of Object.entries(val as Record<string, unknown>)) {
            const trimmedKey = k.trim();
            if (!trimmedKey) continue;
            const strVal = typeof text === 'string' ? text : String(text ?? '');

            let current = keyMap.get(trimmedKey);
            if (!current) {
              current = { zh_cn: '', en_us: '' };
              keyMap.set(trimmedKey, current);
            }

            if (isZh) {
              current.zh_cn = strVal;
            } else if (isEn) {
              current.en_us = strVal;
            } else {
              if (!current.zh_cn) current.zh_cn = strVal;
              if (!current.en_us) current.en_us = strVal;
            }
          }
        }
      }

      for (const [key, trans] of keyMap.entries()) {
        entries.push({
          key,
          zh_cn: trans.zh_cn || trans.en_us,
          en_us: trans.en_us || trans.zh_cn
        });
      }

      return { entries, errors };
    }

    // Otherwise, top-level keys are translation keys:
    // e.g. { "item.ruby": "红宝石" } or { "item.ruby": { zh_cn: "红宝石", en_us: "Ruby" } }
    for (const [key, val] of Object.entries(obj)) {
      const trimmedKey = key.trim();
      if (!trimmedKey) continue;

      if (typeof val === 'string') {
        entries.push({
          key: trimmedKey,
          zh_cn: val,
          en_us: val
        });
      } else if (typeof val === 'object' && val !== null && !Array.isArray(val)) {
        const valObj = val as Record<string, unknown>;
        const trans =
          typeof valObj.translations === 'object' && valObj.translations !== null
            ? (valObj.translations as Record<string, unknown>)
            : valObj;
        const zh = String(
          trans.zh_cn ?? trans['zh-CN'] ?? trans.zh_CN ?? trans.zh ?? trans.translation ?? ''
        );
        const en = String(trans.en_us ?? trans['en-US'] ?? trans.en_US ?? trans.en ?? '');
        entries.push({
          key: trimmedKey,
          zh_cn: zh,
          en_us: en || zh
        });
      } else {
        errors.push(`键 "${trimmedKey}" 的值不是有效的文本或翻译对象。`);
      }
    }

    return { entries, errors };
  }

  errors.push('JSON 必须是对象或数组。');
  return { entries, errors };
}

export function parseLanguageText(rawText: string): { entries: ParsedLanguageEntry[]; errors: string[] } {
  if (!rawText.trim()) {
    return { entries: [], errors: [] };
  }
  const trimmed = rawText.trim();
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed);
      return parseJsonLanguageEntries(parsed);
    } catch (err) {
      return {
        entries: [],
        errors: [`JSON 解析错误: ${err instanceof Error ? err.message : String(err)}`]
      };
    }
  } else {
    return parseCsvLanguageEntries(trimmed);
  }
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

    const { entries, errors } = parseLanguageText(rawText);

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

import React, { useDeferredValue, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle, AlertTriangle, Box, CheckCircle2, CircleDashed,
  Copy, CornerDownRight, FileJson, FileText,
  Image, Info, Layers, Link2, ListFilter,
  Music, PackageOpen, Palette, RefreshCw, Search,
  SlidersHorizontal, Sparkles, Tag, Upload, XCircle
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { assetRecordsFromProjection, AssetCategory, AssetRecord, AssetValidationStatus } from '../types/assets';
import type { AssetImportPreview, AssetMovePreview, AssetProjectionHealthSummary } from '../types/contract';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { assetImportBridge, type AssetImportSelectionGrant } from '../bridge/assetImportBridge';
import { t } from '../i18n';

type BrowserMode = 'ready' | 'empty' | 'loading' | 'error';
type CategoryFilter = 'all' | AssetCategory;
type HealthFilter = 'all' | 'issues' | 'errors' | 'unused' | 'duplicates';
type SortField = 'updated' | 'name' | 'references' | 'size';

interface AssetImportReviewState {
  readonly grant: AssetImportSelectionGrant;
  readonly targetRelativePath: string;
  readonly preview: AssetImportPreview | null;
  readonly busy: boolean;
  readonly error: string | null;
}

interface AssetMoveReviewState {
  readonly asset: AssetRecord;
  readonly targetRelativePath: string;
  readonly preview: AssetMovePreview | null;
  readonly busy: boolean;
  readonly error: string | null;
}

function assetNamespace(assets: readonly AssetRecord[]): string {
  for (const asset of assets) {
    const match = /(?:^|\/)assets\/([^/]+)\//.exec(asset.path);
    if (match?.[1]) return match[1];
  }
  return 'mod';
}

function defaultImportTarget(fileName: string, namespace: string): string {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.png') || lower.endsWith('.jpg') || lower.endsWith('.jpeg')) {
    return `assets/${namespace}/textures/imported/${fileName}`;
  }
  if (lower.endsWith('.ogg') || lower.endsWith('.wav')) return `assets/${namespace}/sounds/${fileName}`;
  if (lower.endsWith('.bbmodel')) return `assets/${namespace}/models/imported/${fileName}`;
  if (lower.endsWith('.lang')) return `assets/${namespace}/lang/${fileName}`;
  if (lower.endsWith('.zip')) return `resourcepacks/${fileName}`;
  return `assets/${namespace}/models/imported/${fileName}`;
}

interface CategoryConfig {
  readonly id: CategoryFilter;
  readonly label: string;
  readonly icon: React.ComponentType<{ size?: number; className?: string }>;
  readonly description: string;
}

const CATEGORY_ITEMS: readonly CategoryConfig[] = [
  { id: 'all', label: '全部资产', icon: ListFilter, description: '工作区全部资源与定义' },
  { id: 'model', label: '模型 (BBModel)', icon: Box, description: 'Blockbench 与实体/方块模型' },
  { id: 'texture', label: '材质贴图', icon: Palette, description: '16x16 / 32x32 纹理' },
  { id: 'animation', label: '动作骨骼', icon: Sparkles, description: '关键帧与动画驱动' },
  { id: 'language', label: '语言包', icon: FileText, description: '多语言翻译映射' },
  { id: 'sound', label: '声音音效', icon: Music, description: '事件音频与音效剪辑' },
  { id: 'resource_pack', label: '资源包', icon: PackageOpen, description: '独立导出与打包' },
  { id: 'blockstate', label: '方块状态', icon: FileJson, description: '方块模型状态映射' },
  { id: 'other', label: '其他', icon: FileJson, description: '工作区中的其他受支持文件' }
];

function modeForScenario(scenarioId: string): BrowserMode {
  if (scenarioId === 'empty-workspace') return 'empty';
  if (scenarioId === 'loading-workbench') return 'loading';
  if (scenarioId === 'external-process-exited' || scenarioId === 'validation-failed') return 'error';
  return 'ready';
}

function AssetCategoryIcon({ category, size = 16, className }: { category?: AssetCategory; size?: number; className?: string }) {
  if (category === 'model') return <Box size={size} className={className} aria-hidden="true" />;
  if (category === 'texture') return <Image size={size} className={className} aria-hidden="true" />;
  if (category === 'animation') return <Sparkles size={size} className={className} aria-hidden="true" />;
  if (category === 'sound') return <Music size={size} className={className} aria-hidden="true" />;
  if (category === 'language') return <FileText size={size} className={className} aria-hidden="true" />;
  if (category === 'resource_pack') return <PackageOpen size={size} className={className} aria-hidden="true" />;
  return <FileJson size={size} className={className} aria-hidden="true" />;
}

function StatusIcon({ status, size = 12 }: { status: AssetValidationStatus; size?: number }) {
  if (status === 'ready') return <CheckCircle2 size={size} aria-hidden="true" />;
  if (status === 'warning') return <AlertTriangle size={size} aria-hidden="true" />;
  if (status === 'error') return <XCircle size={size} aria-hidden="true" />;
  return <CircleDashed size={size} aria-hidden="true" />;
}

function statusClass(status: AssetValidationStatus) {
  return status === 'ready' ? 'green' : status === 'warning' ? 'amber' : status === 'error' ? 'red' : 'blue';
}

function formatDate(value?: string) {
  if (!value) return '未提供';
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value));
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const AssetBrowserView: React.FC = () => {
  const { state, listAssets, previewAssetImport, importAsset, previewAssetMove, moveAsset } = useWorkbench();
  const [assets, setAssets] = useState<AssetRecord[]>([]);
  const [healthSummary, setHealthSummary] = useState<AssetProjectionHealthSummary | null>(null);
  const [assetLoadState, setAssetLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [reloadToken, setReloadToken] = useState(0);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [healthFilter, setHealthFilter] = useState<HealthFilter>('all');
  const [sort, setSort] = useState<SortField>('updated');
  const [selectedId, setSelectedId] = useState('');
  const [modeOverride, setModeOverride] = useState<BrowserMode | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [openingBlockbench, setOpeningBlockbench] = useState(false);
  const [copiedId, setCopiedId] = useState(false);
  const [importReview, setImportReview] = useState<AssetImportReviewState | null>(null);
  const [moveReview, setMoveReview] = useState<AssetMoveReviewState | null>(null);

  const scenarioMode = modeForScenario(state.currentScenarioId);
  const mode = modeOverride ?? (state.currentScenarioId !== 'native'
    ? scenarioMode
    : assetLoadState === 'loading' ? 'loading' : assetLoadState === 'error' ? 'error' : assets.length === 0 ? 'empty' : 'ready');
  const deferredQuery = useDeferredValue(query);

  useEffect(() => {
    setModeOverride(null);
    if (state.currentScenarioId !== 'native' && scenarioMode !== 'ready') {
      setAssets([]);
      setHealthSummary(null);
      setAssetLoadState(scenarioMode === 'error' ? 'error' : scenarioMode === 'loading' ? 'loading' : 'ready');
      return;
    }

    let active = true;
    setAssetLoadState('loading');
    void listAssets().then((projection) => {
      if (!active) return;
      if (!projection) {
        setAssets([]);
        setHealthSummary(null);
        setAssetLoadState('error');
        return;
      }
      setAssets(assetRecordsFromProjection(projection));
      setHealthSummary(projection.health);
      setAssetLoadState('ready');
    }).catch(() => {
      if (!active) return;
      setAssets([]);
      setAssetLoadState('error');
    });
    return () => {
      active = false;
    };
  }, [listAssets, reloadToken, scenarioMode, state.currentScenarioId]);

  useEffect(() => {
    if (!selectedId || !assets.some((asset) => asset.id === selectedId)) {
      setSelectedId(assets[0]?.id ?? '');
    }
  }, [assets, selectedId]);

  const filteredAssets = useMemo(() => {
    const normalized = deferredQuery.trim().toLocaleLowerCase();
    return assets
      .filter((asset) => category === 'all' || asset.category === category)
      .filter((asset) => healthFilter === 'all'
        || (healthFilter === 'issues' && asset.validation !== 'ready')
        || (healthFilter === 'errors' && asset.validation === 'error')
        || (healthFilter === 'unused' && asset.unused === true)
        || (healthFilter === 'duplicates' && asset.duplicateContent === true))
      .filter((asset) => {
        if (!normalized) return true;
        return [asset.name, asset.path, asset.categoryLabel, asset.id, asset.format, asset.sourceLabel]
          .some((val) => val.toLocaleLowerCase().includes(normalized));
      })
      .sort((a, b) => {
        if (sort === 'name') return a.name.localeCompare(b.name);
        if (sort === 'references') return (b.inboundCount ?? b.references.length) - (a.inboundCount ?? a.references.length);
        if (sort === 'size') return b.sizeBytes - a.sizeBytes;
        return (b.updatedAt ?? '').localeCompare(a.updatedAt ?? '');
      });
  }, [assets, category, deferredQuery, healthFilter, sort]);

  const selectedAsset = useMemo(() => {
    return filteredAssets.find((asset) => asset.id === selectedId) ?? filteredAssets[0] ?? null;
  }, [filteredAssets, selectedId]);

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { all: assets.length };
    for (const asset of assets) {
      counts[asset.category] = (counts[asset.category] ?? 0) + 1;
    }
    return counts;
  }, [assets]);

  const copyStableId = (id: string) => {
    navigator.clipboard?.writeText(id).catch(() => {});
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 1600);
  };

  const beginMove = (asset: AssetRecord) => {
    setMoveReview({ asset, targetRelativePath: asset.path, preview: null, busy: false, error: null });
  };

  const runMovePreview = async () => {
    const current = moveReview;
    if (!current) return;
    setMoveReview({ ...current, busy: true, error: null, preview: null });
    try {
      const preview = await previewAssetMove(current.asset.id, current.targetRelativePath);
      setMoveReview({ ...current, busy: false, preview, error: null });
    } catch (error) {
      setMoveReview({ ...current, busy: false, preview: null,
        error: error instanceof Error ? error.message : '资产移动预览失败。' });
    }
  };

  const commitMove = async () => {
    const current = moveReview;
    const preview = current?.preview;
    if (!current || !preview?.canApply) return;
    setMoveReview({ ...current, busy: true, error: null });
    try {
      const result = await moveAsset(preview.planToken);
      if (result.status !== 'committed') {
        setMoveReview({ ...current, busy: false,
          error: t(result.diagnostics[0]?.message) || '资产移动未提交。' });
        return;
      }
      setMoveReview(null);
      setNotice(`已移动到 ${preview.targetRelativePath}；更新 ${preview.referenceCount} 条引用并创建恢复点。`);
      setSelectedId('');
      setReloadToken((token) => token + 1);
    } catch (error) {
      setMoveReview({ ...current, busy: false,
        error: error instanceof Error ? error.message : '资产移动失败。' });
    }
  };

  const runImportPreview = async (grant: AssetImportSelectionGrant, targetRelativePath: string) => {
    setImportReview({ grant, targetRelativePath, preview: null, busy: true, error: null });
    try {
      const preview = await previewAssetImport(grant.id, targetRelativePath);
      setImportReview({ grant, targetRelativePath, preview, busy: false, error: null });
    } catch (error) {
      setImportReview({
        grant,
        targetRelativePath,
        preview: null,
        busy: false,
        error: error instanceof Error ? error.message : '资产导入预览失败。'
      });
    }
  };

  const commitImport = async () => {
    const current = importReview;
    const preview = current?.preview;
    if (!current || !preview?.canApply) return;
    setImportReview({ ...current, busy: true, error: null });
    try {
      const result = await importAsset(preview.planToken, preview.conflict === 'REPLACE');
      if (result.status !== 'committed') {
        setImportReview({ ...current, busy: false,
          error: t(result.diagnostics[0]?.message) || '资产导入未提交。' });
        return;
      }
      setImportReview(null);
      setNotice(preview.conflict === 'REPLACE'
        ? `已安全替换 ${preview.targetRelativePath}；已创建恢复点。`
        : `已导入 ${preview.targetRelativePath}；已创建恢复点。`);
      setReloadToken((token) => token + 1);
    } catch (error) {
      setImportReview({ ...current, busy: false,
        error: error instanceof Error ? error.message : '资产导入失败。' });
    }
  };

  const beginImport = async (replacement?: AssetRecord) => {
    try {
      const grant = await assetImportBridge.selectSource();
      if (grant.cancelled) return;
      const target = replacement?.path ?? defaultImportTarget(grant.fileName, assetNamespace(assets));
      await runImportPreview(grant, target);
    } catch (error) {
      setNotice(error instanceof Error ? `无法选择导入文件：${error.message}` : '无法选择导入文件。');
    }
  };

  const openInBlockbench = async (asset: AssetRecord) => {
    setOpeningBlockbench(true);
    try {
      const result = await blockbenchBridge.openAsset(asset.id);
      if (result.state === 'running') {
        setNotice(`Blockbench 桥接就绪：已打开模型 ${asset.name}。`);
      } else if (result.diagnosticCode === 'BLOCKBENCH_NOT_CONFIGURED') {
        setNotice('尚未配置 Blockbench，可在应用设置中选择安装位置。');
      } else {
        setNotice(`Blockbench 无法打开该资产（${result.diagnosticCode ?? result.state}）。`);
      }
    } catch (error) {
      setNotice(error instanceof Error ? `Blockbench 桥接调用失败：${error.message}` : 'Blockbench 桥接调用失败。');
    } finally {
      setOpeningBlockbench(false);
    }
  };

  if (mode === 'loading') {
    return <AssetStateView mode="loading" query={query} setQuery={setQuery} />;
  }
  if (mode === 'error') {
    return <AssetStateView mode="error" query={query} setQuery={setQuery} onRetry={() => {
      setModeOverride(null);
      setReloadToken((token) => token + 1);
    }} />;
  }
  if (mode === 'empty') {
    return <AssetStateView mode="empty" query={query} setQuery={setQuery} onRetry={() => void beginImport()} />;
  }

  return (
    <section className="stage2-view asset-browser-view animate-fade-in" data-testid="asset-browser">
      <AssetHeader
        query={query}
        setQuery={setQuery}
        totalAssets={assets.length}
        filteredCount={filteredAssets.length}
      />

      <div className="asset-browser-body">
        {/* Left Category Rail */}
        <aside className="asset-category-panel" aria-label="资产分类">
          <div className="asset-panel-label">
            <Layers size={13} aria-hidden="true" />
            <span>分类导航</span>
          </div>

          <nav className="asset-category-list" aria-label="资产类型过滤器">
            {CATEGORY_ITEMS.map((item) => {
              const Icon = item.icon;
              const count = categoryCounts[item.id] ?? 0;
              const active = category === item.id;
              return (
                <button
                  type="button"
                  key={item.id}
                  className={`asset-category-button${active ? ' is-active' : ''}`}
                  aria-pressed={active}
                  data-testid={`asset-category-${item.id}`}
                  onClick={() => {
                    setCategory(item.id);
                    const firstInCat = assets.find(a => item.id === 'all' || a.category === item.id);
                    if (firstInCat) setSelectedId(firstInCat.id);
                  }}
                  title={item.description}
                >
                  <Icon size={14} aria-hidden="true" />
                  <span className="asset-cat-label">{item.label}</span>
                  <span className="asset-cat-badge">{count}</span>
                </button>
              );
            })}
          </nav>

          <div className="asset-health-panel" data-testid="asset-health-panel" aria-label="资产健康筛选">
            <div className="asset-panel-label">
              <AlertTriangle size={13} aria-hidden="true" />
              <span>资产健康</span>
            </div>
            <div className="asset-health-summary" data-testid="asset-health-summary">
              <span><strong>{healthSummary?.errorAssets ?? 0}</strong> 错误</span>
              <span><strong>{healthSummary?.warningAssets ?? 0}</strong> 警告</span>
              <span><strong>{healthSummary?.unusedAssets ?? 0}</strong> 未使用</span>
            </div>
            <div className="asset-health-filters">
              {([
                ['all', '全部'],
                ['issues', '有问题'],
                ['errors', '错误'],
                ['unused', '静态未引用'],
                ['duplicates', '重复内容']
              ] as const).map(([id, label]) => (
                <button
                  type="button"
                  key={id}
                  className={healthFilter === id ? 'is-active' : ''}
                  aria-pressed={healthFilter === id}
                  data-testid={`asset-health-${id}`}
                  onClick={() => setHealthFilter(id)}
                >
                  {label}
                </button>
              ))}
            </div>
            {(healthSummary?.missingReferences ?? 0) > 0 && (
              <div className="asset-health-missing" role="status">
                <AlertCircle size={12} aria-hidden="true" />
                <span>{healthSummary?.missingReferences} 条缺失引用</span>
              </div>
            )}
            {(healthSummary?.duplicateGroups ?? 0) > 0 && (
              <span className="asset-health-summary-item" data-testid="asset-health-duplicate-summary">
                重复组 {healthSummary?.duplicateGroups} / 资产 {healthSummary?.duplicateAssets}
              </span>
            )}
          </div>

          <div className="asset-category-hint">
            <Link2 size={13} aria-hidden="true" />
            <span>引用关系随工作区修订保存。</span>
          </div>
        </aside>

        {/* Middle Asset List Panel */}
        <main className="asset-list-panel" aria-label="资产内容列表">
          <div className="asset-list-toolbar">
            <div className="asset-list-meta">
              <span className="asset-result-count">
                <strong>{filteredAssets.length}</strong> 项可用资产
              </span>
              {category !== 'all' && (
                <span className="asset-filter-tag">
                  <Tag size={10} aria-hidden="true" />
                  {CATEGORY_ITEMS.find(c => c.id === category)?.label}
                </span>
              )}
            </div>

            <div className="asset-toolbar-controls">
              <label className="asset-sort-control">
                <SlidersHorizontal size={13} aria-hidden="true" />
                <span className="sr-only">排序</span>
                <select
                  aria-label="资产排序"
                  value={sort}
                  onChange={(e) => setSort(e.target.value as SortField)}
                >
                  <option value="updated">最近更新</option>
                  <option value="name">资产名称</option>
                  <option value="references">引用数</option>
                  <option value="size">文件大小</option>
                </select>
              </label>

              <button
                type="button"
                className="asset-import-inline-btn btn-secondary"
                onClick={() => void beginImport()}
                data-testid="asset-import-button"
                title="导入外部模型或贴图"
              >
                <Upload size={12} aria-hidden="true" />
                <span>导入</span>
              </button>
            </div>
          </div>

          {filteredAssets.length === 0 ? (
            <div className="asset-no-results" data-testid="asset-browser-no-results" role="status">
              <div className="asset-empty-icon-wrap">
                <Search size={22} aria-hidden="true" />
              </div>
              <strong>没有匹配的资产</strong>
              <span>当前搜索条件或分类筛选下未找到相关文件。</span>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => {
                  setQuery('');
                  setCategory('all');
                  setHealthFilter('all');
                }}
              >
                <XCircle size={13} aria-hidden="true" />
                <span>清除筛选</span>
              </button>
            </div>
          ) : (
            <div className="asset-card-grid" aria-label="资产列表" role="list">
              {filteredAssets.map((asset) => (
                <AssetCard
                  key={asset.id}
                  asset={asset}
                  selected={selectedAsset?.id === asset.id}
                  onSelect={() => setSelectedId(asset.id)}
                />
              ))}
            </div>
          )}
        </main>

        {/* Right Details & Diagnostics Panel */}
        <AssetDetails
          asset={selectedAsset}
          notice={notice}
          copiedId={copiedId}
          openingBlockbench={openingBlockbench}
          onCopyId={copyStableId}
          onImport={(asset) => void beginImport(asset)}
          onMove={beginMove}
          onOpenBlockbench={openInBlockbench}
          onDismissNotice={() => setNotice(null)}
        />
      </div>

      {importReview && (
        <AssetImportReview
          state={importReview}
          onTargetChange={(targetRelativePath) => setImportReview({
            ...importReview,
            targetRelativePath,
            preview: null,
            error: null
          })}
          onPreview={() => void runImportPreview(importReview.grant, importReview.targetRelativePath)}
          onCommit={() => void commitImport()}
          onCancel={() => setImportReview(null)}
        />
      )}

      {moveReview && (
        <AssetMoveReview
          state={moveReview}
          onTargetChange={(targetRelativePath) => setMoveReview({
            ...moveReview,
            targetRelativePath,
            preview: null,
            error: null
          })}
          onPreview={() => void runMovePreview()}
          onCommit={() => void commitMove()}
          onCancel={() => setMoveReview(null)}
        />
      )}

    </section>
  );
};

const AssetMoveReview: React.FC<{
  state: AssetMoveReviewState;
  onTargetChange: (targetRelativePath: string) => void;
  onPreview: () => void;
  onCommit: () => void;
  onCancel: () => void;
}> = ({ state, onTargetChange, onPreview, onCommit, onCancel }) => {
  const preview = state.preview;
  return (
    <div className="asset-import-review-backdrop" role="presentation">
      <section className="asset-import-review" role="dialog" aria-modal="true"
        aria-label="资产重命名或移动预览" data-testid="asset-move-review">
        <div className="asset-import-review-heading">
          <div>
            <strong>重命名 / 移动资产</strong>
            <span>先审阅新路径和每一条引用改写；任何无法安全改写的引用都会阻止提交。</span>
          </div>
          <button type="button" className="asset-clear-button" onClick={onCancel} aria-label="取消移动">
            <XCircle size={16} />
          </button>
        </div>

        <div className="asset-import-review-source">
          <span>当前资产</span>
          <strong data-testid="asset-move-source">{state.asset.path}</strong>
          <small>{state.asset.categoryLabel}</small>
        </div>

        <label className="asset-import-target-field">
          <span>新的工作区路径</span>
          <input data-testid="asset-move-target" value={state.targetRelativePath}
            onChange={(event) => onTargetChange(event.target.value)} disabled={state.busy} />
        </label>

        <div className="asset-import-review-actions">
          <button type="button" className="btn-secondary" onClick={onPreview} disabled={state.busy}
            data-testid="asset-move-preview">
            <RefreshCw size={13} aria-hidden="true" />
            <span>{preview ? '重新预览' : '预览影响'}</span>
          </button>
          <button type="button" className="btn-primary" onClick={onCommit}
            disabled={state.busy || !preview?.canApply} data-testid="asset-move-commit">
            <CornerDownRight size={13} aria-hidden="true" />
            <span>确认移动并更新引用</span>
          </button>
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={state.busy}>取消</button>
        </div>

        {state.busy && <div className="asset-import-review-status" role="status">正在计算引用影响…</div>}
        {state.error && <div className="asset-import-review-error" role="alert">{state.error}</div>}
        {preview && (
          <div className="asset-import-preview-summary" data-testid="asset-move-preview-summary">
            <div><span>旧路径</span><code>{preview.sourceRelativePath}</code></div>
            <div><span>新路径</span><code>{preview.targetRelativePath}</code></div>
            <div><span>新稳定标识</span><code data-testid="asset-move-target-id">{preview.targetAssetId}</code></div>
            <div><span>受影响引用</span><strong data-testid="asset-move-reference-count">{preview.referenceCount}</strong></div>
            {preview.rewrites.length > 0 && (
              <div className="asset-move-rewrites" data-testid="asset-move-rewrites">
                <span>精确改写</span>
                <ul>
                  {preview.rewrites.map((rewrite) => (
                    <li key={`${rewrite.sourcePath}:${rewrite.sourcePointer}`}>
                      <code>{rewrite.sourcePath}{rewrite.sourcePointer}</code>
                      <small>{rewrite.oldRawValue} → {rewrite.newRawValue}</small>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {preview.issueCodes.length > 0 && (
              <div className="asset-import-issue-codes" data-testid="asset-move-issues">
                <span>阻断 / 检查项</span>
                <div>{preview.issueCodes.map((code) => <code key={code}>{code}</code>)}</div>
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
};

const AssetImportReview: React.FC<{
  state: AssetImportReviewState;
  onTargetChange: (targetRelativePath: string) => void;
  onPreview: () => void;
  onCommit: () => void;
  onCancel: () => void;
}> = ({ state, onTargetChange, onPreview, onCommit, onCancel }) => {
  const preview = state.preview;
  const conflictLabel = preview?.conflict === 'REPLACE'
    ? '将替换现有资产'
    : preview?.conflict === 'IDENTICAL' ? '目标内容已相同' : '新建资产';
  return (
    <div className="asset-import-review-backdrop" role="presentation">
      <section className="asset-import-review" role="dialog" aria-modal="true"
        aria-label="资产导入预览" data-testid="asset-import-review">
        <div className="asset-import-review-heading">
          <div>
            <strong>导入资产</strong>
            <span>先检查目标路径、冲突和重复内容，再写入工作区。</span>
          </div>
          <button type="button" className="asset-clear-button" onClick={onCancel} aria-label="取消导入">
            <XCircle size={16} />
          </button>
        </div>

        <div className="asset-import-review-source">
          <span>来源文件</span>
          <strong data-testid="asset-import-source">{state.grant.fileName}</strong>
          <small>{formatBytes(state.grant.size)}</small>
        </div>

        <label className="asset-import-target-field">
          <span>工作区目标路径</span>
          <input data-testid="asset-import-target" value={state.targetRelativePath}
            onChange={(event) => onTargetChange(event.target.value)} disabled={state.busy} />
        </label>

        <div className="asset-import-review-actions">
          <button type="button" className="btn-secondary" onClick={onPreview} disabled={state.busy}
            data-testid="asset-import-preview">
            <RefreshCw size={13} aria-hidden="true" />
            <span>{preview ? '重新预览' : '预览导入'}</span>
          </button>
          <button type="button" className="btn-primary" onClick={onCommit}
            disabled={state.busy || !preview?.canApply} data-testid="asset-import-commit">
            <Upload size={13} aria-hidden="true" />
            <span>{preview?.conflict === 'REPLACE' ? '确认替换并导入' : '确认导入'}</span>
          </button>
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={state.busy}
            data-testid="asset-import-cancel">取消</button>
        </div>

        {state.busy && <div className="asset-import-review-status" role="status">正在校验导入计划…</div>}
        {state.error && <div className="asset-import-review-error" role="alert" data-testid="asset-import-error">
          {state.error}
        </div>}
        {preview && (
          <div className="asset-import-preview-summary" data-testid="asset-import-preview-summary">
            <div><span>操作</span><strong data-testid="asset-import-conflict">{conflictLabel}</strong></div>
            <div><span>目标</span><code>{preview.targetRelativePath}</code></div>
            <div><span>来源 SHA-256</span><code>{preview.sourceSha256.slice(0, 16)}…</code></div>
            {preview.targetSha256 && <div><span>现有 SHA-256</span><code>{preview.targetSha256.slice(0, 16)}…</code></div>}
            {preview.duplicatePaths.length > 0 && (
              <div className="asset-import-duplicates" data-testid="asset-import-duplicates">
                <span>相同内容</span>
                <div>{preview.duplicatePaths.map((path) => <code key={path}>{path}</code>)}</div>
              </div>
            )}
            {preview.issueCodes.length > 0 && (
              <div className="asset-import-issue-codes">
                <span>检查项</span>
                <div>{preview.issueCodes.map((code) => <code key={code}>{code}</code>)}</div>
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
};

/* Header Section with live indexing status and search */
const AssetHeader: React.FC<{
  query: string;
  setQuery: (q: string) => void;
  totalAssets: number;
  filteredCount: number;
}> = ({ query, setQuery, totalAssets, filteredCount }) => {
  return (
    <header className="stage2-view-header asset-browser-header">
      <div className="stage2-view-title">
        <Palette size={20} aria-hidden="true" />
        <div>
          <h2>资产与模型工作台</h2>
          <span>资产与 Blockbench 集成 · 模型、纹理、动画与资源包 · 引用关系可追溯</span>
        </div>
      </div>

      <div className="asset-header-actions">
        <label className="asset-search-field">
          <Search size={14} aria-hidden="true" />
          <span className="sr-only">搜索资产</span>
          <input
            data-testid="asset-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索名称、路径或标识…"
            aria-label="搜索资产"
          />
          {query && (
            <button
              type="button"
              className="asset-clear-button"
              onClick={() => setQuery('')}
              aria-label="清除搜索"
            >
              <XCircle size={13} />
            </button>
          )}
        </label>

        <span className="connection-state" title="资产索引与底层虚拟文件系统保持同步">
          <span aria-hidden="true" />
          <span>已索引 ({filteredCount}/{totalAssets})</span>
        </span>
      </div>
    </header>
  );
};

/* State placeholder view for loading / error / empty */
const AssetStateView: React.FC<{
  mode: Exclude<BrowserMode, 'ready'>;
  query: string;
  setQuery: (q: string) => void;
  onRetry?: () => void;
}> = ({ mode, query, setQuery, onRetry }) => {
  return (
    <section className="stage2-view asset-browser-view animate-fade-in" data-testid="asset-browser">
      <header className="stage2-view-header asset-browser-header">
        <div className="stage2-view-title">
          <Palette size={20} aria-hidden="true" />
          <div>
            <h2>资产与模型工作台</h2>
            <span>资产与 Blockbench 集成 · 模型、纹理、动画与资源包 · 引用关系可追溯</span>
          </div>
        </div>
        <div className="asset-header-actions">
          <label className="asset-search-field">
            <Search size={14} aria-hidden="true" />
            <span className="sr-only">搜索资产</span>
            <input
              data-testid="asset-search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索名称、路径或标识…"
              disabled
            />
          </label>
        </div>
      </header>

      <div
        className={`asset-state-panel${mode === 'error' ? ' asset-state-error' : ''}`}
        data-testid={`asset-browser-${mode}`}
        role={mode === 'error' ? 'alert' : mode === 'loading' ? 'status' : undefined}
        aria-live={mode === 'loading' ? 'polite' : undefined}
      >
        {mode === 'loading' ? (
          <>
            <div className="asset-state-spinner-box">
              <RefreshCw className="asset-state-spinner" size={28} aria-hidden="true" />
            </div>
            <strong>正在读取工作区资产</strong>
            <span>正在建立路径、引用和校验投影…</span>
          </>
        ) : mode === 'error' ? (
          <>
            <div className="asset-state-icon-box error">
              <XCircle size={30} aria-hidden="true" />
            </div>
            <strong>资产投影暂时不可用</strong>
            <span>工作区桥接返回了不完整的资产索引，原始文件不会被修改。</span>
            <button type="button" className="btn-secondary" onClick={onRetry}>
              <RefreshCw size={13} aria-hidden="true" />
              <span>重新读取</span>
            </button>
          </>
        ) : (
          <>
            <div className="asset-state-icon-box empty">
              <PackageOpen size={32} aria-hidden="true" />
            </div>
            <strong>工作区还没有资产</strong>
            <span>导入 Blockbench 模型、纹理或一个独立资源包，资产会自动建立引用关系。</span>
            <button
              type="button"
              className="btn-primary"
              data-testid="asset-import-empty"
              onClick={onRetry}
            >
              <Upload size={14} aria-hidden="true" />
              <span>导入第一个资产</span>
            </button>
          </>
        )}
      </div>
    </section>
  );
};

/* Individual Asset Card in the grid */
const AssetCard: React.FC<{
  asset: AssetRecord;
  selected: boolean;
  onSelect: () => void;
}> = ({ asset, selected, onSelect }) => {
  return (
    <button
      type="button"
      className={`asset-card${selected ? ' is-selected' : ''}`}
      data-testid={`asset-card-${asset.id}`}
      data-asset-id={asset.id}
      aria-pressed={selected}
      onClick={onSelect}
    >
      <div className="asset-card-main">
        <div className="asset-card-preview">
          <AssetCategoryIcon category={asset.category} size={20} />
          <span className="asset-card-format-tag">{asset.format}</span>
        </div>

        <div className="asset-card-copy">
          <div className="asset-card-header-row">
            <strong title={asset.name}>{asset.name}</strong>
            <span className={`badge badge-${statusClass(asset.validation)} asset-status-badge`}>
              <StatusIcon status={asset.validation} size={11} />
              {asset.validationLabel}
            </span>
          </div>

          <div className="asset-card-category-row">
            <span>{asset.categoryLabel}</span>
            <span className="asset-dot">·</span>
            <span>{asset.sourceLabel}</span>
          </div>

          <small className="asset-card-path" title={asset.path}>
            {asset.path}
          </small>
        </div>
      </div>

      <div className="asset-card-footer">
        <span className="asset-card-size">{asset.size}</span>
        <span className="asset-card-refs" title={`被 ${asset.references.length} 个对象引用`}>
          <Link2 size={11} aria-hidden="true" />
          <span>{asset.references.length}</span>
        </span>
      </div>
    </button>
  );
};

/* Right Sidebar Detail Panel */
const AssetDetails: React.FC<{
  asset: AssetRecord | null;
  notice: string | null;
  copiedId: boolean;
  openingBlockbench: boolean;
  onCopyId: (id: string) => void;
  onImport: (asset: AssetRecord) => void;
  onMove: (asset: AssetRecord) => void;
  onOpenBlockbench: (asset: AssetRecord) => void;
  onDismissNotice: () => void;
}> = ({
  asset,
  notice,
  copiedId,
  openingBlockbench,
  onCopyId,
  onImport,
  onMove,
  onOpenBlockbench,
  onDismissNotice
}) => {
  if (!asset) {
    return (
      <aside className="asset-details-panel" aria-label="资产详情">
        <div className="asset-details-empty">
          <Info size={22} aria-hidden="true" />
          <span>选择一项资产查看详情。</span>
        </div>
      </aside>
    );
  }

  return (
    <aside className="asset-details-panel" aria-label="资产详情" data-testid="asset-details">
      {/* Detail Header */}
      <div className="asset-details-heading">
        <div className="asset-details-icon">
          <AssetCategoryIcon category={asset.category} size={20} />
        </div>
        <div className="asset-details-title-wrap">
          <strong title={asset.name}>{asset.name}</strong>
          <div className="asset-details-sub">
            <small>{asset.categoryLabel}</small>
            <span className="asset-dot">·</span>
            <small>{asset.sourceLabel}</small>
          </div>
        </div>
      </div>

      <div className={`badge badge-${statusClass(asset.validation)} asset-details-status`}>
        <StatusIcon status={asset.validation} size={12} />
        <span>{asset.validationLabel}</span>
      </div>

      {/* Surface Preview Canvas */}
      <div className="asset-preview-surface" aria-label="资产预览">
        <div className="asset-preview-icon-cluster">
          <AssetCategoryIcon category={asset.category} size={42} />
        </div>
        <div className="asset-preview-specs">
          <span className="asset-preview-format">{asset.format}</span>
          <small className="asset-preview-dimensions">{asset.dimensions ?? '无预览尺寸'}</small>
        </div>
      </div>

      {/* Structured Metadata DL */}
      <dl className="asset-metadata">
        <div className="asset-metadata-row asset-id-row">
          <dt>稳定标识</dt>
          <dd>
            <code data-testid="asset-stable-id" title={asset.id}>
              {asset.id}
            </code>
            <button
              type="button"
              className="asset-id-copy-btn"
              onClick={() => onCopyId(asset.id)}
              title="复制稳定标识"
              aria-label="复制稳定标识"
            >
              {copiedId ? <CheckCircle2 size={12} className="text-green" /> : <Copy size={12} />}
            </button>
          </dd>
        </div>
        <div className="asset-metadata-row">
          <dt>路径</dt>
          <dd title={asset.path}><code>{asset.path}</code></dd>
        </div>
        <div className="asset-metadata-row">
          <dt>大小</dt>
          <dd>{asset.size}</dd>
        </div>
        <div className="asset-metadata-row">
          <dt>来源</dt>
          <dd>{asset.sourceLabel}</dd>
        </div>
        <div className="asset-metadata-row">
          <dt>更新时间</dt>
          <dd>{formatDate(asset.updatedAt)}</dd>
        </div>
        <div className="asset-metadata-row">
          <dt>使用状态</dt>
          <dd data-testid="asset-usage-status">
            {asset.unused ? '静态未引用候选' : asset.usageAssessed ? '存在静态入站引用' : '不做静态未使用判定'}
          </dd>
        </div>
      </dl>

      {/* Reference Diagnostics */}
      <div className="asset-reference-section">
        <div className="asset-panel-label">
          <Link2 size={14} aria-hidden="true" />
          <span>入站引用</span>
          <span className="asset-ref-count-badge">{asset.inboundCount ?? asset.references.length}</span>
        </div>

        {asset.references.length === 0 ? (
          <div className="asset-reference-empty">暂无入站引用关系。</div>
        ) : (
          <ul className="asset-reference-list" aria-label="引用该资产的来源">
            {asset.references.map((reference) => (
              <li key={reference} className="asset-reference-item">
                <CornerDownRight size={11} className="asset-ref-arrow" aria-hidden="true" />
                <code title={reference}>{reference}</code>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="asset-reference-section" data-testid="asset-outgoing-references">
        <div className="asset-panel-label">
          <CornerDownRight size={14} aria-hidden="true" />
          <span>出站依赖</span>
          <span className="asset-ref-count-badge">{asset.outboundCount ?? asset.outgoingReferences?.length ?? 0}</span>
        </div>
        {(asset.outgoingReferences?.length ?? 0) === 0 ? (
          <div className="asset-reference-empty">该资产没有静态出站依赖。</div>
        ) : (
          <ul className="asset-reference-list" aria-label="该资产引用的目标">
            {asset.outgoingReferences?.map((reference) => (
              <li key={reference} className="asset-reference-item">
                <CornerDownRight size={11} className="asset-ref-arrow" aria-hidden="true" />
                <code title={reference}>{reference}</code>
              </li>
            ))}
          </ul>
        )}
      </div>

      {(asset.issueCodes?.length ?? 0) > 0 && (
        <div className="asset-health-issues" data-testid="asset-health-issue-codes">
          <div className="asset-panel-label">
            <AlertTriangle size={14} aria-hidden="true" />
            <span>健康诊断</span>
          </div>
          {asset.issueCodes?.map((code) => <code key={code}>{code}</code>)}
        </div>
      )}

      {(asset.duplicatePaths?.length ?? 0) > 0 && (
        <div className="asset-reference-section" data-testid="asset-duplicate-paths">
          <div className="asset-panel-label">
            <Copy size={14} aria-hidden="true" />
            <span>相同内容</span>
            <span className="asset-ref-count-badge">{asset.duplicatePaths?.length ?? 0}</span>
          </div>
          <ul className="asset-reference-list" aria-label="内容完全相同的其它资产">
            {asset.duplicatePaths?.map((path) => (
              <li key={path} className="asset-reference-item"><code title={path}>{path}</code></li>
            ))}
          </ul>
        </div>
      )}

      {/* Description Summary */}
      <p className="asset-description">{asset.description}</p>

      {/* Action Buttons */}
      <div className="asset-details-actions">
        <button
          type="button"
          className="btn-secondary asset-action-btn"
          onClick={() => onMove(asset)}
          data-testid="asset-move-button"
        >
          <CornerDownRight size={14} aria-hidden="true" />
          <span>重命名 / 移动</span>
        </button>

        <button
          type="button"
          className="btn-secondary asset-action-btn"
          onClick={() => onImport(asset)}
        >
          <Upload size={14} aria-hidden="true" />
          <span>替换文件</span>
        </button>

        <button
          type="button"
          className="btn-primary asset-action-btn"
          disabled={openingBlockbench}
          onClick={() => onOpenBlockbench(asset)}
        >
          <Box size={14} aria-hidden="true" />
          <span>{openingBlockbench ? '正在打开…' : '在 Blockbench 打开'}</span>
        </button>
      </div>

      {/* Notice Message Banner */}
      {notice && (
        <div className="asset-notice" role="status" data-testid="asset-notice">
          <AlertCircle size={14} className="asset-notice-icon" aria-hidden="true" />
          <span className="asset-notice-text">{notice}</span>
          <button
            type="button"
            className="asset-clear-button asset-notice-close"
            aria-label="关闭提示"
            onClick={onDismissNotice}
          >
            <XCircle size={14} />
          </button>
        </div>
      )}
    </aside>
  );
};

import React, { useDeferredValue, useMemo, useState } from 'react';
import {
  AlertCircle, AlertTriangle, Box, CheckCircle2, CircleDashed,
  Copy, CornerDownRight, FileJson, FileText,
  Image, Info, Layers, Link2, ListFilter,
  Music, PackageOpen, Palette, RefreshCw, Search,
  SlidersHorizontal, Sparkles, Tag, Upload, XCircle
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ASSET_FIXTURES, AssetCategory, AssetRecord, AssetValidationStatus } from '../mock/assetFixtures';
import { blockbenchBridge } from '../bridge/blockbenchBridge';

type BrowserMode = 'ready' | 'empty' | 'loading' | 'error';
type CategoryFilter = 'all' | AssetCategory;
type SortField = 'updated' | 'name' | 'references' | 'size';

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
  { id: 'resource_pack', label: '资源包', icon: PackageOpen, description: '独立导出与打包' }
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value));
}

export const AssetBrowserView: React.FC = () => {
  const { state } = useWorkbench();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [sort, setSort] = useState<SortField>('updated');
  const [selectedId, setSelectedId] = useState(ASSET_FIXTURES[0]?.id ?? '');
  const [modeOverride, setModeOverride] = useState<BrowserMode | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [openingBlockbench, setOpeningBlockbench] = useState(false);
  const [copiedId, setCopiedId] = useState(false);

  const mode = modeOverride ?? modeForScenario(state.currentScenarioId);
  const deferredQuery = useDeferredValue(query);

  const filteredAssets = useMemo(() => {
    const normalized = deferredQuery.trim().toLocaleLowerCase();
    return ASSET_FIXTURES
      .filter((asset) => category === 'all' || asset.category === category)
      .filter((asset) => {
        if (!normalized) return true;
        return [asset.name, asset.path, asset.categoryLabel, asset.id, asset.format, asset.sourceLabel]
          .some((val) => val.toLocaleLowerCase().includes(normalized));
      })
      .sort((a, b) => {
        if (sort === 'name') return a.name.localeCompare(b.name);
        if (sort === 'references') return b.references.length - a.references.length;
        if (sort === 'size') return parseFloat(b.size) - parseFloat(a.size);
        return b.updatedAt.localeCompare(a.updatedAt);
      });
  }, [category, deferredQuery, sort]);

  const selectedAsset = useMemo(() => {
    return filteredAssets.find((asset) => asset.id === selectedId) ?? filteredAssets[0] ?? null;
  }, [filteredAssets, selectedId]);

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { all: ASSET_FIXTURES.length };
    for (const asset of ASSET_FIXTURES) {
      counts[asset.category] = (counts[asset.category] ?? 0) + 1;
    }
    return counts;
  }, []);

  const copyStableId = (id: string) => {
    navigator.clipboard?.writeText(id).catch(() => {});
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 1600);
  };

  const importAsset = () => {
    setModeOverride('ready');
    setNotice('资产导入任务已加入工作区管线，校验完成后将自动挂载。');
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
    return <AssetStateView mode="error" query={query} setQuery={setQuery} onRetry={() => setModeOverride('ready')} />;
  }
  if (mode === 'empty') {
    return <AssetStateView mode="empty" query={query} setQuery={setQuery} onRetry={importAsset} />;
  }

  return (
    <section className="stage2-view asset-browser-view animate-fade-in" data-testid="asset-browser">
      <AssetHeader
        query={query}
        setQuery={setQuery}
        totalAssets={ASSET_FIXTURES.length}
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
                    const firstInCat = ASSET_FIXTURES.find(a => item.id === 'all' || a.category === item.id);
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
                onClick={importAsset}
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
          onImport={importAsset}
          onOpenBlockbench={openInBlockbench}
          onDismissNotice={() => setNotice(null)}
        />
      </div>
    </section>
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
  onImport: () => void;
  onOpenBlockbench: (asset: AssetRecord) => void;
  onDismissNotice: () => void;
}> = ({
  asset,
  notice,
  copiedId,
  openingBlockbench,
  onCopyId,
  onImport,
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
      </dl>

      {/* Reference Diagnostics */}
      <div className="asset-reference-section">
        <div className="asset-panel-label">
          <Link2 size={14} aria-hidden="true" />
          <span>引用关系</span>
          <span className="asset-ref-count-badge">{asset.references.length}</span>
        </div>

        {asset.references.length === 0 ? (
          <div className="asset-reference-empty">暂无外部引用关系。</div>
        ) : (
          <ul className="asset-reference-list" aria-label="引用该资产的目标">
            {asset.references.map((reference) => (
              <li key={reference} className="asset-reference-item">
                <CornerDownRight size={11} className="asset-ref-arrow" aria-hidden="true" />
                <code title={reference}>{reference}</code>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Description Summary */}
      <p className="asset-description">{asset.description}</p>

      {/* Action Buttons */}
      <div className="asset-details-actions">
        <button
          type="button"
          className="btn-secondary asset-action-btn"
          onClick={onImport}
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


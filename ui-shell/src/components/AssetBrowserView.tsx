import { tr } from '../i18n/locale';
import { valueLabel } from '../i18n/labels';
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
import type { AssetImportPreview, AssetImportBatchPreview, AssetMovePreview, AssetProjection, AssetProjectionHealthSummary, Diagnostic, LocalizedText } from '../types/contract';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { assetImportBridge, type AssetImportSelectionGrant } from '../bridge/assetImportBridge';
import { t, uiText, useUiLocale, UI_LOCALE } from '../i18n';
import { BlockbenchSetupPanel } from './BlockbenchSetupPanel';
import { BlockbenchTasksPanel } from './BlockbenchTasksPanel';

type BrowserMode = 'ready' | 'empty' | 'loading' | 'error';
type CategoryFilter = 'all' | AssetCategory;
type HealthFilter = 'all' | 'issues' | 'errors' | 'unused' | 'safe-unused' | 'duplicates';
type SortField = 'updated' | 'name' | 'references' | 'size';

function resourceResolutionLabel(resolution: string): string {
  const labels: Record<string, string> = {
  workspace_resolved: uiText("工作区已解析", "Resolved in workspace"), vanilla_resolved: uiText("原版已解析", "Resolved in vanilla"), dependency_resolved: uiText("活动依赖已解析", "Resolved in active dependencies"),
  missing: uiText("已证实缺失", "Confirmed missing"), unverified: uiText("尚未验证", "Not verified"), invalid: uiText("资源格式无效", "Invalid resource format")
  };
  return labels[resolution] ?? uiText('尚未验证', 'Not verified');
}

type AssetMessage = string | LocalizedText | { zh: string; en: string };
const assetMessage = (zh: string, en: string): AssetMessage => ({ zh, en });
function renderAssetMessage(message: AssetMessage): string {
  return typeof message === 'string' ? message : 'key' in message ? t(message) : uiText(message.zh, message.en);
}

interface AssetImportReviewState {
  readonly grant: AssetImportSelectionGrant;
  readonly targetRelativePath: string;
  readonly preview: AssetImportPreview | null;
  readonly busy: boolean;
  readonly error: AssetMessage | null;
}

interface AssetBatchImportReviewState {
  readonly grants: AssetImportSelectionGrant[];
  readonly targetRelativePaths: string[];
  readonly preview: AssetImportBatchPreview | null;
  readonly busy: boolean;
  readonly error: AssetMessage | null;
}

interface AssetMoveReviewState {
  readonly asset: AssetRecord;
  readonly targetRelativePath: string;
  readonly preview: AssetMovePreview | null;
  readonly busy: boolean;
  readonly error: AssetMessage | null;
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

const categoryItems = (): readonly CategoryConfig[] => [
  { id: 'all', label: uiText("全部资产", "All assets"), icon: ListFilter, description: uiText("工作区全部资源与定义", "All workspace resources and definitions") },
  { id: 'model', label: uiText("模型 (BBModel)", "Models (BBModel)"), icon: Box, description: uiText("Blockbench 与实体/方块模型", "Blockbench, entity and block models") },
  { id: 'texture', label: uiText("材质贴图", "Textures"), icon: Palette, description: uiText("16x16 / 32x32 纹理", "16x16 / 32x32 textures") },
  { id: 'animation', label: uiText("动作骨骼", "Animations"), icon: Sparkles, description: uiText("关键帧与动画驱动", "Keyframes and animation drivers") },
  { id: 'language', label: uiText("语言包", "Languages"), icon: FileText, description: uiText("多语言翻译映射", "Translations for multiple languages") },
  { id: 'sound', label: uiText("声音音效", "Sounds"), icon: Music, description: uiText("事件音频与音效剪辑", "Event audio and sound clips") },
  { id: 'resource_pack', label: uiText("资源包", "Resource packs"), icon: PackageOpen, description: uiText("独立导出与打包", "Standalone export and packaging") },
  { id: 'blockstate', label: uiText("方块状态", "Block states"), icon: FileJson, description: uiText("方块模型状态映射", "Block model state mappings") },
  { id: 'other', label: uiText("其他", "Other"), icon: FileJson, description: uiText("工作区中的其他受支持文件", "Other supported workspace files") }
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
  if (!value) return uiText("未提供", "Not provided");
  return new Intl.DateTimeFormat(UI_LOCALE === 'en' ? 'en-US' : 'zh-CN', {
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
  const locale = useUiLocale();
  const CATEGORY_ITEMS = categoryItems();
  const {
    state, listAssets, previewAssetImport, importAsset, previewAssetImportBatch, importAssetBatch,
    previewAssetMove, moveAsset, assetFocusId, setAssetFocusId, runDiagnosticAction
  } = useWorkbench();
  const [assetProjection, setAssetProjection] = useState<AssetProjection | null>(null);
  const assets = useMemo(() => assetProjection ? assetRecordsFromProjection(assetProjection) : [], [assetProjection, locale]);
  const [assetDiagnostics, setAssetDiagnostics] = useState<Diagnostic[]>([]);
  const [healthSummary, setHealthSummary] = useState<AssetProjectionHealthSummary | null>(null);
  const [assetLoadState, setAssetLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [reloadToken, setReloadToken] = useState(0);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [healthFilter, setHealthFilter] = useState<HealthFilter>('all');
  const [sort, setSort] = useState<SortField>('updated');
  const [selectedId, setSelectedId] = useState('');
  const [modeOverride, setModeOverride] = useState<BrowserMode | null>(null);
  const [notice, setNotice] = useState<AssetMessage | null>(null);
  const [openingBlockbench, setOpeningBlockbench] = useState(false);
  const [blockbenchSessionAssetId, setBlockbenchSessionAssetId] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState(false);
  const [importReview, setImportReview] = useState<AssetImportReviewState | null>(null);
  const [batchImportReview, setBatchImportReview] = useState<AssetBatchImportReviewState | null>(null);
  const [moveReview, setMoveReview] = useState<AssetMoveReviewState | null>(null);

  const scenarioMode = modeForScenario(state.currentScenarioId);
  const mode = modeOverride ?? (state.currentScenarioId !== 'native'
    ? scenarioMode
    : assetLoadState === 'loading' ? 'loading' : assetLoadState === 'error' ? 'error' : assets.length === 0 ? 'empty' : 'ready');
  const deferredQuery = useDeferredValue(query);

  useEffect(() => {
    const refresh = () => setReloadToken(value => value + 1);
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, []);

  useEffect(() => {
    setModeOverride(null);
    if (state.currentScenarioId !== 'native' && scenarioMode !== 'ready') {
      setAssetProjection(null);
      setAssetDiagnostics([]);
      setHealthSummary(null);
      setAssetLoadState(scenarioMode === 'error' ? 'error' : scenarioMode === 'loading' ? 'loading' : 'ready');
      return;
    }

    let active = true;
    setAssetLoadState('loading');
    void listAssets().then((projection) => {
      if (!active) return;
      if (!projection) {
        setAssetProjection(null);
        setAssetDiagnostics([]);
        setHealthSummary(null);
        setAssetLoadState('error');
        return;
      }
      setAssetProjection(projection);
      setAssetDiagnostics(projection.diagnostics);
      setHealthSummary(projection.health);
      setAssetLoadState('ready');
    }).catch(() => {
      if (!active) return;
      setAssetProjection(null);
      setAssetDiagnostics([]);
      setAssetLoadState('error');
    });
    return () => {
      active = false;
    };
  }, [listAssets, state.workbench?.workspace.revision, reloadToken, scenarioMode, state.currentScenarioId]);

  useEffect(() => {
    if (!assetFocusId || !assets.some((asset) => asset.id === assetFocusId)) return;
    setQuery('');
    setCategory('all');
    setHealthFilter('all');
    setSelectedId(assetFocusId);
    const target = assetFocusId;
    window.setTimeout(() => {
      const element = document.querySelector(`[data-asset-id="${target}"]`);
      if (element instanceof HTMLElement) {
        element.scrollIntoView({ block: 'nearest' });
        element.focus();
      }
    }, 80);
    setAssetFocusId(null);
  }, [assetFocusId, assets, setAssetFocusId]);

  useEffect(() => {
    // A diagnostic target takes precedence when the asset list first arrives.
    // Otherwise this fallback races the focus effect and replaces its selection.
    if (assetFocusId && assets.some((asset) => asset.id === assetFocusId)) return;
    if (!selectedId || !assets.some((asset) => asset.id === selectedId)) {
      setSelectedId(assets[0]?.id ?? '');
    }
  }, [assets, selectedId, assetFocusId]);

  const filteredAssets = useMemo(() => {
    const normalized = deferredQuery.trim().toLocaleLowerCase();
    return assets
      .filter((asset) => category === 'all' || asset.category === category)
      .filter((asset) => healthFilter === 'all'
        || (healthFilter === 'issues' && asset.validation !== 'ready')
        || (healthFilter === 'errors' && asset.validation === 'error')
        || (healthFilter === 'unused' && asset.unused === true)
        || (healthFilter === 'safe-unused' && asset.safeUnused === true)
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
        error: error instanceof Error ? error.message : assetMessage("资产移动预览失败。", "Could not preview this asset move.") });
    }
  };

  useEffect(() => assetImportBridge.subscribeDroppedSources((grants) => {
    const namespace = assetNamespace(assets);
    const targets = grants.map((grant) => defaultImportTarget(grant.fileName, namespace));
    void runBatchImportPreview(grants, targets);
  }), [assets, previewAssetImportBatch]);

  const runBatchImportPreview = async (grants: AssetImportSelectionGrant[], targetRelativePaths: string[]) => {
    setBatchImportReview({ grants, targetRelativePaths, preview: null, busy: true, error: null });
    try {
      const preview = await previewAssetImportBatch(grants.map((grant, index) => ({
        sourceGrantId: grant.id,
        targetRelativePath: targetRelativePaths[index] ?? defaultImportTarget(grant.fileName, assetNamespace(assets))
      })));
      setBatchImportReview({ grants, targetRelativePaths, preview, busy: false, error: null });
    } catch (error) {
      setBatchImportReview({ grants, targetRelativePaths, preview: null, busy: false,
        error: error instanceof Error ? error.message : assetMessage("资产批量导入预览失败。", "Could not preview the asset batch import.") });
    }
  };

  const beginBatchImport = async () => {
    try {
      const selection = await assetImportBridge.selectSources();
      if (selection.cancelled || selection.grants.length === 0) return;
      const namespace = assetNamespace(assets);
      const targets = selection.grants.map((grant) => defaultImportTarget(grant.fileName, namespace));
      await runBatchImportPreview(selection.grants, targets);
    } catch (error) {
      setNotice(error instanceof Error ? assetMessage(`无法选择批量导入文件：${error.message}`, `Could not select files for batch import: ${error.message}`) : assetMessage("无法选择批量导入文件。", "Could not select files for batch import."));
    }
  };

  const commitBatchImport = async () => {
    const current = batchImportReview;
    const preview = current?.preview;
    if (!current || !preview?.canApply) return;
    setBatchImportReview({ ...current, busy: true, error: null });
    try {
      const result = await importAssetBatch(preview.planToken, preview.requiresReplacementConfirmation);
      if (result.status !== 'committed') {
        setBatchImportReview({ ...current, busy: false,
          error: result.diagnostics[0]?.message ?? assetMessage("资产批量导入未提交。", "The asset batch import was not committed.") });
        return;
      }
      setBatchImportReview(null);
      const importedCount = result.data?.importedCount ?? preview.changedCount;
      const skipped = result.data?.skippedIdenticalCount ?? preview.identicalCount;
      setNotice(assetMessage(`已批量导入 ${importedCount} 个资产，跳过 ${skipped} 个相同文件；整个批次只创建一个恢复点。`, `Imported assets: ${importedCount}; skipped identical files: ${skipped}. One recovery point was created for the batch.`));
      setReloadToken((token) => token + 1);
    } catch (error) {
      setBatchImportReview({ ...current, busy: false,
        error: error instanceof Error ? error.message : assetMessage("资产批量导入失败。", "The asset batch import failed.") });
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
          error: result.diagnostics[0]?.message ?? assetMessage("资产移动未提交。", "The asset move was not committed.") });
        return;
      }
      setMoveReview(null);
      setNotice(assetMessage(`已移动到 ${preview.targetRelativePath}；更新 ${preview.referenceCount} 条引用并创建恢复点。`, `Moved to ${preview.targetRelativePath}; updated references: ${preview.referenceCount}. A recovery point was created.`));
      setSelectedId('');
      setReloadToken((token) => token + 1);
    } catch (error) {
      setMoveReview({ ...current, busy: false,
        error: error instanceof Error ? error.message : assetMessage("资产移动失败。", "The asset move failed.") });
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
        error: error instanceof Error ? error.message : assetMessage("资产导入预览失败。", "Could not preview the asset import.")
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
          error: result.diagnostics[0]?.message ?? assetMessage("资产导入未提交。", "The asset import was not committed.") });
        return;
      }
      setImportReview(null);
      setNotice(preview.conflict === 'REPLACE'
        ? assetMessage(`已安全替换 ${preview.targetRelativePath}；已创建恢复点。`, `Safely replaced ${preview.targetRelativePath}. A recovery point was created.`)
        : assetMessage(`已导入 ${preview.targetRelativePath}；已创建恢复点。`, `Imported ${preview.targetRelativePath}. A recovery point was created.`));
      setReloadToken((token) => token + 1);
    } catch (error) {
      setImportReview({ ...current, busy: false,
        error: error instanceof Error ? error.message : assetMessage("资产导入失败。", "The asset import failed.") });
    }
  };

  const beginImport = async (replacement?: AssetRecord) => {
    try {
      const grant = await assetImportBridge.selectSource();
      if (grant.cancelled) return;
      const target = replacement?.path ?? defaultImportTarget(grant.fileName, assetNamespace(assets));
      await runImportPreview(grant, target);
    } catch (error) {
      setNotice(error instanceof Error ? assetMessage(`无法选择导入文件：${error.message}`, `Could not select a file to import: ${error.message}`) : assetMessage("无法选择导入文件。", "Could not select a file to import."));
    }
  };

  const openInBlockbench = async (asset: AssetRecord) => {
    setOpeningBlockbench(true);
    try {
      const result = await blockbenchBridge.openAsset(asset.id);
      if (result.state === 'running') {
        setBlockbenchSessionAssetId(asset.id);
        setNotice(assetMessage(`Blockbench 桥接就绪：已打开模型 ${asset.name}。`, `Blockbench bridge ready: opened model ${asset.name}.`));
      } else if (result.diagnosticCode === 'BLOCKBENCH_NOT_CONFIGURED') {
        setNotice(assetMessage("尚未配置 Blockbench，请展开上方“连接 Blockbench”查看安装与检测说明。", "Blockbench is not configured. Expand Connect Blockbench above for installation and connection instructions."));
      } else {
        setNotice(assetMessage(`Blockbench 无法打开该资产（${result.diagnosticCode ?? result.state}）。`, `Blockbench could not open this asset (${result.diagnosticCode ?? result.state}).`));
      }
    } catch (error) {
      setNotice(error instanceof Error ? assetMessage(`Blockbench 桥接调用失败：${error.message}`, `Blockbench bridge call failed: ${error.message}`) : assetMessage("Blockbench 桥接调用失败。", "The Blockbench bridge call failed."));
    } finally {
      setOpeningBlockbench(false);
    }
  };

  useEffect(() => {
    if (!blockbenchSessionAssetId) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const poll = async () => {
      try {
        const result = await blockbenchBridge.status();
        if (!active) return;
        if (result.state === 'running') {
          timer = setTimeout(() => void poll(), 750);
          return;
        }
        setBlockbenchSessionAssetId(null);
        if (result.changeCommitted) {
          const revision = result.workspaceRevision == null ? '' : `，工作区 revision ${result.workspaceRevision}`;
          const recovery = result.recoveryPointId == null ? '' : `，恢复点 ${result.recoveryPointId}`;
          const englishRevision = result.workspaceRevision == null ? '' : `; workspace revision ${result.workspaceRevision}`;
          const englishRecovery = result.recoveryPointId == null ? '' : `; recovery point ${result.recoveryPointId}`;
          setNotice(assetMessage(`Blockbench 保存已同步，资产索引与引用已刷新${revision}${recovery}。`, `Blockbench changes synced; asset index and references refreshed${englishRevision}${englishRecovery}.`));
          setReloadToken((token) => token + 1);
        } else if (result.diagnosticCode) {
          setNotice(assetMessage(`Blockbench 会话已结束：${result.diagnosticCode}。`, `Blockbench session ended: ${result.diagnosticCode}.`));
        } else {
          setNotice(assetMessage("Blockbench 会话已结束，文件内容未发生变化。", "The Blockbench session ended without file changes."));
        }
      } catch (error) {
        if (!active) return;
        setBlockbenchSessionAssetId(null);
        setNotice(error instanceof Error ? assetMessage(`读取 Blockbench 状态失败：${error.message}`, `Could not read Blockbench status: ${error.message}`) : assetMessage("读取 Blockbench 状态失败。", "Could not read Blockbench status."));
      }
    };
    timer = setTimeout(() => void poll(), 500);
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [blockbenchSessionAssetId]);

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

      <BlockbenchSetupPanel />
      <BlockbenchTasksPanel source={selectedAsset} />
      <div className="asset-browser-body">
        {/* Left Category Rail */}
        <aside className="asset-category-panel" aria-label={uiText("资产分类", "Asset categories")}>
          <div className="asset-panel-label">
            <Layers size={13} aria-hidden="true" />
            <span>{uiText("分类导航", "Categories")}</span>
          </div>

          <nav className="asset-category-list" aria-label={uiText("资产类型过滤器", "Asset type filters")}>
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

          <div className="asset-health-panel" data-testid="asset-health-panel" aria-label={uiText("资产健康筛选", "Asset health filters")}>
            <div className="asset-panel-label">
              <AlertTriangle size={13} aria-hidden="true" />
              <span>{uiText("资产健康", "Asset health")}</span>
            </div>
            <div className="asset-health-summary" data-testid="asset-health-summary">
              <span><strong>{healthSummary?.errorAssets ?? 0}</strong> {uiText(" 有错误的资产", " Assets with errors")}</span>
              <span><strong>{healthSummary?.warningAssets ?? 0}</strong> {uiText(" 有警告的资产", " Assets with warnings")}</span>
              <span><strong>{healthSummary?.unusedAssets ?? 0}</strong> {uiText(" 未使用", " Unused")}</span>
              <span data-testid="asset-health-safe-summary"><strong>{healthSummary?.safeUnusedAssets ?? 0}</strong> {uiText(" 可清理候选", " Cleanup candidates")}</span>
            </div>
            <div className="asset-health-filters">
              {([
                ['all', uiText("全部", "All")],
                ['issues', uiText("有问题", "With issues")],
                ['errors', uiText("错误", "Errors")],
                ['unused', uiText("静态未引用", "No static references")],
                ['safe-unused', uiText("可安全清理候选", "Safe cleanup candidates")],
                ['duplicates', uiText("重复内容", "Duplicate content")]
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
                <span>{uiText(`${healthSummary?.missingReferences} 条缺失引用`, `Missing references: ${healthSummary?.missingReferences}`)}</span>
              </div>
            )}
            {(healthSummary?.duplicateGroups ?? 0) > 0 && (
              <span className="asset-health-summary-item" data-testid="asset-health-duplicate-summary">
                {uiText(`重复组 ${healthSummary?.duplicateGroups} / 资产 ${healthSummary?.duplicateAssets}`, `Duplicate groups: ${healthSummary?.duplicateGroups} / assets: ${healthSummary?.duplicateAssets}`)}
              </span>
            )}
          </div>

          <AssetDiagnosticsPanel diagnostics={assetDiagnostics} onAction={runDiagnosticAction} />

          <div className="asset-category-hint">
            <Link2 size={13} aria-hidden="true" />
            <span>{uiText("引用关系随工作区修订保存。", "References are saved with each workspace revision.")}</span>
          </div>
        </aside>

        {/* Middle Asset List Panel */}
        <main className="asset-list-panel" aria-label={uiText("资产内容列表", "Asset contents")}>
          <div className="asset-list-toolbar">
            <div className="asset-list-meta">
              <span className="asset-result-count">
                <strong>{filteredAssets.length}</strong> {uiText("项可用资产", "available assets")}</span>
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
                <span className="sr-only">{uiText("排序", "Sort")}</span>
                <select
                  aria-label={uiText("资产排序", "Sort assets")}
                  value={sort}
                  onChange={(e) => setSort(e.target.value as SortField)}
                >
                  <option value="updated">{uiText("最近更新", "Recently updated")}</option>
                  <option value="name">{uiText("资产名称", "Asset name")}</option>
                  <option value="references">{uiText("引用数", "Reference count")}</option>
                  <option value="size">{uiText("文件大小", "File size")}</option>
                </select>
              </label>

              <button
                type="button"
                className="asset-import-inline-btn btn-secondary"
                onClick={() => void beginImport()}
                data-testid="asset-import-button"
                title={uiText("导入外部模型或贴图", "Import an external model or texture")}
              >
                <Upload size={12} aria-hidden="true" />
                <span>{uiText("导入", "Import")}</span>
              </button>
              <button
                type="button"
                className="asset-import-inline-btn btn-secondary"
                onClick={() => void beginBatchImport()}
                data-testid="asset-batch-import-button"
                title={uiText("选择多个文件并在一次计划中导入", "Select multiple files to import in one plan")}
              >
                <PackageOpen size={12} aria-hidden="true" />
                <span>{uiText("批量导入", "Batch import")}</span>
              </button>
              <span className="asset-drop-hint" data-testid="asset-drop-hint">{uiText("或将资产文件拖放到窗口", "or drop asset files into this window")}</span>
            </div>
          </div>

          {filteredAssets.length === 0 ? (
            <div className="asset-no-results" data-testid="asset-browser-no-results" role="status">
              <div className="asset-empty-icon-wrap">
                <Search size={22} aria-hidden="true" />
              </div>
              <strong>{uiText("没有匹配的资产", "No matching assets")}</strong>
              <span>{uiText("当前搜索条件或分类筛选下未找到相关文件。", "No files match the current search or category filters.")}</span>
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
                <span>{uiText("清除筛选", "Clear filters")}</span>
              </button>
            </div>
          ) : (
            <div className="asset-card-grid" aria-label={uiText("资产列表", "Asset list")} role="list">
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

      {batchImportReview && (
        <AssetBatchImportReview
          state={batchImportReview}
          onTargetChange={(index, targetRelativePath) => {
            const targetRelativePaths = [...batchImportReview.targetRelativePaths];
            targetRelativePaths[index] = targetRelativePath;
            setBatchImportReview({ ...batchImportReview, targetRelativePaths, preview: null, error: null });
          }}
          onPreview={() => void runBatchImportPreview(
            batchImportReview.grants, batchImportReview.targetRelativePaths
          )}
          onCommit={() => void commitBatchImport()}
          onCancel={() => setBatchImportReview(null)}
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

const AssetBatchImportReview: React.FC<{
  state: AssetBatchImportReviewState;
  onTargetChange: (index: number, targetRelativePath: string) => void;
  onPreview: () => void;
  onCommit: () => void;
  onCancel: () => void;
}> = ({ state, onTargetChange, onPreview, onCommit, onCancel }) => {
  const preview = state.preview;
  return (
    <div className="asset-import-review-backdrop" role="presentation">
      <section className="asset-import-review" role="dialog" aria-modal="true"
        aria-label={uiText("资产批量导入预览", "Asset batch import preview")} data-testid="asset-batch-import-review">
        <div className="asset-import-review-heading">
          <div>
            <strong>{uiText("批量导入资产", "Import asset batch")}</strong>
            <span>{uiText("整个批次会先审阅目标与冲突，再用一个恢复点和一个工作区 revision 原子提交。", "Review all targets and conflicts before committing the batch atomically with one recovery point and one workspace revision.")}</span>
          </div>
          <button type="button" className="asset-clear-button" onClick={onCancel} aria-label={uiText("取消批量导入", "Cancel batch import")}>
            <XCircle size={16} />
          </button>
        </div>

        <div className="asset-batch-import-items" data-testid="asset-batch-import-items">
          {state.grants.map((grant, index) => {
            const itemPreview = preview?.items[index];
            return (
              <div className="asset-import-review-source" key={grant.id} data-testid={`asset-batch-item-${index}`}>
                <div>
                  <span>{uiText("来源 ", "Source ")}{index + 1}</span>
                  <strong>{grant.fileName}</strong>
                  <small>{formatBytes(grant.size)}</small>
                </div>
                <label className="asset-import-target-field">
                  <span>{uiText("工作区目标路径", "Workspace target path")}</span>
                  <input data-testid={`asset-batch-target-${index}`} value={state.targetRelativePaths[index] ?? ''}
                    onChange={(event) => onTargetChange(index, event.target.value)} disabled={state.busy} />
                </label>
                <div data-testid={`asset-batch-conflict-${index}`}>
                  {itemPreview ? `${valueLabel(itemPreview.conflict)} · ${valueLabel(itemPreview.category)}` : uiText("等待预览", "Awaiting preview")}
                </div>
              </div>
            );
          })}
        </div>

        {preview && (
          <div className="asset-import-preview-summary" data-testid="asset-batch-preview-summary">
            <div><span>{uiText("新建", "Create")}</span><strong data-testid="asset-batch-create-count">{preview.createCount}</strong></div>
            <div><span>{uiText("替换", "Replace")}</span><strong data-testid="asset-batch-replace-count">{preview.replaceCount}</strong></div>
            <div><span>{uiText("相同 / 跳过", "Identical / skip")}</span><strong>{preview.identicalCount}</strong></div>
            <div><span>{uiText("实际变更", "Changes")}</span><strong>{preview.changedCount}</strong></div>
            {preview.issueCodes.length > 0 && (
              <div className="asset-import-issue-codes" data-testid="asset-batch-issues">
                <span>{uiText("阻断 / 检查项", "Blockers / checks")}</span>
                <div>{preview.issueCodes.map((code) => <code key={code}>{code}</code>)}</div>
              </div>
            )}
          </div>
        )}

        <div className="asset-import-review-actions">
          <button type="button" className="btn-secondary" onClick={onPreview} disabled={state.busy}
            data-testid="asset-batch-preview">
            <RefreshCw size={13} aria-hidden="true" />
            <span>{preview ? uiText("重新预览全部", "Preview all again") : uiText("预览全部", "Preview all")}</span>
          </button>
          <button type="button" className="btn-primary" onClick={onCommit}
            disabled={state.busy || !preview?.canApply} data-testid="asset-batch-commit">
            <PackageOpen size={13} aria-hidden="true" />
            <span>{preview?.requiresReplacementConfirmation ? uiText("确认替换并批量导入", "Confirm replacements and import batch") : uiText("确认批量导入", "Confirm batch import")}</span>
          </button>
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={state.busy}>{uiText("取消", "Cancel")}</button>
        </div>
        {state.busy && <div className="asset-import-review-status" role="status">{uiText("正在校验整个导入批次…", "Validating the import batch…")}</div>}
        {state.error && <div className="asset-import-review-error" role="alert">{renderAssetMessage(state.error)}</div>}
      </section>
    </div>
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
        aria-label={uiText("资产重命名或移动预览", "Asset rename or move preview")} data-testid="asset-move-review">
        <div className="asset-import-review-heading">
          <div>
            <strong>{uiText("重命名 / 移动资产", "Rename / move asset")}</strong>
            <span>{uiText("先审阅新路径和每一条引用改写；任何无法安全改写的引用都会阻止提交。", "Review the new path and each reference rewrite. Any reference that cannot be safely rewritten blocks the change.")}</span>
          </div>
          <button type="button" className="asset-clear-button" onClick={onCancel} aria-label={uiText("取消移动", "Cancel move")}>
            <XCircle size={16} />
          </button>
        </div>

        <div className="asset-import-review-source">
          <span>{uiText("当前资产", "Current asset")}</span>
          <strong data-testid="asset-move-source">{state.asset.path}</strong>
          <small>{state.asset.categoryLabel}</small>
        </div>

        <label className="asset-import-target-field">
          <span>{uiText("新的工作区路径", "New workspace path")}</span>
          <input data-testid="asset-move-target" value={state.targetRelativePath}
            onChange={(event) => onTargetChange(event.target.value)} disabled={state.busy} />
        </label>

        <div className="asset-import-review-actions">
          <button type="button" className="btn-secondary" onClick={onPreview} disabled={state.busy}
            data-testid="asset-move-preview">
            <RefreshCw size={13} aria-hidden="true" />
            <span>{preview ? uiText("重新预览", "Preview again") : uiText("预览影响", "Preview impact")}</span>
          </button>
          <button type="button" className="btn-primary" onClick={onCommit}
            disabled={state.busy || !preview?.canApply} data-testid="asset-move-commit">
            <CornerDownRight size={13} aria-hidden="true" />
            <span>{uiText("确认移动并更新引用", "Confirm move and update references")}</span>
          </button>
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={state.busy}>{uiText("取消", "Cancel")}</button>
        </div>

        {state.busy && <div className="asset-import-review-status" role="status">{uiText("正在计算引用影响…", "Calculating reference impact…")}</div>}
        {state.error && <div className="asset-import-review-error" role="alert">{renderAssetMessage(state.error)}</div>}
        {preview && (
          <div className="asset-import-preview-summary" data-testid="asset-move-preview-summary">
            <div><span>{uiText("旧路径", "Old path")}</span><code>{preview.sourceRelativePath}</code></div>
            <div><span>{uiText("新路径", "New path")}</span><code>{preview.targetRelativePath}</code></div>
            <div><span>{uiText("新稳定标识", "New stable ID")}</span><code data-testid="asset-move-target-id">{preview.targetAssetId}</code></div>
            <div><span>{uiText("受影响引用", "Affected references")}</span><strong data-testid="asset-move-reference-count">{preview.referenceCount}</strong></div>
            {preview.rewrites.length > 0 && (
              <div className="asset-move-rewrites" data-testid="asset-move-rewrites">
                <span>{uiText("精确改写", "Exact rewrites")}</span>
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
                <span>{uiText("阻断 / 检查项", "Blockers / checks")}</span>
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
    ? uiText("将替换现有资产", "Will replace existing asset")
    : preview?.conflict === 'IDENTICAL' ? uiText("目标内容已相同", "Target content is identical") : uiText("新建资产", "Create asset");
  return (
    <div className="asset-import-review-backdrop" role="presentation">
      <section className="asset-import-review" role="dialog" aria-modal="true"
        aria-label={uiText("资产导入预览", "Asset import preview")} data-testid="asset-import-review">
        <div className="asset-import-review-heading">
          <div>
            <strong>{uiText("导入资产", "Import asset")}</strong>
            <span>{uiText("先检查目标路径、冲突和重复内容，再写入工作区。", "Check the target path, conflicts and duplicate content before writing to the workspace.")}</span>
          </div>
          <button type="button" className="asset-clear-button" onClick={onCancel} aria-label={uiText("取消导入", "Cancel import")}>
            <XCircle size={16} />
          </button>
        </div>

        <div className="asset-import-review-source">
          <span>{uiText("来源文件", "Source file")}</span>
          <strong data-testid="asset-import-source">{state.grant.fileName}</strong>
          <small>{formatBytes(state.grant.size)}</small>
        </div>

        <label className="asset-import-target-field">
          <span>{uiText("工作区目标路径", "Workspace target path")}</span>
          <input data-testid="asset-import-target" value={state.targetRelativePath}
            onChange={(event) => onTargetChange(event.target.value)} disabled={state.busy} />
        </label>

        <div className="asset-import-review-actions">
          <button type="button" className="btn-secondary" onClick={onPreview} disabled={state.busy}
            data-testid="asset-import-preview">
            <RefreshCw size={13} aria-hidden="true" />
            <span>{preview ? uiText("重新预览", "Preview again") : uiText("预览导入", "Preview import")}</span>
          </button>
          <button type="button" className="btn-primary" onClick={onCommit}
            disabled={state.busy || !preview?.canApply} data-testid="asset-import-commit">
            <Upload size={13} aria-hidden="true" />
            <span>{preview?.conflict === 'REPLACE' ? uiText("确认替换并导入", "Confirm replacement and import") : uiText("确认导入", "Confirm import")}</span>
          </button>
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={state.busy}
            data-testid="asset-import-cancel">{uiText("取消", "Cancel")}</button>
        </div>

        {state.busy && <div className="asset-import-review-status" role="status">{uiText("正在校验导入计划…", "Validating the import plan…")}</div>}
        {state.error && <div className="asset-import-review-error" role="alert" data-testid="asset-import-error">
          {renderAssetMessage(state.error)}
        </div>}
        {preview && (
          <div className="asset-import-preview-summary" data-testid="asset-import-preview-summary">
            <div><span>{uiText("操作", "Action")}</span><strong data-testid="asset-import-conflict">{conflictLabel}</strong></div>
            <div><span>{uiText("目标", "Target")}</span><code>{preview.targetRelativePath}</code></div>
            <div><span>{uiText("来源 SHA-256", "Source SHA-256")}</span><code>{preview.sourceSha256.slice(0, 16)}…</code></div>
            {preview.targetSha256 && <div><span>{uiText("现有 SHA-256", "Existing SHA-256")}</span><code>{preview.targetSha256.slice(0, 16)}…</code></div>}
            {preview.duplicatePaths.length > 0 && (
              <div className="asset-import-duplicates" data-testid="asset-import-duplicates">
                <span>{uiText("相同内容", "Identical content")}</span>
                <div>{preview.duplicatePaths.map((path) => <code key={path}>{path}</code>)}</div>
              </div>
            )}
            {preview.issueCodes.length > 0 && (
              <div className="asset-import-issue-codes">
                <span>{uiText("检查项", "Checks")}</span>
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
          <h2>{uiText("资产与模型工作台", "Assets and models")}</h2>
          <span>{uiText("资产与 Blockbench 集成 · 模型、纹理、动画与资源包 · 引用关系可追溯", "Assets and Blockbench integration · Models, textures, animations and resource packs · Traceable references")}</span>
        </div>
      </div>

      <div className="asset-header-actions">
        <label className="asset-search-field">
          <Search size={14} aria-hidden="true" />
          <span className="sr-only">{uiText("搜索资产", "Search assets")}</span>
          <input
            data-testid="asset-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={uiText("搜索名称、路径或标识…", "Search name, path or ID…")}
            aria-label={uiText("搜索资产", "Search assets")}
          />
          {query && (
            <button
              type="button"
              className="asset-clear-button"
              onClick={() => setQuery('')}
              aria-label={uiText("清除搜索", "Clear search")}
            >
              <XCircle size={13} />
            </button>
          )}
        </label>

        <span className="connection-state" title={uiText("资产索引与底层虚拟文件系统保持同步", "Asset index is synchronized with the underlying virtual file system")}>
          <span aria-hidden="true" />
          <span>{uiText('已索引', 'Indexed')} ({filteredCount}/{totalAssets})</span>
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
            <h2>{uiText("资产与模型工作台", "Assets and models")}</h2>
            <span>{uiText("资产与 Blockbench 集成 · 模型、纹理、动画与资源包 · 引用关系可追溯", "Assets and Blockbench integration · Models, textures, animations and resource packs · Traceable references")}</span>
          </div>
        </div>
        <div className="asset-header-actions">
          <label className="asset-search-field">
            <Search size={14} aria-hidden="true" />
            <span className="sr-only">{uiText("搜索资产", "Search assets")}</span>
            <input
              data-testid="asset-search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={uiText("搜索名称、路径或标识…", "Search name, path or ID…")}
              disabled
            />
          </label>
        </div>
      </header>

      <BlockbenchSetupPanel />
      <BlockbenchTasksPanel />
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
            <strong>{uiText("正在读取工作区资产", "Loading workspace assets")}</strong>
            <span>{uiText("正在建立路径、引用和校验投影…", "Collecting paths, references and validation results…")}</span>
          </>
        ) : mode === 'error' ? (
          <>
            <div className="asset-state-icon-box error">
              <XCircle size={30} aria-hidden="true" />
            </div>
            <strong>{uiText("资产投影暂时不可用", "Asset data is temporarily unavailable")}</strong>
            <span>{uiText("工作区桥接返回了不完整的资产索引，原始文件不会被修改。", "The workspace bridge returned an incomplete asset index. Original files will not be changed.")}</span>
            <button type="button" className="btn-secondary" onClick={onRetry}>
              <RefreshCw size={13} aria-hidden="true" />
              <span>{uiText("重新读取", "Reload")}</span>
            </button>
          </>
        ) : (
          <>
            <div className="asset-state-icon-box empty">
              <PackageOpen size={32} aria-hidden="true" />
            </div>
            <strong>{uiText("工作区还没有资产", "This workspace has no assets yet")}</strong>
            <span>{uiText("导入 Blockbench 模型、纹理或一个独立资源包，资产会自动建立引用关系。", "Import a Blockbench model, texture or standalone resource pack to index its references automatically.")}</span>
            <button
              type="button"
              className="btn-primary"
              data-testid="asset-import-empty"
              onClick={onRetry}
            >
              <Upload size={14} aria-hidden="true" />
              <span>{uiText("导入第一个资产", "Import your first asset")}</span>
            </button>
          </>
        )}
      </div>
    </section>
  );
};

/* Individual Asset Card in the grid */
const AssetDiagnosticsPanel: React.FC<{
  diagnostics: readonly Diagnostic[];
  onAction: (action: Diagnostic['actions'][number], diagnostic: Diagnostic) => void;
}> = ({ diagnostics, onAction }) => {
  if (diagnostics.length === 0) return null;
  return (
    <div className="asset-health-panel" data-testid="asset-diagnostics-panel" aria-label={t({ key: 'asset.diagnostics.label', fallback: 'Asset diagnostics' })}>
      <div className="asset-panel-label">
        <AlertCircle size={13} aria-hidden="true" />
        <span>{t({ key: 'asset.diagnostics.title', fallback: 'Structured diagnostics' })}</span>
      </div>
      {diagnostics.map((diagnostic) => (
        <div key={`${diagnostic.code}-${diagnostic.path ?? ''}`} data-testid={`asset-diagnostic-${diagnostic.code}`}
          style={{ display: 'flex', flexDirection: 'column', gap: '6px', padding: '8px 0' }}>
          <code style={{ fontSize: '10px', color: 'var(--text-sub)', overflowWrap: 'anywhere' }}>{diagnostic.code}</code>
          <span style={{ fontSize: '11px', lineHeight: 1.5 }}>{t(diagnostic.message)}</span>
          {diagnostic.actions.map((action) => (
            <button key={action.id} type="button" className="btn-secondary"
              data-testid={`asset-diagnostic-action-${action.id}`}
              style={{ minHeight: '32px', alignSelf: 'flex-start' }}
              onClick={() => onAction(action, diagnostic)}>
              {t(action.label)}
            </button>
          ))}
        </div>
      ))}
    </div>
  );
};

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
              {UI_LOCALE === 'en' ? valueLabel(asset.validation) : asset.validationLabel}
            </span>
          </div>

          <div className="asset-card-category-row">
            <span>{UI_LOCALE === 'en' ? valueLabel(asset.category) : asset.categoryLabel}</span>
            <span className="asset-dot">·</span>
            <span>{UI_LOCALE === 'en' ? valueLabel(asset.source) : asset.sourceLabel}</span>
          </div>

          <small className="asset-card-path" title={asset.path}>
            {asset.path}
          </small>
        </div>
      </div>

      <div className="asset-card-footer">
        <span className="asset-card-size">{asset.size}</span>
        <span className="asset-card-refs" title={uiText(`被 ${asset.references.length} 个对象引用`, `Referencing objects: ${asset.references.length}`)}>
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
  notice: AssetMessage | null;
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
      <aside className="asset-details-panel" aria-label={uiText("资产详情", "Asset details")}>
        <div className="asset-details-empty">
          <Info size={22} aria-hidden="true" />
          <span>{uiText("选择一项资产查看详情。", "Select an asset to view its details.")}</span>
        </div>
      </aside>
    );
  }

  return (
    <aside className="asset-details-panel" aria-label={uiText("资产详情", "Asset details")} data-testid="asset-details">
      {/* Detail Header */}
      <div className="asset-details-heading">
        <div className="asset-details-icon">
          <AssetCategoryIcon category={asset.category} size={20} />
        </div>
        <div className="asset-details-title-wrap">
          <strong title={asset.name}>{asset.name}</strong>
          <div className="asset-details-sub">
            <small>{UI_LOCALE === 'en' ? valueLabel(asset.category) : asset.categoryLabel}</small>
            <span className="asset-dot">·</span>
            <small>{UI_LOCALE === 'en' ? valueLabel(asset.source) : asset.sourceLabel}</small>
          </div>
        </div>
      </div>

      <div className={`badge badge-${statusClass(asset.validation)} asset-details-status`}>
        <StatusIcon status={asset.validation} size={12} />
        <span>{UI_LOCALE === 'en' ? valueLabel(asset.validation) : asset.validationLabel}</span>
      </div>

      {/* Surface Preview Canvas */}
      <div className="asset-preview-surface" aria-label={uiText("资产预览", "Asset preview")}>
        <div className="asset-preview-icon-cluster">
          <AssetCategoryIcon category={asset.category} size={42} />
        </div>
        <div className="asset-preview-specs">
          <span className="asset-preview-format">{asset.format}</span>
          <small className="asset-preview-dimensions">{asset.dimensions ?? (asset.category === 'model' ? uiText("无模型预览", "No model preview") : uiText("无预览尺寸", "No preview dimensions"))}</small>
        </div>
      </div>

      {/* Structured Metadata DL */}
      <dl className="asset-metadata">
        <div className="asset-metadata-row asset-id-row">
          <dt>{uiText("稳定标识", "Stable ID")}</dt>
          <dd>
            <code data-testid="asset-stable-id" title={asset.id}>
              {asset.id}
            </code>
            <button
              type="button"
              className="asset-id-copy-btn"
              onClick={() => onCopyId(asset.id)}
              title={uiText("复制稳定标识", "Copy stable ID")}
              aria-label={uiText("复制稳定标识", "Copy stable ID")}
            >
              {copiedId ? <CheckCircle2 size={12} className="text-green" /> : <Copy size={12} />}
            </button>
          </dd>
        </div>
        <div className="asset-metadata-row">
          <dt>{uiText("路径", "Path")}</dt>
          <dd title={asset.path}><code>{asset.path}</code></dd>
        </div>
        <div className="asset-metadata-row">
          <dt>{uiText("大小", "Size")}</dt>
          <dd>{asset.size}</dd>
        </div>
        <div className="asset-metadata-row">
          <dt>{uiText("来源", "Source")}</dt>
          <dd>{asset.sourceLabel}</dd>
        </div>
        <div className="asset-metadata-row">
          <dt>{uiText("更新时间", "Updated")}</dt>
          <dd>{formatDate(asset.updatedAt)}</dd>
        </div>
        <div className="asset-metadata-row">
          <dt>{uiText("使用状态", "Usage")}</dt>
          <dd data-testid="asset-usage-status">
            {asset.safeUnused ? uiText("可安全清理候选（模型/纹理引用检查已完成）", "Safe cleanup candidate (model and texture reference checks completed)") : asset.unused ? (asset.cleanupAssessed ? uiText("静态未引用，但存在工作区引用信号", "No static inbound references, but workspace reference signals exist") : uiText("静态未引用候选（安全清理尚未评估）", "No static inbound references (cleanup safety not assessed)")) : asset.usageAssessed ? uiText("存在静态入站引用", "Has static inbound references") : uiText("不参与静态未使用判断", "Not assessed for static unused status")}
          </dd>
        </div>
      </dl>

      {/* Reference Diagnostics */}
      <div className="asset-reference-section">
        <div className="asset-panel-label">
          <Link2 size={14} aria-hidden="true" />
          <span>{uiText("入站引用", "Inbound references")}</span>
          <span className="asset-ref-count-badge">{asset.inboundCount ?? asset.references.length}</span>
        </div>

        {asset.references.length === 0 ? (
          <div className="asset-reference-empty">{uiText("暂无入站引用关系。", "No inbound references.")}</div>
        ) : (
          <ul className="asset-reference-list" aria-label={uiText("引用该资产的来源", "Sources referencing this asset")}>
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
          <span>{uiText("出站依赖", "Outbound dependencies")}</span>
          <span className="asset-ref-count-badge">{asset.outboundCount ?? asset.outgoingReferences?.length ?? 0}</span>
        </div>
        {(asset.outgoingReferences?.length ?? 0) === 0 ? (
          <div className="asset-reference-empty">{uiText("该资产没有静态出站依赖。", "This asset has no static outbound dependencies.")}</div>
        ) : (
          <ul className="asset-reference-list" aria-label={uiText("该资产引用的目标", "Targets referenced by this asset")}>
            {asset.outgoingResolution?.length ? asset.outgoingResolution.map((reference, index) => (
              <li key={`${reference.sourcePointer}:${reference.targetPath}:${index}`} className="asset-reference-item">
                <details className="asset-resource-resolution">
                  <summary>
                    <span>{resourceResolutionLabel(reference.resolution ?? (reference.targetAssetId ? 'workspace_resolved' : 'unverified'))}</span>
                    <code>{reference.rawValue}</code>
                  </summary>
                  <p>{uiText("引用位置：", "Reference location: ")}<code>{reference.sourcePointer || '/'}</code></p>
                  <p>{uiText("目标：", "Target: ")}<code>{reference.targetPath}</code></p>
                  <p>{uiText("来源及依据：", "Source and evidence: ")}<code>{reference.resourceSource ?? (reference.targetAssetId ? uiText("工作区索引", "Workspace index") : uiText("来源未提供", "Source not provided"))}</code></p>
                  {reference.resourceVersion && <p>{uiText("资源版本：", "Resource version: ")}<code>{reference.resourceVersion}</code></p>}
                  {reference.resolution === 'unverified' && <p>{uiText("请先完成工作区依赖同步或构建，再重新检查；本次检查未下载外部资源。", "Sync workspace dependencies or build, then check again. This check did not download external resources.")}</p>}
                </details>
              </li>
            )) : asset.outgoingReferences?.map((reference, index) => (
              <li key={`${reference}:${index}`} className="asset-reference-item">
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
            <span>{uiText("健康诊断", "Health diagnostics")}</span>
          </div>
          {asset.issueCodes?.map((code) => <code key={code}>{code}</code>)}
        </div>
      )}

      {(asset.duplicatePaths?.length ?? 0) > 0 && (
        <div className="asset-reference-section" data-testid="asset-duplicate-paths">
          <div className="asset-panel-label">
            <Copy size={14} aria-hidden="true" />
            <span>{uiText("相同内容", "Identical content")}</span>
            <span className="asset-ref-count-badge">{asset.duplicatePaths?.length ?? 0}</span>
          </div>
          <ul className="asset-reference-list" aria-label={uiText("内容完全相同的其它资产", "Other assets with identical content")}>
            {asset.duplicatePaths?.map((path) => (
              <li key={path} className="asset-reference-item"><code title={path}>{path}</code></li>
            ))}
          </ul>
        </div>
      )}

      {/* Description Summary */}
      <p className="asset-description">{UI_LOCALE === 'en' && asset.description === '工作区真实资产，由 AssetWorkspaceService 实时索引。' ? tr(asset.description) : asset.description}</p>

      {/* Action Buttons */}
      <div className="asset-details-actions">
        <button
          type="button"
          className="btn-secondary asset-action-btn"
          onClick={() => onMove(asset)}
          data-testid="asset-move-button"
        >
          <CornerDownRight size={14} aria-hidden="true" />
          <span>{uiText("重命名 / 移动", "Rename / move")}</span>
        </button>

        <button
          type="button"
          className="btn-secondary asset-action-btn"
          onClick={() => onImport(asset)}
        >
          <Upload size={14} aria-hidden="true" />
          <span>{uiText("替换文件", "Replace file")}</span>
        </button>

        <button
          type="button"
          className="btn-primary asset-action-btn"
          data-testid="asset-open-blockbench"
          disabled={openingBlockbench}
          onClick={() => onOpenBlockbench(asset)}
        >
          <Box size={14} aria-hidden="true" />
          <span>{openingBlockbench ? uiText("正在打开…", "Opening…") : uiText("在 Blockbench 打开", "Open in Blockbench")}</span>
        </button>
      </div>

      {/* Notice Message Banner */}
      {notice && (
        <div className="asset-notice" role="status" data-testid="asset-notice">
          <AlertCircle size={14} className="asset-notice-icon" aria-hidden="true" />
          <span className="asset-notice-text">{renderAssetMessage(notice)}</span>
          <button
            type="button"
            className="asset-clear-button asset-notice-close"
            aria-label={uiText("关闭提示", "Dismiss notice")}
            onClick={onDismissNotice}
          >
            <XCircle size={14} />
          </button>
        </div>
      )}
    </aside>
  );
};

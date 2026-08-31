import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Check,
  Database,
  Languages,
  Link2,
  Pencil,
  Plus,
  RefreshCw,
  Tags,
  Trash2,
  Variable,
  X,
  Upload,
  Search,
  ChevronLeft,
  ChevronRight,
  FileSpreadsheet,
  FileCode
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  RegistryEntry,
  RegistryRenamePreview,
  WorkspaceReferenceProjection,
  WorkspaceRegistriesProjection
} from '../types/contract';
import { LanguageImportModal, ParsedLanguageEntry, ImportConflictMode } from './LanguageImportModal';
import { t } from '../i18n';

type RegistryName = 'variables' | 'tags' | 'languageKeys';
type DataTab = RegistryName | 'references';
type LanguageFilterMode = 'all' | 'missing' | 'duplicates';

const EMPTY_REGISTRIES: Record<RegistryName, RegistryEntry[]> = {
  variables: [],
  tags: [],
  languageKeys: []
};

const TAB_ITEMS: Array<{ id: DataTab; label: string; icon: React.ComponentType<{ size: number }> }> = [
  { id: 'variables', label: '变量', icon: Variable },
  { id: 'tags', label: '标签', icon: Tags },
  { id: 'languageKeys', label: '语言', icon: Languages },
  { id: 'references', label: '引用图', icon: Link2 }
];

const PAGE_SIZE = 25;

function entryName(entry: RegistryEntry): string {
  return entry.key ?? entry.name ?? '';
}

function entryDetails(tab: RegistryName, entry: RegistryEntry): string {
  if (tab === 'variables') return `${entry.dataType ?? 'number'} · ${entry.scope ?? 'global'}`;
  if (tab === 'tags') return `${entry.namespace ?? 'mod'}:${entry.name ?? ''} · ${entry.category ?? 'items'} · ${entry.members?.length ?? 0} 个成员`;
  const translations = entry.translations ?? {};
  return Object.entries(translations).map(([locale, value]) => `${locale}: ${value}`).join(' · ') || '尚无翻译';
}

export const CreatorDataView: React.FC = () => {
  const {
    state,
    listWorkspaceRegistries,
    getWorkspaceReferences,
    createRegistryEntry,
    updateRegistryEntry,
    previewRegistryRename,
    renameRegistryEntry,
    deleteRegistryEntry
  } = useWorkbench();

  const [tab, setTab] = useState<DataTab>('variables');
  const [projection, setProjection] = useState<WorkspaceRegistriesProjection | null>(null);
  const [references, setReferences] = useState<WorkspaceReferenceProjection | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  // Creation form states
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [secondary, setSecondary] = useState('');
  const [fallback, setFallback] = useState('');
  const [dataType, setDataType] = useState('number');
  const [scope, setScope] = useState('global');
  const [category, setCategory] = useState('items');

  // Rename states
  const [renameDraft, setRenameDraft] = useState<{ entryId: string; value: string } | null>(null);
  const [renamePreview, setRenamePreview] = useState<RegistryRenamePreview | null>(null);

  // Language tools & Search / Filter / Pagination states
  const [searchQuery, setSearchQuery] = useState('');
  const [langFilter, setLangFilter] = useState<LanguageFilterMode>('all');
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);

  const refresh = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const [registryResult, referenceResult] = await Promise.all([
        listWorkspaceRegistries(),
        getWorkspaceReferences()
      ]);
      setProjection(registryResult);
      setReferences(referenceResult);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setLoading(false);
    }
  }, [getWorkspaceReferences, listWorkspaceRegistries]);

  useEffect(() => { void refresh(); }, [refresh, state.workbench?.workspace.revision]);

  const registries = useMemo<Record<RegistryName, RegistryEntry[]>>(() => ({
    variables: projection?.registries?.variables ?? projection?.variables ?? EMPTY_REGISTRIES.variables,
    tags: projection?.registries?.tags ?? projection?.tags ?? EMPTY_REGISTRIES.tags,
    languageKeys: projection?.registries?.languageKeys ?? projection?.languageKeys ?? EMPTY_REGISTRIES.languageKeys
  }), [projection]);

  // Reset pagination on tab change or search change
  useEffect(() => {
    setCurrentPage(1);
    setShowCreate(false);
    setRenameDraft(null);
    setRenamePreview(null);
  }, [tab, searchQuery, langFilter]);

  const resetForm = () => {
    setName('');
    setSecondary('');
    setFallback('');
    setDataType('number');
    setScope('global');
    setCategory('items');
    setShowCreate(false);
  };

  const createEntry = async () => {
    if (tab === 'references' || !name.trim()) return;
    let entry: Partial<RegistryEntry>;
    if (tab === 'variables') {
      entry = { name: name.trim(), dataType, scope };
    } else if (tab === 'tags') {
      entry = { name: name.trim(), namespace: secondary.trim() || 'mod', category, members: [] };
    } else {
      entry = {
        key: name.trim(),
        translations: { zh_cn: secondary.trim(), en_us: fallback.trim() }
      };
    }
    const result = await createRegistryEntry(tab, entry);
    if (result.status !== 'committed') {
      setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '创建注册表条目失败。');
      return;
    }
    setMessage(`已创建 ${name.trim()}，稳定 ID 为 ${result.data?.entry?.id ?? '-'}`);
    resetForm();
    await refresh();
  };

  const reviewRename = async () => {
    if (!renameDraft?.value.trim()) return;
    const preview = await previewRegistryRename(renameDraft.entryId, renameDraft.value.trim());
    if (!preview) {
      setMessage('无法生成重命名影响预览。');
      return;
    }
    setRenamePreview(preview);
  };

  const applyRename = async () => {
    if (!renamePreview) return;
    const result = await renameRegistryEntry(renamePreview.entryId, renamePreview.newName);
    if (result.status !== 'committed') {
      setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '重命名失败。');
      return;
    }
    setMessage(`已将 ${renamePreview.oldName} 重命名为 ${renamePreview.newName}，并更新 ${result.data?.changedElementIds?.length ?? 0} 个元素。`);
    setRenameDraft(null);
    setRenamePreview(null);
    await refresh();
  };

  const removeEntry = async (entry: RegistryEntry) => {
    const impacted = await getWorkspaceReferences(entry.id);
    if ((impacted?.edges.length ?? 0) > 0) {
      setMessage(`${entryName(entry)} 仍被 ${impacted?.edges.length} 处引用，已阻止删除。`);
      setReferences(impacted);
      setTab('references');
      return;
    }
    if (!window.confirm(`确定删除“${entryName(entry)}”吗？删除前会创建本地恢复点。`)) return;
    const result = await deleteRegistryEntry(entry.id);
    if (result.status !== 'committed') {
      setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '删除失败。');
      return;
    }
    setMessage(`已删除 ${entryName(entry)}。`);
    await refresh();
  };

  // Language Import Handler
  const handleImportLanguage = async (entries: ParsedLanguageEntry[], mode: ImportConflictMode) => {
    const currentKeys = registries.languageKeys;
    let addedCount = 0;
    let updatedCount = 0;

    for (const item of entries) {
      const existing = currentKeys.find((k) => (k.key || k.name) === item.key);
      if (existing) {
        if (mode === 'merge' || mode === 'replace') {
          // Update translations
          const updatedTranslations = {
            ...(existing.translations || {}),
            ...(item.zh_cn ? { zh_cn: item.zh_cn } : {}),
            ...(item.en_us ? { en_us: item.en_us } : {})
          };
          await updateRegistryEntry(existing.id, [{ path: '/translations', value: updatedTranslations }]);
          updatedCount++;
        }
      } else {
        await createRegistryEntry('languageKeys', {
          key: item.key,
          translations: {
            zh_cn: item.zh_cn,
            en_us: item.en_us
          }
        });
        addedCount++;
      }
    }

    setMessage(`语言词条导入完成：新增 ${addedCount} 项，更新 ${updatedCount} 项。`);
    await refresh();
  };

  // Language Export Handlers
  const handleExportCSV = () => {
    const keys = registries.languageKeys;
    const header = 'key,zh_cn,en_us\n';
    const rows = keys
      .map((k) => {
        const key = k.key || k.name || '';
        const zh = (k.translations?.zh_cn || '').replace(/,/g, '，');
        const en = (k.translations?.en_us || '').replace(/,/g, ' ');
        return `"${key}","${zh}","${en}"`;
      })
      .join('\n');

    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `translations_${state.workbench?.workspace.name || 'workspace'}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    setMessage(`已导出 ${keys.length} 个语言词条为 CSV 文件。`);
  };

  const handleExportJSON = () => {
    const keys = registries.languageKeys;
    const zhMap: Record<string, string> = {};
    const enMap: Record<string, string> = {};

    keys.forEach((k) => {
      const key = k.key || k.name || '';
      if (k.translations?.zh_cn) zhMap[key] = k.translations.zh_cn;
      if (k.translations?.en_us) enMap[key] = k.translations.en_us;
    });

    const exportObj = {
      zh_cn: zhMap,
      en_us: enMap
    };

    const blob = new Blob([JSON.stringify(exportObj, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `translations_${state.workbench?.workspace.name || 'workspace'}.json`;
    a.click();
    URL.revokeObjectURL(url);
    setMessage(`已导出 ${keys.length} 个语言词条为 JSON 字典。`);
  };

  // Filtered and paginated entries
  const filteredEntries = useMemo(() => {
    if (tab === 'references') return [];
    let list = registries[tab];

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter((e) => {
        const nameMatch = entryName(e).toLowerCase().includes(q);
        const transMatch = Object.values(e.translations || {}).some((v) =>
          String(v).toLowerCase().includes(q)
        );
        return nameMatch || transMatch;
      });
    }

    if (tab === 'languageKeys') {
      if (langFilter === 'missing') {
        list = list.filter((e) => !e.translations?.zh_cn || !e.translations?.en_us);
      } else if (langFilter === 'duplicates') {
        const keyCounts = new Map<string, number>();
        registries.languageKeys.forEach((k) => {
          const name = entryName(k);
          keyCounts.set(name, (keyCounts.get(name) || 0) + 1);
        });
        list = list.filter((e) => (keyCounts.get(entryName(e)) || 0) > 1);
      }
    }

    return list;
  }, [registries, tab, searchQuery, langFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredEntries.length / PAGE_SIZE));
  const paginatedEntries = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return filteredEntries.slice(start, start + PAGE_SIZE);
  }, [filteredEntries, currentPage]);

  const existingLanguageKeySet = useMemo(() => {
    return new Set(registries.languageKeys.map((k) => k.key || k.name || ''));
  }, [registries.languageKeys]);

  return (
    <section className="creator-data-view" data-testid="creator-data-view">
      <header className="creator-data-header">
        <div>
          <h1><Database size={18} />工作区数据</h1>
          <p>稳定 ID、引用感知重命名、语言翻译工具与悬空引用诊断</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {tab === 'languageKeys' && (
            <>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setIsImportModalOpen(true)}
                data-testid="language-import-btn"
                style={{ fontSize: '11px' }}
              >
                <Upload size={13} />
                <span>导入词条</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={handleExportCSV}
                data-testid="language-export-csv-btn"
                style={{ fontSize: '11px' }}
                title="导出为 CSV 电子表格格式"
              >
                <FileSpreadsheet size={13} />
                <span>导出 CSV</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={handleExportJSON}
                data-testid="language-export-json-btn"
                style={{ fontSize: '11px' }}
                title="导出为 JSON 语言文件"
              >
                <FileCode size={13} />
                <span>导出 JSON</span>
              </button>
            </>
          )}
          <button className="btn-secondary" onClick={() => void refresh()} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'spin' : undefined} />刷新
          </button>
        </div>
      </header>

      <nav className="creator-data-tabs" aria-label="工作区数据类型">
        {TAB_ITEMS.map((item) => {
          const Icon = item.icon;
          const count = item.id === 'references' ? references?.stats.edgeCount : registries[item.id].length;
          return (
            <button
              key={item.id}
              aria-current={tab === item.id ? 'page' : undefined}
              data-testid={`tab-${item.id}`}
              onClick={() => {
                setTab(item.id);
                setSearchQuery('');
                setLangFilter('all');
              }}
            >
              <Icon size={15} />
              <span>{item.label}</span>
              <span className="badge badge-copper">{count ?? 0}</span>
            </button>
          );
        })}
      </nav>

      {message && (
        <div className="creator-data-message" role="status">
          {message}
          <button onClick={() => setMessage(null)} aria-label="关闭消息">
            <X size={13} />
          </button>
        </div>
      )}

      {renamePreview && (
        <div className="registry-confirmation" role="alert">
          <div>
            <strong>确认重命名：{renamePreview.oldName} → {renamePreview.newName}</strong>
            <span>将影响 {renamePreview.impactedElementCount} 个元素、{renamePreview.references.edges.length} 条引用；提交前会创建恢复点。</span>
          </div>
          <button className="btn-secondary" onClick={() => setRenamePreview(null)}>返回编辑</button>
          <button className="btn-primary" onClick={() => void applyRename()} disabled={!renamePreview.canApply}>
            <Check size={14} />确认提交
          </button>
        </div>
      )}

      {/* Toolbar & Search Bar */}
      {tab !== 'references' && (
        <div className="registry-toolbar" style={{ flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', gap: '12px', flex: 1 }}>
            {/* Search Input */}
            <div style={{ position: 'relative', width: '220px' }}>
              <Search size={13} style={{ position: 'absolute', left: '8px', top: '8px', color: 'var(--text-sub)' }} />
              <input
                type="text"
                placeholder={`搜索${TAB_ITEMS.find((t) => t.id === tab)?.label}…`}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                data-testid="registry-search-input"
                style={{ paddingLeft: '26px', fontSize: '11px', width: '100%' }}
              />
            </div>

            {/* Language filter pills */}
            {tab === 'languageKeys' && (
              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  type="button"
                  onClick={() => setLangFilter('all')}
                  aria-pressed={langFilter === 'all'}
                  data-testid="filter-lang-all"
                  style={{
                    padding: '3px 8px',
                    fontSize: '11px',
                    borderRadius: 'var(--radius-xs)',
                    border: '1px solid var(--border-subtle)',
                    background: langFilter === 'all' ? 'var(--accent-copper-fill)' : 'transparent',
                    color: langFilter === 'all' ? 'var(--text-on-accent)' : 'var(--text-muted)'
                  }}
                >
                  全部 ({registries.languageKeys.length})
                </button>
                <button
                  type="button"
                  onClick={() => setLangFilter('missing')}
                  aria-pressed={langFilter === 'missing'}
                  data-testid="filter-lang-missing"
                  style={{
                    padding: '3px 8px',
                    fontSize: '11px',
                    borderRadius: 'var(--radius-xs)',
                    border: '1px solid var(--border-subtle)',
                    background: langFilter === 'missing' ? 'var(--badge-amber-bg)' : 'transparent',
                    color: langFilter === 'missing' ? 'var(--badge-amber)' : 'var(--text-muted)'
                  }}
                >
                  缺失翻译 ({projection?.languageStats.missingTranslationCount ?? 0})
                </button>
              </div>
            )}
          </div>

          <button
            className="btn-primary"
            onClick={() => setShowCreate((value) => !value)}
            data-testid="registry-create-btn"
          >
            <Plus size={14} />
            <span>新建条目</span>
          </button>
        </div>
      )}

      {showCreate && tab !== 'references' && (
        <div className="registry-create-band">
          <label>
            <span>{tab === 'languageKeys' ? '语言键' : '名称'}</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder={tab === 'languageKeys' ? 'item.mod.example' : 'entry_name'}
              data-testid="registry-new-name-input"
              autoFocus
            />
          </label>
          {tab === 'variables' && (
            <>
              <label>
                <span>数据类型</span>
                <select value={dataType} onChange={(event) => setDataType(event.target.value)}>
                  <option value="number">数值</option>
                  <option value="logic">布尔</option>
                  <option value="string">文本</option>
                  <option value="itemstack">物品栈</option>
                </select>
              </label>
              <label>
                <span>作用域</span>
                <select value={scope} onChange={(event) => setScope(event.target.value)}>
                  <option value="global">全局</option>
                  <option value="player_persistent">玩家持久化</option>
                  <option value="world">地图 / 世界</option>
                  <option value="local">本地 Procedure</option>
                </select>
              </label>
            </>
          )}
          {tab === 'tags' && (
            <>
              <label>
                <span>命名空间</span>
                <input value={secondary} onChange={(event) => setSecondary(event.target.value)} placeholder="mod" />
              </label>
              <label>
                <span>类别</span>
                <select value={category} onChange={(event) => setCategory(event.target.value)}>
                  <option value="blocks">方块</option>
                  <option value="items">物品</option>
                  <option value="entities">实体</option>
                  <option value="fluids">流体</option>
                  <option value="functions">函数</option>
                </select>
              </label>
            </>
          )}
          {tab === 'languageKeys' && (
            <>
              <label>
                <span>主语言 zh_cn</span>
                <input
                  value={secondary}
                  onChange={(event) => setSecondary(event.target.value)}
                  placeholder="中文显示名称"
                  data-testid="registry-new-zh-input"
                />
              </label>
              <label>
                <span>回退 en_us</span>
                <input
                  value={fallback}
                  onChange={(event) => setFallback(event.target.value)}
                  placeholder="英文显示名称"
                  data-testid="registry-new-en-input"
                />
              </label>
            </>
          )}
          <div className="registry-create-actions">
            <button className="btn-secondary" onClick={resetForm}>取消</button>
            <button
              className="btn-primary"
              onClick={() => void createEntry()}
              disabled={!name.trim()}
              data-testid="registry-create-confirm-btn"
            >
              <Check size={14} />创建
            </button>
          </div>
        </div>
      )}

      <div className="creator-data-content">
        {tab === 'references' ? (
          <ReferenceTable projection={references} />
        ) : (
          <>
            <table className="registry-table" data-testid="registry-table">
              <thead>
                <tr>
                  <th>标识</th>
                  <th>配置 / 翻译</th>
                  <th>支持状态</th>
                  <th aria-label="操作" />
                </tr>
              </thead>
              <tbody>
                {paginatedEntries.map((entry) => (
                  <tr key={entry.id}>
                    <td>
                      {renameDraft?.entryId === entry.id ? (
                        <div className="registry-rename-input">
                          <input
                            value={renameDraft.value}
                            onChange={(event) => setRenameDraft({ entryId: entry.id, value: event.target.value })}
                            onKeyDown={(event) => {
                              if (event.key === 'Enter') void reviewRename();
                            }}
                            autoFocus
                          />
                          <button onClick={() => void reviewRename()} aria-label="预览重命名" title="预览重命名">
                            <Check size={14} />
                          </button>
                          <button onClick={() => setRenameDraft(null)} aria-label="取消重命名" title="取消">
                            <X size={14} />
                          </button>
                        </div>
                      ) : (
                        <>
                          <strong>{entryName(entry)}</strong>
                          <code>{entry.id}</code>
                        </>
                      )}
                    </td>
                    <td>{entryDetails(tab, entry)}</td>
                    <td><span className="badge badge-green">{entry.support?.state ?? 'supported'}</span></td>
                    <td>
                      <div className="registry-row-actions">
                        <button
                          onClick={() => setRenameDraft({ entryId: entry.id, value: entryName(entry) })}
                          aria-label={`重命名 ${entryName(entry)}`}
                          title="重命名"
                        >
                          <Pencil size={14} />
                        </button>
                        <button
                          className="danger"
                          onClick={() => void removeEntry(entry)}
                          aria-label={`删除 ${entryName(entry)}`}
                          title="删除"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && filteredEntries.length === 0 && (
                  <tr>
                    <td colSpan={4} className="registry-empty">
                      {searchQuery ? '没有找到匹配的条目。' : '当前注册表没有条目。'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 14px',
                  borderTop: '1px solid var(--border-subtle)',
                  fontSize: '11px',
                  color: 'var(--text-sub)'
                }}
                data-testid="registry-pagination"
              >
                <span>
                  显示第 {(currentPage - 1) * PAGE_SIZE + 1} - {Math.min(currentPage * PAGE_SIZE, filteredEntries.length)} 项，共 {filteredEntries.length} 项
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage <= 1}
                    data-testid="registry-prev-page-btn"
                    style={{ padding: '3px 8px' }}
                  >
                    <ChevronLeft size={13} />
                    <span>上一页</span>
                  </button>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>
                    {currentPage} / {totalPages}
                  </span>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage >= totalPages}
                    data-testid="registry-next-page-btn"
                    style={{ padding: '3px 8px' }}
                  >
                    <span>下一页</span>
                    <ChevronRight size={13} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {tab === 'languageKeys' && projection && (
        <footer className="language-stats" data-testid="language-health-footer">
          <span>{projection.languageStats.keyCount} 个键</span>
          <span>{projection.languageStats.languageCount} 种语言</span>
          <span className={projection.languageStats.missingTranslationCount ? 'warning' : ''}>
            {projection.languageStats.missingTranslationCount} 处缺失翻译
          </span>
          <span className={projection.languageStats.duplicateKeyCount ? 'error' : ''}>
            {projection.languageStats.duplicateKeyCount} 个重复键
          </span>
        </footer>
      )}

      {/* Language Import Modal */}
      <LanguageImportModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        existingKeys={existingLanguageKeySet}
        onImport={handleImportLanguage}
      />
    </section>
  );
};

const ReferenceTable: React.FC<{ projection: WorkspaceReferenceProjection | null }> = ({ projection }) => {
  const [refSearch, setRefSearch] = useState('');
  const [onlyDangling, setOnlyDangling] = useState(false);
  const [page, setPage] = useState(1);

  const edges = projection?.edges ?? [];
  const filteredEdges = useMemo(() => {
    let list = edges;
    if (onlyDangling) {
      list = list.filter((e) => e.targetId == null);
    }
    if (refSearch.trim()) {
      const q = refSearch.toLowerCase();
      list = list.filter((e) =>
        String(e.sourceId ?? '').toLowerCase().includes(q) ||
        String(e.sourcePath ?? '').toLowerCase().includes(q) ||
        String(e.target ?? '').toLowerCase().includes(q) ||
        String(e.kind ?? '').toLowerCase().includes(q)
      );
    }
    return list;
  }, [edges, onlyDangling, refSearch]);

  const totalPages = Math.max(1, Math.ceil(filteredEdges.length / PAGE_SIZE));
  const paginated = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE;
    return filteredEdges.slice(start, start + PAGE_SIZE);
  }, [filteredEdges, page]);

  return (
    <div className="reference-table-wrap" data-testid="reference-table-wrap">
      <div className="reference-summary" style={{ flexWrap: 'wrap', gap: '10px' }}>
        <span>{projection?.stats.indexedElements ?? 0} 个元素已索引</span>
        <span>{projection?.stats.edgeCount ?? 0} 条引用边</span>
        <span style={{ color: (projection?.diagnostics.length ?? 0) > 0 ? 'var(--badge-red)' : 'inherit' }}>
          {projection?.diagnostics.length ?? 0} 个悬空引用
        </span>
        <span>{projection?.stats.incremental ? '增量索引' : '完整索引'}</span>

        {/* Filter controls */}
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{ position: 'relative', width: '180px' }}>
            <Search size={12} style={{ position: 'absolute', left: '6px', top: '7px', color: 'var(--text-sub)' }} />
            <input
              type="text"
              placeholder="搜索引用边…"
              value={refSearch}
              onChange={(e) => {
                setRefSearch(e.target.value);
                setPage(1);
              }}
              style={{ paddingLeft: '22px', fontSize: '10px', width: '100%' }}
            />
          </div>

          <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '10px', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={onlyDangling}
              onChange={(e) => {
                setOnlyDangling(e.target.checked);
                setPage(1);
              }}
            />
            <span>仅看悬空引用</span>
          </label>
        </div>
      </div>

      <table className="registry-table">
        <thead>
          <tr>
            <th>来源</th>
            <th>路径</th>
            <th>类型</th>
            <th>目标</th>
          </tr>
        </thead>
        <tbody>
          {paginated.map((edge, index) => (
            <tr key={String(edge.id ?? index)}>
              <td><code>{String(edge.sourceId ?? '')}</code></td>
              <td><code>{String(edge.sourcePath ?? '')}</code></td>
              <td>{String(edge.kind ?? '')}</td>
              <td>
                {String(edge.target ?? '')}
                {edge.targetId == null && <span className="badge badge-red" style={{ marginLeft: '4px' }}>悬空</span>}
              </td>
            </tr>
          ))}
          {filteredEdges.length === 0 && (
            <tr>
              <td colSpan={4} className="registry-empty">
                {refSearch || onlyDangling ? '没有匹配的引用边。' : '当前没有引用边。'}
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {totalPages > 1 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '10px',
            fontSize: '10px',
            color: 'var(--text-sub)'
          }}
        >
          <span>共 {filteredEdges.length} 条引用</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page <= 1}
              style={{ padding: '2px 6px', fontSize: '10px' }}
            >
              上一页
            </button>
            <span>{page} / {totalPages}</span>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              style={{ padding: '2px 6px', fontSize: '10px' }}
            >
              下一页
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

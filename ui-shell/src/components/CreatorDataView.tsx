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
  X
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  RegistryEntry,
  RegistryRenamePreview,
  WorkspaceReferenceProjection,
  WorkspaceRegistriesProjection
} from '../types/contract';
import { t } from '../i18n';

type RegistryName = 'variables' | 'tags' | 'languageKeys';
type DataTab = RegistryName | 'references';

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
    previewRegistryRename,
    renameRegistryEntry,
    deleteRegistryEntry
  } = useWorkbench();
  const [tab, setTab] = useState<DataTab>('variables');
  const [projection, setProjection] = useState<WorkspaceRegistriesProjection | null>(null);
  const [references, setReferences] = useState<WorkspaceReferenceProjection | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [secondary, setSecondary] = useState('');
  const [fallback, setFallback] = useState('');
  const [dataType, setDataType] = useState('number');
  const [scope, setScope] = useState('global');
  const [category, setCategory] = useState('items');
  const [renameDraft, setRenameDraft] = useState<{ entryId: string; value: string } | null>(null);
  const [renamePreview, setRenamePreview] = useState<RegistryRenamePreview | null>(null);

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

  const activeEntries = tab === 'references' ? [] : registries[tab];

  return (
    <section className="creator-data-view" data-testid="creator-data-view">
      <header className="creator-data-header">
        <div>
          <h1><Database size={18} />工作区数据</h1>
          <p>稳定 ID、引用感知重命名与悬空引用诊断</p>
        </div>
        <button className="btn-secondary" onClick={() => void refresh()} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'spin' : undefined} />刷新
        </button>
      </header>

      <nav className="creator-data-tabs" aria-label="工作区数据类型">
        {TAB_ITEMS.map((item) => {
          const Icon = item.icon;
          const count = item.id === 'references' ? references?.stats.edgeCount : registries[item.id].length;
          return (
            <button key={item.id} aria-current={tab === item.id ? 'page' : undefined} onClick={() => {
              setTab(item.id);
              setShowCreate(false);
              setRenameDraft(null);
              setRenamePreview(null);
            }}>
              <Icon size={15} /><span>{item.label}</span><span className="badge badge-copper">{count ?? 0}</span>
            </button>
          );
        })}
      </nav>

      {message && <div className="creator-data-message" role="status">{message}<button onClick={() => setMessage(null)} aria-label="关闭消息"><X size={13} /></button></div>}

      {renamePreview && (
        <div className="registry-confirmation" role="alert">
          <div>
            <strong>确认重命名：{renamePreview.oldName} → {renamePreview.newName}</strong>
            <span>将影响 {renamePreview.impactedElementCount} 个元素、{renamePreview.references.edges.length} 条引用；提交前会创建恢复点。</span>
          </div>
          <button className="btn-secondary" onClick={() => setRenamePreview(null)}>返回编辑</button>
          <button className="btn-primary" onClick={() => void applyRename()} disabled={!renamePreview.canApply}><Check size={14} />确认提交</button>
        </div>
      )}

      {tab !== 'references' && (
        <div className="registry-toolbar">
          <div>
            <strong>{TAB_ITEMS.find((item) => item.id === tab)?.label}</strong>
            <span>{projection?.stableIds ? '稳定 ID' : '临时 ID'} · {projection?.referenceAwareRename ? '引用感知' : '直接重命名'}</span>
          </div>
          <button className="btn-primary" onClick={() => setShowCreate((value) => !value)}><Plus size={14} />新建条目</button>
        </div>
      )}

      {showCreate && tab !== 'references' && (
        <div className="registry-create-band">
          <label><span>{tab === 'languageKeys' ? '语言键' : '名称'}</span><input value={name} onChange={(event) => setName(event.target.value)} placeholder={tab === 'languageKeys' ? 'item.mod.example' : 'entry_name'} autoFocus /></label>
          {tab === 'variables' && <>
            <label><span>数据类型</span><select value={dataType} onChange={(event) => setDataType(event.target.value)}><option value="number">数值</option><option value="logic">布尔</option><option value="string">文本</option><option value="itemstack">物品栈</option></select></label>
            <label><span>作用域</span><select value={scope} onChange={(event) => setScope(event.target.value)}><option value="global">全局</option><option value="player_persistent">玩家持久化</option><option value="world">地图 / 世界</option><option value="local">本地 Procedure</option></select></label>
          </>}
          {tab === 'tags' && <>
            <label><span>命名空间</span><input value={secondary} onChange={(event) => setSecondary(event.target.value)} placeholder="mod" /></label>
            <label><span>类别</span><select value={category} onChange={(event) => setCategory(event.target.value)}><option value="blocks">方块</option><option value="items">物品</option><option value="entities">实体</option><option value="fluids">流体</option><option value="functions">函数</option></select></label>
          </>}
          {tab === 'languageKeys' && <>
            <label><span>主语言 zh_cn</span><input value={secondary} onChange={(event) => setSecondary(event.target.value)} /></label>
            <label><span>回退 en_us</span><input value={fallback} onChange={(event) => setFallback(event.target.value)} /></label>
          </>}
          <div className="registry-create-actions"><button className="btn-secondary" onClick={resetForm}>取消</button><button className="btn-primary" onClick={() => void createEntry()} disabled={!name.trim()}><Check size={14} />创建</button></div>
        </div>
      )}

      <div className="creator-data-content">
        {tab === 'references' ? (
          <ReferenceTable projection={references} />
        ) : (
          <table className="registry-table">
            <thead><tr><th>标识</th><th>配置</th><th>支持状态</th><th aria-label="操作" /></tr></thead>
            <tbody>
              {activeEntries.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    {renameDraft?.entryId === entry.id ? (
                      <div className="registry-rename-input"><input value={renameDraft.value} onChange={(event) => setRenameDraft({ entryId: entry.id, value: event.target.value })} onKeyDown={(event) => { if (event.key === 'Enter') void reviewRename(); }} /><button onClick={() => void reviewRename()} aria-label="预览重命名" title="预览重命名"><Check size={14} /></button><button onClick={() => setRenameDraft(null)} aria-label="取消重命名" title="取消"><X size={14} /></button></div>
                    ) : <><strong>{entryName(entry)}</strong><code>{entry.id}</code></>}
                  </td>
                  <td>{entryDetails(tab, entry)}</td>
                  <td><span className="badge badge-green">{entry.support?.state ?? 'supported'}</span></td>
                  <td><div className="registry-row-actions"><button onClick={() => setRenameDraft({ entryId: entry.id, value: entryName(entry) })} aria-label={`重命名 ${entryName(entry)}`} title="重命名"><Pencil size={14} /></button><button className="danger" onClick={() => void removeEntry(entry)} aria-label={`删除 ${entryName(entry)}`} title="删除"><Trash2 size={14} /></button></div></td>
                </tr>
              ))}
              {!loading && activeEntries.length === 0 && <tr><td colSpan={4} className="registry-empty">当前注册表没有条目。</td></tr>}
            </tbody>
          </table>
        )}
      </div>

      {tab === 'languageKeys' && projection && (
        <footer className="language-stats">
          <span>{projection.languageStats.keyCount} 个键</span><span>{projection.languageStats.languageCount} 种语言</span><span className={projection.languageStats.missingTranslationCount ? 'warning' : ''}>{projection.languageStats.missingTranslationCount} 处缺失翻译</span><span className={projection.languageStats.duplicateKeyCount ? 'error' : ''}>{projection.languageStats.duplicateKeyCount} 个重复键</span>
        </footer>
      )}
    </section>
  );
};

const ReferenceTable: React.FC<{ projection: WorkspaceReferenceProjection | null }> = ({ projection }) => (
  <div className="reference-table-wrap">
    <div className="reference-summary">
      <span>{projection?.stats.indexedElements ?? 0} 个元素已索引</span>
      <span>{projection?.stats.edgeCount ?? 0} 条引用边</span>
      <span>{projection?.diagnostics.length ?? 0} 个悬空引用</span>
      <span>{projection?.stats.incremental ? '增量索引' : '完整索引'}</span>
    </div>
    <table className="registry-table">
      <thead><tr><th>来源</th><th>路径</th><th>类型</th><th>目标</th></tr></thead>
      <tbody>
        {(projection?.edges ?? []).map((edge, index) => <tr key={String(edge.id ?? index)}><td><code>{String(edge.sourceId ?? '')}</code></td><td><code>{String(edge.sourcePath ?? '')}</code></td><td>{String(edge.kind ?? '')}</td><td>{String(edge.target ?? '')}{edge.targetId == null && <span className="badge badge-red">悬空</span>}</td></tr>)}
        {(projection?.edges.length ?? 0) === 0 && <tr><td colSpan={4} className="registry-empty">当前没有引用边。</td></tr>}
      </tbody>
    </table>
  </div>
);

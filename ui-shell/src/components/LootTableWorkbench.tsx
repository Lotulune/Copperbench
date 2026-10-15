import { tr } from '../i18n/locale';
import { valueLabel } from '../i18n/labels';
import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import {
  ArrowLeft,
  Gift,
  Save,
  Check,
  X,
  Plus,
  Trash2,
  Copy,
  AlertTriangle,
  Code2,
  Sliders,
  Layers
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ModElementSummary, FieldChange, ModElementEditorProjection } from '../types/contract';
import { uiText, uiMessage, diagnosticMessages, renderUiMessage, useUiLocale, type UiMessage } from '../i18n';
import { numericDraftValue } from '../hooks/editorFieldChanges';
import { commitSpecializedSession, getSpecializedSession, nextSpecializedReadToken, rebaseEditor,
  specializedEditorKey, updateSpecializedSession, useSpecializedEditorSession } from '../hooks/specializedEditorSessions';
import { EditorRecoveryNotice } from './EditorRecoveryNotice';
import { confirmEditorRead } from '../bridge/editorRecovery';

interface LootTableWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

const LOOT_TYPES = [
  { value: 'Block', label: tr("方块掉落 (Block)") },
  { value: 'Entity', label: tr("实体掉落 (Entity)") },
  { value: 'Chest', label: tr("战利品箱 (Chest)") },
  { value: 'Generic', label: tr("通用 (Generic)") },
  { value: 'Fishing', label: tr("钓鱼收获 (Fishing)") },
  { value: 'Advancement reward', label: tr("进度奖励 (Advancement reward)") },
  { value: 'Gift', label: tr("村民/猫礼物 (Gift)") },
  { value: 'Barter', label: tr("猪灵以物易物 (Barter)") },
  { value: 'Archaeology', label: tr("考古刷取 (Archaeology)") },
  { value: 'Empty', label: tr("空战利品表 (Empty)") }
];

const COMMON_ITEMS = [
  'minecraft:diamond',
  'minecraft:iron_ingot',
  'minecraft:copper_ingot',
  'minecraft:raw_iron',
  'minecraft:raw_copper',
  'minecraft:gold_ingot',
  'minecraft:emerald',
  'minecraft:coal',
  'minecraft:redstone',
  'minecraft:lapis_lazuli',
  'copperbench:ruby_gem',
  'minecraft:stick',
  'minecraft:apple',
  'minecraft:book'
];

interface LootEntry {
  id: string;
  type: 'item';
  item: string;
  weight: number;
  minCount: number;
  maxCount: number;
  minEnchantmentLevel: number;
  maxEnchantmentLevel: number;
  affectedByFortune: boolean;
  explosionDecay: boolean;
  silkTouchMode: number; // 0 = Ignore, 1 = Only with, 2 = Only without
}

interface LootPool {
  id: string;
  name: string;
  minrolls: number;
  maxrolls: number;
  hasbonusrolls: boolean;
  minbonusrolls: number;
  maxbonusrolls: number;
  conditions: Array<{ type: string; chance?: number }>;
  entries: LootEntry[];
}

function createDefaultPool(name = tr("战利品池 1")): LootPool {
  return {
    id: 'pool_' + Math.random().toString(36).substring(2, 9),
    name,
    minrolls: 1,
    maxrolls: 1,
    hasbonusrolls: false,
    minbonusrolls: 0,
    maxbonusrolls: 0,
    conditions: [{ type: 'minecraft:survives_explosion' }],
    entries: [
      {
        id: 'entry_' + Math.random().toString(36).substring(2, 9),
        type: 'item',
        item: 'minecraft:diamond',
        weight: 1,
        minCount: 1,
        maxCount: 1,
        minEnchantmentLevel: 0,
        maxEnchantmentLevel: 0,
        affectedByFortune: true,
        explosionDecay: true,
        silkTouchMode: 0
      }
    ]
  };
}

interface LootData { lootType: string; pools: LootPool[]; numericDrafts: Record<string, string> }
const initialLoot = (): LootData => ({ lootType: 'Block', pools: [createDefaultPool()], numericDrafts: {} });
function lootData(editor: ModElementEditorProjection): LootData {
  const allFields = editor.sections.flatMap(section => section.fields);
  const typeField = allFields.find(field => field.path === '/type' || field.path === '/fields/type');
  const poolsField = allFields.find(field => field.path === '/pools' || field.path === '/fields/pools');
  const pools = Array.isArray(poolsField?.value) ? (poolsField.value as Array<Record<string, unknown>>).map((pool, index) => ({
    ...pool, id: typeof pool.id === 'string' ? pool.id : `pool_${index + 1}`,
    name: typeof pool.name === 'string' ? pool.name : `Pool ${index + 1}`,
    minrolls: typeof pool.minrolls === 'number' ? pool.minrolls : typeof pool.minRolls === 'number' ? pool.minRolls : 1,
    maxrolls: typeof pool.maxrolls === 'number' ? pool.maxrolls : typeof pool.maxRolls === 'number' ? pool.maxRolls : 1,
    hasbonusrolls: typeof pool.hasbonusrolls === 'boolean' ? pool.hasbonusrolls : pool.hasBonusRolls === true,
    minbonusrolls: typeof pool.minbonusrolls === 'number' ? pool.minbonusrolls : typeof pool.minBonusRolls === 'number' ? pool.minBonusRolls : 0,
    maxbonusrolls: typeof pool.maxbonusrolls === 'number' ? pool.maxbonusrolls : typeof pool.maxBonusRolls === 'number' ? pool.maxBonusRolls : 0,
    conditions: Array.isArray(pool.conditions) ? pool.conditions : [], entries: Array.isArray(pool.entries) ? pool.entries : []
  })) as LootPool[] : [];
  return { lootType: typeof typeField?.value === 'string' ? typeField.value : 'Block', pools, numericDrafts: {} };
}
export const LootTableWorkbench: React.FC<LootTableWorkbenchProps> = props => {
  const { state } = useWorkbench();
  const key = specializedEditorKey(state.workbench?.workspace.id ?? '', props.element.id, 'loot');
  return <LootEditor key={key} sessionKey={key} {...props} />;
};
const LootEditor: React.FC<LootTableWorkbenchProps & { sessionKey: string }> = ({ element, onClose, sessionKey }) => {
  const { updateModElement, getModElementEditor, refreshWorkspace, state } = useWorkbench();
  const current = useSpecializedEditorSession(sessionKey, initialLoot);
  const { lootType, pools, numericDrafts } = current.data;
  const { saving: isSaving, dirty: isDirty, error: message } = current;
  const changeSession = (patch: Partial<typeof current>) => updateSpecializedSession(sessionKey, initialLoot, old => ({ ...old, ...patch }));
  const setLootType = (lootType: string) => updateSpecializedSession(sessionKey, initialLoot, old => ({ ...old, data: { ...old.data, lootType }, dirty: true }));
  const setPools = (change: React.SetStateAction<LootPool[]>) => updateSpecializedSession(sessionKey, initialLoot, old => ({ ...old,
    data: { ...old.data, pools: typeof change === 'function' ? change(old.data.pools) : change }, dirty: true }));
  const setIsSaving = (saving: boolean) => changeSession({ saving });
  const setIsDirty = (dirty: boolean) => changeSession({ dirty });
  const setMessage = (error: UiMessage | null) => changeSession({ error });
  const actions = useRef({ getModElementEditor, refreshWorkspace });
  actions.current = { getModElementEditor, refreshWorkspace };
  const locale = useUiLocale();
  const numericKey = (field: string, entryId?: string) => JSON.stringify([pools[selectedPoolIndex]?.id, entryId ?? null, field]);
  const editNumber = (key: string, value: string, commit: (value: number) => void) => {
    updateSpecializedSession(sessionKey, initialLoot, old => ({ ...old,
      data: { ...old.data, numericDrafts: { ...old.data.numericDrafts, [key]: value } }, dirty: true }));
    const number = numericDraftValue(value);
    if (number !== undefined) commit(number);
  };

  const [selectedPoolIndex, setSelectedPoolIndex] = useState<number>(0);
  const [activeTab, setActiveTab] = useState<'designer' | 'json'>('designer');
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    const existing = getSpecializedSession(sessionKey, initialLoot);
    if (existing.dirty || existing.saving || existing.loading || existing.saveUnknown) return;
    const readToken = nextSpecializedReadToken();
    const revision = state.workbench?.workspace.revision ?? 0;
    changeSession({ loading: true, readToken });
    void actions.current.getModElementEditor(element.id).then(editor => {
      if (!editor || editor.element.id !== element.id) throw new Error('Loot editor projection unavailable');
      updateSpecializedSession(sessionKey, initialLoot, old => old.readToken !== readToken ? old
        : { ...old, editor, data: old.dirty ? old.data : lootData(editor), revision, loading: false, error: null });
    }).catch(() => {
      updateSpecializedSession(sessionKey, initialLoot, old => old.readToken !== readToken ? old
        : { ...old, loading: false, error: uiMessage('无法加载战利品表。请返回后重试，原草稿已保留。', 'Could not load the loot table. Return and try again; your draft is preserved.') });
    });
  }, [sessionKey, element.id, element.updatedAt]);
  const selectedPool = pools[selectedPoolIndex] ?? pools[0];

  // Validation rules
  const diagnostics = useMemo(() => {
    const diags: string[] = [];
    for (const [key, text] of Object.entries(numericDrafts)) {
      const [poolId, entryId, field] = JSON.parse(key) as [string, string | null, string];
      const pool = pools.find(pool => pool.id === poolId);
      if (!pool || entryId && !pool.entries.some(entry => entry.id === entryId)) continue;
      const number = numericDraftValue(text);
      const min = ['weight', 'minCount', 'maxCount'].includes(field) ? 1 : 0;
      if (number === undefined || !Number.isInteger(number) || number < min) {
        diags.push(uiText(`请输入有效整数：${pool.name} / ${field}`, `Enter a valid whole number: ${pool.name} / ${field}`));
      }
    }
    if (pools.length === 0) {
      diags.push(tr("战利品表必须包含至少一个战利品池。"));
    }
    pools.forEach((pool, pIdx) => {
      if (pool.minrolls > pool.maxrolls) {
        diags.push(tr("池「{0}」（第 {1} 个）：最大掷骰次数 ({2}) 不能小于最小掷骰次数 ({3})。", [pool.name, pIdx + 1, pool.maxrolls, pool.minrolls]));
      }
      if (pool.hasbonusrolls && pool.minbonusrolls > pool.maxbonusrolls) {
        diags.push(tr("池「{0}」：额外掷骰最大值不能小于最小值。", [pool.name]));
      }
      if (pool.entries.length === 0) {
        diags.push(tr("池「{0}」必须包含至少一个物品条目。", [pool.name]));
      }
      pool.entries.forEach((entry, eIdx) => {
        if (!entry.item.trim()) {
          diags.push(tr("池「{0}」的第 {1} 个条目未指定物品标识符。", [pool.name, eIdx + 1]));
        }
        if (entry.weight < 1) {
          diags.push(tr("池「{0}」的条目「{1}」权重必须大于等于 1。", [pool.name, entry.item]));
        }
        if (entry.minCount > entry.maxCount) {
          diags.push(tr("池「{0}」的条目「{1}」最大数量不能小于最小数量。", [pool.name, entry.item]));
        }
      });
    });
    return diags;
  }, [pools, numericDrafts, locale]);

  const updatePool = useCallback((index: number, updater: (p: LootPool) => LootPool) => {
    setPools((prev) => {
      const next = [...prev];
      if (next[index]) next[index] = updater(next[index]);
      return next;
    });
    setIsDirty(true);
    setSaveSuccess(false);
  }, []);

  const handleAddPool = () => {
    const newP = createDefaultPool(tr("战利品池 {0}", [pools.length + 1]));
    setPools([...pools, newP]);
    setSelectedPoolIndex(pools.length);
    setIsDirty(true);
  };

  const handleDuplicatePool = (index: number) => {
    const target = pools[index];
    if (!target) return;
    const duplicated: LootPool = {
      ...JSON.parse(JSON.stringify(target)),
      id: 'pool_' + Math.random().toString(36).substring(2, 9),
      name: tr("{0} (副本)", [target.name])
    };
    setPools([...pools, duplicated]);
    setSelectedPoolIndex(pools.length);
    setIsDirty(true);
  };

  const handleDeletePool = (index: number) => {
    if (pools.length <= 1) {
      setMessage(tr("战利品表必须保留至少一个战利品池。"));
      return;
    }
    const filtered = pools.filter((_, i) => i !== index);
    setPools(filtered);
    setSelectedPoolIndex(Math.max(0, index - 1));
    setIsDirty(true);
  };

  const handleAddEntry = () => {
    if (!selectedPool) return;
    const newEntry: LootEntry = {
      id: 'entry_' + Math.random().toString(36).substring(2, 9),
      type: 'item',
      item: 'minecraft:copper_ingot',
      weight: 1,
      minCount: 1,
      maxCount: 1,
      minEnchantmentLevel: 0,
      maxEnchantmentLevel: 0,
      affectedByFortune: false,
      explosionDecay: true,
      silkTouchMode: 0
    };
    updatePool(selectedPoolIndex, (p: LootPool) => ({
      ...p,
      entries: [...p.entries, newEntry]
    }));
  };

  const handleDeleteEntry = (entryId: string) => {
    if (!selectedPool || selectedPool.entries.length <= 1) {
      setMessage(tr("每个战利品池必须保留至少一个条目。"));
      return;
    }
    updatePool(selectedPoolIndex, (p: LootPool) => ({
      ...p,
      entries: p.entries.filter((e: LootEntry) => e.id !== entryId)
    }));
  };

  const handleUpdateEntry = (entryId: string, updates: Partial<LootEntry>) => {
    updatePool(selectedPoolIndex, (p: LootPool) => ({
      ...p,
      entries: p.entries.map((e: LootEntry) => (e.id === entryId ? { ...e, ...updates } : e))
    }));
  };

  // Generate DataPack representation JSON
  const generatedJson = useMemo(() => {
    const typeMapping: Record<string, string> = {
      Block: 'minecraft:block',
      Entity: 'minecraft:entity',
      Chest: 'minecraft:chest',
      Generic: 'minecraft:generic',
      Fishing: 'minecraft:fishing',
      'Advancement reward': 'minecraft:advancement_reward',
      Gift: 'minecraft:gift',
      Barter: 'minecraft:barter',
      Archaeology: 'minecraft:archaeology',
      Empty: 'minecraft:empty'
    };

    const out = {
      type: typeMapping[lootType] || 'minecraft:block',
      pools: pools.map((p) => {
        const poolObj: Record<string, unknown> = {
          rolls: p.minrolls === p.maxrolls ? p.minrolls : { min: p.minrolls, max: p.maxrolls },
          entries: p.entries.map((entry) => {
            const entryObj: Record<string, unknown> = {
              type: 'minecraft:item',
              name: entry.item,
              weight: entry.weight
            };

            const functions: Array<Record<string, unknown>> = [];
            if (entry.minCount !== 1 || entry.maxCount !== 1) {
              functions.push({
                function: 'minecraft:set_count',
                count: entry.minCount === entry.maxCount ? entry.minCount : { min: entry.minCount, max: entry.maxCount }
              });
            }
            if (entry.affectedByFortune) {
              functions.push({
                function: 'minecraft:apply_bonus',
                enchantment: 'minecraft:fortune',
                formula: 'minecraft:ore_drops'
              });
            }
            if (entry.explosionDecay) {
              functions.push({ function: 'minecraft:explosion_decay' });
            }
            if (entry.maxEnchantmentLevel > 0) {
              functions.push({
                function: 'minecraft:enchant_with_levels',
                levels: { min: entry.minEnchantmentLevel, max: entry.maxEnchantmentLevel }
              });
            }
            if (functions.length > 0) {
              entryObj.functions = functions;
            }

            const conditions: Array<Record<string, unknown>> = [];
            if (entry.silkTouchMode === 1) {
              conditions.push({
                condition: 'minecraft:match_tool',
                predicate: { enchantments: [{ enchantment: 'minecraft:silk_touch', levels: { min: 1 } }] }
              });
            } else if (entry.silkTouchMode === 2) {
              conditions.push({
                condition: 'minecraft:inverted',
                term: {
                  condition: 'minecraft:match_tool',
                  predicate: { enchantments: [{ enchantment: 'minecraft:silk_touch', levels: { min: 1 } }] }
                }
              });
            }
            if (conditions.length > 0) {
              entryObj.conditions = conditions;
            }

            return entryObj;
          })
        };

        if (p.hasbonusrolls && (p.minbonusrolls > 0 || p.maxbonusrolls > 0)) {
          poolObj.bonus_rolls = p.minbonusrolls === p.maxbonusrolls ? p.minbonusrolls : { min: p.minbonusrolls, max: p.maxbonusrolls };
        }

        if (p.conditions.length > 0) {
          poolObj.conditions = p.conditions.map((c) => ({ condition: c.type }));
        }

        return poolObj;
      })
    };

    return JSON.stringify(out, null, 2);
  }, [lootType, pools]);

  const handleSave = async () => {
    const submitted = getSpecializedSession(sessionKey, initialLoot);
    if (!submitted.editor || submitted.saving || submitted.saveUnknown) return;
    if (diagnostics.length > 0) {
      setMessage(uiMessage('请先修正下方配置错误。', 'Correct the configuration errors below before saving.'));
      return;
    }

    setIsSaving(true);
    setMessage(null);
    setSaveSuccess(false);

    const payloadPools = pools.map((p) => ({
      ...p,
      minRolls: p.minrolls,
      minrolls: p.minrolls,
      maxRolls: p.maxrolls,
      maxrolls: p.maxrolls,
      hasBonusRolls: p.hasbonusrolls,
      hasbonusrolls: p.hasbonusrolls,
      minBonusRolls: p.minbonusrolls,
      minbonusrolls: p.minbonusrolls,
      maxBonusRolls: p.maxbonusrolls,
      maxbonusrolls: p.maxbonusrolls
    }));

    const changes: FieldChange[] = [
      { path: '/type', value: lootType },
      { path: '/pools', value: payloadPools }
    ].flatMap(change => {
      const field = submitted.editor!.sections.flatMap(section => section.fields)
        .find(field => field.path === change.path || field.path === `/fields${change.path}`);
      return field && !field.readOnly && JSON.stringify(field.value) !== JSON.stringify(change.value)
        ? [{ path: field.path, value: change.value }] : [];
    });
    if (!changes.length) {
      changeSession({ saving: false, dirty: false, data: { ...submitted.data, numericDrafts: {} } });
      return;
    }

    try {
      const result = await updateModElement(element.id, changes, submitted.revision);
      setIsSaving(false);
      if (result.status === 'committed') {
        setSaveSuccess(true);
        commitSpecializedSession(sessionKey, initialLoot, submitted.data,
          rebaseEditor(submitted.editor, changes, result.data?.element ?? submitted.editor.element), result.newRevision, result.diagnostics,
          { ...submitted.data, numericDrafts: {} });
        setTimeout(() => setSaveSuccess(false), 2500);
      } else {
        changeSession({ diagnostics: result.diagnostics });
        setMessage(diagnosticMessages(result.diagnostics.slice(0, 1), uiMessage('保存战利品表被拒绝。草稿已保留。', 'Saving the loot table was rejected. Your draft is preserved.')));
      }
    } catch {
      setIsSaving(false);
      changeSession({ saveUnknown: true });
      setMessage(uiMessage('未收到保存回执。', 'No save receipt was received.'));
    }
  };
  const readLatest = async () => {
    if (getSpecializedSession(sessionKey, initialLoot).saving) return;
    setIsSaving(true);
    try {
      const { editor, revision: confirmedRevision } = await confirmEditorRead(state.workbench!.workspace.id, actions.current.refreshWorkspace, () => actions.current.getModElementEditor(element.id));
      if (!editor || editor.element.id !== element.id) throw new Error('Loot editor projection unavailable');
      updateSpecializedSession(sessionKey, initialLoot, old => ({ ...old, editor, revision: confirmedRevision,
        dirty: JSON.stringify(old.data) !== JSON.stringify(lootData(editor)), saveUnknown: false,
        error: uiMessage('已读取最新版本。请核对保留的草稿后再保存。', 'The latest version is loaded. Review your preserved draft before saving again.') }));
    } catch { setMessage(uiMessage('无法读取最新版本，草稿和原诊断已保留。', 'Could not read the latest version. Your draft and original diagnostics are preserved.')); }
    finally { setIsSaving(false); }
  };

  if (!current.editor) return <div role="status" data-testid="loottable-workbench-loading" style={{ padding: 24 }}>
    {message ? renderUiMessage(message) : uiText('正在读取战利品表…', 'Loading the loot table…')}</div>;

  return (
    <div
      className="loottable-workbench animate-fade-in"
      data-testid="loottable-workbench"
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflow: 'hidden',
        background: 'var(--bg-base)'
      }}
    >
      {/* Top Header */}
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 18px',
          borderBottom: '1px solid var(--border-subtle)',
          background: 'var(--bg-surface)',
          gap: '12px',
          flexWrap: 'wrap'
        }}
      >
        {/* Left: Identity */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={onClose}
            aria-label={tr("返回元素列表")}
            data-testid="loottable-back-btn"
            style={{ padding: '5px 10px', fontSize: '12px' }}
          >
            <ArrowLeft size={14} />
            <span>{tr("返回")}</span>
          </button>

          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--accent-copper-dim)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--accent-copper)'
            }}
          >
            <Gift size={18} />
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
                {element.displayName}
              </span>
              <span className="badge badge-copper">{tr("战利品表")}</span>
              <span className={`badge badge-${element.state === 'valid' ? 'green' : 'amber'}`}>
                {valueLabel(element.state)}
              </span>
              {isDirty && (
                <span className="badge badge-amber" data-testid="loottable-dirty-badge">
                  {tr("未保存更改")}</span>
              )}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontFamily: 'var(--font-mono)' }}>
              {element.name} ({lootType})
            </div>
          </div>
        </div>

        {/* Center: Loot Table Type + Tabs */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '11px', color: 'var(--text-sub)', fontWeight: 600 }}>
              {tr("掉落类型:")}</span>
            <select
              value={lootType}
              onChange={(e) => {
                setLootType(e.target.value);
                setIsDirty(true);
              }}
              data-testid="loottable-type-select"
              style={{ padding: '3px 8px', fontSize: '11px', fontWeight: 600 }}
            >
              {LOOT_TYPES.map((lt) => (
                <option key={lt.value} value={lt.value}>
                  {lt.label}
                </option>
              ))}
            </select>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              background: 'var(--bg-panel)',
              padding: '2px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--border-subtle)'
            }}
          >
            <button
              type="button"
              onClick={() => setActiveTab('designer')}
              aria-pressed={activeTab === 'designer'}
              data-testid="loottable-tab-designer"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                padding: '4px 10px',
                fontSize: '11px',
                fontWeight: activeTab === 'designer' ? 600 : 500,
                borderRadius: 'var(--radius-xs)',
                background: activeTab === 'designer' ? 'var(--accent-copper-fill)' : 'transparent',
                color: activeTab === 'designer' ? 'var(--text-on-accent)' : 'var(--text-muted)'
              }}
            >
              <Sliders size={13} />
              <span>{tr("结构化设计器")}</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('json')}
              aria-pressed={activeTab === 'json'}
              data-testid="loottable-tab-json"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                padding: '4px 10px',
                fontSize: '11px',
                fontWeight: activeTab === 'json' ? 600 : 500,
                borderRadius: 'var(--radius-xs)',
                background: activeTab === 'json' ? 'var(--accent-copper-fill)' : 'transparent',
                color: activeTab === 'json' ? 'var(--text-on-accent)' : 'var(--text-muted)'
              }}
            >
              <Code2 size={13} />
              <span>{tr("JSON 预览")}</span>
            </button>
          </div>
        </div>

        {/* Right: Save Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {saveSuccess && (
            <span
              style={{
                color: 'var(--badge-green)',
                fontSize: '11px',
                display: 'flex',
                alignItems: 'center',
                gap: '4px'
              }}
            >
              <Check size={14} /> {tr(" 已保存")}</span>
          )}
          <button
            type="button"
            className="btn-primary"
            onClick={handleSave}
            disabled={isSaving || current.saveUnknown || !isDirty || diagnostics.length > 0}
            data-testid="loottable-save-btn"
            style={{ fontSize: '12px', minWidth: '90px' }}
          >
            <Save size={14} />
            <span>{isSaving ? tr("保存中…") : tr("保存战利品表")}</span>
          </button>
        </div>
      </header>

      {/* Validation Banner */}
      {diagnostics.length > 0 && (
        <div
          role="alert"
          style={{
            padding: '8px 18px',
            background: 'var(--badge-red-bg)',
            borderBottom: '1px solid rgba(248, 81, 73, 0.4)',
            color: 'var(--badge-red)',
            fontSize: '11px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}
        >
          <AlertTriangle size={14} style={{ flexShrink: 0 }} />
          <span>{diagnostics[0]}</span>
        </div>
      )}

      {message && (
        <div
          role="status"
          style={{
            padding: '8px 18px',
            background: 'var(--badge-blue-bg)',
            borderBottom: '1px solid rgba(88, 166, 255, 0.3)',
            color: 'var(--badge-blue)',
            fontSize: '11px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <span>{renderUiMessage(message)}</span>
          <button
            type="button"
            onClick={() => setMessage(null)}
            aria-label={uiText('关闭提示', 'Dismiss feedback')}
            style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}
          >
            <X size={13} />
          </button>
        </div>
      )}

      <EditorRecoveryNotice editor="loot" saveUnknown={current.saveUnknown} diagnostics={current.diagnostics}
        busy={isSaving || current.loading} onRead={() => void readLatest()} />

      {/* Main Body */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {activeTab === 'designer' ? (
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
            {/* Left Column: Pools List */}
            <div
              style={{
                width: '260px',
                background: 'var(--bg-surface)',
                borderRight: '1px solid var(--border-subtle)',
                display: 'flex',
                flexDirection: 'column',
                flexShrink: 0
              }}
            >
              <div
                style={{
                  padding: '12px 14px',
                  borderBottom: '1px solid var(--border-subtle)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between'
                }}
              >
                <div style={{ fontWeight: 700, fontSize: '12px', color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Layers size={14} />
                  <span>{tr("战利品池 (")}{pools.length})</span>
                </div>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={handleAddPool}
                  data-testid="loottable-add-pool-btn"
                  style={{ padding: '3px 8px', fontSize: '11px' }}
                >
                  <Plus size={13} />
                  <span>{tr("添加池")}</span>
                </button>
              </div>

              <div style={{ flex: 1, overflowY: 'auto', padding: '8px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                {pools.map((pool, idx) => {
                  const isSel = selectedPoolIndex === idx;
                  return (
                    <button
                      key={pool.id}
                      type="button"
                      onClick={() => setSelectedPoolIndex(idx)}
                      aria-pressed={isSel}
                      data-testid={`pool-item-${idx}`}
                      style={{
                        padding: '10px 12px',
                        borderRadius: 'var(--radius-sm)',
                        border: isSel ? '1px solid var(--accent-copper)' : '1px solid var(--border-subtle)',
                        background: isSel ? 'var(--accent-copper-dim)' : 'var(--bg-panel)',
                        textAlign: 'left',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '4px',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontWeight: 600, fontSize: '12px', color: isSel ? 'var(--accent-copper)' : 'var(--text-main)' }}>
                          {pool.name}
                        </span>
                        <span className="badge badge-copper" style={{ fontSize: '9px' }}>
                          {pool.entries.length} {tr(" 个条目")}</span>
                      </div>
                      <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                        {tr("掷骰: ")}{pool.minrolls === pool.maxrolls ? pool.minrolls : `${pool.minrolls} ~ ${pool.maxrolls}`} {tr(" 次")}</div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Right Column: Selected Pool Editor & Entries */}
            {selectedPool && (
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'auto', padding: '20px', gap: '20px' }}>
                {/* Pool Settings Card */}
                <div
                  style={{
                    background: 'var(--bg-surface)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '14px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontWeight: 700, fontSize: '13px', color: 'var(--text-main)' }}>
                        {tr("池配置")}</span>
                      <input
                        type="text"
                        value={selectedPool.name}
                        onChange={(e) => updatePool(selectedPoolIndex, (p: LootPool) => ({ ...p, name: e.target.value }))}
                        data-testid="pool-name-input"
                        style={{ padding: '3px 8px', fontSize: '12px', width: '160px', fontWeight: 600 }}
                      />
                    </div>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => handleDuplicatePool(selectedPoolIndex)}
                        title={tr("复制此战利品池")}
                        data-testid="duplicate-pool-btn"
                        style={{ padding: '3px 8px', fontSize: '11px' }}
                      >
                        <Copy size={12} />
                        <span>{tr("复制池")}</span>
                      </button>
                      <button
                        type="button"
                        className="btn-danger"
                        onClick={() => handleDeletePool(selectedPoolIndex)}
                        title={tr("删除此战利品池")}
                        data-testid="delete-pool-btn"
                        style={{ padding: '3px 8px', fontSize: '11px' }}
                      >
                        <Trash2 size={12} />
                        <span>{tr("删除池")}</span>
                      </button>
                    </div>
                  </div>

                  {/* Rolls configuration */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '12px' }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                      <span>{tr("最小掷骰次数 (minrolls)")}</span>
                      <input
                        type="text" inputMode="numeric"
                        min={0}
                        max={64000}
                        value={numericDrafts[numericKey('minrolls')] ?? selectedPool.minrolls}
                        onChange={(e) => editNumber(numericKey('minrolls'), e.target.value, value => updatePool(selectedPoolIndex, (p: LootPool) => ({ ...p, minrolls: value })))}
                        data-testid="pool-minrolls-input"
                      />
                    </label>

                    <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                      <span>{tr("最大掷骰次数 (maxrolls)")}</span>
                      <input
                        type="text" inputMode="numeric"
                        min={0}
                        max={64000}
                        value={numericDrafts[numericKey('maxrolls')] ?? selectedPool.maxrolls}
                        onChange={(e) => editNumber(numericKey('maxrolls'), e.target.value, value => updatePool(selectedPoolIndex, (p: LootPool) => ({ ...p, maxrolls: value })))}
                        data-testid="pool-maxrolls-input"
                      />
                    </label>

                    <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', color: 'var(--text-main)', marginTop: '20px', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={selectedPool.hasbonusrolls}
                        onChange={(e) => updatePool(selectedPoolIndex, (p: LootPool) => ({ ...p, hasbonusrolls: e.target.checked }))}
                        data-testid="pool-hasbonusrolls-toggle"
                      />
                      <span>{tr("启用额外掷骰 (Bonus Rolls)")}</span>
                    </label>

                    {selectedPool.hasbonusrolls && (
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                          <span>{tr("最小额外 (min)")}</span>
                          <input
                            type="text" inputMode="numeric"
                            min={0}
                            value={numericDrafts[numericKey('minbonusrolls')] ?? selectedPool.minbonusrolls}
                            onChange={(e) => editNumber(numericKey('minbonusrolls'), e.target.value, value => updatePool(selectedPoolIndex, (p: LootPool) => ({ ...p, minbonusrolls: value })))}
                          />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                          <span>{tr("最大额外 (max)")}</span>
                          <input
                            type="text" inputMode="numeric"
                            min={0}
                            value={numericDrafts[numericKey('maxbonusrolls')] ?? selectedPool.maxbonusrolls}
                            onChange={(e) => editNumber(numericKey('maxbonusrolls'), e.target.value, value => updatePool(selectedPoolIndex, (p: LootPool) => ({ ...p, maxbonusrolls: value })))}
                          />
                        </label>
                      </div>
                    )}
                  </div>
                </div>

                {/* Pool Entries */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div>
                      <h3 style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-main)' }}>
                        {tr("物品掉落条目 (")}{selectedPool.entries.length})
                      </h3>
                      <p style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                        {tr("池掷骰时将根据权重随机选择物品条目发放。")}</p>
                    </div>
                    <button
                      type="button"
                      className="btn-primary"
                      onClick={handleAddEntry}
                      data-testid="loottable-add-entry-btn"
                      style={{ padding: '4px 10px', fontSize: '11px' }}
                    >
                      <Plus size={13} />
                      <span>{tr("添加物品条目")}</span>
                    </button>
                  </div>

                  {/* Entries List */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {selectedPool.entries.map((entry, eIdx) => (
                      <div
                        key={entry.id}
                        data-testid={`entry-card-${eIdx}`}
                        style={{
                          background: 'var(--bg-surface)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-md)',
                          padding: '14px',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '12px'
                        }}
                      >
                        {/* Top row: Item selection + Weight + Delete */}
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1 }}>
                            <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--accent-copper)' }}>
                              #{eIdx + 1}
                            </span>
                            <div style={{ display: 'flex', flexDirection: 'column', flex: 1, gap: '3px' }}>
                              <label style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-sub)' }}>
                                {tr("物品标识符 (Item ID)")}</label>
                              <input
                                type="text"
                                list="common-items-list"
                                value={entry.item}
                                onChange={(e) => handleUpdateEntry(entry.id, { item: e.target.value })}
                                placeholder={tr("minecraft:diamond 或 mod:copper_item")}
                                data-testid={`entry-item-input-${eIdx}`}
                                style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}
                              />
                              <datalist id="common-items-list">
                                {COMMON_ITEMS.map((item) => (
                                  <option key={item} value={item} />
                                ))}
                              </datalist>
                            </div>
                          </div>

                          <label style={{ display: 'flex', flexDirection: 'column', gap: '3px', width: '90px', fontSize: '10px', color: 'var(--text-sub)' }}>
                            <span>{tr("权重 (Weight)")}</span>
                            <input
                              type="text" inputMode="numeric"
                              min={1}
                              max={64000}
                              value={numericDrafts[numericKey('weight', entry.id)] ?? entry.weight}
                              onChange={(e) => editNumber(numericKey('weight', entry.id), e.target.value, value => handleUpdateEntry(entry.id, { weight: value }))}
                              data-testid={`entry-weight-input-${eIdx}`}
                            />
                          </label>

                          <button
                            type="button"
                            onClick={() => handleDeleteEntry(entry.id)}
                            aria-label={tr("删除条目 {0}", [entry.item])}
                            data-testid={`entry-delete-btn-${eIdx}`}
                            style={{
                              background: 'none',
                              border: 'none',
                              color: 'var(--badge-red)',
                              cursor: 'pointer',
                              padding: '6px',
                              borderRadius: 'var(--radius-xs)',
                              alignSelf: 'flex-end'
                            }}
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>

                        {/* Middle row: Count range & Enchantment level */}
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '10px' }}>
                          <label style={{ display: 'flex', flexDirection: 'column', gap: '3px', fontSize: '10px', color: 'var(--text-sub)' }}>
                            <span>{tr("最小数量 (minCount)")}</span>
                            <input
                              type="text" inputMode="numeric"
                              min={1}
                              max={64000}
                              value={numericDrafts[numericKey('minCount', entry.id)] ?? entry.minCount}
                              onChange={(e) => editNumber(numericKey('minCount', entry.id), e.target.value, value => handleUpdateEntry(entry.id, { minCount: value }))}
                              data-testid={`entry-mincount-${eIdx}`}
                            />
                          </label>

                          <label style={{ display: 'flex', flexDirection: 'column', gap: '3px', fontSize: '10px', color: 'var(--text-sub)' }}>
                            <span>{tr("最大数量 (maxCount)")}</span>
                            <input
                              type="text" inputMode="numeric"
                              min={1}
                              max={64000}
                              value={numericDrafts[numericKey('maxCount', entry.id)] ?? entry.maxCount}
                              onChange={(e) => editNumber(numericKey('maxCount', entry.id), e.target.value, value => handleUpdateEntry(entry.id, { maxCount: value }))}
                              data-testid={`entry-maxcount-${eIdx}`}
                            />
                          </label>

                          <label style={{ display: 'flex', flexDirection: 'column', gap: '3px', fontSize: '10px', color: 'var(--text-sub)' }}>
                            <span>{tr("精准采集模式")}</span>
                            <select
                              value={entry.silkTouchMode}
                              onChange={(e) => handleUpdateEntry(entry.id, { silkTouchMode: parseInt(e.target.value) || 0 })}
                              data-testid={`entry-silktouch-${eIdx}`}
                              style={{ fontSize: '11px' }}
                            >
                              <option value={0}>{tr("忽略精准采集")}</option>
                              <option value={1}>{tr("仅精准采集时掉落")}</option>
                              <option value={2}>{tr("仅非精准采集时掉落")}</option>
                            </select>
                          </label>

                          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', justifyContent: 'center' }}>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', cursor: 'pointer' }}>
                              <input
                                type="checkbox"
                                checked={entry.affectedByFortune}
                                onChange={(e) => handleUpdateEntry(entry.id, { affectedByFortune: e.target.checked })}
                                data-testid={`entry-fortune-${eIdx}`}
                              />
                              <span>{tr("受时运影响 (Fortune)")}</span>
                            </label>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', cursor: 'pointer' }}>
                              <input
                                type="checkbox"
                                checked={entry.explosionDecay}
                                onChange={(e) => handleUpdateEntry(entry.id, { explosionDecay: e.target.checked })}
                                data-testid={`entry-explosion-${eIdx}`}
                              />
                              <span>{tr("爆炸衰减 (Explosion)")}</span>
                            </label>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        ) : (
          /* JSON Preview Tab */
          <div style={{ flex: 1, padding: '20px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <h3 style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-main)' }}>
                  {tr("战利品表 JSON")}</h3>
              </div>
            </div>
            <pre
              data-testid="loottable-json-preview"
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '16px',
                margin: 0,
                fontSize: '11px',
                fontFamily: 'var(--font-mono)',
                color: 'var(--text-main)',
                whiteSpace: 'pre-wrap',
                flex: 1,
                overflowY: 'auto'
              }}
            >
              {generatedJson}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
};

import React, { useState, useEffect, useMemo } from 'react';
import {
  ArrowLeft,
  Trophy,
  Save,
  Check,
  X,
  Plus,
  Trash2,
  AlertTriangle,
  Award,
  Sparkles,
  Layers,
  Settings,
  Gift,
  Eye
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ModElementSummary, FieldChange, ModElementEditorProjection } from '../types/contract';
import { t } from '../i18n';

interface AdvancementWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

type AdvancementTab = 'display' | 'criteria' | 'rewards' | 'preview';

interface CriteriaEntry {
  id: string;
  name: string;
  trigger: string;
  item?: string;
}

const TRIGGER_TYPES = [
  { value: 'minecraft:inventory_changed', label: '获得指定物品 (inventory_changed)' },
  { value: 'minecraft:impossible', label: '由函数/命令手动触发 (impossible)' },
  { value: 'minecraft:player_killed_entity', label: '击杀实体 (player_killed_entity)' },
  { value: 'minecraft:tick', label: '每刻持续检测 (tick)' },
  { value: 'minecraft:recipe_unlocked', label: '解锁配方 (recipe_unlocked)' },
  { value: 'minecraft:consume_item', label: '食用/使用物品 (consume_item)' },
  { value: 'minecraft:location', label: '到达指定地点/群系 (location)' },
  { value: 'minecraft:hero_of_the_village', label: '村庄英雄 (hero_of_the_village)' }
];

const BACKGROUND_PRESETS = [
  { value: 'Default', label: '默认石质背景' },
  { value: 'textures/gui/advancements/backgrounds/adventure.png', label: '冒险纹理 (Adventure)' },
  { value: 'textures/gui/advancements/backgrounds/nether.png', label: '下界纹理 (Nether)' },
  { value: 'textures/gui/advancements/backgrounds/end.png', label: '末地纹理 (End)' },
  { value: 'textures/gui/advancements/backgrounds/stone.png', label: '平滑石头 (Stone)' }
];

/**
 * Detects cyclic advancement dependencies.
 * Traverses parent chain upward and descendant tree downward to reject:
 * 1. Direct self reference (candidateParent === currentId || candidateParent === currentName)
 * 2. Any ancestor loop (traversing candidateParent's parent chain leads to currentId or currentName)
 * 3. Any descendant parent (candidateParent is a direct or indirect child of current element)
 */
export function detectAdvancementCycle(
  currentId: string,
  currentName: string,
  candidateParent: string,
  allAdvancements: ModElementSummary[],
  elementEditors: Record<string, ModElementEditorProjection>
): boolean {
  if (!candidateParent || candidateParent === 'root') {
    return false;
  }

  // 1. Direct self-reference
  if (candidateParent === currentId || candidateParent === currentName) {
    return true;
  }

  // Helper to extract parent identifier from an advancement
  const getParentFor = (adv: ModElementSummary): string | null => {
    const advAny = adv as unknown as Record<string, unknown>;
    if (typeof advAny.parent === 'string' && advAny.parent) return advAny.parent;
    if (typeof (advAny.fields as Record<string, unknown>)?.parent === 'string') {
      return (advAny.fields as Record<string, unknown>).parent as string;
    }
    if (typeof (advAny.data as Record<string, unknown>)?.parent === 'string') {
      return (advAny.data as Record<string, unknown>).parent as string;
    }

    const editor = elementEditors[adv.id];
    if (editor?.sections) {
      const allFields = editor.sections.flatMap((s) => s.fields || []);
      const parentField = allFields.find(
        (f) => f.path === '/parent' || f.path === '/fields/parent' || f.path === 'parent' || f.path.endsWith('/parent')
      );
      if (parentField && typeof parentField.value === 'string' && parentField.value) {
        return parentField.value;
      }
    }
    return null;
  };

  // Build name <-> id mappings and parent lookup
  const parentMap = new Map<string, string>();
  const idToName = new Map<string, string>();
  const nameToId = new Map<string, string>();

  idToName.set(currentId, currentName);
  nameToId.set(currentName, currentId);

  allAdvancements.forEach((adv) => {
    idToName.set(adv.id, adv.name);
    nameToId.set(adv.name, adv.id);
    const p = getParentFor(adv);
    if (p && p !== 'root') {
      parentMap.set(adv.name, p);
      parentMap.set(adv.id, p);
    }
  });

  // 2. Upward traversal from candidateParent:
  // If candidateParent's ancestry leads to currentId or currentName, it's a cycle!
  let currentAncestor: string | undefined = candidateParent;
  const visited = new Set<string>();

  while (currentAncestor && currentAncestor !== 'root') {
    if (currentAncestor === currentId || currentAncestor === currentName) {
      return true;
    }
    if (visited.has(currentAncestor)) {
      break;
    }
    visited.add(currentAncestor);

    const nextParent: string | undefined =
      parentMap.get(currentAncestor) ??
      (nameToId.has(currentAncestor) ? parentMap.get(nameToId.get(currentAncestor)!) : undefined) ??
      (idToName.has(currentAncestor) ? parentMap.get(idToName.get(currentAncestor)!) : undefined);

    currentAncestor = nextParent;
  }

  // 3. Descendant traversal (downward from current element):
  // If candidateParent is anywhere in the descendant tree of current element, it's a cycle!
  const descendants = new Set<string>();
  let addedAny = true;

  allAdvancements.forEach((adv) => {
    const p = getParentFor(adv);
    if (p === currentId || p === currentName) {
      descendants.add(adv.id);
      descendants.add(adv.name);
    }
  });

  while (addedAny) {
    addedAny = false;
    allAdvancements.forEach((adv) => {
      if (!descendants.has(adv.id)) {
        const p = getParentFor(adv);
        if (
          p &&
          (descendants.has(p) ||
            (nameToId.has(p) && descendants.has(nameToId.get(p)!)) ||
            (idToName.has(p) && descendants.has(idToName.get(p)!)))
        ) {
          descendants.add(adv.id);
          descendants.add(adv.name);
          addedAny = true;
        }
      }
    });
  }

  if (
    descendants.has(candidateParent) ||
    (nameToId.has(candidateParent) && descendants.has(nameToId.get(candidateParent)!)) ||
    (idToName.has(candidateParent) && descendants.has(idToName.get(candidateParent)!))
  ) {
    return true;
  }

  return false;
}

export const AdvancementWorkbench: React.FC<AdvancementWorkbenchProps> = ({ element, onClose }) => {
  const { updateModElement, getModElementEditor, state } = useWorkbench();

  const [activeTab, setActiveTab] = useState<AdvancementTab>('display');
  const [achievementName, setAchievementName] = useState<string>(element.displayName || '新进度');
  const [achievementDescription, setAchievementDescription] = useState<string>('探索未知领域并制作你的第一个铜制工具。');
  const [achievementIcon, setAchievementIcon] = useState<string>('minecraft:diamond');
  const [achievementType, setAchievementType] = useState<'task' | 'goal' | 'challenge'>('task');
  const [background, setBackground] = useState<string>('Default');
  const [parent, setParent] = useState<string>('root');
  const [showPopup, setShowPopup] = useState<boolean>(true);
  const [announceToChat, setAnnounceToChat] = useState<boolean>(true);
  const [hideIfNotCompleted, setHideIfNotCompleted] = useState<boolean>(false);
  const [disableDisplay, setDisableDisplay] = useState<boolean>(false);

  const [criteria, setCriteria] = useState<CriteriaEntry[]>([
    {
      id: 'crit_1',
      name: 'has_copper_item',
      trigger: 'minecraft:inventory_changed',
      item: 'minecraft:copper_ingot'
    }
  ]);

  const [rewardXP, setRewardXP] = useState<number>(50);
  const [rewardLoot, setRewardLoot] = useState<string[]>([]);
  const [rewardRecipes, setRewardRecipes] = useState<string[]>([]);
  const [rewardFunction, setRewardFunction] = useState<string>('');
  const [newRewardLoot, setNewRewardLoot] = useState<string>('');

  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [saveSuccess, setSaveSuccess] = useState<boolean>(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState<boolean>(false);

  // Load existing projection
  useEffect(() => {
    let cancelled = false;
    getModElementEditor(element.id).then((projection) => {
      if (cancelled || !projection) return;
      const allFields = projection.sections.flatMap((s) => s.fields);
      const nameField = allFields.find((f) => f.path === '/title' || f.path === '/fields/title' || f.path === '/fields/achievementName');
      const descField = allFields.find((f) => f.path === '/description' || f.path === '/fields/description' || f.path === '/fields/achievementDescription');
      const iconField = allFields.find((f) => f.path === '/icon' || f.path === '/fields/icon' || f.path === '/fields/achievementIcon');
      const typeField = allFields.find((f) => f.path === '/frame' || f.path === '/fields/frame' || f.path === '/fields/achievementType');
      const parentField = allFields.find((f) => f.path === '/parent' || f.path === '/fields/parent');
      const bgField = allFields.find((f) => f.path === '/background' || f.path === '/fields/background');
      const popupField = allFields.find((f) => f.path === '/showPopup' || f.path === '/fields/showPopup');
      const chatField = allFields.find((f) => f.path === '/announceToChat' || f.path === '/fields/announceToChat');
      const hideField = allFields.find((f) => f.path === '/hideIfNotCompleted' || f.path === '/fields/hideIfNotCompleted');
      const disableDisplayField = allFields.find((f) => f.path === '/disableDisplay' || f.path === '/fields/disableDisplay');
      const xpField = allFields.find((f) => f.path === '/rewardXP' || f.path === '/fields/rewardXP');
      const lootField = allFields.find((f) => f.path === '/rewardLoot' || f.path === '/fields/rewardLoot');
      const recipesField = allFields.find((f) => f.path === '/rewardRecipes' || f.path === '/fields/rewardRecipes');
      const functionField = allFields.find((f) => f.path === '/rewardFunction' || f.path === '/fields/rewardFunction');
      const criteriaField = allFields.find((f) => f.path === '/criteria' || f.path === '/fields/criteria' || f.path === '/triggerxml' || f.path === '/fields/triggerxml');

      if (nameField && typeof nameField.value === 'string') setAchievementName(nameField.value);
      if (descField && typeof descField.value === 'string') setAchievementDescription(descField.value);
      if (iconField && typeof iconField.value === 'string') setAchievementIcon(iconField.value);
      if (typeField && typeof typeField.value === 'string') setAchievementType(typeField.value as 'task' | 'goal' | 'challenge');
      if (parentField && typeof parentField.value === 'string') setParent(parentField.value);
      if (bgField && typeof bgField.value === 'string') setBackground(bgField.value);
      if (popupField && typeof popupField.value === 'boolean') setShowPopup(popupField.value);
      if (chatField && typeof chatField.value === 'boolean') setAnnounceToChat(chatField.value);
      if (hideField && typeof hideField.value === 'boolean') setHideIfNotCompleted(hideField.value);
      if (disableDisplayField && typeof disableDisplayField.value === 'boolean') setDisableDisplay(disableDisplayField.value);
      if (xpField && typeof xpField.value === 'number') setRewardXP(xpField.value);
      if (lootField && Array.isArray(lootField.value)) setRewardLoot(lootField.value as string[]);
      if (recipesField && Array.isArray(recipesField.value)) setRewardRecipes(recipesField.value as string[]);
      if (functionField && typeof functionField.value === 'string') setRewardFunction(functionField.value);
      if (criteriaField && Array.isArray(criteriaField.value) && criteriaField.value.length > 0) {
        setCriteria(criteriaField.value as CriteriaEntry[]);
      }
      setIsDirty(false);
    }).catch(() => {
      // Keep defaults
    });
    return () => {
      cancelled = true;
    };
  }, [element.id, getModElementEditor]);

  // Prefetch workspace advancements editors so parent relationships are readily accessible
  useEffect(() => {
    const achievements = state.elements.filter((e) => e.type === 'achievement');
    achievements.forEach((adv) => {
      if (!state.elementEditors[adv.id]) {
        void getModElementEditor(adv.id);
      }
    });
  }, [state.elements, state.elementEditors, getModElementEditor]);

  // Available advancements in workspace
  const workspaceAdvancements = useMemo(() => {
    return state.elements.filter((e) => e.type === 'achievement' && e.id !== element.id);
  }, [state.elements, element.id]);

  // Cycle Protection: check if setting candidate as parent creates a circular dependency
  const isParentCycle = useMemo(() => {
    return detectAdvancementCycle(
      element.id,
      element.name,
      parent,
      state.elements.filter((e) => e.type === 'achievement'),
      state.elementEditors
    );
  }, [parent, element.id, element.name, state.elements, state.elementEditors]);

  // Validation rules
  const diagnostics = useMemo(() => {
    const diags: string[] = [];
    if (!achievementName.trim()) {
      diags.push('进度名称 (Title) 不能为空。');
    }
    if (!achievementIcon.trim()) {
      diags.push('进度图标 (Icon) 不能为空。');
    }
    if (criteria.length === 0) {
      diags.push('进度必须包含至少一个触发条件 (Criteria)。');
    }
    criteria.forEach((crit, idx) => {
      if (!crit.name.trim()) {
        diags.push(`第 ${idx + 1} 个条件未设置标识符。`);
      }
    });
    if (isParentCycle) {
      diags.push('检测到循环父级进度依赖，不能将自身或其子级设为父级。');
    }
    return diags;
  }, [achievementName, achievementIcon, criteria, isParentCycle]);

  const handleAddCriteria = () => {
    const newCrit: CriteriaEntry = {
      id: 'crit_' + Math.random().toString(36).substring(2, 9),
      name: `criteria_${criteria.length + 1}`,
      trigger: 'minecraft:inventory_changed',
      item: 'minecraft:copper_ingot'
    };
    setCriteria([...criteria, newCrit]);
    setIsDirty(true);
  };

  const handleRemoveCriteria = (critId: string) => {
    if (criteria.length <= 1) {
      setMessage('进度必须保留至少一个触发条件。');
      return;
    }
    setCriteria(criteria.filter((c) => c.id !== critId));
    setIsDirty(true);
  };

  const handleUpdateCriteria = (critId: string, updates: Partial<CriteriaEntry>) => {
    setCriteria(criteria.map((c) => (c.id === critId ? { ...c, ...updates } : c)));
    setIsDirty(true);
  };

  const handleAddRewardLoot = () => {
    const trimmed = newRewardLoot.trim();
    if (!trimmed) return;
    if (!rewardLoot.includes(trimmed)) {
      setRewardLoot([...rewardLoot, trimmed]);
      setIsDirty(true);
    }
    setNewRewardLoot('');
  };

  const handleSave = async () => {
    if (diagnostics.length > 0) {
      setMessage(`请先修复配置错误：${diagnostics[0]}`);
      return;
    }

    setIsSaving(true);
    setMessage(null);
    setSaveSuccess(false);

    const changes: FieldChange[] = [
      { path: '/title', value: achievementName },
      { path: '/description', value: achievementDescription },
      { path: '/icon', value: achievementIcon },
      { path: '/frame', value: achievementType },
      { path: '/background', value: background },
      { path: '/parent', value: parent },
      { path: '/showPopup', value: showPopup },
      { path: '/announceToChat', value: announceToChat },
      { path: '/hideIfNotCompleted', value: hideIfNotCompleted },
      { path: '/disableDisplay', value: disableDisplay },
      { path: '/rewardXP', value: rewardXP },
      { path: '/rewardLoot', value: rewardLoot },
      { path: '/rewardRecipes', value: rewardRecipes },
      { path: '/rewardFunction', value: rewardFunction },
      { path: '/criteria', value: criteria }
    ];

    try {
      const result = await updateModElement(element.id, changes);
      setIsSaving(false);
      if (result.status === 'committed') {
        setSaveSuccess(true);
        setIsDirty(false);
        setTimeout(() => setSaveSuccess(false), 2500);
      } else {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '保存进度失败。');
      }
    } catch {
      setIsSaving(false);
      setMessage('保存进度时发生错误。');
    }
  };

  return (
    <div
      className="advancement-workbench animate-fade-in"
      data-testid="advancement-workbench"
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
            aria-label="返回元素列表"
            data-testid="advancement-back-btn"
            style={{ padding: '5px 10px', fontSize: '12px' }}
          >
            <ArrowLeft size={14} />
            <span>返回</span>
          </button>

          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--badge-amber-bg)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--badge-amber)'
            }}
          >
            <Trophy size={18} />
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
                {achievementName}
              </span>
              <span className="badge badge-copper">进度</span>
              <span
                className={`badge badge-${
                  achievementType === 'challenge'
                    ? 'amber'
                    : achievementType === 'goal'
                    ? 'blue'
                    : 'green'
                }`}
              >
                {achievementType.toUpperCase()}
              </span>
              {isDirty && (
                <span className="badge badge-amber" data-testid="advancement-dirty-badge">
                  未保存更改
                </span>
              )}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontFamily: 'var(--font-mono)' }}>
              {element.name} · 父级: {parent === 'root' ? '根进度 (Root)' : parent}
            </div>
          </div>
        </div>

        {/* Center: Tabs */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            background: 'var(--bg-panel)',
            padding: '3px',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-subtle)',
            gap: '4px'
          }}
        >
          <button
            type="button"
            onClick={() => setActiveTab('display')}
            aria-pressed={activeTab === 'display'}
            data-testid="advancement-tab-display"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'display' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'display' ? 'var(--accent-copper-fill)' : 'transparent',
              color: activeTab === 'display' ? 'var(--text-on-accent)' : 'var(--text-muted)'
            }}
          >
            <Settings size={13} />
            <span>显示与框架</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('criteria')}
            aria-pressed={activeTab === 'criteria'}
            data-testid="advancement-tab-criteria"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'criteria' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'criteria' ? 'var(--accent-copper-fill)' : 'transparent',
              color: activeTab === 'criteria' ? 'var(--text-on-accent)' : 'var(--text-muted)'
            }}
          >
            <Sparkles size={13} />
            <span>触发条件 ({criteria.length})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('rewards')}
            aria-pressed={activeTab === 'rewards'}
            data-testid="advancement-tab-rewards"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'rewards' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'rewards' ? 'var(--accent-copper-fill)' : 'transparent',
              color: activeTab === 'rewards' ? 'var(--text-on-accent)' : 'var(--text-muted)'
            }}
          >
            <Award size={13} />
            <span>奖励配置</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('preview')}
            aria-pressed={activeTab === 'preview'}
            data-testid="advancement-tab-preview"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'preview' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'preview' ? 'var(--accent-copper-fill)' : 'transparent',
              color: activeTab === 'preview' ? 'var(--text-on-accent)' : 'var(--text-muted)'
            }}
          >
            <Eye size={13} />
            <span>游戏卡片预览</span>
          </button>
        </div>

        {/* Right: Actions */}
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
              <Check size={14} /> 已保存
            </span>
          )}
          <button
            type="button"
            className="btn-primary"
            onClick={handleSave}
            disabled={isSaving || diagnostics.length > 0}
            data-testid="advancement-save-btn"
            style={{ fontSize: '12px', minWidth: '90px' }}
          >
            <Save size={14} />
            <span>{isSaving ? '保存中…' : '保存进度'}</span>
          </button>
        </div>
      </header>

      {/* Cycle or Validation Alert */}
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
          data-testid="advancement-validation-alert"
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
          <span>{message}</span>
          <button
            type="button"
            onClick={() => setMessage(null)}
            style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}
          >
            <X size={13} />
          </button>
        </div>
      )}

      {/* Main Body Content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
        {activeTab === 'display' && (
          <div style={{ maxWidth: '780px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <h2 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-main)' }}>
              显示属性与框架类型
            </h2>

            {/* Parent Selection with Cycle Protection */}
            <div
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: '10px'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <label
                  htmlFor="adv-parent-select"
                  style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '6px' }}
                >
                  <Layers size={14} color="var(--accent-copper)" />
                  <span>父级进度 (Parent Advancement)</span>
                </label>
                <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                  具备循环引用防护
                </span>
              </div>
              <select
                id="adv-parent-select"
                value={parent}
                onChange={(e) => {
                  setParent(e.target.value);
                  setIsDirty(true);
                }}
                data-testid="advancement-parent-select"
                style={{ fontSize: '12px' }}
              >
                <option value="root">根进度 (Root - 无父级，作为标签页起点)</option>
                {workspaceAdvancements.map((adv) => (
                  <option key={adv.id} value={adv.name}>
                    {adv.displayName} ({adv.name})
                  </option>
                ))}
              </select>
            </div>

            {/* Title & Description */}
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
              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>进度标题 (Title)</span>
                <input
                  type="text"
                  value={achievementName}
                  onChange={(e) => {
                    setAchievementName(e.target.value);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-title-input"
                />
              </label>

              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>进度描述 (Description)</span>
                <textarea
                  rows={3}
                  value={achievementDescription}
                  onChange={(e) => {
                    setAchievementDescription(e.target.value);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-desc-input"
                />
              </label>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '14px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>图标物品 (Icon Item ID)</span>
                  <input
                    type="text"
                    value={achievementIcon}
                    onChange={(e) => {
                      setAchievementIcon(e.target.value);
                      setIsDirty(true);
                    }}
                    placeholder="minecraft:diamond"
                    data-testid="advancement-icon-input"
                    style={{ fontFamily: 'var(--font-mono)' }}
                  />
                </label>

                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>框架类型 (Frame Type)</span>
                  <select
                    value={achievementType}
                    onChange={(e) => {
                      setAchievementType(e.target.value as 'task' | 'goal' | 'challenge');
                      setIsDirty(true);
                    }}
                    data-testid="advancement-type-select"
                  >
                    <option value="task">普通任务 (Task - 方形边框)</option>
                    <option value="goal">阶段目标 (Goal - 圆角金边)</option>
                    <option value="challenge">极限挑战 (Challenge - 尖角紫金框)</option>
                  </select>
                </label>
              </div>

              {parent === 'root' && (
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>根进度背景纹理 (Background Texture)</span>
                  <select
                    value={background}
                    onChange={(e) => {
                      setBackground(e.target.value);
                      setIsDirty(true);
                    }}
                    data-testid="advancement-bg-select"
                  >
                    {BACKGROUND_PRESETS.map((bg) => (
                      <option key={bg.value} value={bg.value}>
                        {bg.label}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>

            {/* Notification & Visibility Toggles */}
            <div
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '16px',
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '14px'
              }}
            >
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={showPopup}
                  onChange={(e) => {
                    setShowPopup(e.target.checked);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-popup-toggle"
                />
                <span>达成时在右上角弹出通知 (showPopup)</span>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={announceToChat}
                  onChange={(e) => {
                    setAnnounceToChat(e.target.checked);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-chat-toggle"
                />
                <span>在聊天栏通报给全服玩家 (announceToChat)</span>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={hideIfNotCompleted}
                  onChange={(e) => {
                    setHideIfNotCompleted(e.target.checked);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-hidden-toggle"
                />
                <span>未达成前隐藏此进度 (hideIfNotCompleted)</span>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={disableDisplay}
                  onChange={(e) => {
                    setDisableDisplay(e.target.checked);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-disable-toggle"
                />
                <span>隐藏界面显示（仅作为逻辑条件）</span>
              </label>
            </div>
          </div>
        )}

        {activeTab === 'criteria' && (
          <div style={{ maxWidth: '780px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <h2 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-main)' }}>
                  触发条件 (Criteria & Triggers)
                </h2>
                <p style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                  定义玩家如何达成此进度。多个条件默认需要全部满足。
                </p>
              </div>
              <button
                type="button"
                className="btn-primary"
                onClick={handleAddCriteria}
                data-testid="advancement-add-criteria-btn"
                style={{ padding: '4px 10px', fontSize: '11px' }}
              >
                <Plus size={13} />
                <span>添加条件</span>
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {criteria.map((crit, idx) => (
                <div
                  key={crit.id}
                  data-testid={`criteria-card-${idx}`}
                  style={{
                    background: 'var(--bg-surface)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '12px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--accent-copper)' }}>
                        条件 #{idx + 1}
                      </span>
                      <input
                        type="text"
                        value={crit.name}
                        onChange={(e) => handleUpdateCriteria(crit.id, { name: e.target.value })}
                        placeholder="条件标识符"
                        data-testid={`criteria-name-input-${idx}`}
                        style={{ fontSize: '11px', width: '180px', fontFamily: 'var(--font-mono)' }}
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveCriteria(crit.id)}
                      data-testid={`criteria-delete-btn-${idx}`}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: 'var(--badge-red)',
                        cursor: 'pointer',
                        padding: '4px'
                      }}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '10px', color: 'var(--text-sub)' }}>
                      <span>触发器类型 (Trigger Type)</span>
                      <select
                        value={crit.trigger}
                        onChange={(e) => handleUpdateCriteria(crit.id, { trigger: e.target.value })}
                        data-testid={`criteria-trigger-select-${idx}`}
                        style={{ fontSize: '11px' }}
                      >
                        {TRIGGER_TYPES.map((trig) => (
                          <option key={trig.value} value={trig.value}>
                            {trig.label}
                          </option>
                        ))}
                      </select>
                    </label>

                    {crit.trigger === 'minecraft:inventory_changed' && (
                      <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '10px', color: 'var(--text-sub)' }}>
                        <span>物品要求 (Item ID)</span>
                        <input
                          type="text"
                          value={crit.item || ''}
                          onChange={(e) => handleUpdateCriteria(crit.id, { item: e.target.value })}
                          placeholder="例如 minecraft:copper_ingot"
                          data-testid={`criteria-item-input-${idx}`}
                          style={{ fontFamily: 'var(--font-mono)', fontSize: '11px' }}
                        />
                      </label>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'rewards' && (
          <div style={{ maxWidth: '780px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <h2 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-main)' }}>
                达成奖励 (Advancement Rewards)
              </h2>
              <p style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                当玩家达成此进度时，游戏将自动发放经验值、战利品、解锁配方或调用函数。
              </p>
            </div>

            <div
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: '16px'
              }}
            >
              {/* Experience XP */}
              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>奖励经验值 (XP)</span>
                <input
                  type="number"
                  min={0}
                  max={64000}
                  value={rewardXP}
                  onChange={(e) => {
                    setRewardXP(parseInt(e.target.value) || 0);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-reward-xp-input"
                  style={{ width: '160px' }}
                />
              </label>

              {/* Reward Function */}
              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>奖励执行函数 (Reward Function)</span>
                <input
                  type="text"
                  value={rewardFunction}
                  onChange={(e) => {
                    setRewardFunction(e.target.value);
                    setIsDirty(true);
                  }}
                  placeholder="例如 copperbench:reward_celebration"
                  data-testid="advancement-reward-function-input"
                  style={{ fontFamily: 'var(--font-mono)' }}
                />
              </label>

              {/* Reward Loot Tables */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-main)' }}>
                  奖励战利品表 (Reward Loot Tables)
                </span>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <input
                    type="text"
                    placeholder="例如 copperbench:chests/bonus_reward"
                    value={newRewardLoot}
                    onChange={(e) => setNewRewardLoot(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleAddRewardLoot();
                    }}
                    data-testid="advancement-reward-loot-input"
                    style={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: '11px' }}
                  />
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={handleAddRewardLoot}
                    disabled={!newRewardLoot.trim()}
                    data-testid="advancement-reward-loot-add-btn"
                  >
                    <Plus size={13} />
                    <span>添加战利品表</span>
                  </button>
                </div>

                {rewardLoot.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '6px' }}>
                    {rewardLoot.map((lt) => (
                      <span
                        key={lt}
                        className="badge badge-copper"
                        style={{ display: 'flex', alignItems: 'center', gap: '4px', padding: '4px 8px' }}
                      >
                        <Gift size={12} />
                        <code>{lt}</code>
                        <button
                          type="button"
                          onClick={() => setRewardLoot(rewardLoot.filter((r) => r !== lt))}
                          style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}
                        >
                          <X size={12} />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'preview' && (
          <div style={{ maxWidth: '640px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <h2 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-main)' }}>
                游戏内进度通知卡片模拟
              </h2>
              <p style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                玩家达成进度时在屏幕右上角弹出的 Toast 视觉预览。
              </p>
            </div>

            {/* Simulated Minecraft Toast */}
            <div
              data-testid="advancement-toast-preview"
              style={{
                background: '#262421',
                border:
                  achievementType === 'challenge'
                    ? '2px solid #9a7bd4'
                    : achievementType === 'goal'
                    ? '2px solid #d6b656'
                    : '2px solid #c98446',
                borderRadius: '8px',
                padding: '16px',
                display: 'flex',
                alignItems: 'center',
                gap: '16px',
                boxShadow: 'var(--shadow-lg)'
              }}
            >
              {/* Icon Box */}
              <div
                style={{
                  width: '44px',
                  height: '44px',
                  borderRadius: achievementType === 'goal' ? '50%' : '4px',
                  background: '#33302b',
                  border: '2px solid #57504a',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color:
                    achievementType === 'challenge'
                      ? '#b8a2e8'
                      : achievementType === 'goal'
                      ? '#ecd98a'
                      : '#e8a06a'
                }}
              >
                <Trophy size={24} />
              </div>

              {/* Text Info */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', flex: 1 }}>
                <span
                  style={{
                    fontSize: '11px',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.5px',
                    color:
                      achievementType === 'challenge'
                        ? '#b8a2e8'
                        : achievementType === 'goal'
                        ? '#ecd98a'
                        : '#e8a06a'
                  }}
                >
                  {achievementType === 'challenge'
                    ? '极限挑战达成！'
                    : achievementType === 'goal'
                    ? '目标达成！'
                    : '进度达成！'}
                </span>
                <span style={{ fontSize: '13px', fontWeight: 700, color: '#f5f2ec' }}>
                  {achievementName}
                </span>
                <span style={{ fontSize: '11px', color: '#b6bcc4' }}>
                  {achievementDescription}
                </span>
              </div>

              {/* XP Badge */}
              {rewardXP > 0 && (
                <div
                  style={{
                    background: '#1d3a26',
                    border: '1px solid #3a7a4d',
                    color: '#a8d8b4',
                    padding: '4px 8px',
                    borderRadius: '4px',
                    fontSize: '10px',
                    fontWeight: 700
                  }}
                >
                  +{rewardXP} XP
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

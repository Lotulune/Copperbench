import { tr } from '../i18n/locale';
import { valueLabel } from '../i18n/labels';
import React, { useState, useEffect, useMemo } from 'react';
import {
  ArrowLeft,
  Trophy,
  Save,
  Check,
  X,
  Plus,
  AlertTriangle,
  Award,
  Sparkles,
  Layers,
  Settings,
  Gift,
  Eye
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ModElementSummary, FieldChange, EditorField, ModElementEditorProjection } from '../types/contract';
import { t } from '../i18n';

interface AdvancementWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

type AdvancementTab = 'display' | 'criteria' | 'rewards' | 'preview';

const BACKGROUND_PRESETS = [
  { value: 'Default', label: tr("默认石质背景") },
  { value: 'textures/gui/advancements/backgrounds/adventure.png', label: tr("冒险纹理 (Adventure)") },
  { value: 'textures/gui/advancements/backgrounds/nether.png', label: tr("下界纹理 (Nether)") },
  { value: 'textures/gui/advancements/backgrounds/end.png', label: tr("末地纹理 (End)") },
  { value: 'textures/gui/advancements/backgrounds/stone.png', label: tr("平滑石头 (Stone)") }
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
  const [achievementName, setAchievementName] = useState<string>(element.displayName || tr("新进度"));
  const [achievementDescription, setAchievementDescription] = useState<string>('');
  const [achievementIcon, setAchievementIcon] = useState<string>('Blocks.STONE');
  const [achievementType, setAchievementType] = useState<'task' | 'goal' | 'challenge'>('task');
  const [background, setBackground] = useState<string>('Default');
  const [parent, setParent] = useState<string>('root');
  const [showPopup, setShowPopup] = useState<boolean>(true);
  const [announceToChat, setAnnounceToChat] = useState<boolean>(true);
  const [hideIfNotCompleted, setHideIfNotCompleted] = useState<boolean>(false);
  const [disableDisplay, setDisableDisplay] = useState<boolean>(false);

  const [triggerXml, setTriggerXml] = useState('');
  const [fields, setFields] = useState<Record<string, EditorField>>({});
  const [projectionLoaded, setProjectionLoaded] = useState(false);
  const canEdit = (name: string) => projectionLoaded && fields[name]?.readOnly === false;

  const [rewardXP, setRewardXP] = useState<number>(0);
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
    setProjectionLoaded(false);
    setFields({});
    setTriggerXml('');
    setAchievementName(element.displayName || ''); setAchievementDescription('');
    setAchievementIcon('Blocks.STONE'); setAchievementType('task'); setParent('root');
    setBackground('Default'); setShowPopup(true); setAnnounceToChat(true);
    setHideIfNotCompleted(false); setDisableDisplay(false);
    setRewardXP(0); setRewardLoot([]); setRewardRecipes([]); setRewardFunction('');
    setMessage(null);
    setIsDirty(false);
    getModElementEditor(element.id).then((projection) => {
      if (cancelled) return;
      if (!projection) throw new Error('Editor projection unavailable');
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
      const triggerField = allFields.find((f) => f.path === '/triggerxml' || f.path === '/fields/triggerxml');
      setFields(Object.fromEntries(allFields.map((field) => [field.path.split('/').pop()!, field])));

      if (nameField && typeof nameField.value === 'string') setAchievementName(nameField.value);
      if (descField && typeof descField.value === 'string') setAchievementDescription(descField.value);
      if (iconField && typeof iconField.value === 'string') setAchievementIcon(iconField.value);
      if (typeField && typeof typeField.value === 'string') setAchievementType(typeField.value as 'task' | 'goal' | 'challenge');
      if (parentField && typeof parentField.value === 'string') setParent(parentField.value === 'ROOT' ? 'root' : parentField.value);
      if (bgField && typeof bgField.value === 'string') setBackground(bgField.value);
      if (popupField && typeof popupField.value === 'boolean') setShowPopup(popupField.value);
      if (chatField && typeof chatField.value === 'boolean') setAnnounceToChat(chatField.value);
      if (hideField && typeof hideField.value === 'boolean') setHideIfNotCompleted(hideField.value);
      if (disableDisplayField && typeof disableDisplayField.value === 'boolean') setDisableDisplay(disableDisplayField.value);
      if (xpField && typeof xpField.value === 'number') setRewardXP(xpField.value);
      if (lootField && Array.isArray(lootField.value)) setRewardLoot(lootField.value as string[]);
      if (recipesField && Array.isArray(recipesField.value)) setRewardRecipes(recipesField.value as string[]);
      if (functionField && typeof functionField.value === 'string') setRewardFunction(functionField.value);
      if (triggerField && typeof triggerField.value === 'string') setTriggerXml(triggerField.value);
      setProjectionLoaded(true);
      setIsDirty(false);
    }).catch(() => {
      if (!cancelled) setMessage(tr("无法加载进度编辑信息，请返回后重试。"));
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
      diags.push(tr("进度名称 (Title) 不能为空。"));
    }
    if (!achievementIcon.trim()) {
      diags.push(tr("进度图标 (Icon) 不能为空。"));
    }
    if (fields.triggerxml && triggerXml !== fields.triggerxml.value) {
      const xml = new DOMParser().parseFromString(triggerXml, 'application/xml');
      if (xml.querySelector('parsererror') || xml.documentElement.localName !== 'xml'
          || !xml.querySelector('block[type="advancement_trigger"]')) {
        diags.push(tr("触发条件必须是包含 advancement_trigger 的有效 Blockly XML。"));
      }
    }
    if (isParentCycle) {
      diags.push(tr("检测到循环父级进度依赖，不能将自身或其子级设为父级。"));
    }
    return diags;
  }, [achievementName, achievementIcon, triggerXml, fields, isParentCycle]);

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
    if (!projectionLoaded || isSaving) return;
    if (diagnostics.length > 0) {
      setMessage(tr("请先修复配置错误：{0}", [diagnostics[0]]));
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
      { path: '/parent', value: parent === 'root' ? (fields.parent?.value === 'root' ? 'root' : 'ROOT') : parent },
      { path: '/showPopup', value: showPopup },
      { path: '/announceToChat', value: announceToChat },
      { path: '/hideIfNotCompleted', value: hideIfNotCompleted },
      { path: '/disableDisplay', value: disableDisplay },
      { path: '/rewardXP', value: rewardXP },
      { path: '/rewardLoot', value: rewardLoot },
      { path: '/rewardRecipes', value: rewardRecipes },
      { path: '/rewardFunction', value: rewardFunction },
      { path: '/triggerxml', value: triggerXml }
    ].flatMap((change) => {
      const field = fields[change.path.slice(1)];
      const original = change.path === '/rewardFunction' && field?.value === null ? '' : field?.value;
      return field && !field.readOnly && JSON.stringify(original) !== JSON.stringify(change.value)
        ? [{ path: field.path, value: change.value }] : [];
    });

    if (changes.length === 0) {
      setIsSaving(false);
      setSaveSuccess(true);
      setIsDirty(false);
      setTimeout(() => setSaveSuccess(false), 2500);
      return;
    }

    try {
      const result = await updateModElement(element.id, changes);
      setIsSaving(false);
      if (result.status === 'committed') {
        setSaveSuccess(true);
        setFields((previous) => Object.fromEntries(Object.entries(previous).map(([name, field]) => {
          const change = changes.find((candidate) => candidate.path === field.path);
          return [name, change ? { ...field, value: change.value } : field];
        })));
        setIsDirty(false);
        setTimeout(() => setSaveSuccess(false), 2500);
      } else {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : tr("保存进度失败。"));
      }
    } catch {
      setIsSaving(false);
      setMessage(tr("保存进度时发生错误。"));
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
            aria-label={tr("返回元素列表")}
            data-testid="advancement-back-btn"
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
              <span className="badge badge-copper">{tr("进度")}</span>
              <span
                className={`badge badge-${
                  achievementType === 'challenge'
                    ? 'amber'
                    : achievementType === 'goal'
                    ? 'blue'
                    : 'green'
                }`}
              >
                {valueLabel(achievementType)}
              </span>
              {isDirty && (
                <span className="badge badge-amber" data-testid="advancement-dirty-badge">
                  {tr("未保存更改")}</span>
              )}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)', fontFamily: 'var(--font-mono)' }}>
              {element.name} {tr(" · 父级: ")}{parent === 'root' ? tr("根进度 (Root)") : parent}
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
            <span>{tr("显示与框架")}</span>
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
            <span>{tr("触发条件 (Criteria & Triggers)")}</span>
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
            <span>{tr("奖励配置")}</span>
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
            <span>{tr("游戏卡片预览")}</span>
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
              <Check size={14} /> {tr(" 已保存")}</span>
          )}
          <button
            type="button"
            className="btn-primary"
            onClick={handleSave}
            disabled={!projectionLoaded || isSaving || diagnostics.length > 0 || !Object.values(fields).some((field) => !field.readOnly)}
            data-testid="advancement-save-btn"
            style={{ fontSize: '12px', minWidth: '90px' }}
          >
            <Save size={14} />
            <span>{isSaving ? tr("保存中…") : tr("保存进度")}</span>
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
              {tr("显示属性与框架类型")}</h2>

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
                  <span>{tr("父级进度 (Parent Advancement)")}</span>
                </label>
                <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                  {tr("具备循环引用防护")}</span>
              </div>
              <select
                id="adv-parent-select"
                value={parent}
                onChange={(e) => {
                  setParent(e.target.value);
                  setIsDirty(true);
                }}
                data-testid="advancement-parent-select"
                  disabled={!canEdit('parent')}
                style={{ fontSize: '12px' }}
              >
                <option value="root">{tr("根进度 (Root - 无父级，作为标签页起点)")}</option>
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
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("进度标题 (Title)")}</span>
                <input
                  type="text"
                  value={achievementName}
                  onChange={(e) => {
                    setAchievementName(e.target.value);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-title-input"
                  disabled={!canEdit('title')}
                />
              </label>

              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("进度描述 (Description)")}</span>
                <textarea
                  rows={3}
                  value={achievementDescription}
                  onChange={(e) => {
                    setAchievementDescription(e.target.value);
                    setIsDirty(true);
                  }}
                  data-testid="advancement-desc-input"
                  disabled={!canEdit('description')}
                />
              </label>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '14px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("图标物品 (Icon Item ID)")}</span>
                  <input
                    type="text"
                    value={achievementIcon}
                    onChange={(e) => {
                      setAchievementIcon(e.target.value);
                      setIsDirty(true);
                    }}
                    placeholder="minecraft:diamond"
                    data-testid="advancement-icon-input"
                  disabled={!canEdit('icon')}
                    style={{ fontFamily: 'var(--font-mono)' }}
                  />
                </label>

                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("框架类型 (Frame Type)")}</span>
                  <select
                    value={achievementType}
                    onChange={(e) => {
                      setAchievementType(e.target.value as 'task' | 'goal' | 'challenge');
                      setIsDirty(true);
                    }}
                    data-testid="advancement-type-select"
                  disabled={!canEdit('frame')}
                  >
                    <option value="task">{tr("普通任务 (Task - 方形边框)")}</option>
                    <option value="goal">{tr("阶段目标 (Goal - 圆角金边)")}</option>
                    <option value="challenge">{tr("极限挑战 (Challenge - 尖角紫金框)")}</option>
                  </select>
                </label>
              </div>

              {parent === 'root' && (
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("根进度背景纹理 (Background Texture)")}</span>
                  <select
                    value={background}
                    onChange={(e) => {
                      setBackground(e.target.value);
                      setIsDirty(true);
                    }}
                    data-testid="advancement-bg-select"
                  disabled={!canEdit('background')}
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
                  disabled={!canEdit('showPopup')}
                />
                <span>{tr("达成时在右上角弹出通知 (showPopup)")}</span>
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
                  disabled={!canEdit('announceToChat')}
                />
                <span>{tr("在聊天栏通报给全服玩家 (announceToChat)")}</span>
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
                  disabled={!canEdit('hideIfNotCompleted')}
                />
                <span>{tr("未达成前隐藏此进度 (hideIfNotCompleted)")}</span>
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
                  disabled={!canEdit('disableDisplay')}
                />
                <span>{tr("隐藏界面显示（仅作为逻辑条件）")}</span>
              </label>
            </div>
          </div>
        )}

        {activeTab === 'criteria' && (
          <div style={{ maxWidth: '780px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <label htmlFor="advancement-trigger-xml" style={{ fontSize: '14px', fontWeight: 700 }}>
              {tr("触发条件 XML（triggerxml）")}
            </label>
            <p style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
              {tr("这里显示实际保存的 Blockly 触发条件。仅修改显示或奖励属性时，原有条件会完整保留。")}
            </p>
            <textarea
              id="advancement-trigger-xml"
              data-testid="advancement-trigger-xml"
              value={triggerXml}
              readOnly={!canEdit('triggerxml')}
              rows={16}
              spellCheck={false}
              style={{ fontFamily: 'var(--font-mono)', width: '100%' }}
              onChange={(event) => { setTriggerXml(event.target.value); setIsDirty(true); }}
            />
          </div>
        )}

        {activeTab === 'rewards' && (
          <div style={{ maxWidth: '780px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <h2 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-main)' }}>
                {tr("达成奖励 (Advancement Rewards)")}</h2>
              <p style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                {tr("当玩家达成此进度时，游戏将自动发放经验值、战利品、解锁配方或调用函数。")}</p>
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
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("奖励经验值 (XP)")}</span>
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
                  disabled={!canEdit('rewardXP')}
                  style={{ width: '160px' }}
                />
              </label>

              {/* Reward Function */}
              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '11px', color: 'var(--text-sub)' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{tr("奖励执行函数 (Reward Function)")}</span>
                <input
                  type="text"
                  value={rewardFunction}
                  onChange={(e) => {
                    setRewardFunction(e.target.value);
                    setIsDirty(true);
                  }}
                  placeholder={tr("例如 copperbench:reward_celebration")}
                  data-testid="advancement-reward-function-input"
                  disabled={!canEdit('rewardFunction')}
                  style={{ fontFamily: 'var(--font-mono)' }}
                />
              </label>

              {/* Reward Loot Tables */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-main)' }}>
                  {tr("奖励战利品表 (Reward Loot Tables)")}</span>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <input
                    type="text"
                    placeholder={tr("例如 copperbench:chests/bonus_reward")}
                    value={newRewardLoot}
                    onChange={(e) => setNewRewardLoot(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleAddRewardLoot();
                    }}
                    data-testid="advancement-reward-loot-input"
                    disabled={!canEdit('rewardLoot')}
                    style={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: '11px' }}
                  />
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={handleAddRewardLoot}
                    disabled={!canEdit('rewardLoot') || !newRewardLoot.trim()}
                    data-testid="advancement-reward-loot-add-btn"
                  >
                    <Plus size={13} />
                    <span>{tr("添加战利品表")}</span>
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
                          disabled={!canEdit('rewardLoot')}
                          onClick={() => { setRewardLoot(rewardLoot.filter((r) => r !== lt)); setIsDirty(true); }}
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
                {tr("游戏内进度通知卡片模拟")}</h2>
              <p style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                {tr("玩家达成进度时在屏幕右上角弹出的 Toast 视觉预览。")}</p>
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
                    ? tr("极限挑战达成！")
                    : achievementType === 'goal'
                    ? tr("目标达成！")
                    : tr("进度达成！")}
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

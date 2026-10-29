import { tr } from '../i18n/locale';
import { valueLabel } from '../i18n/labels';
import React, { useState, useEffect, useMemo, useRef } from 'react';
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
import { ModElementSummary, FieldChange, ModElementEditorProjection } from '../types/contract';
import { uiText, uiMessage, renderUiMessage, useUiLocale, type UiMessage } from '../i18n';
import { numericDraftValue } from '../hooks/editorFieldChanges';
import { commitSpecializedSession, getSpecializedSession, nextSpecializedReadToken, rebaseEditor,
  specializedEditorKey, updateSpecializedSession, useSpecializedEditorSession } from '../hooks/specializedEditorSessions';
import { EditorRecoveryNotice } from './EditorRecoveryNotice';
import { confirmEditorRead } from '../bridge/editorRecovery';

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

interface AdvancementData {
  achievementName: string; achievementDescription: string; achievementIcon: string;
  achievementType: 'task' | 'goal' | 'challenge'; background: string; parent: string;
  showPopup: boolean; announceToChat: boolean; hideIfNotCompleted: boolean; disableDisplay: boolean;
  triggerXml: string; rewardXP: string | number; rewardLoot: string[]; rewardRecipes: string[];
  rewardFunction: string; newRewardLoot: string;
}
const initialAdvancement = (): AdvancementData => ({ achievementName: '', achievementDescription: '', achievementIcon: 'Blocks.STONE',
  achievementType: 'task', background: 'Default', parent: 'root', showPopup: true, announceToChat: true,
  hideIfNotCompleted: false, disableDisplay: false, triggerXml: '', rewardXP: 0, rewardLoot: [], rewardRecipes: [], rewardFunction: '', newRewardLoot: '' });

function advancementData(editor: ModElementEditorProjection): AdvancementData {
  const data = initialAdvancement();
  const allFields = editor.sections.flatMap(section => section.fields);
  const names: Record<string, string[]> = {
    achievementName: ['title', 'achievementName'], achievementDescription: ['description', 'achievementDescription'],
    achievementIcon: ['icon', 'achievementIcon'], achievementType: ['frame', 'achievementType'], background: ['background'],
    parent: ['parent'], showPopup: ['showPopup'], announceToChat: ['announceToChat'], hideIfNotCompleted: ['hideIfNotCompleted'],
    disableDisplay: ['disableDisplay'], triggerXml: ['triggerxml'], rewardXP: ['rewardXP'], rewardLoot: ['rewardLoot'],
    rewardRecipes: ['rewardRecipes'], rewardFunction: ['rewardFunction']
  };
  for (const [key, aliases] of Object.entries(names)) {
    const field = allFields.find(field => aliases.some(name => field.path === `/${name}` || field.path === `/fields/${name}`));
    const baseline = data[key as keyof AdvancementData];
    if (field && (Array.isArray(baseline) ? Array.isArray(field.value) : typeof field.value === typeof baseline)) {
      (data as unknown as Record<string, unknown>)[key] = field.value;
    }
  }
  if (data.parent === 'ROOT') data.parent = 'root';
  return data;
}

export const AdvancementWorkbench: React.FC<AdvancementWorkbenchProps> = props => {
  const { state } = useWorkbench();
  const key = specializedEditorKey(state.workbench?.workspace.id ?? '', props.element.id, 'advancement');
  return <AdvancementEditor key={key} sessionKey={key} {...props} />;
};
const AdvancementEditor: React.FC<AdvancementWorkbenchProps & { sessionKey: string }> = ({ element, onClose, sessionKey }) => {
  const { updateModElement, getModElementEditor, refreshWorkspace, state } = useWorkbench();
  const current = useSpecializedEditorSession(sessionKey, initialAdvancement);
  const { achievementName, achievementDescription, achievementIcon, achievementType, background, parent,
    showPopup, announceToChat, hideIfNotCompleted, disableDisplay, triggerXml, rewardXP, rewardLoot,
    rewardRecipes, rewardFunction, newRewardLoot } = current.data;
  const changeSession = (patch: Partial<typeof current>) => updateSpecializedSession(sessionKey, initialAdvancement, old => ({ ...old, ...patch }));
  const setter = <K extends keyof AdvancementData,>(key: K) => (value: React.SetStateAction<AdvancementData[K]>) =>
    updateSpecializedSession(sessionKey, initialAdvancement, old => ({ ...old,
      data: { ...old.data, [key]: typeof value === 'function' ? (value as (value: AdvancementData[K]) => AdvancementData[K])(old.data[key]) : value }, dirty: true }));
  const setAchievementName = setter('achievementName'), setAchievementDescription = setter('achievementDescription'),
    setAchievementIcon = setter('achievementIcon'), setAchievementType = setter('achievementType'), setBackground = setter('background'),
    setParent = setter('parent'), setShowPopup = setter('showPopup'), setAnnounceToChat = setter('announceToChat'),
    setHideIfNotCompleted = setter('hideIfNotCompleted'), setDisableDisplay = setter('disableDisplay'),
    setTriggerXml = setter('triggerXml'), setRewardXP = setter('rewardXP'), setRewardLoot = setter('rewardLoot'),
    setRewardFunction = setter('rewardFunction'), setNewRewardLoot = setter('newRewardLoot');
  const fields = useMemo(() => Object.fromEntries((current.editor?.sections.flatMap(section => section.fields) ?? [])
    .map(field => [field.path.split('/').pop()!, field])), [current.editor]);
  const projectionLoaded = current.editor !== null && !current.loading;
  const canEdit = (name: string) => projectionLoaded && !current.saving && fields[name]?.readOnly === false;
  const { saving: isSaving, dirty: isDirty, error: message } = current;
  const setIsSaving = (saving: boolean) => changeSession({ saving });
  const setIsDirty = (dirty: boolean) => changeSession({ dirty });
  const setMessage = (error: UiMessage | null) => changeSession({ error });
  const actions = useRef({ getModElementEditor, refreshWorkspace });
  actions.current = { getModElementEditor, refreshWorkspace };
  const locale = useUiLocale();

  const [activeTab, setActiveTab] = useState<AdvancementTab>('display');
  const [saveSuccess, setSaveSuccess] = useState<boolean>(false);

  // Re-entering the workbench observes the same workspace/object session.
  useEffect(() => {
    const existing = getSpecializedSession(sessionKey, initialAdvancement);
    if (existing.dirty || existing.saving || existing.loading || existing.saveUnknown) return;
    const readToken = nextSpecializedReadToken();
    const revision = state.workbench?.workspace.revision ?? 0;
    changeSession({ loading: true, readToken });
    void actions.current.getModElementEditor(element.id).then(editor => {
      if (!editor || editor.element.id !== element.id) throw new Error('Advancement editor projection unavailable');
      updateSpecializedSession(sessionKey, initialAdvancement, old => old.readToken !== readToken ? old
        : { ...old, editor, data: old.dirty ? old.data : advancementData(editor), revision, loading: false, error: null });
    }).catch(() => {
      updateSpecializedSession(sessionKey, initialAdvancement, old => old.readToken !== readToken ? old
        : { ...old, loading: false, error: uiMessage('无法加载进度编辑信息。请返回后重试，原草稿已保留。', 'Could not load the advancement editor. Return and try again; your draft is preserved.') });
    });
  }, [sessionKey, element.id, element.updatedAt]);
  // Prefetch workspace advancements editors so parent relationships are readily accessible
  useEffect(() => {
    const achievements = state.elements.filter((e) => e.type === 'achievement');
    achievements.forEach((adv) => {
      if (!state.elementEditors[adv.id]) {
        void actions.current.getModElementEditor(adv.id);
      }
    });
  }, [state.elements, state.elementEditors]);

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
    if (numericDraftValue(rewardXP) === undefined || !Number.isInteger(numericDraftValue(rewardXP))) {
      diags.push(uiText('奖励经验必须是有效整数。', 'Reward XP must be a valid whole number.'));
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
  }, [achievementName, achievementIcon, triggerXml, fields, isParentCycle, rewardXP, locale]);

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
    const submitted = getSpecializedSession(sessionKey, initialAdvancement);
    if (!projectionLoaded || submitted.saving || submitted.saveUnknown || !submitted.editor) return;
    if (diagnostics.length > 0) {
      setMessage(uiMessage('请先修复下方配置错误。', 'Correct the configuration errors below before saving.'));
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
      { path: '/rewardXP', value: numericDraftValue(rewardXP) },
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
      changeSession({ saving: false, data: { ...submitted.data, rewardXP: numericDraftValue(rewardXP)! }, dirty: newRewardLoot.length > 0 });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2500);
      return;
    }

    try {
      const result = await updateModElement(element.id, changes, submitted.revision);
      setIsSaving(false);
      if (result.status === 'committed') {
        setSaveSuccess(true);
        commitSpecializedSession(sessionKey, initialAdvancement, submitted.data,
          rebaseEditor(submitted.editor, changes, result.data?.element ?? submitted.editor.element), result.newRevision, result.diagnostics,
          { ...submitted.data, rewardXP: numericDraftValue(rewardXP)! });
        updateSpecializedSession(sessionKey, initialAdvancement, old => ({ ...old, dirty: old.dirty || old.data.newRewardLoot.length > 0 }));
        setTimeout(() => setSaveSuccess(false), 2500);
      } else {
        changeSession({ diagnostics: result.diagnostics });
        setMessage(result.diagnostics[0]?.message ?? uiMessage('保存进度被拒绝。草稿已保留。', 'Saving the advancement was rejected. Your draft is preserved.'));
      }
    } catch {
      setIsSaving(false);
      changeSession({ saveUnknown: true });
      setMessage(uiMessage('未收到保存回执。', 'No save receipt was received.'));
    }
  };
  const readLatest = async () => {
    if (getSpecializedSession(sessionKey, initialAdvancement).saving) return;
    setIsSaving(true);
    try {
      const { editor, revision: confirmedRevision } = await confirmEditorRead(state.workbench!.workspace.id, actions.current.refreshWorkspace, () => actions.current.getModElementEditor(element.id));
      if (!editor || editor.element.id !== element.id) throw new Error('Advancement editor projection unavailable');
      updateSpecializedSession(sessionKey, initialAdvancement, old => ({ ...old, editor, revision: confirmedRevision,
        dirty: JSON.stringify(old.data) !== JSON.stringify(advancementData(editor)), saveUnknown: false,
        error: uiMessage('已读取最新版本。请核对保留的草稿后再保存。', 'The latest version is loaded. Review your preserved draft before saving again.') }));
    } catch { setMessage(uiMessage('无法读取最新版本，草稿和原诊断已保留。', 'Could not read the latest version. Your draft and original diagnostics are preserved.')); }
    finally { setIsSaving(false); }
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
            disabled={!projectionLoaded || isSaving || current.saveUnknown || !isDirty || diagnostics.length > 0 || !Object.values(fields).some((field) => !field.readOnly)}
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

      <EditorRecoveryNotice editor="advancement" saveUnknown={current.saveUnknown} diagnostics={current.diagnostics}
        busy={isSaving || current.loading} onRead={() => void readLatest()} />

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
                  type="text" inputMode="numeric"
                  min={0}
                  max={64000}
                  value={rewardXP}
                  onChange={(e) => {
                    setRewardXP(e.target.value);
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
              {(numericDraftValue(rewardXP) ?? 0) > 0 && (
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

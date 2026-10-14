import { Select } from './Select';
import { tr, uiText } from '../i18n/locale';
import { renderUiMessage, uiMessage, type UiMessage } from '../i18n';
import { elementLabel, elementShortLabel } from '../i18n/labels';
import React, { useEffect, useState } from 'react';
import { X, Plus, Box, Compass, Scroll, Terminal, FileCode2, Gift, Trophy } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ALL_MOD_ELEMENT_TYPES, ModElementType, type ElementCreationContract } from '../types/contract';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { WorkspacePreviewChoice } from './WorkspacePreviewChoice';

export const CreateElementModal: React.FC = () => {
  const { isCreateModalOpen, setIsCreateModalOpen, createModElement, workspacePreviewTarget,
    getElementCreationContract, listAssets, setActiveView, state } = useWorkbench();
  const [elementType, setElementType] = useState<ModElementType>('block');
  const [name, setName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<UiMessage[] | null>(null);
  const [nameInvalid, setNameInvalid] = useState(false);
  const [texture, setTexture] = useState('');
  const [textureInvalid, setTextureInvalid] = useState(false);
  const [reload, setReload] = useState(0);
  const [creationInputs, setCreationInputs] = useState<{
    type: ModElementType; status: 'loading' | 'ready' | 'error';
    contract?: ElementCreationContract; textures?: string[];
  } | null>(null);
  const requiresTexture = elementType === 'livingentity' || elementType === 'specialentity';
  const textureField = elementType === 'livingentity' ? 'mobModelTexture' : 'entityTexture';
  const inputsReady = !requiresTexture || (creationInputs?.type === elementType && creationInputs.status === 'ready');
  const typeIcons: Record<string, typeof Box> = { block: Box, item: Compass, recipe: Scroll, procedure: Terminal, function: FileCode2, loottable: Gift, achievement: Trophy };

  const dialogRef = useDialogA11y(isCreateModalOpen, () => setIsCreateModalOpen(false));
  const supportedTypes = workspacePreviewTarget?.capabilities.elementTypes?.filter(type => type.supported).map(type => type.elementType);
  const previewReady = !workspacePreviewTarget || (workspacePreviewTarget.selected && workspacePreviewTarget.destination === ''
    && workspacePreviewTarget.capabilities.operations?.some(operation => operation.operation === 'create_mod_element' && operation.available));
  useEffect(() => {
    if (supportedTypes?.length && !supportedTypes.includes(elementType)) setElementType(supportedTypes[0] as ModElementType);
  }, [workspacePreviewTarget, elementType]);

  useEffect(() => {
    if (isCreateModalOpen) dialogRef.current?.querySelector(`[data-testid="create-element-type-${elementType}"]`)
      ?.scrollIntoView({ block: 'nearest' });
  }, [isCreateModalOpen, elementType, dialogRef]);

  useEffect(() => {
    if (!isCreateModalOpen || !requiresTexture) return;
    let active = true;
    setCreationInputs({ type: elementType, status: 'loading' });
    Promise.all([getElementCreationContract(elementType), listAssets()]).then(([contract, assets]) => {
      if (!active) return;
      if (contract.elementType !== elementType || contract.availability !== 'available' || !contract.complete || !assets
          || !contract.fields.some(field => field.path === `/${textureField}` && field.requiredOnCreate))
        throw new Error('Required texture contract unavailable');
      const textures = [...new Set(assets.assets.filter(asset => asset.category === 'TEXTURE' && asset.sourceAvailable !== false)
        .flatMap(asset => {
          const match = /\/textures\/entities\/([a-z0-9_./-]+\.png)$/.exec(asset.relativePath);
          return match ? [match[1]] : [];
        }))].sort();
      setCreationInputs({ type: elementType, status: 'ready', contract, textures });
      setTexture(previous => textures.includes(previous) ? previous : '');
    }).catch(() => { if (active) setCreationInputs({ type: elementType, status: 'error' }); });
    return () => { active = false; };
  }, [isCreateModalOpen, elementType, requiresTexture, textureField, getElementCreationContract, listAssets, reload]);

  useEffect(() => {
    setName(''); setTexture(''); setError(null); setTextureInvalid(false);
  }, [state.workbench?.workspace.id]);

  if (!isCreateModalOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError([uiMessage('元素标识符不能为空', 'Element identifier is required.')]);
      setNameInvalid(true);
      return;
    }
    // Validate identifier format
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(name.trim())) {
      setError([uiMessage('标识符须以小写字母开头，仅含小写字母、数字或下划线，长度为 1–64（如 copper_lamp）。', 'Use 1–64 lowercase letters, digits or underscores, starting with a letter (for example copper_lamp).')]);
      setNameInvalid(true);
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setNameInvalid(false);
    if (!inputsReady) { setIsSubmitting(false); return; }
    if (requiresTexture && (!texture || !creationInputs?.textures?.includes(texture))) {
      setTextureInvalid(true); setIsSubmitting(false); return;
    }
    try {
      const initialValues = requiresTexture ? { fields: {
        [textureField]: elementType === 'livingentity' ? texture : texture.replace(/\.png$/, '')
      } } : {};
      const res = await createModElement(elementType, name.trim(), initialValues);
      setIsSubmitting(false);
      if (res.status === 'committed') {
        setIsCreateModalOpen(false);
        setName('');
        setTexture(''); setTextureInvalid(false);
      } else {
        setError(res.diagnostics.length ? res.diagnostics.map(diagnostic => diagnostic.message)
          : [uiMessage('创建元素失败', 'Could not create the element.')]);
        setNameInvalid(res.diagnostics.some(diagnostic => diagnostic.path === '/name'));
        setTextureInvalid(res.diagnostics.some(diagnostic => diagnostic.path?.endsWith(`/${textureField}`)));
      }
    } catch {
      setIsSubmitting(false);
      setError([uiMessage('创建元素时发生错误', 'An error occurred while creating the element.')]);
    }
  };

  return (
    <div className="modal-overlay" data-testid="create-element-modal">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={tr("创建模组元素")}
        className={`modal-card create-element-card animate-fade-in${requiresTexture ? " has-entity-texture" : ""}`}
      >
        <div className="modal-header">
          <h2>{tr("创建模组元素")}</h2>
          <button type="button" aria-label={tr("关闭创建元素对话框")} onClick={() => setIsCreateModalOpen(false)} style={{ color: 'var(--text-muted)' }}>
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <WorkspacePreviewChoice />
            {error && (
              <div role="alert" id="create-element-error" style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.3)', padding: '8px 12px', borderRadius: 'var(--radius-sm)', color: 'var(--badge-red)', fontSize: '12px' }}>
                {error.map((message, index) => <p key={index} style={{ margin: '4px 0', overflowWrap: 'anywhere' }}>{renderUiMessage(message)}</p>)}
              </div>
            )}

            {/* Name Identifier */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label htmlFor="create-element-name" style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
                {uiText("元素标识符", "Element identifier")}</label>
              <input
                id="create-element-name"
                type="text"
                placeholder={tr("例如 copper_lamp、trail_lantern")}
                value={name}
                onChange={(e) => setName(e.target.value)}
                aria-invalid={nameInvalid}
                aria-describedby={error ? "create-element-error create-element-hint" : "create-element-hint"}
                autoComplete="off"
                spellCheck={false}
                autoFocus
                data-testid="create-element-name-input"
              />
              <span id="create-element-hint" style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                {uiText("小写字母开头，可用数字和下划线，最多 64 个字符。", "Start with a lowercase letter. Use letters, numbers or underscores, up to 64 characters.")}</span>
            </div>

            {/* Element Type Selection */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
                {tr("元素类型")}</label>
              <div className="create-type-grid" role="group" aria-label={tr("元素类型")}>
                {ALL_MOD_ELEMENT_TYPES.map((type) => {
                  const Icon = typeIcons[type] ?? Compass;
                  const item = { type, label: elementLabel(type), icon: Icon };
                  const isSel = elementType === item.type;
                  return (
                    <button
                      key={item.type}
                      type="button"
                      data-testid={`create-element-type-${item.type}`}
                      disabled={isSubmitting || (supportedTypes !== undefined && !supportedTypes.includes(type))}
                      onClick={() => { setElementType(item.type as ModElementType); setError(null); setTextureInvalid(false); }}
                      aria-label={item.label}
                      title={item.label}
                      aria-pressed={isSel}
                      style={{
                        padding: '10px',
                        background: isSel ? 'var(--accent-copper-dim)' : 'var(--bg-panel)',
                        border: isSel ? '1px solid var(--accent-copper)' : '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-md)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        color: isSel ? 'var(--accent-copper)' : 'var(--text-main)',
                        fontWeight: isSel ? 600 : 500,
                        textAlign: 'left'
                      }}
                    >
                      <Icon size={16} />
                      <span style={{ fontSize: '12px' }}>{elementShortLabel(item.type)}</span>
                    </button>
                  );
                })}
              </div>
            </div>
            {requiresTexture && (
              <div className="create-entity-texture" aria-busy={!inputsReady && creationInputs?.status !== 'error'}>
                <label htmlFor="create-element-texture">{uiText('实体贴图', 'Entity texture')}</label>
                {creationInputs?.type === elementType && creationInputs.status === 'error' ? (
                  <p role="alert">{uiText('无法读取创建要求或贴图列表，请重试。', 'Could not load creation requirements or textures. Retry.')}</p>
                ) : !inputsReady ? (
                  <p role="status">{uiText('正在读取贴图…', 'Loading textures…')}</p>
                ) : (
                  <>
                    <Select id="create-element-texture" data-testid="create-element-texture" value={texture}
                      disabled={isSubmitting} aria-invalid={textureInvalid} aria-describedby="create-element-texture-hint"
                      onChange={event => { setTexture(event.target.value); setTextureInvalid(false); setError(null); }}>
                      <option value="">{uiText('选择已导入的实体贴图', 'Choose an imported entity texture')}</option>
                      {creationInputs?.textures?.map(value => <option key={value} value={value}>{value}</option>)}
                    </Select>
                    <p id="create-element-texture-hint" role={textureInvalid ? 'alert' : undefined}>
                      {textureInvalid ? uiText('请选择实体贴图后继续。', 'Choose an entity texture to continue.')
                        : uiText('选择工作区中的实体贴图。还没有贴图时，先到资产页导入，再返回继续；名称会保留。',
                          'Choose a workspace entity texture. Import one in Assets if needed, then return to continue; your name is kept.')}
                    </p>
                  </>
                )}
                <div className="create-entity-texture-actions">
                  <button type="button" className="btn-secondary" disabled={isSubmitting} onClick={() => {
                    setIsCreateModalOpen(false); setActiveView('assets');
                  }}>{uiText('前往资产页导入', 'Import in Assets')}</button>
                  <button type="button" className="btn-secondary" disabled={isSubmitting}
                    onClick={() => setReload(value => value + 1)}>{uiText('刷新贴图', 'Refresh textures')}</button>
                </div>
              </div>
            )}
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setIsCreateModalOpen(false)}
            >
              {tr("取消")}</button>
            <button
              type="submit"
              className="btn-primary"
              disabled={isSubmitting || !inputsReady || !previewReady || (supportedTypes !== undefined && !supportedTypes.includes(elementType))}
              data-testid="create-element-submit-btn"
            >
              <Plus size={14} />
              <span>{isSubmitting ? tr("创建中…") : tr("创建元素")}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

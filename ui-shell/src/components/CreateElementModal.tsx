import { tr, uiText } from '../i18n/locale';
import { renderUiMessage, uiMessage, t, type UiMessage } from '../i18n';
import { elementLabel, elementShortLabel } from '../i18n/labels';
import React, { useLayoutEffect, useRef, useState } from 'react';
import { X, Plus, Search } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ALL_MOD_ELEMENT_TYPES, type ModElementType } from '../types/contract';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { COMMON_ELEMENT_TYPES, elementTypeDescription } from '../content/elementTypeHelp';
import { showNotice } from '../feedback/feedback';
import { elementVisual } from './elementVisual';

const recentTypes = new Map<string, ModElementType[]>();

export const CreateElementModal: React.FC = () => {
  const { state, isCreateModalOpen, setIsCreateModalOpen, createModElement, setSelectedElementId, setActiveView } = useWorkbench();
  const workspaceId = state.workbench?.workspace.id ?? '';
  const [elementType, setElementType] = useState<ModElementType>('block');
  const [name, setName] = useState('');
  const [query, setQuery] = useState('');
  const [catalog, setCatalog] = useState('all');
  const [pending, setPending] = useState(new Set<string>());
  const pendingRef = useRef(new Set<string>());
  const latestRequest = useRef(0);
  const draft = useRef('');
  const [unknown, setUnknown] = useState(new Set<string>());
  const [error, setError] = useState<UiMessage[] | null>(null);
  const [nameInvalid, setNameInvalid] = useState(false);
  const nameInput = useRef<HTMLInputElement>(null);
  const scope = useRef({ workspaceId, open: isCreateModalOpen, session: 0 });
  if (scope.current.workspaceId !== workspaceId || scope.current.open !== isCreateModalOpen) {
    scope.current = { workspaceId, open: isCreateModalOpen, session: scope.current.session + 1 };
  }
  const session = scope.current.session;
  const dialogRef = useDialogA11y(isCreateModalOpen, () => setIsCreateModalOpen(false));
  useLayoutEffect(() => { setError(null); setNameInvalid(false); }, [session]);
  const creationKey = JSON.stringify([workspaceId, elementType, name.trim()]);
  draft.current = creationKey;
  const isSubmitting = pending.has(creationKey);
  const creationUnknown = unknown.has(creationKey);
  const capability = state.workbench?.capabilities.find(item => item.id === 'mod_elements.create');
  const unavailable = !state.workbench || capability?.availability === 'unavailable';
  const unavailableReason = capability?.message ? t(capability.message) : uiText('请先打开可编辑的工作区。', 'Open an editable workspace first.');
  if (!isCreateModalOpen) return null;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (pendingRef.current.has(creationKey) || creationUnknown || unavailable) return;
    const identifier = name.trim();
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(identifier)) {
      setError([identifier ? uiMessage('标识符须以小写字母开头，仅含小写字母、数字或下划线，长度为 1–64（如 copper_lamp）。', 'Use 1–64 lowercase letters, digits or underscores, starting with a letter (for example copper_lamp).')
        : uiMessage('元素标识符不能为空', 'Element identifier is required.')]);
      setNameInvalid(true); nameInput.current?.focus(); return;
    }
    const request = { workspaceId, session, key: creationKey, type: elementType, id: ++latestRequest.current };
    const isCurrent = () => scope.current.workspaceId === request.workspaceId && scope.current.open
      && scope.current.session === request.session && latestRequest.current === request.id && draft.current === request.key;
    pendingRef.current.add(request.key); setPending(new Set(pendingRef.current));
    setError(null); setNameInvalid(false);
    try {
      // Navigation belongs to this dialog session, never to a late receipt.
      const result = await createModElement(request.type, identifier, { select: false });
      if (result.status === 'committed') {
        recentTypes.set(workspaceId, [request.type, ...(recentTypes.get(workspaceId) ?? []).filter(type => type !== request.type)].slice(0, 6));
        const id = result.data?.element?.id ?? result.data?.elementId;
        if (isCurrent()) {
          setIsCreateModalOpen(false); setName('');
          if (id) { setSelectedElementId(id); setActiveView('elements'); }
        } else {
          showNotice({ message: uiMessage(`元素 ${identifier} 已创建。`, `Element ${identifier} was created.`),
            actionLabel: uiMessage('查看元素', 'View element'),
            onAction: id ? () => { if (scope.current.workspaceId === request.workspaceId) { setSelectedElementId(id); setActiveView('elements'); } } : undefined });
        }
      } else {
        const messages = result.diagnostics.length ? result.diagnostics.map(diagnostic => diagnostic.message)
          : [uiMessage('创建元素失败', 'Could not create the element.')];
        if (isCurrent()) {
          setError(messages);
          const invalid = result.diagnostics.some(diagnostic => diagnostic.path === '/name');
          setNameInvalid(invalid); if (invalid) nameInput.current?.focus();
        } else showNotice({ tone: 'error', message: uiMessage(`未能创建元素 ${identifier}。`, `Could not create element ${identifier}.`), details: messages });
      }
    } catch {
      setUnknown(previous => new Set(previous).add(request.key));
      const message = uiMessage(`尚未确认 ${identifier} 的创建结果。请先在元素列表检查，避免重复创建。`, `The result of creating ${identifier} is not yet confirmed. Check the element list before creating it again.`);
      if (isCurrent()) setError([message]);
      else showNotice({ tone: 'error', message });
    } finally {
      pendingRef.current.delete(request.key); setPending(new Set(pendingRef.current));
    }
  };
  const recent = recentTypes.get(workspaceId) ?? [];
  const normalized = query.trim().toLocaleLowerCase();
  const types = ALL_MOD_ELEMENT_TYPES.filter(type => (catalog === 'all' || (catalog === 'recent' ? recent : COMMON_ELEMENT_TYPES).includes(type))
    && `${type} ${elementLabel(type)} ${elementTypeDescription(type)}`.toLocaleLowerCase().includes(normalized));

  return <div className="modal-overlay" data-testid="create-element-modal">
    <div ref={dialogRef} role="dialog" aria-modal="true" aria-label={tr('创建模组元素')} className="modal-card create-element-card animate-fade-in">
      <div className="modal-header"><h2>{tr('创建模组元素')}</h2>
        <button type="button" aria-label={tr('关闭创建元素对话框')} onClick={() => setIsCreateModalOpen(false)}><X size={16} aria-hidden="true" /></button>
      </div>
      <form onSubmit={handleSubmit}>
        <div className="modal-body">
          {error && <div role="alert" id="create-element-error" className="create-element-error">{error.map((message, index) => <p key={index}>{renderUiMessage(message)}</p>)}</div>}
          {unavailable && <p role="status" id="create-unavailable">{unavailableReason}</p>}
          <div className="create-name-field">
            <label htmlFor="create-element-name">{uiText('元素标识符', 'Element identifier')}</label>
            <input ref={nameInput} id="create-element-name" type="text" placeholder={tr('例如 copper_lamp、trail_lantern')} value={name}
              onChange={event => setName(event.target.value)} aria-invalid={nameInvalid}
              aria-describedby={error ? 'create-element-error create-element-hint' : 'create-element-hint'}
              autoComplete="off" spellCheck={false} autoFocus data-testid="create-element-name-input" />
            <p id="create-element-hint">{uiText('小写字母开头，可含数字和下划线', 'Start with a lowercase letter; letters, digits and underscores')}</p>
          </div>
          <div className="create-catalog-toolbar">
            <label><Search size={14} aria-hidden="true" /><span className="sr-only">{uiText('搜索元素类型', 'Search element types')}</span>
              <input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={uiText('搜索类型或用途', 'Search types or purpose')}
                data-testid="create-type-search" onKeyDown={event => { if (event.key === 'Enter') event.preventDefault(); }} /></label>
            <select aria-label={uiText('类型目录', 'Type catalog')} value={catalog} onChange={event => setCatalog(event.target.value)}>
              <option value="all">{uiText('全部类型', 'All types')}</option><option value="common">{uiText('常用类型', 'Common types')}</option><option value="recent">{uiText('最近使用', 'Recently used')}</option>
            </select>
          </div>
          <p className="create-type-selection sr-only" role="status">{uiText('已选：', 'Selected: ')}{elementLabel(elementType)} · {state.workbench?.workspace.generator.displayName}</p>
          <div className="create-type-grid" role="group" aria-label={tr('元素类型')}>
            {types.map(type => { const { Icon, tone } = elementVisual(type); return <button key={type} type="button" data-testid={`create-element-type-${type}`}
              className="create-type-option" onClick={() => setElementType(type)} aria-label={elementLabel(type)} aria-pressed={elementType === type}
              disabled={unavailable} aria-describedby={unavailable ? 'create-unavailable' : `create-help-${type}`}>
              <span className={`create-type-plate tone-${tone}`} aria-hidden="true"><Icon size={16} /></span><span><strong>{elementShortLabel(type)}</strong><small id={`create-help-${type}`}>{elementTypeDescription(type)}</small></span>
            </button>; })}
            {!types.length && <p role="status">{uiText('没有匹配的类型。试试其他关键词或全部类型。', 'No matching types. Try another search or all types.')}</p>}
          </div>
          {(isSubmitting || pending.size > 0) && <p role="status">{uiText('创建请求正在处理。关闭此窗口不会取消请求，结果会通过通知显示。', 'Creation is in progress. Closing this dialog keeps the request running; its result will appear in a notification.')}</p>}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn-secondary" onClick={() => setIsCreateModalOpen(false)}>{tr('取消')}</button>
          <button type="submit" className="btn-primary" disabled={isSubmitting || creationUnknown || unavailable} data-testid="create-element-submit-btn"><Plus size={14} aria-hidden="true" /><span>{isSubmitting ? tr('创建中…') : tr('创建元素')}</span></button>
        </div>
      </form>
    </div>
  </div>;
};
